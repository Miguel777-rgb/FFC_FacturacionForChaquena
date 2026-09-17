import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach, vi } from 'vitest';

import { LecturaCartaSeccion } from './lectura-carta.seccion';

/**
 * La foto se reduce en el navegador antes de subirla. En la prueba no hay lienzo
 * de verdad: se finge lo justo para que esa reduccion devuelva algo.
 */
function fingirLienzo(): void {
  Object.assign(globalThis, {
    createImageBitmap: vi.fn(async () => ({ width: 1200, height: 1600, close: () => {} })),
  });
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({ drawImage: () => {} })) as never;
  HTMLCanvasElement.prototype.toBlob = function (cb: BlobCallback) {
    cb(new Blob(['foto'], { type: 'image/jpeg' }));
  };
}

const LECTURA = {
  dudosos: 1,
  secciones: [
    {
      nombre: 'Parrillas',
      platillos: [
        {
          nombre: 'Parrilla de Res',
          precio: 21,
          descripcion: 'Corte de res con salchicha.',
          vegetariano: false,
          dudoso: false,
        },
        {
          nombre: 'Anticucho de Res',
          precio: 14,
          descripcion: 'Porción de res a la parrilla.',
          vegetariano: false,
          dudoso: true,
          recorte: 'data:image/jpeg;base64,abc',
        },
      ],
    },
  ],
  complementos: [{ nombre: 'Con chaufa', precio: 5, tipo: 'OTROS', dudoso: false }],
};

function crear() {
  const fixture = TestBed.createComponent(LecturaCartaSeccion);
  fixture.detectChanges();
  const http = TestBed.inject(HttpTestingController);
  http.expectOne((r) => r.url.endsWith('/api/v1/carta/lector')).flush({ disponible: true });
  http
    .expectOne((r) => r.url.endsWith('/api/v1/categorias'))
    .flush([{ id: 3, nombre: 'Parrillas' }]);
  http.expectOne((r) => r.url.includes('/api/v1/platillos')).flush({
    contenido: [
      {
        id: 'p-1',
        nombre: 'Parrilla de Res',
        precioVentaBase: 20,
        descripcion: 'Corte de res con salchicha.',
      },
    ],
  });
  fixture.detectChanges();
  return fixture;
}

/** Elegir una foto: jsdom no arma un `FileList`, asi que se le pone al input. */
async function leerUnaFoto(fixture: ReturnType<typeof crear>, respuesta: object = LECTURA) {
  const pagina: HTMLElement = fixture.nativeElement;
  const entrada = pagina.querySelector('input[type="file"]') as HTMLInputElement;
  Object.defineProperty(entrada, 'files', {
    value: [new File(['x'], 'carta1.jpg', { type: 'image/jpeg' })],
    configurable: true,
  });
  entrada.dispatchEvent(new Event('change'));
  fixture.detectChanges();

  (pagina.querySelectorAll('.barra button')[1] as HTMLButtonElement).click();
  // La compresion es asincrona: se deja terminar antes de responder la subida.
  await new Promise((r) => setTimeout(r, 0));
  TestBed.inject(HttpTestingController)
    .expectOne((r) => r.url.endsWith('/api/v1/carta/lecturas'))
    .flush(respuesta);
  await new Promise((r) => setTimeout(r, 0));
  fixture.detectChanges();
}

describe('LecturaCartaSeccion', () => {
  beforeEach(() => {
    fingirLienzo();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  it('sin lector en el servidor no deja elegir fotos y dice por que', () => {
    const fixture = TestBed.createComponent(LecturaCartaSeccion);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url.endsWith('/api/v1/carta/lector'))
      .flush({ disponible: false, motivo: 'falta el idioma espanol' });
    http.expectOne((r) => r.url.endsWith('/api/v1/categorias')).flush([]);
    http.expectOne((r) => r.url.includes('/api/v1/platillos')).flush({ contenido: [] });
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(pagina.textContent).toContain('falta el idioma espanol');
    expect((pagina.querySelector('.barra button') as HTMLButtonElement).disabled).toBe(true);
  });

  it('lo leido se revisa antes de guardar: dice que va a pasar con cada fila', async () => {
    const fixture = crear();
    await leerUnaFoto(fixture);
    const pagina: HTMLElement = fixture.nativeElement;

    // El platillo que ya esta en la carta anuncia el cambio de precio.
    expect(pagina.textContent).toContain('S/ 20 → S/ 21');
    // El que no esta se va a crear.
    expect(pagina.textContent).toContain('Se crea');
    // La fila dudosa trae el recorte de la foto de donde salio.
    expect(pagina.querySelector('img.recorte')).not.toBeNull();
    // La seccion leida se reconocio en la carta, asi que no pide nombre nuevo.
    const seccion = pagina.querySelector('.cabecera-seccion select') as HTMLSelectElement;
    expect(seccion.value).toBe('3');
  });

  it('importa solo lo marcado, y la fila dudosa no va marcada de entrada', async () => {
    const fixture = crear();
    await leerUnaFoto(fixture);
    const pagina: HTMLElement = fixture.nativeElement;

    const casillas = Array.from(
      pagina.querySelectorAll('tbody input[type="checkbox"]'),
    ) as HTMLInputElement[];
    // Dos por fila (importar y vegetariano) mas la del adicional.
    expect(casillas[0].checked).toBe(true);
    expect(casillas[2].checked).toBe(false);

    (pagina.querySelector('.barra.final button') as HTMLButtonElement).click();
    fixture.detectChanges();

    const peticion = TestBed.inject(HttpTestingController).expectOne((r) =>
      r.url.endsWith('/api/v1/carta/importaciones'),
    );
    const cuerpo = peticion.request.body;
    expect(cuerpo.secciones[0].categoriaId).toBe(3);
    expect(cuerpo.secciones[0].platillos).toHaveLength(1);
    expect(cuerpo.secciones[0].platillos[0].nombre).toBe('Parrilla de Res');
    expect(cuerpo.complementos[0].nombre).toBe('Con chaufa');
  });

  it('una seccion que no existe se ofrece como nueva con el nombre leido', async () => {
    const fixture = crear();
    await leerUnaFoto(fixture, {
      secciones: [{ nombre: 'Broaster', platillos: [{ nombre: 'Pollo Broaster', precio: 12 }] }],
      complementos: [],
      dudosos: 0,
    });
    const pagina: HTMLElement = fixture.nativeElement;

    const seccion = pagina.querySelector('.cabecera-seccion select') as HTMLSelectElement;
    expect(seccion.value).toBe('');
    const nombre = pagina.querySelector('.cabecera-seccion input[type="text"]') as HTMLInputElement;
    expect(nombre.value).toBe('Broaster');
  });
});
