import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { InventarioSeccion } from './inventario.seccion';

const INSUMOS = [
  {
    id: 'pollo',
    nombre: 'Pechuga de pollo',
    unidadMedida: 'KG',
    stockActual: 24.64,
    stockMinimo: 5,
    bajoMinimo: false,
    proximoVencimiento: '2026-09-16',
    cantidadPorVencer: 5,
    cantidadVencida: 0,
    valorStock: 57.5,
    cantidadSinCosto: 19.64,
  },
  {
    id: 'culantro',
    nombre: 'Culantro',
    unidadMedida: 'KG',
    stockActual: 0.5,
    stockMinimo: 2,
    bajoMinimo: true,
    proximoVencimiento: '2026-09-13',
    cantidadPorVencer: 0,
    cantidadVencida: 0.5,
    valorStock: 6,
    cantidadSinCosto: 0,
  },
  {
    id: 'arroz',
    nombre: 'Arroz crudo',
    unidadMedida: 'KG',
    stockActual: 50,
    stockMinimo: 10,
    cantidadSinCosto: 50,
  },
];

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

function crear() {
  const fixture = TestBed.createComponent(InventarioSeccion);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);

  for (const peticion of http.match(() => true)) {
    const url = peticion.request.url;
    if (url.endsWith('/api/v1/insumos')) peticion.flush({ contenido: INSUMOS });
    else if (url.includes('/inventario/resumen')) {
      peticion.flush({
        totalInsumos: 3,
        insumosBajoMinimo: 1,
        insumosPorVencer: 1,
        insumosVencidos: 1,
        valorInventario: 63.5,
        movimientos: [],
      });
    } else if (url.endsWith('/api/v1/proveedores')) {
      peticion.flush([{ id: 'avicola', nombre: 'Avícola del Sur', activo: true }]);
    }
  }
  fixture.detectChanges();
  return { fixture, http, pagina: fixture.nativeElement as HTMLElement };
}

/** Escribe en un campo y avisa como lo haria el navegador. */
function escribir(campo: Element, valor: string, evento = 'input'): void {
  (campo as HTMLInputElement).value = valor;
  campo.dispatchEvent(new Event(evento));
}

describe('InventarioSeccion', () => {
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

  it('filtra por lo que vence y marca el vencimiento sin correrlo de dia', () => {
    const { fixture, pagina } = crear();

    const porVencer = Array.from(pagina.querySelectorAll('.filtros button')).find((b) =>
      b.textContent!.includes('Por vencer'),
    ) as HTMLButtonElement;
    expect(porVencer.textContent).toContain('1');
    porVencer.click();
    fixture.detectChanges();

    expect(textos(pagina, 'tbody tr td:first-child')).toEqual(['Pechuga de pollo']);
    // «2026-09-16» leido como UTC se pintaria 15/09 en Lima.
    expect(textos(pagina, 'tbody td[data-etiqueta="Vence"]')[0]).toContain('16/09/2026');
    expect(textos(pagina, 'tbody td[data-etiqueta="Vence"] .chip')).toEqual(['Por vencer']);
  });

  it('la compra lleva proveedor, costo y vencimiento; la merma no, aunque se hayan escrito', () => {
    const { fixture, http, pagina } = crear();

    (pagina.querySelector('td.acciones button.secundario') as HTMLButtonElement).click();
    fixture.detectChanges();

    const dialogo = pagina.querySelector('.dialogo-movimiento')!;
    const [cantidad, costo] = Array.from(dialogo.querySelectorAll('input[type="number"]'));
    escribir(cantidad, '6');
    escribir(dialogo.querySelectorAll('select')[1], 'avicola', 'change');
    escribir(costo, '11.5');
    escribir(dialogo.querySelector('input[type="date"]')!, '2099-01-31');
    escribir(dialogo.querySelector('input[type="text"]')!, 'Compra del lunes');
    fixture.detectChanges();

    (dialogo.querySelectorAll('[pie] button')[1] as HTMLButtonElement).click();
    const compra = http.expectOne(
      (r) => r.method === 'POST' && r.url.endsWith('/inventario/movimientos'),
    );
    expect(compra.request.body).toMatchObject({
      tipoControl: 'ENTRADA_COMPRA',
      cantidad: 6,
      proveedorId: 'avicola',
      costoUnitario: 11.5,
      fechaVencimiento: '2099-01-31',
    });
    // Mientras la compra no responde, el dialogo sigue ocupado y no admite otro envio.
    compra.flush({ insumoNombre: 'Pechuga de pollo', stockAnterior: 24.64, stockNuevo: 30.64 });
    fixture.detectChanges();

    // Se reabre, se cambia el motivo a merma y los datos del lote no viajan.
    (pagina.querySelector('td.acciones button.secundario') as HTMLButtonElement).click();
    fixture.detectChanges();
    escribir(dialogo.querySelector('input[type="number"]')!, '1');
    escribir(dialogo.querySelectorAll('select')[1], 'avicola', 'change');
    escribir(dialogo.querySelector('select')!, 'MERMA_DESPERDICIO', 'change');
    escribir(dialogo.querySelector('input[type="text"]')!, 'Se malogro');
    fixture.detectChanges();

    expect(dialogo.querySelector('fieldset.lote')).toBeNull();
    (dialogo.querySelectorAll('[pie] button')[1] as HTMLButtonElement).click();
    const merma = http.expectOne(
      (r) => r.method === 'POST' && r.body?.tipoControl === 'MERMA_DESPERDICIO',
    );
    expect(merma.request.body.proveedorId).toBeUndefined();
    expect(merma.request.body.costoUnitario).toBeUndefined();
  });
});
