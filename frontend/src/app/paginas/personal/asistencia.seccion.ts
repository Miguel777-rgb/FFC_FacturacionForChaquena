import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';

import {
  AsistenciaDelDiaDtoEstadoEnum,
  PersonalAsistenciaApi,
  type AsistenciaDelDiaDto,
} from '../../api';
import { I18nService } from '../../nucleo/i18n/i18n.service';

function hoyComoCampo(): string {
  const f = new Date();
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}`;
}

/**
 * Asistencia del dia: cada turno con la entrada y la salida que le tocan, y
 * quien entro sin tener turno.
 *
 * El estado lo calcula el servidor con la tolerancia de diez minutos. Aqui no
 * se decide quien falto: una persona cuyo turno sigue en curso todavia "no
 * llega", y solo "falto" cuando el turno ya termino.
 */
@Component({
  selector: 'app-asistencia-seccion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './asistencia.seccion.html',
  styleUrls: ['../../disenio/secciones.scss', './asistencia.seccion.scss'],
})
export class AsistenciaSeccion implements OnInit {
  private readonly api = inject(PersonalAsistenciaApi);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly ESTADO = AsistenciaDelDiaDtoEstadoEnum;
  protected readonly ESTADOS = Object.values(AsistenciaDelDiaDtoEstadoEnum);

  protected readonly dia = signal(hoyComoCampo());
  /** `null` mientras llega: una lista vacia ya significa "nadie tenia turno". */
  protected readonly filas = signal<AsistenciaDelDiaDto[] | null>(null);

  protected readonly conteo = computed(() => {
    const cuenta: Record<string, number> = {};
    for (const f of this.filas() ?? []) {
      if (f.estado) cuenta[f.estado] = (cuenta[f.estado] ?? 0) + 1;
    }
    return cuenta;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.api.asistenciaDelDia({ dia: this.dia() }).subscribe({
      next: (lista) => this.filas.set(lista),
      error: () => this.filas.set([]),
    });
  }

  protected cambiarDia(valor: string): void {
    if (!valor) return;
    this.dia.set(valor);
    this.filas.set(null);
    this.cargar();
  }

  protected hora(valor: string | undefined): string {
    return valor ? this.i18n.fecha(valor, 'hora') : '—';
  }

  /** Faltar y no haber llegado son lo que hay que mirar; lo demas es informacion. */
  protected alarma(estado: AsistenciaDelDiaDtoEstadoEnum): boolean {
    return (
      estado === AsistenciaDelDiaDtoEstadoEnum.FALTO ||
      estado === AsistenciaDelDiaDtoEstadoEnum.NO_LLEGA
    );
  }
}
