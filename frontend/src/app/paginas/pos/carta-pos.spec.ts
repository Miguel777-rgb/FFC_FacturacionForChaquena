import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import type { PlatilloDisponibleDto } from '../../api';
import { CartaPos } from './carta-pos';

/**
 * La carta es por donde el mozo encuentra el plato con el comensal delante. Si
 * el buscador no ignora tildes o las secciones se desordenan, lo que falla es
 * la velocidad del servicio, y eso no deja rastro en ningun log.
 */
describe('CartaPos', () => {
  let fixture: ComponentFixture<CartaPos>;

  const carta: PlatilloDisponibleDto[] = [
    {
      id: 'lomo',
      nombre: 'Lomo Saltado',
      categoriaId: 2,
      categoriaNombre: 'Criollos',
      precioVentaBase: 32,
      disponible: true,
    },
    {
      id: 'aji',
      nombre: 'Ají de Gallina',
      categoriaId: 2,
      categoriaNombre: 'Criollos',
      precioVentaBase: 26,
      disponible: true,
    },
    {
      id: 'papa',
      nombre: 'Papa a la Huancaína',
      categoriaId: 1,
      categoriaNombre: 'Entradas',
      precioVentaBase: 14,
      disponible: true,
    },
    {
      id: 'ceviche',
      nombre: 'Ceviche',
      categoriaId: 3,
      categoriaNombre: 'Marinos',
      precioVentaBase: 28,
      disponible: false,
      insumosFaltantes: ['Pescado'],
    },
  ];

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
    fixture = TestBed.createComponent(CartaPos);
    fixture.componentRef.setInput('carta', carta);
    fixture.detectChanges();
  });

  const texto = (sel: string): string[] =>
    Array.from<HTMLElement>(fixture.nativeElement.querySelectorAll(sel)).map(
      (e) => e.textContent?.trim() ?? '',
    );

  function buscar(q: string): void {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type=search]');
    input.value = q;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  }

  it('agrupa por seccion en el orden en que llega la carta', () => {
    expect(texto('h3')).toEqual(['Criollos', 'Entradas', 'Marinos']);
    expect(texto('.categoria')).toEqual(['Criollos', 'Entradas', 'Marinos']);
    expect(texto('.seccion:first-of-type .nombre')).toEqual(['Lomo Saltado', 'Ají de Gallina']);
  });

  it('busca sin tildes ni mayusculas', () => {
    buscar('HUANCAINA');
    expect(texto('.nombre')).toEqual(['Papa a la Huancaína']);

    buscar('aji');
    expect(texto('.nombre')).toEqual(['Ají de Gallina']);
  });

  it('dice cuando nada coincide', () => {
    buscar('pizza');
    expect(texto('.vacio')).toEqual(['Ningún platillo coincide con «pizza».']);
  });

  it('avisa del plato elegido y no deja elegir uno agotado', () => {
    const elegidos: string[] = [];
    fixture.componentInstance.elegir.subscribe((p) => elegidos.push(p.id ?? ''));
    const platos: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('.plato'),
    );

    platos[0].click();
    const agotado = platos.find((b) => b.textContent?.includes('Ceviche'))!;
    agotado.click();

    expect(elegidos).toEqual(['lomo']);
    expect(agotado.disabled).toBe(true);
    expect(agotado.textContent).toContain('Pescado');
  });

  it('marca cuantos hay ya en la comanda', () => {
    fixture.componentRef.setInput('enComanda', { lomo: 2 });
    fixture.detectChanges();

    expect(texto('.en-comanda .visualmente-oculto')).toEqual(['2 en la comanda']);
  });
});
