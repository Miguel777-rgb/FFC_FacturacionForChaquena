import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { SesionService } from '../nucleo/sesion/sesion.service';
import { LogoService } from '../nucleo/marca/logo.service';
import type { Rol } from '../nucleo/sesion/rol';
import { Icono } from './icono';
import type { NombreIcono } from './iconos';

interface Destino {
  ruta: string;
  etiqueta: string;
  icono: NombreIcono;
  roles: Rol[];
}

/** Las cinco superficies del plan. Mismo reparto de roles que las guardas. */
const DESTINOS: Destino[] = [
  { ruta: '/pos', etiqueta: 'Punto de venta', icono: 'pos', roles: ['MOZO', 'ADMIN'] },
  { ruta: '/kds', etiqueta: 'Cocina', icono: 'cocina', roles: ['COCINA', 'ADMIN'] },
  { ruta: '/caja', etiqueta: 'Caja', icono: 'caja', roles: ['CAJA', 'ADMIN'] },
  {
    ruta: '/despacho',
    etiqueta: 'Despacho',
    icono: 'despacho',
    roles: ['DELIVERY', 'MOZO', 'ADMIN'],
  },
  { ruta: '/trastienda', etiqueta: 'Trastienda', icono: 'trastienda', roles: ['ALMACEN', 'ADMIN'] },
];

const CLAVE_PLEGADO = 'chaquena.panel.plegado';

/**
 * Panel lateral con las superficies a las que este usuario puede entrar.
 *
 * Solo se listan los destinos que su rol permite: un mozo no ve el enlace al
 * arqueo de caja. Esconderlo es comodidad, no seguridad, pero evita que la
 * gente choque contra un 403 en mitad del servicio.
 *
 * Plegado deja una regleta de iconos en vez de desaparecer: en el KDS y en el
 * POS la pantalla es estrecha y cada pixel cuenta, pero perder la navegacion
 * entera obligaria a desplegar para cambiar de sitio. El estado se recuerda por
 * dispositivo, que es donde la decision tiene sentido: la pantalla de cocina
 * quiere estar siempre plegada y el escritorio de caja siempre abierto.
 */
