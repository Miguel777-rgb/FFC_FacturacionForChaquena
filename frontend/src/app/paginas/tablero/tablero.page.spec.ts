import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { TableroPage } from './tablero.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = { sub: 'quien@chaquena.pe', username: 'quien', cargo, roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

const TABLERO = {
  ventas: { total: 120, comandas: 4, ticketPromedio: 30 },
  operacion: {},
  ahoraMismo: {
    comandasAbiertas: 3,
    mesasOcupadas: 2,
    mesasActivas: 10,
    insumosBajoMinimo: 1,
    pagosPorAcreditar: 0,
    alertasDeFraude: 0,
    eventosOutboxEnError: 0,
  },
};

describe('TableroPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  });

  it('a la caja le enlaza lo que puede resolver y deja en dato lo que no', () => {
    TestBed.inject(SesionService).abrir(tokenDe('CAJERO', ['CAJA']));
    const fixture = TestBed.createComponent(TableroPage);
    fixture.detectChanges();

    for (const peticion of TestBed.inject(HttpTestingController).match(() => true)) {
      const url = peticion.request.url;
      if (url.includes('/reportes/tablero')) peticion.flush(TABLERO);
      else if (url.includes('/reportes/serie-ventas')) peticion.flush({ puntos: [] });
      else peticion.flush([]);
    }
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '.pendientes a .nombre')).toContain('Comandas abiertas');
    // El inventario es de almacen: la caja ve la cifra, pero no un enlace a un 403.
    expect(textos(pagina, '.pendientes .sin-enlace .nombre')).toContain('Insumos bajo mínimo');
    expect(textos(pagina, '.vacio')).toContain('Todavía no se vendió ningún plato hoy.');
  });
});
