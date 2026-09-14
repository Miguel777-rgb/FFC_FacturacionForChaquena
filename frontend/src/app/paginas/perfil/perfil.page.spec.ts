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

  TestBed.inject(HttpTestingController)
    .expectOne((r) => r.url.endsWith('/api/v1/trabajadores/activos'))
    .flush([{ username: 'quien', dni: '44556677', celular: '987654321' }]);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('PerfilPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('a quien no administra le dice a quien pedir la contrasena y como vincular Discord', () => {
    const pagina = crear('MOZO', ['MOZO']);

    expect(pagina.textContent).toContain('pídeselo a un administrador');
    expect(pagina.querySelector('code.comando')!.textContent).toBe('/vincular correo:quien@chaquena.pe');
    // El documento no viene en el token: sale de /trabajadores/activos.
    expect(pagina.textContent).toContain('44556677');
  });

  it('al administrador lo lleva a personal y no le ofrece vincularse', () => {
    const pagina = crear('ADMINISTRADOR', ['ADMIN']);

    expect(pagina.querySelector('a[href="/personal"]')).not.toBeNull();
    expect(pagina.querySelector('code.comando')).toBeNull();
    expect(pagina.textContent).toContain('Las cuentas de administrador no se vinculan con los bots.');
  });
});
