import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { AvisosService } from '../nucleo/http/avisos.service';
import { I18nService } from '../nucleo/i18n/i18n.service';
import { Icono } from './icono';

/**
 * Avisos apilados en una esquina. `role="status"` con `aria-live="polite"`
 * para que un lector de pantalla los anuncie sin interrumpir lo que se este
 * haciendo.
 */
@Component({
  selector: 'app-pila-avisos',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="pila" role="status" aria-live="polite">
      @for (a of avisos.avisos(); track a.id) {
        <div class="aviso" [class]="a.tono">
          <p>{{ a.texto }}</p>
          <button
            type="button"
            class="cerrar"
            (click)="avisos.cerrar(a.id)"
            [attr.aria-label]="t('comun.cerrarAviso')"
          >
            <app-icono nombre="quitar" [tamano]="16" />
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
      /* Borde de 1px en el color del tono y fondo suave: se identifica de un
         vistazo sin franjas gruesas. */
      border: 1px solid currentColor;
      background: var(--superficie);
      box-shadow: var(--sombra);
      font-size: var(--t-texto);
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
      color: var(--info);
      background: var(--info-suave);
    }

    .cerrar {
      flex: none;
      width: var(--control-chico);
      min-height: var(--control-chico);
      margin: calc(var(--e2) * -1) calc(var(--e2) * -1) 0 0;
      padding: 0;
      color: inherit;
      background: transparent;
      border: none;
    }

    .cerrar:hover:not(:disabled) {
      background: rgb(0 0 0 / 0.06);
    }
  `,
})
export class PilaAvisos {
  protected readonly avisos = inject(AvisosService);
  protected readonly t = inject(I18nService).t;
}
