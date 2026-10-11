import { inject } from '@angular/core';
import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { SesionService } from '../sesion/sesion.service';
import { I18nService } from '../i18n/i18n.service';
import { AvisosService } from './avisos.service';

/**
 * La forma de todo error de `GlobalExceptionHandler`, escrita a mano: ningun endpoint documenta sus
 * errores con `@ApiResponse`, asi que el generador no produce el modelo.
 */
export interface ErrorResponseDto {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  details?: string[];
}

/** El mensaje mas util de la respuesta; se exporta para que el login lo lea igual que el interceptor. */
export function mensajeDe(error: HttpErrorResponse, porDefecto: string): string {
  const cuerpo = error.error as ErrorResponseDto | string | null;

  if (typeof cuerpo === 'string' && cuerpo.trim()) return cuerpo;

  if (cuerpo && typeof cuerpo === 'object') {
    // Los errores de validacion traen el detalle campo por campo; ese texto es
    // mas util que el "Argumento invalido" generico de la cabecera.
    if (cuerpo.details?.length) return cuerpo.details.join(' · ');
    if (cuerpo.message?.trim()) return cuerpo.message;
  }

  return porDefecto;
}

/**
 * Muestra el error del backend y saca al usuario cuando su sesion ya no vale. El mensaje del servidor va
 * tal cual, en el idioma en que venga: explica cosas que la pantalla no sabe.
 */
export const erroresInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const avisos = inject(AvisosService);
  const t = inject(I18nService).t;

  return siguiente(peticion).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);

      switch (error.status) {
        case 0:
          avisos.error(t('errores.sinConexion'));
          break;

        case 401:
          // El login fallido tambien da 401, pero ahi no hay sesion que cerrar
          // ni a donde echar a nadie: que lo muestre la propia pantalla.
          if (sesion.autenticado()) {
            sesion.cerrar();
            avisos.error(t('errores.sesionVencida'));
            void router.navigate(['/entrar'], { queryParams: { volverA: router.url } });
          }
          break;

        case 403:
          avisos.error(t('errores.sinPermiso'));
          break;

        case 404:
          avisos.error(mensajeDe(error, t('errores.noEncontrado')));
          break;

        case 409:
          // Las transiciones invalidas caen aqui, y el mensaje del servidor ya dice cuales si se
          // permiten: mas util que cualquier texto propio.
          avisos.error(mensajeDe(error, t('errores.conflicto')));
          break;

        case 422:
        case 400:
          avisos.error(mensajeDe(error, t('errores.datos')));
          break;

        default:
          if (error.status >= 500) {
            avisos.error(t('errores.servidor'));
          } else {
            avisos.error(mensajeDe(error, t('errores.generico')));
          }
      }

      return throwError(() => error);
    }),
  );
};
