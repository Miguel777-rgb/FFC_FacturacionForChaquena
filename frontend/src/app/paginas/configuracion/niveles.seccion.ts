import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';

import { NivelesDeLealtadApi, type NivelLealtadDto } from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/**
 * Niveles de lealtad: los escalones por puntos y el descuento de cada uno.
 *
 * La tabla dice el tramo completo de cada nivel —«5 a 14 puntos»— y no solo
 * su minimo, porque lo que el administrador necesita ver es si los escalones
 * quedan razonables uno al lado del otro. El tramo sale de los propios
 * minimos: no hay un «hasta» guardado que pueda contradecirlos.
 *
 * El servidor rechaza dos niveles desde los mismos puntos; aqui se avisa antes
 * de enviar para no gastar un viaje en un 409.
 */
@Component({
  selector: 'app-niveles-seccion',
  imports: [DecimalPipe, Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './niveles.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class NivelesSeccion implements OnInit {
  private readonly nivelesApi = inject(NivelesDeLealtadApi);
  private readonly avisos = inject(AvisosService);
  private readonly confirmacion = inject(ConfirmacionService);

  protected readonly t = inject(I18nService).t;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly niveles = signal<NivelLealtadDto[]>([]);

  // --- formulario -----------------------------------------------------------
  protected readonly formularioAbierto = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly nombre = signal('');
  protected readonly puntos = signal<number | null>(null);
  protected readonly descuento = signal<number | null>(null);

  /** Cada nivel con el ultimo punto de su tramo; el mas alto no tiene techo. */
  protected readonly filas = computed(() => {
    const ordenados = [...this.niveles()].sort((a, b) => a.puntosMinimos - b.puntosMinimos);
    return ordenados.map((nivel, i) => ({
      nivel,
      hasta: i + 1 < ordenados.length ? ordenados[i + 1].puntosMinimos - 1 : null,
    }));
  });

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editandoId() ? 'niveles.editar' : 'niveles.nuevo'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.nivelesApi.listarNivelesLealtad().subscribe({
      next: (lista) => {
        this.niveles.set(lista);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected abrirNuevo(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.puntos.set(null);
    this.descuento.set(0);
    this.formularioAbierto.set(true);
  }

  protected abrirEdicion(nivel: NivelLealtadDto): void {
    this.editandoId.set(nivel.id ?? null);
    this.nombre.set(nivel.nombre);
    this.puntos.set(nivel.puntosMinimos);
    this.descuento.set(nivel.porcentajeDescuento);
    this.formularioAbierto.set(true);
  }

  protected anotarPuntos(valor: string): void {
    const n = Number.parseInt(valor, 10);
    this.puntos.set(Number.isFinite(n) ? n : null);
  }

  protected anotarDescuento(valor: string): void {
    const n = Number.parseFloat(valor);
    this.descuento.set(Number.isFinite(n) ? n : null);
  }

  protected guardar(): void {
    const nombre = this.nombre().trim();
    const puntosMinimos = this.puntos();
    const porcentajeDescuento = this.descuento();
    if (this.guardando()) return;

    if (
      !nombre ||
      puntosMinimos === null ||
      puntosMinimos < 0 ||
      porcentajeDescuento === null ||
      porcentajeDescuento < 0 ||
      porcentajeDescuento > 100
    ) {
      this.avisos.info(this.t('niveles.avisoDatos'));
      return;
    }

    const id = this.editandoId();
    if (this.niveles().some((n) => n.puntosMinimos === puntosMinimos && n.id !== id)) {
      this.avisos.info(this.t('niveles.avisoPuntosRepetidos', { puntos: puntosMinimos }));
      return;
    }

    const nivelLealtadDto = { nombre, puntosMinimos, porcentajeDescuento };
    this.guardando.set(true);
    const peticion = id
      ? this.nivelesApi.actualizarNivelLealtad({ id, nivelLealtadDto })
      : this.nivelesApi.crearNivelLealtad({ nivelLealtadDto });

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(this.t(id ? 'niveles.avisoGuardado' : 'niveles.avisoAlta', { nombre }));
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  protected async eliminar(nivel: NivelLealtadDto): Promise<void> {
    if (!nivel.id || this.guardando()) return;

    const confirmado = await this.confirmacion.pedir({
      titulo: this.t('niveles.eliminarTitulo', { nombre: nivel.nombre }),
      mensaje: this.t('niveles.eliminarMensaje'),
      confirmar: this.t('niveles.eliminar'),
    });
    if (!confirmado || this.guardando()) return;

    this.guardando.set(true);
    this.nivelesApi.eliminarNivelLealtad({ id: nivel.id }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.niveles.update((lista) => lista.filter((n) => n.id !== nivel.id));
        this.avisos.exito(this.t('niveles.avisoEliminado', { nombre: nivel.nombre }));
      },
      error: () => this.guardando.set(false),
    });
  }
}
