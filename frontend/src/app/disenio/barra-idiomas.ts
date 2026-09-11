import { ChangeDetectionStrategy, Component, inject } from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import type { Idioma } from '../nucleo/i18n/idioma';

/**
 * La barra de idiomas: tres banderas fijas en una esquina, encima de todo.
 *
 * Va superpuesta y no dentro del panel lateral porque cambiar de idioma no es
 * navegar: quien lo necesita es justo quien no entiende lo que esta leyendo, y
 * a esa persona no se le puede pedir que primero encuentre un menu. Se pinta
 * fuera del `@if` de la sesion, asi que tambien esta en la pantalla de entrar.
 *
 * Las banderas van dibujadas a mano, como los iconos: los emoji de bandera no
 * se pintan en Windows —salen las dos letras del pais— y una libreria de
 * banderas costaria mas de lo que ahorra. Son tres.
 *
 * Una bandera no es un idioma, asi que cada boton lleva el nombre del idioma en
 * `aria-label` y en `title`: el lector de pantalla y el raton dicen «Portugues»,
 * no «Brasil». Se eligen los paises que corresponden a lo que hay escrito en
 * cada diccionario: Peru es el del local, y el portugues es el de Brasil
 * —«cardapio», «garcom»—, no el de Portugal.
 */
@Component({
  selector: 'app-barra-idiomas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="barra" role="group" [attr.aria-label]="i18n.t('idioma.elegir')">
      @for (i of i18n.idiomas; track i.codigo) {
        <button
          type="button"
          class="bandera"
          [class.activo]="i18n.idioma() === i.codigo"
          [attr.aria-pressed]="i18n.idioma() === i.codigo"
          [attr.aria-label]="i.nombre"
          [attr.title]="i.nombre"
          (click)="elegir(i.codigo)"
        >
          <svg viewBox="0 0 24 16" aria-hidden="true" focusable="false">
            @switch (i.codigo) {
              @case ('es') {
                <!-- Peru: bandas verticales rojo, blanco, rojo. -->
                <rect width="24" height="16" fill="#ffffff" />
                <path fill="#d91023" d="M0 0h8v16H0zM16 0h8v16h-8z" />
              }
              @case ('en') {
                <!-- Estados Unidos: siete franjas y el canton. Trece franjas a
                     este tamano se convierten en un gris rayado. -->
                <rect width="24" height="16" fill="#ffffff" />
                <path
                  fill="#b22234"
                  d="M0 0h24v2.29H0zM0 4.57h24v2.29H0zM0 9.14h24v2.29H0zM0 13.71h24V16H0z"
                />
                <rect width="10" height="9.14" fill="#3c3b6e" />
                <g fill="#ffffff">
                  <circle cx="2.2" cy="2.2" r="0.55" />
                  <circle cx="5" cy="2.2" r="0.55" />
                  <circle cx="7.8" cy="2.2" r="0.55" />
                  <circle cx="3.6" cy="4.6" r="0.55" />
                  <circle cx="6.4" cy="4.6" r="0.55" />
                  <circle cx="2.2" cy="7" r="0.55" />
                  <circle cx="5" cy="7" r="0.55" />
                  <circle cx="7.8" cy="7" r="0.55" />
                </g>
              }
              @case ('pt') {
                <!-- Brasil: verde, rombo, circulo y la banda blanca. -->
                <rect width="24" height="16" fill="#009b3a" />
                <path fill="#fedf00" d="M12 2 22 8l-10 6L2 8z" />
                <circle cx="12" cy="8" r="3.2" fill="#002776" />
                <path
                  fill="#ffffff"
                  d="M9.5 7.2c1.6-1.1 3.6-1.2 5.2-.2l-.35.62c-1.4-.9-3.2-.8-4.6.2z"
                />
              }
            }
          </svg>
        </button>
      }
    </div>
  `,
  styles: `
    /* Abajo a la derecha, la esquina que esta aplicacion ya usa para lo que
       flota. Los avisos se apilan por encima (ver la pila en styles.scss), asi
       que nunca se tapan entre si. */
    .barra {
      position: fixed;
      right: var(--e4);
      bottom: var(--e4);
      z-index: 90;
      display: flex;
      align-items: center;
      gap: var(--e1);
      padding: var(--e1);
      background: var(--superficie);
      border: 1px solid var(--linea);
      border-radius: 999px;
      box-shadow: var(--sombra);
    }

    /* Se anulan a mano los estilos globales del boton, que por defecto pintan
       el rojo relleno de la accion principal. Elegir idioma no lo es. */
    .bandera {
      display: grid;
      place-items: center;
      width: var(--toque);
      min-height: var(--toque);
      padding: 0;
      background: transparent;
      border: none;
      border-radius: 999px;
      cursor: pointer;
    }

    /* La que no esta en uso se atenua en vez de esconderse: las tres tienen que
       verse para poder elegir, pero solo una es la actual. */
    .bandera svg {
      width: 24px;
      height: 16px;
      border-radius: 2px;
      opacity: 0.45;
      outline: 1px solid var(--linea);
      outline-offset: -1px;
      transition: opacity 0.12s ease;
    }

    .bandera:hover svg {
      opacity: 0.85;
    }

    /* El idioma en uso se marca con un anillo y no solo con el color de la
       bandera: en una pantalla de cocina con reflejos, la diferencia de
       opacidad sola no se distingue. */
    .bandera.activo {
      background: var(--acento-suave);
      box-shadow: inset 0 0 0 2px var(--acento);
    }

    .bandera.activo svg {
      opacity: 1;
    }

    .bandera:focus-visible {
      outline: 2px solid var(--acento);
      outline-offset: 2px;
    }

    @media (prefers-reduced-motion: reduce) {
      .bandera svg {
        transition: none;
      }
    }
  `,
})
export class BarraIdiomas {
  protected readonly i18n = inject(I18nService);

  protected elegir(codigo: Idioma): void {
    // El diccionario se descarga al elegirlo; no hay nada que esperar aqui, la
    // pantalla se repinta cuando llega.
    void this.i18n.cambiar(codigo);
  }
}
