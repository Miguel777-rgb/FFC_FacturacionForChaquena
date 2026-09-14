import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { OrdenesPage } from './ordenes.page';

function crear(query: Record<string, string> = {}) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(query) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(OrdenesPage);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController) };
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

/**
 * Ordenes es la unica pantalla que deja mover una comanda fuera de su puesto.
 * Lo que se prueba es que no ofrezca un paso que el servidor va a rechazar.
 */
describe('OrdenesPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('pide las de hoy con el desfase del local y dice cuando no hay ninguna', () => {
    const { fixture, http } = crear();

    const lista = http.expectOne((r) => r.url.endsWith('/api/v1/ordenes'));
    // Sin la zona, el servidor cortaria el dia a medianoche de Greenwich.
    // El cliente generado codifica el valor: `:` viaja como `%3A`.
    expect(decodeURIComponent(lista.request.params.get('desde')!)).toMatch(
      /T00:00:00[+-]\d{2}:\d{2}$/,
    );
    expect(lista.request.params.get('estado')).toBeNull();
    lista.flush({ contenido: [], totalPaginas: 0 });
    fixture.detectChanges();

    expect(textos(fixture.nativeElement, 'td.vacio')).toEqual(['No hay órdenes con estos filtros.']);
    http.verify();
  });

  it('abre la orden que llega por ?orden= y solo ofrece los pasos permitidos', () => {
    const { fixture, http } = crear({ orden: 'abc12345-0000' });

    http.expectOne((r) => r.url.endsWith('/api/v1/ordenes')).flush({ contenido: [] });
    http
      .expectOne((r) => r.url.endsWith('/api/v1/ordenes/abc12345-0000/ticket'))
      .flush({ correlativo: 'T-0042' });
    http.expectOne((r) => r.url.endsWith('/api/v1/ordenes/abc12345-0000')).flush({
      id: 'abc12345-0000',
      estado: 'ENCOLADO',
      tipoOrden: 'MESA',
      mesaNumero: '5',
      detalles: [],
      // Cobrar es de la caja: aunque el servidor lo permita, aqui no se ofrece.
      transicionesPermitidas: ['EN_PREPARACION', 'PAGADO', 'CANCELADO'],
    });
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '[pie] button')).toEqual(['Pasar a En preparación', 'Cancelar orden']);
    expect(textos(pagina, '.datos dd.cifra')).toContain('T-0042');
    http.verify();
  });
});
