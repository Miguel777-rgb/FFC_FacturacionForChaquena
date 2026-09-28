import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, of } from 'rxjs';

import { LocalApi } from '../../api/api/local.api';
import { MapasApi } from '../../api/api/mapas.api';
import type { Punto } from '../../disenio/mapa';

/**
 * El centro de Lima: donde se abre el mapa mientras el local no este marcado.
 * Es un punto de partida razonable para un local peruano, no una suposicion de
 * donde esta este: por eso la pantalla avisa de que falta marcarlo.
 */
export const CENTRO_POR_DEFECTO: Punto = { latitud: -12.0464, longitud: -77.0428 };

/**
 * Donde esta el local y si hay geocodificador, leidos una vez por sesion y
 * compartidos por todos los mapas: el del delivery, el del cliente, el de
 * despacho y el de configuracion.
 *
 * El punto del local sale de `GET /local` (lo marca el administrador en
 * Configuracion). El estado del geocodificador, de `GET /geo/estado`: sin
 * clave de OpenRouteService la direccion no se completa sola, y la pantalla
 * tiene que decirlo antes de que alguien toque el mapa esperandolo.
 */
@Injectable({ providedIn: 'root' })
export class UbicacionLocalService {
  private readonly localApi = inject(LocalApi);
  private readonly mapasApi = inject(MapasApi);

  private readonly _punto = signal<Punto | null>(null);
  private readonly _geocodificacion = signal<boolean | null>(null);
  private pedido = false;
  private pedidoEstado = false;

  /** El local marcado en el mapa, o null si aun no se marco. */
  readonly punto = this._punto.asReadonly();
  /** Donde se abre un mapa sin punto: el local, o el centro de Lima. */
  readonly centro = computed(() => this._punto() ?? CENTRO_POR_DEFECTO);
  /** null mientras no se sabe; false si no hay clave o no se pudo preguntar. */
  readonly geocodificacion = this._geocodificacion.asReadonly();

  /** Lee el punto del local. Cualquier sesion puede: `GET /local` es de todos. */
  cargarPunto(): void {
    if (this.pedido) return;
    this.pedido = true;
    this.localApi
      .obtenerDatosLocal()
      .pipe(catchError(() => of(null)))
      .subscribe((datos) => {
        if (datos?.latitud != null && datos?.longitud != null) {
          this._punto.set({ latitud: datos.latitud, longitud: datos.longitud });
        }
      });
  }

  /**
   * Pregunta si hay geocodificador. Solo lo llaman los mapas donde se escribe
   * una direccion: `GET /geo/estado` es de quienes las escriben, y despacho no.
   */
  cargarEstado(): void {
    this.cargarPunto();
    if (this.pedidoEstado) return;
    this.pedidoEstado = true;
    this.mapasApi
      .estadoGeo()
      .pipe(catchError(() => of(null)))
      .subscribe((estado) => this._geocodificacion.set(estado?.geocodificacion === true));
  }

  /** Configuracion lo llama al guardar: los mapas abiertos se recentran sin recargar. */
  fijar(punto: Punto | null): void {
    this._punto.set(punto);
  }
}
