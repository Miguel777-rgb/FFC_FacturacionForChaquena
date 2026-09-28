import { DecimalPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  afterRenderEffect,
  computed,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';

import type { PlatilloDisponibleDto } from '../../api';
import { Icono } from '../../disenio/icono';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { urlDeArchivo } from '../../nucleo/marca/archivos';

interface Seccion {
  id: string;
  nombre: string;
  platos: PlatilloDisponibleDto[];
}

/** Para buscar «aji» y encontrar «Ají de gallina»: sin tildes ni mayusculas. */
function normalizar(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLocaleLowerCase()
    .trim();
}

/**
 * La carta del POS, al estilo de las apps de reparto: un buscador y una fila de
 * secciones fijos arriba, y debajo un plato por fila con su foto a la derecha.
 *
 * Reemplaza a la rejilla de tarjetas que obligaba a bajar por toda la carta:
 * tocar una seccion salta a ella, y la fila marca en cual se esta mientras se
 * desplaza. Un plato sin foto no deja hueco: su fila simplemente es mas baja.
 *
 * No agrega nada por si misma: avisa con `elegir` y quien la usa abre la hoja
 * del plato. Por eso sirve igual en la comanda nueva y al agregar un plato a
 * una comanda ya enviada. `[inicio]` proyecta algo al lado del buscador (el
 * destino de la comanda). La altura de lo que haya fijo encima se le pasa con
 * la variable CSS `--carta-tope`.
 */
@Component({
  selector: 'app-carta-pos',
  imports: [DecimalPipe, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="herramientas" #herramientas>
      <div class="fila-busqueda">
        <ng-content select="[inicio]" />
        <label class="buscar">
          <app-icono nombre="buscar" [tamano]="18" />
          <input
            type="search"
            [attr.placeholder]="t('pos.buscarPlatillo')"
            [attr.aria-label]="t('pos.buscarPlatillo')"
            [value]="busqueda()"
            (input)="busqueda.set($any($event.target).value)"
          />
        </label>
      </div>

      @if (secciones().length > 1) {
        <nav class="categorias" #categorias [attr.aria-label]="t('pos.categorias')">
          @for (s of secciones(); track s.id) {
            <button
              type="button"
              class="categoria"
              [class.activa]="s.id === activa()"
              [attr.aria-current]="s.id === activa() ? 'true' : null"
              (click)="irA(s.id)"
            >
              {{ s.nombre }}
            </button>
          }
        </nav>
      }
    </div>

    @for (s of secciones(); track s.id) {
      <section class="seccion" [attr.data-seccion]="s.id" [attr.aria-labelledby]="prefijo + s.id">
        <h3 [id]="prefijo + s.id">{{ s.nombre }}</h3>
        <ul class="platos">
          @for (p of s.platos; track p.id) {
            <li>
              <button
                type="button"
                class="plato"
                [class.agotado]="!p.disponible"
                [disabled]="!p.disponible"
                (click)="elegir.emit(p)"
              >
                <span class="texto">
                  <span class="nombre">{{ p.nombre }}</span>
                  @if (p.descripcion) {
                    <span class="descripcion">{{ p.descripcion }}</span>
                  }
                  @if (p.tiempoPreparacionMinutos || p.alergenos?.length) {
                    <span class="detalles">
                      @if (p.tiempoPreparacionMinutos) {
                        <span>{{ t('pos.minutos', { n: p.tiempoPreparacionMinutos }) }}</span>
                      }
                      @if (p.alergenos?.length) {
                        <span>{{
                          t('pos.contiene', { alergenos: p.alergenos?.join(', ') ?? '' })
                        }}</span>
                      }
                    </span>
                  }
                  <span class="precio cifra"
                    >S/&nbsp;{{ p.precioVentaBase | number: '1.2-2' }}</span
                  >
                  @if (!p.disponible) {
                    <!-- El plato agotado se ve y dice que le falta, en vez de
                         desaparecer y dejar al mozo buscandolo. -->
                    <span class="falta">
                      {{ t('pos.agotado', { insumos: p.insumosFaltantes?.join(', ') ?? '' }) }}
                    </span>
                  } @else if (
                    p.porcionesPosibles !== null &&
                    p.porcionesPosibles !== undefined &&
                    p.porcionesPosibles <= 5
                  ) {
                    <span class="quedan">{{ t('pos.quedan', { n: p.porcionesPosibles }) }}</span>
                  }
                </span>

                <span class="lado" [class.con-foto]="!!urlDeArchivo(p.fotoId)">
                  @if (urlDeArchivo(p.fotoId); as foto) {
                    <img class="foto" [src]="foto" alt="" loading="lazy" />
                  }
                  @if (enComanda()[p.id ?? '']; as n) {
                    <span class="en-comanda cifra">
                      <span aria-hidden="true">{{ n }}</span>
                      <span class="visualmente-oculto">{{ t('pos.enComanda', { n }) }}</span>
                    </span>
                  }
                  @if (p.disponible) {
                    <span class="mas" aria-hidden="true">
                      <app-icono nombre="mas" [tamano]="18" [grosor]="2" />
                    </span>
                  }
                </span>
              </button>
            </li>
          }
        </ul>
      </section>
    } @empty {
      <p class="vacio">
        @if (cargando()) {
          {{ t('pos.cargandoCarta') }}
        } @else if (busqueda().trim()) {
          {{ t('pos.sinCoincidenciasCarta', { q: busqueda().trim() }) }}
        } @else {
          {{ t('pos.sinCarta') }}
        }
      </p>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    /* Buscador y secciones quedan fijos al desplazar: son la forma de moverse
       por la carta sin bajar hasta el fondo. */
    .herramientas {
      position: sticky;
      top: var(--carta-tope, 0px);
      z-index: 2;
      display: flex;
      flex-direction: column;
      gap: var(--e2);
      padding: var(--e2) 0;
      background: var(--carta-fondo, var(--fondo));
      border-bottom: 1px solid var(--linea);
    }

    .fila-busqueda {
      display: flex;
      align-items: center;
      gap: var(--e2);
    }

    .buscar {
      position: relative;
      flex: 1 1 10rem;
      min-width: 0;

      app-icono {
        position: absolute;
        top: 50%;
        left: var(--e3);
        color: var(--texto);
        transform: translateY(-50%);
        pointer-events: none;
      }

      input {
        padding-left: calc(var(--e3) + 18px + var(--e2));
      }
    }

    /* Se desplazan en horizontal si no caben: dos filas de secciones taparian
       media pantalla del celular. */
    .categorias {
      position: relative;
      display: flex;
      gap: var(--e2);
      overflow-x: auto;
      scrollbar-width: none;
    }

    .categoria {
      flex: none;
      min-height: var(--control-chico);
      padding: 0 var(--e3);
      font-weight: 500;
      color: var(--texto);
      background: var(--superficie);
      border-color: var(--linea-fuerte);
      border-radius: 999px;
    }

    .categoria:hover:not(:disabled),
    .categoria:active:not(:disabled) {
      color: var(--tinta);
      background: var(--hundido);
    }

    .categoria.activa,
    .categoria.activa:hover:not(:disabled) {
      font-weight: 600;
      color: var(--acento);
      background: var(--acento-suave);
      border-color: var(--acento);
    }

    .seccion {
      padding-top: var(--e4);
    }

    h3 {
      margin: 0 0 var(--e2);
      font-size: var(--t-h4);
    }

    .platos {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(min(100%, 20rem), 1fr));
      gap: var(--e2);
      margin: 0;
      padding: 0;
      list-style: none;
    }

    .plato {
      align-items: stretch;
      justify-content: space-between;
      gap: var(--e3);
      width: 100%;
      height: 100%;
      padding: var(--e3);
      font-weight: 400;
      text-align: left;
      color: var(--tinta);
      background: var(--superficie);
      border-color: var(--linea);
    }

    /* El hover solo cambia el fondo; el texto secundario va en \`--texto\`, que
       sobre \`--hundido\` sigue pasando AA (el tenue no). */
    .plato:hover:not(:disabled),
    .plato:active:not(:disabled) {
      background: var(--hundido);
      border-color: var(--linea-fuerte);
    }

    .texto {
      display: flex;
      flex-direction: column;
      gap: 2px;
      flex: 1;
      min-width: 0;
    }

    .nombre {
      font-weight: 600;
      line-height: 1.3;
    }

    .descripcion {
      display: -webkit-box;
      overflow: hidden;
      font-size: var(--t-chico);
      color: var(--texto);
      -webkit-line-clamp: 2;
      -webkit-box-orient: vertical;
    }

    .detalles {
      font-size: var(--t-chico);
      color: var(--texto);

      > span + span::before {
        content: ' · ';
      }
    }

    .precio {
      margin-top: auto;
      padding-top: var(--e1);
      font-weight: 600;
    }

    .falta,
    .quedan {
      font-size: var(--t-chico);
    }

    .falta {
      color: var(--critico);
    }

    .quedan {
      color: var(--aviso);
    }

    .lado {
      position: relative;
      display: flex;
      flex: none;
      align-items: center;
      gap: var(--e2);
    }

    .lado.con-foto {
      align-items: flex-start;
    }

    .foto {
      width: 5.5rem;
      height: 5.5rem;
      object-fit: cover;
      background: var(--hundido);
      border-radius: var(--radio-chico);
    }

    /* El «+» dice que tocar agrega; es decorativo porque el boton es la fila. */
    .mas {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: 32px;
      height: 32px;
      color: var(--acento);
      background: var(--superficie);
      border: 1px solid var(--linea-fuerte);
      border-radius: 50%;
    }

    .con-foto .mas {
      position: absolute;
      right: calc(var(--e1) * -1);
      bottom: calc(var(--e1) * -1);
      box-shadow: var(--sombra);
    }

    /* Cuantos hay ya en la comanda: la respuesta visible a haber tocado. */
    .en-comanda {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-width: 24px;
      height: 24px;
      padding: 0 6px;
      font-size: var(--t-chico);
      font-weight: 700;
      color: var(--sobre-relleno);
      background: var(--acento-relleno);
      border-radius: 999px;
    }

    .con-foto .en-comanda {
      position: absolute;
      top: calc(var(--e1) * -1);
      right: calc(var(--e1) * -1);
    }

    /* Agotado se lee apagado pero legible, sobre la superficie y no sobre el
       gris hundido: ahi el tenue baja de AA. */
    .plato:disabled {
      color: var(--tenue);
      background: var(--superficie);
      border-color: var(--linea);

      .descripcion,
      .detalles {
        color: var(--tenue);
      }

      .foto {
        opacity: 0.5;
        filter: grayscale(1);
      }
    }
  `,
})
export class CartaPos {
  protected readonly t = inject(I18nService).t;
  protected readonly urlDeArchivo = urlDeArchivo;
  private readonly anfitrion = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly carta = input<PlatilloDisponibleDto[]>([]);
  /** Unidades de cada platillo que ya estan en la comanda, por id. */
  readonly enComanda = input<Record<string, number>>({});
  readonly cargando = input(false);
  readonly elegir = output<PlatilloDisponibleDto>();

  protected readonly busqueda = signal('');
  protected readonly activa = signal<string | null>(null);
  /** Dos cartas pueden convivir (la del POS y la de agregar a una comanda enviada). */
  protected readonly prefijo = `carta-${Math.random().toString(36).slice(2, 8)}-`;

  private readonly herramientas = viewChild.required<ElementRef<HTMLElement>>('herramientas');
  private readonly categorias = viewChild<ElementRef<HTMLElement>>('categorias');

  /** La seccion a la que se salto con un toque, hasta que salga de la vista. */
  private objetivo: string | null = null;
  private esperandoCuadro = false;

  /** Las secciones en el orden en que el servidor manda la carta, ya filtradas. */
  protected readonly secciones = computed<Seccion[]>(() => {
    const q = normalizar(this.busqueda());
    const mapa = new Map<string, Seccion>();
    for (const p of this.carta()) {
      if (q && !normalizar(`${p.nombre ?? ''} ${p.categoriaNombre ?? ''}`).includes(q)) continue;
      const id = String(p.categoriaId ?? p.categoriaNombre ?? 'sin');
      let seccion = mapa.get(id);
      if (!seccion) {
        seccion = { id, nombre: p.categoriaNombre || this.t('pos.sinCategoria'), platos: [] };
        mapa.set(id, seccion);
      }
      seccion.platos.push(p);
    }
    return [...mapa.values()];
  });

  constructor() {
    // Se escucha en captura: asi llega tanto el desplazamiento de la pagina como
    // el del dialogo, cuando la carta vive dentro de uno.
    const alDesplazar = (): void => {
      if (this.esperandoCuadro) return;
      this.esperandoCuadro = true;
      requestAnimationFrame(() => {
        this.esperandoCuadro = false;
        this.marcarSeccionVisible();
      });
    };
    afterNextRender(() =>
      document.addEventListener('scroll', alDesplazar, { capture: true, passive: true }),
    );
    inject(DestroyRef).onDestroy(() => document.removeEventListener('scroll', alDesplazar, true));

    // Al filtrar o al llegar la carta cambia que seccion queda arriba.
    afterRenderEffect(() => {
      this.secciones();
      this.marcarSeccionVisible();
    });

    // La seccion marcada se centra en su fila, que puede estar desplazada.
    afterRenderEffect(() => {
      this.activa();
      const fila = this.categorias()?.nativeElement;
      const chip = fila?.querySelector<HTMLElement>('.activa');
      if (!fila || !chip || typeof fila.scrollTo !== 'function') return;
      fila.scrollTo({
        left: Math.max(chip.offsetLeft - (fila.clientWidth - chip.offsetWidth) / 2, 0),
      });
    });
  }

  /**
   * Salta a la seccion. Sin animacion a proposito: es un salto que el mozo pidio,
   * y un desplazamiento suave de medio segundo es tiempo que espera con el
   * comensal delante.
   */
  protected irA(id: string): void {
    const seccion = this.seccion(id);
    if (!seccion) return;
    const herramientas = this.herramientas().nativeElement;
    const tope = parseFloat(getComputedStyle(herramientas).top) || 0;
    seccion.style.scrollMarginTop = `${tope + herramientas.offsetHeight}px`;
    this.objetivo = id;
    this.activa.set(id);
    seccion.scrollIntoView({ block: 'start' });
  }

  private seccion(id: string): HTMLElement | null {
    return this.anfitrion.nativeElement.querySelector<HTMLElement>(
      `[data-seccion="${CSS.escape(id)}"]`,
    );
  }

  /**
   * Marca la ultima seccion cuyo titulo ya paso bajo la barra fija. La ultima
   * de la carta casi nunca llega arriba —no queda pagina para subirla—, asi que
   * la que se eligio con un toque se respeta mientras siga a la vista.
   */
  private marcarSeccionVisible(): void {
    const secciones = Array.from(
      this.anfitrion.nativeElement.querySelectorAll<HTMLElement>('section.seccion'),
    );
    if (secciones.length === 0) {
      this.activa.set(null);
      return;
    }
    const limite = this.herramientas().nativeElement.getBoundingClientRect().bottom + 1;

    if (this.objetivo) {
      const caja = this.seccion(this.objetivo)?.getBoundingClientRect();
      if (caja && caja.top < window.innerHeight && caja.bottom > limite) {
        this.activa.set(this.objetivo);
        return;
      }
      this.objetivo = null;
    }

    let actual = secciones[0];
    for (const s of secciones) {
      if (s.getBoundingClientRect().top <= limite) actual = s;
      else break;
    }
    this.activa.set(actual.dataset['seccion'] ?? null);
  }
}
