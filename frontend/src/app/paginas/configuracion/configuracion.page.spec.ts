import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { ConfiguracionPage } from './configuracion.page';

/**
 * Las tres secciones del administrador que vivian en la trastienda. Una
 * pestana que desaparece no da error, ni peticion, ni consola: simplemente no
 * esta. Por eso se prueba aqui.
 */
describe('ConfiguracionPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  function crear() {
    const fixture = TestBed.createComponent(ConfiguracionPage);
    fixture.detectChanges();
    return fixture;
  }

  it('ofrece los parametros del local, los bots y los eventos', () => {
    const fixture = crear();

    const pestanas = Array.from(fixture.nativeElement.querySelectorAll('.pestanas button')).map(
      (b) => (b as HTMLElement).textContent!.trim(),
    );
    expect(pestanas).toEqual(['Local', 'Bots', 'Eventos']);
  });

  it('abre por el local y monta solo esa seccion', () => {
    const fixture = crear();
    const pagina: HTMLElement = fixture.nativeElement;

    expect(pagina.querySelector('app-local-seccion')).not.toBeNull();
    expect(pagina.querySelector('app-outbox-seccion')).toBeNull();
  });

  it('cambiar de pestana desmonta la anterior', () => {
    const fixture = crear();
    const pagina: HTMLElement = fixture.nativeElement;

    (pagina.querySelectorAll('.pestanas button')[2] as HTMLButtonElement).click();
    fixture.detectChanges();

    expect(pagina.querySelector('app-outbox-seccion')).not.toBeNull();
    expect(pagina.querySelector('app-local-seccion')).toBeNull();
  });
});
