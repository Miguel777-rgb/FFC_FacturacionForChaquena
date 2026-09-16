import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { PerfilPage } from './perfil.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'quien@chaquena.pe',
    username: 'quien',
    nombres: 'Rosa',
    apellidos: 'Quispe',
    cargo,
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
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
  const fixture = TestBed.createComponent(PerfilPage);
  fixture.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  http
    .expectOne((r) => r.url.endsWith('/api/v1/trabajadores/activos'))
    .flush([{ username: 'quien', dni: '44556677', celular: '987654321' }]);
  http
    .match((r) => r.url.endsWith('/api/v1/asistencia/mia'))
    .forEach((r) =>
      r.flush({
        dentro: false,
        turnos: [
          {
            id: 't1',
            trabajadorId: 'yo',
            fecha: '2026-09-14',
            inicio: '12:00:00',
            fin: '20:00:00',
          },
          {
            id: 't2',
            trabajadorId: 'yo',
            fecha: '2026-09-15',
            inicio: '18:00:00',
            fin: '02:00:00',
            terminaAlDiaSiguiente: true,
          },
        ],
      }),
    );
  fixture.detectChanges();
  return { fixture, http, pagina: fixture.nativeElement as HTMLElement };
}

function botonCon(raiz: HTMLElement, texto: string): HTMLButtonElement | undefined {
  return Array.from(raiz.querySelectorAll('button')).find((b) => b.textContent!.includes(texto));
}

describe('PerfilPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('a quien no administra le dice a quien pedir la contrasena y como vincular Discord', () => {
    const { pagina } = crear('MOZO', ['MOZO']);

    expect(pagina.textContent).toContain('pídeselo a un administrador');
    expect(pagina.querySelector('code.comando')!.textContent).toBe(
      '/vincular correo:quien@chaquena.pe',
    );
    // El documento no viene en el token: sale de /trabajadores/activos.
    expect(pagina.textContent).toContain('44556677');
  });

  it('al administrador lo lleva a personal y no le ofrece vincularse', () => {
    const { pagina } = crear('ADMINISTRADOR', ['ADMIN']);

    expect(pagina.querySelector('a[href="/personal"]')).not.toBeNull();
    expect(pagina.querySelector('code.comando')).toBeNull();
    expect(pagina.textContent).toContain(
      'Las cuentas de administrador no se vinculan con los bots.',
    );
  });

  it('muestra mis turnos y marca mi entrada, sin decir de quien: lo sabe el servidor', () => {
    const { fixture, http, pagina } = crear('MOZO', ['MOZO']);

    const turnos = Array.from(pagina.querySelectorAll('.mis-turnos li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(turnos[0]).toContain('12:00 – 20:00');
    expect(turnos[1]).toContain('18:00 – 02:00');
    expect(turnos[1]).toContain('hasta el día siguiente');

    botonCon(pagina, 'Marcar entrada')!.click();
    const entrada = http.expectOne(
      (r) => r.method === 'POST' && r.url.endsWith('/api/v1/asistencia/entrada'),
    );
    entrada.flush({ dentro: true, entrada: '2026-09-14T12:03:00-05:00', turnos: [] });
    fixture.detectChanges();

    expect(botonCon(pagina, 'Marcar salida')).toBeTruthy();
    expect(pagina.querySelector('.estado-asistencia .chip')!.textContent!.trim()).toBe('Dentro');
  });
});
