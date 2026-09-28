import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  booleanAttribute,
  computed,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';

import { I18nService } from '../nucleo/i18n/i18n.service';
import { IDIOMAS, type Idioma } from '../nucleo/i18n/idioma';
import { Bandera, type Pais } from './bandera';
import { Icono } from './icono';

/**
 * La bandera que acompana a cada idioma. El espanol lleva la del local, no la
 * de Espana: quien lo ve es el personal de un restaurante peruano.
 */
const BANDERA: Record<Idioma, Pais> = { es: 'pe', en: 'us', pt: 'br' };

/** Separacion entre el boton y la lista, y margen minimo con el borde de la pantalla. */
const HOLGURA = 4;
const MARGEN = 8;

/**
 * El selector de idioma: un boton que despliega los tres idiomas, cada uno con
 * su bandera.
 *
 * Deja de ser un `<select>` nativo porque el nativo no admite dibujos dentro de
 * las opciones en todos los navegadores (`appearance: base-select` solo existe
 * en Chrome y Edge), y el iPhone del mozo se quedaria sin banderas. Lo que el
 * nativo regalaba se reimplementa siguiendo el patron *select-only combobox* de
 * la APG: el foco se queda siempre en el boton, la opcion activa se senala con
 * `aria-activedescendant`, y el teclado hace lo mismo que en un desplegable del
 * sistema —flechas, Inicio, Fin, la inicial para saltar, Enter o Espacio para
 * elegir y Escape para cerrar—. Tab cierra sin elegir: cambiar de idioma repinta
 * toda la aplicacion, y no debe ocurrir por salir del control.
 *
 * La lista se abre en la capa superior (`popover`) para que ni el `overflow`
 * del panel lateral ni la barra fija del celular la recorten; se coloca a mano
 * debajo del boton, o encima si abajo no cabe (en el pie del panel no cabe).
 *
 * Cada idioma se nombra en su propia lengua —«English», no «Ingles»— porque
 * quien busca la suya en la lista todavia no entiende la que esta viendo, y
 * lleva `lang` para que el lector de pantalla lo pronuncie en esa lengua.
 *
 * Variantes: `flotante` es la de la pantalla de entrar, fija arriba a la
 * derecha porque ahi no hay panel que la sostenga; `integrada` encaja en el pie
 * del panel, en la barra superior y en Mi perfil. `compacto` deja solo la
 * bandera —barra del celular y regleta—, y el nombre pasa al `aria-label`.
 */
