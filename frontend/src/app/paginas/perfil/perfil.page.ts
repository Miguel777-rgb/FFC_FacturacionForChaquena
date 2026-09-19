import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import { TrabajadoresApi, type TrabajadorResponseDto, type TurnoDto } from '../../api';
import { BarraIdiomas } from '../../disenio/barra-idiomas';
import { Icono } from '../../disenio/icono';
import { SelectorTema } from '../../disenio/selector-tema';
import { AsistenciaService } from '../../nucleo/asistencia/asistencia.service';
import { fechaDeDia } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { SesionService } from '../../nucleo/sesion/sesion.service';

/** Los dias como los nombra el diccionario (`dia.MONDAY`), en el orden de `Date.getDay()`. */
const DIAS = [
  'SUNDAY',
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
] as const;

/**
 * Mi perfil: quien soy para el sistema, mi asistencia y como quiero verlo.
 *
 * Lo que se lee sale del token —nombre, cargo, roles— y de
 * `/trabajadores/activos`, el unico listado de personal que el servidor abre a
 * todos los cargos; de ahi salen el documento y el celular.
 *
 * Marcar entrada y salida es de cualquier cargo y siempre sobre uno mismo. El
 * mismo boton esta en la barra del celular; los dos leen el mismo estado.
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
  protected readonly asistencia = inject(AsistenciaService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;
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

  /** «Lun 14», con el nombre del dia del diccionario y sin mover la fecha de dia. */
  protected diaDe(turno: TurnoDto): string {
    const dia = fechaDeDia(turno.fecha);
    return dia ? `${this.tEnum('dia', DIAS[dia.getDay()])} ${dia.getDate()}` : '—';
  }

  protected rangoDe(turno: TurnoDto): string {
    return `${(turno.inicio ?? '').slice(0, 5)} – ${(turno.fin ?? '').slice(0, 5)}`;
  }

  protected salir(): void {
    this.sesion.cerrar();
    // Igual que en el panel: Atras no debe volver a la pantalla de quien salio.
    void this.router.navigateByUrl('/entrar', { replaceUrl: true });
  }
}
