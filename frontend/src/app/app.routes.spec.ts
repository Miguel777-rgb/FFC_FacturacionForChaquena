import { describe, it, expect } from 'vitest';

import { routes } from './app.routes';
import { INICIO_POR_ROL, ROLES } from './nucleo/sesion/rol';

/**
 * Cuando la trastienda se repartio, `/trastienda` dejo de ser una pantalla. Si
 * algun rol siguiera aterrizando ahi, entraria por una redireccion o, peor, por
 * el comodin a `/inicio` y de vuelta, sin ningun error que lo delate.
 */
describe('Rutas', () => {
  it('cada rol aterriza en una pantalla de verdad, no en una redireccion', () => {
    for (const rol of ROLES) {
      const ruta = routes.find((r) => `/${r.path}` === INICIO_POR_ROL[rol]);

      expect(ruta, `${rol} -> ${INICIO_POR_ROL[rol]}`).toBeDefined();
      expect(ruta?.redirectTo, rol).toBeUndefined();
      expect(ruta?.loadComponent, rol).toBeDefined();
    }
  });

  it('las direcciones viejas llevan a su sitio nuevo', () => {
    expect(routes.find((r) => r.path === 'trastienda')?.redirectTo).toBe('inventario');
    expect(routes.find((r) => r.path === 'kpis')?.redirectTo).toBe('reportes');
  });
});
