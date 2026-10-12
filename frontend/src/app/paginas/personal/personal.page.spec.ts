import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach } from 'vitest';

import { PersonalPage } from './personal.page';
import { SesionService } from '../../nucleo/sesion/sesion.service';

function tokenDeAdmin(): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'admin@chaquena.pe',
    username: 'admin',
    cargo: 'ADMINISTRADOR',
    roles: ['ADMIN'],
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
  return `${b64({ alg: 'HS256', typ: 'JWT' })}.${b64(claims)}.firma-que-no-se-valida`;
}

/** El orden del servidor: alfabetico, asi que ADMINISTRADOR va primero. */
const CARGOS = [
  { id: 5, nombre: 'ADMINISTRADOR', rolesNombres: ['ADMIN'] },
  { id: 4, nombre: 'MOZO', rolesNombres: ['MOZO'] },
];

const ROSA = {
  id: 't1',
  nombres: 'Rosa',
  apellidos: 'Huamán',
  dni: '70000002',
  celular: '987654321',
  username: 'mozo1',
  cargoId: 4,
  cargoNombre: 'MOZO',
  activo: true,
};

/** Responde todo lo pendiente por el primer trozo de URL que coincida. */
function responder(http: HttpTestingController, cuerpos: Array<[string, object]>): void {
  for (const peticion of http.match(() => true)) {
    const cuerpo = cuerpos.find(([trozo]) => peticion.request.url.includes(trozo));
    peticion.flush(cuerpo ? cuerpo[1] : null);
  }
}

function boton(raiz: HTMLElement, texto: string): HTMLButtonElement {
  const encontrado = Array.from(raiz.querySelectorAll('button')).find(
    (b) =>
      b.textContent!.replace(/\s+/g, ' ').trim() === texto ||
      b.getAttribute('aria-label') === texto,
  );
  if (!encontrado) throw new Error(`No hay un boton «${texto}»`);
  return encontrado;
}

function escribir(raiz: HTMLElement, rotulo: string, valor: string): void {
  const campo = raiz.querySelector<HTMLInputElement>(`input[aria-label="${rotulo}"]`);
  if (!campo) throw new Error(`No hay un campo «${rotulo}»`);
  campo.value = valor;
  campo.dispatchEvent(new Event('input'));
}

/** Lo que el select ensena, que es lo que la persona cree que va a enviar. */
function mostrado(select: HTMLSelectElement): string {
  return select.options[select.selectedIndex]?.text.trim() ?? '';
}

/**
 * El cargo del alta y de la edicion. El select se veia vacio mientras la pagina
 * guardaba otro cargo: en el alta era ADMINISTRADOR, el primero de la lista, y
 * un alta sin elegir cargo creaba un administrador.
 */
describe('PersonalPage: el cargo elegido', () => {
  let raiz: HTMLElement;
  let http: HttpTestingController;
  let detectar: () => void;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    TestBed.inject(SesionService).abrir(tokenDeAdmin());
    const fixture = TestBed.createComponent(PersonalPage);
    fixture.detectChanges();
    http = TestBed.inject(HttpTestingController);
    responder(http, [
      ['/cargos', CARGOS],
      ['/roles', []],
      ['/trabajadores', { contenido: [ROSA] }],
    ]);
    detectar = () => fixture.detectChanges();
    detectar();
    raiz = fixture.nativeElement as HTMLElement;
  });

  it('el alta empieza sin cargo y no se envia hasta elegir uno', () => {
    boton(raiz, 'Nuevo trabajador').click();
    detectar();

    const cargo = raiz.querySelector<HTMLSelectElement>('select[aria-label="Cargo"]')!;
    expect(cargo.value).toBe('');
    expect(mostrado(cargo)).toBe('Cargo…');

    escribir(raiz, 'Nombres', 'Ana');
    escribir(raiz, 'Apellidos', 'Quispe');
    escribir(raiz, 'Documento de identidad', '40000001');
    escribir(raiz, 'Celular', '987650321');
    escribir(raiz, 'Usuario', 'ana.quispe');
    escribir(raiz, 'Contraseña', 'Secreta2026');
    boton(raiz, 'Dar de alta').click();
    http.expectNone((p) => p.method === 'POST');

    cargo.value = '4';
    cargo.dispatchEvent(new Event('change'));
    detectar();
    expect(mostrado(cargo)).toBe('MOZO');

    boton(raiz, 'Dar de alta').click();
    const alta = http.expectOne((p) => p.method === 'POST' && p.url.includes('/trabajadores'));
    expect(alta.request.body.cargoId).toBe(4);
  });

  it('la edicion ensena el cargo que la persona ya tiene', () => {
    boton(raiz, 'Editar los datos de Rosa Huamán').click();
    detectar();

    const selects = raiz.querySelectorAll<HTMLSelectElement>('select[aria-label="Cargo"]');
    const cargo = selects[selects.length - 1];
    expect(cargo.value).toBe('4');
    expect(mostrado(cargo)).toBe('MOZO');
  });
});
