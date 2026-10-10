import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import {
  ClientesApi,
  FeedbackYFidelizacionApi,
  type ClienteRequestDto,
  type ClienteResponseDto,
  type CuponResponseDto,
  type EmpresaResponseDto,
  type FidelizacionDto,
  type OrdenResumenDto,
  type PageResponseDtoClienteResponseDto,
  type PreferenciasClienteDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import type { Punto } from '../../disenio/mapa';
import { SelectorUbicacion } from '../../disenio/selector-ubicacion';
import { Telefono } from '../../disenio/telefono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { codigoDeOrden } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { SesionService } from '../../nucleo/sesion/sesion.service';
import { formatoTelefono, normalizarCelular } from '../../nucleo/telefono/telefono';
import { problemaDe } from '../../nucleo/validacion/patrones';

const TAMANO_PAGINA = 20;
const MINIMO_BUSQUEDA = 2;
/** La ficha trae las ultimas; el historial completo esta en Ordenes. */
const ORDENES_EN_FICHA = 10;

interface Ficha {
  progreso: FidelizacionDto | null;
  cupones: CuponResponseDto[];
  preferencias: PreferenciasClienteDto | null;
  empresas: EmpresaResponseDto[];
  ordenes: OrdenResumenDto[];
}

/** El formulario sirve para dar de alta a un cliente o para corregir los datos del que esta en la ficha. */
type ModoFormulario = 'alta' | 'edicion';

type CampoCliente = 'documento' | 'nombres' | 'apellidos' | 'celular' | 'correo';

/**
 * Clientes: quien come aqui y que se sabe de cada uno.
 *
 * La lista sirve para encontrar a alguien —por nombre, documento o celular— y
 * la ficha, para atenderlo: cuantos puntos lleva, que cupones puede canjear,
 * que pide siempre y a que empresa se le factura. Todo sale de endpoints que
 * ya existen; lo que el servidor no guarda, como el nivel de lealtad, no se
 * inventa aqui.
 *
 * Bloquear por fraude es del administrador, y pide motivo: un cliente
 * bloqueado no puede pedir por los bots, y alguien tendra que explicarle por
 * que.
 *
 * El alta y la correccion de los datos usan el mismo formulario, en un
 * dialogo como las demas altas; al corregir se abre encima de la ficha. Quien
 * registra a un cliente termina viendo su ficha, que es donde se le agrega lo
 * demas.
 */
@Component({
  selector: 'app-clientes',
  imports: [DecimalPipe, Icono, Dialogo, SelectorUbicacion, Telefono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './clientes.page.html',
  styleUrls: ['../../disenio/secciones.scss', './clientes.page.scss'],
})
export class ClientesPage implements OnInit {
  private readonly clientesApi = inject(ClientesApi);
  private readonly fidelizacionApi = inject(FeedbackYFidelizacionApi);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly fecha = this.i18n.fecha;
  protected readonly codigo = codigoDeOrden;

  protected readonly esAdmin = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly pagina = signal(0);
  protected readonly resultado = signal<PageResponseDtoClienteResponseDto | null>(null);
  protected readonly busqueda = signal('');
  /** `null` mientras no se busca: entonces se ve la lista paginada. */
  protected readonly encontrados = signal<ClienteResponseDto[] | null>(null);

  private readonly consultas = new Subject<string>();

  protected readonly filas = computed(
    () => this.encontrados() ?? this.resultado()?.contenido ?? [],
  );

  // --- ficha ----------------------------------------------------------------
  protected readonly cajonAbierto = signal(false);
  protected readonly cliente = signal<ClienteResponseDto | null>(null);
  protected readonly ficha = signal<Ficha | null>(null);
  protected readonly bloqueando = signal(false);
  protected readonly motivoBloqueo = signal('');

  /**
   * La direccion habitual con su punto en el mapa: es la que el POS propone,
   * ya ubicada, cuando este cliente pide delivery.
   */
  protected readonly editandoDireccion = signal(false);
  protected readonly direccionEditada = signal('');
  protected readonly puntoEditado = signal<Punto | null>(null);

  // --- alta y correccion -----------------------------------------------------
  protected readonly formularioAbierto = signal(false);
  protected readonly modoFormulario = signal<ModoFormulario>('alta');
  protected readonly documento = signal('');
  protected readonly nombres = signal('');
  protected readonly apellidos = signal('');
  protected readonly celular = signal('');
  protected readonly correo = signal('');
  protected readonly tipo = signal('');
  /** Solo en el alta: al corregir, la direccion tiene su propia seccion con el mapa. */
  protected readonly direccionNueva = signal('');
  protected readonly puntoNuevo = signal<Punto | null>(null);
  private readonly tocados = signal<ReadonlySet<CampoCliente>>(new Set());

  /**
   * Documento, nombres y apellidos los exige el servidor; lo demas, solo si se
   * escribe, con formato. El celular se valida sin los espacios ni guiones con
   * que se suele dictar («956 781 234»): se guarda sin ellos.
   */
  private readonly errores = computed<Record<CampoCliente, ClaveI18n | null>>(() => ({
    documento: this.obligatorio(this.documento()) ?? problemaDe('documento', this.documento()),
    nombres: this.obligatorio(this.nombres()) ?? problemaDe('nombre', this.nombres()),
    apellidos: this.obligatorio(this.apellidos()) ?? problemaDe('nombre', this.apellidos()),
    celular: problemaDe('celular', this.celular().replace(/[\s().-]/g, '')),
    correo: problemaDe('correo', this.correo()),
  }));

  protected readonly tituloCajon = computed(() => {
    const c = this.cliente();
    return c ? this.nombreDe(c) : '';
  });

  protected readonly tituloFormulario = computed(() => {
    const c = this.cliente();
    return this.modoFormulario() === 'edicion' && c
      ? this.t('clientes.editarTitulo', { nombre: this.nombreDe(c) })
      : this.t('clientes.nuevo');
  });

  /** Cuanto lleva hacia el proximo cupon, en calificaciones. */
  protected readonly avance = computed(() => {
    const p = this.ficha()?.progreso;
    const requeridas = p?.calificacionesRequeridas ?? 0;
    if (!p || requeridas <= 0) return null;
    const hechas = requeridas - (p.calificacionesFaltantes ?? requeridas);
    return Math.max(0, Math.min(100, Math.round((hechas / requeridas) * 100)));
  });

  /** Cuanto lleva desde el nivel actual hacia el siguiente, en puntos. */
  protected readonly avanceNivel = computed(() => {
    const p = this.ficha()?.progreso;
    const siguiente = p?.nivelSiguiente?.puntosMinimos;
    if (!p || siguiente == null) return null;
    const desde = p.nivelActual?.puntosMinimos ?? 0;
    const tramo = siguiente - desde;
    if (tramo <= 0) return 0;
    const avance = ((p.puntosFidelidad ?? 0) - desde) / tramo;
    return Math.max(0, Math.min(100, Math.round(avance * 100)));
  });

  constructor() {
    this.consultas
      .pipe(
        map((q) => q.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) =>
          q.length < MINIMO_BUSQUEDA
            ? of(null)
            : this.clientesApi
                .buscarClientes({ q })
                .pipe(catchError(() => of([] as ClienteResponseDto[]))),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((encontrados) => this.encontrados.set(encontrados));
  }

  ngOnInit(): void {
    this.cargar();
  }

  // --- lista ----------------------------------------------------------------

  protected buscar(valor: string): void {
    this.busqueda.set(valor);
    this.consultas.next(valor);
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.clientesApi
      .listarClientes({ pageable: { page: this.pagina(), size: TAMANO_PAGINA } })
      .subscribe({
        next: (pagina) => {
          this.resultado.set(pagina);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
      });
  }

  protected irAPagina(pagina: number): void {
    const total = this.resultado()?.totalPaginas ?? 1;
    if (pagina < 0 || pagina >= total) return;
    this.pagina.set(pagina);
    this.cargar();
  }

  /** Los clientes que llegan por el bot no siempre dejan nombre. */
  protected nombreDe(c: ClienteResponseDto): string {
    return (
      c.nombreCompleto?.trim() ||
      [c.nombres, c.apellidos].filter(Boolean).join(' ').trim() ||
      this.t('clientes.sinNombre')
    );
  }

  // --- ficha ----------------------------------------------------------------

  protected abrir(c: ClienteResponseDto): void {
    if (!c.id) return;
    this.cliente.set(c);
    this.ficha.set(null);
    this.bloqueando.set(false);
    this.editandoDireccion.set(false);
    this.cajonAbierto.set(true);
    this.leerFicha(c.id);
  }

  private leerFicha(id: string): void {
    // Cada parte cae por su cuenta: sin empresas la ficha se pinta igual.
    forkJoin({
      progreso: this.fidelizacionApi.progreso({ clienteId: id }).pipe(catchError(() => of(null))),
      cupones: this.fidelizacionApi
        .cupones({ clienteId: id })
        .pipe(catchError(() => of([] as CuponResponseDto[]))),
      preferencias: this.clientesApi.preferencias({ id }).pipe(catchError(() => of(null))),
      empresas: this.clientesApi
        .empresas({ id })
        .pipe(catchError(() => of([] as EmpresaResponseDto[]))),
      ordenes: this.clientesApi.ordenes({ id }).pipe(catchError(() => of([] as OrdenResumenDto[]))),
    }).subscribe((ficha) =>
      this.ficha.set({
        ...ficha,
        ordenes: [...ficha.ordenes]
          .sort((a, b) => (b.tiempoInicioGlobal ?? '').localeCompare(a.tiempoInicioGlobal ?? ''))
          .slice(0, ORDENES_EN_FICHA),
      }),
    );
  }

  protected descuentoDe(cupon: CuponResponseDto): string {
    if (cupon.porcentajeDescuento) return `${cupon.porcentajeDescuento} %`;
    if (cupon.montoDescuento) return `S/ ${cupon.montoDescuento.toFixed(2)}`;
    return '';
  }

  protected verOrden(orden: OrdenResumenDto): void {
    this.cajonAbierto.set(false);
    void this.router.navigate(['/ordenes'], { queryParams: { orden: orden.id } });
  }

  protected alternarBloqueo(): void {
    this.bloqueando.update((v) => !v);
    this.motivoBloqueo.set('');
  }

  protected cambiarBloqueo(bloqueado: boolean): void {
    const c = this.cliente();
    const motivo = this.motivoBloqueo().trim();
    if (!c?.id || (bloqueado && !motivo) || this.guardando()) return;

    this.guardando.set(true);
    this.clientesApi.bloqueoFraude({ id: c.id, bloqueado, motivo: motivo || undefined }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.bloqueando.set(false);
        const actualizado = { ...c, bloqueadoPorFraude: bloqueado };
        this.cliente.set(actualizado);
        this.reemplazar(actualizado);
        this.avisos.exito(
          this.t(bloqueado ? 'clientes.avisoBloqueado' : 'clientes.avisoDesbloqueado', {
            nombre: this.nombreDe(c),
          }),
        );
      },
      error: () => this.guardando.set(false),
    });
  }

  protected editarDireccion(c: ClienteResponseDto): void {
    this.direccionEditada.set(c.direccionHabitual ?? '');
    this.puntoEditado.set(
      c.latitud != null && c.longitud != null ? { latitud: c.latitud, longitud: c.longitud } : null,
    );
    this.editandoDireccion.set(true);
  }

  /**
   * `PUT /clientes/{id}` reemplaza la ficha entera: se reenvia lo demas tal
   * como esta y solo cambian la direccion y su punto.
   */
  protected guardarDireccion(): void {
    const c = this.cliente();
    if (!c?.id || !c.dni || !c.nombres || !c.apellidos || this.guardando()) return;

    const punto = this.puntoEditado();
    this.guardando.set(true);
    this.clientesApi
      .actualizarCliente({
        id: c.id,
        clienteRequestDto: {
          dni: c.dni,
          nombres: c.nombres,
          apellidos: c.apellidos,
          correo: c.correo,
          celular: c.celular,
          tipoCliente: c.tipoCliente,
          direccionHabitual: this.direccionEditada().trim() || undefined,
          latitud: punto?.latitud,
          longitud: punto?.longitud,
        },
      })
      .subscribe({
        next: (actualizado) => {
          this.guardando.set(false);
          this.editandoDireccion.set(false);
          this.cliente.set(actualizado);
          this.reemplazar(actualizado);
          this.avisos.exito(
            this.t('clientes.avisoDireccion', { nombre: this.nombreDe(actualizado) }),
          );
        },
        error: () => this.guardando.set(false),
      });
  }

  // --- alta y correccion -----------------------------------------------------

  protected abrirAlta(): void {
    this.llenarFormulario(null);
    this.direccionNueva.set('');
    this.puntoNuevo.set(null);
    this.modoFormulario.set('alta');
    this.formularioAbierto.set(true);
  }

  /** Corregir se abre encima de la ficha: al cerrar, la ficha sigue ahi. */
  protected abrirEdicion(): void {
    const c = this.cliente();
    if (!c) return;
    this.llenarFormulario(c);
    this.bloqueando.set(false);
    this.editandoDireccion.set(false);
    this.modoFormulario.set('edicion');
    this.formularioAbierto.set(true);
  }

  protected errorDe(campo: CampoCliente): string | null {
    const clave = this.errores()[campo];
    return clave && this.tocados().has(campo) ? this.t(clave) : null;
  }

  protected marcarTocado(campo: CampoCliente): void {
    this.tocados.update((antes) => new Set(antes).add(campo));
  }

  /**
   * El `PUT` reemplaza la ficha entera, asi que al corregir se reenvia la
   * direccion tal como esta. El celular se guarda como lo traen los bots
   * (`51` y nueve cifras), para que el mismo cliente escrito a mano y
   * escribiendo por el bot se encuentren por su numero.
   */
  protected guardarFormulario(): void {
    if (this.guardando()) return;

    // Se comprueba todo otra vez: se puede llegar al boton sin salir de un campo mal escrito.
    const errores = this.errores();
    if (Object.values(errores).some((e) => e !== null)) {
      this.tocados.set(new Set(Object.keys(errores) as CampoCliente[]));
      const faltan = [this.documento(), this.nombres(), this.apellidos()].some((v) => !v.trim());
      this.avisos.error(this.t(faltan ? 'clientes.avisoFaltanDatos' : 'validacion.revisa'));
      return;
    }

    const datos: ClienteRequestDto = {
      dni: this.documento().trim(),
      nombres: this.nombres().trim(),
      apellidos: this.apellidos().trim(),
      celular: normalizarCelular(this.celular()) || undefined,
      correo: this.correo().trim() || undefined,
      tipoCliente: this.tipo().trim() || undefined,
    };

    const editando = this.modoFormulario() === 'edicion' ? this.cliente() : null;
    if (this.modoFormulario() === 'edicion' && !editando?.id) return;
    const punto = this.puntoNuevo();

    this.guardando.set(true);
    const peticion = editando?.id
      ? this.clientesApi.actualizarCliente({
          id: editando.id,
          clienteRequestDto: {
            ...datos,
            direccionHabitual: editando.direccionHabitual,
            latitud: editando.latitud,
            longitud: editando.longitud,
          },
        })
      : this.clientesApi.crearCliente({
          clienteRequestDto: {
            ...datos,
            direccionHabitual: this.direccionNueva().trim() || undefined,
            latitud: punto?.latitud,
            longitud: punto?.longitud,
          },
        });

    peticion.subscribe({
      next: (guardado) => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t(editando ? 'clientes.avisoEditado' : 'clientes.avisoCreado', {
            nombre: this.nombreDe(guardado),
          }),
        );
        if (editando) {
          this.cliente.set(guardado);
          this.reemplazar(guardado);
        } else {
          this.cargar();
          this.abrir(guardado);
        }
      },
      error: () => this.guardando.set(false),
    });
  }

  private llenarFormulario(c: ClienteResponseDto | null): void {
    this.documento.set(c?.dni ?? '');
    this.nombres.set(c?.nombres ?? '');
    this.apellidos.set(c?.apellidos ?? '');
    // Se ve agrupado, como en la ficha; al guardar vuelve a la forma de los bots.
    this.celular.set(formatoTelefono(c?.celular));
    this.correo.set(c?.correo ?? '');
    this.tipo.set(c?.tipoCliente ?? '');
    this.tocados.set(new Set());
  }

  private obligatorio(valor: string): ClaveI18n | null {
    return valor.trim() ? null : 'validacion.obligatorio';
  }

  private reemplazar(c: ClienteResponseDto): void {
    const cambiar = (lista: ClienteResponseDto[]) => lista.map((x) => (x.id === c.id ? c : x));
    this.encontrados.update((lista) => (lista ? cambiar(lista) : lista));
    this.resultado.update((p) => (p ? { ...p, contenido: cambiar(p.contenido ?? []) } : p));
  }
}
