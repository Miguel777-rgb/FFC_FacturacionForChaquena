import {
  ApplicationConfig,
  inject,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { environment } from '../environments/environment';
import { Configuration } from './api';
import { SesionService } from './nucleo/sesion/sesion.service';
import { erroresInterceptor } from './nucleo/http/errores.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),

    // `withComponentInputBinding` es lo que deja que el `data` de cada ruta
    // llegue a los `input()` del componente sin leer el ActivatedRoute a mano.
    provideRouter(routes, withComponentInputBinding()),

    provideHttpClient(withInterceptors([erroresInterceptor])),

    // El token lo pone el propio cliente generado, y solo en los endpoints que
    // declaran `bearerAuth` en el contrato. Es mas estrecho que un interceptor
    // que lo pegaria en toda peticion saliente, incluidas las de otros hosts.
    {
      provide: Configuration,
      useFactory: () => {
        const sesion = inject(SesionService);
        return new Configuration({
          basePath: environment.apiBasePath,
          credentials: { bearerAuth: () => sesion.tokenActual() },
        });
      },
    },
  ],
};
