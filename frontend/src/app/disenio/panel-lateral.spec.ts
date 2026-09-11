import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { PanelLateral } from './panel-lateral';
import { SesionService } from '../nucleo/sesion/sesion.service';
import { I18nService } from '../nucleo/i18n/i18n.service';
import { routes } from '../app.routes';

function tokenCon(claims: Record<string, unknown>): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

function tokenDe(cargo: string, roles: string[]): string {
  return tokenCon({
    sub: 'quien@chaquena.pe',
    username: 'quien',
    nombres: 'Miguel',
    apellidos: 'Flores',
    cargo,
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600,
  });
}

/** Los enlaces del panel, en el orden en que se pintan. */
function destinosVisibles(): string[] {
  const fixture = TestBed.createComponent(PanelLateral);
  fixture.detectChanges();
  return Array.from(fixture.nativeElement.querySelectorAll('nav a')).map((a) =>
    (a as HTMLElement).textContent!.trim(),
  );
}

/**
 * El panel es la unica pieza que dice que existe cada superficie: una pantalla
 * a la que no llega ningun enlace es, para quien usa el sistema, una pantalla
 * que no esta. Por eso se prueba aqui la lista y no solo las guardas.
 */
describe('PanelLateral', () => {
  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideZonelessChangeDetection(), provideRouter(routes)],
    });
  });

  it('le ensena al administrador las siete superficies', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));

    expect(destinosVisibles()).toEqual([
      'Punto de venta',
      'Cocina',
      'Caja',
      'Despacho',
      'Trastienda',
      'Personal',
      'KPIs',
    ]);
  });

  it('la caja ve su superficie y los indicadores, y nada mas', () => {
    // KPIs entra porque el endpoint del tablero admite CAJA; el personal no,
    // porque repartir permisos es solo del administrador.
    TestBed.inject(SesionService).abrir(tokenDe('CAJERO', ['CAJA']));

    expect(destinosVisibles()).toEqual(['Caja', 'KPIs']);
  });

  it('al almacenero no le ofrece ni personal ni KPIs', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));

    expect(destinosVisibles()).toEqual(['Trastienda']);
  });

  it('cambiar de idioma repinta el panel sin recargar', async () => {
    // Es la razon de llamar a `t(...)` desde la plantilla en vez de usar un
    // pipe: el consumidor reactivo ve la senal del idioma y vuelve a pintar.
    // Con un pipe puro la clave no cambia, asi que devolveria el texto viejo.
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));
    const i18n = TestBed.inject(I18nService);

    const fixture = TestBed.createComponent(PanelLateral);
    fixture.detectChanges();
    const rotulos = () =>
      Array.from(fixture.nativeElement.querySelectorAll('nav a')).map((a) =>
        (a as HTMLElement).textContent!.trim(),
      );

    expect(rotulos()[0]).toBe('Punto de venta');

    await i18n.cambiar('en');
    fixture.detectChanges();
    expect(rotulos()[0]).toBe('Point of sale');

    await i18n.cambiar('pt');
    fixture.detectChanges();
    expect(rotulos()[0]).toBe('Ponto de venda');
  });

  it('cada destino del panel tiene una ruta de verdad detras', () => {
    // Un enlace a una ruta que no existe manda al usuario al comodin '**' y de
    // ahi a inicio, sin ningun error que lo delate.
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));

    const fixture = TestBed.createComponent(PanelLateral);
    fixture.detectChanges();
    const rutas = Array.from(fixture.nativeElement.querySelectorAll('nav a')).map((a) =>
      (a as HTMLAnchorElement).getAttribute('href'),
    );
    const declaradas = routes.map((r) => `/${r.path}`);

    for (const ruta of rutas) {
      expect(declaradas).toContain(ruta);
    }
  });
});
