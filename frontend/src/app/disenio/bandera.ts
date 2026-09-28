import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type Pais = 'pe' | 'us' | 'br';

/**
 * Las 50 estrellas de la bandera de EE. UU. como puntos, en un solo trazo.
 *
 * A 20px una estrella de cinco puntas mide menos de un pixel: se dibuja un
 * punto, que es lo que el ojo ve a ese tamano. Las posiciones son las de la
 * especificacion (9 filas alternando 6 y 5) escaladas al canton de 8 × 7,54.
 */
const ESTRELLAS = (() => {
  const r = 0.3;
  const puntos: string[] = [];
  for (let fila = 0; fila < 9; fila++) {
    const y = ((fila + 1) * 7.54) / 10;
    const columnas = fila % 2 === 0 ? [1, 3, 5, 7, 9, 11] : [2, 4, 6, 8, 10];
    for (const c of columnas) {
      const x = (c * 8) / 12;
      puntos.push(
        `M${(x - r).toFixed(2)} ${y.toFixed(2)}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0`,
      );
    }
  }
  return puntos.join('');
})();

/** Las 7 franjas rojas de las 13, como un solo trazo sobre fondo blanco. */
const FRANJAS = Array.from({ length: 7 }, (_, i) => {
  const alto = 14 / 13;
  return `M0 ${(i * 2 * alto).toFixed(3)}h20v${alto.toFixed(3)}H0z`;
}).join('');

/**
 * La bandera de un pais, a 20 × 14.
 *
 * Solo acompana al nombre de un idioma en el selector: una bandera es un pais,
 * no una lengua, y por eso nunca va sola. Es decorativa (`aria-hidden`): lo que
 * se lee es el nombre que tiene al lado.
 *
 * Se dibujan aqui y no como emoji porque Windows no pinta las banderas emoji
 * —muestra las letras «PE»— y porque DESIGN.md no admite emojis en la interfaz.
 * Van simplificadas para su tamano: sin escudo en la del Peru ni lema en la del
 * Brasil, que a 20px serian una mancha. Las tres comparten caja aunque sus
 * proporciones reales difieran, para que la lista quede alineada.
 */
@Component({
  selector: 'app-bandera',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @switch (pais()) {
      @case ('pe') {
        <svg viewBox="0 0 20 14" aria-hidden="true" focusable="false">
          <rect width="20" height="14" fill="#fff" />
          <rect width="6.667" height="14" fill="#d91023" />
          <rect x="13.333" width="6.667" height="14" fill="#d91023" />
        </svg>
      }
      @case ('us') {
        <svg viewBox="0 0 20 14" aria-hidden="true" focusable="false">
          <rect width="20" height="14" fill="#fff" />
          <path [attr.d]="franjas" fill="#b31942" />
          <rect width="8" height="7.54" fill="#0a3161" />
          <path [attr.d]="estrellas" fill="#fff" />
        </svg>
      }
      @case ('br') {
        <svg viewBox="0 0 20 14" aria-hidden="true" focusable="false">
          <rect width="20" height="14" fill="#009c3b" />
          <path d="M1.7 7L10 1.7L18.3 7L10 12.3z" fill="#ffdf00" />
          <circle cx="10" cy="7" r="3.5" fill="#002776" />
          <!-- La faja: arco de radio 8,25 con centro 2 modulos a la izquierda del
               eje, sobre el borde inferior, recortado donde corta al globo. -->
          <path
            d="M6.69 5.86A8.25 8.25 0 0 1 13.41 7.78"
            fill="none"
            stroke="#fff"
            stroke-width="0.5"
          />
        </svg>
      }
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      width: 20px;
      height: 14px;
      overflow: hidden;
      border-radius: 2px;
      /* Un filete por encima del dibujo: sin el, el blanco de la bandera del
         Peru se pierde sobre la superficie blanca y parece partida en dos. */
      outline: 1px solid color-mix(in srgb, var(--tinta) 14%, transparent);
      outline-offset: -1px;
    }

    svg {
      width: 100%;
      height: 100%;
    }
  `,
})
export class Bandera {
  readonly pais = input.required<Pais>();

  protected readonly franjas = FRANJAS;
  protected readonly estrellas = ESTRELLAS;
}
