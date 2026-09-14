import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import { CartaSeccion } from './carta.seccion';

/**
 * Menu: categorias, platillos y recetas.
 *
 * Fue una pestana de la trastienda. Es su propio destino porque es trabajo
 * distinto del stock: aqui se decide que se vende y con que se hace; en el
 * inventario, cuanto queda. La seccion es la misma de antes, sin cambios.
 */
@Component({
  selector: 'app-menu',
  imports: [CartaSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="superficie">
      <header class="cabecera">
        <h1>{{ t('panel.menu') }}</h1>
      </header>
      <app-carta-seccion />
    </section>
  `,
})
export class MenuPage {
  protected readonly t = inject(I18nService).t;
}
