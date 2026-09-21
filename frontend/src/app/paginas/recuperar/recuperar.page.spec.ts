import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { RecuperarPage } from './recuperar.page';

function crear() {
  const fixture = TestBed.createComponent(RecuperarPage);
  fixture.detectChanges();
  return fixture;
}

function escribir(fixture: ReturnType<typeof crear>, valor: string) {
  const campo: HTMLInputElement = fixture.nativeElement.querySelector('input');
  campo.value = valor;
  campo.dispatchEvent(new Event('input'));
  campo.dispatchEvent(new Event('blur'));
  fixture.detectChanges();
}

describe('RecuperarPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
  });

  it('no envia nada si el correo no tiene forma de correo', () => {
    const fixture = crear();
    escribir(fixture, 'sin-arroba.pe');

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('arroba y dominio');
    TestBed.inject(HttpTestingController).expectNone(() => true);
  });

  it('pide el enlace y contesta lo mismo sin decir si el correo existe', () => {
    const fixture = crear();
    escribir(fixture, 'rosa@chaquena.pe');

    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    const peticion = TestBed.inject(HttpTestingController).expectOne((r) =>
      r.url.endsWith('/api/v1/auth/recuperacion'),
    );
    expect(peticion.request.body.correo).toBe('rosa@chaquena.pe');
    peticion.flush(null, { status: 202, statusText: 'Accepted' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Si ese correo pertenece a una cuenta');
  });

  it('con un fallo del servidor dice exactamente lo mismo', () => {
    const fixture = crear();
    escribir(fixture, 'rosa@chaquena.pe');
    (fixture.nativeElement.querySelector('button[type=submit]') as HTMLButtonElement).click();
    fixture.detectChanges();

    TestBed.inject(HttpTestingController)
      .expectOne((r) => r.url.endsWith('/api/v1/auth/recuperacion'))
      .flush({ message: 'lo que sea' }, { status: 500, statusText: 'Error' });
    fixture.detectChanges();

    // Si el desenlace cambiara al fallar, el formulario contaria por la puerta
    // de atras lo que el 202 calla.
    expect(fixture.nativeElement.textContent).toContain('Si ese correo pertenece a una cuenta');
  });
});
