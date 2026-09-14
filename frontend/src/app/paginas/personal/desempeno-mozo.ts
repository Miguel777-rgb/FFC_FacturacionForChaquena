import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { ReportesApi, type VentasPorMozoDto } from '../../api';
import { fechaIsoLocal, inicioDelDia } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/** Treinta dias contando hoy, como el rango «30 días» de Ventas y reportes. */
const DIAS = 29;

/**
 * Lo que vendio una persona como mozo en los ultimos 30 dias.
 *
 * Sale de `ventas-por-mozo`, el mismo reporte de Ventas y reportes, reducido a
 * una fila. Es una ficha basica a proposito: tardanzas, turnos y satisfaccion
 * por persona necesitan datos que el servidor todavia no guarda, y aqui no se
 * rellenan con nada.
 */
@Component({
  selector: 'app-desempeno-mozo',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="desempeno">
      <h3>{{ t('personal.desempeno') }}</h3>
      @if (cargando()) {
        <p class="tenue">{{ t('comun.cargando') }}</p>
      } @else if (fila(); as f) {
        <dl>
          <div>
            <dt>{{ t('kpis.comandas') }}</dt>
            <dd class="cifra">{{ f.comandas ?? 0 }}</dd>
          </div>
          <div>
            <dt>{{ t('kpis.vendido') }}</dt>
            <dd class="cifra">S/ {{ f.total ?? 0 | number: '1.2-2' }}</dd>
          </div>
          <div>
            <dt>{{ t('kpis.ticketPromedio') }}</dt>
            <dd class="cifra">S/ {{ f.ticketPromedio ?? 0 | number: '1.2-2' }}</dd>
          </div>
        </dl>
      } @else {
        <p class="tenue">{{ t('personal.sinVentas') }}</p>
      }
    </section>
  `,
  styles: `
    .desempeno {
      margin-top: var(--e3);
      padding-top: var(--e3);
      border-top: 1px solid var(--linea);
    }

    h3 {
      margin: 0 0 var(--e2);
      font-family: var(--f-texto);
      font-size: var(--t-texto);
      font-weight: 600;
      letter-spacing: 0;
    }

    p {
      margin: 0;
    }

    dl {
      display: flex;
      flex-wrap: wrap;
      gap: var(--e3) var(--e5);
      margin: 0;
    }

    dt {
      font-size: var(--t-chico);
      font-weight: 600;
      color: var(--tenue);
    }

    dd {
      margin: 0;
      color: var(--tinta);
    }
  `,
})
export class DesempenoMozo implements OnInit {
  private readonly reportesApi = inject(ReportesApi);
  protected readonly t = inject(I18nService).t;

  readonly trabajadorId = input.required<string | undefined>();

  protected readonly cargando = signal(true);
  protected readonly fila = signal<VentasPorMozoDto | null>(null);

  ngOnInit(): void {
    const id = this.trabajadorId();
    if (!id) {
      this.cargando.set(false);
      return;
    }

    this.reportesApi
      .ventasPorMozo({ desde: fechaIsoLocal(inicioDelDia(DIAS)), hasta: fechaIsoLocal(new Date()) })
      .pipe(catchError(() => of([] as VentasPorMozoDto[])))
      .subscribe((lista) => {
        this.fila.set(lista.find((m) => m.mozoId === id) ?? null);
        this.cargando.set(false);
      });
  }
}
