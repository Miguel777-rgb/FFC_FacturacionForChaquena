import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { SilencioLlamadosService } from '../nucleo/llamados/silencio.service';
import { TIMBRE_LLAMADO, contextoCompartido, tocarNotas } from '../nucleo/sonido/timbre';
import { Icono } from './icono';

/**
 * Silenciar el llamado de cocina en este dispositivo. Vive junto al tema y no
 * dentro del aviso, para poder apagarlo antes de que llegue un llamado.
 *
 * El nombre accesible no cambia y el estado va en `aria-pressed`: un nombre
 * que dijera la accion contraria en cada pulsacion confunde al lector de
 * pantalla. El `title` si dice lo que hara el toque.
 */
@Component({
  selector: 'app-campana-llamados',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      type="button"
      class="fantasma icono"
      [attr.aria-pressed]="!silencio.silenciado()"
      [attr.aria-label]="t('llamado.sonido')"
      [attr.title]="t(silencio.silenciado() ? 'llamado.activarSonido' : 'llamado.silenciar')"
      (click)="alternar()"
    >
      <app-icono [nombre]="silencio.silenciado() ? 'campanaTachada' : 'campana'" />
    </button>
  `,
})
export class CampanaLlamados {
  protected readonly silencio = inject(SilencioLlamadosService);
  protected readonly t = inject(I18nService).t;

  /**
   * Al encenderla suena una vez: el mozo oye como suena y si el volumen
   * alcanza, y como el toque es un gesto, el audio queda desbloqueado.
   */
  protected alternar(): void {
    this.silencio.alternar();
    if (this.silencio.silenciado()) return;
    const contexto = contextoCompartido();
    if (contexto) tocarNotas(contexto, TIMBRE_LLAMADO, 0.16);
  }
}
