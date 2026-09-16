import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe, NgTemplateOutlet } from '@angular/common';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  ComandasApi,
  MesaResponseDtoEstadoEnum,
  MesaResponseDtoFormaEnum,
  PosicionMesaFormaEnum,
  ReservaResponseDtoEstadoEnum,
  SalonMesasApi,
  SalonReservasApi,
  type MesaResponseDto,
  type OrdenResumenDto,
  type PlanoRequestDto,
  type ReservaResponseDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { EnVivo } from '../../disenio/en-vivo';
import { Icono } from '../../disenio/icono';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { codigoDeOrden, fechaIsoLocal, formatearDuracion } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { TiempoRealService } from '../../nucleo/tiempo-real/tiempo-real.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { SesionService } from '../../nucleo/sesion/sesion.service';

type Filtro = 'todas' | MesaResponseDtoEstadoEnum;
type Vista = 'salon' | 'reservas';

/** Donde esta una mesa en el plano de su zona y cuanto ocupa, en celdas. */
interface Lugar {
  columna: number;
  fila: number;
  ancho: number;
  alto: number;
  forma: MesaResponseDtoFormaEnum;
}

/** Las mismas medidas que valida el servidor (`ReglaPlano`). */
const COLUMNAS = 12;
const FILAS = 40;
const LADO_MAXIMO = 4;

const FILTROS: ReadonlyArray<{ id: Filtro; nombre: ClaveI18n }> = [
  { id: 'todas', nombre: 'mesas.todas' },
  { id: MesaResponseDtoEstadoEnum.LIBRE, nombre: 'mesa.LIBRE' },
  { id: MesaResponseDtoEstadoEnum.OCUPADA, nombre: 'mesa.OCUPADA' },
  { id: MesaResponseDtoEstadoEnum.RESERVADA, nombre: 'mesa.RESERVADA' },
  { id: MesaResponseDtoEstadoEnum.INHABILITADA, nombre: 'mesa.INHABILITADA' },
];

const VISTAS: ReadonlyArray<{ id: Vista; nombre: ClaveI18n }> = [
  { id: 'salon', nombre: 'mesas.salon' },
  { id: 'reservas', nombre: 'mesas.reservas' },
];

/** Lo que dura una reserva, en los pasos que se ofrecen. */
const DURACIONES = [60, 90, 120, 180];

/** Los pasos de la agenda en el orden en que se leen: lo normal primero, cancelar al final. */
const GESTOS: ReadonlyArray<{ estado: ReservaResponseDtoEstadoEnum; nombre: ClaveI18n }> = [
  { estado: ReservaResponseDtoEstadoEnum.CONFIRMADA, nombre: 'reservas.confirmar' },
  { estado: ReservaResponseDtoEstadoEnum.CUMPLIDA, nombre: 'reservas.llegaron' },
  { estado: ReservaResponseDtoEstadoEnum.NO_ASISTIO, nombre: 'reservas.noVino' },
  { estado: ReservaResponseDtoEstadoEnum.CANCELADA, nombre: 'reservas.cancelar' },
];

/** Solo si se cae el tiempo real. */
const REFRESCO_MS = 30_000;

/** La mesa 2 antes que la 10: el numero se lee como numero aunque sea texto. */
function porNumero(a: MesaResponseDto, b: MesaResponseDto): number {
  return (a.numero ?? '').localeCompare(b.numero ?? '', undefined, { numeric: true });
}

function mismaZona(a: MesaResponseDto, b: MesaResponseDto): boolean {
  return (a.zona ?? '').trim().toLowerCase() === (b.zona ?? '').trim().toLowerCase();
}

