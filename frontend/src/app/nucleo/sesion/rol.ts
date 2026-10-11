/** Los seis roles de los `@PreAuthorize` del backend; aqui solo deciden que pintar. */
export const ROLES = ['ADMIN', 'MOZO', 'CAJA', 'COCINA', 'ALMACEN', 'DELIVERY'] as const;

export type Rol = (typeof ROLES)[number];

export function esRol(valor: string): valor is Rol {
  return (ROLES as readonly string[]).includes(valor);
}

/**
 * Copia exacta de `AutoridadesDelToken.normalizar`, sin mejorarla: si aqui se quitaran acentos y alli no,
 * el front creeria tener un permiso que el servidor le va a negar.
 */
export function normalizarRol(valor: string): string {
  return valor.trim().toUpperCase().replaceAll(' ', '_').replaceAll('-', '_');
}

/** Administrador y caja entran por el tablero, a ver como va el dia; el resto, directo a su puesto. */
export const INICIO_POR_ROL: Record<Rol, string> = {
  ADMIN: '/tablero',
  MOZO: '/pos',
  CAJA: '/tablero',
  COCINA: '/kds',
  ALMACEN: '/inventario',
  DELIVERY: '/despacho',
};
