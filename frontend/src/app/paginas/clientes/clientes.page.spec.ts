import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, input, model, provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { ClientesPage } from './clientes.page';
import type { Punto } from '../../disenio/mapa';
import { SelectorUbicacion } from '../../disenio/selector-ubicacion';
import { SesionService } from '../../nucleo/sesion/sesion.service';

/** El selector de verdad carga Leaflet y pregunta al geocodificador; aqui basta con sus enlaces. */
@Component({ selector: 'app-selector-ubicacion', template: '' })
class SelectorFalso {
  readonly etiqueta = input('');
  readonly direccion = model('');
  readonly punto = model<Punto | null>(null);
}

function tokenDe(cargo: string, roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'quien@chaquena.pe',
    username: 'quien',
    cargo,
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
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
  TestBed.overrideComponent(ClientesPage, {
    remove: { imports: [SelectorUbicacion] },
    add: { imports: [SelectorFalso] },
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

function boton(raiz: HTMLElement, texto: string): HTMLButtonElement {
  const encontrado = Array.from(raiz.querySelectorAll('button')).find(
    (b) => b.textContent!.replace(/\s+/g, ' ').trim() === texto,
  );
  if (!encontrado) throw new Error(`No hay un boton «${texto}»`);
  return encontrado;
}

/**
 * Lo que hace el navegador al pulsar un boton del pie: enviar el formulario al
 * que apunta su atributo `form`. jsdom no sigue esa asociacion al hacer clic.
 */
function enviar(raiz: HTMLElement, texto: string): void {
  const pulsado = boton(raiz, texto);
  const formulario = raiz.querySelector(`form#${pulsado.getAttribute('form')}`);
  if (!formulario || pulsado.type !== 'submit')
    throw new Error(`«${texto}» no envia ningun formulario`);
  formulario.dispatchEvent(new Event('submit'));
}

/** Escribe en el campo cuyo rotulo empieza por `rotulo`, como lo haria alguien con el teclado. */
function escribir(raiz: HTMLElement, rotulo: string, valor: string): void {
  const etiqueta = Array.from(raiz.querySelectorAll('label.campo')).find((l) =>
    l.querySelector('span')!.textContent!.trim().startsWith(rotulo),
  );
  if (!etiqueta) throw new Error(`No hay un campo «${rotulo}»`);
  const campo = etiqueta.querySelector('input')!;
  campo.value = valor;
  campo.dispatchEvent(new Event('input'));
}

/** La ficha va en el cajon; el alta y la correccion, en su propio dialogo. */
const FICHA = 'app-dialogo:not(.dialogo-cliente)';
const FORMULARIO = 'app-dialogo.dialogo-cliente';

const ROSA = {
  id: 'c1',
  nombres: 'Rosa',
  apellidos: 'Quispe',
  dni: '44556677',
  puntosFidelidad: 12,
};

const PARTES_DE_LA_FICHA: Array<[string, object]> = [
  // Las rutas especificas antes que la del listado, que las contiene a todas.
  [
    '/fidelizacion',
    { puntosFidelidad: 12, calificacionesRequeridas: 5, calificacionesFaltantes: 2 },
  ],
  ['/cupones', []],
  ['/preferencias', { platillosFrecuentes: [], notasHabituales: [] }],
  ['/empresas', []],
  ['/ordenes', []],
];

function abrirFicha(
  fixture: ReturnType<typeof crear>['fixture'],
  http: HttpTestingController,
  cliente: object = ROSA,
) {
  responder(http, [['/api/v1/clientes', { contenido: [cliente], totalPaginas: 1 }]]);
  fixture.detectChanges();

  (fixture.nativeElement.querySelector('td.acciones button') as HTMLButtonElement).click();
  fixture.detectChanges();

  responder(http, PARTES_DE_LA_FICHA);
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

    expect(textos(fixture.nativeElement, 'td.vacio')).toEqual([
      'Todavía no hay clientes registrados.',
    ]);
  });

  it('la ficha cuenta lo que le falta para el cupon, y la caja corrige datos pero no bloquea', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);
    abrirFicha(fixture, http);

    const pagina: HTMLElement = fixture.nativeElement;
    expect(pagina.querySelector('.progreso')!.getAttribute('aria-valuenow')).toBe('60');
    expect(textos(pagina, '.ficha > p')).toContain(
      'Le faltan 2 calificaciones para el próximo cupón.',
    );
    expect(textos(pagina, `${FICHA} [pie] button`)).toEqual(['Editar datos']);
    http.verify();
  });

  it('el administrador si puede bloquear por fraude', () => {
    const { fixture, http } = crear('ADMINISTRADOR', ['ADMIN']);
    abrirFicha(fixture, http);

    expect(textos(fixture.nativeElement, `${FICHA} [pie] button`)).toEqual([
      'Editar datos',
      'Bloquear cliente',
    ]);
    http.verify();
  });

  it('el celular se lee agrupado, con el prefijo y como enlace para llamar', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);
    responder(http, [
      ['/api/v1/clientes', { contenido: [{ ...ROSA, celular: '51956781234' }], totalPaginas: 1 }],
    ]);
    fixture.detectChanges();

    const enlace = fixture.nativeElement.querySelector('td app-telefono a') as HTMLAnchorElement;
    expect(enlace.textContent!.trim()).toBe('+51 956 781 234');
    expect(enlace.getAttribute('href')).toBe('tel:+51956781234');
    expect(enlace.getAttribute('aria-label')).toBe('Llamar al +51 956 781 234');
  });

  it('el alta no sale con datos que faltan y lo dice debajo de cada campo', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);
    responder(http, [['/api/v1/clientes', { contenido: [], totalPaginas: 0 }]]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    boton(pagina, 'Nuevo cliente').click();
    fixture.detectChanges();
    escribir(pagina, 'Celular', '98765');
    fixture.detectChanges();
    pagina.querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    expect(http.match((r) => r.method === 'POST')).toEqual([]);
    expect(textos(pagina, '.error-campo')).toEqual([
      'Este dato es obligatorio.',
      'Este dato es obligatorio.',
      'Este dato es obligatorio.',
      'El celular peruano son nueve dígitos y empieza en 9; se admite el 51 delante.',
    ]);
    expect(pagina.querySelector('input[aria-invalid="true"]')).not.toBeNull();
  });

  it('el alta guarda el celular como lo traen los bots y abre la ficha del cliente nuevo', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);
    responder(http, [['/api/v1/clientes', { contenido: [], totalPaginas: 0 }]]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    boton(pagina, 'Nuevo cliente').click();
    fixture.detectChanges();
    expect(textos(pagina, `${FORMULARIO} h2`)).toEqual(['Nuevo cliente']);

    escribir(pagina, 'Documento de identidad', '70123456');
    escribir(pagina, 'Nombres', 'Ana Lucía');
    escribir(pagina, 'Apellidos', 'Torres Vega');
    escribir(pagina, 'Celular', '956 781 234');
    fixture.detectChanges();
    enviar(pagina, 'Registrar cliente');
    fixture.detectChanges();

    const alta = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/v1/clientes'));
    expect(alta.request.body).toEqual({
      dni: '70123456',
      nombres: 'Ana Lucía',
      apellidos: 'Torres Vega',
      celular: '51956781234',
    });
    alta.flush({
      id: 'c9',
      dni: '70123456',
      nombres: 'Ana Lucía',
      apellidos: 'Torres Vega',
      celular: '51956781234',
    });
    fixture.detectChanges();
    responder(http, [
      ...PARTES_DE_LA_FICHA,
      ['/api/v1/clientes', { contenido: [], totalPaginas: 1 }],
    ]);
    fixture.detectChanges();

    // El formulario se cierra y queda la ficha del cliente nuevo.
    expect(pagina.querySelector('#formulario-cliente')).toBeNull();
    expect(textos(pagina, `${FICHA} h2`)).toEqual(['Ana Lucía Torres Vega']);
    expect(textos(pagina, '.datos dd')).toContain('+51 956 781 234');
    http.verify();
  });

  it('corregir los datos reenvia la direccion tal como estaba', () => {
    const { fixture, http } = crear('CAJERO', ['CAJA']);
    const conDireccion = {
      ...ROSA,
      celular: '51987654321',
      direccionHabitual: 'Av. Ejército 512, Yanahuara',
      latitud: -16.39,
      longitud: -71.54,
    };
    abrirFicha(fixture, http, conDireccion);

    const pagina: HTMLElement = fixture.nativeElement;
    boton(pagina, 'Editar datos').click();
    fixture.detectChanges();
    // Se abre encima de la ficha, que sigue debajo.
    expect(textos(pagina, `${FORMULARIO} h2`)).toEqual(['Editar a Rosa Quispe']);
    expect(textos(pagina, `${FICHA} h2`)).toEqual(['Rosa Quispe']);
    // Al corregir no se ofrece la direccion: tiene su propia seccion en la ficha.
    expect(pagina.querySelector(`${FORMULARIO} app-selector-ubicacion`)).toBeNull();

    escribir(pagina, 'Celular', '+51 912 345 678');
    fixture.detectChanges();
    enviar(pagina, 'Guardar');
    fixture.detectChanges();

    const correccion = http.expectOne(
      (r) => r.method === 'PUT' && r.url.endsWith('/api/v1/clientes/c1'),
    );
    expect(correccion.request.body).toEqual({
      dni: '44556677',
      nombres: 'Rosa',
      apellidos: 'Quispe',
      celular: '51912345678',
      direccionHabitual: 'Av. Ejército 512, Yanahuara',
      latitud: -16.39,
      longitud: -71.54,
    });
    correccion.flush({ ...conDireccion, celular: '51912345678' });
    fixture.detectChanges();

    expect(pagina.querySelector('#formulario-cliente')).toBeNull();
    expect(textos(pagina, '.datos dd')).toContain('+51 912 345 678');
    http.verify();
  });
});
