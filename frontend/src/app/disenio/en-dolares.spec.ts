import { Component, provideZonelessChangeDetection, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { EnDolares } from './en-dolares';

const CLAVE = 'chaquena.tipoCambio';

@Component({
  imports: [EnDolares],
  template: `<app-en-dolares [soles]="soles()" />`,
})
class Anfitrion {
  readonly soles = signal(100);
}

async function montar(soles = 100) {
  TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  const fijo = TestBed.createComponent(Anfitrion);
  fijo.componentInstance.soles.set(soles);
  await fijo.whenStable();
  fijo.detectChanges();
  return fijo;
}

function texto(fijo: Awaited<ReturnType<typeof montar>>): string {
  return (fijo.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ').trim() ?? '';
}

describe('EnDolares', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  afterEach(() => vi.unstubAllGlobals());

  it('muestra el equivalente con la cotizacion del dia', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          result: 'success',
          time_last_update_unix: Math.floor(Date.now() / 1000),
          rates: { PEN: 4 },
        }),
      }),
    );

    expect(texto(await montar(100))).toContain('US$ 25.00');
  });

  it('sin cotizacion no pinta nada, en vez de un cero', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')));

    expect(texto(await montar(100))).toBe('');
  });

  it('con la comanda vacia tampoco pinta, aunque haya cotizacion', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          result: 'success',
          time_last_update_unix: Math.floor(Date.now() / 1000),
          rates: { PEN: 4 },
        }),
      }),
    );

    expect(texto(await montar(0))).toBe('');
  });

  it('con una cotizacion vieja dice de cuando es, sin recurrir al color', async () => {
    localStorage.setItem(
      CLAVE,
      JSON.stringify({
        solesPorDolar: 4,
        consultadoEn: new Date(Date.now() - 72 * 60 * 60 * 1000).toISOString(),
      }),
    );
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')));

    const escrito = texto(await montar(100));

    expect(escrito).toContain('US$ 25.00');
    // La antiguedad se lee, no solo se ve: hay una fecha en el texto.
    expect(escrito).toMatch(/\d{1,2}[/.-]\d{1,2}/);
  });
});
