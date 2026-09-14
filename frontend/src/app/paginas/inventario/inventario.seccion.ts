import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  InsumoRequestDtoTipoInsumoEnum,
  InventarioInsumosApi,
  InventarioMovimientosApi,
  MovimientoRequestDtoTipoControlEnum,
  type Descuadre,
  type InsumoResponseDto,
  type MovimientoResponseDto,
  type ResumenInventarioDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

const TIPOS_INSUMO = InsumoRequestDtoTipoInsumoEnum;
const TIPOS_MOVIMIENTO = MovimientoRequestDtoTipoControlEnum;

/**
 * Los movimientos que se registran a mano. La salida por venta y la
 * transformacion no estan: las escribe el sistema al vender y al cocinar, y
 * dejarlas aqui invitaria a descontar dos veces el mismo kilo.
 *
 * Solo los valores del contrato: el rotulo de cada uno sale del diccionario
 * por `tEnum`, igual que en el kardex y en el resumen, para que el mismo
 * movimiento no se llame de dos maneras en la misma pantalla.
 */
const MOTIVOS_MANUALES = [
  TIPOS_MOVIMIENTO.ENTRADA_COMPRA,
  TIPOS_MOVIMIENTO.MERMA_DESPERDICIO,
  TIPOS_MOVIMIENTO.AJUSTE_AUDITORIA,
];

/**
 * Inventario: los insumos, lo que falta, el kardex de cada uno y las dos formas
 * de corregir el stock —un movimiento suelto o un conteo fisico completo.
 *
 * La tabla queda siempre a la vista. El kardex se abre en un cajon lateral y el
 * alta, la edicion y el movimiento en un dialogo: desplegados dentro de la
 * tabla empujaban las filas y, en el celular, una tabla dentro de otra no se
 * podia leer.
 *
 * El conteo fisico es la excepcion y sigue en la tabla, porque se cuenta fila
 * por fila recorriendo el almacen.
 */
