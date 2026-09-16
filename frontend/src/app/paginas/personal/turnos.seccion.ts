import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  PersonalTurnosApi,
  TrabajadoresApi,
  type TrabajadorResponseDto,
  type TurnoDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { fechaDeDia } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/** Los dias como los nombra el diccionario (`dia.MONDAY`), de lunes a domingo. */
const DIAS = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
] as const;

function campoDeDia(fecha: Date): string {
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${fecha.getFullYear()}-${dos(fecha.getMonth() + 1)}-${dos(fecha.getDate())}`;
}

function lunesDe(fecha: Date): Date {
  const lunes = new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate());
  lunes.setDate(lunes.getDate() - ((lunes.getDay() + 6) % 7));
  return lunes;
}

function sumarDias(fecha: Date, dias: number): Date {
  const resultado = new Date(fecha);
  resultado.setDate(resultado.getDate() + dias);
  return resultado;
}

/** "12:00:00", como lo manda el servidor, escrito "12:00". */
function horaCorta(hora: string | undefined): string {
  return (hora ?? '').slice(0, 5);
}

function nombreDe(t: TrabajadorResponseDto): string {
  return `${t.nombres ?? ''} ${t.apellidos ?? ''}`.trim();
}

/**
 * Turnos: el horario de la semana, una fila por persona y una columna por dia.
 *
 * Es un plan, no un registro: un turno se cambia o se quita sin dejar rastro,
 * porque lo que paso de verdad son las marcaciones. Por eso la tardanza y la
 * falta no se escriben aqui; salen de comparar las dos cosas.
 *
 * La tabla es ancha a proposito y en el celular se desplaza: siete dias en una
 * columna no permitirian ver quien cubre el sabado.
 */
@Component({
  selector: 'app-turnos-seccion',
  imports: [Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './turnos.seccion.html',
  styleUrls: ['../../disenio/secciones.scss', './turnos.seccion.scss'],
})
export class TurnosSeccion implements OnInit {
  private readonly turnosApi = inject(PersonalTurnosApi);
  private readonly trabajadoresApi = inject(TrabajadoresApi);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly fecha = this.i18n.fecha;
  protected readonly nombreDe = nombreDe;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly lunes = signal(lunesDe(new Date()));
  protected readonly turnos = signal<TurnoDto[]>([]);
  protected readonly trabajadores = signal<TrabajadorResponseDto[]>([]);

  protected readonly dias = computed(() => {
    const hoy = campoDeDia(new Date());
    return DIAS.map((nombre, i) => {
      const fecha = sumarDias(this.lunes(), i);
      const campo = campoDeDia(fecha);
      return { nombre, numero: fecha.getDate(), campo, hoy: campo === hoy };
    });
  });

  protected readonly tituloSemana = computed(() =>
    this.t('turnos.semanaDel', {
      desde: this.fecha(this.lunes(), 'dia'),
      hasta: this.fecha(sumarDias(this.lunes(), 6), 'dia'),
    }),
  );

  protected readonly personas = computed(() =>
    [...this.trabajadores()].sort((a, b) => nombreDe(a).localeCompare(nombreDe(b))),
  );

  /** Los turnos por persona y dia, para no recorrer la lista entera en cada celda. */
  private readonly porCelda = computed(() => {
    const mapa = new Map<string, TurnoDto[]>();
    for (const turno of this.turnos()) {
      const clave = `${turno.trabajadorId}|${turno.fecha}`;
      mapa.set(clave, [...(mapa.get(clave) ?? []), turno]);
    }
    return mapa;
  });

  // --- ficha del turno ------------------------------------------------------
  protected readonly formularioAbierto = signal(false);
  protected readonly editando = signal<TurnoDto | null>(null);
  protected readonly trabajadorId = signal('');
  protected readonly dia = signal('');
  protected readonly inicio = signal('12:00');
  protected readonly fin = signal('20:00');
  protected readonly nota = signal('');

  /** Salir a las dos habiendo entrado a las seis de la tarde es salir al dia siguiente, no un error. */
  protected readonly terminaAlDiaSiguiente = computed(
    () => !!this.inicio() && !!this.fin() && this.fin() < this.inicio(),
  );

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editando() ? 'turnos.editar' : 'turnos.nuevo'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    forkJoin({
      turnos: this.turnosApi.listarTurnos({ desde: campoDeDia(this.lunes()) }),
      trabajadores: this.trabajadoresApi
        .listarTrabajadoresActivos()
        .pipe(catchError(() => of([] as TrabajadorResponseDto[]))),
    }).subscribe({
      next: ({ turnos, trabajadores }) => {
        this.turnos.set(turnos);
        this.trabajadores.set(trabajadores);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected turnosDe(persona: TrabajadorResponseDto, campo: string): TurnoDto[] {
    return this.porCelda().get(`${persona.id}|${campo}`) ?? [];
  }

  protected rango(turno: TurnoDto): string {
    return `${horaCorta(turno.inicio)} – ${horaCorta(turno.fin)}`;
  }

  protected moverSemana(semanas: number): void {
    this.lunes.update((lunes) => sumarDias(lunes, semanas * 7));
    this.cargar();
  }

  protected estaSemana(): void {
    this.lunes.set(lunesDe(new Date()));
    this.cargar();
  }

  protected abrirNuevo(persona?: TrabajadorResponseDto, campo?: string): void {
    this.editando.set(null);
    this.trabajadorId.set(persona?.id ?? this.personas()[0]?.id ?? '');
    this.dia.set(campo ?? campoDeDia(new Date()));
    this.inicio.set('12:00');
    this.fin.set('20:00');
    this.nota.set('');
    this.formularioAbierto.set(true);
  }

  protected abrirEdicion(turno: TurnoDto): void {
    this.editando.set(turno);
    this.trabajadorId.set(turno.trabajadorId);
    this.dia.set(turno.fecha);
    this.inicio.set(horaCorta(turno.inicio));
    this.fin.set(horaCorta(turno.fin));
    this.nota.set(turno.nota ?? '');
    this.formularioAbierto.set(true);
  }

  protected guardar(): void {
    const trabajadorId = this.trabajadorId();
    const fecha = this.dia();
    const inicio = this.inicio();
    const fin = this.fin();
    if (!trabajadorId || !fecha || !inicio || !fin || this.guardando()) return;
    if (inicio === fin) {
      this.avisos.info(this.t('turnos.avisoCeroHoras'));
      return;
    }

    const turnoDto: TurnoDto = {
      trabajadorId,
      fecha,
      inicio,
      fin,
      nota: this.nota().trim() || undefined,
    };
    const id = this.editando()?.id;

    this.guardando.set(true);
    const peticion = id
      ? this.turnosApi.actualizarTurno({ id, turnoDto })
      : this.turnosApi.crearTurno({ turnoDto });
    peticion.subscribe({
      next: (guardado) => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t('turnos.avisoGuardado', { nombre: guardado.trabajadorNombre ?? '' }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  /** Quitar un turno no borra las entradas que ya marco esa persona. */
  protected async eliminar(): Promise<void> {
    const turno = this.editando();
    if (!turno?.id || this.guardando()) return;

    const confirmado = await this.confirmacion.pedir({
      titulo: this.t('turnos.eliminarTitulo', { nombre: turno.trabajadorNombre ?? '' }),
      mensaje: this.t('turnos.eliminarMensaje', {
        dia: this.fecha(fechaDeDia(turno.fecha), 'dia'),
        rango: this.rango(turno),
      }),
      confirmar: this.t('turnos.eliminar'),
    });
    if (!confirmado) return;

    const id = turno.id;
    this.guardando.set(true);
    this.turnosApi.eliminarTurno({ id }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t('turnos.avisoEliminado', { nombre: turno.trabajadorNombre ?? '' }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }
}
