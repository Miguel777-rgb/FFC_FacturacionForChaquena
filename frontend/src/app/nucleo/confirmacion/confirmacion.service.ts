import {
  ApplicationRef,
  DOCUMENT,
  EnvironmentInjector,
  Injectable,
  createComponent,
  inject,
  signal,
} from '@angular/core';

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
 * Una sola peticion a la vez y un solo dialogo en toda la aplicacion. El
 * dialogo (`app-confirmacion`) no viaja en el bundle inicial: se descarga y se
 * monta en `<body>` la primera vez que alguien lo pide, porque la mayoria de los
 * turnos no da de baja a nadie. Quien llama solo espera la promesa:
 *
 * ```ts
 * if (!(await this.confirmacion.pedir({ titulo, mensaje, confirmar }))) return;
 * ```
 */
@Injectable({ providedIn: 'root' })
export class ConfirmacionService {
  private readonly appRef = inject(ApplicationRef);
  private readonly injector = inject(EnvironmentInjector);
  private readonly documento = inject(DOCUMENT);

  private readonly _abierta = signal<PeticionAbierta | null>(null);
  private montado: Promise<void> | null = null;

  readonly abierta = this._abierta.asReadonly();

  pedir(peticion: PeticionConfirmacion): Promise<boolean> {
    // Si habia otra pendiente se da por rechazada: nunca queda una promesa
    // colgada esperando un dialogo que ya no esta en pantalla.
    this._abierta()?.resolver(false);

    const respuesta = new Promise<boolean>((resolver) => {
      this._abierta.set({ ...peticion, resolver });
    });
    void this.montar();
    return respuesta;
  }

  responder(confirmado: boolean): void {
    const peticion = this._abierta();
    this._abierta.set(null);
    peticion?.resolver(confirmado);
  }

  private montar(): Promise<void> {
    this.montado ??= import('../../disenio/confirmacion')
      .then(({ Confirmacion }) => {
        const ref = createComponent(Confirmacion, { environmentInjector: this.injector });
        this.appRef.attachView(ref.hostView);
        this.documento.body.appendChild(ref.location.nativeElement);
      })
      .catch(() => {
        // Sin red para descargar el dialogo no se puede preguntar, y sin
        // pregunta no se hace nada: se da por rechazada.
        this.montado = null;
        this.responder(false);
      });
    return this.montado;
  }
}
