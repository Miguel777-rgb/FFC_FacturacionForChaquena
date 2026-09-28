import type { ComplementoResponseDto, PlatilloDisponibleDto } from '../../api';

/** Un complemento elegido para un plato, con su propia cantidad. */
export interface ComplementoElegido {
  complemento: ComplementoResponseDto;
  cantidad: number;
}

/** Lo que se decide en la hoja de un plato: cuantos, que observacion y que complementos. */
export interface EleccionPlatillo {
  cantidad: number;
  nota: string;
  complementos: ComplementoElegido[];
}

/**
 * Una linea de la comanda mientras se arma en la pantalla. Guarda el platillo
 * entero, no solo su id, porque el precio y el nombre se pintan aqui sin volver
 * a preguntar al servidor. El total que sale de esto es una estimacion para el
 * mozo: el importe que vale es el que devuelve `POST /ordenes`.
 *
 * `clave` identifica la linea en la pantalla. No sirve el id del platillo: dos
 * Lomos Saltados con observaciones distintas son dos lineas.
 */
export interface LineaComanda extends EleccionPlatillo {
  clave: number;
  platillo: PlatilloDisponibleDto;
}

let ultimaClave = 0;

/** Una clave nueva para una linea; solo tiene que ser unica mientras dure la pantalla. */
export function nuevaClave(): number {
  return ++ultimaClave;
}

/** La observacion como se guarda: sin espacios sobrantes, con las mayusculas del mozo. */
export function limpiarNota(nota: string): string {
  return nota.trim().replace(/\s+/g, ' ');
}

/**
 * Lo que hace que dos lineas sean el mismo pedido: el mismo plato, la misma
 * observacion y los mismos complementos. Si coinciden, se suman; si difieren en
 * algo, son platos distintos para cocina.
 *
 * La observacion se compara sin distinguir mayusculas —«Sin cebolla» y «sin
 * cebolla» piden lo mismo—, y los complementos sin importar el orden en que se
 * eligieron.
 */
export function firma(
  platilloId: string | undefined,
  nota: string,
  complementos: ComplementoElegido[],
): string {
  const extras = complementos
    .filter((c) => c.cantidad > 0)
    .map((c) => `${c.complemento.id}:${c.cantidad}`)
    .sort()
    .join(',');
  return `${platilloId}|${limpiarNota(nota).toLocaleLowerCase()}|${extras}`;
}

function firmaDe(linea: LineaComanda): string {
  return firma(linea.platillo.id, linea.nota, linea.complementos);
}

/**
 * Suma a la comanda lo elegido en la hoja de un plato: a la linea identica si
 * ya existe, o como linea nueva al final.
 *
 * Antes se sumaba siempre a la linea del mismo platillo, y la observacion se
 * escribia una vez para todas sus unidades: «sin cebolla» en dos Lomos valia
 * para los dos aunque solo uno lo pidiera.
 */
export function agregarLinea(
  lineas: LineaComanda[],
  platillo: PlatilloDisponibleDto,
  eleccion: EleccionPlatillo,
): LineaComanda[] {
  const nueva: LineaComanda = {
    clave: nuevaClave(),
    platillo,
    cantidad: eleccion.cantidad,
    nota: limpiarNota(eleccion.nota),
    complementos: eleccion.complementos.filter((c) => c.cantidad > 0),
  };
  const igual = lineas.find((l) => firmaDe(l) === firmaDe(nueva));
  if (igual) {
    return lineas.map((l) => (l === igual ? { ...l, cantidad: l.cantidad + nueva.cantidad } : l));
  }
  return [...lineas, nueva];
}

/**
 * Cambia una linea con lo corregido en su hoja. Si al corregirla queda igual a
 * otra —se le borro la observacion y ya habia uno sin observacion—, se funden en
 * la que estaba antes, para que cocina no reciba dos lineas del mismo pedido.
 */
export function reemplazarLinea(
  lineas: LineaComanda[],
  clave: number,
  eleccion: EleccionPlatillo,
): LineaComanda[] {
  const cambiadas = lineas.map((l) =>
    l.clave === clave
      ? {
          ...l,
          cantidad: eleccion.cantidad,
          nota: limpiarNota(eleccion.nota),
          complementos: eleccion.complementos.filter((c) => c.cantidad > 0),
        }
      : l,
  );
  return fusionar(cambiadas);
}

/** Junta las lineas identicas en la primera de ellas, sin mover el resto. */
export function fusionar(lineas: LineaComanda[]): LineaComanda[] {
  const porFirma = new Map<string, LineaComanda>();
  const resultado: LineaComanda[] = [];
  for (const linea of lineas) {
    const clave = firmaDe(linea);
    const previa = porFirma.get(clave);
    if (previa) {
      previa.cantidad += linea.cantidad;
    } else {
      const copia = { ...linea };
      porFirma.set(clave, copia);
      resultado.push(copia);
    }
  }
  return resultado;
}

/**
 * Precio de un plato con sus complementos. El complemento se cobra por plato:
 * el servidor multiplica su precio por su propia cantidad y por la cantidad de
 * la linea. Dos lomos con una gaseosa cada uno son dos gaseosas, no una.
 */
export function precioUnitario(
  platillo: PlatilloDisponibleDto,
  complementos: ComplementoElegido[],
): number {
  return (
    (platillo.precioVentaBase ?? 0) +
    complementos.reduce((suma, c) => suma + (c.complemento.precioAdicional ?? 0) * c.cantidad, 0)
  );
}

export function precioDeLinea(linea: LineaComanda): number {
  return precioUnitario(linea.platillo, linea.complementos) * linea.cantidad;
}

/** «2× Gaseosa, Arroz extra»: los complementos en una linea de texto. */
export function resumenComplementos(complementos: ComplementoElegido[]): string {
  return complementos
    .map((c) =>
      c.cantidad > 1 ? `${c.cantidad}× ${c.complemento.nombre}` : (c.complemento.nombre ?? ''),
    )
    .join(', ');
}
