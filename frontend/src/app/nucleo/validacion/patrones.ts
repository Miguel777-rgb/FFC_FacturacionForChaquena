import type { ClaveI18n } from '../i18n/traducciones/es';

/**
 * Juntas porque cada patron del trabajador tiene su gemelo en el `@Pattern` del DTO del servidor. Todas van
 * ancladas con `^` y `$`: sin anclas, `\d{8}` acepta un DNI metido en cualquier texto.
 */

/** Ocho digitos exactos: el documento nacional de identidad peruano. */
const DNI = /^\d{8}$/;

/** DNI, carne de extranjeria (hasta 12 cifras) o pasaporte con letras; solo cifras y menos de ocho no es ninguno. */
const DOCUMENTO = /^(?:\d{8,12}|(?=.*[A-Za-z])[A-Za-z0-9-]{6,15})$/;

/** Nueve cifras que empiezan en 9, con o sin el prefijo del pais: los bots traen la forma internacional. */
const CELULAR = /^(\+?51\s?)?9\d{8}$/;

/** Mas estricto que el RFC 5322 y mas laxo que una lista de dominios: solo descarta lo que no puede ser un correo. */
const CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

/** Letras con tildes y ñ, y los separadores de un nombre real (espacio, apostrofo, guion), nunca dos seguidos. */
const NOMBRE = /^\p{L}+(?:[ '’-]\p{L}+)*$/u;

/** Sin mayusculas, para que «Mozo1» y «mozo1» no sean dos cuentas distintas. */
const USUARIO = /^[a-z][a-z0-9._-]{2,19}$/;

/** Las tres `(?=...)` miran hacia delante sin consumir: exigen las tres condiciones en cualquier orden. */
const CONTRASENA = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

export const PATRONES = {
  dni: DNI,
  documento: DOCUMENTO,
  celular: CELULAR,
  correo: CORREO,
  nombre: NOMBRE,
  usuario: USUARIO,
  contrasena: CONTRASENA,
} as const;

export type CampoValidado = keyof typeof PATRONES;

/** El mensaje de cada campo: dice que se espera, no que lo escrito esta mal. */
const MENSAJES: Record<CampoValidado, ClaveI18n> = {
  dni: 'validacion.dni',
  documento: 'validacion.documento',
  celular: 'validacion.celular',
  correo: 'validacion.correo',
  nombre: 'validacion.nombre',
  usuario: 'validacion.usuario',
  contrasena: 'validacion.contrasena',
};

/**
 * La clave del mensaje si el valor no cumple, o `null`. Un campo vacio no da error de formato: que sea
 * obligatorio es otra regla.
 */
export function problemaDe(campo: CampoValidado, valor: string): ClaveI18n | null {
  const limpio = valor.trim();
  if (limpio.length === 0) return null;
  return PATRONES[campo].test(limpio) ? null : MENSAJES[campo];
}