@Component({
  selector: 'app-inventario-seccion',
  imports: [DecimalPipe, Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './inventario.seccion.html',
  styleUrls: ['../../disenio/secciones.scss', './inventario.seccion.scss'],
})
export class InventarioSeccion implements OnInit {
  private readonly insumosApi = inject(InventarioInsumosApi);
  private readonly movimientosApi = inject(InventarioMovimientosApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly fecha = this.i18n.fecha;

  protected readonly TIPOS_INSUMO = Object.values(TIPOS_INSUMO);
  protected readonly MOTIVOS_MANUALES = MOTIVOS_MANUALES;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  protected readonly insumos = signal<InsumoResponseDto[]>([]);
  protected readonly resumen = signal<ResumenInventarioDto | null>(null);

  protected readonly filtro = signal('');
  protected readonly soloAlertas = signal(false);

  // --- kardex ---------------------------------------------------------------

  protected readonly kardexAbierto = signal(false);
  protected readonly kardexDe = signal<InsumoResponseDto | null>(null);
  /** `null` mientras llega: una lista vacia ya significa «sin movimientos». */
  protected readonly kardex = signal<MovimientoResponseDto[] | null>(null);

  // --- movimiento suelto ----------------------------------------------------

  protected readonly movimientoAbierto = signal(false);
  protected readonly moviendo = signal<InsumoResponseDto | null>(null);
  protected readonly cantidad = signal<number | null>(null);
  protected readonly motivo = signal<MovimientoRequestDtoTipoControlEnum>(
    TIPOS_MOVIMIENTO.ENTRADA_COMPRA,
  );
  protected readonly observacion = signal('');

  // --- alta y edicion de insumo ---------------------------------------------

  protected readonly formularioAbierto = signal(false);
  /** `null` es un insumo nuevo; un id, uno que se corrige. */
  protected readonly editandoId = signal<string | null>(null);
  protected readonly nombre = signal('');
  protected readonly unidad = signal('KG');
  protected readonly tipo = signal<InsumoRequestDtoTipoInsumoEnum>(TIPOS_INSUMO.NO_COCIDO);
  protected readonly stockMinimo = signal<number | null>(null);

  // --- conteo fisico --------------------------------------------------------

  protected readonly contando = signal(false);
  protected readonly conteo = signal<Record<string, number | null>>({});
  protected readonly observacionConteo = signal('');

  /**
   * Lo que el ultimo conteo encontro distinto. Se queda en pantalla despues de
   * aplicarlo: el ajuste ya esta hecho, y lo unico que le queda por hacer a
   * quien conto es entender por que faltaban tres kilos.
   */
  protected readonly descuadres = signal<Descuadre[]>([]);

  protected readonly visibles = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    return this.insumos().filter((i) => {
      if (this.soloAlertas() && !i.bajoMinimo) return false;
      if (texto.length === 0) return true;
      return (i.nombre ?? '').toLowerCase().includes(texto);
    });
  });

  /** Cuantas lineas del conteo tienen un numero escrito. */
  protected readonly contadas = computed(
    () => Object.values(this.conteo()).filter((v) => v !== null && Number.isFinite(v)).length,
  );

  protected readonly tituloKardex = computed(() =>
    this.t('inventario.kardexDe', { insumo: this.kardexDe()?.nombre ?? '' }),
  );

  protected readonly tituloMovimiento = computed(() =>
    this.t('inventario.moverTitulo', { insumo: this.moviendo()?.nombre ?? '' }),
  );

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editandoId() ? 'inventario.editarInsumo' : 'inventario.nuevoInsumo'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    forkJoin({
      pagina: this.insumosApi.buscarInsumos({
        pageable: { page: 0, size: 300, sort: ['nombre,asc'] },
      }),
      resumen: this.movimientosApi.resumen().pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ pagina, resumen }) => {
        this.insumos.set(pagina.contenido ?? []);
        this.resumen.set(resumen);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected anotarFiltro(v: string): void {
    this.filtro.set(v);
  }

  protected alternarAlertas(): void {
    this.soloAlertas.update((v) => !v);
  }

  // --- kardex ---------------------------------------------------------------

  /**
   * El kardex es la historia de un insumo: de donde salio cada kilo y quien lo
   * movio. Se pide solo al abrirlo porque son muchas filas por insumo.
   */
  protected verKardex(insumo: InsumoResponseDto): void {
    if (!insumo.id) return;

    this.kardexDe.set(insumo);
    this.kardex.set(null);
    this.kardexAbierto.set(true);
    this.movimientosApi
      .kardex({ insumoId: insumo.id, pageable: { page: 0, size: 50 } })
      .subscribe({
        next: (pagina) => this.kardex.set(pagina.contenido ?? []),
        error: () => this.kardex.set([]),
      });
  }

  /** Lo que el movimiento cambio el stock, con su signo: la cantidad sola no dice si entro o salio. */
  protected diferencia(m: MovimientoResponseDto): number {
    return (m.stockNuevo ?? 0) - (m.stockAnterior ?? 0);
  }

  // --- movimiento suelto ----------------------------------------------------

  protected abrirMovimiento(insumo: InsumoResponseDto): void {
    this.moviendo.set(insumo);
    this.cantidad.set(null);
    this.observacion.set('');
    this.motivo.set(TIPOS_MOVIMIENTO.ENTRADA_COMPRA);
    this.movimientoAbierto.set(true);
  }

  protected anotarCantidad(v: string): void {
    const n = Number.parseFloat(v);
    this.cantidad.set(Number.isFinite(n) ? n : null);
  }

  protected elegirMotivo(v: string): void {
    this.motivo.set(v as MovimientoRequestDtoTipoControlEnum);
  }

  protected anotarObservacion(v: string): void {
    this.observacion.set(v);
  }

  /**
   * La cantidad va siempre en positivo: el signo lo pone el motivo. Una entrada
   * suma y una merma resta, y pedirle al almacenero que ademas acierte con el
   * signo es pedirle que se equivoque.
   */
  protected registrarMovimiento(): void {
    const insumo = this.moviendo();
    const cantidad = this.cantidad();
    const observacion = this.observacion().trim();
    if (!insumo?.id || cantidad === null || cantidad <= 0 || observacion.length === 0) return;
    if (this.guardando()) return;

    this.guardando.set(true);
    this.movimientosApi
      .registrarMovimiento({
        movimientoRequestDto: {
          insumoId: insumo.id,
          cantidad,
          tipoControl: this.motivo(),
          motivoObservacion: observacion,
        },
      })
      .subscribe({
        next: (m) => {
          this.guardando.set(false);
          this.movimientoAbierto.set(false);
          this.avisos.exito(
            this.t('inventario.avisoMovimiento', {
              insumo: m.insumoNombre ?? '',
              antes: m.stockAnterior ?? 0,
              despues: m.stockNuevo ?? 0,
              unidad: m.unidadMedida ?? '',
            }),
          );
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }

  // --- alta y edicion de insumo ---------------------------------------------

  protected abrirAlta(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.unidad.set('KG');
    this.tipo.set(TIPOS_INSUMO.NO_COCIDO);
    this.stockMinimo.set(null);
    this.formularioAbierto.set(true);
  }

  /** Se corrige el nombre, la unidad, el tipo o el minimo. El stock no: ese cambia con movimientos. */
  protected abrirEdicion(insumo: InsumoResponseDto): void {
    this.editandoId.set(insumo.id ?? null);
    this.nombre.set(insumo.nombre ?? '');
    this.unidad.set(insumo.unidadMedida ?? '');
    // El enum de la respuesta y el de la peticion son tipos distintos con los mismos valores.
    const tipo: string | undefined = insumo.tipoInsumo;
    this.tipo.set(this.TIPOS_INSUMO.find((t) => t === tipo) ?? TIPOS_INSUMO.NO_COCIDO);
    this.stockMinimo.set(insumo.stockMinimo ?? null);
    this.formularioAbierto.set(true);
  }

  protected anotarNombre(v: string): void {
    this.nombre.set(v);
  }

  protected anotarUnidad(v: string): void {
    this.unidad.set(v.toUpperCase());
  }

  protected elegirTipo(v: string): void {
    this.tipo.set(v as InsumoRequestDtoTipoInsumoEnum);
  }

  protected anotarStockMinimo(v: string): void {
    const n = Number.parseFloat(v);
    this.stockMinimo.set(Number.isFinite(n) ? n : null);
  }

  /** Un insumo nace con stock cero: lo que hay entra despues, con su movimiento. */
  protected guardarInsumo(): void {
    const nombre = this.nombre().trim();
    const unidadMedida = this.unidad().trim();
    if (nombre.length === 0 || unidadMedida.length === 0 || this.guardando()) return;

    const id = this.editandoId();
    const cuerpo = {
      nombre,
      unidadMedida,
      tipoInsumo: this.tipo(),
      stockMinimo: this.stockMinimo() ?? 0,
    };

    this.guardando.set(true);
    const peticion = id
      ? this.insumosApi.actualizarInsumo({ id, insumoRequestDto: cuerpo })
      : this.insumosApi.crearInsumo({ insumoRequestDto: cuerpo });

    peticion.subscribe({
      next: (i) => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t(id ? 'inventario.avisoEditado' : 'inventario.avisoAlta', {
            insumo: i.nombre ?? nombre,
          }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  // --- conteo fisico --------------------------------------------------------

  protected abrirConteo(): void {
    this.contando.update((v) => !v);
    this.conteo.set({});
    this.observacionConteo.set('');
    this.descuadres.set([]);
  }

  protected anotarConteo(insumoId: string, valor: string): void {
    const n = Number.parseFloat(valor);
    this.conteo.update((m) => ({ ...m, [insumoId]: Number.isFinite(n) ? n : null }));
  }

  protected anotarObservacionConteo(v: string): void {
    this.observacionConteo.set(v);
  }

  /**
   * El conteo fisico manda: lo que hay en el almacen gana sobre lo que dice el
   * sistema, y la diferencia queda registrada como ajuste con su autor.
   *
   * Solo se envian las lineas escritas. Un insumo que nadie conto no es un
   * insumo con cero unidades.
   */
  protected enviarConteo(): void {
    const items = Object.entries(this.conteo())
      .filter(([, v]) => v !== null && Number.isFinite(v))
      .map(([insumoId, cantidadContada]) => ({ insumoId, cantidadContada: cantidadContada! }));

    if (items.length === 0 || this.guardando()) return;

    this.guardando.set(true);
    this.movimientosApi
      .conteoFisico({
        conteoFisicoRequestDto: {
          items,
          observacion: this.observacionConteo().trim() || undefined,
        },
      })
      .subscribe({
        next: (resultado) => {
          this.guardando.set(false);
          this.contando.set(false);
          this.conteo.set({});
          this.descuadres.set(resultado.descuadres ?? []);
          // El servidor responde con lo que conto y lo que tuvo que corregir.
          // El numero de ajustes es la noticia: si es cero, el almacen y el
          // sistema dicen lo mismo y no hay nada que revisar.
          const ajustados = resultado.insumosAjustados ?? 0;
          const contados = resultado.insumosContados ?? 0;
          this.avisos.exito(
            ajustados === 0
              ? this.tp('inventario.avisoCuadra', contados)
              : this.tp('inventario.avisoAjustados', contados, { ajustados }),
          );
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }
}
