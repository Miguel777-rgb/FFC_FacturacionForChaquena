import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { BarraIdiomas } from './barra-idiomas';
import { I18nService } from '../nucleo/i18n/i18n.service';

/**
 * La barra es la unica via para cambiar de idioma. Si desaparece o deja de
 * marcar cual esta en uso, los otros dos idiomas quedan inalcanzables sin que
 * falle nada: no hay error, ni peticion, ni consola. Por eso se prueba aqui.
 */
describe('BarraIdiomas', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  function banderas(): HTMLButtonElement[] {
    const fixture = TestBed.createComponent(BarraIdiomas);
    fixture.detectChanges();
    return Array.from(fixture.nativeElement.querySelectorAll('button.bandera'));
  }

  it('pinta las tres banderas, cada una con el nombre de su idioma', () => {
    const botones = banderas();

    expect(botones.length).toBe(3);
    // Una bandera no es un idioma: el nombre accesible dice «Portugues», no
    // «Brasil», y es lo que lee un lector de pantalla.
    expect(botones.map((b) => b.getAttribute('aria-label'))).toEqual([
      'Español',
      'English',
      'Português',
    ]);
    expect(botones.every((b) => b.querySelector('svg') !== null)).toBe(true);
  });

  it('marca el idioma en uso y no los otros', () => {
    const botones = banderas();

    expect(botones.map((b) => b.getAttribute('aria-pressed'))).toEqual(['true', 'false', 'false']);
    expect(botones[0].classList.contains('activo')).toBe(true);
  });

  it('cambia de idioma al pulsar una bandera', async () => {
    const i18n = TestBed.inject(I18nService);
    const fixture = TestBed.createComponent(BarraIdiomas);
    fixture.detectChanges();

    const botones: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button.bandera'),
    );
    botones[2].click();
    // El diccionario se descarga al elegirlo, asi que hay que dejar correr la
    // promesa antes de mirar.
    await Promise.resolve();
    await new Promise((sigue) => setTimeout(sigue));
    fixture.detectChanges();

    expect(i18n.idioma()).toBe('pt');
    expect(botones[2].classList.contains('activo')).toBe(true);
    expect(botones[0].classList.contains('activo')).toBe(false);
  });
});
