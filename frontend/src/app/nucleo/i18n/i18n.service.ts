import { Injectable, signal } from '@angular/core';

import { formatearFecha, type EstiloFecha } from './formatos';
import { IDIOMAS, IDIOMA_POR_DEFECTO, esIdioma, type Idioma } from './idioma';
import { ES, type ClaveI18n, type ClavePlural, type GrupoEnum } from './traducciones/es';

/** Un diccionario completo: todas las claves, ninguna suelta. */
export type Diccionario = Record<ClaveI18n, string>;

/**
 * Solo el espanol viaja en el bundle inicial, y es el respaldo mientras otro se carga; los otros dos se
 * piden al elegirlos: la tablet de cocina no tiene por que descargar los que no va a leer.
 */
const CARGADORES: Record<Idioma, () => Promise<Diccionario>> = {
  es: () => Promise.resolve(ES),
  en: () => import('./traducciones/en').then((m) => m.EN),
  pt: () => import('./traducciones/pt').then((m) => m.PT),
};

const CLAVE = 'chaquena.idioma';

/** Valores que se pueden meter en un `{hueco}`. */
export type Parametros = Record<string, string | number>;

/** Un hueco sin valor queda a la vista: «S/ {total}» es un fallo que alguien reporta; «S/ » parece un importe. */
function interpolar(texto: string, params?: Parametros): string {
  if (!params) return texto;
  return texto.replace(/\{(\w+)\}/g, (hueco, nombre: string) =>
    nombre in params ? String(params[nombre]) : hueco,
  );
}

/**
 * El idioma es una senal y no `@angular/localize`: se cambia sin recargar ni perder la comanda a medias. Las
 * plantillas llaman a `t(...)` y no a un pipe, que memorizaria el texto viejo; el idioma va en `localStorage`.
 */
@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly _idioma = signal<Idioma>(this.leer());

  /** Empieza en espanol aunque el guardado sea otro: es el respaldo, y `precargar` trae el suyo antes de pintar. */
  private readonly diccionario = signal<Diccionario>(ES);

  readonly idioma = this._idioma.asReadonly();

  /** La lista para el selector. Cada nombre va en su propio idioma. */
  readonly idiomas = IDIOMAS;

  constructor() {
    this.marcarDocumento(this._idioma());
  }

  /** Lo llama `provideAppInitializer`: sin esto, quien dejo la interfaz en ingles la veria arrancar en espanol. */
  async precargar(): Promise<void> {
    const idioma = this._idioma();
    if (idioma === IDIOMA_POR_DEFECTO) return;
    await this.traer(idioma);
  }

  /** Propiedad y no metodo: asi `t('...')` se escribe en la plantilla sin perder el `this`. */
  readonly t = (clave: ClaveI18n, params?: Parametros): string =>
    interpolar(this.diccionario()[clave], params);

  /** Singular o plural segun `n`, que ademas queda como parametro: el numero que decide es el que se pinta. */
  readonly tp = (base: ClavePlural, n: number, params?: Parametros): string =>
    this.t(`${base}.${n === 1 ? 'uno' : 'otros'}` as ClaveI18n, { n, ...params });

  /** Un valor que la lista no conoce sale crudo: mejor `EN_TRANSITO` a la vista que un hueco en blanco. */
  readonly tEnum = (grupo: GrupoEnum, valor: string | null | undefined): string => {
    if (!valor) return '—';
    const clave = `${grupo}.${valor}` as ClaveI18n;
    return (this.diccionario() as Record<string, string | undefined>)[clave] ?? valor;
  };

  /**
   * Lee la senal del idioma como `t`: una tabla pintada se reescribe al cambiar de idioma. Sustituye al
   * `DatePipe`, cuyo `LOCALE_ID` se fija al arrancar y seguiria en el idioma inicial.
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
   * Diccionario e idioma cambian a la vez: si no, el selector diria «English» sobre una pantalla en
   * espanol. Si la descarga falla, no cambia nada.
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

  /** `<html lang>` hace que el lector de pantalla use la voz correcta y que el navegador ofrezca o no traducir. */
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
