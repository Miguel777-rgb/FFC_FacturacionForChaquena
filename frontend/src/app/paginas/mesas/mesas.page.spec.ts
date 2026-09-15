import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';

import { MesasPage } from './mesas.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

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
  TestBed.inject(SesionService).abrir(tokenDe(cargo, roles));
  const fixture = TestBed.createComponent(MesasPage);
  fixture.detectChanges();
  return { fixture, http: TestBed.inject(HttpTestingController) };
}

function textos(raiz: HTMLElement, selector: string): string[] {
  return Array.from(raiz.querySelectorAll(selector)).map((e) =>
    (e as HTMLElement).textContent!.replace(/\s+/g, ' ').trim(),
  );
}

function botonCon(raiz: HTMLElement, texto: string): HTMLButtonElement {
  return Array.from(raiz.querySelectorAll('button')).find((b) =>
    b.textContent!.includes(texto),
  ) as HTMLButtonElement;
}

describe('MesasPage', () => {
  const matchMediaOriginal = window.matchMedia;

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
  });

  afterEach(() => {
    window.matchMedia = matchMediaOriginal;
  });

  it('en el celular agrupa por zona, deja las mesas sin zona al final y dice cuanto lleva la ocupada', () => {
    const { fixture, http } = crear('MOZO', ['MOZO']);

    http
      .expectOne((r) => r.url.endsWith('/api/v1/mesas'))
      .flush([
        {
          id: 'm10',
          numero: '10',
          zona: 'Terraza',
          estado: 'LIBRE',
          capacidad: 4,
          reservaProxima: {
            nombre: 'Rosa Quispe',
            inicio: '2026-09-14T21:00:00-05:00',
            estado: 'PENDIENTE',
          },
        },
        { id: 'm2', numero: '2', zona: 'Terraza', estado: 'OCUPADA', capacidad: 2 },
        { id: 'm1', numero: '1', zona: 'Salón', estado: 'LIBRE', capacidad: 4 },
        { id: 'm9', numero: '9', estado: 'INHABILITADA' },
      ]);
    http
      .expectOne((r) => r.url.endsWith('/api/v1/ordenes/activas'))
      .flush([{ id: 'o1', mesaId: 'm2', montoTotal: 45.5, minutosTranscurridos: 20 }]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '.bloque > h2')).toEqual([
      'Salón 1 mesa',
      'Terraza 2 mesas',
      'Sin zona 1 mesa',
    ]);
    // La 2 antes que la 10, aunque el numero llegue como texto.
    expect(textos(pagina, '.bloque:nth-of-type(2) .numero')).toEqual(['2', '10']);
    expect(textos(pagina, '.mesa[data-estado="OCUPADA"] .consumo span')).toEqual([
      'S/ 45.50',
      '20 min',
    ]);
    // Libre, pero con una reserva para mas tarde: lo dice sin apagar la mesa.
    expect(textos(pagina, '.reserva.luego')[0]).toContain('Rosa Quispe');
    // Dar de alta una mesa es del administrador.
    expect(textos(pagina, '.cabecera button')).not.toContain('Nueva mesa');
    expect(pagina.querySelector('.plano')).toBeNull();
    http.verify();
  });

  it('sin mesas lo dice, y al administrador le ofrece la primera', () => {
    const { fixture, http } = crear('ADMINISTRADOR', ['ADMIN']);

    http.expectOne((r) => r.url.endsWith('/api/v1/mesas')).flush([]);
    http.expectOne((r) => r.url.endsWith('/api/v1/ordenes/activas')).flush([]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '.seccion > .vacio')).toEqual(['Todavía no hay mesas registradas.']);
    expect(textos(pagina, '.cabecera button')).toContain('Nueva mesa');
    http.verify();
  });

  it('en PC el administrador mueve una mesa con las flechas, sin pisar otra, y guarda solo esa', () => {
    window.matchMedia = ((consulta: string) => ({
      matches: true,
      media: consulta,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    })) as unknown as typeof window.matchMedia;
    const { fixture, http } = crear('ADMINISTRADOR', ['ADMIN']);

    http
      .expectOne((r) => r.url.endsWith('/api/v1/mesas'))
      .flush([
        {
          id: 'm1',
          numero: 'M1',
          zona: 'Salón',
          estado: 'LIBRE',
          columna: 0,
          fila: 0,
          ancho: 2,
          alto: 2,
          forma: 'CUADRADA',
        },
        {
          id: 'm2',
          numero: 'M2',
          zona: 'Salón',
          estado: 'LIBRE',
          columna: 3,
          fila: 0,
          ancho: 2,
          alto: 2,
          forma: 'REDONDA',
        },
      ]);
    http.expectOne((r) => r.url.endsWith('/api/v1/ordenes/activas')).flush([]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    expect(textos(pagina, '.plano .mesa.redonda .numero')).toEqual(['M2']);

    botonCon(pagina, 'Editar plano').click();
    fixture.detectChanges();

    const m1 = pagina.querySelector('.plano .mesa') as HTMLButtonElement;
    m1.click();
    fixture.detectChanges();
    // Una celda a la derecha cabe; la segunda pisaria a M2 y la mesa se queda donde estaba.
    m1.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    m1.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();

    botonCon(pagina, 'Guardar plano').click();
    const put = http.expectOne((r) => r.method === 'PUT' && r.url.endsWith('/api/v1/mesas/plano'));
    expect(put.request.body).toEqual({
      mesas: [{ id: 'm1', columna: 1, fila: 0, ancho: 2, alto: 2, forma: 'CUADRADA' }],
    });
  });

  it('en la agenda ofrece solo los pasos que cada reserva permite', () => {
    const { fixture, http } = crear('MOZO', ['MOZO']);
    http.expectOne((r) => r.url.endsWith('/api/v1/mesas')).flush([]);
    http.expectOne((r) => r.url.endsWith('/api/v1/ordenes/activas')).flush([]);
    fixture.detectChanges();

    const pagina: HTMLElement = fixture.nativeElement;
    (pagina.querySelectorAll('.pestanas button')[1] as HTMLButtonElement).click();
    fixture.detectChanges();

    http
      .expectOne((r) => r.url.endsWith('/api/v1/reservas'))
      .flush([
        {
          id: 'r1',
          mesaNumero: 'T4',
          zona: 'Terraza',
          nombre: 'Familia Zevallos',
          personas: 6,
          inicio: '2026-09-14T20:00:00-05:00',
          fin: '2026-09-14T22:00:00-05:00',
          estado: 'CONFIRMADA',
          transicionesPermitidas: ['CUMPLIDA', 'NO_ASISTIO', 'CANCELADA'],
        },
        {
          id: 'r2',
          mesaNumero: 'M2',
          nombre: 'Carlos Mendoza',
          personas: 4,
          inicio: '2026-09-14T13:00:00-05:00',
          fin: '2026-09-14T14:30:00-05:00',
          estado: 'CUMPLIDA',
          transicionesPermitidas: [],
        },
      ]);
    fixture.detectChanges();

    expect(textos(pagina, 'tbody tr:nth-child(1) td.acciones button')).toEqual([
      'Llegaron',
      'No vino',
      'Cancelar reserva',
    ]);
    expect(textos(pagina, 'tbody tr:nth-child(2) td.acciones button')).toEqual([]);

    botonCon(pagina, 'Llegaron').click();
    const patch = http.expectOne(
      (r) => r.method === 'PATCH' && r.url.endsWith('/api/v1/reservas/r1/estado'),
    );
    expect(patch.request.urlWithParams).toContain('estado=CUMPLIDA');
  });
});
