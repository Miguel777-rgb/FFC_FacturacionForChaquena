import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../nucleo/i18n/traducciones/es';
import { TemaService, type Tema } from '../nucleo/tema/tema.service';
import { Icono } from './icono';
import type { NombreIcono } from './iconos';

const ICONO: Record<Tema, NombreIcono> = {
  sistema: 'temaSistema',
  claro: 'temaClaro',
  oscuro: 'temaOscuro',
};

const NOMBRE: Record<Tema, ClaveI18n> = {
  sistema: 'tema.sistema',
  claro: 'tema.claro',
  oscuro: 'tema.oscuro',
};

/**
 * Un solo boton que recorre sistema → claro → oscuro. El icono dice el tema en
 * uso; el nombre accesible dice ademas a cual se pasa al pulsar, porque un
 * icono de luna no aclara si es el estado o la accion.
 */
@Component({
  selector: 'app-selector-tema',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="fantasma icono"
      (click)="tema.alternar()"
      [attr.aria-label]="etiqueta()"
      [attr.title]="etiqueta()"
    >
      <app-icono [nombre]="icono()" />
    </button>
  `,
})
export class SelectorTema {
  protected readonly tema = inject(TemaService);
  private readonly t = inject(I18nService).t;

  protected readonly icono = computed(() => ICONO[this.tema.tema()]);
  protected readonly etiqueta = computed(() =>
    this.t('tema.alternar', {
      actual: this.t(NOMBRE[this.tema.tema()]),
      siguiente: this.t(NOMBRE[this.tema.siguiente()]),
    }),
  );
}
