/** Comanda nueva en cocina: dos notas, como el timbre de un mostrador. */
export const TIMBRE_COMANDA = [880, 1175] as const;

/**
 * Cocina llama al mozo: ding-dong dos veces. Suena en otro equipo que el de
 * comandas, pero se distingue igual por si los dos estan en la misma sala.
 */
export const TIMBRE_LLAMADO = [988, 1319, 988, 1319] as const;

/**
 * Notas cortas generadas con Web Audio. No se carga ningun archivo: no hay
 * nada que descargar ni que pueda faltar.
 *
 * Cada nota sube y baja de volumen en unos milisegundos para que no chasquee al
 * empezar ni al cortar.
 */
export function tocarNotas(
  contexto: AudioContext,
  frecuencias: readonly number[],
  separacion = 0.18,
): void {
  if (contexto.state === 'suspended') void contexto.resume();

  frecuencias.forEach((frecuencia, i) => {
    const inicio = contexto.currentTime + i * separacion;
    const oscilador = contexto.createOscillator();
    const volumen = contexto.createGain();
    oscilador.frequency.value = frecuencia;
    volumen.gain.setValueAtTime(0.0001, inicio);
    volumen.gain.exponentialRampToValueAtTime(0.3, inicio + 0.02);
    volumen.gain.exponentialRampToValueAtTime(0.0001, inicio + separacion - 0.02);
    oscilador.connect(volumen).connect(contexto.destination);
    oscilador.start(inicio);
    oscilador.stop(inicio + separacion);
  });
}

let compartido: AudioContext | null = null;

/**
 * Un solo contexto de audio para el timbre del mozo, que suena desde el
 * armazon y desde la campana. `null` donde no hay Web Audio.
 *
 * El navegador lo deja sonar solo despues de un gesto: quien lo pida dentro de
 * uno (un toque, una tecla) lo deja desbloqueado para los timbres de despues.
 */
export function contextoCompartido(): AudioContext | null {
  if (typeof AudioContext === 'undefined') return null;
  compartido ??= new AudioContext();
  if (compartido.state === 'suspended') void compartido.resume();
  return compartido;
}
