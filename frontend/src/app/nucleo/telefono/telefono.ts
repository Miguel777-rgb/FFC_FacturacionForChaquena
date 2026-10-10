/**
 * Los telefonos tal como se leen y tal como se guardan.
 *
 * En la base conviven dos formas: los bots y la siembra guardan el celular en
 * forma internacional sin `+` (`51956781234`), y lo escrito a mano suele traer
 * solo las nueve cifras. Leer once cifras seguidas obliga a contarlas; por eso
 * se muestran agrupadas y siempre con el prefijo del pais, guardado o no.
 *
 * Solo se agrupa lo que se reconoce sin dudas: un celular peruano o un fijo con
 * su codigo de area. Cualquier otra cosa se muestra tal como se escribio, porque
 * reagruparla a ciegas inventaria un numero que nadie dio.
 */

/** Nueve cifras que empiezan en 9, con o sin el 51 delante. */
const CELULAR = /^(?:51)?(9\d{8})$/;

/** Fijo de Lima: el 1 del codigo de area y siete cifras, con el 0 o con el 51 delante. */
const FIJO_LIMA = /^(?:0|51)1(\d{7})$/;

/** Fijo de provincias: codigo de area de dos cifras (41 a 84) y seis cifras. */
const FIJO_PROVINCIA = /^(?:0|51)([4-8]\d)(\d{6})$/;

function cifras(valor: string): string {
  return valor.replace(/\D/g, '');
}

/** «+51 956 781 234», «(01) 456 7890», «(054) 123 456»; lo demas, tal cual. */
export function formatoTelefono(valor: string | null | undefined): string {
  const escrito = valor?.trim() ?? '';
  const numero = cifras(escrito);

  const celular = CELULAR.exec(numero);
  if (celular) {
    const [, local] = celular;
    return `+51 ${local.slice(0, 3)} ${local.slice(3, 6)} ${local.slice(6)}`;
  }

  const lima = FIJO_LIMA.exec(numero);
  if (lima) {
    const [, local] = lima;
    return `(01) ${local.slice(0, 3)} ${local.slice(3)}`;
  }

  const provincia = FIJO_PROVINCIA.exec(numero);
  if (provincia) {
    const [, area, local] = provincia;
    return `(0${area}) ${local.slice(0, 3)} ${local.slice(3)}`;
  }

  return escrito;
}

/**
 * El destino de un enlace `tel:`: el numero en forma internacional, sin
 * espacios. `null` cuando no es un numero reconocible: marcar algo que no se
 * entiende llamaria a cualquier parte.
 */
export function enlaceTelefono(valor: string | null | undefined): string | null {
  const numero = cifras(valor ?? '');

  const celular = CELULAR.exec(numero);
  if (celular) return `tel:+51${celular[1]}`;

  const lima = FIJO_LIMA.exec(numero);
  if (lima) return `tel:+511${lima[1]}`;

  const provincia = FIJO_PROVINCIA.exec(numero);
  if (provincia) return `tel:+51${provincia[1]}${provincia[2]}`;

  return null;
}

/**
 * Lo que se guarda: un celular peruano va como `51` mas sus nueve cifras, la
 * misma forma en que llegan los de los bots, para que un cliente escrito a mano
 * y el mismo cliente escribiendo por el bot se encuentren por su numero. Lo que
 * no es un celular se guarda recortado y sin tocar.
 */
export function normalizarCelular(valor: string): string {
  const escrito = valor.trim();
  const celular = CELULAR.exec(cifras(escrito));
  return celular ? `51${celular[1]}` : escrito;
}
