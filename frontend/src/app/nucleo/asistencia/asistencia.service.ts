import { Injectable, computed, effect, inject, signal } from '@angular/core';

// Del archivo concreto y no del barril `api`: la barra del celular carga al
// arrancar, y el barril arrastraria todos los servicios generados.
import { PersonalAsistenciaApi } from '../../api/api/personal-asistencia.api';
import type { MiAsistenciaDto } from '../../api/model/mi-asistencia-dto';
import { AvisosService } from '../http/avisos.service';
import { I18nService } from '../i18n/i18n.service';
import { SesionService } from '../sesion/sesion.service';

/**
 * La asistencia de quien tiene la sesion: si esta dentro y el gesto de marcar.
 *
 * La comparten el perfil y la barra del celular, para que marcar en una se vea
 * en la otra sin volver a preguntar. Nadie marca por otro: el servidor saca a
 * quien del token.
 */
@Injectable({ providedIn: 'root' })
export class AsistenciaService {
  private readonly api = inject(PersonalAsistenciaApi);
  private readonly sesion = inject(SesionService);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);

  private readonly _mia = signal<MiAsistenciaDto | null>(null);
  private readonly _marcando = signal(false);

  readonly mia = this._mia.asReadonly();
  readonly marcando = this._marcando.asReadonly();
  readonly dentro = computed(() => this._mia()?.dentro ?? false);
  /** Mientras no se sabe si esta dentro, no se ofrece el boton: marcar a ciegas es marcar al reves. */
  readonly conocida = computed(() => this._mia() !== null);

  constructor() {
    // Cada sesion nueva lo vuelve a pedir: en un celular compartido entra otra persona.
    effect(() => {
      if (!this.sesion.sesion()) {
        this._mia.set(null);
        return;
      }
      this.refrescar();
    });
  }

  refrescar(): void {
    this.api.miAsistencia().subscribe({
      next: (mia) => this._mia.set(mia),
      error: () => this._mia.set(null),
    });
  }

  /**
   * Entra si esta fuera y sale si esta dentro. Si el servidor dice que no
   * —marco en otro dispositivo—, se vuelve a leer en vez de insistir.
   */
  marcar(): void {
    if (this._marcando() || !this.conocida()) return;

    const salir = this.dentro();
    this._marcando.set(true);
    (salir ? this.api.marcarSalida() : this.api.marcarEntrada()).subscribe({
      next: (mia) => {
        this._marcando.set(false);
        this._mia.set(mia);
        this.avisos.exito(
          this.i18n.t(salir ? 'asistencia.avisoSalida' : 'asistencia.avisoEntrada', {
            hora: this.i18n.fecha(new Date(), 'hora'),
          }),
        );
      },
      error: () => {
        this._marcando.set(false);
        this.refrescar();
      },
    });
  }
}
