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
 * Los estilos de fecha que usa la interfaz.
 *
 * `corta` va en las tablas, donde la fecha es una columna estrecha al lado de
 * datos mas importantes. `larga` va en los detalles, donde hay sitio y donde
 * conviene que no quede ninguna duda sobre que dia es: el mes con letras no se
 * puede leer al reves. `hora` va donde el dia ya se sabe, como la agenda de
 * reservas de un dia.
 */
export type EstiloFecha = 'corta' | 'larga' | 'dia' | 'hora';

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
  // Sin hora: la vigencia de una promocion o un cupon se cuenta en dias.
  dia: {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  },
  hora: {
    hour: 'numeric',
    minute: '2-digit',
  },
};

/**
 * Construir un `Intl.DateTimeFormat` no es gratis y la tabla del kardex lo
 * pediria una vez por fila. Como solo hay tres idiomas y cuatro estilos, el cache
 * tiene como mucho doce entradas y no hace falta vaciarlo nunca.
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

/**
 * Fecha en ISO pero con el desfase local, no en Z.
 *
 * `toISOString()` da el instante correcto en UTC, y con eso el servidor agrupa
 * por horas de Greenwich: en Lima la cena del sabado aparece repartida entre el
 * sabado y el domingo. Mandando el desfase, el dia empieza y termina donde lo
 * vive el local.
 */
export function fechaIsoLocal(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  const desfase = -fecha.getTimezoneOffset();
  const signo = desfase >= 0 ? '+' : '-';
  const horas = dos(Math.floor(Math.abs(desfase) / 60));
  const minutos = dos(Math.abs(desfase) % 60);

  return (
    `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}` +
    `T${dos(fecha.getHours())}:${dos(fecha.getMinutes())}:${dos(fecha.getSeconds())}` +
    `${signo}${horas}:${minutos}`
  );
}

/** Medianoche de hoy, o de hace `dias` dias, en la hora del local. */
export function inicioDelDia(dias = 0): Date {
  const fecha = new Date();
  fecha.setDate(fecha.getDate() - dias);
  fecha.setHours(0, 0, 0, 0);
  return fecha;
}

/**
 * Un dia sin hora («2026-09-16») como fecha local. `new Date('2026-09-16')` lo
 * lee como medianoche UTC, que en Lima es el dia anterior a las siete de la
 * noche: un lote que vence el 16 se pintaria como del 15.
 */
export function fechaDeDia(dia: string | null | undefined): Date | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dia ?? '');
  return partes ? new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3])) : null;
}

/**
 * El codigo corto con el que se nombra una comanda en voz alta: los ocho
 * primeros caracteres del UUID, los mismos que el POS imprime como correlativo.
 */
export function codigoDeOrden(id: string | null | undefined): string {
  return (id ?? '').slice(0, 8).toUpperCase();
}

/**
 * Minutos transcurridos escritos como se dicen: «47 min», «3 h 5 min», «8 d 4 h».
 *
 * El backend manda minutos a secas, y una comanda olvidada de hace ocho dias se
 * pintaba «11803 min»: un numero que nadie convierte de cabeza y que ademas
 * rompia la tarjeta de cocina. Pasado un dia los minutos dejan de importar y se
 * omiten.
 *
 * Las unidades son las mismas abreviaturas en espanol, ingles y portugues, por
 * eso no pasan por el diccionario.
 */
export function formatearDuracion(minutos: number | null | undefined): string {
  if (minutos === null || minutos === undefined || !Number.isFinite(minutos)) return '—';

  const total = Math.max(0, Math.floor(minutos));
  if (total < 60) return `${total} min`;

  const dias = Math.floor(total / 1440);
  const horas = Math.floor((total % 1440) / 60);
  const resto = total % 60;

  if (dias > 0) return horas > 0 ? `${dias} d ${horas} h` : `${dias} d`;
  return resto > 0 ? `${horas} h ${resto} min` : `${horas} h`;
}
