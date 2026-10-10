import { describe, expect, it } from 'vitest';

import { enlaceTelefono, formatoTelefono, normalizarCelular } from './telefono';

describe('telefonos', () => {
  it('un celular se lee en grupos de tres y con el prefijo, guardado o no', () => {
    expect(formatoTelefono('51956781234')).toBe('+51 956 781 234');
    expect(formatoTelefono('956781234')).toBe('+51 956 781 234');
    expect(formatoTelefono('+51 956 781 234')).toBe('+51 956 781 234');
    expect(formatoTelefono(' 956-781-234 ')).toBe('+51 956 781 234');
  });

  it('los fijos llevan su codigo de area entre parentesis', () => {
    expect(formatoTelefono('014567890')).toBe('(01) 456 7890');
    expect(formatoTelefono('5114567890')).toBe('(01) 456 7890');
    expect(formatoTelefono('054123456')).toBe('(054) 123 456');
  });

  it('lo que no se reconoce se deja como se escribio', () => {
    expect(formatoTelefono('ANON-12')).toBe('ANON-12');
    expect(formatoTelefono('98765432')).toBe('98765432');
    expect(formatoTelefono('+1 415 555 0100')).toBe('+1 415 555 0100');
    expect(formatoTelefono(null)).toBe('');
    expect(formatoTelefono(undefined)).toBe('');
  });

  it('el enlace marca en forma internacional, y solo lo que se reconoce', () => {
    expect(enlaceTelefono('956781234')).toBe('tel:+51956781234');
    expect(enlaceTelefono('51956781234')).toBe('tel:+51956781234');
    expect(enlaceTelefono('014567890')).toBe('tel:+5114567890');
    expect(enlaceTelefono('054123456')).toBe('tel:+5154123456');
    expect(enlaceTelefono('98765432')).toBeNull();
    expect(enlaceTelefono('')).toBeNull();
  });

  it('un celular se guarda como lo traen los bots: 51 y nueve cifras', () => {
    expect(normalizarCelular('956 781 234')).toBe('51956781234');
    expect(normalizarCelular('+51 956781234')).toBe('51956781234');
    expect(normalizarCelular('51956781234')).toBe('51956781234');
    expect(normalizarCelular('  ')).toBe('');
    expect(normalizarCelular('014567890')).toBe('014567890');
  });
});
