import { DestroyRef, Injectable, effect, inject, signal } from '@angular/core';
import { BehaviorSubject, EMPTY, Observable, Subject, asyncScheduler, interval, merge } from 'rxjs';
import { filter, map, switchMap, throttleTime } from 'rxjs/operators';

// Del archivo concreto y no del barril `api`, como todo lo que vive en `nucleo`.
import { Configuration } from '../../api/configuration';
import type { AvisoTiempoRealDtoTemaEnum } from '../../api/model/aviso-tiempo-real-dto';
import { SesionService } from '../sesion/sesion.service';

/** Los temas del stream: `'COCINA'`, `'MESAS'`... Salen del contrato. */
export type TemaTiempoReal = `${AvisoTiempoRealDtoTemaEnum}`;

/** Espera entre reintentos: 1 s, 2 s, 4 s... hasta medio minuto. */
const ESPERA_INICIAL_MS = 1_000;
const ESPERA_MAXIMA_MS = 30_000;

/** El servidor late cada 25 s: 60 s sin un byte es una conexion muerta aunque el navegador no lo sepa. */
const SILENCIO_MAXIMO_MS = 60_000;

/** Cuanto se mantiene abierta la conexion despues de que la ultima pantalla deja de escuchar. */
const GRACIA_AL_SALIR_MS = 5_000;

/** Varios avisos seguidos del mismo tema se juntan en una sola recarga. */
const AVISOS_JUNTOS_MS = 1_000;

/**
 * SSE leido con `fetch`: `EventSource` no deja poner `Authorization`, y en la URL el token quedaria en los
 * registros. El aviso no trae datos, asi que sin conexion `cambios()` vuelve al refresco periodico.
 */
@Injectable({ providedIn: 'root' })
export class TiempoRealService {
  private readonly sesion = inject(SesionService);
  // Opcional como en los servicios generados: las pruebas de pagina no lo proveen.
  private readonly basePath = inject(Configuration, { optional: true })?.basePath ?? '';

  private readonly _conectado = signal(false);
  /** Si los avisos llegan en vivo. Mientras es `false`, las pantallas refrescan solas. */
  readonly conectado = this._conectado.asReadonly();
  // Un Subject al lado de la senal y no `toObservable`: este necesita un ciclo de
  // deteccion de cambios para emitir, y el respaldo no puede depender de eso.
  private readonly conectado$ = new BehaviorSubject(false);

  private readonly avisos$ = new Subject<TemaTiempoReal>();
  private readonly reconectado$ = new Subject<void>();

  private oyentes = 0;
  private intentos = 0;
  private huboCaida = false;
  private controlador: AbortController | null = null;
  private reintento: ReturnType<typeof setTimeout> | null = null;
  private cierreDiferido: ReturnType<typeof setTimeout> | null = null;
  private vigilante: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    // Otra sesion en el mismo navegador, o ninguna: la conexion abierta llevaba
    // el token de antes.
    let tokenAnterior = this.sesion.tokenActual();
    effect(() => {
      const token = this.sesion.sesion()?.token;
      if (token === tokenAnterior) return;
      tokenAnterior = token;
      this.detener();
      if (token && this.oyentes > 0) void this.conectar();
    });