@Component({
  selector: 'app-panel-lateral',
  imports: [RouterLink, RouterLinkActive, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside [class.plegado]="plegado()">
      <!-- Marca: el logo del local arriba a la izquierda. Mientras no se cargue
           ninguno, la ranura invita a hacerlo en vez de dejar un hueco. -->
      <div class="marca-local">
        <label class="ranura" [attr.title]="logo.logo() ? 'Cambiar el logo' : 'Cargar el logo'">
          @if (logo.logo(); as fuente) {
            <img [src]="fuente" alt="Logo del local" />
          } @else {
            <app-icono nombre="imagen" [tamano]="20" />
          }
          <input
            type="file"
            accept="image/webp,image/png,image/jpeg,image/svg+xml"
            (change)="elegirLogo($event)"
            [attr.aria-label]="logo.logo() ? 'Cambiar el logo del local' : 'Cargar el logo del local'"
          />
        </label>

        @if (!plegado()) {
          <div class="identidad">
            <span class="marca">Chaquena</span>
            <span class="modulo">Logistica</span>
          </div>

          @if (logo.logo()) {
            <button type="button" class="icono-solo" (click)="logo.quitar()" aria-label="Quitar el logo">
              <app-icono nombre="quitar" [tamano]="16" />
            </button>
          }
        }
      </div>

      @if (errorLogo(); as texto) {
        @if (!plegado()) {
          <p class="error-logo" role="alert">{{ texto }}</p>
        }
      }

      <nav [attr.aria-label]="'Superficies'">
        <ul>
          @for (d of visibles(); track d.ruta) {
            <li>
              <a
                [routerLink]="d.ruta"
                routerLinkActive="activo"
                [attr.title]="plegado() ? d.etiqueta : null"
              >
                <app-icono [nombre]="d.icono" />
                @if (!plegado()) {
                  <span>{{ d.etiqueta }}</span>
                }
              </a>
            </li>
          }
        </ul>
      </nav>

      <footer>
        @if (!plegado()) {
          <div class="quien">
            <span class="nombre">{{ sesion.nombre() }}</span>
            <span class="roles">{{ sesion.roles().join(' · ') || 'sin roles' }}</span>
          </div>
        }

        <button
          type="button"
          class="icono-solo"
          (click)="salir()"
          [attr.title]="plegado() ? 'Salir' : null"
          aria-label="Cerrar sesion"
        >
          <app-icono nombre="salir" />
        </button>
      </footer>

      <button
        type="button"
        class="plegar"
        (click)="alternar()"
        [attr.aria-expanded]="!plegado()"
        [attr.aria-label]="plegado() ? 'Desplegar el panel' : 'Plegar el panel'"
      >
        <app-icono nombre="panel" [tamano]="18" />
        @if (!plegado()) {
          <span>Plegar</span>
        }
      </button>
    </aside>
  `,
  styles: `
    aside {
      display: flex;
      flex-direction: column;
      gap: var(--e2);
      width: 15rem;
      height: 100dvh;
      padding: var(--e3);
      background: var(--superficie);
      border-right: 1px solid var(--linea);
      /* El rojo entra por un filete de un pixel, no por un fondo: el panel
         queda a la vista todo el turno y un lateral rojo cansaria. */
      box-shadow: inset 2px 0 0 -1px var(--acento);
      transition: width 0.15s ease;
      overflow: hidden;
    }

    aside.plegado {
      width: calc(var(--toque) + var(--e3) * 2);
    }

    /* --- marca ------------------------------------------------------------ */
    .marca-local {
      display: flex;
      align-items: center;
      gap: var(--e2);
      padding-bottom: var(--e3);
      border-bottom: 1px solid var(--linea);
    }

    /* La ranura es el propio input de archivo: la etiqueta envuelve un input
       escondido, asi que se pulsa la imagen para cambiarla. */
    .ranura {
      position: relative;
      display: grid;
      place-items: center;
      flex: none;
      width: var(--toque);
      height: var(--toque);
      border: 1px dashed var(--linea);
      border-radius: var(--radio-chico);
      color: var(--tenue);
      cursor: pointer;
      overflow: hidden;
    }

    .ranura:hover {
      border-color: var(--acento);
      color: var(--acento);
    }

    .ranura:focus-within {
      outline: 2px solid var(--acento);
      outline-offset: 2px;
    }

    .ranura img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    /* Se oculta sin display:none para que siga recibiendo el foco del teclado. */
    .ranura input {
      position: absolute;
      inset: 0;
      opacity: 0;
      cursor: pointer;
      min-height: 0;
      padding: 0;
    }

    .identidad {
      display: flex;
      flex-direction: column;
      flex: 1;
      min-width: 0;
    }

    .modulo {
      font-size: 0.95rem;
      font-weight: 600;
      line-height: 1.1;
    }

    .error-logo {
      margin: 0;
      padding: var(--e2);
      font-size: 0.8rem;
      color: var(--critico);
      background: var(--critico-suave);
      border: 1px solid var(--critico);
      border-radius: var(--radio-chico);
    }

    /* --- navegacion ------------------------------------------------------- */
    nav {
      flex: 1;
      overflow-y: auto;
    }

    ul {
      display: flex;
      flex-direction: column;
      gap: 2px;
      margin: 0;
      padding: 0;
      list-style: none;
    }

    a {
      display: flex;
      align-items: center;
      gap: var(--e3);
      min-height: var(--toque);
      padding: 0 var(--e3);
      border-radius: var(--radio-chico);
      color: var(--tinta);
      text-decoration: none;
      white-space: nowrap;
    }

    a:hover {
      background: var(--hundido);
    }

    /* El destino actual se marca con peso y un filete, no solo con color: en la
       cocina hay pantallas donde el tinte claro casi no se distingue con
       reflejos. */
    a.activo {
      color: var(--acento);
      background: var(--acento-suave);
      font-weight: 600;
      box-shadow: inset 2px 0 0 var(--acento);
    }

    /* --- pie -------------------------------------------------------------- */
    footer {
      display: flex;
      align-items: center;
      gap: var(--e2);
      padding-top: var(--e3);
      border-top: 1px solid var(--linea);
    }

    .quien {
      display: flex;
      flex-direction: column;
      flex: 1;
      min-width: 0;
    }

    .nombre {
      font-size: 0.85rem;
      font-weight: 500;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .roles {
      font-family: var(--f-mono);
      font-size: 0.65rem;
      letter-spacing: 0.06em;
      color: var(--tenue);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* Ni principal ni destructivo: se anulan a mano los estilos globales del
       elemento button, que por defecto pintan el rojo relleno de la accion
       principal. */
    .icono-solo,
    .plegar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--e2);
      min-height: var(--toque);
      padding: 0 var(--e2);
      color: var(--tenue);
      background: transparent;
      border: none;
      border-radius: var(--radio-chico);
    }

    .icono-solo {
      flex: none;
      width: var(--toque);
      padding: 0;
    }

    .plegar {
      width: 100%;
      font-size: 0.8rem;
    }

    .icono-solo:hover,
    .plegar:hover {
      color: var(--tinta);
      background: var(--hundido);
    }

    /* En movil el panel se pliega solo: 15rem sobre una pantalla de 380px no
       deja sitio para la comanda. */
    @media (max-width: 40rem) {
      aside {
        width: calc(var(--toque) + var(--e3) * 2);
      }

      aside:not(.plegado) .identidad,
      aside:not(.plegado) .quien,
      aside:not(.plegado) a span,
      aside:not(.plegado) .plegar span,
      aside:not(.plegado) .error-logo {
        display: none;
      }
    }
  `,
})
export class PanelLateral {
  protected readonly sesion = inject(SesionService);
  protected readonly logo = inject(LogoService);
  private readonly router = inject(Router);

  protected readonly plegado = signal(this.leerPlegado());
  protected readonly errorLogo = signal<string | null>(null);

  protected readonly visibles = computed(() =>
    DESTINOS.filter((d) => this.sesion.tieneAlgunRol(d.roles)),
  );

  protected alternar(): void {
    this.plegado.update((v) => !v);
    try {
      localStorage.setItem(CLAVE_PLEGADO, String(this.plegado()));
    } catch {
      /* sin almacenamiento el panel simplemente no recuerda el estado */
    }
  }

  protected async elegirLogo(evento: Event): Promise<void> {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    if (!archivo) return;

    this.errorLogo.set(await this.logo.cargar(archivo));
    // Se limpia para que elegir el mismo archivo otra vez vuelva a disparar
    // el evento change.
    entrada.value = '';
  }

  protected salir(): void {
    this.sesion.cerrar();
    void this.router.navigateByUrl('/entrar');
  }

  private leerPlegado(): boolean {
    try {
      return localStorage.getItem(CLAVE_PLEGADO) === 'true';
    } catch {
      return false;
    }
  }
}
