import { describe, it, expect } from 'vitest';

import { PATRONES, problemaDe } from './patrones';

/**
 * Las expresiones regulares se prueban por sus dos lados: lo que tienen que
 * aceptar y, sobre todo, lo que tienen que rechazar. Un patron que solo se
 * prueba con valores buenos pasa aunque acepte cualquier cosa.
 */
describe('patrones de validacion', () => {
  it('el DNI son ocho digitos exactos', () => {
    expect(PATRONES.dni.test('70123456')).toBe(true);
    expect(PATRONES.dni.test('7012345')).toBe(false);
    expect(PATRONES.dni.test('701234567')).toBe(false);
    expect(PATRONES.dni.test('7012345a')).toBe(false);
    // Las anclas: sin ellas, esto pasaria por un DNI valido.
    expect(PATRONES.dni.test('mi dni es 70123456, ya')).toBe(false);
  });

  it('el celular peruano empieza en 9, con o sin el prefijo del pais', () => {
    expect(PATRONES.celular.test('987654321')).toBe(true);
    expect(PATRONES.celular.test('51987654321')).toBe(true);
    expect(PATRONES.celular.test('+51 987654321')).toBe(true);
    // Un fijo de Lima no es un celular.
    expect(PATRONES.celular.test('014567890')).toBe(false);
    expect(PATRONES.celular.test('98765432')).toBe(false);
  });

  it('el correo pide arroba y dominio con punto', () => {
    expect(PATRONES.correo.test('mozo.uno@chaquena.pe')).toBe(true);
    expect(PATRONES.correo.test('a+b@sub.dominio.com')).toBe(true);
    expect(PATRONES.correo.test('sin-arroba.pe')).toBe(false);
    expect(PATRONES.correo.test('sin@dominio')).toBe(false);
    expect(PATRONES.correo.test('con espacio@chaquena.pe')).toBe(false);
  });

  it('el nombre admite tildes, ñ, apostrofo y guion, pero no cifras', () => {
    expect(PATRONES.nombre.test('María José')).toBe(true);
    expect(PATRONES.nombre.test('Muñoz')).toBe(true);
    expect(PATRONES.nombre.test("D'Angelo")).toBe(true);
    expect(PATRONES.nombre.test('García-López')).toBe(true);
    expect(PATRONES.nombre.test('Mozo1')).toBe(false);
    expect(PATRONES.nombre.test('Ana  Luisa')).toBe(false);
    expect(PATRONES.nombre.test('-Ana')).toBe(false);
  });

  it('el usuario empieza por letra minuscula y no admite mayusculas', () => {
    expect(PATRONES.usuario.test('mozo1')).toBe(true);
    expect(PATRONES.usuario.test('caja.norte')).toBe(true);
    expect(PATRONES.usuario.test('Mozo1')).toBe(false);
    expect(PATRONES.usuario.test('1mozo')).toBe(false);
    expect(PATRONES.usuario.test('ab')).toBe(false);
  });

  it('la contrasena exige mayuscula, minuscula y cifra en cualquier orden', () => {
    expect(PATRONES.contrasena.test('Chaquena2001')).toBe(true);
    expect(PATRONES.contrasena.test('1aB45678')).toBe(true);
    expect(PATRONES.contrasena.test('chaquena2001')).toBe(false);
    expect(PATRONES.contrasena.test('CHAQUENA2001')).toBe(false);
    expect(PATRONES.contrasena.test('Abc1234')).toBe(false);
  });

  it('un campo vacio no es un problema de formato', () => {
    expect(problemaDe('dni', '')).toBeNull();
    expect(problemaDe('dni', '   ')).toBeNull();
    expect(problemaDe('dni', '70123456')).toBeNull();
    expect(problemaDe('dni', '701')).toBe('validacion.dni');
  });

  it('los espacios de alrededor no cuentan como error', () => {
    expect(problemaDe('correo', '  mozo@chaquena.pe  ')).toBeNull();
  });
});
