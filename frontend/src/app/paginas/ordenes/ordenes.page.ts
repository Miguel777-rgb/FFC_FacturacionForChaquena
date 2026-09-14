import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  CambioEstadoRequestDtoEstadoEnum,
  ComandasApi,
  OrdenResponseDtoTransicionesPermitidasEnum,
  type BuscarOrdenesRequestParams,
  type OrdenResponseDto,
  type OrdenResumenDto,
  type PageResponseDtoOrdenResumenDto,
  type TicketCocinaDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import {
  codigoDeOrden,
  fechaIsoLocal,
  formatearDuracion,
  inicioDelDia,
} from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';

type Rango = 'hoy' | 'semana' | 'mes';
type Estado = NonNullable<BuscarOrdenesRequestParams['estado']>;
type Tipo = NonNullable<BuscarOrdenesRequestParams['tipoOrden']>;

const RANGOS: ReadonlyArray<{ id: Rango; nombre: ClaveI18n; dias: number }> = [
  { id: 'hoy', nombre: 'kpis.hoy', dias: 0 },
  { id: 'semana', nombre: 'kpis.semana', dias: 6 },
  { id: 'mes', nombre: 'kpis.mes', dias: 29 },
];

const ESTADOS: readonly Estado[] = [
  'ENCOLADO',
  'EN_PREPARACION',
  'EN_DESPACHO',
  'ENTREGADO',
  'PAGADO',
  'CONCLUIDO',
  'CANCELADO',
  'FRAUDULENTO',
];

const TIPOS: readonly Tipo[] = ['MESA', 'RETIRO_LOCAL', 'DELIVERY'];

/**
 * Los pasos que se pueden dar desde aqui. Cobrar (PAGADO) y marcar fraude son
 * de la caja, que es donde se registra el dinero; cancelar va aparte porque
 * pide motivo.
 */
const PASOS_MANUALES: readonly OrdenResponseDtoTransicionesPermitidasEnum[] = [
  OrdenResponseDtoTransicionesPermitidasEnum.EN_PREPARACION,
  OrdenResponseDtoTransicionesPermitidasEnum.EN_DESPACHO,
  OrdenResponseDtoTransicionesPermitidasEnum.ENTREGADO,
  OrdenResponseDtoTransicionesPermitidasEnum.CONCLUIDO,
];

const TAMANO_PAGINA = 20;
const REFRESCO_MS = 30_000;

/**
 * Ordenes: todas las comandas, no solo las abiertas.
 *
 * El POS y la caja trabajan sobre lo que esta pasando ahora; esta pantalla
 * responde a «que paso con la comanda de la mesa 5 de hace una hora». Filtra en
 * el servidor por rango, estado y tipo, y pagina alli; la busqueda por mesa,
 * cliente o codigo se hace sobre la pagina ya cargada, que es donde se mira.
 *
 * El detalle se abre en un cajon y no en otra pantalla: se consulta una orden
 * sin perder la lista ni los filtros. Los gestos que ofrece salen de
 * `transicionesPermitidas`, que calcula el servidor con la misma tabla con la
 * que valida: un boton de aqui no puede terminar en un 409.
 */
