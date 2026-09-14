import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { ClientesPage } from './clientes.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = { sub: 'quien@chaquena.pe', username: 'quien', cargo, roles, exp: Math.floor(Date.now() / 1000) + 3600 };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function crear(cargo: string, roles: string[]) {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideHttpClient(),
      provideHttpClientTesting(),
      provideRouter([]),
    ],
  });
  TestBed.inject(SesionService).abrir(tokenDe(cargo, roles));
  const fixture = TestBed.createComponent(ClientesPage);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController) };
}

/** Responde todo lo pendiente por el primer trozo de URL que coincida. */
function responder(http: HttpTestingController, cuerpos: Array<[string, object]>): void {
  for (const peticion of http.match(() => true)) {
    const cuerpo = cuerpos.find(([trozo]) => peticion.request.url.includes(trozo));
    peticion.flush(cuerpo ? cuerpo[1] : null);
  }
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

const ROSA = { id: 'c1', nombres: 'Rosa', apellidos: 'Quispe', dni: '44556677', puntosFidelidad: 12 };

function abrirFicha(fixture: ReturnType<typeof crear>['fixture'], http: HttpTestingController) {
  responder(http, [['/api/v1/clientes', { contenido: [ROSA], totalPaginas: 1 }]]);
  fixture.detectChanges();

  (fixture.nativeElement.querySelector('td.acciones button') as HTMLButtonElement).click();
  fixture.detectChanges();

  // Las rutas especificas antes que la del listado, que las contiene a todas.
  responder(http, [
    ['/fidelizacion', { puntosFidelidad: 12, calificacionesRequeridas: 5, calificacionesFaltantes: 2 }],
    ['/cupones', []],
    ['/preferencias', { platillosFrecuentes: [], notasHabituales: [] }],
    ['/empresas', []],
    ['/ordenes', []],
  ]);
  fixture.detectChanges();
}

describe('ClientesPage', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  it('sin clientes lo dice en la tabla', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);

    responder(http, [['/api/v1/clientes', { contenido: [], totalPaginas: 0 }]]);
    fixture.detectChanges();

    expect(textos(fixture.nativeElement, 'td.vacio')).toEqual(['Todavía no hay clientes registrados.']);
  });

  it('la ficha cuenta lo que le falta para el cupon, y la caja no puede bloquear', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);
    abrirFicha(fixture, http);

    const pagina: HTMLElement = fixture.nativeElement;
    expect(pagina.querySelector('.progreso')!.getAttribute('aria-valuenow')).toBe('60');
    expect(textos(pagina, '.ficha > p')).toContain('Le faltan 2 calificaciones para el próximo cupón.');
    expect(textos(pagina, '[pie] button')).toEqual([]);
    http.verify();
  });

  it('el administrador si puede bloquear por fraude', () => {
    const { fixture, http } = crear('ADMINISTRADOR', ['ADMIN']);
    abrirFicha(fixture, http);

    expect(textos(fixture.nativeElement, '[pie] button')).toEqual(['Bloquear cliente']);
    http.verify();
  });
});
