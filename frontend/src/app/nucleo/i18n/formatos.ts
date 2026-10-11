import type { Idioma } from './idioma';

/**
 * `Intl` necesita la region para ordenar dia y mes, y son las de quien lee: 11/09 es septiembre en Lima y
 * noviembre en Estados Unidos. Por eso la fecha cambia de formato con el idioma y los importes no.
 */
const REGION: Record<Idioma, string> = {
  es: 'es-PE',
  en: 'en-US',
  pt: 'pt-BR',
};

/** `corta` en tablas, `larga` en detalles (el mes con letras no se lee al reves), `hora` donde el dia ya se sabe. */
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

/** Un `Intl.DateTimeFormat` no es gratis y el kardex lo pediria por fila; son doce entradas como mucho. */
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

/** Sin fecha valida devuelve la raya, como `tEnum`, y no «Invalid Date», que es un mensaje para quien programa. */
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
 * ISO con el desfase local y no en Z: con UTC el servidor agrupa por horas de Greenwich y la cena del
 * sabado en Lima se reparte entre sabado y domingo.
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

/** `new Date('2026-09-16')` es medianoche UTC, el dia anterior en Lima: un lote que vence el 16 saldria del 15. */
export function fechaDeDia(dia: string | null | undefined): Date | null {
  const partes = /^(\d{4})-(\d{2})-(\d{2})$/.exec(dia ?? '');
  return partes ? new Date(Number(partes[1]), Number(partes[2]) - 1, Number(partes[3])) : null;
}

/** Los ocho primeros caracteres del UUID: el correlativo que imprime el POS y que se dice en voz alta. */
export function codigoDeOrden(id: string | null | undefined): string {
  return (id ?? '').slice(0, 8).toUpperCase();
}

/**
 * «47 min», «3 h 5 min», «8 d 4 h»: una comanda olvidada se pintaba «11803 min». Las abreviaturas son las
 * mismas en los tres idiomas, por eso no pasan por el diccionario.
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

/** «12 s», «1 min 5 s»: lo que tarda un mozo en responder; pasada la hora, como `formatearDuracion`. */
export function formatearSegundos(segundos: number | null | undefined): string {
  if (segundos === null || segundos === undefined || !Number.isFinite(segundos)) return '—';

  const total = Math.max(0, Math.round(segundos));
  if (total < 60) return `${total} s`;

  const minutos = Math.floor(total / 60);
  if (minutos >= 60) return formatearDuracion(minutos);
  const resto = total % 60;
  return resto > 0 ? `${minutos} min ${resto} s` : `${minutos} min`;
}

/**
 * Metros redondeados a la decena bajo el kilometro, despues kilometros con un decimal y el separador de
 * quien lee: «3.2 km» en Peru y en ingles, «3,2 km» en Brasil.
 */
export function formatearDistancia(metros: number | null | undefined, idioma: Idioma): string {
  if (metros === null || metros === undefined || Number.isNaN(metros)) return '—';
  if (metros < 1000) return `${Math.round(metros / 10) * 10} m`;
  const km = new Intl.NumberFormat(REGION[idioma], { maximumFractionDigits: 1 }).format(
    metros / 1000,
  );
  return `${km} km`;
}
