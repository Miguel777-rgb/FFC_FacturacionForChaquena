import { ChangeDetectionStrategy, Component, inject, OnInit } from '@angular/core';
import { Router } from '@angular/router';

import { SesionService } from '../../nucleo/sesion/sesion.service';
import { INICIO_POR_ROL } from '../../nucleo/sesion/rol';

/**
 * No tiene interfaz propia: reparte a cada quien a su superficie segun el rol.
 * Existe para que `/` sea una ruta valida sin decidir a mano el destino en
 * cada sitio que redirige.
 */
@Component({
  selector: 'app-inicio',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
export class InicioPage implements OnInit {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  ngOnInit(): void {
    const rol = this.sesion.roles()[0];
    void this.router.navigateByUrl(rol ? INICIO_POR_ROL[rol] : '/sin-permiso', {
      replaceUrl: true,
    });
  }
}
