import { TestBed, type ComponentFixture } from '@angular/core/testing';
import { Component, input, output, provideZonelessChangeDetection } from '@angular/core';
import { of } from 'rxjs';
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';

import { LocalApi } from '../api/api/local.api';
import { MapasApi } from '../api/api/mapas.api';
import { Mapa, type Punto } from './mapa';
import { SelectorUbicacion } from './selector-ubicacion';

/** El mapa de verdad carga Leaflet; aqui basta con algo que reciba y emita puntos. */
@Component({ selector: 'app-mapa', template: '' })
class MapaFalso {
  readonly centro = input<Punto>();
  readonly punto = input<Punto | null>(null);
  readonly origen = input<Punto | null>(null);
  readonly editable = input(false);
  readonly zoom = input(15);
  readonly etiqueta = input('');
  readonly elegir = output<Punto>();
}

/**
 * El selector es lo que convierte un toque en el mapa en una direccion escrita,
 * y una direccion escrita en un punto. Si una de las dos vias se rompe, el mozo
 * no ve ningun error: simplemente la direccion no se completa, o el pin no se
 * mueve. Por eso se prueban las dos, y lo que pasa sin geocodificador.
 */
describe('SelectorUbicacion', () => {
  let fixture: ComponentFixture<SelectorUbicacion>;
  let geocodificacion: boolean;
  const mapas = {
    estadoGeo: vi.fn(),
    direccionEnPunto: vi.fn(),
    sugerirDirecciones: vi.fn(),
    rutaDesdeElLocal: vi.fn(),
  };

  beforeEach(() => {
    localStorage.clear();
    vi.useFakeTimers();
    geocodificacion = true;
    Object.values(mapas).forEach((f) => f.mockReset());
    mapas.estadoGeo.mockImplementation(() => of({ geocodificacion }));
    mapas.rutaDesdeElLocal.mockReturnValue(of({ metros: 3210, segundos: 734 }));

    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: MapasApi, useValue: mapas },
        {
          provide: LocalApi,
          useValue: { obtenerDatosLocal: () => of({ latitud: -12.05, longitud: -77.04 }) },
        },
      ],
    });
    TestBed.overrideComponent(SelectorUbicacion, {
      remove: { imports: [Mapa] },
      add: { imports: [MapaFalso] },
    });
  });

  afterEach(() => vi.useRealTimers());

  function crear(conRuta = false): SelectorUbicacion {
    fixture = TestBed.createComponent(SelectorUbicacion);
    fixture.componentRef.setInput('etiqueta', 'Dirección de entrega');
    fixture.componentRef.setInput('conRuta', conRuta);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const campo = (): HTMLInputElement => fixture.nativeElement.querySelector('input[role=combobox]');
  const nota = (): string =>
    fixture.nativeElement.querySelector('.nota')?.textContent?.trim() ?? '';

  function tocarMapa(punto: Punto): void {
    const mapa = fixture.debugElement.query((d) => d.componentInstance instanceof MapaFalso);
    (mapa.componentInstance as MapaFalso).elegir.emit(punto);
    fixture.detectChanges();
  }

  it('al tocar el mapa pone el punto y completa la direccion', () => {
    const selector = crear();
    mapas.direccionEnPunto.mockReturnValue(
      of({ direccion: 'Jirón Lima 452, Cercado de Lima', latitud: -12.0465, longitud: -77.0429 }),
    );

    tocarMapa({ latitud: -12.0465, longitud: -77.0429 });

    expect(mapas.direccionEnPunto).toHaveBeenCalledWith({ latitud: -12.0465, longitud: -77.0429 });
    expect(selector.punto()).toEqual({ latitud: -12.0465, longitud: -77.0429 });
    expect(selector.direccion()).toBe('Jirón Lima 452, Cercado de Lima');
    expect(campo().value).toBe('Jirón Lima 452, Cercado de Lima');
  });

  it('si el punto no tiene calle, lo escrito se queda y la nota pide escribirla', () => {
    const selector = crear();
    selector.direccion.set('Frente al parque');
    mapas.direccionEnPunto.mockReturnValue(of({ latitud: -12.1, longitud: -77.1 }));

    tocarMapa({ latitud: -12.1, longitud: -77.1 });

    expect(selector.direccion()).toBe('Frente al parque');
    expect(nota()).toContain('No hay una calle con nombre');
  });

  it('al escribir sugiere tras una pausa, y elegir una sugerencia pone el punto', () => {
    const selector = crear();
    mapas.sugerirDirecciones.mockReturnValue(
      of([
        {
          direccion: 'Jirón Lima 452, Cercado de Lima',
          etiqueta: 'Jirón Lima 452, Lima, Peru',
          latitud: -12.0465,
          longitud: -77.0429,
        },
      ]),
    );

    campo().value = 'Jiron Lima';
    campo().dispatchEvent(new Event('input'));
    expect(mapas.sugerirDirecciones).not.toHaveBeenCalled();
    vi.advanceTimersByTime(400);
    fixture.detectChanges();

    expect(mapas.sugerirDirecciones).toHaveBeenCalledWith({ texto: 'Jiron Lima' });
    expect(campo().getAttribute('aria-expanded')).toBe('true');

    // Con el teclado: flecha abajo y Enter, como en cualquier combobox.
    campo().dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));
    campo().dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    fixture.detectChanges();

    expect(selector.direccion()).toBe('Jirón Lima 452, Cercado de Lima');
    expect(selector.punto()).toEqual({ latitud: -12.0465, longitud: -77.0429 });
    expect(campo().getAttribute('aria-expanded')).toBe('false');
  });

  it('sin geocodificador el punto se marca igual, sin buscar la direccion, y lo dice', () => {
    geocodificacion = false;
    const selector = crear();

    tocarMapa({ latitud: -12.1, longitud: -77.1 });

    expect(selector.punto()).toEqual({ latitud: -12.1, longitud: -77.1 });
    expect(mapas.direccionEnPunto).not.toHaveBeenCalled();
    expect(nota()).toContain('falta la clave de OpenRouteService');
  });

  it('en el delivery dice la distancia y el tiempo desde el local', () => {
    const selector = crear(true);
    mapas.direccionEnPunto.mockReturnValue(of({ direccion: 'Av. Abancay 100' }));

    tocarMapa({ latitud: -12.1, longitud: -77.03 });
    fixture.detectChanges();

    expect(selector.punto()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.ruta')?.textContent?.trim()).toBe(
      // En el espanol del Peru el decimal va con punto, como en «S/ 32.00».
      '3.2 km · unos 12 min en auto desde el local',
    );
  });
});
