import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { AsistenciaSeccion } from './asistencia.seccion';

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

describe('AsistenciaSeccion', () => {
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

  it('muestra cada turno con su entrada, marca la tardanza y a quien falto', () => {
    const fixture = TestBed.createComponent(AsistenciaSeccion);
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/api/v1/asistencia/dia'))
      .flush([
        {
          trabajadorId: 'p1',
          nombre: 'Luis Quispe',
          cargo: 'MOZO',
          turnoInicio: '2026-09-14T12:00:00-05:00',
          turnoFin: '2026-09-14T20:00:00-05:00',
          entrada: '2026-09-14T12:15:00-05:00',
          estado: 'DENTRO',
          minutosTarde: 15,
        },
        {
          trabajadorId: 'p2',
          nombre: 'Ana Flores',
          cargo: 'CAJERO',
          turnoInicio: '2026-09-14T08:00:00-05:00',
          turnoFin: '2026-09-14T11:00:00-05:00',
          estado: 'FALTO',
          minutosTarde: 0,
        },
        {
          trabajadorId: 'p3',
          nombre: 'Pedro Mamani',
          cargo: 'ALMACENERO',
          entrada: '2026-09-14T09:00:00-05:00',
          salida: '2026-09-14T13:00:00-05:00',
          estado: 'SALIO',
          minutosTarde: 0,
        },
      ]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, 'tbody td[data-etiqueta="Entrada"]')[0]).toContain('tarde 15 min');
    expect(textos(pagina, 'tbody td[data-etiqueta="Turno"]')[2]).toBe('Sin turno');
    expect(textos(pagina, 'tbody .chip')).toEqual(['Dentro', 'Faltó', 'Salió']);

    const faltaron = Array.from(pagina.querySelectorAll('.kpis li')).find((li) =>
      li.textContent!.includes('Faltó'),
    )!;
    expect(faltaron.classList.contains('alerta')).toBe(true);
  });
});
