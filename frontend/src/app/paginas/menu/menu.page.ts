import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { CartaSeccion } from './carta.seccion';
import { ComplementosSeccion } from './complementos.seccion';
import { PromocionesSeccion } from './promociones.seccion';

type Seccion = 'platillos' | 'complementos' | 'promociones';

const SECCIONES: ReadonlyArray<{ id: Seccion; nombre: ClaveI18n }> = [
  { id: 'platillos', nombre: 'menu.platillos' },
  { id: 'complementos', nombre: 'menu.complementos' },
  { id: 'promociones', nombre: 'menu.promociones' },
];

/**
 * Menu: lo que se vende. Los platillos con su receta, lo que se les suma y las
 * promociones que los rebajan.
 *
 * Es su propio destino y no una parte del inventario porque es trabajo
 * distinto: aqui se decide que se vende y a cuanto; en el inventario, cuanto
 * queda. Las tres partes van en pestanas porque se tocan en momentos distintos,
 * y cada una pide sus datos solo al abrirse.
 */
@Component({
  selector: 'app-menu',
  imports: [CartaSeccion, ComplementosSeccion, PromocionesSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="superficie">
      <header class="cabecera">
        <h1>{{ t('panel.menu') }}</h1>
      </header>

      <nav class="pestanas" [attr.aria-label]="t('menu.secciones')">
        @for (s of SECCIONES; track s.id) {
          <button
            type="button"
            [class.activa]="activa() === s.id"
            [attr.aria-current]="activa() === s.id ? 'page' : null"
            (click)="activa.set(s.id)"
          >
            {{ t(s.nombre) }}
          </button>
        }
      </nav>

      @switch (activa()) {
        @case ('platillos') {
          <app-carta-seccion />
        }
        @case ('complementos') {
          <app-complementos-seccion />
        }
        @case ('promociones') {
          <app-promociones-seccion />
        }
      }
    </section>
  `,
})
export class MenuPage {
  protected readonly t = inject(I18nService).t;

  protected readonly SECCIONES = SECCIONES;
  protected readonly activa = signal<Seccion>('platillos');
}
