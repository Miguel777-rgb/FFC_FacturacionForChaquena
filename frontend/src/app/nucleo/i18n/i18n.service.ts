import { Injectable, signal } from '@angular/core';

import { formatearFecha, type EstiloFecha } from './formatos';
import { IDIOMAS, IDIOMA_POR_DEFECTO, esIdioma, type Idioma } from './idioma';
import { ES, type ClaveI18n, type ClavePlural, type GrupoEnum } from './traducciones/es';

/** Un diccionario completo: todas las claves, ninguna suelta. */
export type Diccionario = Record<ClaveI18n, string>;

/**
 * Solo el espanol viaja en el bundle inicial. Los otros dos se piden cuando
 * alguien los elige.
 *
 * Los tres juntos pesan casi noventa kilobytes de texto, y la pantalla de
 * cocina —que arranca en la tablet del local y no cambia de idioma nunca—
 * no tiene por que descargar los dos que no va a leer. El espanol se queda
 * dentro porque ademas es el respaldo mientras otro se esta cargando.
 */
const CARGADORES: Record<Idioma, () => Promise<Diccionario>> = {
  es: () => Promise.resolve(ES),
  en: () => import('./traducciones/en').then((m) => m.EN),
  pt: () => import('./traducciones/pt').then((m) => m.PT),
};

const CLAVE = 'chaquena.idioma';

/** Valores que se pueden meter en un `{hueco}`. */
export type Parametros = Record<string, string | number>;

/**
 * Sustituye los `{huecos}` por sus valores.
 *
 * Un hueco sin valor se deja tal cual en vez de borrarse: «S/ {total}» a la
 * vista es un fallo evidente que alguien reporta, mientras que «S/ » parece un
 * importe que de verdad no existe.
 */
function interpolar(texto: string, params?: Parametros): string {
  if (!params) return texto;
  return texto.replace(/\{(\w+)\}/g, (hueco, nombre: string) =>
    nombre in params ? String(params[nombre]) : hueco,
  );
}

