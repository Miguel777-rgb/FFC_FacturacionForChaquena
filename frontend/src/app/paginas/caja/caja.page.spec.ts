import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { CajaPage } from './caja.page';

const BILLETE_FALSO = {
  id: 'p1',
  estado: 'FRAUDULENTO',
  esFraudulento: true,
  tipoPago: 'EFECTIVO',
  monto: 100,
  observacion: 'Billete de S/ 100 falso detectado en caja',
  registradoPor: 'caja1',
  fecha: '2026-10-10T21:05:00-05:00',
  ordenId: 'abcd1234-0000-0000-0000-000000000000',
};

/** Sin motivo ni autor: lo que queda de una alerta antigua o de un proceso. */
const SIN_DATOS = { id: 'p2', esFraudulento: true, tipoPago: 'TARJETA', monto: 58 };

function crear(alertas: object[]) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
    ],
  });
  const fixture = TestBed.createComponent(CajaPage);
  fixture.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  const cuerpos: Array<[string, object | null]> = [
    ['/alertas-fraude', alertas],
    ['/pagos/pendientes', []],
    ['/ordenes/activas', []],
    ['/productos-top', []],
    ['/arqueo', {}],
  ];
  for (const peticion of http.match(() => true)) {
    const cuerpo = cuerpos.find(([trozo]) => peticion.request.url.includes(trozo));
    peticion.flush(cuerpo ? cuerpo[1] : null);
  }
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

function texto(e: Element | null): string {
  return (e?.textContent ?? '').replace(/\s+/g, ' ').trim();
}

/** La tarjeta «Alertas de fraude» del tablero lleva aqui: tiene que haber que ver. */
describe('CajaPage: bandeja de alertas de fraude', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('lista cada pago marcado como fraude y lleva a su comanda', () => {
    const pagina = crear([BILLETE_FALSO, SIN_DATOS]);

    expect(texto(pagina.querySelector('.bandeja-fraude h2'))).toBe(
      'Alertas de fraude registradas (2)',
    );

    const [billete, otra] = Array.from(pagina.querySelectorAll('.pagos-fraude li'));
    expect(texto(billete)).toContain('Fraudulento');
    expect(texto(billete)).toContain('Efectivo');
    expect(texto(billete)).toContain('S/ 100.00');
    expect(texto(billete)).toContain('Motivo: Billete de S/ 100 falso detectado en caja');
    expect(texto(billete)).toContain('Reportado por caja1');

    const enlace = billete.querySelector('a')!;
    expect(texto(enlace)).toBe('Ver comanda #ABCD1234');
    expect(enlace.getAttribute('href')).toBe('/ordenes?orden=abcd1234-0000-0000-0000-000000000000');

    // Sin motivo lo dice, sin autor no inventa uno y sin comanda no ofrece enlace.
    expect(texto(otra)).toContain('Motivo: Sin detalle especificado');
    expect(texto(otra)).not.toContain('Reportado por');
    expect(otra.querySelector('a')).toBeNull();
  });

  it('sin alertas no pinta la bandeja', () => {
    const pagina = crear([]);

    expect(pagina.querySelector('.bandeja-fraude')).toBeNull();
  });
});
