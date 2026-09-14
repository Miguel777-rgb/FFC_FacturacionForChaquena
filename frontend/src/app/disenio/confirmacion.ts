import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { ConfirmacionService } from '../nucleo/confirmacion/confirmacion.service';
import { I18nService } from '../nucleo/i18n/i18n.service';
import { Dialogo } from './dialogo';

/**
 * El unico dialogo de confirmacion de la aplicacion. Lo abre
 * `ConfirmacionService.pedir(...)`; se monta una vez en `app.html`.
 *
 * El foco arranca en «Dejarlo» (`autofocus`), no en el boton rojo: quien pulsa
 * Enter por inercia no da de baja a nadie.
 */
@Component({
  selector: 'app-confirmacion',
  imports: [Dialogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirmacion.abierta(); as peticion) {
      <app-dialogo
        [titulo]="peticion.titulo"
        [abierto]="true"
        (abiertoChange)="$event || confirmacion.responder(false)"
      >
        <p class="texto">{{ peticion.mensaje }}</p>
        <div pie>
          <button
            type="button"
            class="secundario"
            autofocus
            (click)="confirmacion.responder(false)"
          >
            {{ t('comun.dejarlo') }}
          </button>
          <button type="button" class="peligro" (click)="confirmacion.responder(true)">
            {{ peticion.confirmar }}
          </button>
        </div>
      </app-dialogo>
    }
  `,
  styles: `
    .texto {
      margin: 0;
      max-width: 60ch;
    }
  `,
})
export class Confirmacion {
  protected readonly confirmacion = inject(ConfirmacionService);
  protected readonly t = inject(I18nService).t;
}
