import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';

import { SesionService } from './sesion.service';
import { INICIO_POR_ROL, type Rol } from './rol';

/** Exige sesion vigente y guarda a donde se queria ir, para volver ahi despues del login. */
export const sesionAbierta: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (sesion.autenticado() && !sesion.expirada()) return true;

  sesion.cerrar();
  return router.createUrlTree(['/entrar'], { queryParams: { volverA: estado.url } });
};

/**
 * Con sesion abierta, /entrar lleva al puesto del rol: si no, retroceder con el navegador dibujaba el
 * login dentro del cascaron de la sesion.
 */
export const sinSesion: CanActivateFn = () => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (!sesion.autenticado() || sesion.expirada()) return true;

  const primerRol = sesion.roles()[0];
  return router.parseUrl(primerRol ? INICIO_POR_ROL[primerRol] : '/inicio');
};

/**
 * Comodidad de navegacion, no seguridad: quien edite el token pasa de aqui, y lo que protege los datos
 * son los `@PreAuthorize` del backend.
 */
export function exigeRol(...permitidos: Rol[]): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const router = inject(Router);

    if (sesion.tieneAlgunRol(permitidos)) return true;
    return router.createUrlTree(['/sin-permiso']);
  };
}
