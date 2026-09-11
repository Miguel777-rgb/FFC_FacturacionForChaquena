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
  CajaArqueoYFraudeApi,
  CajaPagosApi,
  CambioEstadoRequestDtoEstadoEnum,
  ComandasApi,
  FeedbackYFidelizacionApi,
  OrdenResponseDtoEstadoEnum,
  OrdenResumenDtoEstadoEnum,
  PagoResponseDtoEstadoEnum,
  RegistrarPagoRequestDtoTipoPagoEnum,
  ReportesApi,
  type ArqueoCajaDto,
  type FeedbackResponseDto,
  type OrdenResponseDto,
  type OrdenResumenDto,
  type PagoResponseDto,
  type ProductoTopDto,
  type ReporteVentasDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

const METODOS = RegistrarPagoRequestDtoTipoPagoEnum;

/**
 * Caja: cobrar la comanda entregada, acreditar lo que no es efectivo y cerrar
 * el ciclo.
 *
 * Los tres metodos de pago se comportan distinto y la pantalla lo refleja. El
 * efectivo se confirma solo y devuelve vuelto. La tarjeta y la billetera nacen
 * en PENDIENTE —el dinero todavia no esta— y hay que acreditarlas desde la
 * bandeja; hasta entonces no cuentan para el total de la comanda.
 *
 * La comanda **no** se marca pagada a mano. El backend la pasa a PAGADO solo
 * cuando lo confirmado alcanza el total, asi que se registra el cobro y se
 * vuelve a leer la comanda para ver que decidio el servidor.
 */
