import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { SesionService } from '../../nucleo/sesion/sesion.service';

/**
 * Marcador de posicion de las cinco superficies mientras se construyen.
 *
 * No es relleno: cada ruta le pasa por `data` que superficie es, en que fase
 * del plan entra y que endpoints va a consumir, asi que la pantalla dice la
 * verdad sobre lo que falta en vez de fingir que ya existe.
 *
 * Se borra cuando la superficie correspondiente esta hecha.
 */
@Component({
  selector: 'app-superficie-pendiente',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="pendiente">
      <header>
        <p class="fase">Fase {{ fase() }} del plan</p>
        <h1>{{ titulo() }}</h1>
        <p class="resumen">{{ resumen() }}</p>
      </header>

      <div class="bloque">
        <h2>Endpoints que consumira</h2>
        <ul class="endpoints">
          @for (e of endpoints(); track e) {
            <li>{{ e }}</li>
          }
        </ul>
      </div>

      <footer>
        Sesion de <strong>{{ sesion.nombre() }}</strong> · roles
        <strong>{{ sesion.roles().join(', ') || 'ninguno' }}</strong>
      </footer>
    </section>
  `,
  styles: `
    .pendiente {
      display: flex;
      flex-direction: column;
      gap: var(--e5);
      padding: var(--e6);
      max-width: 60rem;
      margin-inline: auto;
    }

    header {
      display: flex;
      flex-direction: column;
      gap: var(--e2);
    }

    .fase {
      font-family: var(--f-mono);
      font-size: 0.7rem;
      letter-spacing: 0.12em;
      text-transform: uppercase;
      color: var(--acento);
      margin: 0;
    }

    h1 {
      margin: 0;
      font-size: 2rem;
    }

    .resumen {
      margin: 0;
      color: var(--tenue);
      max-width: 60ch;
    }

    .bloque {
      padding: var(--e4);
      background: var(--superficie);
      border: 1px solid var(--linea);
      border-radius: var(--radio);
    }

    h2 {
      margin: 0 0 var(--e3);
      font-size: 0.75rem;
      font-family: var(--f-mono);
      letter-spacing: 0.1em;
      text-transform: uppercase;
      color: var(--tenue);
    }

    .endpoints {
      margin: 0;
      padding: 0;
      list-style: none;
      display: flex;
      flex-direction: column;
      gap: var(--e1);
      font-family: var(--f-mono);
      font-size: 0.85rem;
      color: var(--tenue);
      overflow-x: auto;
    }

    footer {
      font-size: 0.85rem;
      color: var(--tenue);
      border-top: 1px solid var(--linea);
      padding-top: var(--e3);
    }
  `,
})
export class SuperficiePendientePage {
  /** Llegan desde `data` en las rutas, con `withComponentInputBinding()`. */
  readonly titulo = input.required<string>();
  readonly resumen = input.required<string>();
  readonly fase = input.required<number>();
  readonly endpoints = input.required<string[]>();

  protected readonly sesion = inject(SesionService);
}
