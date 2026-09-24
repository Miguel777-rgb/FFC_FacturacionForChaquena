import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { TipoCambioService } from '../nucleo/cambio/tipo-cambio.service';

/**
 * El equivalente en dolares de un importe en soles, debajo del total.
 *
 * El local cobra en soles y la cotizacion la publica un tercero, asi que esta
 * cifra es orientativa y nunca sustituye al total: se pinta mas pequena, con el
 * simbolo de aproximacion delante y sin entrar en el ticket. Quien cobra sigue
 * leyendo los soles.
 *
 * No pinta nada mientras no haya cotizacion. Un «US$ 0,00» en una pantalla de
 * cobro es peor que un hueco: parece un precio.
 *
 * Cuando el dato no es de la ultima hora se dice la fecha en el propio texto, y
 * no con un color ni con opacidad: un cajero daltonico y una pantalla al sol
 * tienen que poder distinguir una cotizacion de hoy de una de la semana pasada.
 */
@Component({
  selector: 'app-en-dolares',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DecimalPipe],
  template: `
    @if (dolares(); as monto) {
      <p class="en-dolares" [attr.title]="detalle()">
        <span aria-hidden="true">≈ US$ {{ monto | number: '1.2-2' }}</span>
        <span class="visualmente-oculto">{{ detalle() }}</span>
        @if (!cambio.fresco()) {
          <span class="fecha">{{ delDia() }}</span>
        }
      </p>
    }
  `,
  styles: `
    .en-dolares {
      display: flex;
      flex-wrap: wrap;
      gap: 0 0.4rem;
      justify-content: flex-end;
      margin: 0.15rem 0 0;
      font-size: var(--t-chico);
      color: var(--tenue);
    }

    .fecha {
      font-variant-numeric: tabular-nums;
    }
  `,
})
export class EnDolares {
  private readonly i18n = inject(I18nService);
  protected readonly cambio = inject(TipoCambioService);

  /** El importe en soles que se quiere expresar en dolares. */
  readonly soles = input.required<number>();

  /**
   * `null` tambien cuando el importe es cero: una comanda vacia no necesita su
   * equivalente, y devolverlo aqui evita que la plantilla dependa de que el
   * cero sea falso en JavaScript, que es cierto pero no se lee.
   */
  protected readonly dolares = computed(() => {
    const soles = this.soles();
    return soles > 0 ? this.cambio.aDolares(soles) : null;
  });

  /** Lo que lee un lector de pantalla y lo que sale al posar el puntero. */
  protected readonly detalle = computed(() => {
    const actual = this.cambio.cambio();
    if (actual === null) return '';
    return this.i18n.t('cambio.detalle', {
      tasa: actual.solesPorDolar.toFixed(4),
      fecha: this.i18n.fecha(actual.consultadoEn, 'corta'),
    });
  });

  protected readonly delDia = computed(() => {
    const actual = this.cambio.cambio();
    if (actual === null) return '';
    return this.i18n.t('cambio.delDia', { fecha: this.i18n.fecha(actual.consultadoEn, 'corta') });
  });

  constructor() {
    void this.cambio.asegurar();
  }
}
