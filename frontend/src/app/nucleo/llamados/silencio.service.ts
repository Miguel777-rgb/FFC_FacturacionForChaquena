import { Injectable, signal } from '@angular/core';

/** Donde se recuerda si el mozo silencio los llamados. Por defecto suenan. */
const CLAVE = 'ffc.llamados.silencio';

/**
 * Si el llamado de cocina suena y vibra en este dispositivo.
 *
 * Va aparte del servicio del llamado: la campana solo necesita saber si suena,
 * no abrir el canal ni conocer los llamados.
 */
@Injectable({ providedIn: 'root' })
export class SilencioLlamadosService {
  private readonly _silenciado = signal(leer());
  readonly silenciado = this._silenciado.asReadonly();

  alternar(): void {
    const silenciado = !this._silenciado();
    this._silenciado.set(silenciado);
    try {
      localStorage.setItem(CLAVE, silenciado ? '1' : '0');
    } catch {
      /* almacenamiento bloqueado: vale para esta visita */
    }
  }
}

function leer(): boolean {
  try {
    return localStorage.getItem(CLAVE) === '1';
  } catch {
    return false;
  }
}
