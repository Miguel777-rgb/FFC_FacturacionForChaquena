import { Injectable, signal } from '@angular/core';

/** Lo que se le pregunta a la persona antes de un gesto que no tiene vuelta facil. */
export interface PeticionConfirmacion {
  titulo: string;
  mensaje: string;
  /** El verbo del boton que confirma: «Dar de baja», no «Aceptar». */
  confirmar: string;
}

interface PeticionAbierta extends PeticionConfirmacion {
  resolver: (confirmado: boolean) => void;
}

/**
 * Pide confirmacion antes de una accion de peligro.
 *
 * Con la regla de color de la guia, peligro y marca son dos rojos rellenos. Lo
 * que impide confundirlos es que el rojo de peligro nunca esta a un solo toque:
 * el gesto que lo abre es gris, y el boton rojo vive aqui, dentro de un dialogo
 * que dice que va a pasar.
 *
 * Una sola peticion a la vez y un solo dialogo en toda la aplicacion
 * (`app-confirmacion`, montado en `app.html`). Quien llama solo espera la
 * promesa:
 *
 * ```ts
 * if (!(await this.confirmacion.pedir({ titulo, mensaje, confirmar }))) return;
 * ```
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  private readonly _abierta = signal<PeticionAbierta | null>(null);

  readonly abierta = this._abierta.asReadonly();

  pedir(peticion: PeticionConfirmacion): Promise<boolean> {
    // Si habia otra pendiente se da por rechazada: nunca queda una promesa
    // colgada esperando un dialogo que ya no esta en pantalla.
    this._abierta()?.resolver(false);

    return new Promise<boolean>((resolver) => {
      this._abierta.set({ ...peticion, resolver });
    });
  }

  responder(confirmado: boolean): void {
    const peticion = this._abierta();
    this._abierta.set(null);
    peticion?.resolver(confirmado);
  }
}
