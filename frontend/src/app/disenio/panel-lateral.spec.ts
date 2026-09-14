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

function texto(fixture: { nativeElement: HTMLElement }, selector: string): string[] {
  return Array.from(fixture.nativeElement.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.trim(),
  );
}

/** Los enlaces del panel, en el orden en que se pintan. */
function destinosVisibles(): string[] {
  const fixture = TestBed.createComponent(PanelLateral);
  fixture.detectChanges();
  return texto(fixture, 'nav a');
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

  it('le ensena al administrador los cuatro puestos y las cinco pantallas de gestion', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));

    expect(destinosVisibles()).toEqual([
      'Tomar comanda',
      'Cocina',
      'Caja',
      'Despacho',
      'Menú',
      'Inventario',
      'Ventas y reportes',
      'Personal',
      'Configuración',
    ]);
  });

  it('agrupa en operacion y gestion, y no pinta un grupo vacio', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));

    const fixture = TestBed.createComponent(PanelLateral);
    fixture.detectChanges();

    expect(texto(fixture, '.rotulo-grupo')).toEqual(['Gestión']);
  });

  it('la caja ve su puesto y las ventas, y nada mas', () => {
    // Ventas y reportes entra porque el endpoint del tablero admite CAJA; el
    // personal no, porque repartir permisos es solo del administrador.
    TestBed.inject(SesionService).abrir(tokenDe('CAJERO', ['CAJA']));

    expect(destinosVisibles()).toEqual(['Caja', 'Ventas y reportes']);
  });

  it('al almacenero le ofrece el menu y el inventario, no la configuracion', () => {
    // Lo que fueron las pestanas exclusivas del administrador en la trastienda
    // (parametros, bots y eventos) no pueden reaparecer para el almacen.
    TestBed.inject(SesionService).abrir(tokenDe('ALMACENERO', ['ALMACEN']));

    expect(destinosVisibles()).toEqual(['Menú', 'Inventario']);
  });

  it('cambiar de idioma repinta el panel sin recargar', async () => {
    // Es la razon de llamar a `t(...)` desde la plantilla en vez de usar un
    // pipe: el consumidor reactivo ve la senal del idioma y vuelve a pintar.
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));
    const i18n = TestBed.inject(I18nService);

    const fixture = TestBed.createComponent(PanelLateral);
    fixture.detectChanges();

    expect(texto(fixture, 'nav a')[0]).toBe('Tomar comanda');

    await i18n.cambiar('en');
    fixture.detectChanges();
    expect(texto(fixture, 'nav a')[0]).toBe('Take an order');

    await i18n.cambiar('pt');
    fixture.detectChanges();
    expect(texto(fixture, 'nav a')[0]).toBe('Abrir comanda');
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
    const conPantalla = routes.filter((r) => r.loadComponent).map((r) => `/${r.path}`);

    for (const ruta of rutas) {
      expect(conPantalla).toContain(ruta);
    }
  });

  it('en el cajon del celular no se pliega y avisa al pulsar un destino', () => {
    TestBed.inject(SesionService).abrir(tokenDe('ADMINISTRADOR', ['ADMIN']));
    localStorage.setItem('chaquena.panel.plegado', 'true');

    const fixture = TestBed.createComponent(PanelLateral);
    fixture.componentRef.setInput('cajon', true);
    let avisos = 0;
    fixture.componentInstance.navego.subscribe(() => avisos++);
    fixture.detectChanges();

    const panel: HTMLElement = fixture.nativeElement;
    expect(panel.querySelector('aside')!.classList.contains('plegado')).toBe(false);
    expect(panel.querySelector('button.plegar')).toBeNull();

    (panel.querySelector('nav a') as HTMLAnchorElement).click();
    expect(avisos).toBe(1);
  });
});
