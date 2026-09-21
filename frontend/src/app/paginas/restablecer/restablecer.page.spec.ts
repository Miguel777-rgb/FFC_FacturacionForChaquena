import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { RestablecerPage } from './restablecer.page';

function conToken(token: string | null) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: { get: () => token } } },
      },
    ],
  });
  const fixture = TestBed.createComponent(RestablecerPage);
  fixture.detectChanges();
  return fixture;
}

function escribir(fixture: ReturnType<typeof conToken>, nueva: string, repetida: string) {
  const campos: HTMLInputElement[] = Array.from(
    fixture.nativeElement.querySelectorAll('input[type=password]'),
  );
  for (const [campo, valor] of [
    [campos[0], nueva],
    [campos[1], repetida],
  ] as const) {
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    campo.dispatchEvent(new Event('blur'));
  }
  fixture.detectChanges();
}

describe('RestablecerPage', () => {
  beforeEach(() => TestBed.resetTestingModule());

  it('sin token en la direccion no ensena el formulario', () => {
    const fixture = conToken(null);

    expect(fixture.nativeElement.textContent).toContain('enlace está incompleto');
    expect(fixture.nativeElement.querySelector('input[type=password]')).toBeNull();
  });

  it('rechaza una contrasena que no cumple el patron, sin llamar al servidor', () => {
    const fixture = conToken('un-token');
    escribir(fixture, 'chaquena', 'chaquena');

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Mínimo ocho caracteres');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('exige que las dos contrasenas sean la misma', () => {
    const fixture = conToken('un-token');
    escribir(fixture, 'Chaquena2026', 'Chaquena2027');

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('tienen que ser la misma');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('manda el token con la contrasena nueva', () => {
    const fixture = conToken('un-token');
    escribir(fixture, 'Chaquena2026', 'Chaquena2026');

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const peticion = TestBed.inject(HttpTestingController).expectOne((r) =>
      r.url.endsWith('/api/v1/auth/recuperacion/confirmacion'),
    );
    expect(peticion.request.body).toEqual({ token: 'un-token', password: 'Chaquena2026' });
  });

  it('ensena el motivo del servidor cuando el enlace ya no vale', () => {
    const fixture = conToken('un-token');
    escribir(fixture, 'Chaquena2026', 'Chaquena2026');
    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/api/v1/auth/recuperacion/confirmacion'))
      .flush(
        { message: 'Este enlace ya se uso. Pide uno nuevo.' },
        { status: 409, statusText: 'Conflict' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('ya se uso');
  });
});