    inject(DestroyRef).onDestroy(() => {
      this.oyentes = 0;
      this.detener();
    });
  }

  /**
   * Emite cuando la pantalla debe volver a pedir: al llegar un aviso de sus temas, al reconectar y, sin
   * conexion, cada `respaldoMs`. No emite al suscribirse.
   */
  cambios(temas: readonly TemaTiempoReal[], respaldoMs: number): Observable<void> {
    return new Observable<void>((suscriptor) => {
      this.engancharse();

      const avisos = this.avisos$.pipe(
        filter((tema) => temas.includes(tema)),
        throttleTime(AVISOS_JUNTOS_MS, asyncScheduler, { leading: true, trailing: true }),
        map(() => undefined),
      );
      const respaldo = this.conectado$.pipe(
        switchMap((vivo) => (vivo ? EMPTY : interval(respaldoMs).pipe(map(() => undefined)))),
      );

      const suscripcion = merge(avisos, this.reconectado$, respaldo).subscribe(suscriptor);
      return () => {
        suscripcion.unsubscribe();
        this.soltarse();
      };
    });
  }

  // --- ciclo de la conexion -------------------------------------------------

  private engancharse(): void {
    this.oyentes++;
    if (this.cierreDiferido) {
      clearTimeout(this.cierreDiferido);
      this.cierreDiferido = null;
    }
    if (this.oyentes === 1 && !this.controlador && !this.reintento) void this.conectar();
  }

  /** Entre dos pantallas que escuchan, la conexion no se cierra y se vuelve a abrir. */
  private soltarse(): void {
    this.oyentes = Math.max(0, this.oyentes - 1);
    if (this.oyentes > 0) return;
    this.cierreDiferido = setTimeout(() => {
      this.cierreDiferido = null;
      if (this.oyentes === 0) this.detener();
    }, GRACIA_AL_SALIR_MS);
  }

  private detener(): void {
    this.controlador?.abort();
    this.controlador = null;
    for (const temporizador of [this.reintento, this.cierreDiferido, this.vigilante]) {
      if (temporizador) clearTimeout(temporizador);
    }
    this.reintento = this.cierreDiferido = this.vigilante = null;
    this.intentos = 0;
    this.huboCaida = false;
    this.anotarConexion(false);
  }

  private async conectar(): Promise<void> {
    this.reintento = null;
    const token = this.sesion.tokenActual();
    if (!token || this.sesion.expirada() || this.oyentes === 0) return;

    const controlador = new AbortController();
    this.controlador = controlador;

    try {
      const respuesta = await fetch(`${this.basePath}/api/v1/eventos/stream`, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
        cache: 'no-store',
        signal: controlador.signal,
      });
      // Sin permiso no hay reintento que lo arregle; el refresco periodico sigue.
      if (respuesta.status === 401 || respuesta.status === 403) {
        this.controlador = null;
        this.caer();
        return;
      }
      if (!respuesta.ok || !respuesta.body) throw new Error(`HTTP ${respuesta.status}`);

      // Termina cuando el servidor cierra a los diez minutos: su `listo` ya puso los intentos a cero
      // y se reabre enseguida. Un proxy que corta sin dejar pasar nada espera mas cada vez.
      await this.leer(respuesta.body, controlador);
    } catch {
      if (controlador.signal.aborted && this.controlador !== controlador) return;
    }

    if (this.controlador !== controlador) return;
    this.controlador = null;
    this.caer();
    this.programarReintento();
  }

  private caer(): void {
    if (this._conectado()) this.huboCaida = true;
    this.anotarConexion(false);
    if (this.vigilante) clearTimeout(this.vigilante);
    this.vigilante = null;
  }

  private programarReintento(): void {
    if (this.oyentes === 0) return;
    const espera =
      this.intentos === 0
        ? 0
        : Math.min(ESPERA_MAXIMA_MS, ESPERA_INICIAL_MS * 2 ** (this.intentos - 1));
    this.intentos++;
    this.reintento = setTimeout(() => void this.conectar(), espera + Math.random() * 250);
  }

  // --- lectura del stream ---------------------------------------------------

  private async leer(
    cuerpo: ReadableStream<Uint8Array>,
    controlador: AbortController,
  ): Promise<void> {
    const lector = cuerpo.getReader();
    // Con `stream: true` una letra con tilde partida entre dos trozos no se rompe.
    const decodificador = new TextDecoder();
    let pendiente = '';
    this.vigilar(controlador);

    for (;;) {
      const { value, done } = await lector.read();
      if (done) return;
      this.vigilar(controlador);

      pendiente += decodificador.decode(value, { stream: true });
      // Un evento termina en una linea en blanco; el ultimo trozo puede estar a medias.
      const bloques = pendiente.split(/\r?\n\r?\n/);
      pendiente = bloques.pop() ?? '';
      for (const bloque of bloques) this.procesar(bloque);
    }
  }

  private procesar(bloque: string): void {
    let evento = 'message';
    const datos: string[] = [];
    for (const linea of bloque.split(/\r?\n/)) {
      if (linea.startsWith(':')) continue; // el latido
      const corte = linea.indexOf(':');
      const campo = corte < 0 ? linea : linea.slice(0, corte);
      const valor = corte < 0 ? '' : linea.slice(corte + 1).replace(/^ /, '');
      if (campo === 'event') evento = valor;
      else if (campo === 'data') datos.push(valor);
    }

    if (evento === 'listo') {
      this.intentos = 0;
      this.anotarConexion(true);
      if (this.huboCaida) {
        this.huboCaida = false;
        this.reconectado$.next();
      }
      return;
    }
    if (evento === 'aviso') {
      try {
        const aviso = JSON.parse(datos.join('\n')) as { tema?: TemaTiempoReal };
        if (aviso.tema) this.avisos$.next(aviso.tema);
      } catch {
        /* un aviso ilegible se ignora: el siguiente trae lo mismo */
      }
    }
  }

  private anotarConexion(viva: boolean): void {
    this._conectado.set(viva);
    if (this.conectado$.value !== viva) this.conectado$.next(viva);
  }

  private vigilar(controlador: AbortController): void {
    if (this.vigilante) clearTimeout(this.vigilante);
    this.vigilante = setTimeout(() => controlador.abort(), SILENCIO_MAXIMO_MS);
  }
}
