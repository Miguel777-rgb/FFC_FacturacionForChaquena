/**
 * Lo que viaja por el WebSocket del llamado de cocina al mozo.
 *
 * Copia de `LlamadoCocinaDto` y `AvisoLlamadoDto` del backend: los mensajes
 * STOMP no estan en el contrato OpenAPI, asi que el generador no los conoce.
 * Si cambia un campo alli, cambia aqui.
 */
export type EstadoLlamado = 'PENDIENTE' | 'ATENDIDO' | 'CERRADO';

export interface LlamadoCocina {
  id: string;
  ordenId: string;
  /** Los ocho primeros caracteres del id de la comanda, como en cocina. */
  correlativo: string;
  tipoOrden: string;
  mesaNumero: string | null;
  /** El nombre de pila de quien llamo, no su correo. */
  llamadoPor: string;
  llamadoEn: string;
  estado: EstadoLlamado;
  atendidoPor: string | null;
  atendidoEn: string | null;
  /** Lo que tardo el mozo en decir «Voy»; nulo mientras nadie responde. */
  segundosRespuesta: number | null;
}

/**
 * - `estado`: la foto al suscribirse, una sola vez.
 * - `llamado`: cocina llamo o volvio a llamar.
 * - `atendido`: un mozo dijo «Voy».
 * - `cerrado`: la comanda salio del pase sin respuesta.
 * - `presencia`: cambio el numero de mozos conectados (solo a cocina).
 * - `error`: solo a la sesion que lo provoco.
 */
export type TipoAvisoLlamado =
  'estado' | 'llamado' | 'atendido' | 'cerrado' | 'presencia' | 'error';

export interface AvisoLlamado {
  tipo: TipoAvisoLlamado;
  llamados: LlamadoCocina[] | null;
  mozosConectados: number | null;
  mensaje: string | null;
}

/**
 * La lista de llamados despues de un aviso. Pura, para probarla sin socket.
 *
 * La foto reemplaza la lista entera; un llamado o un «Voy» ponen al dia el que
 * ya estaba, o lo agregan; un cierre lo quita. Lo demas no la toca, y devuelve
 * la misma lista para que nadie se repinte por nada.
 */
export function aplicarAviso(
  lista: readonly LlamadoCocina[],
  aviso: AvisoLlamado,
): readonly LlamadoCocina[] {
  const llegados = aviso.llamados ?? [];
  switch (aviso.tipo) {
    case 'estado':
      return llegados;
    case 'llamado':
    case 'atendido': {
      const ids = new Set(llegados.map((l) => l.id));
      return [...lista.filter((l) => !ids.has(l.id)), ...llegados];
    }
    case 'cerrado': {
      const ids = new Set(llegados.map((l) => l.id));
      return lista.filter((l) => !ids.has(l.id));
    }
    default:
      return lista;
  }
}

/**
 * El llamado que cuenta para una comanda: el pendiente si lo hay y, si no, el
 * ultimo que se respondio.
 */
export function llamadoDeOrden(
  lista: readonly LlamadoCocina[],
  ordenId: string | null | undefined,
): LlamadoCocina | null {
  if (!ordenId) return null;
  let ultimo: LlamadoCocina | null = null;
  for (const llamado of lista) {
    if (llamado.ordenId !== ordenId) continue;
    if (llamado.estado === 'PENDIENTE') return llamado;
    if (!ultimo || Date.parse(llamado.llamadoEn) > Date.parse(ultimo.llamadoEn)) ultimo = llamado;
  }
  return ultimo;
}
