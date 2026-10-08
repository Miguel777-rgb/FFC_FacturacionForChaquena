import {
  DestroyRef,
  Injectable,
  InjectionToken,
  type WritableSignal,
  assertInInjectionContext,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Subject } from 'rxjs';

// Del archivo concreto y no del barril `api`, como todo lo que vive en `nucleo`.
import { Configuration } from '../../api/configuration';
import { AvisosService } from '../http/avisos.service';
import { I18nService } from '../i18n/i18n.service';
import type { Rol } from '../sesion/rol';
import { SesionService } from '../sesion/sesion.service';
import { aplicarAviso, llamadoDeOrden, type AvisoLlamado, type LlamadoCocina } from './llamado';

const PUNTO_DE_CONEXION = '/api/v1/ws';

/** Llaman COCINA y ADMIN; reciben y responden los mozos. Nadie mas abre el canal. */
const ROLES_DEL_LLAMADO: readonly Rol[] = ['MOZO', 'COCINA', 'ADMIN'];

/** Latidos STOMP en los dos sentidos, los mismos que pide el servidor. */
const LATIDO_MS = 10_000;

/** Espera entre reintentos: 1 s, 2 s, 4 s... hasta medio minuto, como el tiempo real. */
const ESPERA_INICIAL_MS = 1_000;
const ESPERA_MAXIMA_MS = 30_000;

/** Cuanto sigue abierto el canal despues de que la ultima pantalla lo suelta. */
const GRACIA_AL_SALIR_MS = 5_000;

/**
 * Sin conexion durante mas de esto, el mozo lo ve. Antes no: un celular que
 * cambia de red reconecta en un par de segundos, y avisarlo cada vez seria
 * ruido.
 */
const AVISAR_CAIDA_MS = 10_000;

/** Sin confirmacion del servidor en este tiempo, el boton vuelve a estar disponible. */
const ESPERA_RESPUESTA_MS = 10_000;

/** Lo que el servicio necesita de un cliente STOMP, y nada mas. */
export interface ClienteStomp {
  activar(): void;
  desactivar(): void;
  publicar(destino: string, cuerpo: unknown): void;
  suscribir(destino: string, alRecibir: (cuerpo: string) => void): void;
}

export interface ConfigClienteStomp {
  url: string;
  /** Cabeceras del CONNECT, leidas en cada intento. `null` deja de intentar: ya no hay sesion. */
  cabeceras: () => Record<string, string> | null;
  alConectar: () => void;
  alCaerse: () => void;
}

/**
 * Crea el cliente. El de verdad carga `@stomp/stompjs` con `import()`, asi la
 * libreria va en su propio trozo y solo la descarga quien abre el canal; las
 * pruebas lo cambian por uno falso.
 */
export const FABRICA_CLIENTE_STOMP = new InjectionToken<
  (config: ConfigClienteStomp) => Promise<ClienteStomp>
>('FABRICA_CLIENTE_STOMP', { providedIn: 'root', factory: () => crearClienteStomp });

type ModuloStomp = typeof import('@stomp/stompjs');

async function crearClienteStomp(config: ConfigClienteStomp): Promise<ClienteStomp> {
  // Para el navegador la libreria se publica como UMD, y al cargarla en su
  // propio trozo el empaquetador la deja entera en `default`. Las pruebas, que
  // resuelven la version ESM, la ven con sus nombres sueltos: valen las dos.
  const modulo: ModuloStomp & { default?: ModuloStomp } = await import('@stomp/stompjs');
  const { Client, ReconnectionTimeMode } = modulo.default ?? modulo;
  const cliente = new Client({
    brokerURL: config.url,
    heartbeatIncoming: LATIDO_MS,
    heartbeatOutgoing: LATIDO_MS,
    reconnectDelay: ESPERA_INICIAL_MS,
    maxReconnectDelay: ESPERA_MAXIMA_MS,
    reconnectTimeMode: ReconnectionTimeMode.EXPONENTIAL,
    // Un celular que cambio de red deja el socket abierto y mudo: cuando faltan
    // los latidos se cierra en el acto, en vez de esperar a que el sistema se
    // entere.
    discardWebsocketOnCommFailure: true,
    // El token se lee en cada intento: una reconexion lleva el vigente. Si el
    // servidor lo rechaza, manda ERROR, cierra, y el siguiente intento vuelve
    // a pasar por aqui.
    beforeConnect: (c) => {
      const cabeceras = config.cabeceras();
      if (cabeceras) c.connectHeaders = cabeceras;
      else void c.deactivate();
    },
    onConnect: () => config.alConectar(),
    onWebSocketClose: () => config.alCaerse(),
  });
  return {
    activar: () => cliente.activate(),
    desactivar: () => void cliente.deactivate(),
    publicar: (destino, cuerpo) =>
      cliente.publish({ destination: destino, body: JSON.stringify(cuerpo) }),
    suscribir: (destino, alRecibir) => {
      cliente.subscribe(destino, (mensaje) => alRecibir(mensaje.body));
    },
  };
}

