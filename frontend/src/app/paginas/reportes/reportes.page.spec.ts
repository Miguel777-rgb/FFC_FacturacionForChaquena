import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { ReportesPage } from './reportes.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = { sub: 'quien@chaquena.pe', username: 'quien', cargo, roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function crear(cargo: string, roles: string[]) {
  TestBed.configureTestingModule({
    providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
  });
  TestBed.inject(SesionService).abrir(tokenDe(cargo, roles));
  const fixture = TestBed.createComponent(ReportesPage);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController) };
}

function pestanas(raiz: HTMLElement): string[] {
  return Array.from(raiz.querySelectorAll('.pestanas button')).map((b) => b.textContent!.trim());
}

describe('ReportesPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('a la caja no le pide la satisfaccion ni le ofrece esa pestana', () => {
    // El endpoint es solo de ADMIN: pedirlo desde caja seria un 403 en cada carga.
    const { fixture, http } = crear('CAJERO', ['CAJA']);

    const urls = http.match(() => true).map((r) => r.request.url);
    expect(urls.some((u) => u.includes('/reportes/satisfaccion'))).toBe(false);
    expect(pestanas(fixture.nativeElement)).toEqual(['Resumen', 'Por mozo']);
  });

  it('el administrador ve las tres pestanas y la satisfaccion del mismo rango', () => {
    const { fixture, http } = crear('ADMINISTRADOR', ['ADMIN']);

    const pendientes = http.match(() => true);
    const tablero = pendientes.find((r) => r.request.url.includes('/reportes/tablero'))!;
    const satisfaccion = pendientes.find((r) => r.request.url.includes('/reportes/satisfaccion'))!;

    expect(pestanas(fixture.nativeElement)).toHaveLength(3);
    expect(satisfaccion.request.params.get('desde')).toBe(tablero.request.params.get('desde'));
  });
});
