import { Injectable, signal } from '@angular/core';

const CLAVE = 'chaquena.logo';

/** 512 KB de data URI. Por encima, localStorage empieza a fallar en algunos
 *  navegadores y el arranque se nota. Un logo de barra no necesita mas, y en
 *  WebP —el formato previsto— sobra de largo. */
const MAX_BYTES = 512 * 1024;

/** WebP primero: es el formato del logo del local. Los demas se aceptan para
 *  no obligar a convertir un archivo que ya se tiene a mano. */
const TIPOS = ['image/webp', 'image/png', 'image/jpeg', 'image/svg+xml'];

/**
 * Logo del local, elegido desde la propia interfaz.
 *
 * Vive en `localStorage` y no en `sessionStorage` —al reves que la sesion—
 * porque es una preferencia del dispositivo, no del turno: la tablet del mozo
 * debe seguir mostrando el logo cuando cambia quien la usa.
 *
 * No viaja al servidor. `ConfiguracionLocal` no tiene campo para el, y
 * anadirselo obliga a decidir donde se guardan los binarios. Mientras eso no
 * exista, cada dispositivo lleva el suyo; el dia que el backend lo soporte,
 * este servicio es el unico punto que hay que cambiar.
 */
@Injectable({ providedIn: 'root' })
export class LogoService {
  private readonly _logo = signal<string | null>(this.leer());

  /** Data URI del logo, o null si no se ha cargado ninguno. */
  readonly logo = this._logo.asReadonly();

  /**
   * Guarda el archivo elegido. Devuelve un mensaje de error, o null si fue
   * bien: quien llama decide como mostrarlo.
   */
  async cargar(archivo: File): Promise<string | null> {
    if (!TIPOS.includes(archivo.type)) {
      return 'Formato no admitido. Usa WEBP, PNG, JPG o SVG.';
    }
    if (archivo.size > MAX_BYTES) {
      return `El archivo pesa ${Math.round(archivo.size / 1024)} KB. El maximo es 512 KB.`;
    }

    let dataUri: string;
    try {
      dataUri = await this.leerComoDataUri(archivo);
    } catch {
      return 'No se pudo leer el archivo.';
    }

    try {
      localStorage.setItem(CLAVE, dataUri);
    } catch {
      // Modo privado o cuota llena: el logo vale para esta sesion y se pierde
      // al recargar. Es preferible a rechazar la carga.
      this._logo.set(dataUri);
      return null;
    }

    this._logo.set(dataUri);
    return null;
  }

  quitar(): void {
    this._logo.set(null);
    try {
      localStorage.removeItem(CLAVE);
    } catch {
      /* sin almacenamiento no hay nada que borrar */
    }
  }

  private leerComoDataUri(archivo: File): Promise<string> {
    return new Promise((resolver, rechazar) => {
      const lector = new FileReader();
      lector.onload = () => resolver(String(lector.result));
      lector.onerror = () => rechazar(lector.error);
      lector.readAsDataURL(archivo);
    });
  }

  private leer(): string | null {
    try {
      return localStorage.getItem(CLAVE);
    } catch {
      return null;
    }
  }
}
