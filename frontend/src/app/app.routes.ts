import type { Routes } from '@angular/router';

import { sesionAbierta, exigeRol } from './nucleo/sesion/guardas';

/**
 * Un arbol de rutas, cinco superficies. Cada una carga en diferido: la tablet
 * del mozo no descarga el codigo del arqueo de caja, y la pantalla de cocina
 * arranca con lo minimo.
 *
 * Los roles de cada `exigeRol` estan copiados de los `@PreAuthorize` del
 * controlador correspondiente. Si no coinciden, el usuario llega a una
 * pantalla que el servidor le va a negar con un 403.
 */
export const routes: Routes = [
  {
    path: 'entrar',
    title: 'Entrar · Chaquena',
    loadComponent: () => import('./paginas/login/login.page').then((m) => m.LoginPage),
  },

  // Las tres superficies del flujo principal —tomar, cocinar, cobrar— ya no son
  // marcadores: cubren el recorrido completo de una comanda de mesa. Lo que
  // sigue pendiente de cada una esta anotado en su propio componente.
  {
    path: 'pos',
    title: 'POS · Chaquena',
    canActivate: [sesionAbierta, exigeRol('MOZO', 'ADMIN')],
    loadComponent: () => import('./paginas/pos/pos.page').then((m) => m.PosPage),
  },

  {
    path: 'kds',
    title: 'Cocina · Chaquena',
    canActivate: [sesionAbierta, exigeRol('COCINA', 'ADMIN')],
    loadComponent: () => import('./paginas/kds/kds.page').then((m) => m.KdsPage),
  },

  {
    path: 'caja',
    title: 'Caja · Chaquena',
    canActivate: [sesionAbierta, exigeRol('CAJA', 'ADMIN')],
    loadComponent: () => import('./paginas/caja/caja.page').then((m) => m.CajaPage),
  },

  {
    path: 'despacho',
    title: 'Despacho · Chaquena',
    canActivate: [sesionAbierta, exigeRol('DELIVERY', 'MOZO', 'ADMIN')],
    loadComponent: () =>
      import('./paginas/pendiente/superficie-pendiente.page').then(
        (m) => m.SuperficiePendientePage,
      ),
    data: {
      titulo: 'Despacho y delivery',
      fase: 4,
      resumen:
        'Tablero de pedidos en ruta, asignacion de transportista y vehiculo, y el teclado de OTP que cierra la entrega.',
      endpoints: [
        'GET  /api/v1/delivery/tablero',
        'POST /api/v1/ordenes/{ordenId}/delivery/asignar',
        'POST /api/v1/ordenes/{ordenId}/delivery/despachar',
        'POST /api/v1/ordenes/{ordenId}/delivery/otp/verificar',
        'GET  /api/v1/transportistas/activos',
      ],
    },
  },

  {
    path: 'trastienda',
    title: 'Trastienda · Chaquena',
    canActivate: [sesionAbierta, exigeRol('ALMACEN', 'ADMIN')],
    loadComponent: () =>
      import('./paginas/pendiente/superficie-pendiente.page').then(
        (m) => m.SuperficiePendientePage,
      ),
    data: {
      titulo: 'Trastienda',
      fase: 5,
      resumen:
        'Insumos y alertas de stock, kardex, conteo fisico, recetas, carta, personal, cupones y la bandeja del outbox.',
      endpoints: [
        'GET  /api/v1/insumos/alertas',
        'GET  /api/v1/inventario/kardex/{insumoId}',
        'POST /api/v1/inventario/conteo-fisico',
        'PUT  /api/v1/platillos/{id}/receta',
        'GET  /api/v1/outbox/eventos',
      ],
    },
  },

  {
    path: 'sin-permiso',
    title: 'Sin permiso · Chaquena',
    canActivate: [sesionAbierta],
    loadComponent: () =>
      import('./paginas/sin-permiso/sin-permiso.page').then((m) => m.SinPermisoPage),
  },

  {
    path: 'inicio',
    canActivate: [sesionAbierta],
    loadComponent: () => import('./paginas/inicio/inicio.page').then((m) => m.InicioPage),
  },

  { path: '', pathMatch: 'full', redirectTo: 'inicio' },
  { path: '**', redirectTo: 'inicio' },
];
