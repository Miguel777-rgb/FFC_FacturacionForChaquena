import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import type * as Leaflet from 'leaflet';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { TemaService } from '../nucleo/tema/tema.service';

/** Un punto del mapa, en grados decimales. */
export interface Punto {
  latitud: number;
  longitud: number;
}

/**
 * Las teselas oficiales de OpenStreetMap: gratuitas y sin clave, las mismas que
 * usa el mapa de OpenRouteService, que resuelve las direcciones pero no sirve
 * fondos de mapa. Su politica pide atribucion visible y un uso moderado, que un
 * local cumple de sobra. CARTO, que las daba en claro y oscuro, empezo a pedir
 * clave; el oscuro se consigue ahora con un filtro sobre las mismas teselas.
 */
const TESELAS = 'https://tile.openstreetmap.org/{z}/{x}/{y}.png';
const ATRIBUCION =
  '&copy; <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noopener">OpenStreetMap</a>';

/** El pin del punto elegido: la gota en el borgona de la marca, con su centro blanco. */
const PIN = `<svg width="30" height="40" viewBox="0 0 30 40" aria-hidden="true" focusable="false">
  <path d="M15 1C7.3 1 1 7.1 1 14.8 1 25.3 15 39 15 39s14-13.7 14-24.2C29 7.1 22.7 1 15 1z"
    style="fill: var(--acento-relleno); stroke: #fff; stroke-width: 2" />
  <circle cx="15" cy="14.5" r="5" fill="#fff" />
</svg>`;

let cargaLeaflet: Promise<typeof Leaflet> | null = null;

/**
 * Leaflet y su hoja de estilos se piden la primera vez que se pinta un mapa: son
 * unos 45 kB que el tablero, la cocina o la caja no tienen por que descargar. La
 * hoja se copia a `leaflet/` en la compilacion (angular.json) y se enlaza aqui,
 * en vez de ir en los estilos del componente, donde reventaria su presupuesto.
 */
function cargarLeaflet(): Promise<typeof Leaflet> {
  cargaLeaflet ??= (async () => {
    if (!document.querySelector('link[data-leaflet]')) {
      const hoja = document.createElement('link');
      hoja.rel = 'stylesheet';
      hoja.href = 'leaflet/leaflet.css';
      hoja.dataset['leaflet'] = '';
      document.head.appendChild(hoja);
    }
    const modulo = await import('leaflet');
    // Leaflet 1.x es CommonJS: segun el empaquetador llega en `default` o suelto.
    return (modulo as unknown as { default?: typeof Leaflet }).default ?? modulo;
  })();
  return cargaLeaflet;
}

/**
 * Un mapa con, como mucho, dos marcas: el punto elegido (el pin) y el local
 * (un circulo). Es solo el dibujo: no sabe de direcciones ni de ORS. Con
 * `editable`, tocar el mapa o soltar el pin arrastrado avisa con `elegir`, y
 * quien lo usa decide que hacer con el punto.
 *
 * El mapa no es la unica via: todo lo que se hace tocandolo se puede hacer
 * escribiendo la direccion (con sugerencias) en el campo que lo acompana, que
 * es lo que usa quien navega con teclado o lector de pantalla.
 */
@Component({
  selector: 'app-mapa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { '[class.oscuro]': 'tema.esOscuro()' },
  template: `<div #lienzo class="lienzo" role="region" [attr.aria-label]="etiqueta()"></div>`,
  styles: `
    :host {
      display: block;
      height: var(--mapa-alto, 240px);
      overflow: hidden;
      background: var(--hundido);
      border: 1px solid var(--linea);
      border-radius: var(--radio);
      /* Los paneles de Leaflet usan z-index de hasta 1000: se quedan dentro. */
      isolation: isolate;
    }

    .lienzo {
      width: 100%;
      height: 100%;
      background: var(--hundido);
    }

    /* En el tema oscuro las teselas se invierten y se les devuelve el tono: el
       agua sigue azul y los parques verdes, pero las calles quedan claras sobre
       fondo oscuro. Solo las teselas: el pin y el local conservan su color. */
    :host(.oscuro) ::ng-deep .leaflet-tile-pane {
      filter: invert(1) hue-rotate(180deg) brightness(0.9) contrast(0.9);
    }

    /* Lo que dibuja Leaflet no lleva el atributo de encapsulado: se alcanza
       desde el anfitrion. */
    :host ::ng-deep .pin-mapa {
      filter: drop-shadow(0 1px 1px rgb(0 0 0 / 0.35));
    }

    /* El tamano lo pone Leaflet en linea, desde \`iconSize\`. */
    :host ::ng-deep .origen-mapa {
      background: var(--tinta);
      border: 3px solid var(--superficie);
      border-radius: 50%;
      box-shadow: 0 0 0 1px var(--tinta);
    }

    :host ::ng-deep .leaflet-bar a {
      color: var(--tinta);
      background: var(--superficie);
      border-color: var(--linea);
    }

    :host ::ng-deep .leaflet-bar a:hover {
      background: var(--hundido);
    }

    /* Con .leaflet-container delante: la hoja de Leaflet se enlaza despues y,
       a igual especificidad, su fondo blanco ganaria tambien en el oscuro. */
    :host ::ng-deep .leaflet-container .leaflet-control-attribution {
      color: var(--texto);
      background: color-mix(in srgb, var(--superficie) 85%, transparent);
    }

    :host ::ng-deep .leaflet-control-attribution a {
      color: var(--acento);
    }
  `,
})
export class Mapa {
  private readonly t = inject(I18nService).t;
  protected readonly tema = inject(TemaService);

