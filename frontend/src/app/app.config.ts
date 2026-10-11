import {
  ApplicationConfig,
  inject,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
  provideZonelessChangeDetection,
} from '@angular/core';
import { TitleStrategy, provideRouter, withComponentInputBinding } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';

import { routes } from './app.routes';
import { environment } from '../environments/environment';
// Directo al archivo y no al barril `./api`: el barril reexporta cada
// servicio generado, y desde aqui los metia todos en el bundle inicial.
import { Configuration } from './api/configuration';
import { SesionService } from './nucleo/sesion/sesion.service';
import { erroresInterceptor } from './nucleo/http/errores.interceptor';
import { idiomaInterceptor } from './nucleo/http/idioma.interceptor';
import { I18nService } from './nucleo/i18n/i18n.service';
import { TituloDeRuta } from './nucleo/i18n/titulo.strategy';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideZonelessChangeDetection(),

    // El idioma guardado se descarga antes de pintar: sin esta espera, quien dejo la interfaz
    // en ingles veria la primera pantalla en espanol.
    provideAppInitializer(() => inject(I18nService).precargar()),

    // `withComponentInputBinding` es lo que deja que el `data` de cada ruta
    // llegue a los `input()` del componente sin leer el ActivatedRoute a mano.
    provideRouter(routes, withComponentInputBinding()),

    provideHttpClient(withInterceptors([idiomaInterceptor, erroresInterceptor])),

    // Las rutas declaran la clave de su titulo: esta estrategia la traduce y la reescribe al
    // cambiar de idioma sin navegar.
    { provide: TitleStrategy, useClass: TituloDeRuta },

    // El token lo pone el cliente generado solo donde el contrato declara `bearerAuth`: mas
    // estrecho que un interceptor, que lo pegaria tambien en peticiones a otros hosts.
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
