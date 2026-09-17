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
  CatalogoCategoriasApi,
  CatalogoLecturaDeCartaApi,
  CatalogoPlatillosApi,
  ComplementoImportacionDtoTipoEnum,
  type CategoriaResponseDto,
  type ComplementoImportacionDto,
  type LecturaCartaDto,
  type PlatilloResponseDto,
  type SeccionImportacionDto,
} from '../../api';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/** Tantas como caben en una carta doblada; mas es una espera larga sin ganar nada. */
const MAX_FOTOS = 6;

/** Lo que Tesseract necesita para leer letra pequena sin que la subida pese de mas. */
const ANCHO_SUBIDA = 2000;
const CALIDAD_SUBIDA = 0.85;

const TIPOS = ['image/jpeg', 'image/png', 'image/webp'];

/**
 * El mismo nombre sin tildes ni mayusculas que usa el servidor para reconocer lo
 * que ya existe. Aqui sirve para dos cosas: juntar en una sola lo que dos fotos
 * leyeron igual, y decir por adelantado que va a pasar con cada fila.
 */
function clave(texto: string | null | undefined): string {
  return (texto ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

/** Lo que va a pasar con una fila al importarla. */
interface Estado {
  tipo: 'nuevo' | 'igual' | 'precio' | 'descripcion';
  /** El precio que tiene hoy el platillo, cuando la fila lo cambia. */
  antes: number | null;
}

interface FilaRevision {
  id: number;
  marcada: boolean;
  nombre: string;
  precio: number | null;
  descripcion: string;
  vegetariano: boolean;
  dudosa: boolean;
  recorte: string | null;
}

interface SeccionRevision {
  id: number;
  /** Lo que decia la foto. Se conserva para poder compararlo con lo elegido. */
  leida: string;
  categoriaId: number | null;
  nombreNueva: string;
  filas: FilaRevision[];
}

interface FilaAdicional {
  id: number;
  marcada: boolean;
  nombre: string;
  precio: number | null;
  tipo: ComplementoImportacionDtoTipoEnum;
  dudosa: boolean;
  recorte: string | null;
}

/**
 * Leer la carta desde fotos. Es de ADMIN porque una importacion crea o cambia de
 * precio decenas de platillos de una vez.
 *
 * Leer no guarda nada: el servidor devuelve lo que reconocio y esta pantalla lo
 * deja editar fila por fila. Se importa solo lo que quede marcado, y el precio y
 * la descripcion se escriben a mano cuando la foto no se dejo leer; las filas que
 * el servidor marco dudosas traen al lado el recorte de la foto de donde salieron,
 * que es mas rapido que volver a mirar la carta entera.
 */
@Component({
  selector: 'app-lectura-carta-seccion',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './lectura-carta.seccion.html',
  styleUrls: ['../../disenio/secciones.scss', './lectura-carta.seccion.scss'],
})
export class LecturaCartaSeccion implements OnInit {
  private readonly lecturaApi = inject(CatalogoLecturaDeCartaApi);
  private readonly categoriasApi = inject(CatalogoCategoriasApi);
  private readonly platillosApi = inject(CatalogoPlatillosApi);
  private readonly avisos = inject(AvisosService);

  private readonly i18n = inject(I18nService);
  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly MAX_FOTOS = MAX_FOTOS;
  protected readonly TIPOS_ADICIONAL = Object.values(ComplementoImportacionDtoTipoEnum);

  protected readonly lectorDisponible = signal(true);
  protected readonly motivoLector = signal('');

  protected readonly categorias = signal<CategoriaResponseDto[]>([]);
  /** Los que ya estan en la carta, por nombre normalizado. */
  private readonly existentes = signal<ReadonlyMap<string, PlatilloResponseDto>>(new Map());

  protected readonly fotos = signal<File[]>([]);
  protected readonly leyendo = signal(0);
  protected readonly importando = signal(false);

  protected readonly secciones = signal<SeccionRevision[]>([]);
  protected readonly adicionales = signal<FilaAdicional[]>([]);

  private siguienteId = 1;

  protected readonly hayRevision = computed(
    () => this.secciones().length > 0 || this.adicionales().length > 0,
  );

  protected readonly dudosas = computed(
    () =>
      this.secciones().reduce((n, s) => n + s.filas.filter((f) => f.dudosa).length, 0) +
      this.adicionales().filter((a) => a.dudosa).length,
  );

  protected readonly marcadas = computed(
    () =>
      this.secciones().reduce((n, s) => n + s.filas.filter((f) => f.marcada).length, 0) +
      this.adicionales().filter((a) => a.marcada).length,
  );

  ngOnInit(): void {
    forkJoin({
      estado: this.lecturaApi
        .estadoLectorCarta()
        .pipe(catchError(() => of({ disponible: false, motivo: '' }))),
      categorias: this.categoriasApi.listarCategorias().pipe(catchError(() => of([]))),
      platillos: this.platillosApi
        .buscarPlatillos({ pageable: { page: 0, size: 500, sort: ['nombre,asc'] } })
        .pipe(catchError(() => of({ contenido: [] }))),
    }).subscribe(({ estado, categorias, platillos }) => {
      this.lectorDisponible.set(estado.disponible ?? false);
      this.motivoLector.set(estado.motivo ?? '');
      this.categorias.set(categorias);
      const mapa = new Map<string, PlatilloResponseDto>();
      for (const p of platillos.contenido ?? []) {
        const k = clave(p.nombre);
        if (k && !mapa.has(k)) mapa.set(k, p);
      }
      this.existentes.set(mapa);
    });
  }

  // --- fotos ----------------------------------------------------------------

  protected elegirFotos(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const elegidas = [...(entrada.files ?? [])];
    // Se limpia para que volver a elegir el mismo archivo dispare change otra vez.
    entrada.value = '';
    if (elegidas.length === 0) return;

    if (elegidas.some((f) => !TIPOS.includes(f.type))) {
      this.avisos.error(this.t('lectura.formatoFoto'));
    }
    const validas = elegidas.filter((f) => TIPOS.includes(f.type));
    const juntas = [...this.fotos(), ...validas];
    if (juntas.length > MAX_FOTOS) {
      this.avisos.info(this.t('lectura.sobranFotos', { max: MAX_FOTOS }));
    }
    this.fotos.set(juntas.slice(0, MAX_FOTOS));
  }

  protected quitarFoto(foto: File): void {
    this.fotos.update((fs) => fs.filter((f) => f !== foto));
  }

  /**
   * Las fotos van una detras de otra y no todas a la vez: cada lectura ocupa el
   * procesador del servidor durante varios segundos, y asi la barra puede decir
   * por cual va.
   */
  protected async leerFotos(): Promise<void> {
    const fotos = this.fotos();
    if (fotos.length === 0 || this.leyendo() > 0) return;

    this.secciones.set([]);
    this.adicionales.set([]);
    for (let i = 0; i < fotos.length; i++) {
      this.leyendo.set(i + 1);
      try {
        const imagen = await this.comprimir(fotos[i]);
        const lectura = await new Promise<LecturaCartaDto>((resolver, fallar) =>
          this.lecturaApi.leerCarta({ imagen }).subscribe({ next: resolver, error: fallar }),
        );
        this.juntar(lectura);
      } catch {
        // El interceptor ya avisa del fallo; el resto de las fotos sigue.
      }
    }
    this.leyendo.set(0);
    if (!this.hayRevision()) this.avisos.info(this.t('lectura.nadaLeido'));
  }

  /**
   * La foto de un celular pesa varios megas y lleva mas pixeles de los que hacen
   * falta. Se reduce en el navegador para que la subida quepa en el limite del
   * servidor y el viaje no dependa de la conexion del local.
   */
  private async comprimir(foto: File): Promise<Blob> {
    const mapa = await createImageBitmap(foto);
    const escala = Math.min(1, ANCHO_SUBIDA / mapa.width);
    const lienzo = document.createElement('canvas');
    lienzo.width = Math.round(mapa.width * escala);
    lienzo.height = Math.round(mapa.height * escala);
    const pincel = lienzo.getContext('2d');
    if (!pincel) return foto;
    pincel.drawImage(mapa, 0, 0, lienzo.width, lienzo.height);
    mapa.close();
    const jpeg = await new Promise<Blob | null>((resolver) =>
      lienzo.toBlob(resolver, 'image/jpeg', CALIDAD_SUBIDA),
    );
    return jpeg ?? foto;
  }

  // --- revision -------------------------------------------------------------

  /** Lo de la foto nueva se suma a lo que ya se reviso, sin repetir nombres. */
  private juntar(lectura: LecturaCartaDto): void {
    const secciones = [...this.secciones()];
    const vistos = new Set(secciones.flatMap((s) => s.filas.map((f) => clave(f.nombre))));

    for (const leida of lectura.secciones ?? []) {
      const nombre = leida.nombre ?? '';
      let seccion = secciones.find((s) => clave(s.leida) === clave(nombre));
      if (!seccion) {
        const existente = this.categorias().find((c) => clave(c.nombre) === clave(nombre));
        seccion = {
          id: this.siguienteId++,
          leida: nombre,
          categoriaId: existente?.id ?? null,
          nombreNueva: existente ? '' : nombre,
          filas: [],
        };
        secciones.push(seccion);
      }
      for (const p of leida.platillos ?? []) {
        const k = clave(p.nombre);
        if (k && vistos.has(k)) continue;
        if (k) vistos.add(k);
        seccion.filas.push({
          id: this.siguienteId++,
          // Una fila dudosa se mira antes de marcarla: el nombre o el precio pueden estar mal.
          marcada: !(p.dudoso ?? false),
          nombre: p.nombre ?? '',
          precio: p.precio ?? null,
          descripcion: p.descripcion ?? '',
          vegetariano: p.vegetariano ?? false,
          dudosa: p.dudoso ?? false,
          recorte: p.recorte ?? null,
        });
      }
    }
    this.secciones.set(secciones);

    const adicionales = [...this.adicionales()];
    for (const c of lectura.complementos ?? []) {
      const k = clave(c.nombre);
      if (k && adicionales.some((a) => clave(a.nombre) === k)) continue;
      adicionales.push({
        id: this.siguienteId++,
        marcada: !(c.dudoso ?? false),
        nombre: c.nombre ?? '',
        precio: c.precio ?? null,
        // Los dos enums generados son la misma lista; la fila trabaja con la de importar.
        tipo: (c.tipo ??
          ComplementoImportacionDtoTipoEnum.OTROS) as ComplementoImportacionDtoTipoEnum,
        dudosa: c.dudoso ?? false,
        recorte: c.recorte ?? null,
      });
    }
    this.adicionales.set(adicionales);
  }

  protected estadoDe(fila: FilaRevision): Estado {
    const existente = this.existentes().get(clave(fila.nombre));
    if (!existente) return { tipo: 'nuevo', antes: null };
    const antes = existente.precioVentaBase ?? 0;
    if (fila.precio !== null && antes !== fila.precio) return { tipo: 'precio', antes };
    const descripcion = fila.descripcion.trim();
    if (descripcion && descripcion !== (existente.descripcion ?? '')) {
      return { tipo: 'descripcion', antes };
    }
    return { tipo: 'igual', antes };
  }

  protected cambiarSeccion(seccion: SeccionRevision, valor: string): void {
    this.editarSeccion(seccion, (s) => ({
      ...s,
      categoriaId: valor === '' ? null : Number(valor),
      nombreNueva: valor === '' ? s.nombreNueva || s.leida : '',
    }));
  }

  protected anotarSeccionNueva(seccion: SeccionRevision, valor: string): void {
    this.editarSeccion(seccion, (s) => ({ ...s, nombreNueva: valor }));
  }

  protected marcarSeccion(seccion: SeccionRevision, marcada: boolean): void {
    this.editarSeccion(seccion, (s) => ({
      ...s,
      filas: s.filas.map((f) => ({ ...f, marcada })),
    }));
  }

  protected todasMarcadas(seccion: SeccionRevision): boolean {
    return seccion.filas.length > 0 && seccion.filas.every((f) => f.marcada);
  }

  protected marcarFila(seccion: SeccionRevision, fila: FilaRevision, marcada: boolean): void {
    this.editarFila(seccion, fila, (f) => ({ ...f, marcada }));
  }

  protected anotarNombre(seccion: SeccionRevision, fila: FilaRevision, valor: string): void {
    this.editarFila(seccion, fila, (f) => ({ ...f, nombre: valor }));
  }

  protected anotarPrecio(seccion: SeccionRevision, fila: FilaRevision, valor: string): void {
    const n = Number.parseFloat(valor);
    this.editarFila(seccion, fila, (f) => ({ ...f, precio: Number.isFinite(n) ? n : null }));
  }

  protected anotarDescripcion(seccion: SeccionRevision, fila: FilaRevision, valor: string): void {
    this.editarFila(seccion, fila, (f) => ({ ...f, descripcion: valor }));
  }

  protected marcarVegetariano(
    seccion: SeccionRevision,
    fila: FilaRevision,
    vegetariano: boolean,
  ): void {
    this.editarFila(seccion, fila, (f) => ({ ...f, vegetariano }));
  }

  /** La foto se deja partes: una fila en blanco para escribir lo que falta. */
  protected agregarFila(seccion: SeccionRevision): void {
    this.editarSeccion(seccion, (s) => ({
      ...s,
      filas: [
        ...s.filas,
        {
          id: this.siguienteId++,
          marcada: true,
          nombre: '',
          precio: null,
          descripcion: '',
          vegetariano: false,
          dudosa: false,
          recorte: null,
        },
      ],
    }));
  }

  protected quitarFila(seccion: SeccionRevision, fila: FilaRevision): void {
    this.editarSeccion(seccion, (s) => ({ ...s, filas: s.filas.filter((f) => f.id !== fila.id) }));
  }

  private editarSeccion(
    seccion: SeccionRevision,
    cambio: (s: SeccionRevision) => SeccionRevision,
  ): void {
    this.secciones.update((ss) => ss.map((s) => (s.id === seccion.id ? cambio(s) : s)));
  }

  private editarFila(
    seccion: SeccionRevision,
    fila: FilaRevision,
    cambio: (f: FilaRevision) => FilaRevision,
  ): void {
    this.editarSeccion(seccion, (s) => ({
      ...s,
      filas: s.filas.map((f) => (f.id === fila.id ? cambio(f) : f)),
    }));
  }

  // --- adicionales ----------------------------------------------------------

  protected marcarAdicional(fila: FilaAdicional, marcada: boolean): void {
    this.editarAdicional(fila, (a) => ({ ...a, marcada }));
  }

  protected anotarNombreAdicional(fila: FilaAdicional, valor: string): void {
    this.editarAdicional(fila, (a) => ({ ...a, nombre: valor }));
  }

  protected anotarPrecioAdicional(fila: FilaAdicional, valor: string): void {
    const n = Number.parseFloat(valor);
    this.editarAdicional(fila, (a) => ({ ...a, precio: Number.isFinite(n) ? n : null }));
  }

  protected elegirTipoAdicional(fila: FilaAdicional, valor: string): void {
    this.editarAdicional(fila, (a) => ({ ...a, tipo: valor as ComplementoImportacionDtoTipoEnum }));
  }

  protected quitarAdicional(fila: FilaAdicional): void {
    this.adicionales.update((as) => as.filter((a) => a.id !== fila.id));
  }

  protected agregarAdicional(): void {
    this.adicionales.update((as) => [
      ...as,
      {
        id: this.siguienteId++,
        marcada: true,
        nombre: '',
        precio: null,
        tipo: ComplementoImportacionDtoTipoEnum.OTROS,
        dudosa: false,
        recorte: null,
      },
    ]);
  }

  private editarAdicional(fila: FilaAdicional, cambio: (a: FilaAdicional) => FilaAdicional): void {
    this.adicionales.update((as) => as.map((a) => (a.id === fila.id ? cambio(a) : a)));
  }

  // --- importacion ----------------------------------------------------------

  protected importar(): void {
    if (this.importando() || this.leyendo() > 0) return;

    const secciones: SeccionImportacionDto[] = [];
    for (const s of this.secciones()) {
      const filas = s.filas.filter((f) => f.marcada);
      if (filas.length === 0) continue;
      if (s.categoriaId === null && !s.nombreNueva.trim()) {
        this.avisos.error(this.t('lectura.faltaSeccion'));
        return;
      }
      if (filas.some((f) => !f.nombre.trim() || f.precio === null)) {
        this.avisos.error(this.t('lectura.faltaNombre'));
        return;
      }
      secciones.push({
        categoriaId: s.categoriaId ?? undefined,
        nombreNueva: s.categoriaId === null ? s.nombreNueva.trim() : undefined,
        platillos: filas.map((f) => ({
          nombre: f.nombre.trim(),
          precio: f.precio as number,
          descripcion: f.descripcion.trim() || undefined,
          vegetariano: f.vegetariano,
        })),
      });
    }

    const marcados = this.adicionales().filter((a) => a.marcada);
    if (marcados.some((a) => !a.nombre.trim() || a.precio === null)) {
      this.avisos.error(this.t('lectura.faltaNombre'));
      return;
    }
    const complementos: ComplementoImportacionDto[] = marcados.map((a) => ({
      nombre: a.nombre.trim(),
      precio: a.precio as number,
      tipo: a.tipo,
    }));

    if (secciones.length === 0 && complementos.length === 0) {
      this.avisos.error(this.t('lectura.nadaMarcado'));
      return;
    }

    this.importando.set(true);
    this.lecturaApi.importarCarta({ importacionCartaDto: { secciones, complementos } }).subscribe({
      next: (r) => {
        this.importando.set(false);
        this.avisos.exito(
          this.t('lectura.importado', {
            creados: r.platillosCreados ?? 0,
            actualizados: r.platillosActualizados ?? 0,
            seccionesCreadas: r.seccionesCreadas ?? 0,
          }),
        );
        this.empezarDeNuevo();
        this.ngOnInit();
      },
      error: () => this.importando.set(false),
    });
  }

  protected empezarDeNuevo(): void {
    this.fotos.set([]);
    this.secciones.set([]);
    this.adicionales.set([]);
  }
}
