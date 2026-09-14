import { describe, it, expect } from 'vitest';

import { formatearDuracion } from './formatos';

/**
 * «11803 min» fue lo que salio en el POS y en la cocina con una comanda de hace
 * ocho dias. Un minuto mal convertido no rompe nada: solo hace que alguien lea
 * mal cuanto lleva esperando una mesa.
 */
describe('formatearDuracion', () => {
  it('por debajo de una hora se queda en minutos', () => {
    expect(formatearDuracion(0)).toBe('0 min');
    expect(formatearDuracion(47)).toBe('47 min');
  });

  it('pasa a horas y omite los minutos en punto', () => {
    expect(formatearDuracion(60)).toBe('1 h');
    expect(formatearDuracion(185)).toBe('3 h 5 min');
  });

  it('pasado un dia cuenta dias y horas, sin minutos', () => {
    expect(formatearDuracion(1440)).toBe('1 d');
    expect(formatearDuracion(11803)).toBe('8 d 4 h');
  });

  it('sin dato devuelve la raya, no «NaN min»', () => {
    expect(formatearDuracion(null)).toBe('—');
    expect(formatearDuracion(undefined)).toBe('—');
    expect(formatearDuracion(Number.NaN)).toBe('—');
  });
});
