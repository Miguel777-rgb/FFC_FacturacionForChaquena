/**
 * Los seis roles que el backend reconoce. Salen de los `@PreAuthorize` de los
 * controladores, donde aparecen siempre como `hasRole('ADMIN')` o
 * `hasAnyRole('ADMIN','MOZO',...)`.
 *
 * Esta lista existe para que el enrutado y los menus sepan a quien mostrar
 * que. La autorizacion de verdad la hace el servidor: aqui solo se decide
 * que pintar.
 */
export const ROLES = ['ADMIN', 'MOZO', 'CAJA', 'COCINA', 'ALMACEN', 'DELIVERY'] as const;

export type Rol = (typeof ROLES)[number];

export function esRol(valor: string): valor is Rol {
  return (ROLES as readonly string[]).includes(valor);
}

/**
 * Copia exacta de `JwtAuthenticationFilter.normalizar` del backend:
 * recorta, pasa a mayusculas y convierte espacios y guiones en guion bajo.
 * Sin esto, un cargo guardado como "Jefe de Cocina" no casaria con nada.
 *
 * Se replica a proposito en lugar de mejorarla: si aqui se quitaran acentos y
 * alli no, el frontend creeria tener un permiso que el servidor le va a negar,
 * que es peor que no tenerlo.
 */
export function normalizarRol(valor: string): string {
  return valor.trim().toUpperCase().replaceAll(' ', '_').replaceAll('-', '_');
}

/**
 * Superficie a la que entra cada rol al iniciar sesion.
 *
 * El administrador aterriza en el POS y no en la trastienda porque es quien
 * recorre el flujo principal entero por si solo, y ese recorrido empieza
 * tomando una comanda. La trastienda, ademas, sigue siendo un marcador.
 */
export const INICIO_POR_ROL: Record<Rol, string> = {
  ADMIN: '/pos',
  MOZO: '/pos',
  CAJA: '/caja',
  COCINA: '/kds',
  ALMACEN: '/trastienda',
  DELIVERY: '/despacho',
};
