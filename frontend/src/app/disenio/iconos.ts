/**
 * Iconos como cadenas de `path` SVG, no como componentes ni como fuente de
 * iconos.
 *
 * Van en `currentColor` y sobre una rejilla de 24, asi que heredan el color y
 * el tamano del sitio donde se pinten. Se declaran aqui y no en cada plantilla
 * para que el panel lateral, los botones y las cabeceras de superficie usen
 * exactamente el mismo trazo: dos iconos de cocina distintos en dos pantallas
 * distintas es lo que hace que una aplicacion parezca cosida a retazos.
 *
 * No se instala ninguna libreria de iconos: son dieciseis trazos y una
 * dependencia mas costaria mas de lo que ahorra.
 */
export type NombreIcono =
  | 'pos'
  | 'cocina'
  | 'caja'
  | 'despacho'
  | 'trastienda'
  | 'personal'
  | 'kpis'
  | 'ojo'
  | 'lapiz'
  | 'llave'
  | 'alta'
  | 'baja'
  | 'panel'
  | 'salir'
  | 'imagen'
  | 'quitar';

export const ICONOS: Record<NombreIcono, string> = {
  /** Bandeja de servicio: la mesa y la comanda. */
  pos: 'M12 3a1 1 0 0 1 1 1v.6a7 7 0 0 1 6 6.9H5a7 7 0 0 1 6-6.9V4a1 1 0 0 1 1-1ZM3 14h18a1 1 0 0 1 0 2H3a1 1 0 0 1 0-2Zm2 4h14a3 3 0 0 1-3 3H8a3 3 0 0 1-3-3Z',

  /** Olla al fuego: la cola de cocina. */
  cocina:
    'M5 9h14a1 1 0 0 1 1 1v2a7 7 0 0 1-4 6.3V20a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1v-1.7A7 7 0 0 1 4 12v-2a1 1 0 0 1 1-1Zm3.5-6a.8.8 0 0 1 .7 1.2c-.5.9-.4 1.5.2 2.3a.8.8 0 1 1-1.3 1c-1-1.3-1.2-2.7-.3-4.1a.8.8 0 0 1 .7-.4Zm4 0a.8.8 0 0 1 .7 1.2c-.5.9-.4 1.5.2 2.3a.8.8 0 1 1-1.3 1c-1-1.3-1.2-2.7-.3-4.1a.8.8 0 0 1 .7-.4Z',

  /** Billete: el cobro y el arqueo. */
  caja: 'M3 6h18a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Zm1 2v8h16V8H4Zm8 1.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5ZM5.5 10a1 1 0 1 1 0 2 1 1 0 0 1 0-2Zm13 2a1 1 0 1 1 0 2 1 1 0 0 1 0-2Z',

  /** Furgoneta: el reparto en ruta. */
  despacho:
    'M3 6h10a1 1 0 0 1 1 1v2h3.4a1 1 0 0 1 .8.4l2.6 3.4a1 1 0 0 1 .2.6V16a1 1 0 0 1-1 1h-1a3 3 0 0 0-6 0H9.9a3 3 0 0 0-6 0H3a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1Zm11 5v2h5.3L17.4 11H14ZM6.9 15.5a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2Zm10 0a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2Z',

  /** Cajas apiladas: insumos, kardex y stock. */
  trastienda:
    'M4 3h7v7H4V3Zm9 0h7v7h-7V3ZM4 12h7v9H4v-9Zm9 0h7v9h-7v-9Zm2 2v5h3v-5h-3ZM6 5v3h3V5H6Zm9 0v3h3V5h-3ZM6 14v5h3v-5H6Z',

  /** Barras laterales: plegar y desplegar el panel. */
  /** Dos personas: la nomina y los permisos que lleva cada una. */
  personal:
    'M9 4a3.5 3.5 0 1 1 0 7 3.5 3.5 0 0 1 0-7Zm7.5 1a3 3 0 1 1 0 6 3 3 0 0 1 0-6ZM9 12.5c3.6 0 6.5 1.7 6.5 3.8V20a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1v-3.7c0-2.1 2.9-3.8 6.5-3.8Zm8.4.6c2.6.3 4.6 1.6 4.6 3.2V20a1 1 0 0 1-1 1h-3.7c.1-.3.2-.6.2-1v-3.7c0-1.2-.4-2.3-1.1-3.2Z',

  /** Tres barras que suben: la venta comparada consigo misma. */
  kpis: 'M4 13h3a1 1 0 0 1 1 1v6a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-6a1 1 0 0 1 1-1Zm6.5-5h3a1 1 0 0 1 1 1v11a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1Zm6.5-5h3a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z',

  /** Ojo: mirar la ficha entera sin tocar nada. */
  ojo: 'M12 5c5 0 9 4.4 9.8 6.4a1.5 1.5 0 0 1 0 1.2C21 14.6 17 19 12 19s-9-4.4-9.8-6.4a1.5 1.5 0 0 1 0-1.2C3 9.4 7 5 12 5Zm0 2c-3.5 0-6.7 3.1-7.7 5 1 1.9 4.2 5 7.7 5s6.7-3.1 7.7-5c-1-1.9-4.2-5-7.7-5Zm0 1.8a3.2 3.2 0 1 1 0 6.4 3.2 3.2 0 0 1 0-6.4Z',

  /** Lapiz: corregir lo que ya esta escrito. */
  lapiz:
    'M16.8 3.3a1.6 1.6 0 0 1 2.3 0l1.6 1.6a1.6 1.6 0 0 1 0 2.3l-1.5 1.5-3.9-3.9 1.5-1.5ZM14 6.2l3.9 3.9-8.5 8.4a1 1 0 0 1-.5.3l-4.2 1a.8.8 0 0 1-1-1l1-4.2a1 1 0 0 1 .3-.5L14 6.2Z',

  /** Llave: la contrasena, que abre pero no describe a nadie. */
  llave:
    'M15 3a6 6 0 1 1-5.7 7.9L3.3 17a1 1 0 0 0-.3.7V20a1 1 0 0 0 1 1h2.5a1 1 0 0 0 .7-.3l.8-.8V18h1.9l1.2-1.2v-1.9h1.9l1-1A6 6 0 0 1 15 3Zm1.8 3a1.6 1.6 0 1 1 0 3.2 1.6 1.6 0 0 1 0-3.2Z',

  /** Visto dentro de un circulo: darle de alta otra vez. */
  alta: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm4.5 6.3-6 6-2.9-2.9a1 1 0 0 0-1.4 1.4l3.6 3.6a1 1 0 0 0 1.4 0l6.7-6.7a1 1 0 1 0-1.4-1.4Z',

  /** El mismo circulo tachado: darle de baja. */
  baja: 'M12 2a10 10 0 1 1 0 20 10 10 0 0 1 0-20Zm0 2a8 8 0 0 0-6.2 13.1L17.1 5.8A8 8 0 0 0 12 4Zm6.2 2.9L6.9 18.2A8 8 0 0 0 18.2 6.9Z',

  panel:
    'M3 4h18a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm1 2v12h5V6H4Zm7 0v12h9V6h-9Z',

  /** Puerta con flecha: cerrar sesion. */
  salir:
    'M10 3a1 1 0 0 1 0 2H6v14h4a1 1 0 0 1 0 2H5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5Zm6.3 4.3 4 4a1 1 0 0 1 0 1.4l-4 4a1 1 0 0 1-1.4-1.4L17.1 13H10a1 1 0 0 1 0-2h7.1l-2.2-2.3a1 1 0 0 1 1.4-1.4Z',

  /** Marco con montana: la ranura del logo vacia. */
  imagen:
    'M3 4h18a1 1 0 0 1 1 1v14a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1Zm1 2v9.6l4.3-4.3a1 1 0 0 1 1.4 0l3 3 2.3-2.3a1 1 0 0 1 1.4 0L20 14.6V6H4Zm4.5 1a1.8 1.8 0 1 1 0 3.6 1.8 1.8 0 0 1 0-3.6Z',

  /** Aspa: quitar el logo cargado. */
  quitar:
    'M6.4 5 12 10.6 17.6 5 19 6.4 13.4 12 19 17.6 17.6 19 12 13.4 6.4 19 5 17.6 10.6 12 5 6.4 6.4 5Z',
};
