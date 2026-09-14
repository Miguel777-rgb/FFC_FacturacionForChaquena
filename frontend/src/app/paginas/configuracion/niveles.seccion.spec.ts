import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { NivelesSeccion } from './niveles.seccion';

function crear(niveles: object[]) {
  const fixture = TestBed.createComponent(NivelesSeccion);
  fixture.detectChanges();
  TestBed.inject(HttpTestingController)
    .expectOne((r) => r.url.endsWith('/api/v1/niveles-lealtad'))
    .flush(niveles);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

describe('NivelesSeccion', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('dice el tramo de puntos de cada nivel, con el mas alto sin techo', () => {
    // Llegan desordenados a proposito: el tramo se calcula sobre el orden por puntos.
    const pagina = crear([
      { id: 'oro', nombre: 'Oro', puntosMinimos: 15, porcentajeDescuento: 10 },
      { id: 'bronce', nombre: 'Bronce', puntosMinimos: 0, porcentajeDescuento: 0 },
      { id: 'plata', nombre: 'Plata', puntosMinimos: 5, porcentajeDescuento: 5 },
    ]);

    expect(textos(pagina, 'tbody td:first-child')).toEqual(['Bronce', 'Plata', 'Oro']);
    expect(textos(pagina, 'tbody td[data-etiqueta="Puntos"]')).toEqual([
      '0 a 4 puntos',
      '5 a 14 puntos',
      '15 puntos o más',
    ]);
  });

  it('sin niveles avisa que nadie recibe descuento por lealtad', () => {
    const pagina = crear([]);

    expect(textos(pagina, 'td.vacio')[0]).toContain('nadie recibe descuento');
  });
});
