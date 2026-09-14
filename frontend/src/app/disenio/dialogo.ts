import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  input,
  model,
  viewChild,
} from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { Icono } from './icono';

let siguienteId = 0;

/**
 * Dialogo sobre el `<dialog>` nativo, en dos formas: `modal` centrado o `cajon`
 * pegado a la derecha (el detalle de una orden, una edicion larga).
 *
 * Se usa el elemento del navegador y no una capa propia porque `showModal()` ya
 * resuelve lo dificil: deja inerte el resto de la pagina, atrapa el foco, lo
 * devuelve al cerrar y cierra con Escape. Una capa hecha a mano suele olvidar
 * alguna de las cuatro.
 *
 * El contenido va en el cuerpo; los botones, en un elemento con el atributo
 * `pie`, que se proyecta abajo:
 *
 * ```html
 * <app-dialogo [titulo]="..." [(abierto)]="editando">
 *   <p>...</p>
 *   <div pie><button>...</button></div>
 * </app-dialogo>
 * ```
 */
@Component({
  selector: 'app-dialogo',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog
      #dialogo
      [class.cajon]="modo() === 'cajon'"
      [attr.aria-labelledby]="idTitulo"
      (close)="alCerrar()"
      (click)="clicEnFondo($event)"
    >
      <div class="caja">
        <header>
          <h2 [id]="idTitulo">{{ titulo() }}</h2>
          <button
            type="button"
            class="fantasma icono chico"
            (click)="abierto.set(false)"
            [attr.aria-label]="t('comun.cerrar')"
          >
            <app-icono nombre="quitar" [tamano]="18" />
          </button>
        </header>
        <div class="cuerpo">
          <ng-content />
        </div>
        <footer>
          <ng-content select="[pie]" />
        </footer>
      </div>
    </dialog>
  `,
  styles: `
    dialog {
      width: min(32rem, calc(100vw - 2rem));
      max-height: calc(100dvh - 2rem);
      padding: 0;
      color: var(--texto);
      background: var(--superficie);
      border: 1px solid var(--linea);
      border-radius: var(--radio);
      box-shadow: var(--sombra-elevada);
    }

    dialog[open] {
      animation: entrar 0.18s cubic-bezier(0.16, 1, 0.3, 1);
    }

    dialog::backdrop {
      background: rgb(17 24 39 / 0.45);
    }

    .caja {
      display: flex;
      flex-direction: column;
      max-height: inherit;
    }

    header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--e3);
      padding: var(--e4) var(--e4) var(--e2) var(--e5);
    }

    h2 {
      margin: 0;
      font-size: var(--t-h3);
    }

    .cuerpo {
      padding: var(--e2) var(--e5) var(--e4);
      overflow-y: auto;
    }

    footer {
      display: flex;
      flex-wrap: wrap;
      justify-content: flex-end;
      gap: var(--e2);
      padding: var(--e3) var(--e5) var(--e5);
    }

    footer:empty {
      display: none;
    }

    /* El pie proyectado es un contenedor mas: sus botones se reparten igual. */
    footer ::ng-deep [pie] {
      display: contents;
    }

    dialog.cajon {
      width: min(28rem, 100vw);
      height: 100dvh;
      max-height: 100dvh;
      margin: 0 0 0 auto;
      border-radius: var(--radio) 0 0 var(--radio);
    }

    dialog.cajon[open] {
      animation-name: deslizar;
    }

    dialog.cajon .caja {
      height: 100%;
    }

    dialog.cajon .cuerpo {
      flex: 1;
    }

    @keyframes entrar {
      from {
        opacity: 0;
        transform: translateY(8px);
      }
    }

    @keyframes deslizar {
      from {
        transform: translateX(24px);
        opacity: 0;
      }
    }

    @media (max-width: 47.99rem) {
      dialog.cajon {
        width: 100vw;
        border-radius: 0;
      }

      footer ::ng-deep [pie] > * {
        flex: 1 1 auto;
      }
    }
  `,
})
export class Dialogo {
  readonly titulo = input.required<string>();
  readonly modo = input<'modal' | 'cajon'>('modal');
  readonly abierto = model(false);

  protected readonly t = inject(I18nService).t;
  protected readonly idTitulo = `dialogo-${++siguienteId}`;

  private readonly dialogo = viewChild.required<ElementRef<HTMLDialogElement>>('dialogo');

  constructor() {
    effect(() => {
      const elemento = this.dialogo().nativeElement;
      if (this.abierto()) {
        if (elemento.open) return;
        // jsdom no implementa `showModal`: en las pruebas basta con abrirlo.
        if (typeof elemento.showModal === 'function') {
          elemento.showModal();
        } else {
          elemento.setAttribute('open', '');
        }
      } else if (elemento.open) {
        elemento.close();
      }
    });
  }

  /** Escape y `close()` terminan aqui: el estado de fuera se entera. */
  protected alCerrar(): void {
    if (this.abierto()) this.abierto.set(false);
  }

  /** Un clic en el fondo oscuro llega al propio `<dialog>`, no a su contenido. */
  protected clicEnFondo(evento: MouseEvent): void {
    if (evento.target === this.dialogo().nativeElement) this.abierto.set(false);
  }
}
