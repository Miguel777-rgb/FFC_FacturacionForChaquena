import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { ICONOS, type NombreIcono } from './iconos';

/**
 * Pinta uno de los iconos de `iconos.ts`.
 *
 * Son iconos de contorno: trazo en `currentColor`, asi que heredan el color del
 * texto que los rodea. `relleno` los pinta llenos, que es como se marca una
 * estrella ya puntuada.
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
      [attr.fill]="relleno() ? 'currentColor' : 'none'"
      stroke="currentColor"
      [attr.stroke-width]="grosor()"
      stroke-linecap="round"
      stroke-linejoin="round"
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
  /** 1,75 y no el 2 de Tabler: a 18-20px el trazo de 2 se ve grueso junto a Inter. */
  readonly grosor = input(1.75);
  readonly relleno = input(false);

  protected readonly trazo = computed(() => ICONOS[this.nombre()]);
}