function sePisan(a: Lugar, b: Lugar): boolean {
  return (
    a.columna < b.columna + b.ancho &&
    b.columna < a.columna + a.ancho &&
    a.fila < b.fila + b.alto &&
    b.fila < a.fila + a.alto
  );
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

function hoyComoCampo(): string {
  return paraCampo(new Date()).slice(0, 10);
}

/**
 * Mesas: el salon de un vistazo y la agenda de reservas del dia.
 *
 * En PC el salon es un plano por zona: cada mesa en su sitio, con su tamano y
 * su forma, porque asi se reconoce la mesa del rincon sin leer numeros. En el
 * celular el plano no cabe y se ve como lista por zona. El administrador mueve
 * las mesas arrastrandolas o con las flechas, y el plano se guarda entero de
 * una vez.
 *
 * La ocupacion la decide la comanda, no esta pantalla: una mesa se ocupa al
 * tomar el pedido en el POS y se libera al cobrarlo. "Reservada" tampoco se
 * elige aqui: la mesa se ve reservada mientras una reserva de la agenda la
 * aparta, desde una hora antes.
 */
@Component({
  selector: 'app-mesas',
  imports: [DecimalPipe, NgTemplateOutlet, Icono, Dialogo, EnVivo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './mesas.page.html',
  styleUrls: ['../../disenio/secciones.scss', './mesas.page.scss'],
})
export class MesasPage implements OnInit {
  private readonly mesasApi = inject(SalonMesasApi);
  private readonly reservasApi = inject(SalonReservasApi);
  private readonly comandasApi = inject(ComandasApi);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
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
  protected readonly VISTAS = VISTAS;
  protected readonly DURACIONES = DURACIONES;
  protected readonly ESTADO = MesaResponseDtoEstadoEnum;
  protected readonly FORMA = MesaResponseDtoFormaEnum;
  protected readonly RESERVA = ReservaResponseDtoEstadoEnum;

  /** Dar de alta una mesa y mover el plano es del administrador. */
  protected readonly esAdmin = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));

  protected readonly vista = signal<Vista>('salon');
  /** En PC el salon se dibuja como plano; en el celular, como lista por zona. */
  protected readonly plano = signal(false);

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly mesas = signal<MesaResponseDto[]>([]);
  protected readonly activas = signal<OrdenResumenDto[]>([]);
  protected readonly filtro = signal<Filtro>('todas');

  // --- detalle --------------------------------------------------------------
  protected readonly detalleAbierto = signal(false);
  protected readonly elegidaId = signal<string | null>(null);

  // --- edicion del plano ----------------------------------------------------
  protected readonly editando = signal(false);
  /** Solo las mesas movidas; las demas se leen de lo que dijo el servidor. */
  protected readonly borrador = signal<ReadonlyMap<string, Lugar>>(new Map());
  protected readonly seleccionadaId = signal<string | null>(null);
  private arrastre: {
    id: string;
    x: number;
    y: number;
    lugar: Lugar;
    anchoCelda: number;
    altoCelda: number;
  } | null = null;

  // --- reserva --------------------------------------------------------------
  protected readonly reservaAbierta = signal(false);
  protected readonly reservaMesaId = signal('');
  protected readonly reservaNombre = signal('');
  protected readonly reservaCelular = signal('');
  protected readonly reservaPersonas = signal<number | null>(2);
  protected readonly reservaInicio = signal('');
  protected readonly reservaDuracion = signal(90);
  protected readonly reservaNota = signal('');

  // --- agenda ---------------------------------------------------------------
  protected readonly dia = signal(hoyComoCampo());
  /** `null` mientras llega: una lista vacia ya significa "no hay reservas". */
  protected readonly reservas = signal<ReservaResponseDto[] | null>(null);

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

  /**
   * Las zonas con sus mesas. En la lista el filtro quita las que no coinciden;
   * en el plano solo las apaga, porque cada mesa tiene su sitio y un hueco en
   * medio del salon se leeria como una mesa que no existe.
   */
  protected readonly zonas = computed(() => {
    const filtro = this.filtro();
    const plano = this.plano();
    const grupos = new Map<string, MesaResponseDto[]>();
    for (const m of [...this.mesas()].sort(porNumero)) {
      if (!plano && filtro !== 'todas' && m.estado !== filtro) continue;
      const zona = m.zona?.trim() ?? '';
      grupos.set(zona, [...(grupos.get(zona) ?? []), m]);
    }
    // Las mesas sin zona van al final: son las que nadie termino de ubicar.
    return [...grupos.entries()]
      .sort(([a], [b]) => (a === '' ? 1 : b === '' ? -1 : a.localeCompare(b)))
      .map(([nombre, mesas]) => ({ nombre, mesas, filas: this.filasDe(mesas) }));
  });

  /** Las zonas que ya existen, para sugerirlas al dar de alta y no escribir «Terraza» de tres formas. */
  protected readonly zonasConocidas = computed(() =>
    [
      ...new Set(
        this.mesas()
          .map((m) => m.zona?.trim())
          .filter((z): z is string => !!z),
      ),
    ].sort(),
  );

  protected readonly elegida = computed(
    () => this.mesas().find((m) => m.id === this.elegidaId()) ?? null,
  );

  protected readonly seleccionada = computed(
    () => this.mesas().find((m) => m.id === this.seleccionadaId()) ?? null,
  );

  protected readonly comandasElegida = computed(() => {
    const id = this.elegidaId();
    return id ? (this.comandasPorMesa().get(id) ?? []) : [];
  });

  protected readonly tituloDetalle = computed(() => {
    const m = this.elegida();
    return m ? this.t('comun.mesa', { numero: m.numero ?? '' }) : '';
  });

  /** Las movidas de verdad: arrastrar una mesa y devolverla a su sitio no es un cambio. */
  protected readonly cambiosDelPlano = computed<PlanoRequestDto['mesas']>(() => {
    const cambios: PlanoRequestDto['mesas'] = [];
    for (const m of this.mesas()) {
      const nuevo = m.id ? this.borrador().get(m.id) : undefined;
      if (!m.id || !nuevo) continue;
      const antes = this.lugarOriginal(m);
      if (
        nuevo.columna !== antes.columna ||
        nuevo.fila !== antes.fila ||
        nuevo.ancho !== antes.ancho ||
        nuevo.alto !== antes.alto ||
        nuevo.forma !== antes.forma
      ) {
        // El contrato genera un enum por modelo: la misma forma, con el tipo del plano.
        const forma =
          nuevo.forma === MesaResponseDtoFormaEnum.REDONDA
            ? PosicionMesaFormaEnum.REDONDA
            : PosicionMesaFormaEnum.CUADRADA;
        cambios.push({ id: m.id, ...nuevo, forma });
      }
    }
    return cambios;
  });

  /** No se reserva una mesa inhabilitada: el servidor la rechazaria. */
  protected readonly mesasReservables = computed(() =>
    [...this.mesas()]
      .filter((m) => m.estado !== MesaResponseDtoEstadoEnum.INHABILITADA)
      .sort(porNumero),
  );

  constructor() {
    // Se repinta con cada aviso del servidor; si el tiempo real se cae, cada
    // REFRESCO_MS como antes.
    inject(TiempoRealService)
      .cambios(['MESAS'], REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));

    // jsdom no tiene matchMedia: en las pruebas se ve la lista, salvo que la prueba lo simule.
    if (typeof window !== 'undefined' && typeof window.matchMedia === 'function') {
      const consulta = window.matchMedia('(min-width: 48rem)');
      const anotar = () => {
        this.plano.set(consulta.matches);
        if (!consulta.matches) this.descartarPlano();
      };
      anotar();
      consulta.addEventListener('change', anotar);
      inject(DestroyRef).onDestroy(() => consulta.removeEventListener('change', anotar));
    }
  }

  ngOnInit(): void {
    this.cargar();
  }

  /** Mientras se edita el plano no se refresca: lo que llegue pisaria las mesas a medio mover. */
  protected cargar(silencioso = false): void {
    if (silencioso && this.editando()) return;
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

  protected elegirVista(vista: Vista): void {
    this.vista.set(vista);
    if (vista === 'reservas') this.cargarAgenda();
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
    return mesa.capacidad
      ? this.tp('mesas.personas', mesa.capacidad)
      : this.t('mesas.sinCapacidad');
  }

  /**
   * Si la reserva ya aparta la mesa, a nombre de quien; si es para mas tarde,
   * a que hora, para que el mozo no siente a nadie que no vaya a terminar.
   */
  protected reservaDe(mesa: MesaResponseDto): string | null {
    const r = mesa.reservaProxima;
    if (!r) return null;
    const hora = this.fecha(r.inicio, 'hora');
    return mesa.estado === MesaResponseDtoEstadoEnum.RESERVADA
      ? this.t('mesas.reservadaA', { nombre: r.nombre ?? '', hora })
      : this.t('mesas.proximaReserva', { nombre: r.nombre ?? '', hora });
  }

  /** Lo que dice la mesa al pasar el raton por el plano, sin abrirla. */
  protected resumenDe(mesa: MesaResponseDto): string {
    return [
      this.t('comun.mesa', { numero: mesa.numero ?? '' }),
      this.tEnum('mesa', mesa.estado),
      this.capacidadDe(mesa),
      this.reservaDe(mesa),
    ]
      .filter(Boolean)
      .join(' · ');
  }

  protected coincide(mesa: MesaResponseDto): boolean {
    const filtro = this.filtro();
    return filtro === 'todas' || mesa.estado === filtro;
  }

  protected rangoDe(r: ReservaResponseDto): string {
    return `${this.fecha(r.inicio, 'hora')} – ${this.fecha(r.fin, 'hora')}`;
  }

  // --- plano ----------------------------------------------------------------

  protected lugarDe(mesa: MesaResponseDto): Lugar {
    return this.borrador().get(mesa.id ?? '') ?? this.lugarOriginal(mesa);
  }

  private lugarOriginal(mesa: MesaResponseDto): Lugar {
    return {
      columna: mesa.columna ?? 0,
      fila: mesa.fila ?? 0,
      ancho: mesa.ancho ?? 2,
      alto: mesa.alto ?? 2,
      forma: mesa.forma ?? MesaResponseDtoFormaEnum.CUADRADA,
    };
  }

  /** Las filas que ocupa la zona. Editando se dejan tres de sobra, para tener donde arrastrar. */
  private filasDe(mesas: MesaResponseDto[]): number {
    const ocupadas = Math.max(0, ...mesas.map((m) => this.lugarDe(m).fila + this.lugarDe(m).alto));
    return this.editando() ? Math.min(FILAS, ocupadas + 3) : Math.max(ocupadas, 2);
  }

  protected empezarEdicion(): void {
    this.borrador.set(new Map());
    this.seleccionadaId.set(null);
    this.editando.set(true);
  }

  protected descartarPlano(): void {
    this.editando.set(false);
    this.borrador.set(new Map());
    this.seleccionadaId.set(null);
    this.arrastre = null;
  }

  protected guardarPlano(): void {
    const cambios = this.cambiosDelPlano();
    if (cambios.length === 0 || this.guardando()) return;

    this.guardando.set(true);
    this.mesasApi.guardarPlanoMesas({ planoRequestDto: { mesas: cambios } }).subscribe({
      next: (mesas) => {
        this.guardando.set(false);
        this.mesas.set(mesas);
        this.descartarPlano();
        this.avisos.exito(this.tp('mesas.avisoPlano', cambios.length));
      },
      error: () => this.guardando.set(false),
    });
  }

  /** Dentro del plano y sin pisar otra mesa de su zona. El servidor lo vuelve a comprobar al guardar. */
  private cabe(mesa: MesaResponseDto, lugar: Lugar): boolean {
    if (
      lugar.ancho < 1 ||
      lugar.alto < 1 ||
      lugar.ancho > LADO_MAXIMO ||
      lugar.alto > LADO_MAXIMO
    ) {
      return false;
    }
    if (
      lugar.columna < 0 ||
      lugar.fila < 0 ||
      lugar.columna + lugar.ancho > COLUMNAS ||
      lugar.fila + lugar.alto > FILAS
    ) {
      return false;
    }
    return this.mesas().every(
      (otra) =>
        otra.id === mesa.id || !mismaZona(otra, mesa) || !sePisan(lugar, this.lugarDe(otra)),
    );
  }

  /** Si el sitio no sirve, la mesa se queda en el ultimo que si servia. */
  private moverA(mesa: MesaResponseDto, lugar: Lugar): void {
    if (!mesa.id || !this.cabe(mesa, lugar)) return;
    const id = mesa.id;
    this.borrador.update((actual) => new Map(actual).set(id, lugar));
  }

  protected tocarMesa(mesa: MesaResponseDto): void {
    if (this.editando()) {
      this.seleccionadaId.set(mesa.id ?? null);
    } else {
      this.abrir(mesa);
    }
  }

  protected empezarArrastre(evento: PointerEvent, mesa: MesaResponseDto): void {
    if (!this.editando() || !mesa.id) return;
    const boton = evento.currentTarget as HTMLElement;
    const plano = boton.closest('.plano') as HTMLElement | null;
    if (!plano) return;

    boton.setPointerCapture?.(evento.pointerId);
    this.seleccionadaId.set(mesa.id);
    this.arrastre = {
      id: mesa.id,
      x: evento.clientX,
      y: evento.clientY,
      lugar: this.lugarDe(mesa),
      anchoCelda: plano.getBoundingClientRect().width / COLUMNAS,
      altoCelda: Number.parseFloat(getComputedStyle(plano).gridAutoRows) || 52,
    };
  }

  protected arrastrar(evento: PointerEvent, mesa: MesaResponseDto): void {
    const a = this.arrastre;
    if (!a || a.id !== mesa.id || a.anchoCelda <= 0) return;
    this.moverA(mesa, {
      ...a.lugar,
      columna: a.lugar.columna + Math.round((evento.clientX - a.x) / a.anchoCelda),
      fila: a.lugar.fila + Math.round((evento.clientY - a.y) / a.altoCelda),
    });
  }

  protected soltar(): void {
    this.arrastre = null;
  }

  /** Las flechas mueven la mesa una celda: el plano se puede ordenar sin raton. */
  protected teclaEnMesa(evento: KeyboardEvent, mesa: MesaResponseDto): void {
    if (!this.editando()) return;
    const paso: Record<string, [number, number]> = {
      ArrowLeft: [-1, 0],
      ArrowRight: [1, 0],
      ArrowUp: [0, -1],
      ArrowDown: [0, 1],
    };
    const delta = paso[evento.key];
    if (!delta) return;
    evento.preventDefault();
    this.seleccionadaId.set(mesa.id ?? null);
    const l = this.lugarDe(mesa);
    this.moverA(mesa, { ...l, columna: l.columna + delta[0], fila: l.fila + delta[1] });
  }

  protected redimensionar(eje: 'ancho' | 'alto', paso: number): void {
    const mesa = this.seleccionada();
    if (!mesa) return;
    const l = this.lugarDe(mesa);
    this.moverA(
      mesa,
      eje === 'ancho' ? { ...l, ancho: l.ancho + paso } : { ...l, alto: l.alto + paso },
    );
  }

  protected alternarForma(): void {
    const mesa = this.seleccionada();
    if (!mesa) return;
    const l = this.lugarDe(mesa);
    this.moverA(mesa, {
      ...l,
      forma:
        l.forma === MesaResponseDtoFormaEnum.REDONDA
          ? MesaResponseDtoFormaEnum.CUADRADA
          : MesaResponseDtoFormaEnum.REDONDA,
    });
  }

  // --- detalle --------------------------------------------------------------

  protected abrir(mesa: MesaResponseDto): void {
    if (!mesa.id) return;
    this.elegidaId.set(mesa.id);
    this.detalleAbierto.set(true);
  }

  protected verComanda(orden: OrdenResumenDto): void {
    this.detalleAbierto.set(false);
    void this.router.navigate(['/ordenes'], { queryParams: { orden: orden.id } });
  }

  protected puede(r: ReservaResponseDto, estado: ReservaResponseDtoEstadoEnum): boolean {
    // Son dos enums generados con los mismos valores: se comparan como texto.
    return (r.transicionesPermitidas ?? []).map(String).includes(estado);
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

  /** Inhabilitar y volver a habilitar: la mesa coja, la que se aparta para un evento. */
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

  // --- reserva --------------------------------------------------------------

  protected abrirReserva(mesa?: MesaResponseDto): void {
    this.reservaMesaId.set(mesa?.id ?? this.mesasReservables()[0]?.id ?? '');
    this.reservaNombre.set('');
    this.reservaCelular.set('');
    this.reservaPersonas.set(mesa?.capacidad ? Math.min(2, mesa.capacidad) : 2);
    this.reservaInicio.set(paraCampo(horaSugerida()));
    this.reservaDuracion.set(90);
    this.reservaNota.set('');
    this.detalleAbierto.set(false);
    this.reservaAbierta.set(true);
  }

  protected anotarPersonas(valor: string): void {
    const n = Number.parseInt(valor, 10);
    this.reservaPersonas.set(Number.isFinite(n) && n > 0 ? n : null);
  }

  protected elegirDuracion(valor: string): void {
    const n = Number.parseInt(valor, 10);
    if (Number.isFinite(n)) this.reservaDuracion.set(n);
  }

  /** Mas personas que sillas se avisa antes de enviar, para no gastar un 400. */
  protected guardarReserva(): void {
    const mesa = this.mesas().find((m) => m.id === this.reservaMesaId());
    const nombre = this.reservaNombre().trim();
    const personas = this.reservaPersonas();
    const inicio = new Date(this.reservaInicio());
    if (!mesa?.id || !nombre || personas === null || Number.isNaN(inicio.getTime())) return;
    if (this.guardando()) return;
    if (mesa.capacidad && personas > mesa.capacidad) {
      this.avisos.info(
        this.t('reservas.avisoCapacidad', { numero: mesa.numero ?? '', n: mesa.capacidad }),
      );
      return;
    }

    this.guardando.set(true);
    this.reservasApi
      .crearReserva({
        reservaRequestDto: {
          mesaId: mesa.id,
          nombre,
          personas,
          inicio: fechaIsoLocal(inicio),
          duracionMinutos: this.reservaDuracion(),
          celular: this.reservaCelular().trim() || undefined,
          nota: this.reservaNota().trim() || undefined,
        },
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.reservaAbierta.set(false);
          this.avisos.exito(this.t('mesas.avisoReservada', { numero: mesa.numero ?? '' }));
          this.cargar(true);
          if (this.vista() === 'reservas') this.cargarAgenda();
        },
        error: () => this.guardando.set(false),
      });
  }

  // --- agenda ---------------------------------------------------------------

  protected cargarAgenda(): void {
    this.reservasApi.listarReservas({ dia: this.dia() }).subscribe({
      next: (lista) => this.reservas.set(lista),
      error: () => this.reservas.set([]),
    });
  }

  protected cambiarDia(valor: string): void {
    if (!valor) return;
    this.dia.set(valor);
    this.reservas.set(null);
    this.cargarAgenda();
  }

  /** Los pasos que la reserva permite, en el orden de la agenda. La tabla vive en el servidor. */
  protected gestosDe(r: ReservaResponseDto): typeof GESTOS {
    return GESTOS.filter((g) => this.puede(r, g.estado));
  }

  /** Cancelar pide confirmacion: la mesa vuelve a ofrecerse y quien reservo ya no la tiene. */
  protected async cambiarReserva(
    r: ReservaResponseDto,
    estado: ReservaResponseDtoEstadoEnum,
  ): Promise<void> {
    if (!r.id || this.guardando()) return;
    if (
      estado === ReservaResponseDtoEstadoEnum.CANCELADA &&
      !(await this.confirmacion.pedir({
        titulo: this.t('reservas.cancelarTitulo', { nombre: r.nombre ?? '' }),
        mensaje: this.t('reservas.cancelarMensaje', {
          mesa: r.mesaNumero ?? '',
          hora: this.fecha(r.inicio, 'hora'),
        }),
        confirmar: this.t('reservas.cancelar'),
      }))
    ) {
      return;
    }

    const id = r.id;
    this.guardando.set(true);
    this.reservasApi.cambiarEstadoReserva({ id, estado }).subscribe({
      next: (actualizada) => {
        this.guardando.set(false);
        this.detalleAbierto.set(false);
        this.reservas.update((lista) => lista?.map((x) => (x.id === id ? actualizada : x)) ?? null);
        this.avisos.exito(
          this.t('reservas.avisoEstado', {
            nombre: r.nombre ?? '',
            estado: this.tEnum('reserva', estado),
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

  /** El servidor la ubica en el primer hueco de su zona. */
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
