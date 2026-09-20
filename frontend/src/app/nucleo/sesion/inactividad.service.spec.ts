import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { Router, provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { AVISO_MS, InactividadService, LIMITE_MS } from './inactividad.service';
import { SesionService } from './sesion.service';

function tokenDeAdmin(): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'admin@chaquena.pe',
    username: 'admin',
    cargo: 'ADMINISTRADOR',
    roles: ['ADMIN'],
    exp: Math.floor(Date.now() / 1000) + 24 * 3600,
  };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

/** Rutas de mentira: solo hace falta que el router reconozca las dos urls. */
const RUTAS = [
  { path: 'tablero', children: [] },
  { path: 'kds', children: [] },
  { path: 'entrar', children: [] },
];

/** Deja pasar el tiempo como lo veria el navegador: reloj y temporizadores juntos. */
function pasan(ms: number): void {
  vi.advanceTimersByTime(ms);
}

describe('InactividadService', () => {
  let servicio: InactividadService;
  let sesion: SesionService;

  beforeEach(async () => {
    vi.useFakeTimers();
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter(RUTAS)],
    });
    sesion = TestBed.inject(SesionService);
    sesion.abrir(tokenDeAdmin());
    await TestBed.inject(Router).navigateByUrl('/tablero');
    servicio = TestBed.inject(InactividadService);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('con la sesion recien abierta no avisa', () => {
    expect(servicio.avisando()).toBeNull();
  });

  it('avisa en los ultimos cinco minutos y cierra la sesion al cumplirse la media hora', () => {
    pasan(LIMITE_MS - AVISO_MS + 1000);
    expect(servicio.avisando()).toBeLessThanOrEqual(AVISO_MS / 1000);
    expect(sesion.autenticado()).toBe(true);

    pasan(AVISO_MS);

    expect(sesion.autenticado()).toBe(false);
  });

  it('tocar la pantalla reinicia la cuenta', () => {
    pasan(LIMITE_MS - 60_000);
    expect(servicio.avisando()).not.toBeNull();

    document.dispatchEvent(new Event('pointerdown'));
    pasan(1000);

    expect(servicio.avisando()).toBeNull();
    pasan(LIMITE_MS - AVISO_MS);
    expect(sesion.autenticado()).toBe(true);
  });

  it('el boton del aviso vale como actividad', () => {
    pasan(LIMITE_MS - 30_000);
    servicio.sigoAqui();
    pasan(60_000);

    expect(sesion.autenticado()).toBe(true);
  });

  it('la cola de cocina no se cierra: es un panel de pared', async () => {
    await TestBed.inject(Router).navigateByUrl('/kds');

    pasan(LIMITE_MS * 2);

    expect(servicio.avisando()).toBeNull();
    expect(sesion.autenticado()).toBe(true);
  });
});
