import { environment } from '../../../environments/environment';
import type { ClaveI18n } from '../i18n/traducciones/es';

/** Lo que el servidor acepta. SVG no: es texto que puede llevar scripts. */
export const TIPOS_IMAGEN: readonly string[] = ['image/webp', 'image/png', 'image/jpeg'];

export const MAX_BYTES_IMAGEN = 2 * 1024 * 1024;

/**
 * La URL de una imagen subida. Va sin token a proposito: un `<img>` no manda
 * cabeceras, y el servidor sirve cada imagen por su UUID sin pedir sesion.
 */
export function urlDeArchivo(id: string | null | undefined): string | null {
  return id ? `${environment.apiBasePath}/api/v1/archivos/${id}` : null;
}

/**
 * Por que no se puede subir, o null. Se comprueba antes de enviar para no
 * gastar la subida de 2 MB en el celular; el servidor lo vuelve a mirar en los
 * bytes, que es lo que de verdad cuenta.
 */
export function problemaDeImagen(archivo: File): { clave: ClaveI18n; kb: number } | null {
  if (!TIPOS_IMAGEN.includes(archivo.type)) {
    return { clave: 'imagen.formato', kb: 0 };
  }
  if (archivo.size > MAX_BYTES_IMAGEN) {
    return { clave: 'imagen.peso', kb: Math.round(archivo.size / 1024) };
  }
  return null;
}
