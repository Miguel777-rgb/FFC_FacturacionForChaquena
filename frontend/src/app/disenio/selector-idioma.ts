import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { esIdioma } from '../nucleo/i18n/idioma';

/**
 * El selector de idioma: un desplegable con los tres idiomas.
 *
 * Es un `<select>` nativo y no una lista dibujada a mano. Un desplegable propio
 * obliga a reimplementar lo que el navegador ya trae —abrir con el teclado,
 * recorrer con las flechas, escribir la inicial para saltar, cerrar con Esc— y
 * en el celular pierde la rueda del sistema, que es lo que el mozo espera. Lo
 * unico que hace falta encima es que el control diga para que sirve, porque las
 * opciones estan cada una en su propio idioma y el rotulo no siempre se
 * entiende.
 *
 * Cada idioma se nombra en su propia lengua —«English», no «Ingles»— porque
 * quien busca la suya en la lista todavia no entiende la que esta viendo. Por
 * eso `IDIOMAS` no pasa por el diccionario.
 *
 * `variante="integrada"` lo encaja en el pie del panel lateral y en la barra
 * superior del celular; la flotante es la de la pantalla de entrar, donde no
 * hay panel que lo sostenga.
 */
@Component({
  selector: 'app-selector-idioma',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="envoltura" [class.integrada]="variante() === 'integrada'">
      <label class="visualmente-oculto" [attr.for]="id">{{ i18n.t('idioma.elegir') }}</label>
      <select
        [id]="id"
        [attr.title]="i18n.t('idioma.elegir')"
        [value]="i18n.idioma()"
        (change)="elegir($any($event.target).value)"
      >
        @for (i of i18n.idiomas; track i.codigo) {
          <option [value]="i.codigo" [selected]="i.codigo === i18n.idioma()">{{ i.nombre }}</option>
        }
      </select>
    </div>
  `,
  styles: `
    .envoltura {
      display: inline-flex;
    }

    select {
      /* Alto de toque completo: se pulsa con el dedo en la tableta del salon. */
      min-height: var(--control);
      padding-block: 0;
      font-size: var(--t-chico);
    }

    /* Dentro del panel y de la barra superior el control no compite con los
       destinos: sin fondo hasta que se apunta. */
    .integrada select {
      max-width: 9rem;
      background: transparent;
      border-color: var(--linea);
    }

    .integrada select:hover {
      background: var(--hundido);
    }
  `,
})
export class SelectorIdioma {
  protected readonly i18n = inject(I18nService);

  readonly variante = input<'flotante' | 'integrada'>('flotante');

  /** Un id propio por instancia: el panel y la barra superior conviven en el celular. */
  protected readonly id = `idioma-${Math.random().toString(36).slice(2, 8)}`;

  protected elegir(valor: string): void {
    if (esIdioma(valor)) {
      // El diccionario se descarga al elegirlo; la pantalla se repinta cuando llega.
      void this.i18n.cambiar(valor);
    }
  }
}
