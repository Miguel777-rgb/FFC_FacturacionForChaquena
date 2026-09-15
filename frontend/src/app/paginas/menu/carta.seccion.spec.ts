import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { CartaSeccion } from './carta.seccion';

const PLATILLOS = [
  {
    id: 'aji',
    nombre: 'Ají de gallina',
    categoriaId: 1,
    categoriaNombre: 'Gallina y Pollo',
    precioVentaBase: 25,
    activo: true,
    costo: 3.48,
    margen: 21.52,
    margenPorcentaje: 86.1,
    insumosSinCosto: [],
    fotoId: 'foto-1',
    tiempoPreparacionMinutos: 15,
    alergenos: [
      { id: 1, nombre: 'Gluten', activo: true },
      { id: 9, nombre: 'Kiwicha', activo: false },
    ],
  },
  {
    id: 'lomo',
    nombre: 'Lomo saltado',
    categoriaId: 1,
    categoriaNombre: 'Gallina y Pollo',
    precioVentaBase: 32,
    activo: true,
    insumosSinCosto: ['Carne de res', 'Papa amarilla'],
    alergenos: [],
  },
];

const ALERGENOS = [
  { id: 1, nombre: 'Gluten', activo: true },
  { id: 2, nombre: 'Soya', activo: true },
  { id: 9, nombre: 'Kiwicha', activo: false },
  { id: 5, nombre: 'Maní', activo: false },
];

function textos(raiz: Element, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

function crear() {
  const fixture = TestBed.createComponent(CartaSeccion);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);

  for (const peticion of http.match(() => true)) {
    const url = peticion.request.url;
    if (url.endsWith('/api/v1/platillos')) peticion.flush({ contenido: PLATILLOS });
    else if (url.includes('/categorias')) peticion.flush([{ id: 1, nombre: 'Gallina y Pollo' }]);
    else if (url.endsWith('/api/v1/insumos')) peticion.flush({ contenido: [] });
    else if (url.endsWith('/api/v1/alergenos')) peticion.flush(ALERGENOS);
  }
  fixture.detectChanges();
  return { fixture, http, pagina: fixture.nativeElement as HTMLElement };
}

describe('CartaSeccion', () => {
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

  it('muestra costo y margen, y cuando no hay costo dice que insumo falta comprar', () => {
    const { pagina } = crear();

    const costos = textos(pagina, 'tbody td[data-etiqueta="Costo"]');
    expect(costos[0]).toBe('S/ 3.48');
    expect(costos[1]).toContain('Sin costo');
    expect(costos[1]).toContain('Carne de res, Papa amarilla');

    const margenes = textos(pagina, 'tbody td[data-etiqueta="Margen"]');
    expect(margenes[0]).toContain('S/ 21.52');
    expect(margenes[0]).toContain('86.1 %');
    expect(margenes[1]).toBe('—');
  });

  it('al editar envia foto, tiempo y alergenos, y no pierde uno dado de baja que el plato ya tenia', () => {
    const { fixture, http, pagina } = crear();

    (pagina.querySelector('tbody td.acciones .icono-accion') as HTMLButtonElement).click();
    fixture.detectChanges();

    const ficha = pagina.querySelector('.dialogo-platillo')!;
    // Mani esta de baja y el plato no lo tiene: no se ofrece. Kiwicha tambien
    // esta de baja, pero el plato ya la lleva.
    expect(textos(ficha, '.casilla')).toEqual(['Gluten', 'Soya', 'Kiwicha (De baja)']);

    const soya = ficha.querySelectorAll('.casilla input')[1] as HTMLInputElement;
    soya.checked = true;
    soya.dispatchEvent(new Event('change'));
    const tiempo = ficha.querySelector('input.tiempo') as HTMLInputElement;
    tiempo.value = '20';
    tiempo.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (Array.from(ficha.querySelectorAll('[pie] button')).at(-1) as HTMLButtonElement).click();
    const put = http.expectOne(
      (r) => r.method === 'PUT' && r.url.endsWith('/api/v1/platillos/aji'),
    );
    expect(put.request.body).toMatchObject({
      fotoId: 'foto-1',
      tiempoPreparacionMinutos: 20,
      activo: true,
    });
    expect([...put.request.body.alergenoIds].sort()).toEqual([1, 2, 9]);
  });

  it('no sube un SVG: lo rechaza antes de gastar la subida', () => {
    const { fixture, http, pagina } = crear();

    (pagina.querySelector('.barra button.secundario') as HTMLButtonElement).click();
    fixture.detectChanges();

    const ficha = pagina.querySelector('.dialogo-platillo')!;
    const selector = ficha.querySelector('input[type="file"]') as HTMLInputElement;
    const svg = new File(['<svg xmlns="http://www.w3.org/2000/svg"/>'], 'plato.svg', {
      type: 'image/svg+xml',
    });
    Object.defineProperty(selector, 'files', { value: [svg] });
    selector.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    http.expectNone((r) => r.url.includes('/api/v1/archivos'));
    expect(ficha.querySelector('.error-foto')?.textContent).toContain('WebP, PNG o JPG');
  });
});
