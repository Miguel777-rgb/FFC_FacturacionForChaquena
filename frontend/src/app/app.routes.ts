import type { Routes } from '@angular/router';

import { sesionAbierta, exigeRol } from './nucleo/sesion/guardas';

/**
 * Un arbol de rutas, una superficie por destino del panel. Cada una carga en
 * diferido: el celular del mozo no descarga el codigo del arqueo de caja, y la
 * cocina arranca con lo minimo.
 *
 * Los roles de cada `exigeRol` estan copiados de los `@PreAuthorize` del
 * controlador correspondiente. Si no coinciden, el usuario llega a una
 * pantalla que el servidor le va a negar con un 403.
 *
 * El `title` no es el titulo sino su clave de traduccion, y es la misma que
 * nombra el destino en el panel: el menu, el titulo de la pantalla y la pestana
 * del navegador dicen siempre lo mismo. Lo resuelve `TituloDeRuta`.
 */
export const routes: Routes = [
  {
    path: 'entrar',
    title: 'titulo.entrar',
    loadComponent: () => import('./paginas/login/login.page').then((m) => m.LoginPage),
  },

  // --- operacion: los cuatro puestos por los que pasa una comanda ---------------
  {
    path: 'pos',
    title: 'panel.pos',
    canActivate: [sesionAbierta, exigeRol('MOZO', 'ADMIN')],
    loadComponent: () => import('./paginas/pos/pos.page').then((m) => m.PosPage),
  },

  {
    path: 'kds',
    title: 'panel.kds',
    canActivate: [sesionAbierta, exigeRol('COCINA', 'ADMIN')],
    loadComponent: () => import('./paginas/kds/kds.page').then((m) => m.KdsPage),
  },

  {
    path: 'caja',
    title: 'panel.caja',
    canActivate: [sesionAbierta, exigeRol('CAJA', 'ADMIN')],
    loadComponent: () => import('./paginas/caja/caja.page').then((m) => m.CajaPage),
  },

  {
    path: 'despacho',
    title: 'panel.despacho',
    canActivate: [sesionAbierta, exigeRol('DELIVERY', 'MOZO', 'ADMIN')],
    loadComponent: () => import('./paginas/despacho/despacho.page').then((m) => m.DespachoPage),
  },

  // --- gestion: cada funcion administrativa es su propio destino ----------------
  // La trastienda juntaba cinco cosas que no se parecian. Se reparte: la carta y
  // el stock son trabajo de almacen; los parametros, los bots y los eventos, del
  // administrador.
  {
    path: 'menu',
    title: 'panel.menu',
    canActivate: [sesionAbierta, exigeRol('ALMACEN', 'ADMIN')],
    loadComponent: () => import('./paginas/menu/menu.page').then((m) => m.MenuPage),
  },

  {
    path: 'inventario',
    title: 'panel.inventario',
    canActivate: [sesionAbierta, exigeRol('ALMACEN', 'ADMIN')],
    loadComponent: () =>
      import('./paginas/inventario/inventario.page').then((m) => m.InventarioPage),
  },

  {
    path: 'reportes',
    title: 'panel.reportes',
    canActivate: [sesionAbierta, exigeRol('ADMIN', 'CAJA')],
    loadComponent: () => import('./paginas/reportes/reportes.page').then((m) => m.ReportesPage),
  },

  {
    path: 'personal',
    title: 'panel.personal',
    canActivate: [sesionAbierta, exigeRol('ADMIN')],
    loadComponent: () => import('./paginas/personal/personal.page').then((m) => m.PersonalPage),
  },

  {
    path: 'configuracion',
    title: 'panel.configuracion',
    canActivate: [sesionAbierta, exigeRol('ADMIN')],
    loadComponent: () =>
      import('./paginas/configuracion/configuracion.page').then((m) => m.ConfiguracionPage),
  },

  // Las direcciones viejas siguen llevando a algun sitio: alguien las tiene en
  // favoritos en la PC de caja.
  { path: 'trastienda', pathMatch: 'full', redirectTo: 'inventario' },
  { path: 'kpis', pathMatch: 'full', redirectTo: 'reportes' },

  {
    path: 'sin-permiso',
    title: 'titulo.sinPermiso',
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