@Component({
  selector: 'app-selector-idioma',
  imports: [Bandera, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '[class.flotante]': "variante() === 'flotante'",
    '[class.compacto]': 'compacto()',
    '(document:pointerdown)': 'alPulsarFuera($event)',
    '(window:resize)': 'cerrar(false)',
    '(window:scroll)': 'cerrar(false)',
  },
  template: `
    <button
      #boton
      type="button"
      class="disparador"
      role="combobox"
      aria-haspopup="listbox"
      [attr.aria-expanded]="abierto()"
      [attr.aria-controls]="id + '-lista'"
      [attr.aria-activedescendant]="abierto() ? id + '-' + activo() : null"
      [attr.aria-label]="rotulo()"
      [attr.title]="rotulo()"
      (click)="alternar()"
      (keydown)="alTeclear($event)"
      (keyup.space)="$event.preventDefault()"
    >
      <app-bandera [pais]="bandera(actual().codigo)" />
      @if (!compacto()) {
        <span class="nombre" [attr.lang]="actual().codigo">{{ actual().nombre }}</span>
      }
      <app-icono class="flecha" nombre="desplegar" [tamano]="16" />
    </button>

    <ul
      #lista
      popover="manual"
      role="listbox"
      class="lista"
      [class.abierta]="abierto()"
      [class.teclado]="conTeclado()"
      [id]="id + '-lista'"
      [attr.aria-label]="i18n.t('idioma.elegir')"
    >
      @for (i of i18n.idiomas; track i.codigo) {
        <li
          role="option"
          class="opcion"
          [id]="id + '-' + i.codigo"
          [attr.lang]="i.codigo"
          [attr.aria-selected]="i.codigo === i18n.idioma()"
          [class.activa]="i.codigo === activo()"
          (pointermove)="apuntar(i.codigo)"
          (click)="elegir(i.codigo)"
        >
          <app-bandera [pais]="bandera(i.codigo)" />
          <span>{{ i.nombre }}</span>
          @if (i.codigo === i18n.idioma()) {
            <app-icono class="marca" nombre="confirmar" [tamano]="16" />
          }
        </li>
      }
    </ul>
  `,
  styles: `
    :host {
      display: inline-flex;
    }

    /* Sin sesion no hay panel: el control queda en la esquina, fuera de la
       tarjeta de entrar y sin ocupar sitio en el flujo. */
    :host(.flotante) {
      position: fixed;
      top: max(var(--e4), env(safe-area-inset-top));
      right: max(var(--e4), env(safe-area-inset-right));
      z-index: 5;
    }

    /* Se anulan a mano los estilos globales del boton: esto no es una accion
       principal, es un control. */
    .disparador {
      flex: 1 1 auto;
      min-width: 0;
      gap: var(--e2);
      padding: 0 var(--e3);
      font-weight: 500;
      color: var(--tinta);
      background: var(--superficie);
      border-color: var(--linea-fuerte);
    }

    /* Si quien lo aloja le da mas ancho (el pie del panel), la flecha se va al
       borde, como en un campo; si le da menos, el nombre se recorta. */
    .nombre {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    /* Dentro del panel y de la barra superior no compite con los destinos: sin
       fondo hasta que se apunta. */
    :host(:not(.flotante)) .disparador {
      background: transparent;
      border-color: var(--linea);
    }

    .disparador:hover:not(:disabled),
    .disparador:active:not(:disabled),
    .disparador[aria-expanded='true'] {
      background: var(--hundido);
    }

    :host(.compacto) .disparador {
      gap: var(--e1);
      padding: 0 var(--e2);
    }

    .flecha {
      margin-left: auto;
      color: var(--texto);
      transition: transform 0.15s ease;
    }

    .disparador[aria-expanded='true'] .flecha {
      transform: rotate(180deg);
    }

    /* La lista. Con \`popover\` vive en la capa superior; sin el (navegadores
       viejos, pruebas) es un \`fixed\` con z-index. Se anulan los estilos que el
       navegador pone a todo popover, que lo centran en la pantalla. */
    .lista {
      position: fixed;
      inset: auto;
      z-index: 50;
      min-width: 11rem;
      margin: 0;
      padding: var(--e1);
      list-style: none;
      color: var(--tinta);
      background: var(--superficie);
      border: 1px solid var(--linea);
      border-radius: var(--radio);
      box-shadow: var(--sombra-elevada);
    }

    .lista:not(.abierta) {
      display: none;
    }

    /* Aparece desde el boton: baja si se abre debajo, sube si se abre encima. */
    .lista.abierta {
      animation: aparecer 0.15s cubic-bezier(0.16, 1, 0.3, 1);
    }

    .lista.abierta.arriba {
      animation-name: aparecer-arriba;
    }

    @keyframes aparecer {
      from {
        opacity: 0;
        transform: translateY(-4px);
      }
    }

    @keyframes aparecer-arriba {
      from {
        opacity: 0;
        transform: translateY(4px);
      }
    }

    .opcion {
      display: flex;
      align-items: center;
      gap: var(--e3);
      min-height: var(--control);
      padding: 0 var(--e3);
      font-size: var(--t-texto);
      border-radius: var(--radio-chico);
      cursor: pointer;
    }

    .opcion.activa {
      background: var(--hundido);
    }

    /* Con el teclado el fondo gris no basta para ver donde se esta: la opcion
       activa lleva el mismo contorno que el foco del resto de la aplicacion. */
    .lista.teclado .opcion.activa {
      outline: 2px solid var(--acento);
      outline-offset: -2px;
    }

    .opcion[aria-selected='true'] {
      font-weight: 600;
    }

    .marca {
      margin-left: auto;
      color: var(--acento);
    }
  `,
})
export class SelectorIdioma {
  protected readonly i18n = inject(I18nService);
  private readonly anfitrion = inject(ElementRef<HTMLElement>);
  private readonly injector = inject(Injector);

  readonly variante = input<'flotante' | 'integrada'>('flotante');
  readonly compacto = input(false, { transform: booleanAttribute });

  private readonly boton = viewChild.required<ElementRef<HTMLButtonElement>>('boton');
  private readonly lista = viewChild.required<ElementRef<HTMLUListElement>>('lista');

  /** Un id propio por instancia: el panel y la barra superior conviven en el celular. */
  protected readonly id = `idioma-${Math.random().toString(36).slice(2, 8)}`;

  protected readonly abierto = signal(false);
  /** La opcion sobre la que esta el teclado o el puntero; no es la elegida. */
  protected readonly activo = signal<Idioma>(this.i18n.idioma());
  protected readonly conTeclado = signal(false);

  protected readonly actual = computed(
    () => IDIOMAS.find((i) => i.codigo === this.i18n.idioma()) ?? IDIOMAS[0],
  );

  /** Dice para que sirve el control y que idioma tiene puesto, en ese orden. */
  protected readonly rotulo = computed(() =>
    this.i18n.t('idioma.actual', { idioma: this.actual().nombre }),
  );

  protected bandera(codigo: Idioma): Pais {
    return BANDERA[codigo];
  }

  protected alternar(): void {
    if (this.abierto()) {
      this.cerrar();
    } else {
      this.conTeclado.set(false);
      this.abrir();
    }
  }

