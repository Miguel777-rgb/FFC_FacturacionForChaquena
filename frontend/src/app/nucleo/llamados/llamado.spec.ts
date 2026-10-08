import { describe, expect, it } from 'vitest';

import { aplicarAviso, llamadoDeOrden, type AvisoLlamado, type LlamadoCocina } from './llamado';

function llamado(parcial: Partial<LlamadoCocina> = {}): LlamadoCocina {
  return {
    id: 'l1',
    ordenId: 'o1',
    correlativo: '493F9AA1',
    tipoOrden: 'MESA',
    mesaNumero: 'M3',
    llamadoPor: 'Julio',
    llamadoEn: '2026-10-08T15:00:00Z',
    estado: 'PENDIENTE',
    atendidoPor: null,
    atendidoEn: null,
    segundosRespuesta: null,
    ...parcial,
  };
}

function aviso(tipo: AvisoLlamado['tipo'], llamados: LlamadoCocina[] = []): AvisoLlamado {
  return { tipo, llamados, mozosConectados: null, mensaje: null };
}

describe('aplicarAviso', () => {
  it('la foto reemplaza lo que hubiera: es el estado entero al suscribirse', () => {
    const antes = [llamado({ id: 'viejo' })];
    expect(aplicarAviso(antes, aviso('estado', [llamado()]))).toEqual([llamado()]);
  });

  it('un llamado repetido pone al dia el que ya estaba en vez de duplicarlo', () => {
    const antes = [llamado(), llamado({ id: 'l2', ordenId: 'o2' })];
    const repetido = llamado({ llamadoPor: 'Ana' });

    const despues = aplicarAviso(antes, aviso('llamado', [repetido]));

    expect(despues).toHaveLength(2);
    expect(despues.find((l) => l.id === 'l1')?.llamadoPor).toBe('Ana');
  });

  it('el «Voy» cambia el estado y el cierre lo quita', () => {
    const atendido = llamado({ estado: 'ATENDIDO', atendidoPor: 'Rosa' });
    const conVoy = aplicarAviso([llamado()], aviso('atendido', [atendido]));
    expect(conVoy).toEqual([atendido]);

    expect(aplicarAviso(conVoy, aviso('cerrado', [atendido]))).toEqual([]);
  });

  it('la presencia y los errores no tocan la lista, ni siquiera la copian', () => {
    const antes = [llamado()];
    expect(aplicarAviso(antes, aviso('presencia'))).toBe(antes);
    expect(aplicarAviso(antes, aviso('error'))).toBe(antes);
  });
});

describe('llamadoDeOrden', () => {
  it('el pendiente manda sobre uno ya respondido de la misma comanda', () => {
    const respondido = llamado({ id: 'l0', estado: 'ATENDIDO', llamadoEn: '2026-10-08T15:05:00Z' });
    expect(llamadoDeOrden([respondido, llamado()], 'o1')?.id).toBe('l1');
  });

  it('sin pendiente, el ultimo que se respondio', () => {
    const primero = llamado({ id: 'a', estado: 'ATENDIDO', llamadoEn: '2026-10-08T15:00:00Z' });
    const segundo = llamado({ id: 'b', estado: 'ATENDIDO', llamadoEn: '2026-10-08T15:09:00Z' });
    expect(llamadoDeOrden([segundo, primero], 'o1')?.id).toBe('b');
  });

  it('nada para una comanda sin llamados o sin id', () => {
    expect(llamadoDeOrden([llamado()], 'o9')).toBeNull();
    expect(llamadoDeOrden([llamado()], undefined)).toBeNull();
  });
});
