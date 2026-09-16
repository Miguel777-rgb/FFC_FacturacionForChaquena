import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { PersonalDesempenoApi, type DesempenoDto } from '../../api';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/**
 * Lo que hizo una persona en los ultimos 30 dias: lo que vendio, como la
 * calificaron y como cumplio sus turnos.
 *
 * Se muestra entero para cualquier cargo. Un cocinero no toma comandas y su
 * venta sale en cero, que es cierto; esconder la fila haria pensar que falta el
 * dato. La atencion es la unica parte de la calificacion que depende de quien
 * atendio: la comida es de cocina y el lugar, del local.
 */
@Component({
  selector: 'app-desempeno-trabajador',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="desempeno">
      <h3>{{ t('desempeno.titulo') }}</h3>
      @if (cargando()) {
        <p class="tenue">{{ t('comun.cargando') }}</p>
      } @else if (datos(); as d) {
        <dl>
          <div>
            <dt>{{ t('desempeno.comandas') }}</dt>
            <dd class="cifra">{{ d.comandas ?? 0 }}</dd>
          </div>
          <div>
            <dt>{{ t('desempeno.vendido') }}</dt>
            <dd class="cifra">S/ {{ d.vendido ?? 0 | number: '1.2-2' }}</dd>
          </div>
          <div>
            <dt>{{ t('desempeno.ticket') }}</dt>
            <dd class="cifra">S/ {{ d.ticketPromedio ?? 0 | number: '1.2-2' }}</dd>
          </div>
          <div>
            <dt>{{ t('desempeno.atencion') }}</dt>
            <dd>
              @if (d.satisfaccionAtencion !== undefined && d.satisfaccionAtencion !== null) {
                <span class="cifra">
                  {{
                    t('desempeno.atencionValor', {
                      puntaje: (d.satisfaccionAtencion | number: '1.1-1') ?? '',
                    })
                  }}
                </span>
                <small class="tenue">
                  · {{ tp('desempeno.calificaciones', d.calificaciones ?? 0) }}</small
                >
              } @else {
                <span class="tenue">{{ t('desempeno.sinCalificaciones') }}</span>
              }
            </dd>
          </div>
          <div>
            <dt>{{ t('desempeno.turnos') }}</dt>
            <dd class="cifra">
              {{
                t('desempeno.turnosValor', { asistidos: d.asistidos ?? 0, turnos: d.turnos ?? 0 })
              }}
            </dd>
          </div>
          <div>
            <dt>{{ t('desempeno.tardanzas') }}</dt>
            <dd>
              <span class="cifra">{{ d.tardanzas ?? 0 }}</span>
              @if ((d.minutosTarde ?? 0) > 0) {
                <small class="tenue">
                  · {{ t('desempeno.minutosTarde', { n: d.minutosTarde ?? 0 }) }}</small
                >
              }
            </dd>
          </div>
          <div>
            <dt>{{ t('desempeno.inasistencias') }}</dt>
            <dd class="cifra" [class.alerta]="(d.inasistencias ?? 0) > 0">
              {{ d.inasistencias ?? 0 }}
            </dd>
          </div>
          <div>
            <dt>{{ t('desempeno.horas') }}</dt>
            <dd class="cifra">{{ d.horasTrabajadas ?? 0 | number: '1.1-1' }} h</dd>
          </div>
        </dl>
        @if ((d.salidasSinMarcar ?? 0) > 0) {
          <p class="aviso-salidas">
            {{ tp('desempeno.salidasSinMarcar', d.salidasSinMarcar ?? 0) }}
          </p>
        }
      } @else {
        <p class="tenue">{{ t('desempeno.sinDatos') }}</p>
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

    dd.alerta {
      color: var(--critico);
    }

    .aviso-salidas {
      margin-top: var(--e2);
      font-size: var(--t-chico);
      color: var(--aviso);
    }
  `,
})
export class DesempenoTrabajador implements OnInit {
  private readonly api = inject(PersonalDesempenoApi);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;

  readonly trabajadorId = input.required<string | undefined>();

  protected readonly cargando = signal(true);
  protected readonly datos = signal<DesempenoDto | null>(null);

  ngOnInit(): void {
    const id = this.trabajadorId();
    if (!id) {
      this.cargando.set(false);
      return;
    }

    this.api.desempenoTrabajador({ id }).subscribe({
      next: (datos) => {
        this.datos.set(datos);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }
}
