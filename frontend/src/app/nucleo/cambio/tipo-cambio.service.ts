import { Injectable, computed, signal } from '@angular/core';

/** Cuantos soles cuesta un dolar, y de cuando es el dato. */
export interface TipoCambio {
  solesPorDolar: number;
  consultadoEn: Date;
}

const CLAVE = 'chaquena.tipoCambio';

/**
 * Cada cuanto se vuelve a preguntar. Es la cache que el propio servicio declara
 * en su cabecera, y solo decide cuando sale una peticion nueva.
 */
const RECONSULTA_MS = 60 * 60 * 1000;

/**
 * Cuando la cotizacion deja de ser la vigente. Es otra cosa que lo anterior, y
 * confundirlas fue un error: el servicio publica <b>una vez al dia</b>, asi que
 * la cotizacion de esta manana tiene doce horas y sigue siendo la buena. Con el
 * plazo de reconsulta, la pantalla anunciaba la fecha casi siempre y el aviso
 * dejaba de significar nada. Veintiseis horas dan margen para que la
 * publicacion del dia siguiente llegue sin que la anterior parezca vieja.
 */
const VIGENCIA_MS = 26 * 60 * 60 * 1000;

/**
 * Si la red no contesta en este plazo, se sigue con lo que hubiera guardado. El
 * cajero no puede esperar a un tercero para cobrar.
 */
const ESPERA_MAXIMA_MS = 4000;

const FUENTE = 'https://open.er-api.com/v6/latest/USD';

/** Lo unico que se lee de la respuesta; el resto de campos no se toca. */
interface RespuestaFuente {
  result?: string;
  time_last_update_unix?: number;
  rates?: Record<string, number>;
}

/**
 * El cambio del dolar a soles, traido de un servicio publico ajeno al sistema.
 *
 * Es el unico punto del frontend que habla con un tercero. Todo lo demas sale
 * del backend propio a traves del cliente generado.
 *
 * <b>Por que `fetch` y no `HttpClient`.</b> Las dos peticiones que salen por
 * `HttpClient` pasan por `idiomaInterceptor` y `erroresInterceptor`, y los dos
 * estan escritos para el backend propio: el primero anuncia el idioma del
 * usuario, que a un tercero no le incumbe, y el segundo saca un aviso rojo
 * cuando algo falla. Un servicio de cotizaciones que no responde no es un error
 * que interrumpa un cobro, asi que esta llamada se queda fuera de esa tuberia
 * por construccion y no por una excepcion dentro de ella. `TiempoRealService` ya
 * usa `fetch` por un motivo distinto y el mismo patron.
 *
 * <b>Que pasa cuando el tercero no esta.</b> La ultima respuesta buena se
 * guarda en el dispositivo con su hora. Si la consulta falla, caduca o tarda
 * demasiado, se sigue mostrando esa, y `fresco()` pasa a `false` para que la
 * pantalla pueda decir de cuando es. Solo cuando no hay ninguna guardada
 * —primer arranque sin red— no hay nada que ensenar.
 *
 * Vive en `localStorage` y no en `sessionStorage`: la cotizacion es del dia, no
 * del turno, y no dice nada de quien la miro.
 */
@Injectable({ providedIn: 'root' })
export class TipoCambioService {
  private readonly _cambio = signal<TipoCambio | null>(this.leer());
  private readonly _consultando = signal(false);

  readonly cambio = this._cambio.asReadonly();
  readonly consultando = this._consultando.asReadonly();

  /** Si la cotizacion guardada sigue siendo la vigente. */
  readonly fresco = computed(() => {
    const actual = this._cambio();
    return actual !== null && this.edadDe(actual) < VIGENCIA_MS;
  });

  /** Si toca volver a preguntar. Mas exigente que `fresco`, y por otro motivo. */
  private readonly recienConsultado = computed(() => {
    const actual = this._cambio();
    return actual !== null && this.edadDe(actual) < RECONSULTA_MS;
  });

  private pendiente: Promise<void> | null = null;

  /**
   * Convierte un importe en soles a dolares, o `null` si todavia no hay
   * cotizacion. Devolver `null` y no cero es lo que deja a la plantilla no
   * pintar nada en vez de prometer que algo cuesta US$ 0.
   */
  aDolares(soles: number): number | null {
    const actual = this._cambio();
    if (actual === null || actual.solesPorDolar <= 0) return null;
    return soles / actual.solesPorDolar;
  }

  /**
   * Trae la cotizacion si hace falta. Es idempotente: varias pantallas pueden
   * llamarla a la vez y sale una sola peticion.
   */
  asegurar(): Promise<void> {
    if (this.recienConsultado()) return Promise.resolve();
    this.pendiente ??= this.consultar().finally(() => {
      this.pendiente = null;
    });
    return this.pendiente;
  }

  private async consultar(): Promise<void> {
    this._consultando.set(true);
    const corte = new AbortController();
    const reloj = setTimeout(() => corte.abort(), ESPERA_MAXIMA_MS);

    try {
      const respuesta = await fetch(FUENTE, { signal: corte.signal });
      if (!respuesta.ok) return;

      const cuerpo = (await respuesta.json()) as RespuestaFuente;
      const soles = cuerpo.rates?.['PEN'];
      if (cuerpo.result !== 'success' || typeof soles !== 'number' || soles <= 0) return;

      // La hora que interesa es la de la publicacion, no la de la consulta: dos
      // navegadores que preguntan con una hora de diferencia leen la misma
      // cotizacion, y decir que una es mas reciente seria mentir.
      const publicado = cuerpo.time_last_update_unix;
      const consultadoEn =
        typeof publicado === 'number' ? new Date(publicado * 1000) : new Date();

      this.guardar({ solesPorDolar: soles, consultadoEn });
    } catch {
      /* Sin red, cortado por el reloj o con un cuerpo que no es JSON: se
         conserva lo ultimo bueno y `fresco()` ya avisa de que no es de ahora. */
    } finally {
      clearTimeout(reloj);
      this._consultando.set(false);
    }
  }

  private edadDe(cambio: TipoCambio): number {
    return Date.now() - cambio.consultadoEn.getTime();
  }

  private guardar(cambio: TipoCambio): void {
    this._cambio.set(cambio);
    try {
      localStorage.setItem(
        CLAVE,
        JSON.stringify({
          solesPorDolar: cambio.solesPorDolar,
          consultadoEn: cambio.consultadoEn.toISOString(),
        }),
      );
    } catch {
      /* sin almacenamiento la cotizacion vale para esta pestana */
    }
  }

  private leer(): TipoCambio | null {
    try {
      const crudo = localStorage.getItem(CLAVE);
      if (!crudo) return null;

      const guardado = JSON.parse(crudo) as { solesPorDolar?: unknown; consultadoEn?: unknown };
      const soles = guardado.solesPorDolar;
      const fecha = new Date(String(guardado.consultadoEn));
      if (typeof soles !== 'number' || soles <= 0 || Number.isNaN(fecha.getTime())) return null;

      return { solesPorDolar: soles, consultadoEn: fecha };
    } catch {
      // Un JSON corrupto no puede dejar la pantalla de cobro sin arrancar.
      return null;
    }
  }
}
