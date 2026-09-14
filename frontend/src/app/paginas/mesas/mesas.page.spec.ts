import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { MesasPage } from './mesas.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = { sub: 'quien@chaquena.pe', username: 'quien', cargo, roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function crear(cargo: string, roles: string[]) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
    ],
  });
  TestBed.inject(SesionService).abrir(tokenDe(cargo, roles));
  const fixture = TestBed.createComponent(MesasPage);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController) };
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

describe('MesasPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('agrupa por zona, deja las mesas sin zona al final y dice cuanto lleva la ocupada', () => {
    const { fixture, http } = crear('MOZO', ['MOZO']);

    http.expectOne((r) => r.url.endsWith('/api/v1/mesas')).flush([
      { id: 'm10', numero: '10', zona: 'Terraza', estado: 'LIBRE', capacidad: 4 },
      { id: 'm2', numero: '2', zona: 'Terraza', estado: 'OCUPADA', capacidad: 2 },
      { id: 'm1', numero: '1', zona: 'Salón', estado: 'LIBRE', capacidad: 4 },
      { id: 'm9', numero: '9', estado: 'INHABILITADA' },
    ]);
    http
      .expectOne((r) => r.url.endsWith('/api/v1/ordenes/activas'))
      .flush([{ id: 'o1', mesaId: 'm2', montoTotal: 45.5, minutosTranscurridos: 20 }]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '.bloque > h2')).toEqual(['Salón 1 mesa', 'Terraza 2 mesas', 'Sin zona 1 mesa']);
    // La 2 antes que la 10, aunque el numero llegue como texto.
    expect(textos(pagina, '.bloque:nth-of-type(2) .numero')).toEqual(['2', '10']);
    expect(textos(pagina, '.mesa[data-estado="OCUPADA"] .consumo span')).toEqual(['S/ 45.50', '20 min']);
    // Dar de alta una mesa es del administrador.
    expect(textos(pagina, '.cabecera button')).not.toContain('Nueva mesa');
    http.verify();
  });

  it('sin mesas lo dice, y al administrador le ofrece la primera', () => {
    const { fixture, http } = crear('ADMINISTRADOR', ['ADMIN']);

    http.expectOne((r) => r.url.endsWith('/api/v1/mesas')).flush([]);
    http.expectOne((r) => r.url.endsWith('/api/v1/ordenes/activas')).flush([]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '.seccion > .vacio')).toEqual(['Todavía no hay mesas registradas.']);
    expect(textos(pagina, '.cabecera button')).toContain('Nueva mesa');
    http.verify();
  });
});
