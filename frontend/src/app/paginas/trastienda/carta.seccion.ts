import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { forkJoin } from 'rxjs';

import {
  CatalogoCategoriasApi,
  CatalogoPlatillosApi,
  InventarioInsumosApi,
  type CategoriaResponseDto,
  type InsumoResponseDto,
  type PlatilloResponseDto,
  type RecetaItemDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/**
 * Carta: las secciones impresas, los platillos y la receta de cada uno.
 *
 * La receta no es decoracion: es lo que permite que el POS sepa que hay stock
 * para un plato y que la venta descuente los insumos correctos. Un platillo sin
 * receta se puede vender pero no descuenta nada, y el inventario se separa de la
 * realidad sin que nadie lo note.
 */
@Component({
  selector: 'app-carta-seccion',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './carta.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class CartaSeccion implements OnInit {
  private readonly platillosApi = inject(CatalogoPlatillosApi);
  private readonly categoriasApi = inject(CatalogoCategoriasApi);
  private readonly insumosApi = inject(InventarioInsumosApi);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  protected readonly platillos = signal<PlatilloResponseDto[]>([]);
  protected readonly categorias = signal<CategoriaResponseDto[]>([]);
  protected readonly insumos = signal<InsumoResponseDto[]>([]);

  protected readonly filtro = signal('');
  protected readonly categoriaFiltro = signal<number | null>(null);

  // --- alta de platillo -----------------------------------------------------

  protected readonly altaAbierta = signal(false);
  protected readonly nombre = signal('');
  protected readonly precio = signal<number | null>(null);
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly descripcion = signal('');

  // --- alta de categoria ----------------------------------------------------

  protected readonly categoriaAbierta = signal(false);
  protected readonly nombreCategoria = signal('');

  // --- receta ---------------------------------------------------------------

  protected readonly recetaDe = signal<PlatilloResponseDto | null>(null);
  protected readonly receta = signal<RecetaItemDto[]>([]);
  protected readonly insumoNuevo = signal('');
  protected readonly cantidadNueva = signal<number | null>(null);

  protected readonly visibles = computed(() => {
    const texto = this.filtro().trim().toLowerCase();
    const cat = this.categoriaFiltro();
    return this.platillos().filter((p) => {
      if (cat !== null && p.categoriaId !== cat) return false;
      if (texto.length === 0) return true;
      return (p.nombre ?? '').toLowerCase().includes(texto);
    });
  });

  /** Insumos que todavia no estan en la receta abierta. */
  protected readonly insumosDisponibles = computed(() => {
    const puestos = new Set(this.receta().map((r) => r.insumoId));
    return this.insumos().filter((i) => !puestos.has(i.id ?? ''));
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    forkJoin({
      platillos: this.platillosApi.buscarPlatillos({
        pageable: { page: 0, size: 300, sort: ['nombre,asc'] },
      }),
      categorias: this.categoriasApi.listarCategorias(),
      insumos: this.insumosApi.buscarInsumos({
        pageable: { page: 0, size: 300, sort: ['nombre,asc'] },
      }),
    }).subscribe({
      next: ({ platillos, categorias, insumos }) => {
        this.platillos.set(platillos.contenido ?? []);
        this.categorias.set(categorias);
        this.insumos.set(insumos.contenido ?? []);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected anotarFiltro(v: string): void {
    this.filtro.set(v);
  }

  protected filtrarPorCategoria(v: string): void {
    this.categoriaFiltro.set(v === '' ? null : Number(v));
  }

  protected nombreCategoriaDe(id: number | undefined): string {
    return this.categorias().find((c) => c.id === id)?.nombre ?? '—';
  }

  // --- alta de platillo -----------------------------------------------------

  protected abrirAlta(): void {
    this.altaAbierta.update((v) => !v);
    this.categoriaId.set(this.categorias()[0]?.id ?? null);
  }

  protected anotarNombre(v: string): void {
    this.nombre.set(v);
  }

  protected anotarPrecio(v: string): void {
    const n = Number.parseFloat(v);
    this.precio.set(Number.isFinite(n) ? n : null);
  }

  protected elegirCategoria(v: string): void {
    this.categoriaId.set(v === '' ? null : Number(v));
  }

  protected anotarDescripcion(v: string): void {
    this.descripcion.set(v);
  }

  protected altaPlatillo(): void {
    const nombre = this.nombre().trim();
    const precioVentaBase = this.precio();
    const categoriaId = this.categoriaId();
    if (!nombre || precioVentaBase === null || categoriaId === null || this.guardando()) return;

    this.guardando.set(true);
    this.platillosApi
      .crearPlatillo({
        platilloRequestDto: {
          nombre,
          precioVentaBase,
          categoriaId,
          descripcion: this.descripcion().trim() || undefined,
          activo: true,
        },
      })
      .subscribe({
        next: (p) => {
          this.guardando.set(false);
          this.altaAbierta.set(false);
          this.nombre.set('');
          this.precio.set(null);
          this.descripcion.set('');
          this.avisos.exito(this.t('carta.avisoPlatillo', { nombre: p.nombre ?? '' }));
          this.cargar();
          this.abrirReceta(p);
        },
        error: () => this.guardando.set(false),
      });
  }

  /**
   * Sacar un platillo de carta no lo borra: las comandas historicas conservan lo
   * que se vendio y a que precio. Solo deja de poder pedirse.
   */
  protected cambiarActivo(p: PlatilloResponseDto): void {
    if (!p.id || this.guardando()) return;

    this.guardando.set(true);
    this.platillosApi.cambiarActivoPlatillo({ id: p.id, activo: !p.activo }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  // --- alta de categoria ----------------------------------------------------

  protected abrirCategoria(): void {
    this.categoriaAbierta.update((v) => !v);
    this.nombreCategoria.set('');
  }

  protected anotarNombreCategoria(v: string): void {
    this.nombreCategoria.set(v);
  }

  protected altaCategoria(): void {
    const nombre = this.nombreCategoria().trim();
    if (!nombre || this.guardando()) return;

    this.guardando.set(true);
    this.categoriasApi.crearCategoria({ categoriaRequestDto: { nombre } }).subscribe({
      next: (c) => {
        this.guardando.set(false);
        this.categoriaAbierta.set(false);
        this.avisos.exito(this.t('carta.avisoSeccion', { nombre: c.nombre ?? '' }));
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  // --- receta ---------------------------------------------------------------

  protected abrirReceta(p: PlatilloResponseDto): void {
    if (this.recetaDe()?.id === p.id) {
      this.recetaDe.set(null);
      this.receta.set([]);
      return;
    }
    if (!p.id) return;

    this.recetaDe.set(p);
    this.receta.set([]);
    this.insumoNuevo.set('');
    this.cantidadNueva.set(null);
    this.platillosApi.obtenerReceta({ id: p.id }).subscribe({
      next: (items) => this.receta.set(items),
    });
  }

  protected elegirInsumo(v: string): void {
    this.insumoNuevo.set(v);
  }

  protected anotarCantidad(v: string): void {
    const n = Number.parseFloat(v);
    this.cantidadNueva.set(Number.isFinite(n) ? n : null);
  }

  protected agregarIngrediente(): void {
    const insumoId = this.insumoNuevo();
    const cantidadRequerida = this.cantidadNueva();
    if (!insumoId || cantidadRequerida === null || cantidadRequerida <= 0) return;

    const insumo = this.insumos().find((i) => i.id === insumoId);
    this.receta.update((r) => [
      ...r,
      {
        insumoId,
        cantidadRequerida,
        insumoNombre: insumo?.nombre,
        unidadMedida: insumo?.unidadMedida,
      },
    ]);
    this.insumoNuevo.set('');
    this.cantidadNueva.set(null);
  }

  protected quitarIngrediente(item: RecetaItemDto): void {
    this.receta.update((r) => r.filter((i) => i.insumoId !== item.insumoId));
  }

  /**
   * `PUT /platillos/{id}/receta` reemplaza la receta entera, no la parchea: lo
   * que se manda es la lista completa, y lo que no este deja de formar parte del
   * plato.
   */
  protected guardarReceta(): void {
    const p = this.recetaDe();
    if (!p?.id || this.guardando()) return;

    this.guardando.set(true);
    this.platillosApi
      .reemplazarReceta({
        id: p.id,
        recetaRequestDto: {
          insumos: this.receta().map((i) => ({
            insumoId: i.insumoId,
            cantidadRequerida: i.cantidadRequerida,
          })),
        },
      })
      .subscribe({
        next: (guardada) => {
          this.guardando.set(false);
          this.receta.set(guardada);
          this.avisos.exito(this.t('carta.avisoRecetaGuardada', { platillo: p.nombre ?? '' }));
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }
}
