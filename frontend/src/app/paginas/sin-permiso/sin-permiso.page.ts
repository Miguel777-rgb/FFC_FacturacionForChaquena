import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { SesionService } from '../../nucleo/sesion/sesion.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { INICIO_POR_ROL } from '../../nucleo/sesion/rol';

@Component({
  selector: 'app-sin-permiso',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="centro">
      <h1>{{ t('sinPermiso.titulo') }}</h1>
      <!-- La frase entera sale de una sola clave, con el nombre y los roles
           como parametros. Partirla para poner un <strong> en medio ataria el
           orden de las palabras al del espanol, y en ingles no es el mismo. -->
      <p>
        {{
          t('sinPermiso.entraste', {
            nombre: sesion.nombre(),
            etiqueta: tp('sinPermiso.rol', sesion.roles().length),
            roles: sesion.roles().join(', ') || t('sinPermiso.ninguno'),
          })
        }}
      </p>
      <p>{{ t('sinPermiso.pide') }}</p>
      <button type="button" (click)="volver()">{{ t('sinPermiso.volver') }}</button>
    </main>
  `,
  styles: `
    .centro {
      min-height: 100dvh;
      display: grid;
      place-content: center;
      justify-items: start;
      gap: var(--e4);
      padding: var(--e6);
      max-width: 44ch;
      margin-inline: auto;
    }
  `,
})
export class SinPermisoPage {
  protected readonly sesion = inject(SesionService);
  private readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;

  protected volver(): void {
    const rol = this.sesion.roles()[0];
    void this.router.navigateByUrl(rol ? INICIO_POR_ROL[rol] : '/entrar');
  }
}