@Component({
  selector: 'app-caja',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './caja.page.html',
  styleUrl: './caja.page.scss',
})
export class CajaPage implements OnInit {
  private readonly comandasApi = inject(ComandasApi);
  private readonly pagosApi = inject(CajaPagosApi);
  private readonly cajaApi = inject(CajaArqueoYFraudeApi);
  private readonly reportesApi = inject(ReportesApi);
  private readonly fidelizacionApi = inject(FeedbackYFidelizacionApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  /**
   * Los tres criterios que se califican. Va la clave, no el rotulo: la
   * plantilla lo traduce, asi que cambiar de idioma con la calificacion a
   * medias no deja tres estrellas rotuladas en espanol.
   */
  protected readonly CRITERIOS = [
    { id: 'comida' as const, nombre: 'caja.comida' as const },
    { id: 'atencion' as const, nombre: 'caja.atencion' as const },
    { id: 'lugar' as const, nombre: 'caja.lugar' as const },
  ];

  protected readonly ESTRELLAS = [1, 2, 3, 4, 5];

  protected readonly METODOS = METODOS;
  protected readonly ORDEN_METODOS = [METODOS.EFECTIVO, METODOS.TARJETA, METODOS.E_WALLET];

  protected readonly cargando = signal(true);
  protected readonly cobrando = signal(false);

  protected readonly enSalon = signal<OrdenResumenDto[]>([]);
  protected readonly arqueo = signal<ArqueoCajaDto | null>(null);
  protected readonly pendientes = signal<PagoResponseDto[]>([]);
  protected readonly ventas = signal<ReporteVentasDto | null>(null);
  protected readonly top = signal<ProductoTopDto[]>([]);

  /**
   * La comanda que se esta cobrando. Se guarda entera y no como id porque en
   * cuanto se paga desaparece de `GET /ordenes/activas` —esa lista llega hasta
   * ENTREGADO—, y aun hace falta tenerla delante para cerrarla.
   */
  protected readonly seleccionada = signal<OrdenResponseDto | null>(null);
  protected readonly pagosDeLaCuenta = signal<PagoResponseDto[]>([]);

  // --- el cobro que se esta escribiendo -------------------------------------

  protected readonly metodo = signal<RegistrarPagoRequestDtoTipoPagoEnum>(METODOS.EFECTIVO);
  protected readonly monto = signal<number | null>(null);
  protected readonly entregado = signal<number | null>(null);
  protected readonly referencia = signal('');
  protected readonly ultimoVuelto = signal<number | null>(null);

  // --- calificacion de la comanda -------------------------------------------

  protected readonly calificando = signal(false);
  protected readonly puntajes = signal<Record<'comida' | 'atencion' | 'lugar', number>>({
    comida: 0,
    atencion: 0,
    lugar: 0,
  });
  protected readonly comentario = signal('');

  /**
   * Lo que el servidor respondio a la ultima calificacion. Trae el mensaje de
   * fidelizacion y, cuando toca, el cupon recien emitido: es el unico momento
   * en que ese codigo aparece, y hay que dictarselo al comensal antes de que se
   * levante de la mesa.
   */
  protected readonly calificacion = signal<FeedbackResponseDto | null>(null);

  /** Alerta de billete falso: motivo escrito y si se bloquea al cliente. */
  protected readonly alertando = signal(false);
  protected readonly motivoFraude = signal('');
  protected readonly bloquearCliente = signal(false);

  // --- derivados ------------------------------------------------------------

  protected readonly esEfectivo = computed(() => this.metodo() === METODOS.EFECTIVO);

  /**
   * Lo que ya esta acreditado. Solo cuentan los CONFIRMADO: un pago con tarjeta
   * pendiente es una promesa, y sumarlo dejaria una cuenta que parece saldada
   * con dinero que todavia no llego.
   */
  protected readonly confirmado = computed(() =>
    this.pagosDeLaCuenta()
      .filter((p) => p.estado === PagoResponseDtoEstadoEnum.CONFIRMADO)
      .reduce((suma, p) => suma + (p.monto ?? 0), 0),
  );

  protected readonly enEspera = computed(() =>
    this.pagosDeLaCuenta()
      .filter((p) => p.estado === PagoResponseDtoEstadoEnum.PENDIENTE)
      .reduce((suma, p) => suma + (p.monto ?? 0), 0),
  );

  /** Lo que falta por cobrar de esta cuenta. Es lo que propone el formulario. */
  protected readonly porCobrar = computed(() =>
    Math.max(0, (this.seleccionada()?.montoTotal ?? 0) - this.confirmado()),
  );

  protected readonly vuelto = computed(() => {
    const puesto = this.entregado();
    const importe = this.monto();
    if (!this.esEfectivo() || puesto === null || importe === null) return null;
    return puesto - importe;
  });

  protected readonly puedeCobrar = computed(() => {
    const orden = this.seleccionada();
    const importe = this.monto();

    if (orden?.estado !== OrdenResponseDtoEstadoEnum.ENTREGADO) return false;
    if (this.cobrando() || importe === null || importe <= 0) return false;
    // El efectivo necesita saber cuanto puso el comensal, para el vuelto.
    if (this.esEfectivo()) {
      const cambio = this.vuelto();
      return cambio !== null && cambio >= 0;
    }
    return true;
  });

  protected readonly puedeCerrar = computed(
    () => this.seleccionada()?.estado === OrdenResponseDtoEstadoEnum.PAGADO && !this.cobrando(),
  );

  /**
   * Se califica lo ya cobrado y antes de cerrar: pedirlo antes seria calificar
   * una comida a medias, y despues de cerrar el comensal ya se fue.
   */
  protected readonly sePuedeCalificar = computed(
    () =>
      this.seleccionada()?.estado === OrdenResponseDtoEstadoEnum.PAGADO &&
      this.calificacion() === null,
  );

  protected readonly puntajesCompletos = computed(() =>
    Object.values(this.puntajes()).every((v) => v > 0),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    const hoy = new Date().toISOString().slice(0, 10);

    forkJoin({
      salon: this.comandasApi.activas(),
      arqueo: this.cajaApi.arqueo({}),
      pendientes: this.cajaApi.pendientes().pipe(catchError(() => of([] as PagoResponseDto[]))),
      ventas: this.reportesApi
        .ventas({ desde: `${hoy}T00:00:00Z` })
        .pipe(catchError(() => of(null))),
      top: this.reportesApi
        .productosTop({ desde: `${hoy}T00:00:00Z`, limite: 5 })
        .pipe(catchError(() => of([] as ProductoTopDto[]))),
    }).subscribe({
      next: ({ salon, arqueo, pendientes, ventas, top }) => {
        this.enSalon.set(salon);
        this.arqueo.set(arqueo);
        this.pendientes.set(pendientes);
        this.ventas.set(ventas);
        this.top.set(top);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  // --- abrir y cerrar la cuenta ---------------------------------------------

  /**
   * Abre la cuenta. Se pide el detalle completo en vez de reutilizar el resumen
   * de la lista: el resumen no trae las lineas, y una cuenta sin lineas no se
   * puede repasar con el comensal delante.
   */
  protected seleccionar(orden: OrdenResumenDto): void {
    if (!orden.id) return;

    this.limpiarCobro();
    this.cargarCuenta(orden.id);
  }

  private cargarCuenta(id: string): void {
    forkJoin({
      orden: this.comandasApi.obtenerOrden({ id }),
      pagos: this.pagosApi
        .listarPagosDeOrden({ ordenId: id })
        .pipe(catchError(() => of([] as PagoResponseDto[]))),
    }).subscribe({
      next: ({ orden, pagos }) => {
        this.seleccionada.set(orden);
        this.pagosDeLaCuenta.set(pagos);
        this.cobrando.set(false);
        // Se propone cobrar lo que falta: el caso comun es una sola forma de
        // pago por el importe entero, y el pago dividido es la excepcion.
        this.monto.set(this.porCobrar() || null);
      },
      error: () => this.cobrando.set(false),
    });
  }

  protected cerrarCuenta(): void {
    this.seleccionada.set(null);
    this.pagosDeLaCuenta.set([]);
    this.limpiarCobro();
  }

  // --- escribir el cobro ----------------------------------------------------

  protected elegirMetodo(m: RegistrarPagoRequestDtoTipoPagoEnum): void {
    this.metodo.set(m);
    this.entregado.set(null);
    this.referencia.set('');
    this.ultimoVuelto.set(null);
  }

  protected anotarMonto(valor: string): void {
    const numero = Number.parseFloat(valor);
    this.monto.set(Number.isFinite(numero) ? numero : null);
  }

  protected anotarEntregado(valor: string): void {
    const numero = Number.parseFloat(valor);
    this.entregado.set(Number.isFinite(numero) ? numero : null);
  }

  protected anotarReferencia(valor: string): void {
    this.referencia.set(valor);
  }

  /** Deja el importe exacto: el caso mas comun cuando se paga justo. */
  protected importeExacto(): void {
    this.entregado.set(this.monto());
  }

  /** Cobrar solo una parte, para dividir la cuenta entre varios comensales. */
  protected cobrarMitad(): void {
    this.monto.set(Math.round((this.porCobrar() / 2) * 100) / 100);
    this.entregado.set(null);
  }

  // --- cobro, acreditacion y cierre -----------------------------------------

  protected cobrar(): void {
    const orden = this.seleccionada();
    const importe = this.monto();
    if (!orden?.id || importe === null || !this.puedeCobrar()) return;

    this.cobrando.set(true);

    this.pagosApi
      .registrarPagoDeOrden({
        ordenId: orden.id,
        registrarPagoRequestDto: {
          tipoPago: this.metodo(),
          monto: importe,
          montoEntregado: this.esEfectivo() ? (this.entregado() ?? undefined) : undefined,
          referencia: this.esEfectivo() ? undefined : this.referencia().trim() || undefined,
        },
      })
      .subscribe({
        next: (pago) => {
          if (pago.estado === PagoResponseDtoEstadoEnum.PENDIENTE) {
            this.avisos.info(
              this.t('caja.avisoPagoPendiente', {
                metodo: this.tEnum('metodo', this.metodo()),
                monto: importe.toFixed(2),
              }),
            );
          } else {
            this.ultimoVuelto.set(pago.vuelto ?? 0);
            this.avisos.exito(
              this.t('caja.avisoCobrado', { vuelto: (pago.vuelto ?? 0).toFixed(2) }),
            );
          }
          // Quien decide si la comanda quedo PAGADA es el servidor, al comprobar
          // que lo confirmado cubre el total. Se relee para verlo, no se supone.
          this.cargarCuenta(orden.id!);
          this.cargar();
        },
        error: () => this.cobrando.set(false),
      });
  }

  /**
   * Acredita un cobro con billetera o tarjeta. Es el momento en que el dinero
   * pasa a contar: si con eso se cubre el total, el servidor deja la comanda en
   * PAGADO por su cuenta.
   */
  protected acreditar(pago: PagoResponseDto): void {
    if (!pago.id || this.cobrando()) return;

    this.cobrando.set(true);
    this.cajaApi.confirmar({ pagoId: pago.id }).subscribe({
      next: () => {
        this.avisos.exito(this.t('caja.avisoAcreditado', { monto: (pago.monto ?? 0).toFixed(2) }));
        const abierta = this.seleccionada();
        if (abierta?.id) this.cargarCuenta(abierta.id);
        else this.cobrando.set(false);
        this.cargar();
      },
      error: () => this.cobrando.set(false),
    });
  }

  /** Sella `tiempoFinGlobal` y libera la mesa. Aqui termina el ciclo. */
  protected cerrarCiclo(): void {
    const orden = this.seleccionada();
    if (!orden?.id || !this.puedeCerrar()) return;

    this.cobrando.set(true);

    this.comandasApi
      .cambiarEstadoOrden({
        id: orden.id,
        cambioEstadoRequestDto: { estado: CambioEstadoRequestDtoEstadoEnum.CONCLUIDO },
      })
      .subscribe({
        next: () => {
          this.cobrando.set(false);
          this.avisos.exito(this.t('caja.avisoCerrada', { mesa: orden.mesaNumero ?? '—' }));
          this.cerrarCuenta();
          this.cargar();
        },
        error: () => this.cobrando.set(false),
      });
  }

  // --- calificacion de la comanda -------------------------------------------

  protected abrirCalificacion(): void {
    this.calificando.update((v) => !v);
    this.puntajes.set({ comida: 0, atencion: 0, lugar: 0 });
    this.comentario.set('');
  }

  protected puntuar(criterio: 'comida' | 'atencion' | 'lugar', estrellas: number): void {
    this.puntajes.update((p) => ({ ...p, [criterio]: estrellas }));
  }

  protected anotarComentario(valor: string): void {
    this.comentario.set(valor);
  }

  /**
   * Registra la calificacion. La regla de las N calificaciones vive en el
   * servidor —el umbral lo fija administracion en la trastienda—, asi que aqui
   * no se cuenta nada: se manda y se lee lo que responde.
   *
   * Se califica una sola vez por comanda; un segundo intento devuelve 409 y el
   * interceptor ya lo explica.
   */
  protected calificar(): void {
    const orden = this.seleccionada();
    const puntajes = this.puntajes();
    if (!orden?.id || !this.puntajesCompletos() || this.cobrando()) return;

    this.cobrando.set(true);
    this.fidelizacionApi
      .registrarFeedbackDeOrden({
        ordenId: orden.id,
        feedbackRequestDto: {
          puntajeComida: puntajes.comida,
          puntajeAtencion: puntajes.atencion,
          puntajeLugar: puntajes.lugar,
          comentario: this.comentario().trim() || undefined,
        },
      })
      .subscribe({
        next: (respuesta) => {
          this.cobrando.set(false);
          this.calificando.set(false);
          this.calificacion.set(respuesta);
          // El texto lo escribe el servidor: sabe cuantas calificaciones lleva
          // el cliente y cuantas le faltan. Reescribirlo aqui seria adivinarlo.
          if (respuesta.mensajeFidelizacion) {
            this.avisos.exito(respuesta.mensajeFidelizacion);
          }
        },
        error: () => this.cobrando.set(false),
      });
  }

  // --- alerta de billete falso ----------------------------------------------

  protected abrirAlerta(): void {
    this.alertando.update((v) => !v);
    this.motivoFraude.set('');
    this.bloquearCliente.set(false);
  }

  protected anotarMotivoFraude(valor: string): void {
    this.motivoFraude.set(valor);
  }

  protected alternarBloqueo(): void {
    this.bloquearCliente.update((v) => !v);
  }

  /**
   * Marca la comanda como fraudulenta. Es un estado terminal —desde
   * FRAUDULENTO no se sale— y por eso el gesto pide un motivo escrito: lo que
   * quede aqui es lo unico que administracion va a leer despues.
   */
  protected alertarFraude(): void {
    const orden = this.seleccionada();
    const motivo = this.motivoFraude().trim();
    if (!orden?.id || motivo.length === 0 || this.cobrando()) return;

    this.cobrando.set(true);
    this.cajaApi
      .alertaFraude({
        ordenId: orden.id,
        alertaFraudeRequestDto: { motivo, bloquearCliente: this.bloquearCliente() },
      })
      .subscribe({
        next: () => {
          this.alertando.set(false);
          this.avisos.info(this.t('caja.avisoFraude'));
          this.cerrarCuenta();
          this.cargar();
        },
        error: () => this.cobrando.set(false),
      });
  }

  // --- utilidades de plantilla ----------------------------------------------

  private limpiarCobro(): void {
    this.metodo.set(METODOS.EFECTIVO);
    this.monto.set(null);
    this.entregado.set(null);
    this.referencia.set('');
    this.ultimoVuelto.set(null);
    this.alertando.set(false);
    this.calificando.set(false);
    this.calificacion.set(null);
    this.puntajes.set({ comida: 0, atencion: 0, lugar: 0 });
    this.comentario.set('');
  }

  /** Solo se cobra lo entregado: antes de eso la comida no ha llegado a la mesa. */
  protected seCobra(orden: OrdenResumenDto): boolean {
    return orden.estado === OrdenResumenDtoEstadoEnum.ENTREGADO;
  }
}
