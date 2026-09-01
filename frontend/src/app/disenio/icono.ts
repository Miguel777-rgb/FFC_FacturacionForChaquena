import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ICONOS, type NombreIcono } from './iconos';

/**
 * Pinta uno de los iconos de `iconos.ts`.
 *
 * `aria-hidden` va fijo: un icono nunca es la unica forma de nombrar algo. El
 * texto accesible lo pone quien lo usa, con la etiqueta visible al lado o con
 * un `aria-label` en el boton que lo contiene.
 */
@Component({
  selector: 'app-icono',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <svg
      [attr.width]="tamano()"
      [attr.height]="tamano()"
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden="true"
      focusable="false"
    >
      <path [attr.d]="trazo()" />
    </svg>
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
    }
  `,
})
export class Icono {
  readonly nombre = input.required<NombreIcono>();
  readonly tamano = input(20);

  protected readonly trazo = computed(() => ICONOS[this.nombre()]);
}
