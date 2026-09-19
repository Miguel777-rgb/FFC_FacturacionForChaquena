import { inject } from '@angular/core';
import { Router, type CanActivateFn } from '@angular/router';

import { SesionService } from './sesion.service';
import { INICIO_POR_ROL, type Rol } from './rol';

/**
 * Exige sesion abierta y no vencida. Guarda a donde queria ir el usuario para
 * devolverlo ahi despues del login, en vez de soltarlo siempre en el inicio.
 */
export const sesionAbierta: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (sesion.autenticado() && !sesion.expirada()) return true;

  sesion.cerrar();
  return router.createUrlTree(['/entrar'], { queryParams: { volverA: estado.url } });
};

/**
 * Lo contrario: con la sesion abierta, la pantalla de entrar no se ve y se va
 * al puesto del rol.
 *
 * Sin esto, retroceder con el navegador hasta /entrar dibujaba el formulario
 * dentro del cascaron de la sesion: el panel con el nombre y el cargo de quien
 * ya entro, y al lado un login pidiendo usuario y contrasena.
 */
export const sinSesion: CanActivateFn = () => {
  const sesion = inject(SesionService);
  const router = inject(Router);

  if (!sesion.autenticado() || sesion.expirada()) return true;

  const primerRol = sesion.roles()[0];
  return router.parseUrl(primerRol ? INICIO_POR_ROL[primerRol] : '/inicio');
};

/**
 * Restringe una ruta a ciertos roles.
 *
 * Es una comodidad de navegacion, no una medida de seguridad: cualquiera puede
 * editar el token en el navegador y pasar de aqui. Lo que de verdad protege los
 * datos son los `@PreAuthorize` del backend, que responden 403 igual.
 *
 *   { path: 'kds', canActivate: [sesionAbierta, exigeRol('COCINA', 'ADMIN')] }
 */
export function exigeRol(...permitidos: Rol[]): CanActivateFn {
  return () => {
    const sesion = inject(SesionService);
    const router = inject(Router);

    if (sesion.tieneAlgunRol(permitidos)) return true;
    return router.createUrlTree(['/sin-permiso']);
  };
}
