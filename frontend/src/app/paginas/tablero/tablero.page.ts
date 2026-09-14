import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  ReportesApi,
  SerieVentasDtoGranularidadEnum,
  type ProductoTopDto,
  type SerieVentasDto,
  type TableroDto,
} from '../../api';
import { Icono } from '../../disenio/icono';
import type { NombreIcono } from '../../disenio/iconos';
import { fechaIsoLocal, inicioDelDia } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import type { Rol } from '../../nucleo/sesion/rol';
import { SesionService } from '../../nucleo/sesion/sesion.service';

/** Un tablero que se mira de pasada: se refresca solo cada minuto. */
const REFRESCO_MS = 60_000;

const GRAFICA = { ancho: 640, alto: 160, margen: 24 };

interface Pendiente {
  clave: ClaveI18n;
  valor: number;
  /** Para las mesas: «2 de 12». */
  de?: number;
  ruta: string;
  roles: Rol[];
  icono: NombreIcono;
  /** Si pasa de cero es algo que hay que ir a resolver, no solo una cifra. */
  alerta: boolean;
}

interface Comparacion {
  clave: ClaveI18n;
  pct: number;
  sentido: 'sube' | 'baja' | 'igual';
}

/**
 * Tablero: la primera pantalla del administrador y de la caja.
 *
 * Tres preguntas en el orden en que se hacen al llegar: como va el dia, que hay
 * que resolver ahora, y como va la semana.
 *
 * La comparacion con ayer es contra ayer **a esta misma hora**, no contra el
 * dia entero: a las once de la manana cualquier dia pierde contra la cena de
 * ayer, y un «−80 %» que no significa nada es peor que no comparar. Si ayer no
 * se vendio nada no se compara.
 *
 * Cada cifra pendiente lleva a la pantalla donde se resuelve, pero solo si el
 * rol puede entrar: la caja ve los insumos bajo minimo como dato, sin un enlace
 * que la mande a un 403.
 */
