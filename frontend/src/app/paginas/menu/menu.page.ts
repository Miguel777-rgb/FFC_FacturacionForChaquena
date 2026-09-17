import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { SesionService } from '../../nucleo/sesion/sesion.service';
import { AlergenosSeccion } from './alergenos.seccion';
import { CartaSeccion } from './carta.seccion';
import { ComplementosSeccion } from './complementos.seccion';
import { LecturaCartaSeccion } from './lectura-carta.seccion';
import { PromocionesSeccion } from './promociones.seccion';

type Seccion = 'platillos' | 'complementos' | 'promociones' | 'alergenos' | 'lectura';

/** `soloAdmin` copia el `@PreAuthorize` del controlador que hay detras. */
const SECCIONES: ReadonlyArray<{ id: Seccion; nombre: ClaveI18n; soloAdmin?: boolean }> = [
  { id: 'platillos', nombre: 'menu.platillos' },
  { id: 'complementos', nombre: 'menu.complementos' },
  { id: 'promociones', nombre: 'menu.promociones' },
  { id: 'alergenos', nombre: 'menu.alergenos' },
  { id: 'lectura', nombre: 'menu.lectura', soloAdmin: true },
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
  imports: [
    AlergenosSeccion,
    CartaSeccion,
    ComplementosSeccion,
    LecturaCartaSeccion,
    PromocionesSeccion,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="superficie">
      <header class="cabecera">
        <h1>{{ t('panel.menu') }}</h1>
      </header>

      <nav class="pestanas" [attr.aria-label]="t('menu.secciones')">
        @for (s of secciones(); track s.id) {
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
        @case ('alergenos') {
          <app-alergenos-seccion />
        }
        @case ('lectura') {
          <app-lectura-carta-seccion />
        }
      }
    </section>
  `,
})
export class MenuPage {
  protected readonly t = inject(I18nService).t;

  private readonly sesion = inject(SesionService);

  /** Leer la carta desde fotos es de ADMIN: una importacion cambia decenas de precios. */
  protected readonly secciones = computed(() =>
    SECCIONES.filter((s) => !s.soloAdmin || this.sesion.tieneAlgunRol(['ADMIN'])),
  );
  protected readonly activa = signal<Seccion>('platillos');
}
