import { inject } from '@angular/core';
import { HttpErrorResponse, type HttpInterceptorFn } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { SesionService } from '../sesion/sesion.service';
import { AvisosService } from './avisos.service';

/**
 * Forma que devuelve `GlobalExceptionHandler` en el backend para todo error.
 *
 * Se escribe a mano porque el contrato OpenAPI no la declara: ningun endpoint
 * documenta sus respuestas de error con `@ApiResponse`, asi que el generador no
 * produce el modelo. Si algun dia se documentan, esto se reemplaza por el tipo
 * generado.
 */
export interface ErrorResponseDto {
  timestamp?: string;
  status?: number;
  error?: string;
  message?: string;
  path?: string;
  details?: string[];
}

/**
 * Saca el mensaje mas util que traiga la respuesta.
 *
 * Se exporta porque las pantallas que muestran el error por su cuenta —el
 * login, sin ir mas lejos— tienen que leerlo igual que lo lee el interceptor.
 * Duplicar esta logica alli fue justo lo que dejo al login inventando textos
 * a partir del codigo HTTP en vez de decir lo que el servidor explicaba.
 */
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
 * Traduce los errores del backend a un aviso legible, y saca al usuario cuando
 * su sesion ya no vale.
 *
 * El token no se toca aqui: lo pone el propio cliente generado a traves de
 * `credentials.bearerAuth`, que solo lo manda a los endpoints que declaran
 * seguridad. Asi no se filtra a un tercero si algun dia se llama a otro host.
 */
export const erroresInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const sesion = inject(SesionService);
  const router = inject(Router);
  const avisos = inject(AvisosService);

  return siguiente(peticion).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) return throwError(() => error);

      switch (error.status) {
        case 0:
          avisos.error('Sin conexion con el servidor. Revisa la red y vuelve a intentar.');
          break;

        case 401:
          // El login fallido tambien da 401, pero ahi no hay sesion que cerrar
          // ni a donde echar a nadie: que lo muestre la propia pantalla.
          if (sesion.autenticado()) {
            sesion.cerrar();
            avisos.error('Tu sesion vencio. Entra de nuevo.');
            void router.navigate(['/entrar'], { queryParams: { volverA: router.url } });
          }
          break;

        case 403:
          avisos.error('Tu cargo no tiene permiso para esta accion.');
          break;

        case 404:
          avisos.error(mensajeDe(error, 'No se encontro lo que buscabas.'));
          break;

        case 409:
          // Las transiciones invalidas de comanda caen aqui, y el mensaje del
          // servidor ya explica que transiciones si se permiten. Mostrarlo tal
          // cual es mas util que cualquier texto propio.
          avisos.error(mensajeDe(error, 'La operacion choca con el estado actual.'));
          break;

        case 422:
        case 400:
          avisos.error(mensajeDe(error, 'Revisa los datos: hay algo que el servidor no acepta.'));
          break;

        default:
          if (error.status >= 500) {
            avisos.error('El servidor fallo. Si se repite, avisa al administrador.');
          } else {
            avisos.error(mensajeDe(error, 'No se pudo completar la operacion.'));
          }
      }

      return throwError(() => error);
    }),
  );
};
