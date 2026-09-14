import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, forkJoin, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';

import {
  ClientesApi,
  FeedbackYFidelizacionApi,
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
import { AvisosService } from '../../nucleo/http/avisos.service';
import { codigoDeOrden } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { SesionService } from '../../nucleo/sesion/sesion.service';

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
 */
@Component({
  selector: 'app-clientes',
  imports: [DecimalPipe, Icono, Dialogo],
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

  protected readonly tituloCajon = computed(() => {
    const c = this.cliente();
    return c ? this.nombreDe(c) : '';
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
      ordenes: this.clientesApi
        .ordenes({ id })
        .pipe(catchError(() => of([] as OrdenResumenDto[]))),
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
    this.clientesApi
      .bloqueoFraude({ id: c.id, bloqueado, motivo: motivo || undefined })
      .subscribe({
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

  private reemplazar(c: ClienteResponseDto): void {
    const cambiar = (lista: ClienteResponseDto[]) => lista.map((x) => (x.id === c.id ? c : x));
    this.encontrados.update((lista) => (lista ? cambiar(lista) : lista));
    this.resultado.update((p) => (p ? { ...p, contenido: cambiar(p.contenido ?? []) } : p));
  }
}
