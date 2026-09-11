import { inject } from '@angular/core';
import type { HttpInterceptorFn } from '@angular/common/http';

import { I18nService } from '../i18n/i18n.service';

/**
 * Declara en cada peticion el idioma que la persona esta viendo.
 *
 * Hoy el backend no lo mira: sus mensajes salen en espanol y el interceptor de
 * errores los muestra tal cual, porque el texto del servidor explica cosas que
 * la pantalla no sabe —que transiciones de estado si se permiten, que insumo
 * falto—. Reescribirlos aqui seria inventarlos.
 *
 * La cabecera va igualmente porque es el mecanismo estandar y esta es la unica
 * pieza que sabe el idioma: el dia que el backend traduzca sus mensajes, no
 * hay nada que cambiar en el frontend. Cuesta una cabecera por peticion.
 */
export const idiomaInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const i18n = inject(I18nService);

  return siguiente(peticion.clone({ setHeaders: { 'Accept-Language': i18n.idioma() } }));
};
