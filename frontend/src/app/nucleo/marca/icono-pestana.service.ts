import { DOCUMENT, Injectable, effect, inject } from '@angular/core';

import { LogoService } from './logo.service';

/**
 * El icono de la pestana: el logo del local mientras hay sesion, y el que trae
 * `index.html` cuando no la hay o el local no tiene logo.
 *
 * La pantalla de entrar no puede saber que logo hay, porque leer los datos del
 * local exige estar dentro: por eso la aplicacion trae uno propio. Al entrar se
 * cambia por el que se subio en Configuracion, y al salir vuelve el propio.
 *
 * Cambia el `href` del mismo `<link rel="icon">` en vez de sumar otro: con dos,
 * cada navegador elige a su manera cual pinta.
 *
 * Nadie lo inyecta para usarlo: lo construye `App` al arrancar, como el tema.
 */
@Injectable({ providedIn: 'root' })
export class IconoPestanaService {
  private readonly logo = inject(LogoService).logo;
  private readonly enlace = inject(DOCUMENT).querySelector<HTMLLinkElement>('link[rel~="icon"]');
  /** El de `index.html`, leido antes de tocar el enlace. */
  private readonly propio = this.enlace?.getAttribute('href') ?? null;

  constructor() {
    effect(() => {
      const href = this.logo() ?? this.propio;
      if (this.enlace && href) this.enlace.setAttribute('href', href);
    });
  }
}
