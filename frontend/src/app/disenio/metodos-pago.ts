import { DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';

import type { VentasPorMetodoPagoDto } from '../api/model/ventas-por-metodo-pago-dto';
import { I18nService } from '../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../nucleo/i18n/traducciones/es';

/**
 * Lo cobrado con cada metodo de pago, en barras horizontales.
 *
 * Barras y no una torta: tres porciones parecidas no se distinguen a ojo, y
 * una barra al lado de su cifra si. El largo es relativo al metodo que mas
 * cobro, y la parte del total va escrita.
 */
@Component({
  selector: 'app-metodos-pago',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (total() > 0) {
      <ul class="metodos">
        @for (m of filas(); track m.metodo) {
          <li>
            <div class="fila">
              <span class="nombre">{{ tEnum('metodo', m.metodo) }}</span>
              <span class="cifra">S/ {{ m.total ?? 0 | number: '1.2-2' }}</span>
            </div>
            <div class="barra-metodo" aria-hidden="true">
              <span [style.width.%]="m.largo"></span>
            </div>
            <span class="detalle">
              {{ tp('metodosPago.pagos', m.pagos ?? 0) }} ·
              <span class="cifra">{{ m.parte }} %</span>
            </span>
          </li>
        }
      </ul>
    } @else {
      <p class="vacio">{{ t(cargando() ? 'comun.cargando' : vacio()) }}</p>
    }
  `,
  styles: `
    .metodos {
      display: flex;
      flex-direction: column;
      gap: var(--e3);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .fila {
      display: flex;
      justify-content: space-between;
      gap: var(--e3);
      color: var(--tinta);
    }

    .barra-metodo {
      height: 6px;
      margin: var(--e1) 0 2px;
      overflow: hidden;
      background: var(--hundido);
      border-radius: 3px;
    }

    .barra-metodo span {
      display: block;
      height: 100%;
      background: var(--secundario);
      border-radius: 3px;
    }

    .detalle {
      font-size: var(--t-chico);
      color: var(--tenue);
    }
  `,
})
export class MetodosPago {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;

  readonly metodos = input<VentasPorMetodoPagoDto[]>([]);
  readonly cargando = input(false);
  /** Lo que se dice sin pagos: el tablero habla de hoy, reportes de un rango. */
  readonly vacio = input<ClaveI18n>('metodosPago.sinPagos');

  protected readonly total = computed(() =>
    this.metodos().reduce((suma, m) => suma + (m.total ?? 0), 0),
  );

  protected readonly filas = computed(() => {
    const maximo = Math.max(0, ...this.metodos().map((m) => m.total ?? 0));
    const total = this.total();
    return [...this.metodos()]
      .sort((a, b) => (b.total ?? 0) - (a.total ?? 0))
      .map((m) => ({
        ...m,
        largo: maximo > 0 ? ((m.total ?? 0) / maximo) * 100 : 0,
        parte: total > 0 ? Math.round(((m.total ?? 0) / total) * 100) : 0,
      }));
  });
}
