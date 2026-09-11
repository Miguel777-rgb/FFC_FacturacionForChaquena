import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { TrastiendaPage } from './trastienda.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

/** JWT con firma falsa: el frontend decodifica pero nunca verifica. */
function tokenCon(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

const EN_UNA_HORA = Math.floor(Date.now() / 1000) + 3600;

function tokenDe(cargo: string, roles: string[]): string {
  return tokenCon({
    sub: 'quien@chaquena.pe',
    username: 'quien',
    nombres: 'Miguel',
    apellidos: 'Flores',
    cargo,
    roles,
    exp: EN_UNA_HORA,
  });
}

/** Los rotulos que se ven, en el orden en que se pintan. */
function pestanasVisibles(): string[] {
  const fixture = TestBed.createComponent(TrastiendaPage);
  fixture.detectChanges();
  return Array.from(fixture.nativeElement.querySelectorAll('.pestanas button')).map((b) =>
    (b as HTMLElement).textContent!.trim(),
  );
}

/**
 * La trastienda decide que ensena a partir del rol, y ese calculo es el unico
 * sitio donde una pestana puede desaparecer sin que nada falle: no hay error,
 * ni peticion, ni consola; simplemente no esta. Por eso se prueba aqui y no a
 * ojo en el navegador, donde una pestana ausente y un bundle viejo se ven
 * exactamente igual.
 */
describe('TrastiendaPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  it('le ensena al administrador las cinco secciones', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));

    expect(pestanasVisibles()).toEqual(['Inventario', 'Carta', 'Local', 'Bots', 'Eventos']);
  });

  it('al almacenero solo le ensena inventario y carta', () => {
    // Las otras tres piden ADMIN en el servidor: ensenarselas seria regalarle
    // un 403 en mitad del turno.
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));

    expect(pestanasVisibles()).toEqual(['Inventario', 'Carta']);
  });

  it('el personal y los indicadores ya no viven aqui', () => {
    // Se fueron a su propia superficie. Si alguien los devolviera a una pestana,
    // esta prueba lo dice antes de que el panel lateral quede con dos entradas
    // que llevan al mismo sitio.
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));

    expect(pestanasVisibles()).not.toContain('Personal');
    expect(pestanasVisibles()).not.toContain('Tablero');
  });

  it('abre por la primera pestana a la que cada uno tiene derecho', () => {
    // El almacenero no puede aterrizar en el tablero, que es de ADMIN, ni el
    // administrador en otra cosa que no sea la primera de su lista.
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));
    const almacen = TestBed.createComponent(TrastiendaPage);
    almacen.detectChanges();
    expect(almacen.nativeElement.querySelector('.cabecera h1').textContent.trim()).toBe(
      'Inventario',
    );
  });
});
