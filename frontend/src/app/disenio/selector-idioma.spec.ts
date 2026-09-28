import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { SelectorIdioma } from './selector-idioma';
import { I18nService } from '../nucleo/i18n/i18n.service';

/**
 * El selector es la unica via para cambiar de idioma. Si deja de abrirse, de
 * marcar cual esta en uso o de responder al teclado, los otros dos quedan
 * inalcanzables sin que falle nada: no hay error, ni peticion, ni consola. Y
 * como ya no es un `<select>` nativo, el teclado que el navegador regalaba
 * ahora es codigo propio: por eso se prueba tecla a tecla.
 */
describe('SelectorIdioma', () => {
  let fixture: ComponentFixture<SelectorIdioma>;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    fixture = TestBed.createComponent(SelectorIdioma);
    fixture.detectChanges();
  });

  const boton = (): HTMLButtonElement => fixture.nativeElement.querySelector('button');
  const lista = (): HTMLUListElement => fixture.nativeElement.querySelector('[role=listbox]');
  const opciones = (): HTMLLIElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('[role=option]'));
  const abierto = (): boolean => boton().getAttribute('aria-expanded') === 'true';
  const activa = (): string | undefined =>
    opciones().find((o) => o.id === boton().getAttribute('aria-activedescendant'))?.lang;

  function tecla(key: string): void {
    boton().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  /** El diccionario se descarga al elegirlo: hay que dejar correr la promesa. */
  async function esperarIdioma(): Promise<void> {
    await Promise.resolve();
    await new Promise((sigue) => setTimeout(sigue));
    fixture.detectChanges();
  }

  it('ofrece los tres idiomas, cada uno en su propia lengua y con su bandera', () => {
    expect(opciones().map((o) => o.lang)).toEqual(['es', 'en', 'pt']);
    expect(opciones().map((o) => o.textContent?.trim())).toEqual([
      'Español',
      'English',
      'Português',
    ]);
    // La bandera acompana, nunca sustituye: cada opcion lleva la suya y su nombre.
    expect(opciones().every((o) => o.querySelector('app-bandera svg'))).toBe(true);
  });

  it('dice para que sirve y que idioma esta en uso', () => {
    expect(boton().getAttribute('role')).toBe('combobox');
    expect(boton().getAttribute('aria-label')).toBe('Idioma de la interfaz: Español');
    expect(boton().textContent?.trim()).toBe('Español');
    expect(abierto()).toBe(false);
    expect(opciones().find((o) => o.getAttribute('aria-selected') === 'true')?.lang).toBe('es');
  });

  it('se abre con un clic y cambia de idioma al elegir otra opcion', async () => {
    const i18n = TestBed.inject(I18nService);

    boton().click();
    fixture.detectChanges();
    expect(abierto()).toBe(true);
    expect(lista().classList).toContain('abierta');

    opciones()[2].click();
    await esperarIdioma();

    expect(i18n.idioma()).toBe('pt');
    expect(abierto()).toBe(false);
    expect(boton().getAttribute('aria-label')).toBe('Idioma da interface: Português');
  });

  it('con el teclado: la flecha abre, las flechas recorren y Enter elige', async () => {
    const i18n = TestBed.inject(I18nService);

    tecla('ArrowDown');
    // Abrir no mueve: se ve la lista con el idioma en uso como opcion activa.
    expect(abierto()).toBe(true);
    expect(activa()).toBe('es');

    tecla('ArrowDown');
    expect(activa()).toBe('en');
    tecla('End');
    expect(activa()).toBe('pt');
    tecla('ArrowDown');
    expect(activa()).toBe('pt');
    tecla('Home');
    expect(activa()).toBe('es');
    tecla('ArrowDown');

    tecla('Enter');
    await esperarIdioma();

    expect(i18n.idioma()).toBe('en');
    expect(abierto()).toBe(false);
  });

  it('Escape cierra sin cambiar de idioma y deja el foco en el boton', () => {
    const i18n = TestBed.inject(I18nService);
    boton().focus();

    tecla('ArrowDown');
    tecla('ArrowDown');
    tecla('Escape');

    expect(abierto()).toBe(false);
    expect(i18n.idioma()).toBe('es');
    expect(document.activeElement).toBe(boton());
  });

  it('la inicial salta entre los idiomas que la comparten', () => {
    tecla('e');
    expect(abierto()).toBe(true);
    expect(activa()).toBe('en');
    tecla('e');
    expect(activa()).toBe('es');
    tecla('p');
    expect(activa()).toBe('pt');
  });

  it('un toque fuera cierra sin elegir', () => {
    const i18n = TestBed.inject(I18nService);
    boton().click();
    fixture.detectChanges();

    document.body.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    fixture.detectChanges();

    expect(abierto()).toBe(false);
    expect(i18n.idioma()).toBe('es');
  });

  it('compacto deja solo la bandera, y el nombre sigue en el rotulo', () => {
    fixture.componentRef.setInput('compacto', true);
    fixture.detectChanges();

    expect(boton().querySelector('.nombre')).toBeNull();
    expect(boton().querySelector('app-bandera')).not.toBeNull();
    expect(boton().getAttribute('aria-label')).toBe('Idioma de la interfaz: Español');
  });
});
