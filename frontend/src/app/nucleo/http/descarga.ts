import type { HttpResponse } from '@angular/common/http';

/**
 * El nombre que el servidor puso en `Content-Disposition`, o el de respaldo.
 *
 * Se prefiere `filename*` (RFC 5987), que es el que admite tildes; `filename`
 * a secas queda para servidores que no lo mandan.
 */
export function nombreDeAdjunto(respuesta: HttpResponse<Blob>, respaldo: string): string {
  const cabecera = respuesta.headers.get('Content-Disposition') ?? '';
  const extendido = /filename\*\s*=\s*[^']*''([^;]+)/i.exec(cabecera);
  if (extendido) {
    try {
      return decodeURIComponent(extendido[1].trim());
    } catch {
      /* mal codificado: se prueba con el simple */
    }
  }
  const simple = /filename\s*=\s*"?([^";]+)"?/i.exec(cabecera);
  return simple ? simple[1].trim() : respaldo;
}

/**
 * Entrega un Blob al navegador como archivo descargado.
 *
 * El enlace temporal es la unica forma portable de ponerle nombre. La URL del
 * Blob se libera despues, no en el acto: Safari cancela la descarga si se
 * revoca antes de que empiece.
 */
export function guardarArchivo(contenido: Blob, nombre: string): void {
  if (typeof URL.createObjectURL !== 'function') return; // jsdom
  const url = URL.createObjectURL(contenido);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.rel = 'noopener';
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  setTimeout(() => URL.revokeObjectURL(url), 30_000);
}