@Component({
  selector: 'app-ordenes',
  imports: [DecimalPipe, Icono, Dialogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './ordenes.page.html',
  styleUrls: ['../../disenio/secciones.scss', './ordenes.page.scss'],
})
export class OrdenesPage implements OnInit {
  private readonly comandasApi = inject(ComandasApi);
  private readonly avisos = inject(AvisosService);
  private readonly route = inject(ActivatedRoute);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly fecha = this.i18n.fecha;
  protected readonly duracion = formatearDuracion;
  protected readonly codigo = codigoDeOrden;

  protected readonly RANGOS = RANGOS;
  protected readonly ESTADOS = ESTADOS;
  protected readonly TIPOS = TIPOS;

  protected readonly cargando = signal(true);
  protected readonly rango = signal<Rango>('hoy');
  protected readonly estado = signal<Estado | ''>('');
  protected readonly tipo = signal<Tipo | ''>('');
  protected readonly busqueda = signal('');
  protected readonly pagina = signal(0);
  protected readonly resultado = signal<PageResponseDtoOrdenResumenDto | null>(null);

  // --- detalle --------------------------------------------------------------
  protected readonly cajonAbierto = signal(false);
  protected readonly resumen = signal<OrdenResumenDto | null>(null);
  protected readonly detalle = signal<OrdenResponseDto | null>(null);
  protected readonly ticket = signal<TicketCocinaDto | null>(null);
  protected readonly cancelando = signal(false);
  protected readonly motivo = signal('');
  protected readonly reponer = signal(true);
  protected readonly guardando = signal(false);

  protected readonly visibles = computed(() => {
    const lista = this.resultado()?.contenido ?? [];
    const q = this.busqueda().trim().toLowerCase();
    if (!q) return lista;
    return lista.filter((o) =>
      [this.codigo(o.id), o.mesaNumero, o.clienteNombre]
        .filter(Boolean)
        .some((v) => String(v).toLowerCase().includes(q)),
    );
  });

  protected readonly tituloCajon = computed(() => {
    const o = this.detalle() ?? this.resumen();
    if (!o) return this.t('ordenes.cargandoDetalle');
    return this.t('ordenes.tituloDetalle', { codigo: this.codigo(o.id), destino: this.destino(o) });
  });

  protected readonly pasos = computed(() => {
    const permitidas = this.detalle()?.transicionesPermitidas ?? [];
    return PASOS_MANUALES.filter((p) => permitidas.includes(p));
  });

  protected readonly sePuedeCancelar = computed(
    () =>
      this.detalle()?.transicionesPermitidas?.includes(
        OrdenResponseDtoTransicionesPermitidasEnum.CANCELADO,
      ) ?? false,
  );

  /** Los hitos de la comanda en el orden en que ocurren. Un nulo aun no paso. */
  protected readonly hitos = computed(() => {
    const d = this.detalle();
    if (!d) return [];
    const lista: Array<{ clave: ClaveI18n; valor?: string }> = [
      { clave: 'ordenes.hitoRecibida', valor: d.tiempoInicioGlobal },
      { clave: 'ordenes.hitoCocina', valor: d.tiempoInicioCocina },
      { clave: 'ordenes.hitoListo', valor: d.tiempoCierrePlatillo },
      { clave: 'ordenes.hitoDespachada', valor: d.tiempoCierreDespacho },
      { clave: 'ordenes.hitoCerrada', valor: d.tiempoFinGlobal },
    ];
    return lista;
  });

  constructor() {
    interval(REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));
  }

  ngOnInit(): void {
    this.cargar();

    // Desde Mesas se llega con `?orden=`: la comanda se abre encima de la lista.
    const id = this.route.snapshot.queryParamMap.get('orden');
    if (id) this.abrirPorId(id);
  }

  // --- lista ----------------------------------------------------------------

  protected cambiarRango(id: Rango): void {
    if (this.rango() === id) return;
    this.rango.set(id);
    this.pagina.set(0);
    this.cargar();
  }

  protected filtrarEstado(valor: string): void {
    this.estado.set(valor as Estado | '');
    this.pagina.set(0);
    this.cargar();
  }

  protected filtrarTipo(valor: string): void {
    this.tipo.set(valor as Tipo | '');
    this.pagina.set(0);
    this.cargar();
  }

  protected irAPagina(pagina: number): void {
    const total = this.resultado()?.totalPaginas ?? 1;
    if (pagina < 0 || pagina >= total) return;
    this.pagina.set(pagina);
    this.cargar();
  }

  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);

    const dias = RANGOS.find((r) => r.id === this.rango())?.dias ?? 0;
    this.comandasApi
      .buscarOrdenes({
        pageable: { page: this.pagina(), size: TAMANO_PAGINA, sort: ['dateCreated,desc'] },
        estado: this.estado() || undefined,
        tipoOrden: this.tipo() || undefined,
        desde: fechaIsoLocal(inicioDelDia(dias)),
        hasta: fechaIsoLocal(new Date()),
      })
      .subscribe({
        next: (pagina) => {
          this.resultado.set(pagina);
          this.cargando.set(false);
        },
        error: () => this.cargando.set(false),
      });
  }

  // --- detalle --------------------------------------------------------------

  protected abrir(orden: OrdenResumenDto): void {
    if (!orden.id) return;
    this.resumen.set(orden);
    this.abrirPorId(orden.id);
  }

  private abrirPorId(id: string): void {
    this.detalle.set(null);
    this.ticket.set(null);
    this.cancelando.set(false);
    this.cajonAbierto.set(true);
    this.leerDetalle(id);
  }

  private leerDetalle(id: string): void {
    forkJoin({
      detalle: this.comandasApi.obtenerOrden({ id }),
      ticket: this.comandasApi.ticket({ id }).pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ detalle, ticket }) => {
        this.detalle.set(detalle);
        this.ticket.set(ticket);
      },
    });
  }

  protected pasarA(estado: OrdenResponseDtoTransicionesPermitidasEnum): void {
    const orden = this.detalle();
    if (!orden?.id || this.guardando()) return;

    this.guardando.set(true);
    this.comandasApi
      .cambiarEstadoOrden({
        id: orden.id,
        cambioEstadoRequestDto: {
          estado:
            CambioEstadoRequestDtoEstadoEnum[estado as keyof typeof CambioEstadoRequestDtoEstadoEnum],
        },
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.avisos.exito(
            this.t('ordenes.avisoEstado', {
              codigo: this.codigo(orden.id),
              estado: this.tEnum('estado', estado),
            }),
          );
          this.leerDetalle(orden.id!);
          this.cargar(true);
        },
        error: () => this.guardando.set(false),
      });
  }

  protected alternarCancelacion(): void {
    this.cancelando.update((v) => !v);
    this.motivo.set('');
    this.reponer.set(true);
  }

  protected cancelar(): void {
    const orden = this.detalle();
    const motivo = this.motivo().trim();
    if (!orden?.id || !motivo || this.guardando()) return;

    this.guardando.set(true);
    this.comandasApi
      .cancelar({ id: orden.id, cancelarOrdenRequestDto: { motivo, reponerStock: this.reponer() } })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cancelando.set(false);
          this.avisos.exito(this.t('ordenes.avisoCancelada', { codigo: this.codigo(orden.id) }));
          this.leerDetalle(orden.id!);
          this.cargar(true);
        },
        error: () => this.guardando.set(false),
      });
  }

  // --- formato --------------------------------------------------------------

  /** «Mesa 5» si es de mesa; si no, el tipo: «Delivery», «Retiro en local». */
  protected destino(orden: { tipoOrden?: string; mesaNumero?: string }): string {
    return orden.tipoOrden === 'MESA' && orden.mesaNumero
      ? this.t('comun.mesa', { numero: orden.mesaNumero })
      : this.tEnum('tipoOrden', orden.tipoOrden);
  }
}
