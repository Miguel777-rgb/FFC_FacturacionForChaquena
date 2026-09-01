import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { SesionService } from '../../nucleo/sesion/sesion.service';
import { INICIO_POR_ROL } from '../../nucleo/sesion/rol';

@Component({
  selector: 'app-sin-permiso',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main class="centro">
      <h1>Esta pantalla no es para tu cargo</h1>
      <p>
        Entraste como <strong>{{ sesion.nombre() }}</strong
        >, con {{ sesion.roles().length === 1 ? 'el rol' : 'los roles' }}
        <strong>{{ sesion.roles().join(', ') || 'ninguno' }}</strong
        >. Si necesitas acceso, pideselo al administrador.
      </p>
      <button type="button" (click)="volver()">Ir a mi pantalla</button>
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
  private readonly router = inject(Router);

  protected volver(): void {
    const rol = this.sesion.roles()[0];
    void this.router.navigateByUrl(rol ? INICIO_POR_ROL[rol] : '/entrar');
  }
}
