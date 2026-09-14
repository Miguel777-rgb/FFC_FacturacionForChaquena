import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { ProveedoresSeccion } from './proveedores.seccion';

function crear() {
  const fixture = TestBed.createComponent(ProveedoresSeccion);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http
    .expectOne((r) => r.url.endsWith('/api/v1/proveedores'))
    .flush([
      {
        id: 'a',
        nombre: 'Avícola del Sur',
        ruc: '20612345671',
        telefono: '014567891',
        activo: true,
      },
      { id: 'b', nombre: 'Mercado Santa Anita', activo: false },
    ]);
  fixture.detectChanges();
  return { fixture, http, pagina: fixture.nativeElement as HTMLElement };
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

describe('ProveedoresSeccion', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  it('muestra tambien los dados de baja, apagados, porque sus lotes siguen existiendo', () => {
    const { pagina } = crear();

    expect(textos(pagina, 'tbody td:first-child')).toEqual([
      'Avícola del Sur',
      'Mercado Santa Anita',
    ]);
    expect(textos(pagina, 'tbody .chip')).toEqual(['Activo', 'De baja']);
    expect(pagina.querySelectorAll('tbody tr')[1].classList.contains('tenue')).toBe(true);
    expect(textos(pagina, 'tbody td[data-etiqueta="Contacto"]')[1]).toBe('Sin datos de contacto');
  });

  it('no registra un RUC que no tiene 11 digitos', () => {
    const { fixture, http, pagina } = crear();

    (pagina.querySelector('.barra button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const [nombre, ruc] = Array.from(pagina.querySelectorAll('app-dialogo input[type="text"]'));
    (nombre as HTMLInputElement).value = 'Pesquera Muelle Norte';
    nombre.dispatchEvent(new Event('input'));
    (ruc as HTMLInputElement).value = '2061234567';
    ruc.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (pagina.querySelectorAll('app-dialogo [pie] button')[1] as HTMLButtonElement).click();
    http.expectNone((r) => r.method === 'POST');
  });
});
