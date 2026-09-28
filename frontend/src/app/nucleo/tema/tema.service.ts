import { DOCUMENT, Injectable, computed, inject, signal } from '@angular/core';

/** `sistema` deja decidir al sistema operativo via `prefers-color-scheme`. */
export type Tema = 'sistema' | 'claro' | 'oscuro';

const CLAVE = 'chaquena.tema';
const ORDEN: readonly Tema[] = ['sistema', 'claro', 'oscuro'];

/**
 * El tema claro u oscuro de la interfaz.
 *
 * Los tokens de los dos temas ya viven en `estilos/_tokens.scss`; este servicio
 * solo escribe `data-tema` en `<html>`, que es el atributo que los activa. Sin
 * eleccion no escribe nada y manda el sistema.
 *
 * Se recuerda en `localStorage`, como el idioma y el logo: es preferencia del
 * dispositivo, no del turno. El celular del despacho que se usa de noche en la
 * calle quiere oscuro aunque cambie quien lo lleva.
 *
 * Se aplica al construirse, y lo construye `App` al arrancar: asi la pantalla de
 * entrar, que no tiene selector, ya sale con el tema guardado.
 */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly documento = inject(DOCUMENT);
  private readonly _tema = signal<Tema>(this.leer());

  readonly tema = this._tema.asReadonly();
  readonly siguiente = computed<Tema>(
    () => ORDEN[(ORDEN.indexOf(this._tema()) + 1) % ORDEN.length],
  );

  /** Lo que prefiere el sistema ahora; cambia en vivo si el celular pasa a modo noche. */
  private readonly consultaSistema =
    typeof matchMedia === 'function' ? matchMedia('(prefers-color-scheme: dark)') : null;
  private readonly sistemaOscuro = signal(this.consultaSistema?.matches ?? false);

  /**
   * Si lo que se ve es oscuro, por eleccion o por el sistema. Lo necesitan las
   * piezas que no se pintan con los tokens de CSS: las teselas del mapa son
   * imagenes, y hay que pedir las oscuras.
   */
  readonly esOscuro = computed(
    () => this._tema() === 'oscuro' || (this._tema() === 'sistema' && this.sistemaOscuro()),
  );

  constructor() {
    this.aplicar(this._tema());
    // El servicio vive lo que la aplicacion: no hace falta soltar la escucha.
    this.consultaSistema?.addEventListener('change', (e) => this.sistemaOscuro.set(e.matches));
  }

  elegir(tema: Tema): void {
    this._tema.set(tema);
    this.aplicar(tema);
    try {
      localStorage.setItem(CLAVE, tema);
    } catch {
      /* sin almacenamiento el tema vale para esta sesion y se olvida al recargar */
    }
  }

  /** Recorre sistema → claro → oscuro. */
  alternar(): void {
    this.elegir(this.siguiente());
  }

  private aplicar(tema: Tema): void {
    const raiz = this.documento.documentElement;
    if (tema === 'sistema') {
      raiz.removeAttribute('data-tema');
    } else {
      raiz.setAttribute('data-tema', tema);
    }
  }

  private leer(): Tema {
    try {
      const guardado = localStorage.getItem(CLAVE);
      return ORDEN.includes(guardado as Tema) ? (guardado as Tema) : 'sistema';
    } catch {
      return 'sistema';
    }
  }
}
