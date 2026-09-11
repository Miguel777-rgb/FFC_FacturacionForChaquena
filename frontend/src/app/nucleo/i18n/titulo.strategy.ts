import { Injectable, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { TitleStrategy, type RouterStateSnapshot } from '@angular/router';

import { I18nService } from './i18n.service';
import type { ClaveI18n } from './traducciones/es';

/** La marca no se traduce: es el nombre del local. */
const MARCA = 'Chaquena';

/**
 * El titulo de la pestana, traducido.
 *
 * Las rutas no llevan el titulo escrito sino su clave (`titulo.pos`), asi que
 * el texto sale del mismo diccionario que el resto de la aplicacion.
 *
 * La clave se guarda en una senal y el titulo se escribe desde un `effect`.
 * Sin eso, cambiar de idioma dejaria la pestana con el titulo anterior hasta
 * la siguiente navegacion: es lo unico de la aplicacion que vive fuera de la
 * plantilla y no se repinta solo.
 */
@Injectable({ providedIn: 'root' })
export class TituloDeRuta extends TitleStrategy {
  private readonly i18n = inject(I18nService);
  private readonly titulo = inject(Title);
  private readonly clave = signal<ClaveI18n | null>(null);

  constructor() {
    super();
    effect(() => {
      const clave = this.clave();
      this.titulo.setTitle(clave ? `${this.i18n.t(clave)} · ${MARCA}` : MARCA);
    });
  }

  override updateTitle(estado: RouterStateSnapshot): void {
    const declarado = this.buildTitle(estado);
    this.clave.set((declarado as ClaveI18n | undefined) ?? null);
  }
}
