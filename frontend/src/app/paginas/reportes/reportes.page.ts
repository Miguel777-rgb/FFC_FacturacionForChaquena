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
  FeedbackYFidelizacionApi,
  ReportesApi,
  SerieVentasDtoGranularidadEnum,
  type ProductoTopDto,
  type ReporteSatisfaccionDto,
  type SerieVentasDto,
  type TableroDto,
  type VentasPorMozoDto,
} from '../../api';
import { Icono } from '../../disenio/icono';
import { fechaIsoLocal, inicioDelDia } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import type { Rol } from '../../nucleo/sesion/rol';
import { SesionService } from '../../nucleo/sesion/sesion.service';

/** Los tres rangos que se miran de verdad: el turno, la semana y el mes, contando hoy. */
type Rango = 'hoy' | 'semana' | 'mes';

const RANGOS: ReadonlyArray<{ id: Rango; nombre: ClaveI18n; dias: number }> = [
  { id: 'hoy', nombre: 'kpis.hoy', dias: 0 },
  { id: 'semana', nombre: 'kpis.semana', dias: 6 },
  { id: 'mes', nombre: 'kpis.mes', dias: 29 },
];

type Pestana = 'resumen' | 'mozos' | 'satisfaccion';

/** La satisfaccion es solo del administrador: `/reportes/satisfaccion` no admite a caja. */
const PESTANAS: ReadonlyArray<{ id: Pestana; nombre: ClaveI18n; roles: Rol[] }> = [
  { id: 'resumen', nombre: 'reportes.resumen', roles: ['ADMIN', 'CAJA'] },
  { id: 'mozos', nombre: 'reportes.porMozo', roles: ['ADMIN', 'CAJA'] },
  { id: 'satisfaccion', nombre: 'reportes.satisfaccion', roles: ['ADMIN'] },
];

/** Una barra ya resuelta a coordenadas, para que la plantilla no calcule nada. */
interface Barra {
  x: number;
  y: number;
  alto: number;
  ancho: number;
  etiqueta: string;
  titulo: string;
}

const GRAFICA = { ancho: 720, alto: 160, hueco: 2 };

/**
 * Ventas y reportes: lo que paso en un rango, para compararlo.
 *
 * Lo que esta pasando ahora —comandas abiertas, stock bajo minimo, cobros sin
 * acreditar— vive en el tablero, que es por donde entran el administrador y la
 * caja. Aqui queda la historia: cuanto se vendio, en que horas, que platos y
 * quien los vendio, y que opinaron los comensales.
 *
 * Las consultas se piden juntas y ninguna tumba a las otras: si el ranking de
 * mozos falla, el resumen se pinta igual.
 */
