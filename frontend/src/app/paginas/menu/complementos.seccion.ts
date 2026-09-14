import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  CatalogoComplementosApi,
  ComplementoRequestDtoTipoComplementoEnum,
  InventarioInsumosApi,
  type ComplementoResponseDto,
  type InsumoResponseDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

type Tipo = ComplementoRequestDtoTipoComplementoEnum;

const TIPOS = Object.values(ComplementoRequestDtoTipoComplementoEnum);

function aTipo(valor: string | undefined): Tipo {
  return TIPOS.find((t) => t === valor) ?? ComplementoRequestDtoTipoComplementoEnum.OTROS;
}

/**
 * Complementos: lo que se suma a un plato —la bebida, la salsa, el helado— con
 * su precio aparte.
 *
 * Apagar uno es el gesto de todos los dias: se acabo la chicha y el POS tiene
 * que dejar de ofrecerla ya, sin borrarla de la carta. Por eso el interruptor
 * va en la fila, y cambiar el precio queda un paso mas atras, en el dialogo.
 */
@Component({
  selector: 'app-complementos-seccion',
  imports: [DecimalPipe, Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './complementos.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class ComplementosSeccion implements OnInit {
  private readonly complementosApi = inject(CatalogoComplementosApi);
  private readonly insumosApi = inject(InventarioInsumosApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly TIPOS = TIPOS;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly complementos = signal<ComplementoResponseDto[]>([]);
  protected readonly insumos = signal<InsumoResponseDto[]>([]);
  protected readonly filtroTipo = signal('');

  // --- formulario -----------------------------------------------------------
  protected readonly formularioAbierto = signal(false);
  /** `null` es un complemento nuevo; un id, uno que se edita. */
  protected readonly editandoId = signal<string | null>(null);
  protected readonly nombre = signal('');
  protected readonly tipo = signal<Tipo>(ComplementoRequestDtoTipoComplementoEnum.BEBIDA);
  protected readonly precio = signal<number | null>(null);
  protected readonly insumoId = signal('');

  protected readonly visibles = computed(() => {
    const tipo = this.filtroTipo();
    return this.complementos()
      .filter((c) => !tipo || c.tipoComplemento === tipo)
      .sort(
        (a, b) =>
          (a.tipoComplemento ?? '').localeCompare(b.tipoComplemento ?? '') ||
          (a.nombre ?? '').localeCompare(b.nombre ?? ''),
      );
  });

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editandoId() ? 'complementos.editar' : 'complementos.nuevo'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    // Sin insumos el complemento se guarda igual; solo no descuenta stock.
    forkJoin({
      complementos: this.complementosApi.listarComplementos({}),
      insumos: this.insumosApi
        .buscarInsumos({ pageable: { page: 0, size: 300, sort: ['nombre,asc'] } })
        .pipe(catchError(() => of(null))),
    }).subscribe({
      next: ({ complementos, insumos }) => {
        this.complementos.set(complementos);
        this.insumos.set(insumos?.contenido ?? []);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected abrirNuevo(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.tipo.set(ComplementoRequestDtoTipoComplementoEnum.BEBIDA);
    this.precio.set(null);
    this.insumoId.set('');
    this.formularioAbierto.set(true);
  }

  protected abrirEdicion(c: ComplementoResponseDto): void {
    this.editandoId.set(c.id ?? null);
    this.nombre.set(c.nombre ?? '');
    this.tipo.set(aTipo(c.tipoComplemento));
    this.precio.set(c.precioAdicional ?? null);
    this.insumoId.set(c.insumoAsociadoId ?? '');
    this.formularioAbierto.set(true);
  }

  protected elegirTipo(valor: string): void {
    this.tipo.set(aTipo(valor));
  }

  protected anotarPrecio(valor: string): void {
    const n = Number.parseFloat(valor);
    this.precio.set(Number.isFinite(n) && n >= 0 ? n : null);
  }

  /**
   * El `PUT` reemplaza: se manda tambien si esta activo, o editar el precio de
   * un complemento apagado lo volveria a ofrecer sin que nadie lo pidiera.
   */
  protected guardar(): void {
    const nombre = this.nombre().trim();
    const precioAdicional = this.precio();
    if (!nombre || precioAdicional === null || this.guardando()) return;

    const id = this.editandoId();
    const actual = this.complementos().find((c) => c.id === id);
    const cuerpo = {
      nombre,
      precioAdicional,
      tipoComplemento: this.tipo(),
      insumoAsociadoId: this.insumoId() || undefined,
      activo: actual?.activo ?? true,
    };

    this.guardando.set(true);
    const peticion = id
      ? this.complementosApi.actualizarComplemento({ id, complementoRequestDto: cuerpo })
      : this.complementosApi.crearComplemento({ complementoRequestDto: cuerpo });

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t(id ? 'complementos.avisoGuardado' : 'complementos.avisoAlta', { nombre }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  protected alternarActivo(c: ComplementoResponseDto): void {
    if (!c.id || this.guardando()) return;

    const activo = !c.activo;
    this.guardando.set(true);
    this.complementosApi.cambiarActivoComplemento({ id: c.id, activo }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.complementos.update((lista) =>
          lista.map((x) => (x.id === c.id ? { ...x, activo } : x)),
        );
        this.avisos.exito(
          this.t(activo ? 'menu.avisoSeOfrece' : 'menu.avisoNoSeOfrece', { nombre: c.nombre ?? '' }),
        );
      },
      error: () => this.guardando.set(false),
    });
  }
}
