import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  model,
  signal,
  untracked,
} from '@angular/core';
import { catchError, of } from 'rxjs';

import { MapasApi } from '../api/api/mapas.api';
import type { DireccionDto } from '../api/model/direccion-dto';
import type { RutaDto } from '../api/model/ruta-dto';
import { formatearDistancia } from '../nucleo/i18n/formatos';
import { I18nService } from '../nucleo/i18n/i18n.service';
import { UbicacionLocalService } from '../nucleo/mapa/ubicacion-local.service';
import { Icono } from './icono';
import { Mapa, type Punto } from './mapa';

/** Lo que se espera tras la ultima tecla antes de pedir sugerencias: una por palabra, no por letra. */
const PAUSA_SUGERENCIAS_MS = 350;
const MINIMO_PARA_SUGERIR = 3;

/**
 * Una direccion con su punto en el mapa, en las dos direcciones:
 *
 * - Tocar el mapa (o soltar el pin arrastrado) pone el punto y completa la
 *   direccion con la calle mas cercana.
 * - Escribir la direccion ofrece sugerencias cerca del local; elegir una pone el
 *   punto. Es tambien el camino de quien no usa el mapa: teclado y lector de
 *   pantalla llegan a lo mismo sin tocarlo.
 *
 * La direccion completada es un punto de partida, no la ultima palabra: el
 * campo sigue editable, y la referencia (piso, dpto, «porton verde») va en su
 * propio campo porque el mapa no la sabe.
 *
 * Si no hay geocodificador (sin clave de OpenRouteService, o sin red), el mapa
 * sigue marcando el punto y la direccion se escribe a mano. La pantalla lo dice.
 *
 * Todo el estado es del que lo usa, con `model`: la comanda, la ficha del
 * cliente o los datos del local.
 */
