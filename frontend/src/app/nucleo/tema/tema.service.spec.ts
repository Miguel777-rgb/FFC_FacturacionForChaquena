import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { TemaService } from './tema.service';

/**
 * El tema no falla con un error: si deja de escribir `data-tema`, la interfaz
 * simplemente sigue en claro y nadie sabe por que el boton no hace nada.
 */
describe('TemaService', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.removeAttribute('data-tema');
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  it('sin eleccion guardada deja mandar al sistema', () => {
    const tema = TestBed.inject(TemaService);

    expect(tema.tema()).toBe('sistema');
    expect(document.documentElement.hasAttribute('data-tema')).toBe(false);
  });

  it('elegir oscuro escribe el atributo y lo recuerda', () => {
    TestBed.inject(TemaService).elegir('oscuro');

    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
    expect(localStorage.getItem('chaquena.tema')).toBe('oscuro');
  });

  it('alternar recorre sistema, claro y oscuro, y vuelve a empezar', () => {
    const tema = TestBed.inject(TemaService);
    const visto: string[] = [];

    for (let i = 0; i < 3; i++) {
      tema.alternar();
      visto.push(tema.tema());
    }

    expect(visto).toEqual(['claro', 'oscuro', 'sistema']);
    expect(document.documentElement.hasAttribute('data-tema')).toBe(false);
  });

  it('al recargar aplica el tema guardado antes de que nadie lo toque', () => {
    localStorage.setItem('chaquena.tema', 'claro');

    TestBed.inject(TemaService);

    expect(document.documentElement.getAttribute('data-tema')).toBe('claro');
  });
});