/**
 * El idioma de la interfaz y el texto que le corresponde.
 *
 * Es una senal, no una constante de compilacion: cambiar de idioma repinta la
 * aplicacion en el sitio, sin recargar y sin perder la comanda a medias que
 * hubiera en pantalla. Ese es el motivo de no usar `@angular/localize`, que
 * compila un bundle por idioma y obliga a servir tres copias y a recargar para
 * cambiar. Aqui el cocinero cambia el idioma y sigue con la misma cola delante.
 *
 * Las plantillas leen `t(...)` directamente y no a traves de un pipe. Un pipe
 * puro memoriza por sus argumentos, y como la clave no cambia al cambiar el
 * idioma, devolveria el texto viejo; uno impuro se ejecutaria en cada ciclo.
 * Llamar a la funcion deja que el consumidor reactivo de la plantilla vea la
 * senal que hay dentro, que es exactamente lo que hace falta.
 *
 * El idioma vive en `localStorage` —como el logo y el panel plegado, y al
 * reves que la sesion— porque es una preferencia del dispositivo y no del
 * turno: la tablet de la cocina no cambia de idioma porque cambie el cocinero.
 *
 * `cambiar` es asincrono porque el diccionario que no es el espanol se
 * descarga al elegirlo. Quien lo llama no tiene que esperar a nada: la
 * pantalla se repinta sola cuando la senal cambia.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly _idioma = signal<Idioma>(this.leer());

  /**
   * El texto que se esta pintando. Empieza en espanol aunque el idioma
   * guardado sea otro: es el respaldo hasta que llegue el suyo, y `precargar`
   * lo trae antes de que se pinte la primera pantalla.
   */
  private readonly diccionario = signal<Diccionario>(ES);

  readonly idioma = this._idioma.asReadonly();

  /** La lista para el selector. Cada nombre va en su propio idioma. */
  readonly idiomas = IDIOMAS;

  constructor() {
    this.marcarDocumento(this._idioma());
  }

  /**
   * Trae el diccionario del idioma guardado antes de arrancar la aplicacion.
   *
   * Lo llama `provideAppInitializer`. Sin esto, quien dejo la interfaz en
   * ingles veria la primera pantalla en espanol durante un instante, que es
   * justo el momento en que uno esta leyendo para orientarse.
   */
  async precargar(): Promise<void> {
    const idioma = this._idioma();
    if (idioma === IDIOMA_POR_DEFECTO) return;
    await this.traer(idioma);
  }

  /**
   * El texto de una clave, con sus parametros ya puestos.
   *
   * Va como propiedad y no como metodo para poder escribir `t('...')` en la
   * plantilla sin perder el `this`.
   */
  readonly t = (clave: ClaveI18n, params?: Parametros): string =>
    interpolar(this.diccionario()[clave], params);

  /**
   * La variante singular o plural de una clave, segun `n`.
   *
   * `n` queda disponible como parametro sin repetirlo en cada llamada, que es
   * el caso normal: el numero que decide el plural es el mismo que se pinta.
   */
  readonly tp = (base: ClavePlural, n: number, params?: Parametros): string =>
    this.t(`${base}.${n === 1 ? 'uno' : 'otros'}` as ClaveI18n, { n, ...params });

  /**
   * El nombre legible de un valor de enumeracion del contrato.
   *
   * Si el servidor manda un valor que esta lista todavia no conoce —un estado
   * nuevo, un canal nuevo—, se devuelve el valor crudo. Es feo y se nota, que
   * es justo lo que se quiere: mejor `EN_TRANSITO` a la vista que un hueco en
   * blanco donde deberia haber un estado.
   */
  readonly tEnum = (grupo: GrupoEnum, valor: string | null | undefined): string => {
    if (!valor) return '—';
    const clave = `${grupo}.${valor}` as ClaveI18n;
    return (this.diccionario() as Record<string, string | undefined>)[clave] ?? valor;
  };

  /**
   * Una fecha del backend escrita como la escribe quien la esta leyendo.
   *
   * Lee la senal del idioma igual que `t`, asi que una tabla ya pintada se
   * reescribe sola al cambiar de bandera, sin recargar y sin volver a pedir los
   * datos: lo que cambia es como se pinta la misma fecha, no cual es.
   *
   * Sustituye al `DatePipe` de Angular, que resuelve su locale por `LOCALE_ID`
   * —un valor de inyeccion que se fija al arrancar— y por tanto seguiria
   * escribiendo en el idioma inicial despues de cambiarlo. `Intl` ya viene en
   * el navegador y no obliga a registrar los datos de cada locale.
   */
  readonly fecha = (
    valor: string | number | Date | null | undefined,
    estilo: EstiloFecha = 'corta',
  ): string => formatearFecha(valor, this._idioma(), estilo);

  async cambiar(idioma: Idioma): Promise<void> {
    if (!esIdioma(idioma) || idioma === this._idioma()) return;

    await this.traer(idioma);
    this.marcarDocumento(idioma);
    try {
      localStorage.setItem(CLAVE, idioma);
    } catch {
      /* sin almacenamiento el idioma vale para esta pestana y se pierde al recargar */
    }
  }

  /**
   * Descarga el diccionario y lo pone en servicio junto con su idioma.
   *
   * Los dos van a la vez a proposito: si el idioma cambiara antes de que el
   * texto llegue, el `<select>` diria «English» sobre una pantalla todavia en
   * espanol. Si la descarga falla —la red se cayo entre la carga de la pagina
   * y el clic—, no se cambia nada y se sigue leyendo lo que ya habia.
   */
  private async traer(idioma: Idioma): Promise<void> {
    try {
      const diccionario = await CARGADORES[idioma]();
      this.diccionario.set(diccionario);
      this._idioma.set(idioma);
    } catch {
      /* sin diccionario no se cambia de idioma: se sigue con el que ya estaba */
    }
  }

  /**
   * `<html lang>` no es decoracion: es lo que hace que un lector de pantalla
   * pronuncie el texto con la voz correcta y que el navegador ofrezca —o no—
   * traducir la pagina.
   */
  private marcarDocumento(idioma: Idioma): void {
    try {
      document.documentElement.lang = idioma;
    } catch {
      /* sin DOM (pruebas fuera del navegador) no hay nada que marcar */
    }
  }

  private leer(): Idioma {
    try {
      const guardado = localStorage.getItem(CLAVE);
      return esIdioma(guardado) ? guardado : IDIOMA_POR_DEFECTO;
    } catch {
      return IDIOMA_POR_DEFECTO;
    }
  }
}
