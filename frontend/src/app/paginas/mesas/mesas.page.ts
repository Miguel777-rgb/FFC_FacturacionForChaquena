import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  ComandasApi,
  MesaResponseDtoEstadoEnum,
  SalonMesasApi,
  type MesaResponseDto,
  type OrdenResumenDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { codigoDeOrden, fechaIsoLocal, formatearDuracion } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { SesionService } from '../../nucleo/sesion/sesion.service';

type Filtro = 'todas' | MesaResponseDtoEstadoEnum;

const FILTROS: ReadonlyArray<{ id: Filtro; nombre: ClaveI18n }> = [
  { id: 'todas', nombre: 'mesas.todas' },
  { id: MesaResponseDtoEstadoEnum.LIBRE, nombre: 'mesa.LIBRE' },
  { id: MesaResponseDtoEstadoEnum.OCUPADA, nombre: 'mesa.OCUPADA' },
  { id: MesaResponseDtoEstadoEnum.RESERVADA, nombre: 'mesa.RESERVADA' },
  { id: MesaResponseDtoEstadoEnum.INHABILITADA, nombre: 'mesa.INHABILITADA' },
];

const REFRESCO_MS = 30_000;

/** La mesa 2 antes que la 10: el numero se lee como numero aunque sea texto. */
function porNumero(a: MesaResponseDto, b: MesaResponseDto): number {
  return (a.numero ?? '').localeCompare(b.numero ?? '', undefined, { numeric: true });
}

/** El valor que entiende un `datetime-local`: sin segundos ni desfase. */
function paraCampo(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return (
    `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}` +
    `T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}`
  );
}

/** Una reserva se pide casi siempre para dentro de un rato: la proxima media hora pasada una hora. */
function horaSugerida(): Date {
  const fecha = new Date(Date.now() + 60 * 60_000);
  fecha.setMinutes(fecha.getMinutes() < 30 ? 30 : 60, 0, 0);
  return fecha;
}

/**
 * Mesas: el salon de un vistazo.
 *
 * Una tarjeta por mesa, agrupadas por zona, porque asi se recorre el local: la
 * terraza, el salon, la barra. Cada tarjeta dice lo que el mozo necesita al
 * pasar —si esta libre, cuanto lleva la comanda abierta, a nombre de quien esta
 * reservada— sin abrir nada.
 *
 * El plano con posiciones llega con el backend de reservas; mientras tanto la
 * rejilla no finge una distribucion que el servidor no guarda.
 *
 * La ocupacion la decide la comanda, no esta pantalla: una mesa se ocupa al
 * tomar el pedido en el POS y se libera al cobrarlo. Aqui solo se reserva, se
 * libera una reserva y se inhabilita la mesa que no se puede usar.
 */
