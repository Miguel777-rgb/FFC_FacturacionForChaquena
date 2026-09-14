import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { DatosLocalSeccion } from './datos-local.seccion';

const DIAS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

function crear() {
  const fixture = TestBed.createComponent(DatosLocalSeccion);
  fixture.detectChanges();

  const http = TestBed.inject(HttpTestingController);
  http.expectOne((r) => r.method === 'GET' && r.url.endsWith('/api/v1/local')).flush({
    nombreComercial: 'Chaquena',
    porcentajeIgv: 18,
    // El servidor manda las horas con segundos.
    horarios: DIAS.map((dia) =>
      dia === 'MONDAY'
        ? { dia, cerrado: true }
        : { dia, cerrado: false, abre: '12:00:00', cierra: '22:00:00' },
    ),
  });
  fixture.detectChanges();
  return { fixture, http, pagina: fixture.nativeElement as HTMLElement };
}

describe('DatosLocalSeccion', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('pinta los siete dias en su idioma, con las horas sin segundos y el cerrado sin horas', () => {
    const { pagina } = crear();
    const filas = Array.from(pagina.querySelectorAll('tbody tr'));

    expect(filas.map((f) => f.querySelector('td')!.textContent!.trim())).toEqual([
      'Lunes',
      'Martes',
      'Miércoles',
      'Jueves',
      'Viernes',
      'Sábado',
      'Domingo',
    ]);
    expect((filas[1].querySelector('input[type="time"]') as HTMLInputElement).value).toBe('12:00');
    expect((filas[0].querySelector('input[type="time"]') as HTMLInputElement).disabled).toBe(true);
  });

  it('no manda un dia abierto con una sola hora y lo marca en la tabla', () => {
    const { fixture, http, pagina } = crear();

    const cierreDelViernes = pagina
      .querySelectorAll('tbody tr')[4]
      .querySelectorAll('input[type="time"]')[1] as HTMLInputElement;
    cierreDelViernes.value = '';
    cierreDelViernes.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(pagina.querySelectorAll('tr.incompleto')).toHaveLength(1);
    expect(pagina.querySelector('.mensaje.aviso')).not.toBeNull();

    (pagina.querySelector('.acciones-formulario button') as HTMLButtonElement).click();
    http.expectNone((r) => r.method === 'PUT');
  });
});
