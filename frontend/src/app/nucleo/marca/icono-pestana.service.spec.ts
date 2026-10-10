import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection, signal } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { IconoPestanaService } from './icono-pestana.service';
import { LogoService } from './logo.service';

/**
 * El icono no falla con un error: si deja de seguir al logo, la pestana se
 * queda con el de la aplicacion; si no vuelve al salir, la pantalla de entrar
 * muestra el logo de la sesion anterior.
 */
describe('IconoPestanaService', () => {
  const logo = signal<string | null>(null);
  let enlace: HTMLLinkElement;

  beforeEach(() => {
    logo.set(null);
    enlace = document.createElement('link');
    enlace.rel = 'icon';
    enlace.setAttribute('href', 'chaquena.ico');
    document.head.append(enlace);
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), { provide: LogoService, useValue: { logo } }],
    });
  });

  afterEach(() => enlace.remove());

  it('sin logo deja el icono que trae la aplicacion', () => {
    TestBed.inject(IconoPestanaService);
    TestBed.tick();

    expect(enlace.getAttribute('href')).toBe('chaquena.ico');
  });

  it('al llegar el logo del local lo pone en la pestana', () => {
    TestBed.inject(IconoPestanaService);
    TestBed.tick();

    logo.set('/api/v1/archivos/6142ff68');
    TestBed.tick();

    expect(enlace.getAttribute('href')).toBe('/api/v1/archivos/6142ff68');
  });

  it('al cerrar la sesion vuelve el icono propio', () => {
    logo.set('/api/v1/archivos/6142ff68');
    TestBed.inject(IconoPestanaService);
    TestBed.tick();

    logo.set(null);
    TestBed.tick();

    expect(enlace.getAttribute('href')).toBe('chaquena.ico');
  });
});