@Component({
  selector: 'app-mesas',
  imports: [DecimalPipe, Icono, Dialogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mesas.page.html',
  styleUrls: ['../../disenio/secciones.scss', './mesas.page.scss'],
})
export class MesasPage implements OnInit {
  private readonly mesasApi = inject(SalonMesasApi);
  private readonly comandasApi = inject(ComandasApi);
  private readonly avisos = inject(AvisosService);
  private readonly router = inject(Router);
  private readonly sesion = inject(SesionService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly fecha = this.i18n.fecha;
  protected readonly duracion = formatearDuracion;
  protected readonly codigo = codigoDeOrden;

  protected readonly FILTROS = FILTROS;
  protected readonly ESTADO = MesaResponseDtoEstadoEnum;

  /** Dar de alta una mesa es del administrador (`POST /mesas`). */
  protected readonly esAdmin = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly mesas = signal<MesaResponseDto[]>([]);
  protected readonly activas = signal<OrdenResumenDto[]>([]);
  protected readonly filtro = signal<Filtro>('todas');

  // --- detalle --------------------------------------------------------------
  protected readonly detalleAbierto = signal(false);
  protected readonly elegidaId = signal<string | null>(null);
  protected readonly reservando = signal(false);
  protected readonly aNombreDe = signal('');
  protected readonly para = signal('');

  // --- alta -----------------------------------------------------------------
  protected readonly altaAbierta = signal(false);
  protected readonly numero = signal('');
  protected readonly zona = signal('');
  protected readonly capacidad = signal<number | null>(null);

  /** Las comandas abiertas de cada mesa. Puede haber mas de una: dos cuentas en la misma mesa. */
  protected readonly comandasPorMesa = computed(() => {
    const mapa = new Map<string, OrdenResumenDto[]>();
    for (const o of this.activas()) {
      if (!o.mesaId) continue;
      mapa.set(o.mesaId, [...(mapa.get(o.mesaId) ?? []), o]);
    }
    return mapa;
  });

  protected readonly conteo = computed(() => {
    const cuenta: Record<string, number> = {};
    for (const m of this.mesas()) {
      if (m.estado) cuenta[m.estado] = (cuenta[m.estado] ?? 0) + 1;
    }
    return cuenta;
  });

  protected readonly zonas = computed(() => {
    const filtro = this.filtro();
    const grupos = new Map<string, MesaResponseDto[]>();
    for (const m of this.mesas()
      .filter((m) => filtro === 'todas' || m.estado === filtro)
      .sort(porNumero)) {
      const zona = m.zona?.trim() ?? '';
      grupos.set(zona, [...(grupos.get(zona) ?? []), m]);
    }
    // Las mesas sin zona van al final: son las que nadie termino de ubicar.
    return [...grupos.entries()]
      .sort(([a], [b]) => (a === '' ? 1 : b === '' ? -1 : a.localeCompare(b)))
      .map(([nombre, mesas]) => ({ nombre, mesas }));
  });

  /** Las zonas que ya existen, para sugerirlas al dar de alta y no escribir «Terraza» de tres formas. */
  protected readonly zonasConocidas = computed(() =>
    [...new Set(this.mesas().map((m) => m.zona?.trim()).filter((z): z is string => !!z))].sort(),
  );

  protected readonly elegida = computed(
    () => this.mesas().find((m) => m.id === this.elegidaId()) ?? null,
  );

  protected readonly comandasElegida = computed(() => {
    const id = this.elegidaId();
    return id ? (this.comandasPorMesa().get(id) ?? []) : [];
  });

  protected readonly tituloDetalle = computed(() => {
    const m = this.elegida();
    return m ? this.t('comun.mesa', { numero: m.numero ?? '' }) : '';
  });

  constructor() {
    interval(REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);

    // Sin las comandas el salon se pinta igual: se pierde el importe, no la mesa.
    forkJoin({
      mesas: this.mesasApi.mapa(),
      activas: this.comandasApi.activas().pipe(catchError(() => of([] as OrdenResumenDto[]))),
    }).subscribe({
      next: ({ mesas, activas }) => {
        this.mesas.set(mesas);
        this.activas.set(activas);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  // --- tarjeta --------------------------------------------------------------

  protected totalDe(mesa: MesaResponseDto): number {
    return (this.comandasPorMesa().get(mesa.id ?? '') ?? []).reduce(
      (suma, o) => suma + (o.montoTotal ?? 0),
      0,
    );
  }

  /** Lo que lleva abierta la comanda mas antigua: es la que se esta enfriando. */
  protected minutosDe(mesa: MesaResponseDto): number | null {
    const comandas = this.comandasPorMesa().get(mesa.id ?? '') ?? [];
    if (comandas.length === 0) return null;
    return Math.max(...comandas.map((o) => o.minutosTranscurridos ?? 0));
  }

  protected capacidadDe(mesa: MesaResponseDto): string {
    return mesa.capacidad ? this.tp('mesas.personas', mesa.capacidad) : this.t('mesas.sinCapacidad');
  }

  protected reservaDe(mesa: MesaResponseDto): string {
    const hora = mesa.reservadaPara ? this.fecha(mesa.reservadaPara) : '';
    return mesa.reservadaANombreDe
      ? this.t('mesas.reservadaA', { nombre: mesa.reservadaANombreDe, hora })
      : this.t('mesas.reservadaSinNombre', { hora });
  }

  // --- detalle --------------------------------------------------------------

  protected abrir(mesa: MesaResponseDto): void {
    if (!mesa.id) return;
    this.elegidaId.set(mesa.id);
    this.reservando.set(false);
    this.detalleAbierto.set(true);
  }

  protected verComanda(orden: OrdenResumenDto): void {
    this.detalleAbierto.set(false);
    void this.router.navigate(['/ordenes'], { queryParams: { orden: orden.id } });
  }

  protected alternarReserva(): void {
    this.reservando.update((v) => !v);
    this.aNombreDe.set('');
    this.para.set(paraCampo(horaSugerida()));
  }

  protected reservar(): void {
    const mesa = this.elegida();
    const instante = new Date(this.para());
    if (!mesa?.id || Number.isNaN(instante.getTime()) || this.guardando()) return;

    this.guardando.set(true);
    this.mesasApi
      .reservar({
        id: mesa.id,
        reservarMesaRequestDto: {
          anombreDe: this.aNombreDe().trim() || undefined,
          para: fechaIsoLocal(instante),
        },
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.reservando.set(false);
          this.avisos.exito(this.t('mesas.avisoReservada', { numero: mesa.numero ?? '' }));
          this.cargar(true);
        },
        error: () => this.guardando.set(false),
      });
  }

  protected liberar(): void {
    const mesa = this.elegida();
    if (!mesa?.id || this.guardando()) return;

    this.guardando.set(true);
    this.mesasApi.liberar({ id: mesa.id }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.exito(this.t('mesas.avisoLiberada', { numero: mesa.numero ?? '' }));
        this.cargar(true);
      },
      error: () => this.guardando.set(false),
    });
  }

  /** Inhabilitar y volver a habilitar: la mesa coja, la que se reserva para un evento. */
  protected cambiarEstado(estado: 'LIBRE' | 'INHABILITADA'): void {
    const mesa = this.elegida();
    if (!mesa?.id || this.guardando()) return;

    this.guardando.set(true);
    this.mesasApi.cambiarEstadoMesa({ id: mesa.id, estado }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.exito(
          this.t(estado === 'LIBRE' ? 'mesas.avisoHabilitada' : 'mesas.avisoInhabilitada', {
            numero: mesa.numero ?? '',
          }),
        );
        this.cargar(true);
      },
      error: () => this.guardando.set(false),
    });
  }

  // --- alta -----------------------------------------------------------------

  protected abrirAlta(): void {
    this.numero.set('');
    this.zona.set('');
    this.capacidad.set(null);
    this.altaAbierta.set(true);
  }

  protected anotarCapacidad(valor: string): void {
    const n = Number.parseInt(valor, 10);
    this.capacidad.set(Number.isFinite(n) && n > 0 ? n : null);
  }

  protected alta(): void {
    const numero = this.numero().trim();
    if (!numero || this.guardando()) return;

    this.guardando.set(true);
    this.mesasApi
      .crearMesa({
        mesaRequestDto: {
          numero,
          zona: this.zona().trim() || undefined,
          capacidad: this.capacidad() ?? undefined,
          activa: true,
        },
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.altaAbierta.set(false);
          this.avisos.exito(this.t('mesas.avisoAlta', { numero }));
          this.cargar(true);
        },
        error: () => this.guardando.set(false),
      });
  }
}
