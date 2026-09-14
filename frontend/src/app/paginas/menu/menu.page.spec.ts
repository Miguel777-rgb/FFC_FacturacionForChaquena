import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { MenuPage } from './menu.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = { sub: 'quien@chaquena.pe', username: 'quien', cargo, roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function crear() {
  const fixture = TestBed.createComponent(MenuPage);
  fixture.detectChanges();
  return fixture;
}

describe('MenuPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('reparte el menu en platillos, complementos y promociones, y abre por los platillos', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));
    const fixture = crear();
    const pagina: HTMLElement = fixture.nativeElement;

    const pestanas = Array.from(pagina.querySelectorAll('.pestanas button')).map((b) =>
      b.textContent!.trim(),
    );
    expect(pestanas).toEqual(['Platillos', 'Complementos', 'Promociones']);
    expect(pagina.querySelector('app-carta-seccion')).not.toBeNull();
    expect(pagina.querySelector('app-promociones-seccion')).toBeNull();
  });

  it('al almacen le ensena las promociones sin dejarle crearlas', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));
    const fixture = crear();
    const pagina: HTMLElement = fixture.nativeElement;

    (pagina.querySelectorAll('.pestanas button')[2] as HTMLButtonElement).click();
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .match((r) => r.url.endsWith('/api/v1/promociones'))
      .forEach((r) => r.flush([]));
    fixture.detectChanges();

    expect(pagina.textContent).toContain('Solo un administrador puede crear o pausar promociones.');
    expect(pagina.textContent).not.toContain('Nueva promoción');
    expect(pagina.querySelector('app-carta-seccion')).toBeNull();
  });
});
