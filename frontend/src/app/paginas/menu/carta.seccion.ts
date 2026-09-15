import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  ArchivosApi,
  CatalogoAlergenosApi,
  CatalogoCategoriasApi,
  CatalogoPlatillosApi,
  InventarioInsumosApi,
  type AlergenoDto,
  type CategoriaResponseDto,
  type InsumoResponseDto,
  type PlatilloRequestDto,
  type PlatilloResponseDto,
  type RecetaItemDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { problemaDeImagen, urlDeArchivo } from '../../nucleo/marca/archivos';

/**
 * Carta: las secciones impresas, los platillos y la receta de cada uno.
 *
 * La receta no es decoracion: es lo que permite que el POS sepa que hay stock
 * para un plato y que la venta descuente los insumos correctos. Un platillo sin
 * receta se puede vender pero no descuenta nada, y el inventario se separa de la
 * realidad sin que nadie lo note.
 *
 * El costo y el margen no se escriben aqui: los calcula el servidor con la
 * receta y la ultima compra de cada insumo. Cuando falta el costo de alguno, la
 * tabla dice cual, porque eso es lo que hay que ir a registrar.
 */
@Component({
  selector: 'app-carta-seccion',
  imports: [DecimalPipe, Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './carta.seccion.html',
  styleUrls: ['../../disenio/secciones.scss', './carta.seccion.scss'],
})
export class CartaSeccion implements OnInit {
  private readonly platillosApi = inject(CatalogoPlatillosApi);
  private readonly categoriasApi = inject(CatalogoCategoriasApi);
  private readonly insumosApi = inject(InventarioInsumosApi);
  private readonly alergenosApi = inject(CatalogoAlergenosApi);
  private readonly archivosApi = inject(ArchivosApi);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;
  protected readonly urlDeArchivo = urlDeArchivo;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  protected readonly platillos = signal<PlatilloResponseDto[]>([]);
  protected readonly categorias = signal<CategoriaResponseDto[]>([]);
  protected readonly insumos = signal<InsumoResponseDto[]>([]);
  protected readonly alergenos = signal<AlergenoDto[]>([]);

  protected readonly filtro = signal('');
  protected readonly categoriaFiltro = signal<number | null>(null);

  // --- ficha del platillo: alta y edicion -----------------------------------

  protected readonly fichaAbierta = signal(false);
  /** `null` es un platillo nuevo. */
  protected readonly editando = signal<PlatilloResponseDto | null>(null);
  protected readonly nombre = signal('');
  protected readonly precio = signal<number | null>(null);
  protected readonly categoriaId = signal<number | null>(null);
  protected readonly descripcion = signal('');
  protected readonly tiempo = signal<number | null>(null);
  protected readonly fotoId = signal<string | null>(null);
  protected readonly subiendoFoto = signal(false);
  protected readonly errorFoto = signal<string | null>(null);
  protected readonly marcados = signal<ReadonlySet<number>>(new Set());

  protected readonly tituloFicha = computed(() => {
    const p = this.editando();
    return p
      ? this.t('carta.editarTitulo', { platillo: p.nombre ?? '' })
      : this.t('carta.nuevoPlatillo');
  });

  /**
   * Los que se pueden marcar: los activos, mas los dados de baja que el
   * platillo ya tenia. Esconder esos haria que guardar el platillo los quitara
   * sin que nadie lo decidiera.
   */
  protected readonly alergenosOfrecidos = computed(() => {
    const marcados = this.marcados();
    return this.alergenos().filter((a) => a.activo || marcados.has(a.id ?? -1));
  });

  /**
   * El costo es del servidor; el margen se recalcula con el precio que se esta
   * escribiendo, para ver cuanto deja antes de guardar.
   */
  protected readonly margenEnFicha = computed(() => {
    const costo = this.editando()?.costo;
    const precio = this.precio();
    if (costo === undefined || costo === null || precio === null) return null;
    const margen = precio - costo;
    return { costo, margen, porcentaje: precio > 0 ? (margen * 100) / precio : null };
  });

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
      // Sin catalogo el platillo se guarda igual; solo no se ofrecen casillas.
      alergenos: this.alergenosApi
        .listarAlergenos({ soloActivos: false })
        .pipe(catchError(() => of([] as AlergenoDto[]))),
    }).subscribe({
      next: ({ platillos, categorias, insumos, alergenos }) => {
        this.platillos.set(platillos.contenido ?? []);
        this.categorias.set(categorias);
        this.insumos.set(insumos.contenido ?? []);
        this.alergenos.set(alergenos);
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

  protected alergenosDe(p: PlatilloResponseDto): string {
    return (p.alergenos ?? []).map((a) => a.nombre).join(', ');
  }

  // --- ficha del platillo: alta y edicion -----------------------------------

  protected abrirAlta(): void {
    this.editando.set(null);
    this.nombre.set('');
    this.precio.set(null);
    this.categoriaId.set(this.categoriaFiltro() ?? this.categorias()[0]?.id ?? null);
    this.descripcion.set('');
    this.tiempo.set(null);
    this.fotoId.set(null);
    this.marcados.set(new Set());
    this.errorFoto.set(null);
    this.fichaAbierta.set(true);
  }

  protected abrirEdicion(p: PlatilloResponseDto): void {
    this.editando.set(p);
    this.nombre.set(p.nombre ?? '');
    this.precio.set(p.precioVentaBase ?? null);
    this.categoriaId.set(p.categoriaId ?? null);
    this.descripcion.set(p.descripcion ?? '');
    this.tiempo.set(p.tiempoPreparacionMinutos ?? null);
    this.fotoId.set(p.fotoId ?? null);
    this.marcados.set(new Set((p.alergenos ?? []).map((a) => a.id ?? -1).filter((id) => id >= 0)));
    this.errorFoto.set(null);
    this.fichaAbierta.set(true);
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

  /** Vacio es "sin definir", no cero minutos. */
  protected anotarTiempo(v: string): void {
    const n = Number.parseInt(v, 10);
    this.tiempo.set(Number.isFinite(n) && n > 0 ? n : null);
  }

  protected alternarAlergeno(id: number | undefined, marcado: boolean): void {
    if (id === undefined) return;
    this.marcados.update((actuales) => {
      const siguiente = new Set(actuales);
      if (marcado) siguiente.add(id);
      else siguiente.delete(id);
      return siguiente;
    });
  }

  /**
   * La foto se sube al elegirla y la ficha solo guarda su id: asi se ve antes
   * de guardar, y guardar no tiene que esperar a una subida.
   */
  protected elegirFoto(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    // Se limpia para que elegir el mismo archivo otra vez vuelva a disparar change.
    entrada.value = '';
    if (!archivo) return;

    const problema = problemaDeImagen(archivo);
    if (problema) {
      this.errorFoto.set(this.t(problema.clave, { kb: problema.kb }));
      return;
    }

    this.errorFoto.set(null);
    this.subiendoFoto.set(true);
    this.archivosApi.subirArchivo({ archivo }).subscribe({
      next: (subido) => {
        this.subiendoFoto.set(false);
        this.fotoId.set(subido.id ?? null);
      },
      error: () => this.subiendoFoto.set(false),
    });
  }

  protected quitarFoto(): void {
    this.fotoId.set(null);
  }

  /**
   * El `PUT` reemplaza: viaja la foto, el tiempo y la lista completa de
   * alergenos. Lo que no vaya se quita.
   */
  protected guardarPlatillo(): void {
    const nombre = this.nombre().trim();
    const precioVentaBase = this.precio();
    const categoriaId = this.categoriaId();
    if (!nombre || precioVentaBase === null || categoriaId === null) return;
    if (this.guardando() || this.subiendoFoto()) return;

    const editando = this.editando();
    const platilloRequestDto: PlatilloRequestDto = {
      nombre,
      precioVentaBase,
      categoriaId,
      descripcion: this.descripcion().trim() || undefined,
      activo: editando ? editando.activo : true,
      fotoId: this.fotoId() ?? undefined,
      tiempoPreparacionMinutos: this.tiempo() ?? undefined,
      alergenoIds: [...this.marcados()],
    };

    this.guardando.set(true);
    const peticion = editando?.id
      ? this.platillosApi.actualizarPlatillo({ id: editando.id, platilloRequestDto })
      : this.platillosApi.crearPlatillo({ platilloRequestDto });

    peticion.subscribe({
      next: (p) => {
        this.guardando.set(false);
        this.fichaAbierta.set(false);
        this.avisos.exito(
          this.t(editando ? 'carta.avisoEditado' : 'carta.avisoPlatillo', {
            nombre: p.nombre ?? nombre,
          }),
        );
        this.cargar();
        // Un platillo nuevo todavia no descuenta nada: se abre su receta.
        if (!editando) this.abrirReceta(p);
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