  protected alTeclear(evento: KeyboardEvent): void {
    const codigos = IDIOMAS.map((i) => i.codigo);
    const posicion = codigos.indexOf(this.activo());

    switch (evento.key) {
      case 'ArrowDown':
      case 'ArrowUp':
      case 'Home':
      case 'End': {
        evento.preventDefault();
        this.conTeclado.set(true);
        if (!this.abierto()) {
          // Abrir no mueve: se ve la lista con el idioma en uso marcado, como
          // en un desplegable del sistema. Inicio y Fin si saltan.
          this.abrir();
          if (evento.key === 'Home') this.activo.set(codigos[0]);
          if (evento.key === 'End') this.activo.set(codigos[codigos.length - 1]);
          return;
        }
        const destino =
          evento.key === 'Home'
            ? 0
            : evento.key === 'End'
              ? codigos.length - 1
              : Math.min(
                  Math.max(posicion + (evento.key === 'ArrowDown' ? 1 : -1), 0),
                  codigos.length - 1,
                );
        this.activo.set(codigos[destino]);
        return;
      }

      // Enter y Espacio se atienden aqui y no como clic del boton: asi un mismo
      // gesto no abre y elige a la vez. El `keyup` del espacio se anula en la
      // plantilla porque es el que dispararia ese clic.
      case 'Enter':
      case ' ':
        evento.preventDefault();
        this.conTeclado.set(true);
        if (this.abierto()) this.elegir(this.activo());
        else this.abrir();
        return;

      case 'Escape':
        if (this.abierto()) {
          evento.preventDefault();
          this.cerrar();
        }
        return;

      case 'Tab':
        this.cerrar(false);
        return;
    }

    // La inicial salta al siguiente idioma que empiece por ella; repetirla
    // recorre los que la comparten («E»: Espanol, English).
    if (evento.key.length === 1 && !evento.ctrlKey && !evento.metaKey && !evento.altKey) {
      const letra = evento.key.toLocaleLowerCase();
      const orden = [...codigos.slice(posicion + 1), ...codigos.slice(0, posicion + 1)];
      const siguiente = orden.find((c) =>
        IDIOMAS.find((i) => i.codigo === c)!
          .nombre.toLocaleLowerCase()
          .startsWith(letra),
      );
      if (siguiente) {
        this.conTeclado.set(true);
        if (!this.abierto()) this.abrir();
        this.activo.set(siguiente);
      }
    }
  }

  protected apuntar(codigo: Idioma): void {
    this.conTeclado.set(false);
    this.activo.set(codigo);
  }

  protected elegir(codigo: Idioma): void {
    this.cerrar();
    if (codigo !== this.i18n.idioma()) {
      // El diccionario se descarga al elegirlo; la pantalla se repinta cuando llega.
      void this.i18n.cambiar(codigo);
    }
  }

  /** Un toque fuera cierra sin elegir, y sin robarle el foco a lo que se toco. */
  protected alPulsarFuera(evento: PointerEvent): void {
    if (!this.abierto()) return;
    if (!this.anfitrion.nativeElement.contains(evento.target as Node)) this.cerrar(false);
  }

  protected cerrar(devolverFoco = true): void {
    if (!this.abierto()) return;
    this.abierto.set(false);

    const lista = this.lista().nativeElement;
    try {
      if (lista.matches(':popover-open')) lista.hidePopover();
    } catch {
      /* sin soporte de popover no hay nada que ocultar: lo hace la clase */
    }
    if (devolverFoco) this.boton().nativeElement.focus();
  }

  private abrir(): void {
    this.activo.set(this.i18n.idioma());
    this.abierto.set(true);
    // Se coloca despues de pintarse: hace falta su alto para saber si cabe debajo.
    afterNextRender({ write: () => this.colocar() }, { injector: this.injector });
  }

  private colocar(): void {
    if (!this.abierto()) return;
    const lista = this.lista().nativeElement;
    try {
      if (!lista.matches(':popover-open')) lista.showPopover();
    } catch {
      /* sin soporte de popover se queda como fixed con z-index */
    }

    const boton = this.boton().nativeElement.getBoundingClientRect();
    const ancho = lista.offsetWidth;
    const alto = lista.offsetHeight;

    const cabeDebajo = boton.bottom + HOLGURA + alto <= window.innerHeight - MARGEN;
    // La clase va directa y no por una senal: llegaria en un segundo repintado
    // y reiniciaria la animacion de apertura a medio camino.
    lista.classList.toggle('arriba', !cabeDebajo);
    const top = cabeDebajo ? boton.bottom + HOLGURA : boton.top - HOLGURA - alto;
    // Alineada al borde izquierdo del boton; si asi se saldria por la derecha
    // (la esquina del login, la barra del celular), al borde derecho.
    const alineadaIzquierda = boton.left + ancho <= window.innerWidth - MARGEN;
    const left = Math.max(alineadaIzquierda ? boton.left : boton.right - ancho, MARGEN);

    lista.style.top = `${Math.max(top, MARGEN)}px`;
    lista.style.left = `${left}px`;
  }
}
