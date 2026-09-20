import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { SelectorIdioma } from './selector-idioma';
import { I18nService } from '../nucleo/i18n/i18n.service';

/**
 * El desplegable es la unica via para cambiar de idioma. Si desaparece o deja
 * de marcar cual esta en uso, los otros dos quedan inalcanzables sin que falle
 * nada: no hay error, ni peticion, ni consola. Por eso se prueba aqui.
 */
describe('SelectorIdioma', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  function desplegable(): HTMLSelectElement {
    const fixture = TestBed.createComponent(SelectorIdioma);
    fixture.detectChanges();
    return fixture.nativeElement.querySelector('select');
  }

  it('ofrece los tres idiomas, cada uno escrito en su propia lengua', () => {
    const opciones = Array.from(desplegable().options);

    expect(opciones.map((o) => o.value)).toEqual(['es', 'en', 'pt']);
    expect(opciones.map((o) => o.textContent?.trim())).toEqual(['Español', 'English', 'Português']);
  });

  it('marca el idioma en uso y lleva un rotulo que dice para que sirve', () => {
    const fixture = TestBed.createComponent(SelectorIdioma);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');
    const rotulo: HTMLLabelElement = fixture.nativeElement.querySelector('label');

    expect(select.value).toBe('es');
    // El rotulo esta oculto a la vista pero no al lector de pantalla, y apunta
    // al control: una lista de idiomas sin rotulo no dice que es.
    expect(rotulo.htmlFor).toBe(select.id);
    expect(rotulo.textContent?.trim()).toBe('Idioma de la interfaz');
  });

  it('cambia de idioma al elegir otra opcion', async () => {
    const i18n = TestBed.inject(I18nService);
    const fixture = TestBed.createComponent(SelectorIdioma);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');

    select.value = 'pt';
    select.dispatchEvent(new Event('change'));
    // El diccionario se descarga al elegirlo, asi que hay que dejar correr la
    // promesa antes de mirar.
    await Promise.resolve();
    await new Promise((sigue) => setTimeout(sigue));
    fixture.detectChanges();

    expect(i18n.idioma()).toBe('pt');
    expect(select.value).toBe('pt');
  });

  it('ignora un valor que no sea uno de los tres idiomas', async () => {
    const i18n = TestBed.inject(I18nService);
    const fixture = TestBed.createComponent(SelectorIdioma);
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');

    select.insertAdjacentHTML('beforeend', '<option value="klingon">Klingon</option>');
    select.value = 'klingon';
    select.dispatchEvent(new Event('change'));
    await new Promise((sigue) => setTimeout(sigue));

    expect(i18n.idioma()).toBe('es');
  });
});
