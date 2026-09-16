import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { EnVivo } from '../../disenio/en-vivo';
import { Icono } from '../../disenio/icono';
import { formatearDuracion } from '../../nucleo/i18n/formatos';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, interval, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  CocinaKDSApi,
  ComandaKdsDtoEstadoEnum,
  InventarioInsumosApi,
  type ComandaKdsDto,
  type InsumoResponseDto,
  type KpisCocinaDto,
  type LineaKds,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { TiempoRealService } from '../../nucleo/tiempo-real/tiempo-real.service';

/**
 * Cada cuanto se repinta la cola si se cae el tiempo real. Con la conexion
 * viva, una comanda nueva aparece en cuanto se confirma. En cocina nadie va a
 * pulsar "actualizar" con las manos ocupadas, asi que sin conexion la pantalla
 * sigue refrescandose sola; el cronometro no depende de esto, corre aparte.
 */
const REFRESCO_MS = 15_000;

/** Donde se recuerda si la cocina quiere el aviso sonoro. Por defecto, callado. */
const CLAVE_SONIDO = 'ffc.kds.sonido';

/** Promesas de tiempo que se ofrecen de un toque. Cubren casi todo el servicio. */
const MINUTOS_SUGERIDOS = [10, 15, 20, 30, 45];

/**
 * Pantalla de cocina: la cola por orden de llegada, la promesa de tiempo, el
 * tilde por platillo y el aviso de insumo faltante.
 *
 * Tres columnas por estado, porque un cocinero mirando de lejos necesita saber
 * de un vistazo que espera, que esta en fuego y que ya salio.
 */
