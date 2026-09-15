import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { SesionService } from '../nucleo/sesion/sesion.service';
import { LogoService } from '../nucleo/marca/logo.service';
import { I18nService } from '../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../nucleo/i18n/traducciones/es';
import type { Rol } from '../nucleo/sesion/rol';
import { BarraIdiomas } from './barra-idiomas';
import { Icono } from './icono';
import { SelectorTema } from './selector-tema';
import type { NombreIcono } from './iconos';

type Grupo = 'operacion' | 'gestion';

interface Destino {
  ruta: string;
  /** Clave de traduccion, no el rotulo: el panel cambia de idioma en el sitio. */
  etiqueta: ClaveI18n;
  icono: NombreIcono;
  grupo: Grupo;
  roles: Rol[];
}

/**
 * Los destinos, en dos grupos: la operacion, en el orden en que pasa una
 * comanda, y la gestion del local.
 *
 * Mismo reparto de roles que las guardas de `app.routes.ts`. El tablero y
 * Ventas y reportes admiten tambien a caja porque sus endpoints lo admiten:
 * quien cuadra el dinero tiene derecho a ver la venta del dia sin pedirsela a
 * nadie.
 */
const DESTINOS: Destino[] = [
  {
    ruta: '/tablero',
    etiqueta: 'panel.tablero',
    icono: 'tablero',
    grupo: 'operacion',
    roles: ['ADMIN', 'CAJA'],
  },
  {
    ruta: '/pos',
    etiqueta: 'panel.pos',
    icono: 'pos',
    grupo: 'operacion',
    roles: ['MOZO', 'ADMIN'],
  },
  {
    ruta: '/ordenes',
    etiqueta: 'panel.ordenes',
    icono: 'ordenes',
    grupo: 'operacion',
    roles: ['ADMIN', 'MOZO', 'CAJA'],
  },
  {
    ruta: '/mesas',
    etiqueta: 'panel.mesas',
    icono: 'mesas',
    grupo: 'operacion',
    roles: ['ADMIN', 'MOZO', 'CAJA'],
  },
  {
    ruta: '/kds',
    etiqueta: 'panel.kds',
    icono: 'cocina',
    grupo: 'operacion',
    roles: ['COCINA', 'ADMIN'],
  },
  {
    ruta: '/caja',
    etiqueta: 'panel.caja',
    icono: 'caja',
    grupo: 'operacion',
    roles: ['CAJA', 'ADMIN'],
  },
  {
    ruta: '/despacho',
    etiqueta: 'panel.despacho',
    icono: 'despacho',
    grupo: 'operacion',
    roles: ['DELIVERY', 'MOZO', 'ADMIN'],
  },
  {
    ruta: '/menu',
    etiqueta: 'panel.menu',
    icono: 'menu',
    grupo: 'gestion',
    roles: ['ALMACEN', 'ADMIN'],
  },
  {
    ruta: '/inventario',
    etiqueta: 'panel.inventario',
    icono: 'inventario',
    grupo: 'gestion',
    roles: ['ALMACEN', 'ADMIN'],
  },
  {
    ruta: '/reportes',
    etiqueta: 'panel.reportes',
    icono: 'reportes',
    grupo: 'gestion',
    roles: ['ADMIN', 'CAJA'],
  },
  {
    ruta: '/personal',
    etiqueta: 'panel.personal',
    icono: 'personal',
    grupo: 'gestion',
    roles: ['ADMIN'],
  },
  {
    ruta: '/clientes',
    etiqueta: 'panel.clientes',
    icono: 'clientes',
    grupo: 'gestion',
    roles: ['ADMIN', 'CAJA'],
  },
  {
    ruta: '/configuracion',
    etiqueta: 'panel.configuracion',
    icono: 'configuracion',
    grupo: 'gestion',
    roles: ['ADMIN'],
  },
];

