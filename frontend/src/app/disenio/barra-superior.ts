import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';

import { AsistenciaService } from '../nucleo/asistencia/asistencia.service';
import { I18nService } from '../nucleo/i18n/i18n.service';
import { LogoService } from '../nucleo/marca/logo.service';
import { SelectorIdioma } from './selector-idioma';
import { Icono } from './icono';
import { PanelLateral } from './panel-lateral';
import { SelectorTema } from './selector-tema';

/**
 * La barra de arriba del celular: el menu, la marca, el idioma y el tema.
 *
 * En 390px una regleta de iconos se comia un sexto del ancho y la barra de
 * idiomas flotaba encima de los botones. Aqui la navegacion se abre en un cajon
 * a la izquierda —el mismo panel lateral, en modo `cajon`— y el idioma queda a
 * la vista en la barra, sin tapar nada.
 *
 * El cajon es un `<dialog>` modal: deja inerte el resto, cierra con Escape y
 * devuelve el foco al boton del menu. Se cierra tambien al pulsar un destino,
 * al tocar el fondo y al terminar cualquier navegacion.
 */
@Component({
  selector: 'app-barra-superior',
  imports: [PanelLateral, SelectorIdioma, SelectorTema, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="barra">
      <button
        type="button"
        class="fantasma icono"
        aria-haspopup="dialog"
        [attr.aria-expanded]="abierto()"
        [attr.aria-label]="t('panel.abrirMenu')"
        (click)="abierto.set(true)"
      >
        <app-icono nombre="menuLineas" />
      </button>

      <span class="identidad">
        @if (logo.logo(); as fuente) {
          <img [src]="fuente" alt="" />
        }
        <span class="marca">Chaquena</span>
      </span>

      <!-- Marcar asistencia sin ir al perfil: es el gesto del primer y el
           ultimo minuto del turno, y se hace con el celular en la mano. -->
      @if (asistencia.conocida()) {
        <button
          type="button"
          class="fantasma icono marcar"
          [class.dentro]="asistencia.dentro()"
          [attr.aria-label]="
            t(asistencia.dentro() ? 'asistencia.marcarSalida' : 'asistencia.marcarEntrada')
          "
          [attr.title]="
            t(asistencia.dentro() ? 'asistencia.marcarSalida' : 'asistencia.marcarEntrada')
          "
          [attr.aria-busy]="asistencia.marcando()"
          [disabled]="asistencia.marcando()"
          (click)="asistencia.marcar()"
        >
          <app-icono nombre="reloj" />
        </button>
      }

      <app-selector-idioma variante="integrada" />
      <app-selector-tema />
    </header>

    <dialog
      #cajon
      class="cajon"
      [attr.aria-label]="t('panel.superficies')"
      (close)="abierto.set(false)"
      (click)="clicEnFondo($event)"
    >
      @if (abierto()) {
        <button
          type="button"
          class="fantasma icono cerrar"
          [attr.aria-label]="t('panel.cerrarMenu')"
          (click)="abierto.set(false)"
        >
          <app-icono nombre="quitar" />
        </button>
        <app-panel-lateral [cajon]="true" (navego)="abierto.set(false)" />
      }
    </dialog>
  `,
  styles: `
    .barra {
      display: flex;
      align-items: center;
      gap: var(--e1);
      min-height: calc(var(--control) + var(--e2));
      padding: var(--e1) var(--e2);
      background: var(--superficie);
      border-bottom: 1px solid var(--linea);
    }

    .identidad {
      display: flex;
      align-items: center;
      gap: var(--e2);
      flex: 1;
      min-width: 0;
      overflow: hidden;
      white-space: nowrap;
    }

    .identidad img {
      width: 28px;
      height: 28px;
      object-fit: contain;
      border-radius: var(--radio-chico);
    }

    /* Con el reloj en la barra no cabe el nombre entero en 390 px: si hay logo,
       el logo ya dice de quien es la pantalla y el nombre cortado a medias sobra. */
    @media (max-width: 26rem) {
      .identidad img + .marca {
        display: none;
      }
    }

    /* Dentro se nota en el color del reloj: el mismo boton sirve para entrar y salir. */
    .marcar.dentro {
      color: var(--ok);
    }

    dialog.cajon {
      width: min(18rem, 86vw);
      max-width: none;
      height: 100dvh;
      max-height: 100dvh;
      margin: 0;
      padding: 0;
      background: var(--superficie);
      border: none;
      border-right: 1px solid var(--linea);
      box-shadow: var(--sombra-elevada);
    }

    dialog.cajon[open] {
      animation: entrar 0.2s cubic-bezier(0.16, 1, 0.3, 1);
    }

    dialog.cajon::backdrop {
      background: rgb(17 24 39 / 0.45);
    }

    dialog app-panel-lateral {
      display: block;
      height: 100%;
    }

    .cerrar {
      position: absolute;
      top: var(--e3);
      right: var(--e2);
      z-index: 1;
    }

    @keyframes entrar {
      from {
        opacity: 0;
        transform: translateX(-24px);
      }
    }
  `,
})
export class BarraSuperior {
  protected readonly t = inject(I18nService).t;
  protected readonly logo = inject(LogoService);
  protected readonly asistencia = inject(AsistenciaService);

  protected readonly abierto = signal(false);

  private readonly cajon = viewChild.required<ElementRef<HTMLDialogElement>>('cajon');

  constructor() {
    effect(() => {
      const dialogo = this.cajon().nativeElement;
      if (this.abierto()) {
        if (dialogo.open) return;
        // jsdom no implementa `showModal`: en las pruebas basta con abrirlo.
        if (typeof dialogo.showModal === 'function') {
          dialogo.showModal();
        } else {
          dialogo.setAttribute('open', '');
        }
      } else if (dialogo.open) {
        dialogo.close();
      }
    });

    // Cualquier navegacion cierra el cajon, venga de un enlace o de un
    // redirect: nadie quiere aterrizar en otra pantalla con el menu encima.
    inject(Router)
      .events.pipe(
        filter((e) => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.abierto.set(false));
  }

  /** Un clic en el fondo oscuro llega al propio `<dialog>`, no a su contenido. */
  protected clicEnFondo(evento: MouseEvent): void {
    if (evento.target === this.cajon().nativeElement) this.abierto.set(false);
  }
}
