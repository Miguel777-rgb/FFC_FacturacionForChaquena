import { inject } from '@angular/core';
import type { HttpInterceptorFn } from '@angular/common/http';

import { I18nService } from '../i18n/i18n.service';

/**
 * Declara en cada peticion el idioma que la persona esta viendo.
 *
 * El backend la usa para sus errores y su validacion (`messages*.properties`),
 * para las exportaciones y para el correo de recuperacion. Los mensajes de
 * dominio —que transiciones se permiten, que insumo falto— siguen en espanol, y
 * el interceptor de errores los muestra tal cual: reescribirlos aqui seria
 * inventarlos.
 */
export const idiomaInterceptor: HttpInterceptorFn = (peticion, siguiente) => {
  const i18n = inject(I18nService);

  return siguiente(peticion.clone({ setHeaders: { 'Accept-Language': i18n.idioma() } }));
};
