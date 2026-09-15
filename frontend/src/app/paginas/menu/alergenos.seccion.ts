import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';

import { CatalogoAlergenosApi, type AlergenoDto } from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/**
 * Alergenos: el catalogo que se marca en cada platillo.
 *
 * Llega sembrado con los catorce habituales, y el local agrega o renombra los
 * que su cocina necesite. Renombrar uno cambia lo que dicen todos los platillos
 * que lo llevan, porque sigue siendo el mismo alergeno.
 *
 * Se dan de baja, no se borran: uno de baja deja de ofrecerse al marcar un
 * platillo, pero los platillos que ya lo tenian lo siguen mostrando.
 */
@Component({
  selector: 'app-alergenos-seccion',
  imports: [Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './alergenos.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class AlergenosSeccion implements OnInit {
  private readonly alergenosApi = inject(CatalogoAlergenosApi);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly alergenos = signal<AlergenoDto[]>([]);

  protected readonly formularioAbierto = signal(false);
  protected readonly editandoId = signal<number | null>(null);
  protected readonly nombre = signal('');

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editandoId() === null ? 'alergenos.nuevo' : 'alergenos.editar'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.alergenosApi.listarAlergenos({ soloActivos: false }).subscribe({
      next: (lista) => {
        this.alergenos.set(lista);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected abrirNuevo(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.formularioAbierto.set(true);
  }

  protected abrirEdicion(a: AlergenoDto): void {
    this.editandoId.set(a.id ?? null);
    this.nombre.set(a.nombre);
    this.formularioAbierto.set(true);
  }

  /** El nombre repetido se avisa antes de enviar, para no gastar un 409. */
  protected guardar(): void {
    const nombre = this.nombre().trim();
    if (!nombre || this.guardando()) return;

    const id = this.editandoId();
    const repetido = this.alergenos().find(
      (a) => a.id !== id && a.nombre.trim().toLowerCase() === nombre.toLowerCase(),
    );
    if (repetido) {
      this.avisos.info(this.t('alergenos.avisoRepetido', { nombre: repetido.nombre }));
      return;
    }

    this.guardando.set(true);
    const peticion =
      id === null
        ? this.alergenosApi.crearAlergeno({ alergenoDto: { nombre } })
        : this.alergenosApi.actualizarAlergeno({ id, alergenoDto: { nombre } });

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t(id === null ? 'alergenos.avisoAlta' : 'alergenos.avisoGuardado', { nombre }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  protected alternarActivo(a: AlergenoDto): void {
    if (a.id === undefined || this.guardando()) return;

    const activo = !a.activo;
    this.guardando.set(true);
    this.alergenosApi.cambiarActivoAlergeno({ id: a.id, activo }).subscribe({
      next: (actualizado) => {
        this.guardando.set(false);
        this.alergenos.update((lista) => lista.map((x) => (x.id === a.id ? actualizado : x)));
        this.avisos.exito(
          this.t(activo ? 'alergenos.avisoReactivado' : 'alergenos.avisoBaja', {
            nombre: a.nombre,
          }),
        );
      },
      error: () => this.guardando.set(false),
    });
  }
}
