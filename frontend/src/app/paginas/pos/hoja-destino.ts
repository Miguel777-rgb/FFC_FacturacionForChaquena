import { ChangeDetectionStrategy, Component, inject, input, model, output } from '@angular/core';

import {
  CrearOrdenRequestDtoTipoOrdenEnum,
  MesaResponseDtoEstadoEnum,
  type MesaResponseDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { I18nService } from '../../nucleo/i18n/i18n.service';

const TIPOS = CrearOrdenRequestDtoTipoOrdenEnum;

/**
 * La hoja del destino de la comanda: en mesa, retiro en local o delivery.
 *
 * Se abre desde el chip que esta junto al buscador de la carta, asi que el mapa
 * de mesas ya no ocupa la pantalla antes de la carta. Elegir una mesa es lo
 * unico que hay que hacer en ella: la hoja se cierra sola. Para retiro y
 * delivery hay un «Listo», porque la direccion se escribe.
 *
 * Solo pinta y avisa; el estado vive en el POS, que es quien lo envia.
 */
@Component({
  selector: 'app-hoja-destino',
  imports: [Dialogo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-dialogo modo="hoja" [titulo]="t('pos.destino')" [(abierto)]="abierto">
      <!-- Tres opciones excluyentes, siempre visibles, como un selector
           segmentado: un desplegable cuesta dos toques y esconde lo demas. -->
      <div class="tipos" role="group" [attr.aria-label]="t('pos.tipoComanda')">
        @for (o of opciones; track o.tipo) {
          <button
            type="button"
            [class.elegido]="tipo() === o.tipo"
            [attr.aria-pressed]="tipo() === o.tipo"
            (click)="elegirTipo.emit(o.tipo)"
          >
            {{ t(o.nombre) }}
          </button>
        }
      </div>

      @if (tipo() === TIPOS.MESA) {
        <!-- Ocupada no se bloquea: el servidor solo rechaza la mesa
             inhabilitada, y una mesa larga puede llevar dos comandas. -->
        <div class="mesas">
          @for (m of mesas(); track m.id) {
            <button
              type="button"
              class="mesa"
              [class.elegida]="m.id === mesaElegida()?.id"
              [class.ocupada]="m.estado === ESTADOS.OCUPADA"
              [disabled]="m.estado === ESTADOS.INHABILITADA"
              [attr.aria-pressed]="m.id === mesaElegida()?.id"
              (click)="alElegirMesa(m)"
            >
              <span class="numero">{{ m.numero }}</span>
              <span class="detalle">{{ m.zona || t('comun.sinZona') }}</span>
              <span class="estado">{{ tEnum('mesa', m.estado) }}</span>
            </button>
          } @empty {
            <p class="vacio">{{ t(cargando() ? 'pos.cargandoMesas' : 'pos.sinMesas') }}</p>
          }
        </div>
      } @else if (tipo() === TIPOS.DELIVERY) {
        <label class="campo">
          <span>{{ t('pos.direccionEntrega') }}</span>
          <input
            type="text"
            [attr.placeholder]="t('pos.direccionEjemplo')"
            [value]="direccion()"
            (input)="anotarDireccion.emit($any($event.target).value)"
          />
        </label>
        <p class="vacio">{{ t('pos.avisoDelivery') }}</p>
      } @else {
        <p class="vacio">{{ t('pos.avisoRetiro') }}</p>
      }

      @if (tipo() !== TIPOS.MESA) {
        <div pie>
          <button type="button" (click)="abierto.set(false)">{{ t('pos.listo') }}</button>
        </div>
      }
    </app-dialogo>
  `,
  styles: `
    .tipos {
      display: flex;
      gap: 2px;
      margin-bottom: var(--e4);
      padding: 2px;
      background: var(--hundido);
      border: 1px solid var(--linea);
      border-radius: var(--radio);

      button {
        flex: 1;
        min-width: 0;
        padding: 0 var(--e2);
        font-weight: 500;
        color: var(--texto);
        background: transparent;
        border-radius: var(--radio-chico);
      }

      button:hover:not(:disabled),
      button:active:not(:disabled) {
        color: var(--tinta);
        background: transparent;
      }

      button.elegido,
      button.elegido:hover:not(:disabled) {
        font-weight: 600;
        color: var(--acento);
        background: var(--superficie);
        border-color: var(--linea);
        box-shadow: var(--sombra);
      }
    }

    .mesas {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(6.5rem, 1fr));
      gap: var(--e2);
    }

    .mesa {
      flex-direction: column;
      align-items: flex-start;
      gap: 0;
      min-height: calc(var(--toque) + var(--e4));
      padding: var(--e2) var(--e3);
      font-weight: 400;
      text-align: left;
      color: var(--tinta);
      background: var(--superficie);
      border: 1px solid var(--linea-fuerte);
    }

    .numero {
      font-size: 1.05rem;
      font-weight: 600;
    }

    .detalle,
    .estado {
      font-size: var(--t-chico);
      color: var(--texto);
    }

    .mesa:hover:not(:disabled),
    .mesa:active:not(:disabled) {
      background: var(--acento-suave);
      border-color: var(--acento);
    }

    .mesa.ocupada .estado {
      font-weight: 600;
      color: var(--aviso);
    }

    .mesa.elegida,
    .mesa.elegida:hover:not(:disabled) {
      color: var(--sobre-relleno);
      background: var(--acento-relleno);
      border-color: var(--acento-relleno);

      .detalle,
      .estado {
        color: var(--sobre-relleno);
      }
    }
  `,
})
export class HojaDestino {
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly TIPOS = TIPOS;
  protected readonly ESTADOS = MesaResponseDtoEstadoEnum;

  protected readonly opciones = [
    { tipo: TIPOS.MESA, nombre: 'pos.enMesa' },
    { tipo: TIPOS.RETIRO_LOCAL, nombre: 'pos.retiro' },
    { tipo: TIPOS.DELIVERY, nombre: 'pos.delivery' },
  ] as const;

  readonly tipo = input.required<CrearOrdenRequestDtoTipoOrdenEnum>();
  readonly mesas = input<MesaResponseDto[]>([]);
  readonly mesaElegida = input<MesaResponseDto | null>(null);
  readonly direccion = input('');
  readonly cargando = input(false);
  readonly abierto = model(false);

  readonly elegirTipo = output<CrearOrdenRequestDtoTipoOrdenEnum>();
  readonly elegirMesa = output<MesaResponseDto>();
  readonly anotarDireccion = output<string>();

  protected alElegirMesa(mesa: MesaResponseDto): void {
    if (mesa.estado === MesaResponseDtoEstadoEnum.INHABILITADA) return;
    this.elegirMesa.emit(mesa);
    this.abierto.set(false);
  }
}
