import type { Routes } from '@angular/router';

import { sesionAbierta, sinSesion, exigeRol } from './nucleo/sesion/guardas';

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
    canActivate: [sinSesion],
    loadComponent: () => import('./paginas/login/login.page').then((m) => m.LoginPage),
  },

  // Las dos pantallas del olvido: pedir el enlace y elegir la contrasena nueva.
  // Publicas por lo mismo que el acceso —quien llega aqui no puede entrar— y con
  // `sinSesion` por lo mismo: con la sesion abierta no pintan nada.
  {
    path: 'recuperar',
    title: 'titulo.recuperar',
    canActivate: [sinSesion],
    loadComponent: () => import('./paginas/recuperar/recuperar.page').then((m) => m.RecuperarPage),
  },
  {
    path: 'restablecer',
    title: 'titulo.restablecer',
    canActivate: [sinSesion],
    loadComponent: () =>
      import('./paginas/restablecer/restablecer.page').then((m) => m.RestablecerPage),
  },

  // --- operacion: el dia del local y los puestos por los que pasa una comanda ----
  {
    path: 'tablero',
    title: 'panel.tablero',
    canActivate: [sesionAbierta, exigeRol('ADMIN', 'CAJA')],
    loadComponent: () => import('./paginas/tablero/tablero.page').then((m) => m.TableroPage),
  },

  {
    path: 'pos',
    title: 'panel.pos',
    canActivate: [sesionAbierta, exigeRol('MOZO', 'ADMIN')],
    loadComponent: () => import('./paginas/pos/pos.page').then((m) => m.PosPage),
  },

  // Consultar es de cualquier sesion, pero cambiar de estado y cancelar piden
  // estos tres roles: sin ellos la pantalla solo serviria para mirar.
  {
    path: 'ordenes',
    title: 'panel.ordenes',
    canActivate: [sesionAbierta, exigeRol('ADMIN', 'MOZO', 'CAJA')],
    loadComponent: () => import('./paginas/ordenes/ordenes.page').then((m) => m.OrdenesPage),
  },

  {
    path: 'mesas',
    title: 'panel.mesas',
    canActivate: [sesionAbierta, exigeRol('ADMIN', 'MOZO', 'CAJA')],
    loadComponent: () => import('./paginas/mesas/mesas.page').then((m) => m.MesasPage),
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

  // El servidor deja leer clientes a cualquier sesion; la ficha es de quien
  // cobra y de quien administra, que son los que atienden a un cliente con nombre.
  {
    path: 'clientes',
    title: 'panel.clientes',
    canActivate: [sesionAbierta, exigeRol('ADMIN', 'CAJA')],
    loadComponent: () => import('./paginas/clientes/clientes.page').then((m) => m.ClientesPage),
  },

  {
    path: 'configuracion',
    title: 'panel.configuracion',
    canActivate: [sesionAbierta, exigeRol('ADMIN')],
    loadComponent: () =>
      import('./paginas/configuracion/configuracion.page').then((m) => m.ConfiguracionPage),
  },

  // --- usuario ------------------------------------------------------------------
  {
    path: 'perfil',
    title: 'panel.perfil',
    canActivate: [sesionAbierta],
    loadComponent: () => import('./paginas/perfil/perfil.page').then((m) => m.PerfilPage),
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
