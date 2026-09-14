import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import { InventarioSeccion } from './inventario.seccion';

/**
 * Inventario: insumos, kardex, movimientos y conteo fisico.
 *
 * Fue la primera pestana de la trastienda y es donde aterriza el almacenero.
 * La seccion es la misma de antes, sin cambios.
 */
@Component({
  selector: 'app-inventario',
  imports: [InventarioSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="superficie">
      <header class="cabecera">
        <h1>{{ t('panel.inventario') }}</h1>
      </header>
      <app-inventario-seccion />
    </section>
  `,
})
export class InventarioPage {
  protected readonly t = inject(I18nService).t;
}
