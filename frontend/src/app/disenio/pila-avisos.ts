import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AvisosService } from '../nucleo/http/avisos.service';

/**
 * Avisos apilados en una esquina. `role="status"` con `aria-live="polite"`
 * para que un lector de pantalla los anuncie sin interrumpir lo que se este
 * haciendo.
 */
@Component({
  selector: 'app-pila-avisos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pila" role="status" aria-live="polite">
      @for (a of avisos.avisos(); track a.id) {
        <div class="aviso" [class]="a.tono">
          <p>{{ a.texto }}</p>
          <button type="button" class="cerrar" (click)="avisos.cerrar(a.id)" aria-label="Cerrar aviso">
            &times;
          </button>
        </div>
      }
    </div>
  `,
  styles: `
    .pila {
      position: fixed;
      right: var(--e4);
      bottom: var(--e4);
      z-index: 100;
      display: flex;
      flex-direction: column;
      gap: var(--e2);
      width: min(24rem, calc(100vw - var(--e6)));
      pointer-events: none;
    }

    .aviso {
      pointer-events: auto;
      display: flex;
      align-items: flex-start;
      gap: var(--e2);
      padding: var(--e3);
      border-radius: var(--radio);
      border: 1px solid currentColor;
      /* Franja del tono a la izquierda: el aviso se identifica de un vistazo
         lateral, sin llegar a leerlo. */
      border-left-width: 3px;
      background: var(--superficie);
      box-shadow: var(--sombra);
      font-size: 0.9rem;
    }

    .aviso p {
      margin: 0;
      flex: 1;
      color: var(--tinta);
    }

    .aviso.error {
      color: var(--critico);
      background: var(--critico-suave);
    }
    .aviso.exito {
      color: var(--ok);
      background: var(--ok-suave);
    }
    .aviso.info {
      color: var(--acento);
      background: var(--acento-suave);
    }

    .cerrar {
      min-height: 24px;
      padding: 0 var(--e2);
      font-size: 1.1rem;
      line-height: 1;
      color: inherit;
      background: transparent;
      border: none;
    }
  `,
})
export class PilaAvisos {
  protected readonly avisos = inject(AvisosService);
}
