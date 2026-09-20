import type { ClaveI18n } from '../i18n/traducciones/es';

/**
 * Las expresiones regulares que validan el alta de un trabajador.
 *
 * Viven en un solo archivo, y no sueltas en la plantilla, por tres razones. La
 * primera es que cada patron tiene un gemelo en el `@Pattern` del DTO del
 * servidor: al estar juntos se ve de un vistazo cual hay que cambiar tambien
 * alla. La segunda es que asi se prueban sin montar la pantalla. La tercera es
 * que el mensaje va pegado al patron: una expresion sin su explicacion deja al
 * usuario adivinando que quiere el campo.
 *
 * Todas van ancladas con `^` y `$`. Sin las anclas, `\d{8}` acepta un DNI de
 * ocho cifras metido dentro de cualquier otra cosa —«mi dni es 12345678, ya»—,
 * que es el error clasico de validar con expresiones regulares.
 */

/** Ocho digitos exactos: el documento nacional de identidad peruano. */
const DNI = /^\d{8}$/;

/**
 * Celular peruano: nueve cifras que empiezan en 9. Se admite el prefijo del
 * pais, con o sin `+` y con o sin espacio, porque los numeros sembrados y los
 * que llegan por los bots vienen en la forma internacional (`51987654321`).
 */
const CELULAR = /^(\+?51\s?)?9\d{8}$/;

/**
 * Correo. Deliberadamente mas estricto que la gramatica del RFC 5322 —que
 * admite comillas, comentarios y direcciones sin punto— y mas laxo que una
 * lista de dominios. Lo que de verdad prueba que un correo existe es mandarle
 * un mensaje; esto solo descarta lo que no puede ser uno.
 */
const CORREO = /^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}$/;

/**
 * Nombres y apellidos: letras, con tildes y ñ, y los separadores que aparecen
 * en un nombre real —el espacio de «Maria Jose», el apostrofo de «D'Angelo», el
 * guion de «Garcia-Lopez»—, nunca dos seguidos ni al principio ni al final.
 * Nada de cifras: un nombre con cifras es casi siempre un campo mal llenado.
 */
const NOMBRE = /^\p{L}+(?:[ '’-]\p{L}+)*$/u;

/**
 * Usuario: empieza por letra y sigue con letras, cifras, punto, guion o guion
 * bajo, entre 3 y 20 caracteres. Sin mayusculas, para que «Mozo1» y «mozo1» no
 * sean dos cuentas distintas.
 */
const USUARIO = /^[a-z][a-z0-9._-]{2,19}$/;

/**
 * Contrasena: ocho o mas caracteres con al menos una minuscula, una mayuscula y
 * una cifra. Las tres primeras partes son `(?=...)`, miradas hacia delante que
 * comprueban sin consumir: la expresion exige las tres condiciones a la vez sin
 * importar en que orden aparezcan.
 */
const CONTRASENA = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

export const PATRONES = {
  dni: DNI,
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
  celular: 'validacion.celular',
  correo: 'validacion.correo',
  nombre: 'validacion.nombre',
  usuario: 'validacion.usuario',
  contrasena: 'validacion.contrasena',
};

/**
 * La clave del mensaje cuando el valor no cumple, o `null` cuando cumple.
 *
 * Un campo vacio no devuelve problema de formato: que sea obligatorio es otra
 * regla, y mezclarlas hace que el formulario grite en rojo antes de que a
 * nadie le haya dado tiempo a escribir.
 */
export function problemaDe(campo: CampoValidado, valor: string): ClaveI18n | null {
  const limpio = valor.trim();
  if (limpio.length === 0) return null;
  return PATRONES[campo].test(limpio) ? null : MENSAJES[campo];
}
