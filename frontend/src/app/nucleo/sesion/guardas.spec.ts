import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import {
  Router,
  UrlTree,
  provideRouter,
  type ActivatedRouteSnapshot,
  type RouterStateSnapshot,
} from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { sesionAbierta, sinSesion } from './guardas';
import { SesionService } from './sesion.service';

function tokenCon(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function claims(rol: string, segundos = 3600): Record<string, unknown> {
  return {
    sub: `${rol.toLowerCase()}@chaquena.pe`,
    username: rol.toLowerCase(),
    cargo: rol,
    roles: [rol],
    exp: Math.floor(Date.now() / 1000) + segundos,
  };
}

/** Ejecuta una guarda como lo hace el router, con su contexto de inyeccion. */
function correr(guarda: typeof sinSesion, url = '/entrar'): boolean | UrlTree {
  return TestBed.runInInjectionContext(() =>
    guarda({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot),
  ) as boolean | UrlTree;
}

describe('guardas de sesion', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter([])],
    });
  });

  it('sin sesion deja ver la pantalla de entrar', () => {
    expect(correr(sinSesion)).toBe(true);
  });

  it('con sesion abierta manda al puesto del rol en vez de ensenar el login', () => {
    TestBed.inject(SesionService).abrir(tokenCon(claims('MOZO')));

    const destino = correr(sinSesion);

    expect(destino).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(destino as UrlTree)).toBe('/pos');
  });

  it('una sesion vencida no bloquea la pantalla de entrar', () => {
    TestBed.inject(SesionService).abrir(tokenCon(claims('ADMIN', -60)));

    expect(correr(sinSesion)).toBe(true);
  });

  it('sin sesion, una ruta protegida devuelve al login guardando a donde iba', () => {
    const destino = correr(sesionAbierta, '/caja');

    expect(TestBed.inject(Router).serializeUrl(destino as UrlTree)).toBe('/entrar?volverA=%2Fcaja');
  });
});
