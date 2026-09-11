/**
 * Los tres idiomas de la interfaz.
 *
 * El espanol es el del local y el que manda: es la fuente de las claves y el
 * unico diccionario que se escribe a mano de cero. Los otros dos son
 * `Record<ClaveI18n, string>`, asi que el compilador no deja publicar una
 * traduccion incompleta.
 *
 * El codigo es tambien el que se pone en `<html lang>` y el que viaja en
 * `Accept-Language`: son etiquetas BCP 47 validas por si solas, sin region.
 * No se fija la region a proposito —`es` y no `es-PE`— porque lo que se
 * traduce son las palabras, no los formatos: los importes son soles y se
 * escriben como se escriben en el local.
 */
export const IDIOMAS = [
  { codigo: 'es', nombre: 'Español' },
  { codigo: 'en', nombre: 'English' },
  { codigo: 'pt', nombre: 'Português' },
] as const;

export type Idioma = (typeof IDIOMAS)[number]['codigo'];

/**
 * El idioma del local. Se elige a mano; no se deduce del navegador.
 *
 * Deducirlo tendria sentido en una web abierta a cualquiera, pero esto es la
 * herramienta de trabajo de un local peruano: una tablet de cocina configurada
 * en ingles no debe recibir al cocinero en un idioma que no espera. Quien
 * necesite otro lo cambia una vez y el dispositivo lo recuerda.
 */
export const IDIOMA_POR_DEFECTO: Idioma = 'es';

/**
 * El nombre de cada idioma va escrito en el propio idioma —«English», no
 * «Ingles»— porque quien busca su lengua en la lista todavia no entiende la
 * que esta viendo. Por eso `IDIOMAS` no se traduce.
 */
export function esIdioma(valor: string | null): valor is Idioma {
  return IDIOMAS.some((i) => i.codigo === valor);
}
