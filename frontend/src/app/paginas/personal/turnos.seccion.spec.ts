import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { TurnosSeccion } from './turnos.seccion';

function campoDeDia(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`;
}

function lunesDeEstaSemana(): Date {
  const hoy = new Date();
  const lunes = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
  lunes.setDate(lunes.getDate() - ((lunes.getDay() + 6) % 7));
  return lunes;
}

function textos(raiz: Element, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

function escribir(campo: Element, valor: string): void {
  (campo as HTMLInputElement).value = valor;
  campo.dispatchEvent(new Event('input'));
}

describe('TurnosSeccion', () => {
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

  it('pinta la semana por persona y guarda un turno de noche que termina al dia siguiente', () => {
    const lunes = lunesDeEstaSemana();
    const martes = new Date(lunes);
    martes.setDate(lunes.getDate() + 1);

    const fixture = TestBed.createComponent(TurnosSeccion);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);

    const pedido = http.expectOne((r) => r.url.endsWith('/api/v1/turnos'));
    expect(pedido.request.urlWithParams).toContain(`desde=${campoDeDia(lunes)}`);
    pedido.flush([
      {
        id: 't1',
        trabajadorId: 'p1',
        trabajadorNombre: 'Luis Quispe',
        fecha: campoDeDia(lunes),
        inicio: '12:00:00',
        fin: '20:00:00',
      },
    ]);
    http
      .expectOne((r) => r.url.endsWith('/api/v1/trabajadores/activos'))
      .flush([
        { id: 'p1', nombres: 'Luis', apellidos: 'Quispe', cargoNombre: 'MOZO', activo: true },
        { id: 'p2', nombres: 'Ana', apellidos: 'Flores', cargoNombre: 'CAJERO', activo: true },
      ]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, 'tbody th[scope="row"]')).toEqual([
      'Ana Flores CAJERO',
      'Luis Quispe MOZO',
    ]);
    expect(textos(pagina, '.turno')).toEqual(['12:00 – 20:00']);

    // El + del martes de Ana: la ficha llega con la persona y el dia puestos.
    const filaAna = pagina.querySelectorAll('tbody tr')[0];
    (filaAna.querySelectorAll('.agregar')[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    const ficha = pagina.querySelector('.dialogo-turno')!;
    escribir(ficha.querySelector('input.inicio')!, '18:00');
    escribir(ficha.querySelector('input.fin')!, '02:00');
    fixture.detectChanges();
    expect(ficha.querySelector('.al-dia-siguiente')?.textContent).toContain('día siguiente');

    (Array.from(ficha.querySelectorAll('[pie] button')).at(-1) as HTMLButtonElement).click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/v1/turnos'));
    expect(post.request.body).toMatchObject({
      trabajadorId: 'p2',
      fecha: campoDeDia(martes),
      inicio: '18:00',
      fin: '02:00',
    });
  });
});
