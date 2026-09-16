import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { TiempoRealService } from '../nucleo/tiempo-real/tiempo-real.service';

/**
 * Dice si la pantalla recibe los cambios al momento o se esta refrescando sola.
 *
 * Sin esto, cocina no sabe si una comanda que no aparece no existe o no llego:
 * con la conexion caida, lo nuevo puede tardar lo que tarda el refresco.
 */
@Component({
  selector: 'app-en-vivo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="en-vivo" [class.vivo]="tiempoReal.conectado()" role="status">
      <span class="punto" aria-hidden="true"></span>
      {{ t(tiempoReal.conectado() ? 'tiempoReal.enVivo' : 'tiempoReal.sinVivo') }}
    </span>
  `,
  styles: `
    /* Pegado al titulo: en la cabecera, el boton de actualizar sigue a la derecha. */
    :host {
      margin-inline-end: auto;
    }

    .en-vivo {
      display: inline-flex;
      align-items: center;
      gap: var(--e2);
      font-size: var(--t-chico);
      color: var(--tenue);
      white-space: nowrap;
    }

    .punto {
      width: 8px;
      height: 8px;
      background: var(--linea-fuerte);
      border-radius: 50%;
    }

    .vivo {
      color: var(--ok);
    }

    .vivo .punto {
      background: var(--ok);
    }
  `,
})
export class EnVivo {
  protected readonly tiempoReal = inject(TiempoRealService);
  protected readonly t = inject(I18nService).t;
}