/**
 * El llamado de cocina al mozo, por WebSocket con STOMP.
 *
 * Va por WebSocket y no por el SSE de `TiempoRealService` porque necesita las
 * dos cosas que SSE no da: que el mozo conteste «Voy» por el mismo canal y que
 * el servidor sepa cuantos mozos estan conectados.
 *
 * El navegador no deja poner cabeceras al abrir un WebSocket, asi que el token
 * viaja en la trama CONNECT de STOMP. Un canal por pestana, y solo mientras
 * alguien lo usa: el aviso del mozo, toda la sesion; la pantalla de cocina,
 * mientras esta abierta.
 */
@Injectable({ providedIn: 'root' })
export class LlamadosService {
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);
  private readonly fabrica = inject(FABRICA_CLIENTE_STOMP);
  // Opcional como en los servicios generados: las pruebas de pagina no lo proveen.
  private readonly basePath = inject(Configuration, { optional: true })?.basePath ?? '';

  private readonly _conectado = signal(false);
  /** Si el canal esta abierto. Sin el no se llama ni se responde. */
  readonly conectado = this._conectado.asReadonly();

  private readonly _sinConexion = signal(false);
  /** Lleva mas de diez segundos sin canal: lo que el mozo tiene que saber. */
  readonly sinConexion = this._sinConexion.asReadonly();

  private readonly _llamados = signal<readonly LlamadoCocina[]>([]);
  readonly llamados = this._llamados.asReadonly();

  /** Los que esperan un «Voy», del que mas espera al mas nuevo. */
  readonly pendientes = computed(() =>
    this._llamados()
      .filter((l) => l.estado === 'PENDIENTE')
      .sort((a, b) => Date.parse(a.llamadoEn) - Date.parse(b.llamadoEn)),
  );

  private readonly _mozos = signal<number | null>(null);
  /** Mozos distintos con el canal abierto. Solo se lo cuentan a cocina. */
  readonly mozosConectados = this._mozos.asReadonly();

  private readonly _llamando = signal<ReadonlySet<string>>(new Set());
  /** Comandas por las que se pulso «Llamar» y el servidor aun no confirma. */
  readonly llamando = this._llamando.asReadonly();

  private readonly _respondiendo = signal<ReadonlySet<string>>(new Set());
  /** Llamados a los que esta pestana dijo «Voy» y el servidor aun no confirma. */
  readonly respondiendo = this._respondiendo.asReadonly();

  private readonly _timbre = new Subject<void>();
  /**
   * Cuando el mozo tiene que oirlo: un llamado nuevo, uno que cocina repitio, o
   * los que lo esperaban cuando se conecto.
   */
  readonly timbre = this._timbre.asObservable();

  private cliente: ClienteStomp | null = null;
  private arrancando = false;
  /** Sube al parar: lo que llegue de una conexion anterior se descarta. */
  private generacion = 0;
  private oyentes = 0;
  private cierreDiferido: ReturnType<typeof setTimeout> | null = null;
  private avisoDeCaida: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Otra sesion en el mismo navegador, o ninguna: el canal abierto llevaba
    // el token y los roles de antes.
    let tokenAnterior = this.sesion.tokenActual();
    effect(() => {
      const token = this.sesion.sesion()?.token;
      if (token === tokenAnterior) return;
      tokenAnterior = token;
      this.detener();
      if (token && this.oyentes > 0) void this.arrancar();
    });

    inject(DestroyRef).onDestroy(() => {
      this.oyentes = 0;
      this.detener();
    });
  }

  /**
   * Mantiene el canal abierto mientras viva quien lo pide. Va en el
   * constructor de un componente, y se suelta solo al destruirlo.
   */
  usar(): void {
    assertInInjectionContext(this.usar);
    this.oyentes++;
    if (this.cierreDiferido) {
      clearTimeout(this.cierreDiferido);
      this.cierreDiferido = null;
    }
    if (!this.cliente && !this.arrancando) void this.arrancar();
    inject(DestroyRef).onDestroy(() => this.soltar());
  }

  /** Cocina llama por una comanda lista. Repetirlo hace sonar otra vez el mismo llamado. */
  llamar(ordenId: string | null | undefined): void {
    if (!ordenId || !this.cliente || !this._conectado() || this._llamando().has(ordenId)) return;
    this.esperar(this._llamando, ordenId);
    this.cliente.publicar('/app/llamados/llamar', { ordenId });
  }

  /** El «Voy» del mozo. El primero que llega se lo lleva; a los demas se les quita el aviso. */
  atender(llamadoId: string): void {
    if (!this.cliente || !this._conectado() || this._respondiendo().has(llamadoId)) return;
    this.esperar(this._respondiendo, llamadoId);
    this.cliente.publicar('/app/llamados/atender', { llamadoId });
  }

  /** El llamado que cuenta para una comanda: el pendiente o, si no hay, el ultimo respondido. */
  deOrden(ordenId: string | null | undefined): LlamadoCocina | null {
    return llamadoDeOrden(this._llamados(), ordenId);
  }

  /** «Mesa M3», «Delivery»: a donde va lo que cocina tiene listo, en el idioma elegido. */
  donde(llamado: LlamadoCocina): string {
    return llamado.tipoOrden === 'MESA'
      ? this.i18n.t('comun.mesa', { numero: llamado.mesaNumero || '—' })
      : this.i18n.tEnum('tipoOrden', llamado.tipoOrden);
  }

  // --- ciclo del canal ------------------------------------------------------

  private async arrancar(): Promise<void> {
    if (!this.sesion.tieneAlgunRol(ROLES_DEL_LLAMADO)) return;
    const generacion = ++this.generacion;
    this.arrancando = true;
    this.vigilarCaida();
    try {
      const cliente = await this.fabrica({
        url: this.url(),
        cabeceras: () => this.cabeceras(),
        alConectar: () => this.alConectar(generacion),
        alCaerse: () => this.alCaerse(generacion),
      });
      if (generacion !== this.generacion) {
        cliente.desactivar();
        return;
      }
      this.cliente = cliente;
      cliente.activar();
    } catch {
      // Sin la libreria (el trozo no bajo) no hay llamado, y el resto de la app
      // sigue igual. La proxima pantalla que use el canal lo vuelve a intentar.
    } finally {
      if (generacion === this.generacion) this.arrancando = false;
    }
  }

  /** Entre dos pantallas que lo usan, el canal no se cierra y se vuelve a abrir. */
  private soltar(): void {
    this.oyentes = Math.max(0, this.oyentes - 1);
    if (this.oyentes > 0) return;
    this.cierreDiferido = setTimeout(() => {
      this.cierreDiferido = null;
      if (this.oyentes === 0) this.detener();
    }, GRACIA_AL_SALIR_MS);
  }

  private detener(): void {
    this.generacion++;
    this.arrancando = false;
    this.cliente?.desactivar();
    this.cliente = null;
    for (const temporizador of [this.cierreDiferido, this.avisoDeCaida]) {
      if (temporizador) clearTimeout(temporizador);
    }
    this.cierreDiferido = this.avisoDeCaida = null;
    this._conectado.set(false);
    this._sinConexion.set(false);
    this._llamados.set([]);
    this._mozos.set(null);
    this._llamando.set(new Set());
    this._respondiendo.set(new Set());
  }

  private alConectar(generacion: number): void {
    const cliente = this.cliente;
    if (generacion !== this.generacion || !cliente) return;
    this._conectado.set(true);
    this._sinConexion.set(false);
    if (this.avisoDeCaida) {
      clearTimeout(this.avisoDeCaida);
      this.avisoDeCaida = null;
    }

    // Una suscripcion por reconexion: STOMP no las recuerda. Los temas van
    // antes que la foto, para que nada de lo que pase entre medias se pierda.
    const roles = this.sesion.roles();
    const esMozo = roles.includes('MOZO');
    cliente.suscribir('/user/queue/llamados', (cuerpo) => this.recibir(cuerpo, false));
    if (esMozo) {
      cliente.suscribir('/topic/llamados/mozos', (cuerpo) => this.recibir(cuerpo, true));
    }
    if (roles.includes('COCINA') || roles.includes('ADMIN')) {
      cliente.suscribir('/topic/llamados/cocina', (cuerpo) => this.recibir(cuerpo, false));
    }
    cliente.suscribir('/app/llamados/estado', (cuerpo) => this.recibir(cuerpo, esMozo));
  }

  private alCaerse(generacion: number): void {
    if (generacion !== this.generacion) return;
    this._conectado.set(false);
    this.vigilarCaida();
  }

  private vigilarCaida(): void {
    if (this.avisoDeCaida) return;
    this.avisoDeCaida = setTimeout(() => {
      this.avisoDeCaida = null;
      if (!this._conectado()) this._sinConexion.set(true);
    }, AVISAR_CAIDA_MS);
  }

  // --- lo que llega -----------------------------------------------------------

  /** `suena`: el aviso vino por el canal de los mozos, y un llamado nuevo ahi se oye. */
  private recibir(cuerpo: string, suena: boolean): void {
    let aviso: AvisoLlamado;
    try {
      aviso = JSON.parse(cuerpo) as AvisoLlamado;
    } catch {
      return; // un aviso ilegible se ignora: el siguiente trae el estado al dia
    }

    if (aviso.mozosConectados !== null && aviso.mozosConectados !== undefined) {
      this._mozos.set(aviso.mozosConectados);
    }
    if (aviso.tipo === 'error') {
      this.alRechazo(aviso.mensaje);
      return;
    }

    this._llamados.update((lista) => aplicarAviso(lista, aviso));
    const llegados = aviso.llamados ?? [];

    if (aviso.tipo === 'llamado') {
      for (const llamado of llegados) this.soltarEspera(this._llamando, llamado.ordenId);
    }
    if (aviso.tipo === 'atendido') {
      for (const llamado of llegados) {
        if (!this._respondiendo().has(llamado.id)) continue;
        this.soltarEspera(this._respondiendo, llamado.id);
        this.avisos.exito(this.i18n.t('llamado.vas', { donde: this.donde(llamado) }));
      }
    }
    const traePendientes = llegados.some((l) => l.estado === 'PENDIENTE');
    if (suena && traePendientes && (aviso.tipo === 'llamado' || aviso.tipo === 'estado')) {
      this._timbre.next();
    }
  }

  /**
   * El servidor dijo que no: llamar por una comanda que no esta lista, o un
   * «Voy» que otro dio antes. El mensaje llega en espanol, igual que los de
   * HTTP. Al mozo que llego tarde no se le pinta de error: alguien ya va.
   */
  private alRechazo(mensaje: string | null): void {
    const eraUnVoy = this._respondiendo().size > 0;
    this._respondiendo.set(new Set());
    this._llamando.set(new Set());
    const texto = mensaje || this.i18n.t('llamado.noSePudo');
    if (eraUnVoy) this.avisos.info(texto);
    else this.avisos.error(texto);
  }

  // --- utilidades -------------------------------------------------------------

  private esperar(conjunto: WritableSignal<ReadonlySet<string>>, id: string): void {
    conjunto.update((ids) => new Set(ids).add(id));
    setTimeout(() => this.soltarEspera(conjunto, id), ESPERA_RESPUESTA_MS);
  }

  private soltarEspera(conjunto: WritableSignal<ReadonlySet<string>>, id: string): void {
    if (!conjunto().has(id)) return;
    conjunto.update((ids) => {
      const quedan = new Set(ids);
      quedan.delete(id);
      return quedan;
    });
  }

  private cabeceras(): Record<string, string> | null {
    const token = this.sesion.tokenActual();
    return token && !this.sesion.expirada() ? { Authorization: `Bearer ${token}` } : null;
  }

  /**
   * El backend en desarrollo (`http://localhost:8080`) o, en produccion, el
   * mismo origen que sirve la app. El esquema pasa a WebSocket: https da wss.
   */
  private url(): string {
    const origen = this.basePath || `${location.protocol}//${location.host}`;
    return origen.replace(/^http/, 'ws') + PUNTO_DE_CONEXION;
  }
}
