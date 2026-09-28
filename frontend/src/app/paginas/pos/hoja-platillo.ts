import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
} from '@angular/core';

import type { ComplementoResponseDto, PlatilloDisponibleDto } from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { urlDeArchivo } from '../../nucleo/marca/archivos';
import { precioUnitario, type ComplementoElegido, type EleccionPlatillo } from './linea';

/**
 * La hoja de un plato: lo que se pide de el antes de mandarlo a la comanda.
 *
 * Es el paso que faltaba entre tocar el plato y verlo en la comanda. Antes un
 * toque sumaba una unidad y la observacion se escribia despues en la linea, que
 * era una por platillo: «sin cebolla» valia para todos los Lomos de la mesa.
 * Aqui la observacion y los complementos se deciden para estos platos, y si
 * otro Lomo lleva otra cosa, se agrega aparte y es otra linea.
 *
 * Sirve para agregar y para corregir —una linea de la comanda en armado o un
 * detalle de una comanda ya enviada—: quien la abre le pasa lo que ya habia en
 * `inicial` y decide que hacer con lo que devuelve `confirmar`. No se cierra
 * sola: al corregir una comanda enviada hay que esperar al servidor, y mientras
 * tanto `ocupado` deja el boton quieto.
 */
@Component({
  selector: 'app-hoja-platillo',
  imports: [DecimalPipe, Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialogo modo="hoja" [titulo]="platillo()?.nombre ?? ''" [(abierto)]="abierto">
      @if (platillo(); as p) {
        <div class="hoja">
          @if (urlDeArchivo(p.fotoId); as foto) {
            <img class="foto" [src]="foto" alt="" />
          }

          <div class="datos">
            @if (p.descripcion) {
              <p class="descripcion">{{ p.descripcion }}</p>
            }
            @if (p.tiempoPreparacionMinutos || p.alergenos?.length) {
              <p class="detalles">
                @if (p.tiempoPreparacionMinutos) {
                  <span>{{ t('pos.minutos', { n: p.tiempoPreparacionMinutos }) }}</span>
                }
                @if (p.alergenos?.length) {
                  <span class="alergenos">
                    <app-icono nombre="alerta" [tamano]="14" />
                    {{ t('pos.contiene', { alergenos: p.alergenos?.join(', ') ?? '' }) }}
                  </span>
                }
              </p>
            }
            <p class="precio cifra">S/&nbsp;{{ p.precioVentaBase | number: '1.2-2' }}</p>
            @if (
              p.porcionesPosibles !== null &&
              p.porcionesPosibles !== undefined &&
              p.porcionesPosibles <= 5
            ) {
              <p class="quedan">{{ t('pos.quedan', { n: p.porcionesPosibles }) }}</p>
            }
          </div>

          <div class="fila-campo">
            <span class="rotulo" [id]="id + '-cantidad'">{{ t('pos.cantidad') }}</span>
            <div class="contador" role="group" [attr.aria-labelledby]="id + '-cantidad'">
              <button
                type="button"
                class="paso"
                [disabled]="cantidad() <= 1"
                (click)="cantidad.set(cantidad() - 1)"
                [attr.aria-label]="t('pos.quitarUnidad', { platillo: p.nombre ?? '' })"
              >
                <app-icono nombre="menos" [tamano]="18" />
              </button>
              <span class="cantidad cifra" aria-live="polite">{{ cantidad() }}</span>
              <button
                type="button"
                class="paso"
                (click)="cantidad.set(cantidad() + 1)"
                [attr.aria-label]="t('pos.agregarUnidad', { platillo: p.nombre ?? '' })"
              >
                <app-icono nombre="mas" [tamano]="18" />
              </button>
            </div>
          </div>

          <!-- El complemento se cobra por plato: dos lomos con una gaseosa cada
               uno son dos gaseosas. El servidor multiplica por la cantidad de la
               linea, y el total de aqui hace lo mismo. -->
          @if (complementos().length > 0) {
            <fieldset class="complementos">
              <legend>{{ t('pos.complementosPorPlato') }}</legend>
              <ul>
                @for (c of complementos(); track c.id) {
                  <li>
                    <span class="nombre">{{ c.nombre }}</span>
                    <span class="precio-extra cifra"
                      >+&nbsp;S/&nbsp;{{ c.precioAdicional | number: '1.2-2' }}</span
                    >
                    <div class="contador">
                      <button
                        type="button"
                        class="paso"
                        [disabled]="cantidadDe(c) === 0"
                        (click)="cambiarComplemento(c, -1)"
                        [attr.aria-label]="t('pos.quitarComplemento', { nombre: c.nombre ?? '' })"
                      >
                        <app-icono nombre="menos" [tamano]="16" />
                      </button>
                      <span class="cantidad cifra">{{ cantidadDe(c) }}</span>
                      <button
                        type="button"
                        class="paso"
                        (click)="cambiarComplemento(c, 1)"
                        [attr.aria-label]="t('pos.agregarComplemento', { nombre: c.nombre ?? '' })"
                      >
                        <app-icono nombre="mas" [tamano]="16" />
                      </button>
                    </div>
                  </li>
                }
              </ul>
            </fieldset>
          }

          <!-- La observacion sale impresa en cocina con estos platos, y solo con
               estos: viaja como \`excepcionesNota\` de su propia linea. -->
          <label class="campo">
            <span>{{ t('pos.observacion') }}</span>
            <textarea
              rows="2"
              [attr.placeholder]="t('pos.notaPlaceholder')"
              [value]="nota()"
              (input)="nota.set($any($event.target).value)"
            ></textarea>
            <small class="ayuda">{{ t('pos.observacionAyuda') }}</small>
          </label>
        </div>
      }

      <div pie>
        <button
          type="button"
          (click)="aceptar()"
          [disabled]="ocupado()"
          [attr.aria-busy]="ocupado()"
        >
          {{
            t(accion() === 'agregar' ? 'pos.agregarPor' : 'pos.guardarPor', {
              total: (total() | number: '1.2-2') ?? '',
            })
          }}
        </button>
      </div>
    </app-dialogo>
  `,
  styles: `
    .hoja {
      display: flex;
      flex-direction: column;
      gap: var(--e4);
    }

    /* La foto ayuda a reconocer el plato: ancha y baja, para que la cantidad y
       el boton sigan a la vista sin desplazar en el celular. */
    .foto {
      width: 100%;
      aspect-ratio: 16 / 7;
      object-fit: cover;
      background: var(--hundido);
      border-radius: var(--radio-chico);
    }

    .datos p {
      margin: 0;
    }

    .datos {
      display: flex;
      flex-direction: column;
      gap: var(--e1);
    }

    .descripcion {
      color: var(--texto);
    }

    .detalles {
      display: flex;
      flex-wrap: wrap;
      gap: var(--e1) var(--e3);
      font-size: var(--t-chico);
      color: var(--texto);
    }

    .alergenos {
      display: inline-flex;
      align-items: center;
      gap: var(--e1);
      color: var(--aviso);
      font-weight: 600;
    }

    .precio {
      font-size: var(--t-h4);
      font-weight: 600;
      color: var(--tinta);
    }

    .quedan {
      font-size: var(--t-chico);
      color: var(--aviso);
    }

    .fila-campo {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--e3);
    }

    .rotulo,
    legend {
      font-size: var(--t-chico);
      font-weight: 600;
      color: var(--texto);
    }

    .contador {
      display: flex;
      align-items: center;
      gap: var(--e2);
    }

    .paso {
      width: var(--control);
      padding: 0;
      color: var(--acento);
      background: transparent;
      border-color: var(--linea-fuerte);
    }

    .paso:hover:not(:disabled),
    .paso:active:not(:disabled) {
      background: var(--acento-suave);
    }

    .cantidad {
      min-width: 1.75rem;
      font-weight: 600;
      text-align: center;
      color: var(--tinta);
    }

    .complementos {
      margin: 0;
      padding: 0;
      border: 0;

      legend {
        padding: 0;
        margin-bottom: var(--e2);
      }

      ul {
        display: flex;
        flex-direction: column;
        margin: 0;
        padding: 0;
        list-style: none;
      }

      li {
        display: flex;
        align-items: center;
        gap: var(--e2);
        padding: var(--e1) 0;
      }

      li + li {
        border-top: 1px solid var(--linea);
      }

      .nombre {
        flex: 1;
        min-width: 0;
        color: var(--tinta);
      }

      .precio-extra {
        font-size: var(--t-chico);
        color: var(--texto);
      }

      .paso {
        width: var(--control-chico);
        min-height: var(--control-chico);
      }
    }

    textarea {
      resize: vertical;
    }

    .ayuda {
      font-size: var(--t-chico);
      color: var(--tenue);
    }

    /* El boton de la hoja ocupa todo el ancho: es lo unico que queda por hacer. */
    [pie] button {
      flex: 1 1 auto;
    }
  `,
})
export class HojaPlatillo {
  protected readonly t = inject(I18nService).t;
  protected readonly urlDeArchivo = urlDeArchivo;

  readonly platillo = input<PlatilloDisponibleDto | null>(null);
  readonly complementos = input<ComplementoResponseDto[]>([]);
  /** Lo que ya tenia la linea que se corrige; null para un plato nuevo. */
  readonly inicial = input<EleccionPlatillo | null>(null);
  readonly accion = input<'agregar' | 'guardar'>('agregar');
  readonly ocupado = input(false);
  readonly abierto = model(false);

  readonly confirmar = output<EleccionPlatillo>();

  protected readonly id = `hoja-${Math.random().toString(36).slice(2, 8)}`;
  protected readonly cantidad = signal(1);
  protected readonly nota = signal('');
  protected readonly elegidos = signal<ComplementoElegido[]>([]);

  protected readonly total = computed(() => {
    const p = this.platillo();
    return p ? precioUnitario(p, this.elegidos()) * this.cantidad() : 0;
  });

  constructor() {
    // Cada vez que se abre, o que cambia el plato con la hoja abierta, empieza
    // de lo que traia la linea o de cero: nada de lo del plato anterior.
    effect(() => {
      if (!this.abierto()) return;
      this.platillo();
      const inicial = untracked(() => this.inicial());
      this.cantidad.set(inicial?.cantidad ?? 1);
      this.nota.set(inicial?.nota ?? '');
      this.elegidos.set(inicial?.complementos.map((c) => ({ ...c })) ?? []);
    });
  }

  protected cantidadDe(complemento: ComplementoResponseDto): number {
    return this.elegidos().find((c) => c.complemento.id === complemento.id)?.cantidad ?? 0;
  }

  protected cambiarComplemento(complemento: ComplementoResponseDto, delta: number): void {
    const cantidad = Math.max(this.cantidadDe(complemento) + delta, 0);
    this.elegidos.update((lista) => {
      if (cantidad === 0) return lista.filter((c) => c.complemento.id !== complemento.id);
      // Se conserva el orden en que se eligieron: es el que se lee en la linea.
      const indice = lista.findIndex((c) => c.complemento.id === complemento.id);
      const nuevo = { complemento, cantidad };
      if (indice < 0) return [...lista, nuevo];
      return lista.map((c, i) => (i === indice ? nuevo : c));
    });
  }

  protected aceptar(): void {
    if (this.ocupado()) return;
    this.confirmar.emit({
      cantidad: this.cantidad(),
      nota: this.nota(),
      complementos: this.elegidos(),
    });
  }
}
