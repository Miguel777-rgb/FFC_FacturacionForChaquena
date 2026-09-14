import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Icono } from '../../disenio/icono';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  ReportesApi,
  SerieVentasDtoGranularidadEnum,
  type ProductoTopDto,
  type SerieVentasDto,
  type TableroDto,
  type VentasPorMozoDto,
} from '../../api';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';

/** Los tres rangos que se miran de verdad: el turno, la semana y el mes. */
type Rango = 'hoy' | 'semana' | 'mes';

const RANGOS: ReadonlyArray<{ id: Rango; nombre: ClaveI18n; dias: number }> = [
  { id: 'hoy', nombre: 'kpis.hoy', dias: 0 },
  { id: 'semana', nombre: 'kpis.semana', dias: 7 },
  { id: 'mes', nombre: 'kpis.mes', dias: 30 },
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
 * KPIs: lo primero que se abre por la manana y lo ultimo que se mira al cerrar.
 *
 * Separa dos clases de cifra que no se leen igual. Arriba, lo que paso en el
 * rango elegido —cuanto se vendio, cuanto tardo la cocina—, que es historia y
 * se compara. Debajo, lo que esta pasando ahora mismo —comandas abiertas, stock
 * bajo minimo, cobros sin acreditar—, que no depende del rango y no se compara
 * con nada: o esta en cero o hay que ir a hacer algo.
 *
 * Las cuatro consultas se piden juntas y ninguna tumba a las otras: si el
 * ranking de mozos falla, la pantalla se pinta igual sin esa tabla. Una
 * superficie de indicadores que se queda en blanco por un indicador es peor que
 * una a la que le falta uno.
 *
 * Es una superficie propia y no una pestana de la trastienda porque no es
 * trabajo de almacen: quien mira esto decide sobre el local entero, y llegar
 * hasta el dato no puede costar dos clics dentro de otra pantalla.
 */
@Component({
  selector: 'app-kpis',
  imports: [DecimalPipe, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kpis.page.html',
  styleUrls: ['../../disenio/secciones.scss', './kpis.page.scss'],
})
export class KpisPage implements OnInit {
  private readonly reportesApi = inject(ReportesApi);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly RANGOS = RANGOS;
  protected readonly GRAFICA = GRAFICA;

  protected readonly cargando = signal(true);
  protected readonly rango = signal<Rango>('hoy');

  protected readonly tablero = signal<TableroDto | null>(null);
  protected readonly serie = signal<SerieVentasDto | null>(null);
  protected readonly mozos = signal<VentasPorMozoDto[]>([]);
  protected readonly top = signal<ProductoTopDto[]>([]);

  /**
   * Cuantas cosas piden atencion ahora mismo. Es la unica cifra que se lee sin
   * pensar: si es cero, el bloque de abajo se puede ignorar entero.
   */
  protected readonly pendientes = computed(() => {
    const a = this.tablero()?.ahoraMismo;
    if (!a) return 0;
    return (
      (a.insumosBajoMinimo ?? 0) +
      (a.pagosPorAcreditar ?? 0) +
      (a.alertasDeFraude ?? 0) +
      (a.eventosOutboxEnError ?? 0)
    );
  });

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
   * del servicio —donde estan los picos—, no la comparacion con el mes pasado.
   * Cuando no se vendio nada, no se dibuja nada: una fila de barras de altura
   * cero solo simula que hay datos.
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
    const hasta = new Date();
    const desde = new Date(hasta);
    if (dias === 0) {
      desde.setHours(0, 0, 0, 0);
    } else {
      desde.setDate(desde.getDate() - dias);
      desde.setHours(0, 0, 0, 0);
    }

    const rangoIso = { desde: isoConZona(desde), hasta: isoConZona(hasta) };
    const granularidad =
      dias === 0 ? SerieVentasDtoGranularidadEnum.HORA : SerieVentasDtoGranularidadEnum.DIA;

    // Cada consulta cae por su cuenta: el tablero se pinta con lo que llegue.
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
    }).subscribe({
      next: ({ tablero, serie, mozos, top }) => {
        this.tablero.set(tablero);
        this.serie.set(serie);
        this.mozos.set(mozos);
        this.top.set(top);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}

/**
 * Fecha en ISO pero con el desfase local, no en Z.
 *
 * `toISOString()` da el instante correcto en UTC, y con eso el servidor agrupa
 * por horas de Greenwich: en Lima la cena del sabado aparece repartida entre el
 * sabado y el domingo. Mandando el desfase, el dia empieza y termina donde lo
 * vive el local.
 */
function isoConZona(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  const desfase = -fecha.getTimezoneOffset();
  const signo = desfase >= 0 ? '+' : '-';
  const horas = dos(Math.floor(Math.abs(desfase) / 60));
  const minutos = dos(Math.abs(desfase) % 60);

  return (
    `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}` +
    `T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}:${dos(fecha.getSeconds())}` +
    `${signo}${horas}:${minutos}`
  );
}