@Component({
  selector: 'app-selector-ubicacion',
  imports: [Icono, Mapa],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="ubicacion">
      <div class="campo">
        <label [for]="id + '-direccion'">{{ etiqueta() }}</label>
        <div class="combo">
          <input
            [id]="id + '-direccion'"
            type="text"
            role="combobox"
            autocomplete="off"
            aria-autocomplete="list"
            [attr.aria-expanded]="abiertas()"
            [attr.aria-controls]="id + '-sugerencias'"
            [attr.aria-activedescendant]="activa() >= 0 ? id + '-s' + activa() : null"
            [attr.placeholder]="placeholder()"
            [value]="direccion()"
            (input)="escribir($any($event.target).value)"
            (keydown)="teclear($event)"
            (blur)="cerrarSugerencias()"
          />
          <ul
            class="sugerencias"
            role="listbox"
            [id]="id + '-sugerencias'"
            [attr.aria-label]="t('mapa.sugerencias')"
            [hidden]="!abiertas()"
          >
            @for (s of sugerencias(); track $index) {
              <!-- mousedown sin efecto: el campo no pierde el foco al tocar
                   la sugerencia, y la lista no se cierra antes del clic. -->
              <li
                role="option"
                [id]="id + '-s' + $index"
                [attr.aria-selected]="$index === activa()"
                [class.activa]="$index === activa()"
                (mousedown)="$event.preventDefault()"
                (click)="elegirSugerencia(s)"
              >
                <span class="principal">{{ s.direccion || s.etiqueta }}</span>
                @if (s.etiqueta && s.etiqueta !== s.direccion) {
                  <span class="secundaria">{{ s.etiqueta }}</span>
                }
              </li>
            }
          </ul>
        </div>
      </div>

      @if (conReferencia()) {
        <label class="campo">
          <span>{{ t('mapa.referencia') }}</span>
          <input
            type="text"
            [attr.placeholder]="t('mapa.referenciaEjemplo')"
            [value]="referencia()"
            (input)="referencia.set($any($event.target).value)"
          />
        </label>
      }

      <app-mapa
        [centro]="ubicacion.centro()"
        [punto]="punto()"
        [origen]="mostrarLocal() ? ubicacion.punto() : null"
        [editable]="true"
        [etiqueta]="t('mapa.region')"
        (elegir)="alElegirEnMapa($event)"
      />

      <p class="nota" [class.aviso]="avisoNota()" aria-live="polite">{{ nota() }}</p>

      @if (mostrarLocal() && ubicacion.punto() === null) {
        <p class="nota">{{ t('mapa.sinLocal') }}</p>
      }

      @if (textoRuta(); as ruta) {
        <p class="ruta">
          <app-icono nombre="despacho" [tamano]="18" />
          {{ ruta }}
        </p>
      }
    </div>
  `,
  styles: `
    .ubicacion {
      display: flex;
      flex-direction: column;
      gap: var(--e3);
    }

    .campo {
      display: flex;
      flex-direction: column;
      gap: var(--e1);

      label,
      > span {
        font-size: var(--t-chico);
        font-weight: 600;
        color: var(--texto);
      }
    }

    .combo {
      position: relative;
    }

    /* Debajo del campo y por encima del mapa, que se queda con sus capas dentro. */
    .sugerencias {
      position: absolute;
      top: calc(100% + 2px);
      right: 0;
      left: 0;
      z-index: 10;
      margin: 0;
      padding: var(--e1);
      list-style: none;
      background: var(--superficie);
      border: 1px solid var(--linea);
      border-radius: var(--radio);
      box-shadow: var(--sombra-elevada);

      li {
        display: flex;
        flex-direction: column;
        justify-content: center;
        gap: 2px;
        min-height: var(--control);
        padding: var(--e1) var(--e3);
        border-radius: var(--radio-chico);
        cursor: pointer;
      }

      li:hover,
      li.activa {
        background: var(--hundido);
      }

      /* Con el teclado, el mismo contorno que el foco del resto de la aplicacion. */
      li.activa {
        outline: 2px solid var(--acento);
        outline-offset: -2px;
      }

      .principal {
        color: var(--tinta);
      }

      .secundaria {
        font-size: var(--t-chico);
        color: var(--texto);
      }
    }

    app-mapa {
      --mapa-alto: 220px;
    }

    .nota {
      margin: 0;
      font-size: var(--t-chico);
      color: var(--tenue);
    }

    .nota.aviso {
      color: var(--aviso);
    }

    .ruta {
      display: flex;
      align-items: center;
      gap: var(--e2);
      margin: 0;
      font-weight: 600;
      color: var(--tinta);
    }
  `,
})
export class SelectorUbicacion {
  private readonly i18n = inject(I18nService);
  private readonly mapasApi = inject(MapasApi);
  protected readonly ubicacion = inject(UbicacionLocalService);
  protected readonly t = this.i18n.t;

  readonly direccion = model('');
  readonly punto = model<Punto | null>(null);
  readonly referencia = model('');

  readonly etiqueta = input.required<string>();
  readonly placeholder = input('');
  readonly conReferencia = input(false);
  /** Distancia y tiempo desde el local; solo tiene sentido en un delivery. */
  readonly conRuta = input(false);
  /** Marca el local en el mapa. En Configuracion no: ahi el punto es el local. */
  readonly mostrarLocal = input(true);

  protected readonly id = `ubicacion-${Math.random().toString(36).slice(2, 8)}`;
  protected readonly sugerencias = signal<DireccionDto[]>([]);
  protected readonly abiertas = signal(false);
  protected readonly activa = signal(-1);
  protected readonly buscando = signal(false);
  /** Se toco un punto y ORS no le encontro calle con nombre. */
  protected readonly sinNombre = signal(false);
  protected readonly ruta = signal<RutaDto | null>(null);

  private pausa: ReturnType<typeof setTimeout> | undefined;
  /** Cada peticion lleva su numero: una respuesta vieja que llega tarde no pisa a la nueva. */
  private ultimaDireccion = 0;
  private ultimasSugerencias = 0;
  private ultimaRuta = 0;

  protected readonly avisoNota = computed(
    () => this.ubicacion.geocodificacion() === false || this.sinNombre(),
  );

  protected readonly nota = computed(() => {
    if (this.ubicacion.geocodificacion() === false) return this.t('mapa.sinGeocodificacion');
    if (this.buscando()) return this.t('mapa.buscando');
    if (this.sinNombre()) return this.t('mapa.sinNombre');
    return this.t('mapa.ayuda');
  });

  protected readonly textoRuta = computed(() => {
    const ruta = this.ruta();
    if (!ruta?.metros || !ruta.segundos) return null;
    return this.t('mapa.ruta', {
      distancia: formatearDistancia(ruta.metros, this.i18n.idioma()),
      min: Math.max(1, Math.round(ruta.segundos / 60)),
    });
  });

  constructor() {
    this.ubicacion.cargarEstado();
    inject(DestroyRef).onDestroy(() => clearTimeout(this.pausa));

    // La ruta sigue al punto, venga de donde venga: un toque, una sugerencia o
    // la direccion habitual del cliente que eligio el POS.
    effect(() => {
      const punto = this.punto();
      if (!this.conRuta()) return;
      untracked(() => this.calcularRuta(punto));
    });
  }

  protected escribir(valor: string): void {
    this.direccion.set(valor);
    this.sinNombre.set(false);
    clearTimeout(this.pausa);

    const texto = valor.trim();
    if (texto.length < MINIMO_PARA_SUGERIR || this.ubicacion.geocodificacion() === false) {
      this.cerrarSugerencias();
      return;
    }
    this.pausa = setTimeout(() => this.pedirSugerencias(texto), PAUSA_SUGERENCIAS_MS);
  }

  private pedirSugerencias(texto: string): void {
    const numero = ++this.ultimasSugerencias;
    this.mapasApi
      .sugerirDirecciones({ texto })
      .pipe(catchError(() => of([] as DireccionDto[])))
      .subscribe((encontradas) => {
        if (numero !== this.ultimasSugerencias) return;
        this.sugerencias.set(encontradas);
        this.activa.set(-1);
        this.abiertas.set(encontradas.length > 0);
      });
  }

  protected teclear(evento: KeyboardEvent): void {
    if (!this.abiertas()) return;
    const total = this.sugerencias().length;

    switch (evento.key) {
      case 'ArrowDown':
        evento.preventDefault();
        this.activa.set(Math.min(this.activa() + 1, total - 1));
        return;
      case 'ArrowUp':
        evento.preventDefault();
        this.activa.set(Math.max(this.activa() - 1, 0));
        return;
      case 'Enter':
        if (this.activa() >= 0) {
          evento.preventDefault();
          this.elegirSugerencia(this.sugerencias()[this.activa()]);
        }
        return;
      case 'Escape':
        evento.preventDefault();
        this.cerrarSugerencias();
        return;
    }
  }

  protected cerrarSugerencias(): void {
    this.abiertas.set(false);
    this.activa.set(-1);
  }

  protected elegirSugerencia(sugerencia: DireccionDto): void {
    this.cerrarSugerencias();
    this.direccion.set(sugerencia.direccion || sugerencia.etiqueta || this.direccion());
    this.sinNombre.set(false);
    if (sugerencia.latitud != null && sugerencia.longitud != null) {
      this.punto.set({ latitud: sugerencia.latitud, longitud: sugerencia.longitud });
    }
  }

  /**
   * El punto tocado reemplaza la direccion escrita: el mozo lo marco para que
   * se complete. Si ORS no encuentra calle, lo escrito se queda y la nota pide
   * escribirla.
   */
  alElegirEnMapa(punto: Punto): void {
    this.punto.set(punto);
    this.cerrarSugerencias();
    if (this.ubicacion.geocodificacion() === false) return;

    const numero = ++this.ultimaDireccion;
    this.buscando.set(true);
    this.sinNombre.set(false);
    this.mapasApi
      .direccionEnPunto({ latitud: punto.latitud, longitud: punto.longitud })
      .pipe(catchError(() => of(null)))
      .subscribe((encontrada) => {
        if (numero !== this.ultimaDireccion) return;
        this.buscando.set(false);
        if (encontrada?.direccion) {
          this.direccion.set(encontrada.direccion);
        } else {
          this.sinNombre.set(true);
        }
      });
  }

  private calcularRuta(punto: Punto | null): void {
    const numero = ++this.ultimaRuta;
    this.ruta.set(null);
    if (!punto || this.ubicacion.geocodificacion() === false) return;
    this.mapasApi
      .rutaDesdeElLocal({ latitud: punto.latitud, longitud: punto.longitud })
      .pipe(catchError(() => of(null)))
      .subscribe((ruta) => {
        if (numero === this.ultimaRuta) this.ruta.set(ruta);
      });
  }
}
