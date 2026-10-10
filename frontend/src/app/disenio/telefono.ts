import {
  ChangeDetectionStrategy,
  Component,
  Pipe,
  type PipeTransform,
  computed,
  inject,
  input,
} from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { enlaceTelefono, formatoTelefono } from '../nucleo/telefono/telefono';

/**
 * `{{ c.celular | telefono }}`: el numero agrupado para leerlo de un vistazo.
 * Para donde tocarlo no tiene sentido, como el resultado de una busqueda que ya
 * es un boton, o el propio numero en el perfil.
 */
@Pipe({ name: 'telefono' })
export class TelefonoPipe implements PipeTransform {
  transform(valor: string | null | undefined): string {
    return formatoTelefono(valor);
  }
}

/**
 * El numero agrupado y, si se reconoce, como enlace `tel:`: en el celular,
 * tocarlo abre la llamada. Sin numero pinta `vacio`.
 *
 * El nombre accesible empieza por «Llamar al» y contiene el numero tal como se
 * ve, para que quien lo dicta en voz alta pueda nombrarlo.
 */
@Component({
  selector: 'app-telefono',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (enlace(); as destino) {
      <a
        class="cifra"
        [href]="destino"
        [attr.aria-label]="t('telefono.llamar', { numero: texto() })"
        >{{ texto() }}</a
      >
    } @else if (texto()) {
      <span class="cifra">{{ texto() }}</span>
    } @else {
      {{ vacio() }}
    }
  `,
  styles: `
    /* Desde una computadora casi nadie llama tocando el numero: en una tabla,
       una columna entera en el color de la marca gritaba. Se lee como el texto
       de al lado, con el subrayado suave que dice que es un enlace, y toma el
       color al pasar por encima. */
    a {
      color: inherit;
      text-decoration-color: var(--linea-fuerte);
    }

    a:hover {
      color: var(--acento);
      text-decoration-color: currentColor;
    }

    /* Con el dedo si se llama: el enlace se ve como tal y mide lo mismo que
       cualquier control, 44 px. Crece hacia arriba y hacia abajo con margenes
       negativos, para que la fila de una ficha no quede mas alta que sus
       vecinas: la linea sigue midiendo lo que mide su texto. */
    @media (pointer: coarse) {
      a {
        display: inline-flex;
        align-items: center;
        min-height: var(--toque);
        margin-block: calc((var(--toque) - 1lh) / -2);
        color: var(--acento);
        text-decoration-color: currentColor;
      }
    }
  `,
})
export class Telefono {
  protected readonly t = inject(I18nService).t;

  readonly numero = input<string | null | undefined>(null);
  /** Lo que se ve cuando no hay numero: una raya, «sin celular»... */
  readonly vacio = input('—');

  protected readonly texto = computed(() => formatoTelefono(this.numero()));
  protected readonly enlace = computed(() => enlaceTelefono(this.numero()));
}