const GRUPOS: ReadonlyArray<{ id: Grupo; etiqueta: ClaveI18n }> = [
  { id: 'operacion', etiqueta: 'panel.grupoOperacion' },
  { id: 'gestion', etiqueta: 'panel.grupoGestion' },
];

const CLAVE_PLEGADO = 'chaquena.panel.plegado';

let siguientePanel = 0;

/**
 * Panel lateral con las superficies a las que este usuario puede entrar.
 *
 * Solo se listan los destinos que su rol permite: un mozo no ve el enlace al
 * arqueo de caja. Esconderlo es comodidad, no seguridad, pero evita que la
 * gente choque contra un 403 en mitad del servicio.
 *
 * Tres formas segun la pantalla:
 *
 * - PC: abierto, y plegable a una regleta de iconos. El estado se recuerda por
 *   dispositivo.
 * - Entre 768 y 1023px: regleta siempre; 15rem no dejarian sitio al contenido.
 * - Celular: no se pinta aqui. La barra superior lo abre dentro de un cajon con
 *   `cajon`, y cada enlace avisa con `navego` para cerrarlo.
 *
 * El idioma, el tema y la salida viven en su pie: con sesion abierta nada flota
 * encima del contenido.
 */
@Component({
  selector: 'app-panel-lateral',
  imports: [RouterLink, RouterLinkActive, Icono, SelectorTema, BarraIdiomas],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside [class.plegado]="regleta()" [class.cajon]="cajon()">
      <!-- Marca: el logo del local arriba a la izquierda. Es el mismo en todas
           las pantallas y solo lo cambia el administrador: para el, la ranura
           invita a cargarlo; los demas ven el logo, o nada si no hay. -->
      <div class="marca-local">
        @if (logo.puedeCambiar()) {
          <label
            class="ranura"
            [attr.title]="t(logo.logo() ? 'panel.cambiarLogo' : 'panel.cargarLogo')"
          >
            @if (logo.logo(); as fuente) {
              <img [src]="fuente" [alt]="t('panel.cargarLogoAria')" />
            } @else {
              <app-icono nombre="imagen" [tamano]="20" />
            }
            <input
              type="file"
              accept="image/webp,image/png,image/jpeg"
              (change)="elegirLogo($event)"
              [attr.aria-label]="t(logo.logo() ? 'panel.cambiarLogoAria' : 'panel.cargarLogoAria')"
            />
          </label>
        } @else if (logo.logo(); as fuente) {
          <span class="ranura fija">
            <img [src]="fuente" alt="" />
          </span>
        }

        <div class="identidad">
          <span class="marca">Chaquena</span>
          <span class="modulo">{{ t('panel.modulo') }}</span>
        </div>

        @if (logo.logo() && logo.puedeCambiar()) {
          <button
            type="button"
            class="icono-solo quitar-logo"
            (click)="logo.quitar()"
            [attr.aria-label]="t('panel.quitarLogo')"
          >
            <app-icono nombre="quitar" [tamano]="16" />
          </button>
        }
      </div>

      @if (errorLogo(); as texto) {
        <p class="error-logo" role="alert">{{ texto }}</p>
      }

      <nav [attr.aria-label]="t('panel.superficies')">
        @for (g of grupos(); track g.id) {
          <div class="grupo">
            <p class="rotulo-grupo" [id]="idBase + '-' + g.id">{{ t(g.etiqueta) }}</p>
            <ul [attr.aria-labelledby]="idBase + '-' + g.id">
              @for (d of g.destinos; track d.ruta) {
                <li>
                  <a
                    [routerLink]="d.ruta"
                    routerLinkActive="activo"
                    [attr.aria-label]="t(d.etiqueta)"
                    [attr.title]="regleta() ? t(d.etiqueta) : null"
                    (click)="navego.emit()"
                  >
                    <app-icono [nombre]="d.icono" />
                    <span class="etiqueta">{{ t(d.etiqueta) }}</span>
                  </a>
                </li>
              }
            </ul>
          </div>
        }
      </nav>

      <footer>
        <!-- Quien soy lleva a Mi perfil: es donde se mira el propio nombre. -->
        <a
          class="quien"
          routerLink="/perfil"
          routerLinkActive="activo"
          [attr.title]="t('panel.perfil')"
          (click)="navego.emit()"
        >
          <span class="nombre">{{ sesion.nombre() }}</span>
          <span class="roles">{{ sesion.roles().join(' · ') || t('panel.sinRoles') }}</span>
        </a>

        <div class="controles">
          <app-barra-idiomas variante="integrada" [vertical]="regleta()" />
          <app-selector-tema />
          <button
            type="button"
            class="icono-solo"
            (click)="salir()"
            [attr.title]="t('panel.salir')"
            [attr.aria-label]="t('panel.cerrarSesion')"
          >
            <app-icono nombre="salir" />
          </button>
        </div>
      </footer>

      @if (!cajon() && !tableta()) {
        <button
          type="button"
          class="plegar"
          (click)="alternar()"
          [attr.aria-expanded]="!plegado()"
          [attr.aria-label]="t(plegado() ? 'panel.desplegarAria' : 'panel.plegarAria')"
        >
          <app-icono nombre="panel" [tamano]="18" />
          <span>{{ t('panel.plegar') }}</span>
        </button>
      }
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
      transition: width 0.15s ease;
      overflow: hidden;
    }

    aside.cajon {
      width: 100%;
      height: 100%;
      border-right: none;
    }

    /* --- marca ------------------------------------------------------------ */
    .marca-local {
      display: flex;
      align-items: center;
      gap: var(--e2);
      padding-bottom: var(--e3);
      border-bottom: 1px solid var(--linea);
    }

    /* En el cajon, el aspa de cerrar ocupa la esquina superior derecha. */
    aside.cajon .marca-local {
      padding-right: calc(var(--control) + var(--e2));
    }

    /* La ranura es el propio input de archivo: se pulsa la imagen para
       cambiarla. */
    .ranura {
      position: relative;
      display: grid;
      place-items: center;
      flex: none;
      width: var(--toque);
      height: var(--toque);
      border: 1px dashed var(--linea-fuerte);
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

    /* Quien no es administrador ve el logo, pero no es un boton. */
    .ranura.fija,
    .ranura.fija:hover {
      border-style: solid;
      border-color: var(--linea);
      cursor: default;
    }

    .ranura img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    /* Se oculta sin display:none para que siga recibiendo el foco. */
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
      font-size: var(--t-texto);
      font-weight: 600;
      line-height: 1.1;
      color: var(--tinta);
    }

    .error-logo {
      margin: 0;
      padding: var(--e2);
      font-size: var(--t-chico);
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

    .grupo + .grupo {
      margin-top: var(--e3);
    }

    .rotulo-grupo {
      margin: 0 0 var(--e1);
      padding: 0 var(--e3);
      font-size: var(--t-leyenda);
      font-weight: 600;
      color: var(--tenue);
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
      font-weight: 500;
      color: var(--texto);
      text-decoration: none;
      white-space: nowrap;
    }

    a:hover {
      color: var(--tinta);
      background: var(--hundido);
    }

    /* El destino actual se marca con peso y un filete, no solo con color. */
    a.activo {
      color: var(--acento);
      background: var(--acento-suave);
      font-weight: 600;
      box-shadow: inset 3px 0 0 var(--acento);
    }

    /* --- pie -------------------------------------------------------------- */
    footer {
      display: flex;
      flex-direction: column;
      gap: var(--e2);
      padding-top: var(--e3);
      border-top: 1px solid var(--linea);
    }

    /* Lleva a Mi perfil, pero no es un destino mas: sin icono ni filete. */
    a.quien {
      flex-direction: column;
      align-items: stretch;
      justify-content: center;
      gap: 0;
      min-width: 0;
      padding: var(--e1) var(--e2);
      white-space: normal;
    }

    a.quien.activo {
      box-shadow: none;
    }

    .nombre {
      font-size: var(--t-texto);
      font-weight: 600;
      color: var(--tinta);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .roles {
      font-size: var(--t-leyenda);
      color: var(--tenue);
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .controles {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--e1);
    }

    .controles app-barra-idiomas {
      margin-right: auto;
    }

    /* Ni principal ni peligro: se anulan a mano los estilos globales del boton. */
    .icono-solo,
    .plegar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      gap: var(--e2);
      min-height: var(--control-chico);
      padding: 0 var(--e2);
      color: var(--tenue);
      background: transparent;
      border: none;
      border-radius: var(--radio-chico);
    }

    .icono-solo {
      flex: none;
      width: var(--control-chico);
      padding: 0;
    }

    .plegar {
      width: 100%;
      font-size: var(--t-chico);
      font-weight: 500;
    }

    .icono-solo:hover:not(:disabled),
    .plegar:hover:not(:disabled) {
      color: var(--tinta);
      background: var(--hundido);
    }

    /* --- regleta ---------------------------------------------------------- */
    /* Plegado a mano en PC, o siempre entre 768 y 1023px (tableta). En la regleta solo
       quedan los iconos; el nombre de cada destino pasa a \`title\` y sigue en
       \`aria-label\`. */
    aside.plegado {
      width: calc(var(--toque) + var(--e3) * 2);
    }

    aside.plegado .identidad,
    aside.plegado .quitar-logo,
    aside.plegado .error-logo,
    aside.plegado .rotulo-grupo,
    aside.plegado .etiqueta,
    aside.plegado .quien,
    aside.plegado .plegar span {
      display: none;
    }

    aside.plegado a {
      justify-content: center;
      padding: 0;
    }

    aside.plegado .grupo + .grupo {
      padding-top: var(--e2);
      margin-top: var(--e2);
      border-top: 1px solid var(--linea);
    }

    aside.plegado .controles {
      flex-direction: column;
    }

    aside.plegado .controles app-barra-idiomas {
      margin-right: 0;
    }
  `,
})
export class PanelLateral {
  protected readonly sesion = inject(SesionService);
  protected readonly logo = inject(LogoService);
  protected readonly t = inject(I18nService).t;
  private readonly router = inject(Router);

  /** Dentro del cajon del celular: nunca plegado y sin boton de plegar. */
  readonly cajon = input(false);
  /** Se pulso un destino: quien abrio el cajon lo cierra. */
  readonly navego = output<void>();

  protected readonly idBase = `panel-${++siguientePanel}`;
  protected readonly plegado = signal(this.leerPlegado());
  /** Entre 768 y 1023px la regleta no se elige: 15rem no dejarian sitio. */
  protected readonly tableta = signal(false);
  protected readonly regleta = computed(() => !this.cajon() && (this.plegado() || this.tableta()));
  protected readonly errorLogo = signal<string | null>(null);

  protected readonly grupos = computed(() => {
    const visibles = DESTINOS.filter((d) => this.sesion.tieneAlgunRol(d.roles));
    return GRUPOS.map((g) => ({ ...g, destinos: visibles.filter((d) => d.grupo === g.id) })).filter(
      (g) => g.destinos.length > 0,
    );
  });

  constructor() {
    // jsdom no tiene matchMedia: en las pruebas el panel se comporta como en PC.
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;

    const consulta = window.matchMedia('(min-width: 48rem) and (max-width: 63.99rem)');
    const anotar = () => this.tableta.set(consulta.matches);
    anotar();
    consulta.addEventListener('change', anotar);
    inject(DestroyRef).onDestroy(() => consulta.removeEventListener('change', anotar));
  }

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
    this.navego.emit();
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
