import type { Idioma } from './idioma';

/**
 * La region con la que se formatean las fechas de cada idioma.
 *
 * `idioma.ts` deja los codigos sin region —`es` y no `es-PE`— porque eso es lo
 * que viaja en `<html lang>` y en `Accept-Language`, donde lo que importa es la
 * lengua. Aqui hace falta lo contrario: `Intl` no sabe ordenar dia y mes con un
 * `en` a secas, necesita saber de que ingles se habla.
 *
 * Las tres regiones son las de quien lee, no las del local. El peruano escribe
 * 11/09; el estadounidense escribe esa misma fecha 09/11 y lee 11/09 como el 9
 * de noviembre. No es una preferencia estetica: es la diferencia entre dos
 * dias distintos, y por eso la fecha si cambia de formato con el idioma aunque
 * los importes no lo hagan —un sol es un sol lo lea quien lo lea.
 */
const REGION: Record<Idioma, string> = {
  es: 'es-PE',
  en: 'en-US',
  pt: 'pt-BR',
};

/**
 * Los dos estilos de fecha que usa la interfaz.
 *
 * `corta` va en las tablas, donde la fecha es una columna estrecha al lado de
 * datos mas importantes. `larga` va en los detalles, donde hay sitio y donde
 * conviene que no quede ninguna duda sobre que dia es: el mes con letras no se
 * puede leer al reves.
 */
export type EstiloFecha = 'corta' | 'larga';

const OPCIONES: Record<EstiloFecha, Intl.DateTimeFormatOptions> = {
  corta: {
    day: '2-digit',
    month: '2-digit',
    hour: 'numeric',
    minute: '2-digit',
  },
  larga: {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: 'numeric',
    minute: '2-digit',
  },
};

/**
 * Construir un `Intl.DateTimeFormat` no es gratis y la tabla del kardex lo
 * pediria una vez por fila. Como solo hay tres idiomas y dos estilos, el cache
 * tiene como mucho seis entradas y no hace falta vaciarlo nunca.
 */
const CACHE = new Map<string, Intl.DateTimeFormat>();

function formateador(idioma: Idioma, estilo: EstiloFecha): Intl.DateTimeFormat {
  const llave = `${idioma}:${estilo}`;
  let formateador = CACHE.get(llave);

  if (!formateador) {
    formateador = new Intl.DateTimeFormat(REGION[idioma], OPCIONES[estilo]);
    CACHE.set(llave, formateador);
  }

  return formateador;
}

/**
 * La fecha escrita como la escribe quien la esta leyendo.
 *
 * Lo que llega del backend es una cadena ISO 8601, que es inequivoca pero no se
 * le ensena a nadie. Si no llega nada, o llega algo que no es una fecha, se
 * devuelve la raya —igual que en `tEnum`— en vez de «Invalid Date», que es un
 * mensaje para quien programa y no para quien mira la pantalla.
 */
export function formatearFecha(
  valor: string | number | Date | null | undefined,
  idioma: Idioma,
  estilo: EstiloFecha = 'corta',
): string {
  if (valor === null || valor === undefined || valor === '') return '—';

  const fecha = valor instanceof Date ? valor : new Date(valor);
  if (Number.isNaN(fecha.getTime())) return '—';

  return formateador(idioma, estilo).format(fecha);
}