@Component({
  selector: 'app-reportes',
  imports: [DecimalPipe, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './reportes.page.html',
  styleUrls: ['../../disenio/secciones.scss', './reportes.page.scss'],
})
export class ReportesPage implements OnInit {
  private readonly reportesApi = inject(ReportesApi);
  private readonly fidelizacionApi = inject(FeedbackYFidelizacionApi);
  private readonly sesion = inject(SesionService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly RANGOS = RANGOS;
  protected readonly GRAFICA = GRAFICA;

  protected readonly pestanas = computed(() =>
    PESTANAS.filter((p) => this.sesion.tieneAlgunRol(p.roles)),
  );
  private readonly esAdmin = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));

  protected readonly pestana = signal<Pestana>('resumen');
  protected readonly cargando = signal(true);
  protected readonly rango = signal<Rango>('hoy');

  protected readonly tablero = signal<TableroDto | null>(null);
  protected readonly serie = signal<SerieVentasDto | null>(null);
  protected readonly mozos = signal<VentasPorMozoDto[]>([]);
  protected readonly top = signal<ProductoTopDto[]>([]);
  protected readonly satisfaccion = signal<ReporteSatisfaccionDto | null>(null);

  /** Cuanto se pasa la cocina de su propio objetivo. Negativo significa que va sobrada. */
  protected readonly desvioCocina = computed(() => {
    const op = this.tablero()?.operacion;
    if (op?.minutosPromedioCocina == null || op.minutosObjetivoCocina == null) return null;
    return op.minutosPromedioCocina - op.minutosObjetivoCocina;
  });

  /**
   * La serie convertida en barras.
   *
   * El maximo se calcula sobre los puntos y no se fija: un dia flojo tiene que
   * llenar la grafica igual que uno bueno, porque lo que se lee aqui es la forma
   * del servicio —donde estan los picos—. Cuando no se vendio nada, no se dibuja
   * nada: una fila de barras de altura cero solo simula que hay datos.
   */
  protected readonly barras = computed<Barra[]>(() => {
    const puntos = this.serie()?.puntos ?? [];
    if (puntos.length === 0) return [];

    const maximo = Math.max(...puntos.map((p) => p.total ?? 0));
    if (maximo <= 0) return [];

    const ancho = GRAFICA.ancho / puntos.length;
    const porHora = this.serie()?.granularidad === SerieVentasDtoGranularidadEnum.HORA;

    return puntos.map((p, i) => {
      const alto = Math.max(((p.total ?? 0) / maximo) * GRAFICA.alto, p.total ? 2 : 0);
      const inicio = p.inicio ? new Date(p.inicio) : null;
      const etiqueta = inicio
        ? porHora
          ? `${String(inicio.getHours()).padStart(2, '0')}h`
          : `${inicio.getDate()}/${inicio.getMonth() + 1}`
        : '';
      return {
        x: i * ancho,
        y: GRAFICA.alto - alto,
        alto,
        ancho: Math.max(ancho - GRAFICA.hueco, 1),
        etiqueta,
        titulo: this.i18n.tp('kpis.barra', p.comandas ?? 0, {
          etiqueta,
          total: (p.total ?? 0).toFixed(2),
        }),
      };
    });
  });

  /**
   * Solo se rotulan algunas barras. Con treinta dias en 720 pixeles las
   * etiquetas se solapan y dejan de leerse; se marca una de cada n para que el
   * eje siga diciendo donde esta uno.
   */
  protected readonly pasoEtiquetas = computed(() => {
    const total = this.barras().length;
    return total <= 12 ? 1 : Math.ceil(total / 12);
  });

  protected readonly totalSerie = computed(() =>
    (this.serie()?.puntos ?? []).reduce((suma, p) => suma + (p.total ?? 0), 0),
  );

  /**
   * Los mozos de mas a menos venta, con su parte del total. No es un ranking
   * para premiar a nadie: sirve para ver si un turno se sostuvo sobre una sola
   * persona.
   */
  protected readonly mozosConParte = computed(() => {
    const lista = [...this.mozos()].sort((a, b) => (b.total ?? 0) - (a.total ?? 0));
    const total = lista.reduce((suma, m) => suma + (m.total ?? 0), 0);
    return lista.map((m) => ({
      ...m,
      parte: total > 0 ? Math.round(((m.total ?? 0) / total) * 100) : 0,
    }));
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cambiarRango(id: Rango): void {
    if (this.rango() === id) return;
    this.rango.set(id);
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    const dias = RANGOS.find((r) => r.id === this.rango())?.dias ?? 0;
    const rangoIso = { desde: fechaIsoLocal(inicioDelDia(dias)), hasta: fechaIsoLocal(new Date()) };
    const granularidad =
      dias === 0 ? SerieVentasDtoGranularidadEnum.HORA : SerieVentasDtoGranularidadEnum.DIA;

    forkJoin({
      tablero: this.reportesApi.tableroLocal(rangoIso).pipe(catchError(() => of(null))),
      serie: this.reportesApi
        .serieVentas({ ...rangoIso, granularidad })
        .pipe(catchError(() => of(null))),
      mozos: this.reportesApi
        .ventasPorMozo(rangoIso)
        .pipe(catchError(() => of([] as VentasPorMozoDto[]))),
      top: this.reportesApi
        .productosTop({ ...rangoIso, limite: 8 })
        .pipe(catchError(() => of([] as ProductoTopDto[]))),
      // A caja no se le pide: el servidor le responderia 403.
      satisfaccion: this.esAdmin()
        ? this.fidelizacionApi.satisfaccion(rangoIso).pipe(catchError(() => of(null)))
        : of(null),
    }).subscribe({
      next: ({ tablero, serie, mozos, top, satisfaccion }) => {
        this.tablero.set(tablero);
        this.serie.set(serie);
        this.mozos.set(mozos);
        this.top.set(top);
        this.satisfaccion.set(satisfaccion);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
