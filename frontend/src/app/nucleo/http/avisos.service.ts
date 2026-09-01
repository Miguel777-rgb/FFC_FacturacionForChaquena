import { Injectable, signal } from '@angular/core';

export type TonoAviso = 'error' | 'exito' | 'info';

export interface Aviso {
  id: number;
  tono: TonoAviso;
  texto: string;
}

/**
 * Cola de avisos que se pintan en una esquina de la pantalla.
 *
 * En un POS tactil el usuario no siempre esta mirando: los errores se quedan
 * hasta que alguien los cierra, y solo los mensajes de exito se van solos.
 */
@Injectable({ providedIn: 'root' })
export class AvisosService {
  private siguienteId = 1;
  private readonly _avisos = signal<Aviso[]>([]);

  readonly avisos = this._avisos.asReadonly();

  error(texto: string): void {
    this.publicar('error', texto);
  }

  exito(texto: string): void {
    this.publicar('exito', texto, 4000);
  }

  info(texto: string): void {
    this.publicar('info', texto, 6000);
  }

  cerrar(id: number): void {
    this._avisos.update((lista) => lista.filter((a) => a.id !== id));
  }

  limpiar(): void {
    this._avisos.set([]);
  }

  private publicar(tono: TonoAviso, texto: string, msVida?: number): void {
    const id = this.siguienteId++;

    this._avisos.update((lista) => {
      // Dos peticiones que fallan igual no deben apilar el mismo cartel.
      const sinRepetido = lista.filter((a) => !(a.tono === tono && a.texto === texto));
      return [...sinRepetido, { id, tono, texto }];
    });

    if (msVida !== undefined) {
      setTimeout(() => this.cerrar(id), msVida);
    }
  }
}
