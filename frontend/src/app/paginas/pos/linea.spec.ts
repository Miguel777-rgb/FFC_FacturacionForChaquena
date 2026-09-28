import { describe, it, expect } from 'vitest';

import type { ComplementoResponseDto, PlatilloDisponibleDto } from '../../api';
import {
  agregarLinea,
  precioDeLinea,
  reemplazarLinea,
  type ComplementoElegido,
  type LineaComanda,
} from './linea';

const lomo: PlatilloDisponibleDto = { id: 'lomo', nombre: 'Lomo Saltado', precioVentaBase: 32 };
const ceviche: PlatilloDisponibleDto = { id: 'ceviche', nombre: 'Ceviche', precioVentaBase: 28 };
const gaseosa: ComplementoResponseDto = { id: 'gas', nombre: 'Gaseosa', precioAdicional: 5 };
const arroz: ComplementoResponseDto = { id: 'arr', nombre: 'Arroz extra', precioAdicional: 4 };

const con = (...extras: [ComplementoResponseDto, number][]): ComplementoElegido[] =>
  extras.map(([complemento, cantidad]) => ({ complemento, cantidad }));

function pedir(
  lineas: LineaComanda[],
  platillo: PlatilloDisponibleDto,
  nota = '',
  cantidad = 1,
  complementos: ComplementoElegido[] = [],
): LineaComanda[] {
  return agregarLinea(lineas, platillo, { cantidad, nota, complementos });
}

/**
 * La observacion era de la linea y la linea era del platillo: «sin cebolla» en
 * un Lomo de una linea ×2 valia para los dos. Ahora cada pedido distinto es su
 * propia linea, y cocina lo recibe como platos distintos.
 */
describe('lineas de la comanda', () => {
  it('el mismo plato con otra observacion es otra linea', () => {
    let lineas = pedir([], lomo, 'sin cebolla');
    lineas = pedir(lineas, lomo);

    expect(lineas.map((l) => [l.cantidad, l.nota])).toEqual([
      [1, 'sin cebolla'],
      [1, ''],
    ]);
  });

  it('el mismo pedido se suma a su linea, sin mirar mayusculas ni espacios', () => {
    let lineas = pedir([], lomo, 'Sin cebolla');
    lineas = pedir(lineas, ceviche);
    lineas = pedir(lineas, lomo, '  sin   cebolla ', 2);

    expect(lineas.map((l) => [l.platillo.id, l.cantidad, l.nota])).toEqual([
      ['lomo', 3, 'Sin cebolla'],
      ['ceviche', 1, ''],
    ]);
  });

  it('los complementos distinguen el pedido, en cualquier orden', () => {
    let lineas = pedir([], lomo, '', 1, con([gaseosa, 1], [arroz, 1]));
    lineas = pedir(lineas, lomo, '', 1, con([arroz, 1], [gaseosa, 1]));
    lineas = pedir(lineas, lomo, '', 1, con([gaseosa, 2]));

    expect(lineas.map((l) => l.cantidad)).toEqual([2, 1]);
  });

  it('corregir una linea solo cambia esa linea', () => {
    let lineas = pedir([], lomo, '', 2);
    lineas = pedir(lineas, ceviche);
    const [primera] = lineas;

    lineas = reemplazarLinea(lineas, primera.clave, {
      cantidad: 2,
      nota: 'bien cocido',
      complementos: [],
    });

    expect(lineas.map((l) => [l.platillo.id, l.cantidad, l.nota])).toEqual([
      ['lomo', 2, 'bien cocido'],
      ['ceviche', 1, ''],
    ]);
  });

  it('si al corregirla queda igual a otra, se funden en la primera', () => {
    let lineas = pedir([], lomo);
    lineas = pedir(lineas, ceviche);
    lineas = pedir(lineas, lomo, 'sin cebolla');
    const conNota = lineas[2];

    lineas = reemplazarLinea(lineas, conNota.clave, { cantidad: 1, nota: '', complementos: [] });

    expect(lineas.map((l) => [l.platillo.id, l.cantidad])).toEqual([
      ['lomo', 2],
      ['ceviche', 1],
    ]);
  });

  it('el complemento se cobra por plato', () => {
    const [linea] = pedir([], lomo, '', 2, con([gaseosa, 1]));

    // Dos lomos con una gaseosa cada uno: (32 + 5) × 2.
    expect(precioDeLinea(linea)).toBe(74);
  });

  it('un complemento en cero no cuenta ni distingue la linea', () => {
    let lineas = pedir([], lomo, '', 1, con([gaseosa, 0]));
    lineas = pedir(lineas, lomo);

    expect(lineas).toHaveLength(1);
    expect(lineas[0].complementos).toEqual([]);
  });
});
