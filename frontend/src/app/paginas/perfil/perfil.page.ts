import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { TrabajadoresApi, type TrabajadorResponseDto } from '../../api';
import { BarraIdiomas } from '../../disenio/barra-idiomas';
import { Icono } from '../../disenio/icono';
import { SelectorTema } from '../../disenio/selector-tema';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { SesionService } from '../../nucleo/sesion/sesion.service';

/**
 * Mi perfil: quien soy para el sistema y como quiero verlo.
 *
 * Lo que se lee sale del token —nombre, cargo, roles— y de
 * `/trabajadores/activos`, el unico listado de personal que el servidor abre a
 * todos los cargos; de ahi salen el documento y el celular.
 *
 * La contrasena no se cambia aqui y la pantalla lo dice en vez de esconderlo:
 * el servidor solo deja que un administrador la restablezca. Un formulario que
 * terminara en un 403 seria peor que la frase que explica a quien pedirselo.
 */
@Component({
  selector: 'app-perfil',
  imports: [RouterLink, Icono, BarraIdiomas, SelectorTema],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './perfil.page.html',
  styleUrls: ['../../disenio/secciones.scss', './perfil.page.scss'],
})
export class PerfilPage implements OnInit {
  private readonly trabajadoresApi = inject(TrabajadoresApi);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly sesion = inject(SesionService);

  protected readonly t = this.i18n.t;
  protected readonly fecha = this.i18n.fecha;

  protected readonly esAdmin = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));
  protected readonly ficha = signal<TrabajadorResponseDto | null>(null);

  ngOnInit(): void {
    const usuario = this.sesion.sesion()?.username;
    if (!usuario) return;

    this.trabajadoresApi
      .listarTrabajadoresActivos()
      .pipe(catchError(() => of([] as TrabajadorResponseDto[])))
      .subscribe((lista) => this.ficha.set(lista.find((t) => t.username === usuario) ?? null));
  }

  protected salir(): void {
    this.sesion.cerrar();
    void this.router.navigateByUrl('/entrar');
  }
}