  /** Donde se abre si no hay punto: el local, o el centro de Lima si aun no se marco. */
  readonly centro = input.required<Punto>();
  readonly punto = input<Punto | null>(null);
  /** El local, marcado aparte del punto elegido. */
  readonly origen = input<Punto | null>(null);
  readonly editable = input(false);
  readonly zoom = input(15);
  readonly etiqueta = input('');
  readonly elegir = output<Punto>();

  private readonly lienzo = viewChild.required<ElementRef<HTMLElement>>('lienzo');
  private readonly listo = signal(false);

  private L?: typeof Leaflet;
  private mapa?: Leaflet.Map;
  private pin?: Leaflet.Marker;
  private marcaOrigen?: Leaflet.Marker;
  private vivo = true;

  constructor() {
    const anfitrion = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
    let observador: ResizeObserver | undefined;

    afterNextRender(() => {
      void cargarLeaflet().then((L) => {
        if (!this.vivo) return;
        this.L = L;
        this.crear(L);
        // Dentro de un dialogo el mapa nace sin tamano: al abrirse cambia, y
        // Leaflet tiene que volver a medir o pinta teselas grises.
        if (typeof ResizeObserver === 'function') {
          observador = new ResizeObserver(() => this.mapa?.invalidateSize());
          observador.observe(anfitrion);
        }
        this.listo.set(true);
      });
    });

    inject(DestroyRef).onDestroy(() => {
      this.vivo = false;
      observador?.disconnect();
      this.mapa?.remove();
    });

    effect(() => {
      if (this.listo()) this.moverPin(this.punto());
    });
    effect(() => {
      if (this.listo()) this.moverOrigen(this.origen());
    });
    // Si el centro llega despues (el punto del local se lee aparte) y no hay
    // pin, el mapa se mueve alli.
    effect(() => {
      const centro = this.centro();
      if (this.listo() && !this.punto()) {
        this.mapa?.setView([centro.latitud, centro.longitud], this.zoom());
      }
    });
  }

  private crear(L: typeof Leaflet): void {
    const centro = this.punto() ?? this.centro();
    const mapa = L.map(this.lienzo().nativeElement, {
      zoomControl: false,
      center: [centro.latitud, centro.longitud],
      zoom: this.zoom(),
    });
    L.control
      .zoom({ zoomInTitle: this.t('mapa.acercar'), zoomOutTitle: this.t('mapa.alejar') })
      .addTo(mapa);
    // Sin la bandera que Leaflet 1.9 pone delante de su nombre: DESIGN.md no
    // admite emojis en la interfaz.
    mapa.attributionControl.setPrefix(
      '<a href="https://leafletjs.com" target="_blank" rel="noopener">Leaflet</a>',
    );

    L.tileLayer(TESELAS, { attribution: ATRIBUCION, maxZoom: 19 }).addTo(mapa);

    mapa.on('click', (e: Leaflet.LeafletMouseEvent) => {
      if (this.editable()) this.elegir.emit({ latitud: e.latlng.lat, longitud: e.latlng.lng });
    });
    this.mapa = mapa;
  }

  private moverPin(punto: Punto | null): void {
    if (!this.L || !this.mapa) return;
    if (!punto) {
      this.pin?.remove();
      this.pin = undefined;
      return;
    }

    const lugar = this.L.latLng(punto.latitud, punto.longitud);
    if (!this.pin) {
      this.pin = this.L.marker(lugar, {
        icon: this.L.divIcon({
          className: 'pin-mapa',
          html: PIN,
          iconSize: [30, 40],
          iconAnchor: [15, 39],
        }),
        draggable: this.editable(),
        keyboard: false,
        title: this.t('mapa.puntoElegido'),
      }).addTo(this.mapa);
      this.pin.on('dragend', () => {
        const donde = this.pin?.getLatLng();
        if (donde) this.elegir.emit({ latitud: donde.lat, longitud: donde.lng });
      });
    } else {
      this.pin.setLatLng(lugar);
    }

    // Un punto que llega de fuera (una sugerencia, el cliente elegido) puede
    // estar lejos: el mapa va a buscarlo. Uno tocado ya esta a la vista.
    if (!this.mapa.getBounds().contains(lugar)) {
      this.mapa.setView(lugar, Math.max(this.mapa.getZoom(), 16));
    }
  }

  private moverOrigen(origen: Punto | null): void {
    if (!this.L || !this.mapa) return;
    this.marcaOrigen?.remove();
    this.marcaOrigen = undefined;
    if (!origen) return;
    this.marcaOrigen = this.L.marker([origen.latitud, origen.longitud], {
      icon: this.L.divIcon({ className: 'origen-mapa', iconSize: [20, 20] }),
      keyboard: false,
      interactive: false,
      title: this.t('mapa.local'),
    }).addTo(this.mapa);
  }
}
