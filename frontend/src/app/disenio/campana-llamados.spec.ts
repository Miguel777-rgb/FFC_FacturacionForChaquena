import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { beforeEach, describe, expect, it } from 'vitest';

import { CampanaLlamados } from './campana-llamados';

describe('CampanaLlamados', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  it('arranca con sonido y, al pulsarla, lo apaga, lo dice y lo recuerda', async () => {
    const fixture = TestBed.createComponent(CampanaLlamados);
    await fixture.whenStable();
    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;

    // El nombre no cambia: el estado va en aria-pressed.
    expect(boton.getAttribute('aria-label')).toBe('Sonido de los llamados de cocina');
    expect(boton.getAttribute('aria-pressed')).toBe('true');

    boton.click();
    await fixture.whenStable();

    expect(boton.getAttribute('aria-label')).toBe('Sonido de los llamados de cocina');
    expect(boton.getAttribute('aria-pressed')).toBe('false');
    expect(boton.getAttribute('title')).toBe('Activar el sonido de los llamados de cocina');
    expect(localStorage.getItem('ffc.llamados.silencio')).toBe('1');
  });

  it('respeta lo que el mozo eligio la vez anterior en este dispositivo', async () => {
    localStorage.setItem('ffc.llamados.silencio', '1');
    const fixture = TestBed.createComponent(CampanaLlamados);
    await fixture.whenStable();

    const boton = fixture.nativeElement.querySelector('button') as HTMLButtonElement;
    expect(boton.getAttribute('aria-pressed')).toBe('false');
  });
});
