import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { InventarioSeccion } from './inventario.seccion';
import { ProveedoresSeccion } from './proveedores.seccion';

type Seccion = 'insumos' | 'proveedores';

const SECCIONES: ReadonlyArray<{ id: Seccion; nombre: ClaveI18n }> = [
  { id: 'insumos', nombre: 'inventario.insumos' },
  { id: 'proveedores', nombre: 'inventario.proveedores' },
];

/**
 * Inventario: los insumos con sus lotes, y a quien se le compran.
 *
 * Es donde aterriza el almacenero. Abre por los insumos, que es lo que se mira
 * todos los dias; los proveedores se tocan cuando aparece uno nuevo.
 */
@Component({
  selector: 'app-inventario',
  imports: [InventarioSeccion, ProveedoresSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="superficie">
      <header class="cabecera">
        <h1>{{ t('panel.inventario') }}</h1>
      </header>

      <nav class="pestanas" [attr.aria-label]="t('inventario.secciones')">
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
        @case ('insumos') {
          <app-inventario-seccion />
        }
        @case ('proveedores') {
          <app-proveedores-seccion />
        }
      }
    </section>
  `,
})
export class InventarioPage {
  protected readonly t = inject(I18nService).t;

  protected readonly SECCIONES = SECCIONES;
  protected readonly activa = signal<Seccion>('insumos');
}