@Component({
  selector: 'app-tablero',
  imports: [DecimalPipe, RouterLink, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tablero.page.html',
  styleUrl: './tablero.page.scss',
})
export class TableroPage implements OnInit {
  private readonly reportesApi = inject(ReportesApi);
  private readonly sesion = inject(SesionService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly GRAFICA = GRAFICA;

  protected readonly cargando = signal(true);
  protected readonly hoy = signal<TableroDto | null>(null);
  protected readonly ayer = signal<TableroDto | null>(null);
  protected readonly semana = signal<SerieVentasDto | null>(null);
  protected readonly top = signal<ProductoTopDto[]>([]);

  protected readonly comparacionVentas = computed(() =>
    this.comparar(this.hoy()?.ventas?.total, this.ayer()?.ventas?.total),
  );

  protected readonly comparacionComandas = computed(() =>
    this.comparar(this.hoy()?.ventas?.comandas, this.ayer()?.ventas?.comandas),
  );

  protected readonly cocinaFuera = computed(() => {
    const op = this.hoy()?.operacion;
    return (
      op?.minutosPromedioCocina != null &&
      op.minutosObjetivoCocina != null &&
      op.minutosPromedioCocina > op.minutosObjetivoCocina
    );
  });

  protected readonly pendientes = computed(() => {
    const a = this.hoy()?.ahoraMismo;
    if (!a) return [];

    const todos: Pendiente[] = [
      {
        clave: 'tablero.comandasAbiertas',
        valor: a.comandasAbiertas ?? 0,
        ruta: '/ordenes',
        roles: ['ADMIN', 'MOZO', 'CAJA'],
        icono: 'ordenes',
        alerta: false,
      },
      {
        clave: 'tablero.mesasOcupadas',
        valor: a.mesasOcupadas ?? 0,
        de: a.mesasActivas ?? 0,
        ruta: '/mesas',
        roles: ['ADMIN', 'MOZO', 'CAJA'],
        icono: 'mesas',
        alerta: false,
      },
      {
        clave: 'tablero.pagosPorAcreditar',
        valor: a.pagosPorAcreditar ?? 0,
        ruta: '/caja',
        roles: ['CAJA', 'ADMIN'],
        icono: 'caja',
        alerta: true,
      },
      {
        clave: 'tablero.alertasFraude',
        valor: a.alertasDeFraude ?? 0,
        ruta: '/caja',
        roles: ['CAJA', 'ADMIN'],
        icono: 'alerta',
        alerta: true,
      },
      {
        clave: 'tablero.insumosBajoMinimo',
        valor: a.insumosBajoMinimo ?? 0,
        ruta: '/inventario',
        roles: ['ALMACEN', 'ADMIN'],
        icono: 'inventario',
        alerta: true,
      },
      {
        clave: 'tablero.eventosError',
        valor: a.eventosOutboxEnError ?? 0,
        ruta: '/configuracion',
        roles: ['ADMIN'],
        icono: 'configuracion',
        alerta: true,
      },
    ];

    return todos.map((p) => ({ ...p, enlace: this.sesion.tieneAlgunRol(p.roles) }));
  });

  /**
   * La semana como area. El maximo sale de los puntos: lo que se lee es la
   * forma de la semana, no la comparacion con otra.
   */
  protected readonly grafica = computed(() => {
    const puntos = this.semana()?.puntos ?? [];
    const maximo = Math.max(0, ...puntos.map((p) => p.total ?? 0));
    if (puntos.length === 0 || maximo <= 0) return null;

    const { ancho, alto, margen } = GRAFICA;
    const paso = puntos.length > 1 ? (ancho - margen * 2) / (puntos.length - 1) : 0;

    const coords = puntos.map((p, i) => {
      const inicio = p.inicio ? new Date(p.inicio) : null;
      return {
        x: margen + i * paso,
        y: 8 + (alto - 16) * (1 - (p.total ?? 0) / maximo),
        total: p.total ?? 0,
        etiqueta: inicio ? `${inicio.getDate()}/${inicio.getMonth() + 1}` : '',
      };
    });

    const linea = coords
      .map((c, i) => `${i === 0 ? 'M' : 'L'}${c.x.toFixed(1)} ${c.y.toFixed(1)}`)
      .join(' ');
    const ultimo = coords[coords.length - 1];
    const area = `${linea} L${ultimo.x.toFixed(1)} ${alto} L${coords[0].x.toFixed(1)} ${alto} Z`;
    const total = puntos.reduce((suma, p) => suma + (p.total ?? 0), 0);

    return { coords, linea, area, total };
  });

  /** Lo mas vendido con el largo de su barra, relativo al primero. */
  protected readonly topConBarra = computed(() => {
    const lista = this.top();
    const maximo = Math.max(0, ...lista.map((p) => p.montoTotal ?? 0));
    return lista.map((p) => ({ ...p, pct: maximo > 0 ? ((p.montoTotal ?? 0) / maximo) * 100 : 0 }));
  });

  constructor() {
    interval(REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));
  }

  ngOnInit(): void {
    this.cargar();
  }

  /** El refresco automatico no vacia la pantalla: solo reemplaza cuando llega. */
  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);

    const ahora = new Date();
    const ayerMismaHora = new Date(ahora);
    ayerMismaHora.setDate(ayerMismaHora.getDate() - 1);
    const hoy = { desde: fechaIsoLocal(inicioDelDia()), hasta: fechaIsoLocal(ahora) };

    forkJoin({
      hoy: this.reportesApi.tableroLocal(hoy).pipe(catchError(() => of(null))),
      ayer: this.reportesApi
        .tableroLocal({ desde: fechaIsoLocal(inicioDelDia(1)), hasta: fechaIsoLocal(ayerMismaHora) })
        .pipe(catchError(() => of(null))),
      semana: this.reportesApi
        .serieVentas({
          desde: fechaIsoLocal(inicioDelDia(6)),
          hasta: fechaIsoLocal(ahora),
          granularidad: SerieVentasDtoGranularidadEnum.DIA,
        })
        .pipe(catchError(() => of(null))),
      top: this.reportesApi
        .productosTop({ ...hoy, limite: 5 })
        .pipe(catchError(() => of([] as ProductoTopDto[]))),
    }).subscribe({
      next: ({ hoy: deHoy, ayer, semana, top }) => {
        this.hoy.set(deHoy);
        this.ayer.set(ayer);
        this.semana.set(semana);
        this.top.set(top);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private comparar(hoy?: number, ayer?: number): Comparacion | null {
    if (hoy == null || ayer == null || ayer <= 0) return null;
    const pct = Math.round(((hoy - ayer) / ayer) * 100);
    if (pct === 0) return { clave: 'tablero.igualQueAyer', pct: 0, sentido: 'igual' };
    return pct > 0
      ? { clave: 'tablero.masQueAyer', pct, sentido: 'sube' }
      : { clave: 'tablero.menosQueAyer', pct: -pct, sentido: 'baja' };
  }
}
