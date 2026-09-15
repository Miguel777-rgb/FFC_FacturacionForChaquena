import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { AlergenosSeccion } from './alergenos.seccion';

function crear() {
  const fixture = TestBed.createComponent(AlergenosSeccion);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http
    .expectOne((r) => r.url.endsWith('/api/v1/alergenos'))
    .flush([
      { id: 1, nombre: 'Gluten', activo: true },
      { id: 9, nombre: 'Kiwicha', activo: false },
    ]);
  fixture.detectChanges();
  return { fixture, http, pagina: fixture.nativeElement as HTMLElement };
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

describe('AlergenosSeccion', () => {
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

  it('muestra tambien los dados de baja, apagados, porque siguen marcados en sus platillos', () => {
    const { pagina } = crear();

    expect(textos(pagina, 'tbody td:first-child')).toEqual(['Gluten', 'Kiwicha']);
    expect(textos(pagina, 'tbody .chip')).toEqual(['Se ofrece', 'De baja']);
    expect(pagina.querySelectorAll('tbody tr')[1].classList.contains('tenue')).toBe(true);
  });

  it('no envia un nombre que ya existe, aunque cambien las mayusculas', () => {
    const { fixture, http, pagina } = crear();

    (pagina.querySelector('.barra button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const nombre = pagina.querySelector('app-dialogo input[type="text"]') as HTMLInputElement;
    nombre.value = '  gluten ';
    nombre.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    (pagina.querySelectorAll('app-dialogo [pie] button')[1] as HTMLButtonElement).click();
    http.expectNone((r) => r.method === 'POST');
  });
});
