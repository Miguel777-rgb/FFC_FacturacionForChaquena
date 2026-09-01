import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { SesionService } from './sesion.service';

/**
 * Arma un JWT con la firma falsa. Vale porque el frontend nunca la verifica:
 * el backend firma con HMAC simetrico y el secreto no vive en el navegador.
 */
function tokenCon(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

const EN_UNA_HORA = Math.floor(Date.now() / 1000) + 3600;

/** Claims tal como los emite AuthServiceImpl.generarJwtParaTrabajador. */
const CLAIMS_ADMIN = {
  sub: 'admin@chaquena.pe',
  username: 'admin',
  nombres: 'Ada',
  apellidos: 'Lovelace',
  cargo: 'ADMINISTRADOR',
  roles: ['ADMIN'],
  permisos: ['CAJA_LEER', 'INVENTARIO_ESCRIBIR'],
  exp: EN_UNA_HORA,
};

describe('SesionService', () => {
  let sesion: SesionService;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    sesion = TestBed.inject(SesionService);
  });

  it('arranca sin sesion', () => {
    expect(sesion.autenticado()).toBe(false);
    expect(sesion.tokenActual()).toBeUndefined();
  });

  it('toma el rol del claim roles, no del cargo', () => {
    // El cargo se llama ADMINISTRADOR pero el `@PreAuthorize` pide ADMIN:
    // el puente lo hace cargo_roles, que llega en el claim `roles`.
    sesion.abrir(tokenCon(CLAIMS_ADMIN));

    expect(sesion.roles()).toEqual(['ADMIN']);
    expect(sesion.sesion()?.cargo).toBe('ADMINISTRADOR');
  });

  it('deja sin roles a un cargo que no tiene rol asociado', () => {
    // "Cocinero de Almacen" existe en la base sembrada y no mapea a ningun rol.
    sesion.abrir(tokenCon({ ...CLAIMS_ADMIN, cargo: 'Cocinero de Almacen', roles: [] }));

    expect(sesion.roles()).toEqual([]);
    expect(sesion.tieneAlgunRol(['ADMIN'])).toBe(false);
  });

  it('acepta el cargo como rol cuando coincide con uno conocido', () => {
    sesion.abrir(tokenCon({ ...CLAIMS_ADMIN, cargo: 'MOZO', roles: ['MOZO'] }));

    expect(sesion.roles()).toEqual(['MOZO']); // sin duplicar
  });

  it('normaliza igual que el backend: espacios y guiones a guion bajo', () => {
    sesion.abrir(tokenCon({ ...CLAIMS_ADMIN, cargo: 'x', roles: ['en preparacion', 'admin'] }));

    // "en preparacion" no es un rol conocido y se descarta; "admin" si lo es.
    expect(sesion.roles()).toEqual(['ADMIN']);
  });

  it('prefiere el nombre del token cuando no le pasan uno', () => {
    sesion.abrir(tokenCon(CLAIMS_ADMIN));
    expect(sesion.nombre()).toBe('Ada Lovelace');
  });

  it('rechaza un token que no es un JWT', () => {
    expect(() => sesion.abrir('esto-no-es-un-token')).toThrow();
    expect(sesion.autenticado()).toBe(false);
  });

  it('considera expirada una sesion cuyo exp ya paso', () => {
    sesion.abrir(tokenCon({ ...CLAIMS_ADMIN, exp: Math.floor(Date.now() / 1000) - 10 }));
    expect(sesion.expirada()).toBe(true);
  });

  it('no restaura del almacenamiento una sesion ya vencida', () => {
    sesion.abrir(tokenCon({ ...CLAIMS_ADMIN, exp: Math.floor(Date.now() / 1000) - 10 }));

    // Un servicio nuevo lee lo que quedo guardado, como al recargar la pagina.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    expect(TestBed.inject(SesionService).autenticado()).toBe(false);
  });

  it('restaura una sesion vigente al recargar', () => {
    sesion.abrir(tokenCon(CLAIMS_ADMIN));

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const recargada = TestBed.inject(SesionService);

    expect(recargada.autenticado()).toBe(true);
    expect(recargada.roles()).toEqual(['ADMIN']);
  });

  it('cerrar borra la sesion y el almacenamiento', () => {
    sesion.abrir(tokenCon(CLAIMS_ADMIN));
    sesion.cerrar();

    expect(sesion.autenticado()).toBe(false);
    expect(sesion.tokenActual()).toBeUndefined();
    expect(sessionStorage.getItem('chaquena.sesion')).toBeNull();
  });

  it('tieneAlgunRol con lista vacia deja pasar a cualquiera', () => {
    sesion.abrir(tokenCon(CLAIMS_ADMIN));
    expect(sesion.tieneAlgunRol([])).toBe(true);
  });
});
