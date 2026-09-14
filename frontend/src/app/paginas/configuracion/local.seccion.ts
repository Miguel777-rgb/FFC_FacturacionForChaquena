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
  ClientesEmpresasApi,
  FeedbackYFidelizacionApi,
  type ConfiguracionLocalDto,
  type EmpresaResponseDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/**
 * Local: los numeros que gobiernan el resto del sistema.
 *
 * Los cuatro parametros no son preferencias de pantalla: el umbral decide
 * cuando la caja emite un cupon, el descuento decide cuanto vale, la vigencia
 * cuando caduca y el objetivo de cocina es la vara contra la que el KDS mide
 * sus tiempos. Cambiar uno cambia lo que hacen tres superficies.
 *
 * El reporte de satisfaccion, que antes vivia aqui, esta en Ventas y reportes:
 * es una cifra que se compara por periodo, no un ajuste.
 */
@Component({
  selector: 'app-local-seccion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './local.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class LocalSeccion implements OnInit {
  private readonly fidelizacionApi = inject(FeedbackYFidelizacionApi);
  private readonly empresasApi = inject(ClientesEmpresasApi);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  // --- parametros del local -------------------------------------------------

  protected readonly calificaciones = signal<number | null>(null);
  protected readonly descuento = signal<number | null>(null);
  protected readonly vigencia = signal<number | null>(null);
  protected readonly objetivoCocina = signal<number | null>(null);

  /** Lo que habia al cargar, para saber si de verdad se cambio algo. */
  private readonly original = signal<ConfiguracionLocalDto | null>(null);

  // --- empresas -------------------------------------------------------------

  protected readonly empresas = signal<EmpresaResponseDto[]>([]);
  protected readonly altaAbierta = signal(false);
  protected readonly ruc = signal('');
  protected readonly razonSocial = signal('');
  protected readonly direccionFiscal = signal('');
  protected readonly celularEmpresa = signal('');

  protected readonly hayCambios = computed(() => {
    const antes = this.original();
    if (!antes) return false;
    return (
      this.calificaciones() !== (antes.calificacionesParaCupon ?? null) ||
      this.descuento() !== (antes.porcentajeDescuentoCupon ?? null) ||
      this.vigencia() !== (antes.diasVigenciaCupon ?? null) ||
      this.objetivoCocina() !== (antes.minutosObjetivoCocina ?? null)
    );
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    forkJoin({
      configuracion: this.fidelizacionApi.configuracion(),
      empresas: this.empresasApi
        .listarEmpresas({ pageable: { page: 0, size: 50, sort: ['razonSocial,asc'] } })
        .pipe(catchError(() => of({ contenido: [] as EmpresaResponseDto[] }))),
    }).subscribe({
      next: ({ configuracion, empresas }) => {
        this.aplicar(configuracion);
        this.empresas.set(empresas.contenido ?? []);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private aplicar(config: ConfiguracionLocalDto): void {
    this.original.set(config);
    this.calificaciones.set(config.calificacionesParaCupon ?? null);
    this.descuento.set(config.porcentajeDescuentoCupon ?? null);
    this.vigencia.set(config.diasVigenciaCupon ?? null);
    this.objetivoCocina.set(config.minutosObjetivoCocina ?? null);
  }

  // --- parametros -----------------------------------------------------------

  protected anotarCalificaciones(v: string): void {
    this.calificaciones.set(this.entero(v));
  }

  protected anotarDescuento(v: string): void {
    this.descuento.set(this.entero(v));
  }

  protected anotarVigencia(v: string): void {
    this.vigencia.set(this.entero(v));
  }

  protected anotarObjetivoCocina(v: string): void {
    this.objetivoCocina.set(this.entero(v));
  }

  /**
   * Se manda la configuracion entera: el `PUT` reemplaza, no parchea. Por eso
   * los cuatro campos viajan siempre, incluso los que nadie toco.
   */
  protected guardar(): void {
    if (!this.hayCambios() || this.guardando()) return;

    this.guardando.set(true);
    this.fidelizacionApi
      .actualizarConfiguracion({
        configuracionLocalDto: {
          calificacionesParaCupon: this.calificaciones() ?? undefined,
          porcentajeDescuentoCupon: this.descuento() ?? undefined,
          diasVigenciaCupon: this.vigencia() ?? undefined,
          minutosObjetivoCocina: this.objetivoCocina() ?? undefined,
        },
      })
      .subscribe({
        next: (guardada) => {
          this.guardando.set(false);
          this.aplicar(guardada);
          this.avisos.exito(this.t('local.avisoGuardado'));
        },
        error: () => this.guardando.set(false),
      });
  }

  protected descartar(): void {
    const antes = this.original();
    if (antes) this.aplicar(antes);
  }

  // --- empresas -------------------------------------------------------------

  protected abrirAlta(): void {
    this.altaAbierta.update((v) => !v);
    this.ruc.set('');
    this.razonSocial.set('');
    this.direccionFiscal.set('');
    this.celularEmpresa.set('');
  }

  protected anotarRuc(v: string): void {
    this.ruc.set(v.replace(/\D/g, '').slice(0, 11));
  }

  protected anotarRazonSocial(v: string): void {
    this.razonSocial.set(v);
  }

  protected anotarDireccionFiscal(v: string): void {
    this.direccionFiscal.set(v);
  }

  protected anotarCelularEmpresa(v: string): void {
    this.celularEmpresa.set(v);
  }

  /**
   * El registro de empresas existe para el dia que haya facturacion: es el RUC
   * y la razon social que iran en la factura. Hoy solo se guarda; ningun
   * comprobante sale todavia de aqui.
   */
  protected altaEmpresa(): void {
    const ruc = this.ruc().trim();
    const razonSocial = this.razonSocial().trim();
    if (ruc.length !== 11 || razonSocial.length === 0 || this.guardando()) {
      this.avisos.info(this.t('local.avisoRuc'));
      return;
    }

    this.guardando.set(true);
    this.empresasApi
      .crearEmpresa({
        empresaRequestDto: {
          ruc,
          razonSocial,
          direccionFiscal: this.direccionFiscal().trim() || undefined,
          celular: this.celularEmpresa().trim() || undefined,
        },
      })
      .subscribe({
        next: (e) => {
          this.guardando.set(false);
          this.altaAbierta.set(false);
          this.avisos.exito(
            this.t('local.avisoEmpresa', { empresa: e.razonSocial ?? '', ruc: e.ruc ?? '' }),
          );
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }

  private entero(v: string): number | null {
    const n = Number.parseInt(v, 10);
    return Number.isFinite(n) ? n : null;
  }
}
