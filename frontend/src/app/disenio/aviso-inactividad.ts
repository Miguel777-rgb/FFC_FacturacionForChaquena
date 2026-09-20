import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { InactividadService } from '../nucleo/sesion/inactividad.service';
import { I18nService } from '../nucleo/i18n/i18n.service';
import { Icono } from './icono';

/**
 * Los ultimos cinco minutos antes del cierre por inactividad, con el reloj a la
 * vista y un boton para seguir.
 *
 * Es una franja sobre el contenido y no un dialogo: quien esta a medio tomar
 * una comanda tiene que poder seguir escribiendo —eso mismo cancela la cuenta—
 * sin apartar antes un modal.
 *
 * El texto se anuncia una sola vez cuando aparece; el reloj lleva
 * `aria-hidden`, porque un lector de pantalla leyendo cada segundo tapa todo lo
 * demas.
 */
@Component({
  selector: 'app-aviso-inactividad',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (inactividad.avisando(); as segundos) {
      <div class="aviso-inactividad" role="alert">
        <app-icono nombre="reloj" [tamano]="18" />
        <p>{{ t('inactividad.aviso') }}</p>
        <span class="reloj cifra" aria-hidden="true">{{ reloj() }}</span>
        <button type="button" (click)="inactividad.sigoAqui()">
          {{ t('inactividad.sigoAqui') }}
        </button>
      </div>
    }
  `,
  styles: `
    .aviso-inactividad {
      position: sticky;
      top: 0;
      z-index: 50;
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--e2);
      padding: var(--e2) var(--e3);
      color: var(--aviso);
      background: var(--aviso-suave);
      border-bottom: 1px solid var(--linea);
    }

    p {
      flex: 1;
      margin: 0;
      font-size: var(--t-chico);
    }

    .reloj {
      font-size: var(--t-h4);
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class AvisoInactividad {
  protected readonly inactividad = inject(InactividadService);
  protected readonly t = inject(I18nService).t;

  /** mm:ss, que es como se lee una cuenta atras de minutos. */
  protected readonly reloj = computed(() => {
    const segundos = this.inactividad.avisando() ?? 0;
    return `${Math.floor(segundos / 60)}:${String(segundos % 60).padStart(2, '0')}`;
  });
}
