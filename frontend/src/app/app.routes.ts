import type { Routes } from '@angular/router';

import { sesionAbierta, exigeRol } from './nucleo/sesion/guardas';

/**
 * Un arbol de rutas, siete superficies. Cada una carga en diferido: la tablet
 * del mozo no descarga el codigo del arqueo de caja, y la pantalla de cocina
 * arranca con lo minimo.
 *
 * Los roles de cada `exigeRol` estan copiados de los `@PreAuthorize` del
 * controlador correspondiente. Si no coinciden, el usuario llega a una
 * pantalla que el servidor le va a negar con un 403.
 *
 * El `title` no es el titulo sino su clave de traduccion: lo resuelve
 * `TituloDeRuta`, que ademas le pega la marca y lo reescribe al cambiar de
 * idioma.
 */
export const routes: Routes = [
  {
    path: 'entrar',
    title: 'titulo.entrar',
    loadComponent: () => import('./paginas/login/login.page').then((m) => m.LoginPage),
  },

  // Las siete superficies estan implementadas. Las tres primeras —tomar,
  // cocinar, cobrar— cubren el recorrido completo de una comanda de mesa;
  // despacho cierra el reparto, y las tres ultimas son la administracion del
  // local. Lo que sigue pendiente de cada una esta anotado en su propio
  // componente.
  {
    path: 'pos',
    title: 'titulo.pos',
    canActivate: [sesionAbierta, exigeRol('MOZO', 'ADMIN')],
    loadComponent: () => import('./paginas/pos/pos.page').then((m) => m.PosPage),
  },

  {
    path: 'kds',
    title: 'titulo.kds',
    canActivate: [sesionAbierta, exigeRol('COCINA', 'ADMIN')],
    loadComponent: () => import('./paginas/kds/kds.page').then((m) => m.KdsPage),
  },

  {
    path: 'caja',
    title: 'titulo.caja',
    canActivate: [sesionAbierta, exigeRol('CAJA', 'ADMIN')],
    loadComponent: () => import('./paginas/caja/caja.page').then((m) => m.CajaPage),
  },

  {
    path: 'despacho',
    title: 'titulo.despacho',
    canActivate: [sesionAbierta, exigeRol('DELIVERY', 'MOZO', 'ADMIN')],
    loadComponent: () => import('./paginas/despacho/despacho.page').then((m) => m.DespachoPage),
  },

  {
    path: 'trastienda',
    title: 'titulo.trastienda',
    canActivate: [sesionAbierta, exigeRol('ALMACEN', 'ADMIN')],
    loadComponent: () =>
      import('./paginas/trastienda/trastienda.page').then((m) => m.TrastiendaPage),
  },

  // El personal y los indicadores fueron pestanas de la trastienda y ya no lo
  // son: administrar permisos no es trabajo de almacen, y una cifra que se mira
  // al llegar no puede costar dos clics dentro de otra pantalla.
  {
    path: 'personal',
    title: 'titulo.personal',
    canActivate: [sesionAbierta, exigeRol('ADMIN')],
    loadComponent: () => import('./paginas/personal/personal.page').then((m) => m.PersonalPage),
  },

  {
    path: 'kpis',
    title: 'titulo.kpis',
    canActivate: [sesionAbierta, exigeRol('ADMIN', 'CAJA')],
    loadComponent: () => import('./paginas/kpis/kpis.page').then((m) => m.KpisPage),
  },

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
