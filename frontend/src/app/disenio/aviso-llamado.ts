import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { LlamadosService } from '../nucleo/llamados/llamados.service';
import { SilencioLlamadosService } from '../nucleo/llamados/silencio.service';
import { TIMBRE_LLAMADO, contextoCompartido, tocarNotas } from '../nucleo/sonido/timbre';
import { Icono } from './icono';

/** Cuantos llamados se ven a la vez; el resto espera turno en «Y N llamados más». */
const VISIBLES = 3;

/** Mientras alguno espera vuelve a sonar: en un salon con ruido el primer timbre se pierde. */
const REPETIR_MS = 30_000;

/** Dos golpes cortos: se notan en el bolsillo sin parecer una llamada de telefono. */
const VIBRACION = [300, 150, 300];

/**
 * El llamado de cocina en la pantalla del mozo, sea cual sea la pantalla.
 *
 * Es una franja sobre el contenido y no un dialogo, como el aviso de
 * inactividad: quien esta a medio tomar una comanda la termina sin apartar
 * antes un modal. Se queda hasta que algun mozo pulsa «Voy»; entonces
 * desaparece de todos.
 *
 * Solo la monta el armazon, y solo para sesiones de mozo. El cliente STOMP no
 * viene con ella: lo descarga el servicio con `import()` al abrir el canal.
 */
@Component({
  selector: 'app-aviso-llamado',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (visibles().length > 0) {
      <section class="llamados" role="alert" [attr.aria-label]="t('llamado.region')">
        @for (llamado of visibles(); track llamado.id) {
          <div class="llamado">
            <app-icono nombre="campana" [tamano]="20" />
            <p>
              <strong>{{ t('llamado.titulo') }}</strong>
              <span>{{
                t('llamado.detalle', {
                  donde: llamados.donde(llamado),
                  correlativo: llamado.correlativo,
                  nombre: llamado.llamadoPor,
                })
              }}</span>
            </p>
            <button
              type="button"
              class="exito"
              [disabled]="!llamados.conectado() || llamados.respondiendo().has(llamado.id)"
              [attr.aria-busy]="llamados.respondiendo().has(llamado.id)"
              [attr.aria-label]="
                t('llamado.voyA', {
                  donde: llamados.donde(llamado),
                  correlativo: llamado.correlativo,
                })
              "
              (click)="llamados.atender(llamado.id)"
            >
              {{ t('llamado.voy') }}
            </button>
          </div>
        }
        @if (ocultos() > 0) {
          <p class="mas">{{ tp('llamado.mas', ocultos()) }}</p>
        }
      </section>
    }

    @if (llamados.sinConexion()) {
      <p class="sin-conexion" role="status">
        <app-icono nombre="alerta" [tamano]="16" />
        {{ t('llamado.sinConexion') }}
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .llamados {
      background: var(--ok-suave);
      border-bottom: 1px solid var(--linea);
    }

    .llamado {
      display: flex;
      align-items: center;
      gap: var(--e3);
      padding: var(--e2) var(--e4);
    }

    .llamado + .llamado {
      border-top: 1px solid var(--linea);
    }

    .llamado > app-icono {
      flex: none;
      color: var(--ok);
    }

    /* El titulo y el detalle en una linea en PC; en el celular el detalle baja
       solo, sin cortar la mesa a la mitad. */
    .llamado p {
      display: flex;
      flex-wrap: wrap;
      column-gap: var(--e2);
      flex: 1;
      min-width: 0;
      margin: 0;
      font-size: var(--t-texto);
      color: var(--texto);
    }

    strong {
      font-weight: 600;
      color: var(--tinta);
    }

    .llamado button {
      flex: none;
    }

    .mas {
      margin: 0;
      padding: 0 var(--e4) var(--e2) calc(var(--e4) + 20px + var(--e3));
      font-size: var(--t-chico);
      color: var(--texto);
    }

    .sin-conexion {
      display: flex;
      align-items: center;
      gap: var(--e2);
      margin: 0;
      padding: var(--e2) var(--e4);
      font-size: var(--t-chico);
      color: var(--aviso);
      background: var(--aviso-suave);
      border-bottom: 1px solid var(--linea);
    }
  `,
})
export class AvisoLlamado {
  protected readonly llamados = inject(LlamadosService);
  private readonly silencio = inject(SilencioLlamadosService);
  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;

  protected readonly visibles = computed(() => this.llamados.pendientes().slice(0, VISIBLES));
  protected readonly ocultos = computed(() =>
    Math.max(0, this.llamados.pendientes().length - VISIBLES),
  );
  /** Un booleano aparte: la repeticion no se reinicia cada vez que cambia la lista. */
  private readonly hayPendientes = computed(() => this.llamados.pendientes().length > 0);

  constructor() {
    this.llamados.usar();

    this.llamados.timbre.pipe(takeUntilDestroyed()).subscribe(() => this.sonar());

    effect((alLimpiar) => {
      if (!this.hayPendientes()) return;
      const repeticion = setInterval(() => this.sonar(), REPETIR_MS);
      alLimpiar(() => clearInterval(repeticion));
    });

    // El navegador solo deja sonar despues de un gesto. Al recargar, la sesion
    // sigue abierta pero no hubo ninguno: el primer toque o tecla, donde sea,
    // deja el audio listo para cuando llegue un llamado.
    const desbloquear = () => void contextoCompartido();
    for (const evento of ['pointerdown', 'keydown'] as const) {
      document.addEventListener(evento, desbloquear, { once: true, capture: true });
    }
    inject(DestroyRef).onDestroy(() => {
      for (const evento of ['pointerdown', 'keydown'] as const) {
        document.removeEventListener(evento, desbloquear, { capture: true });
      }
    });
  }

  private sonar(): void {
    if (this.silencio.silenciado()) return;
    const contexto = contextoCompartido();
    if (contexto) tocarNotas(contexto, TIMBRE_LLAMADO, 0.16);
    try {
      navigator.vibrate?.(VIBRACION);
    } catch {
      /* sin vibracion quedan el sonido y la franja */
    }
  }
}