@Component({
  selector: 'app-kds',
  imports: [DecimalPipe, Icono, EnVivo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kds.page.html',
  styleUrl: './kds.page.scss',
})
export class KdsPage implements OnInit {
  private readonly kdsApi = inject(CocinaKDSApi);
  private readonly insumosApi = inject(InventarioInsumosApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);
  protected readonly duracion = formatearDuracion;

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly MINUTOS_SUGERIDOS = MINUTOS_SUGERIDOS;

  protected readonly cargando = signal(true);
  protected readonly cola = signal<ComandaKdsDto[]>([]);
  protected readonly kpis = signal<KpisCocinaDto | null>(null);
  protected readonly insumos = signal<InsumoResponseDto[]>([]);

  /** Comanda sobre la que hay una peticion en vuelo, para no pulsar dos veces. */
  protected readonly ocupada = signal<string | null>(null);

  /** Comanda cuyo aviso de faltante se esta escribiendo. */
  protected readonly reportando = signal<string | null>(null);
  protected readonly insumoFaltante = signal('');
  protected readonly detalleFaltante = signal('');

  /**
   * Reloj propio, un latido por segundo.
   *
   * Los minutos que manda el servidor se calculan al serializar la respuesta:
   * en una tarjeta que lleva catorce segundos en pantalla ya estan viejos, y el
   * cronometro es precisamente lo que la cocina mira. Se re-deriva desde
   * `recibida`, que si es un instante fijo, y el valor del servidor queda como
   * respaldo para cuando esa marca no venga.
   */
  private readonly ahora = signal(Date.now());

  /** Aviso sonoro de comandas nuevas. Es opcional: en una cocina con extractor puede no oirse, y en una pequena sobra. */
  protected readonly sonido = signal(leerSonido());
  /** Las comandas de la ultima carga. `null` hasta la primera, que no suena. */
  private vistas: ReadonlySet<string> | null = null;
  private audio: AudioContext | null = null;

  // --- las tres columnas ----------------------------------------------------

  protected readonly esperando = computed(() =>
    this.cola().filter((c) => c.estado === ComandaKdsDtoEstadoEnum.ENCOLADO),
  );

  protected readonly enFuego = computed(() =>
    this.cola().filter(
      (c) => c.estado === ComandaKdsDtoEstadoEnum.EN_PREPARACION && !c.flagCierrePlatillo,
    ),
  );

  protected readonly listas = computed(() =>
    this.cola().filter(
      (c) => c.estado !== ComandaKdsDtoEstadoEnum.ENCOLADO && c.flagCierrePlatillo,
    ),
  );

  constructor() {
    // Se repinta con cada aviso del servidor; si el tiempo real se cae, cada
    // REFRESCO_MS como antes.
    inject(TiempoRealService)
      .cambios(['COCINA'], REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));

    // El cronometro va por su cuenta: la cola puede tardar quince segundos en
    // volver, pero el numero de la tarjeta tiene que avanzar cada segundo.
    interval(1_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.ahora.set(Date.now()));

    inject(DestroyRef).onDestroy(() => void this.audio?.close());
  }

  ngOnInit(): void {
    this.cargar();
  }

  /**
   * `silencioso` es lo que separa el refresco automatico de la carga inicial:
   * el temporizador no debe vaciar la pantalla cada quince segundos, que en
   * cocina se leeria como que la cola desaparecio.
   */
  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);

    forkJoin({
      cola: this.kdsApi.cola(),
      kpis: this.kdsApi.kpis(),
    }).subscribe({
      next: ({ cola, kpis }) => {
        this.avisarSiHayNuevas(cola);
        this.cola.set(cola);
        this.kpis.set(kpis);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });

    // Los insumos solo hacen falta para el desplegable del faltante: se piden
    // una vez y no vuelven a entrar en el ciclo de refresco.
    if (!silencioso && this.insumos().length === 0) {
      this.insumosApi
        .buscarInsumos({ pageable: { page: 0, size: 200, sort: ['nombre,asc'] } })
        .pipe(catchError(() => of({ contenido: [] as InsumoResponseDto[] })))
        .subscribe((pagina) => this.insumos.set(pagina.contenido ?? []));
    }
  }

  // --- aviso sonoro ---------------------------------------------------------

  protected alternarSonido(): void {
    const activo = !this.sonido();
    this.sonido.set(activo);
    try {
      localStorage.setItem(CLAVE_SONIDO, activo ? '1' : '0');
    } catch {
      /* almacenamiento bloqueado: vale para esta visita */
    }
    // El navegador solo deja sonar despues de un gesto, y este lo es: se prueba
    // aqui mismo para que la cocina sepa como suena y que el volumen alcanza.
    if (activo) this.sonar();
  }

  /** Suena si entro alguna comanda que la pantalla no habia visto. */
  private avisarSiHayNuevas(cola: ComandaKdsDto[]): void {
    const ids = new Set(cola.map((c) => c.ordenId).filter((id): id is string => !!id));
    const antes = this.vistas;
    this.vistas = ids;
    if (!antes || !this.sonido()) return;
    if ([...ids].some((id) => !antes.has(id))) this.sonar();
  }

  /**
   * Dos notas cortas, como el timbre de un mostrador. Se generan con Web Audio
   * en vez de cargar un archivo: no hay nada que descargar ni que pueda faltar.
   */
  private sonar(): void {
    if (typeof AudioContext === 'undefined') return;
    this.audio ??= new AudioContext();
    const contexto = this.audio;
    if (contexto.state === 'suspended') void contexto.resume();

    [880, 1175].forEach((frecuencia, i) => {
      const inicio = contexto.currentTime + i * 0.18;
      const oscilador = contexto.createOscillator();
      const volumen = contexto.createGain();
      oscilador.frequency.value = frecuencia;
      volumen.gain.setValueAtTime(0.0001, inicio);
      volumen.gain.exponentialRampToValueAtTime(0.3, inicio + 0.02);
      volumen.gain.exponentialRampToValueAtTime(0.0001, inicio + 0.16);
      oscilador.connect(volumen).connect(contexto.destination);
      oscilador.start(inicio);
      oscilador.stop(inicio + 0.18);
    });
  }

  // --- cronometro -----------------------------------------------------------

  /** Minutos desde que la comanda entro, recalculados en el cliente. */
  protected minutos(comanda: ComandaKdsDto): number {
    if (!comanda.recibida) return comanda.minutosEnCola ?? 0;
    const desde = Date.parse(comanda.recibida);
    if (Number.isNaN(desde)) return comanda.minutosEnCola ?? 0;
    return Math.max(0, Math.floor((this.ahora() - desde) / 60_000));
  }

  /**
   * Tarde es tarde contra la promesa que hizo la propia cocina. Si todavia no
   * hay promesa, se respeta el veredicto del servidor, que compara contra el
   * objetivo general del local.
   */
  protected vaTarde(comanda: ComandaKdsDto): boolean {
    const prometido = comanda.tiempoEstimadoCocinaMinutos;
    if (prometido) return this.minutos(comanda) > prometido;
    return comanda.fueraDeObjetivo === true;
  }

  // --- los gestos de la cola ------------------------------------------------

  /**
   * Cocina se hace cargo. Arranca el cronometro de preparacion y cierra la
   * etapa de recepcion: a partir de aqui el tiempo que pase cuenta como tiempo
   * de cocina, no como espera.
   */
  protected tomar(comanda: ComandaKdsDto): void {
    if (!comanda.ordenId || this.ocupada()) return;

    this.ocupada.set(comanda.ordenId);
    this.kdsApi.tomar({ id: comanda.ordenId }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.avisos.exito(this.t('kds.avisoTomada', { correlativo: comanda.correlativo ?? '' }));
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  /**
   * La promesa de tiempo. Es lo que el mozo repite en la mesa, asi que se pide
   * en cuanto la comanda entra en fuego y no antes: prometer sin haber mirado
   * lo que hay pendiente es prometer a ciegas.
   */
  protected prometer(comanda: ComandaKdsDto, minutos: number): void {
    if (!comanda.ordenId || this.ocupada()) return;

    this.ocupada.set(comanda.ordenId);
    this.kdsApi.estimar({ id: comanda.ordenId, estimarTiempoRequestDto: { minutos } }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.avisos.exito(
          this.t('kds.avisoPrometida', { correlativo: comanda.correlativo ?? '', min: minutos }),
        );
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  /**
   * La comanda esta lista. Sella `tiempoCierrePlatillo`, que es lo que mide
   * cuanto tardo de verdad el plato, y deja el pase al mozo.
   */
  protected listo(comanda: ComandaKdsDto): void {
    if (!comanda.ordenId || this.ocupada()) return;

    this.ocupada.set(comanda.ordenId);
    this.kdsApi.listo({ id: comanda.ordenId }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.avisos.exito(this.t('kds.avisoLista', { correlativo: comanda.correlativo ?? '' }));
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  /**
   * Tilde por platillo, para las comandas que salen por partes. Ya se guarda en
   * la linea, asi que sobrevive al refresco de cada quince segundos; antes se
   * borraba solo y por eso no servia para nada.
   */
  protected marcarPlatillo(comanda: ComandaKdsDto, linea: LineaKds): void {
    if (!linea.detalleId || linea.listo || this.ocupada()) return;

    this.ocupada.set(comanda.ordenId ?? null);
    this.kdsApi.detalleListo({ detalleId: linea.detalleId }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  // --- aviso de insumo faltante ---------------------------------------------

  protected pedirFaltante(comanda: ComandaKdsDto): void {
    this.reportando.set(this.reportando() === comanda.ordenId ? null : (comanda.ordenId ?? null));
    this.insumoFaltante.set('');
    this.detalleFaltante.set('');
  }

  protected elegirInsumo(id: string): void {
    this.insumoFaltante.set(id);
  }

  protected anotarDetalleFaltante(texto: string): void {
    this.detalleFaltante.set(texto);
  }

  /**
   * Avisa de que algo no se puede cocinar. No cambia el estado de la comanda:
   * el mensaje va al mozo, que es quien puede hablar con el comensal y decidir
   * si cambia el plato o lo quita.
   */
  protected reportarFaltante(comanda: ComandaKdsDto): void {
    const insumoId = this.insumoFaltante();
    const detalle = this.detalleFaltante().trim();
    if (!comanda.ordenId || !insumoId || detalle.length === 0) return;

    this.ocupada.set(comanda.ordenId);
    this.kdsApi
      .reportarFaltante({
        id: comanda.ordenId,
        reportarFaltanteRequestDto: { insumoId, detalle },
      })
      .subscribe({
        next: (respuesta) => {
          this.ocupada.set(null);
          this.reportando.set(null);
          this.avisos.info(respuesta.mensaje ?? this.t('kds.avisoFaltante'));
        },
        error: () => this.ocupada.set(null),
      });
  }

  protected desistirFaltante(): void {
    this.reportando.set(null);
  }
}

function leerSonido(): boolean {
  try {
    return localStorage.getItem(CLAVE_SONIDO) === '1';
  } catch {
    return false;
  }
}
