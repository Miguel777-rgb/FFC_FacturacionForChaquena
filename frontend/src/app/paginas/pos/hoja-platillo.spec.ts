import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { HojaPlatillo } from './hoja-platillo';
import type { EleccionPlatillo } from './linea';

/**
 * La hoja es donde se decide que lleva cada plato. Si deja de devolver la
 * observacion, o arrastra la del plato anterior, el error llega a cocina: el
 * comensal recibe cebolla o la mesa de al lado recibe su «sin cebolla».
 */
describe('HojaPlatillo', () => {
  let fixture: ComponentFixture<HojaPlatillo>;
  let emitido: EleccionPlatillo | null;

  const lomo = { id: 'lomo', nombre: 'Lomo Saltado', precioVentaBase: 32, disponible: true };
  const gaseosa = { id: 'gas', nombre: 'Gaseosa', precioAdicional: 5 };

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    fixture = TestBed.createComponent(HojaPlatillo);
    fixture.componentRef.setInput('platillo', lomo);
    fixture.componentRef.setInput('complementos', [gaseosa]);
    emitido = null;
    fixture.componentInstance.confirmar.subscribe((e) => (emitido = e));
  });

  function abrir(): void {
    fixture.componentRef.setInput('abierto', true);
    fixture.detectChanges();
  }

  const boton = (etiqueta: string): HTMLButtonElement =>
    fixture.nativeElement.querySelector(`button[aria-label="${etiqueta}"]`);
  const principal = (): HTMLButtonElement => fixture.nativeElement.querySelector('[pie] button');
  const nota = (): HTMLTextAreaElement => fixture.nativeElement.querySelector('textarea');

  function pulsar(b: HTMLButtonElement): void {
    b.click();
    fixture.detectChanges();
  }

  it('devuelve cantidad, observacion y complementos, con el total en el boton', () => {
    abrir();

    pulsar(boton('Agregar una unidad de Lomo Saltado'));
    pulsar(boton('Agregar Gaseosa'));
    nota().value = 'sin cebolla';
    nota().dispatchEvent(new Event('input'));
    fixture.detectChanges();

    // (32 + 5) × 2
    expect(principal().textContent?.trim()).toBe('Agregar · S/ 74.00');
    pulsar(principal());

    expect(emitido?.cantidad).toBe(2);
    expect(emitido?.nota).toBe('sin cebolla');
    expect(emitido?.complementos.map((c) => [c.complemento.id, c.cantidad])).toEqual([['gas', 1]]);
  });

  it('no baja de un plato', () => {
    abrir();
    expect(boton('Quitar una unidad de Lomo Saltado').disabled).toBe(true);
  });

  it('al corregir parte de lo que ya tenia la linea', () => {
    fixture.componentRef.setInput('accion', 'guardar');
    fixture.componentRef.setInput('inicial', {
      cantidad: 3,
      nota: 'bien cocido',
      complementos: [{ complemento: gaseosa, cantidad: 2 }],
    });
    abrir();

    expect(nota().value).toBe('bien cocido');
    // (32 + 5 × 2) × 3
    expect(principal().textContent?.trim()).toBe('Guardar · S/ 126.00');
  });

  it('al volver a abrirse para otro plato empieza de cero', () => {
    abrir();
    nota().value = 'sin cebolla';
    nota().dispatchEvent(new Event('input'));
    pulsar(boton('Agregar una unidad de Lomo Saltado'));

    fixture.componentRef.setInput('abierto', false);
    fixture.detectChanges();
    fixture.componentRef.setInput('platillo', { ...lomo, id: 'otro', nombre: 'Ceviche' });
    abrir();

    expect(nota().value).toBe('');
    pulsar(principal());
    expect(emitido?.cantidad).toBe(1);
    expect(emitido?.nota).toBe('');
  });
});
