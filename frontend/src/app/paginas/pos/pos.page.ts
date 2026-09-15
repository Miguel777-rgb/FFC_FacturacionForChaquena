import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { Icono } from '../../disenio/icono';
import { formatearDuracion } from '../../nucleo/i18n/formatos';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  CatalogoComplementosApi,
  CatalogoPlatillosApi,
  CatalogoPromocionesApi,
  ClientesApi,
  FeedbackYFidelizacionApi,
  CambioEstadoRequestDtoEstadoEnum,
  ComandasApi,
  CrearOrdenRequestDtoCanalOrigenEnum,
  CrearOrdenRequestDtoTipoOrdenEnum,
  CrearOrdenRequestDtoTipoPagoEnum,
  MesaResponseDtoEstadoEnum,
  OrdenResumenDtoTransicionesPermitidasEnum,
  SalonMesasApi,
  type ClienteResponseDto,
  type ComplementoResponseDto,
  type CuponResponseDto,
  type FidelizacionDto,
  type MesaResponseDto,
  type OrdenDetalleDto,
  type OrdenResponseDto,
  type OrdenResumenDto,
  type PlatilloDisponibleDto,
  type PromocionResponseDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { urlDeArchivo } from '../../nucleo/marca/archivos';

/** Un complemento elegido para una linea, con su propia cantidad. */
interface ComplementoElegido {
  complemento: ComplementoResponseDto;
  cantidad: number;
}

/**
 * Una linea de la comanda mientras se arma en la pantalla. Guarda el platillo
 * entero, no solo su id, porque el precio y el nombre se pintan aqui sin volver
 * a preguntar al servidor. El total que sale de esto es una estimacion para el
 * mozo: el importe que vale es el que devuelve `POST /ordenes`, calculado con
 * los precios que el servidor tenga en ese instante.
 */
interface LineaComanda {
  platillo: PlatilloDisponibleDto;
  cantidad: number;
  nota: string;
  complementos: ComplementoElegido[];
}

/** Lo que hay que dictarle al cliente cuando la comanda sale a domicilio. */
interface EntregaPendiente {
  correlativo: string;
  otp: string;
  direccion: string;
}

const TIPOS = CrearOrdenRequestDtoTipoOrdenEnum;

/**
 * Punto de venta: el paso donde nace la comanda y el paso donde el mozo la
 * entrega en la mesa.
 *
 * Cubre las tres formas de pedir —mesa, retiro en local y delivery—, los
 * complementos por linea, la promocion vigente, el cupon del cliente
 * identificado y la cancelacion. Lo que la pantalla puede ofrecer sobre una
 * comanda ya enviada lo decide `transicionesPermitidas`, que viene en la
 * respuesta: la tabla de transiciones vive en el servidor y copiarla aqui la
 * desincronizaria en la primera regla nueva.
 */
@Component({
  selector: 'app-pos',
  imports: [DecimalPipe, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pos.page.html',
  styleUrl: './pos.page.scss',
})
export class PosPage implements OnInit {
  private readonly mesasApi = inject(SalonMesasApi);
  private readonly platillosApi = inject(CatalogoPlatillosApi);
  private readonly complementosApi = inject(CatalogoComplementosApi);
  private readonly promocionesApi = inject(CatalogoPromocionesApi);
  private readonly clientesApi = inject(ClientesApi);
  private readonly fidelizacionApi = inject(FeedbackYFidelizacionApi);
  private readonly comandasApi = inject(ComandasApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);
  private readonly confirmacion = inject(ConfirmacionService);
  protected readonly duracion = formatearDuracion;

  /** La plantilla los llama directamente; leen la senal del idioma al hacerlo. */
  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly urlDeArchivo = urlDeArchivo;

  /** El enum del contrato, para que la plantilla no escriba las cadenas a mano. */
  protected readonly TIPOS = TIPOS;

  protected readonly cargando = signal(true);
  protected readonly enviando = signal(false);

  protected readonly mesas = signal<MesaResponseDto[]>([]);
  protected readonly carta = signal<PlatilloDisponibleDto[]>([]);
  protected readonly enSalon = signal<OrdenResumenDto[]>([]);
  protected readonly complementos = signal<ComplementoResponseDto[]>([]);
  protected readonly promociones = signal<PromocionResponseDto[]>([]);

  // --- destino de la comanda ------------------------------------------------

  protected readonly tipoOrden = signal<CrearOrdenRequestDtoTipoOrdenEnum>(TIPOS.MESA);
  protected readonly mesaElegida = signal<MesaResponseDto | null>(null);
  protected readonly direccion = signal('');

  // --- cliente --------------------------------------------------------------

  protected readonly cliente = signal<ClienteResponseDto | null>(null);
  protected readonly busqueda = signal('');
  protected readonly resultados = signal<ClienteResponseDto[]>([]);
  protected readonly buscandoCliente = signal(false);

  // --- descuentos -----------------------------------------------------------

  protected readonly promocionId = signal<string | null>(null);
  protected readonly cupon = signal('');

  /**
   * Los cupones vigentes del cliente identificado. Se piden al elegirlo porque
   * el codigo se lo dictaron en la visita anterior y nadie se lo aprende: sin
   * esta lista el mozo tiene que teclear a ciegas algo que el sistema ya sabe.
   */
  protected readonly cupones = signal<CuponResponseDto[]>([]);

  /**
   * El nivel de lealtad del cliente identificado, para que el mozo pueda
   * decirlo. El descuento no se estima aqui: lo aplica el servidor al crear la
   * comanda, y solo si el cupon no rebaja mas.
   */
  protected readonly fidelizacion = signal<FidelizacionDto | null>(null);

  // --- la comanda en construccion -------------------------------------------

  protected readonly lineas = signal<LineaComanda[]>([]);

  /** Linea cuyo cajon de complementos esta abierto. Null = cerrado. */
  protected readonly lineaEnComplementos = signal<LineaComanda | null>(null);

  /** Comanda cuya cancelacion se esta escribiendo, y el motivo. */
  protected readonly cancelando = signal<string | null>(null);
  protected readonly motivo = signal('');

  /**
   * Comanda ya enviada que se esta corrigiendo. Solo se abre si el servidor la
   * declara `editable`, que hoy significa ENCOLADO: en cuanto cocina la toma,
   * cambiarla por debajo seria cambiar un plato que ya esta en la sarten.
   */
  protected readonly editando = signal<OrdenResponseDto | null>(null);
  protected readonly guardandoLinea = signal(false);
  protected readonly platilloParaAgregar = signal('');

  /**
   * El OTP de la ultima comanda a domicilio. Solo viaja en la respuesta de
   * `POST /ordenes` —las lecturas posteriores lo omiten para que no quede a la
   * vista del repartidor—, asi que si no se muestra aqui, nadie del local puede
   * volver a leerlo. Se dicta al cliente y se cierra a mano.
   */
  protected readonly entregaPendiente = signal<EntregaPendiente | null>(null);

  // --- derivados ------------------------------------------------------------

  protected readonly subtotal = computed(() =>
    this.lineas().reduce((suma, l) => suma + this.precioDeLinea(l), 0),
  );

  protected readonly promocionElegida = computed(
    () => this.promociones().find((p) => p.id === this.promocionId()) ?? null,
  );

  /**
   * Descuento estimado de la promocion. El cupon no entra: su validez y su
   * importe los decide el servidor —tiene que existir, estar vigente y
   * pertenecer a este cliente—, y adivinarlo aqui seria prometer un precio.
   */
  protected readonly descuento = computed(() => {
    const promocion = this.promocionElegida();
    if (!promocion) return 0;

    const porPorcentaje = ((promocion.porcentajeDescuento ?? 0) / 100) * this.subtotal();
    const fijo = promocion.montoDescuento ?? 0;
    return Math.min(porPorcentaje + fijo, this.subtotal());
  });

  protected readonly total = computed(() => this.subtotal() - this.descuento());

  protected readonly esMesa = computed(() => this.tipoOrden() === TIPOS.MESA);
  protected readonly esDelivery = computed(() => this.tipoOrden() === TIPOS.DELIVERY);

  /**
   * El cupon pertenece a un cliente concreto: sin cliente identificado el
   * servidor responde 409 sin excepcion. Mas vale no ofrecer el campo que
   * ofrecer uno que siempre falla.
   */
  protected readonly puedeUsarCupon = computed(() => this.cliente() !== null);

  /**
   * Mismas tres reglas que valida el servidor al crear: mesa para las de mesa,
   * direccion para las de delivery, y al menos una linea.
   */
  protected readonly puedeEnviar = computed(() => {
    if (this.lineas().length === 0 || this.enviando()) return false;
    if (this.esMesa()) return this.mesaElegida() !== null;
    if (this.esDelivery()) return this.direccion().trim().length > 0;
    return true;
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    // Los complementos y las promociones son catalogo de apoyo: si alguno falla
    // la comanda se sigue pudiendo levantar sin ellos, asi que no arrastran a
    // toda la pantalla al error.
    forkJoin({
      mesas: this.mesasApi.mapa(),
      carta: this.platillosApi.menuDisponible(),
      salon: this.comandasApi.activas(),
      complementos: this.complementosApi
        .listarComplementos({ soloActivos: true })
        .pipe(catchError(() => of([] as ComplementoResponseDto[]))),
      promociones: this.promocionesApi
        .aplicables()
        .pipe(catchError(() => of([] as PromocionResponseDto[]))),
    }).subscribe({
      next: ({ mesas, carta, salon, complementos, promociones }) => {
        this.mesas.set(mesas);
        this.carta.set(carta);
        this.enSalon.set(salon);
        this.complementos.set(complementos);
        this.promociones.set(promociones);
        this.cargando.set(false);
      },
      // El interceptor ya publico el aviso con el motivo; aqui solo hay que
      // soltar el indicador para que la pantalla no quede cargando para siempre.
      error: () => this.cargando.set(false),
    });
  }

  // --- destino --------------------------------------------------------------

  /**
   * Cambiar de tipo suelta el destino que deja de tener sentido: una comanda
   * que pasa de mesa a delivery no puede seguir ocupando la mesa 4, y una que
   * vuelve a mesa no debe arrastrar una direccion que ya nadie va a usar.
   */
  protected elegirTipo(tipo: CrearOrdenRequestDtoTipoOrdenEnum): void {
    if (this.tipoOrden() === tipo) return;
    this.tipoOrden.set(tipo);
    if (tipo !== TIPOS.MESA) this.mesaElegida.set(null);
    if (tipo !== TIPOS.DELIVERY) this.direccion.set('');
  }

  protected elegirMesa(mesa: MesaResponseDto): void {
    if (mesa.estado === MesaResponseDtoEstadoEnum.INHABILITADA) return;
    this.mesaElegida.set(this.mesaElegida()?.id === mesa.id ? null : mesa);
  }

  protected anotarDireccion(valor: string): void {
    this.direccion.set(valor);
  }

  // --- cliente --------------------------------------------------------------

  protected anotarBusqueda(valor: string): void {
    this.busqueda.set(valor);
    if (valor.trim().length === 0) this.resultados.set([]);
  }

  /** Busca por nombre, documento o telefono; el servidor decide por cual. */
  protected buscarCliente(): void {
    const q = this.busqueda().trim();
    if (q.length === 0 || this.buscandoCliente()) return;

    this.buscandoCliente.set(true);
    this.clientesApi.buscarClientes({ q }).subscribe({
      next: (encontrados) => {
        this.resultados.set(encontrados);
        this.buscandoCliente.set(false);
        if (encontrados.length === 0) {
          this.avisos.info(this.t('pos.avisoSinCoincidencias', { q }));
        }
      },
      error: () => this.buscandoCliente.set(false),
    });
  }

  protected elegirCliente(elegido: ClienteResponseDto): void {
    this.cliente.set(elegido);
    this.resultados.set([]);
    this.busqueda.set('');
    this.cupones.set([]);
    this.fidelizacion.set(null);

    // Los cupones son un extra: si la consulta falla la comanda se levanta
    // igual, y el codigo se puede escribir a mano.
    if (elegido.id) {
      this.fidelizacionApi
        .cupones({ clienteId: elegido.id })
        .pipe(catchError(() => of([] as CuponResponseDto[])))
        .subscribe((suyos) => this.cupones.set(suyos.filter((c) => c.vigente)));

      // Si el mozo cambio de cliente mientras llegaba, la respuesta ya no es suya.
      this.fidelizacionApi
        .progreso({ clienteId: elegido.id })
        .pipe(catchError(() => of(null)))
        .subscribe((progreso) => {
          if (this.cliente()?.id === elegido.id) this.fidelizacion.set(progreso);
        });
    }

    // Si el cliente tiene direccion habitual y la comanda va a domicilio, se
    // propone: reescribirla entera cada vez es el trabajo que el POS existe
    // para ahorrar. Se puede corregir antes de enviar.
    if (this.esDelivery() && !this.direccion().trim() && elegido.direccionHabitual) {
      this.direccion.set(elegido.direccionHabitual);
    }
  }

  protected soltarCliente(): void {
    this.cliente.set(null);
    this.cupon.set('');
    this.cupones.set([]);
    this.fidelizacion.set(null);
  }

  /** Un toque en el cupon del cliente escribe su codigo; otro lo suelta. */
  protected alternarCupon(codigo: string | undefined): void {
    if (!codigo) return;
    this.cupon.set(this.cupon() === codigo ? '' : codigo);
  }

  /**
   * Cliente nuevo sin ficha completa: se guarda lo que el mozo alcanzo a
   * anotar. Es el caso normal en salon, donde nadie pide el documento para
   * servir un almuerzo, pero el nombre sirve para llamar a la mesa y el
   * telefono para avisar del pedido.
   */
  protected anotarClienteNuevo(): void {
    const nombre = this.busqueda().trim();
    if (nombre.length === 0) {
      this.avisos.info(this.t('pos.avisoNombreReferencia'));
      return;
    }

    this.buscandoCliente.set(true);
    this.clientesApi
      .crearAnonimo({
        clienteAnonimoRequestDto: {
          nombreReferencia: nombre,
          direccionHabitual: this.direccion().trim() || undefined,
        },
      })
      .subscribe({
        next: (nuevo) => {
          this.buscandoCliente.set(false);
          this.elegirCliente(nuevo);
          this.avisos.exito(
            this.t('pos.avisoClienteAnotado', { nombre: nuevo.nombreCompleto ?? nombre }),
          );
        },
        error: () => this.buscandoCliente.set(false),
      });
  }

  // --- armado de la comanda -------------------------------------------------

  /**
   * Un platillo agotado no se puede pedir. El servidor devuelve la carta entera
   * con su bandera `disponible` a proposito —el POS muestra en gris lo que no
   * hay, en vez de esconderlo—, pero pedirlo termina en un 422 de stock
   * insuficiente, asi que el boton ni siquiera responde.
   */
  protected agregar(platillo: PlatilloDisponibleDto): void {
    if (!platillo.disponible) return;

    const existente = this.lineas().find((l) => l.platillo.id === platillo.id);
    if (existente) {
      this.cambiarCantidad(existente, 1);
      return;
    }

    this.lineas.update((lista) => [
      ...lista,
      { platillo, cantidad: 1, nota: '', complementos: [] },
    ]);
  }

  protected cambiarCantidad(linea: LineaComanda, delta: number): void {
    this.lineas.update((lista) =>
      lista
        .map((l) => (l === linea ? { ...l, cantidad: l.cantidad + delta } : l))
        .filter((l) => l.cantidad > 0),
    );
  }

  protected anotar(linea: LineaComanda, nota: string): void {
    this.lineas.update((lista) => lista.map((l) => (l === linea ? { ...l, nota } : l)));
  }

  protected quitar(linea: LineaComanda): void {
    this.lineas.update((lista) => lista.filter((l) => l !== linea));
    if (this.lineaEnComplementos() === linea) this.lineaEnComplementos.set(null);
  }

  protected vaciar(): void {
    this.lineas.set([]);
    this.mesaElegida.set(null);
    this.lineaEnComplementos.set(null);
    this.promocionId.set(null);
    this.cupon.set('');
    this.cliente.set(null);
    this.direccion.set('');
  }

  // --- complementos ---------------------------------------------------------

  protected abrirComplementos(linea: LineaComanda): void {
    this.lineaEnComplementos.set(this.lineaEnComplementos() === linea ? null : linea);
  }

  protected cerrarComplementos(): void {
    this.lineaEnComplementos.set(null);
  }

  protected cantidadDeComplemento(
    linea: LineaComanda,
    complemento: ComplementoResponseDto,
  ): number {
    return linea.complementos.find((c) => c.complemento.id === complemento.id)?.cantidad ?? 0;
  }

  /**
   * El complemento se cobra por plato: el servidor multiplica su precio por su
   * propia cantidad y por la cantidad de la linea. Dos lomos con una gaseosa
   * cada uno son dos gaseosas, no una.
   */
  protected cambiarComplemento(
    linea: LineaComanda,
    complemento: ComplementoResponseDto,
    delta: number,
  ): void {
    this.lineas.update((lista) =>
      lista.map((l) => {
        if (l !== linea) return l;

        const actual = l.complementos.find((c) => c.complemento.id === complemento.id);
        const cantidad = (actual?.cantidad ?? 0) + delta;

        if (cantidad <= 0) {
          return {
            ...l,
            complementos: l.complementos.filter((c) => c.complemento.id !== complemento.id),
          };
        }
        if (actual) {
          return {
            ...l,
            complementos: l.complementos.map((c) =>
              c.complemento.id === complemento.id ? { ...c, cantidad } : c,
            ),
          };
        }
        return { ...l, complementos: [...l.complementos, { complemento, cantidad }] };
      }),
    );

    // La linea es un objeto nuevo tras la actualizacion; el cajon tiene que
    // seguir abierto sobre la misma posicion, no sobre la referencia vieja.
    const indice = this.lineas().findIndex((l) => l.platillo.id === linea.platillo.id);
    this.lineaEnComplementos.set(indice >= 0 ? this.lineas()[indice] : null);
  }

  protected precioDeLinea(linea: LineaComanda): number {
    const base = (linea.platillo.precioVentaBase ?? 0) * linea.cantidad;
    const extras = linea.complementos.reduce(
      (suma, c) => suma + (c.complemento.precioAdicional ?? 0) * c.cantidad * linea.cantidad,
      0,
    );
    return base + extras;
  }

  protected resumenComplementos(linea: LineaComanda): string {
    return linea.complementos
      .map((c) =>
        c.cantidad > 1 ? `${c.cantidad}× ${c.complemento.nombre}` : c.complemento.nombre,
      )
      .join(', ');
  }

  // --- descuentos -----------------------------------------------------------

  protected elegirPromocion(id: string): void {
    this.promocionId.set(id || null);
  }

  protected anotarCupon(valor: string): void {
    this.cupon.set(valor);
  }

  /**
   * Marca el cupon como canjeado contra la comanda recien creada.
   *
   * Hacen falta las dos llamadas. `POST /ordenes` valida el cupon y aplica su
   * descuento, pero deja el cupon VIGENTE: quien lo marca gastado es
   * `POST /cupones/{codigo}/canjear`. Sin esta segunda llamada el mismo codigo
   * rebaja tantas comandas como se quiera.
   *
   * Va despues y no dentro del envio a proposito. La comanda ya existe y la
   * comida ya esta en cocina; si el canje falla, lo que corresponde es avisar,
   * no fingir que la venta no ocurrio.
   */
  private canjearCupon(codigo: string | undefined, ordenId: string | undefined): void {
    if (!codigo || !ordenId) return;

    this.fidelizacionApi.canjear({ codigo, ordenId }).subscribe({
      error: () => this.avisos.info(this.t('pos.avisoCuponNoCanjeado', { codigo })),
    });
  }

  // --- envio ----------------------------------------------------------------

  /**
   * Manda la comanda. El servidor valida el stock, descuenta los insumos, calcula
   * el total y ocupa la mesa; si la receta de algo no se cubre responde 422 y no
   * queda comanda a medias.
   *
   * El tipo de pago viaja como EFECTIVO porque es la intencion declarada al
   * tomar el pedido, no el cobro: el cobro de verdad lo registra la caja al
   * final, y puede terminar siendo otro.
   */
  protected enviar(): void {
    if (!this.puedeEnviar()) return;

    this.enviando.set(true);

    this.comandasApi
      .crearOrden({
        crearOrdenRequestDto: {
          tipoOrden: this.tipoOrden(),
          canalOrigen: CrearOrdenRequestDtoCanalOrigenEnum.POS,
          tipoPago: CrearOrdenRequestDtoTipoPagoEnum.EFECTIVO,
          mesaId: this.esMesa() ? this.mesaElegida()!.id : undefined,
          direccionDelivery: this.esDelivery() ? this.direccion().trim() : undefined,
          clienteId: this.cliente()?.id,
          promocionId: this.promocionId() ?? undefined,
          cuponCodigo: this.puedeUsarCupon() ? this.cupon().trim() || undefined : undefined,
          items: this.lineas().map((l) => ({
            platilloId: l.platillo.id!,
            cantidad: l.cantidad,
            excepcionesNota: l.nota.trim() || undefined,
            complementos: l.complementos.map((c) => ({
              complementoId: c.complemento.id!,
              cantidad: c.cantidad,
            })),
          })),
        },
      })
      .subscribe({
        next: (orden) => {
          this.enviando.set(false);
          this.canjearCupon(orden.cuponCodigo, orden.id);

          const destino = orden.mesaNumero
            ? this.t('pos.destinoMesa', { numero: orden.mesaNumero })
            : this.t(this.esDelivery() ? 'pos.destinoDelivery' : 'pos.destinoRetiro');
          this.avisos.exito(
            this.t('pos.avisoEnviada', {
              destino,
              total: (orden.montoTotal ?? 0).toFixed(2),
            }),
          );

          // Es la unica ocasion en que el OTP viaja: se retiene en pantalla
          // hasta que el mozo lo haya dictado y lo cierre.
          if (orden.codigoOtpEntrega) {
            this.entregaPendiente.set({
              correlativo: (orden.id ?? '').slice(0, 8).toUpperCase(),
              otp: orden.codigoOtpEntrega,
              direccion: orden.direccionDelivery ?? this.direccion().trim(),
            });
          }

          this.vaciar();
          // La mesa quedo ocupada y la carta perdio stock: las dos listas que se
          // acaban de quedar viejas se recargan juntas.
          this.cargar();
        },
        error: () => this.enviando.set(false),
      });
  }

  protected cerrarEntrega(): void {
    this.entregaPendiente.set(null);
  }

  // --- comandas ya enviadas -------------------------------------------------

  /**
   * La comanda llego a la mesa. Sella el cronometro de despacho y la deja
   * ENTREGADO, que es el estado desde el que la caja puede cobrarla.
   */
  protected entregar(orden: OrdenResumenDto): void {
    if (!orden.id) return;

    this.comandasApi
      .cambiarEstadoOrden({
        id: orden.id,
        cambioEstadoRequestDto: { estado: CambioEstadoRequestDtoEstadoEnum.ENTREGADO },
      })
      .subscribe({
        next: () => {
          this.avisos.exito(this.t('pos.avisoEntregada', { mesa: orden.mesaNumero ?? '—' }));
          this.cargar();
        },
      });
  }

  /**
   * Cancelar pide el motivo en la propia fila y no en un dialogo del navegador:
   * en una tablet el `prompt` sale fuera de la aplicacion, no se puede estilar
   * y en algunos quioscos ni siquiera aparece.
   */
  protected pedirMotivo(orden: OrdenResumenDto): void {
    this.cancelando.set(orden.id === this.cancelando() ? null : (orden.id ?? null));
    this.motivo.set('');
  }

  protected anotarMotivo(valor: string): void {
    this.motivo.set(valor);
  }

  protected desistirCancelacion(): void {
    this.cancelando.set(null);
    this.motivo.set('');
  }

  /**
   * Cancelar repone el stock por defecto: los insumos se descontaron al crear
   * la comanda y, si el plato no llego a hacerse, siguen en la despensa.
   */
  protected cancelar(orden: OrdenResumenDto): void {
    const motivo = this.motivo().trim();
    if (!orden.id || motivo.length === 0) return;

    this.comandasApi
      .cancelar({
        id: orden.id,
        cancelarOrdenRequestDto: { motivo, reponerStock: true },
      })
      .subscribe({
        next: () => {
          this.desistirCancelacion();
          this.avisos.exito(this.t('pos.avisoCancelada'));
          this.cargar();
        },
      });
  }

  /**
   * Los gestos que se ofrecen salen de `transicionesPermitidas`, que calcula el
   * servidor con la misma tabla que va a validar el cambio. Un boton que
   * aparece aqui no puede terminar en un 409 por transicion ilegal.
   */
  protected sePuedeEntregar(orden: OrdenResumenDto): boolean {
    return this.permite(orden, OrdenResumenDtoTransicionesPermitidasEnum.ENTREGADO);
  }

  protected sePuedeCancelar(orden: OrdenResumenDto): boolean {
    return this.permite(orden, OrdenResumenDtoTransicionesPermitidasEnum.CANCELADO);
  }

  private permite(
    orden: OrdenResumenDto,
    estado: OrdenResumenDtoTransicionesPermitidasEnum,
  ): boolean {
    return orden.transicionesPermitidas?.includes(estado) ?? false;
  }

  protected esMesaElegida(mesa: MesaResponseDto): boolean {
    return this.mesaElegida()?.id === mesa.id;
  }

  // --- correccion de una comanda ya enviada ---------------------------------

  /**
   * `editable` lo decide el servidor con el mismo conjunto que despues valida
   * el cambio. Se pide la comanda completa porque el resumen del salon no trae
   * las lineas, y son justamente las lineas lo que se va a corregir.
   */
  protected sePuedeEditar(orden: OrdenResumenDto): boolean {
    return orden.editable === true;
  }

  protected abrirEdicion(orden: OrdenResumenDto): void {
    if (!orden.id) return;
    if (this.editando()?.id === orden.id) {
      this.cerrarEdicion();
      return;
    }

    this.comandasApi.obtenerOrden({ id: orden.id }).subscribe({
      next: (completa) => {
        this.editando.set(completa);
        this.platilloParaAgregar.set('');
      },
    });
  }

  protected cerrarEdicion(): void {
    this.editando.set(null);
    this.platilloParaAgregar.set('');
  }

  /**
   * Los tres endpoints de linea devuelven la comanda entera recalculada, asi
   * que el panel se repinta con lo que decidio el servidor y no con lo que la
   * pantalla creia que iba a pasar.
   *
   * No es solo prolijidad: `PUT /ordenes/{id}/detalles/{detalleId}` reemplaza
   * la linea, y la que vuelve tiene un id NUEVO. Guardarse el detalle de antes
   * y seguir operando sobre el termina en un 404 en el siguiente gesto.
   */
  private trasEditar(actualizada: OrdenResponseDto): void {
    this.guardandoLinea.set(false);
    this.editando.set(actualizada);
    this.cargar();
  }

  protected cambiarCantidadDetalle(detalle: OrdenDetalleDto, delta: number): void {
    const orden = this.editando();
    if (!orden?.id || !detalle.id || this.guardandoLinea()) return;

    const cantidad = (detalle.cantidad ?? 0) + delta;
    if (cantidad <= 0) {
      this.quitarDetalle(detalle);
      return;
    }

    this.guardandoLinea.set(true);
    this.comandasApi
      .actualizarDetalle({
        id: orden.id,
        detalleId: detalle.id,
        itemOrdenRequestDto: {
          platilloId: detalle.platilloId!,
          cantidad,
          excepcionesNota: detalle.excepcionesNota,
          // Los complementos se reenvian tal cual: el endpoint reemplaza la
          // linea entera, y omitirlos equivaldria a quitar la gaseosa que el
          // comensal ya pidio.
          complementos: (detalle.complementos ?? []).map((c) => ({
            complementoId: c.complementoId!,
            cantidad: c.cantidad ?? 1,
          })),
        },
      })
      .subscribe({
        next: (actualizada) => this.trasEditar(actualizada),
        error: () => this.guardandoLinea.set(false),
      });
  }

  /**
   * La comanda ya esta en cocina: quitar una linea cambia lo que se cocina, asi
   * que se pregunta antes. El gesto que abre la pregunta es gris; el rojo vive
   * dentro del dialogo.
   */
  protected async quitarDetalle(detalle: OrdenDetalleDto): Promise<void> {
    const orden = this.editando();
    if (!orden?.id || !detalle.id || this.guardandoLinea()) return;

    const confirmado = await this.confirmacion.pedir({
      titulo: this.t('pos.quitarDetalleTitulo', { platillo: detalle.platilloNombre ?? '' }),
      mensaje: this.t('pos.quitarDetalleMensaje'),
      confirmar: this.t('pos.quitarDetalleConfirmar'),
    });
    if (!confirmado || this.guardandoLinea()) return;

    this.guardandoLinea.set(true);
    this.comandasApi.eliminarDetalle({ id: orden.id, detalleId: detalle.id }).subscribe({
      next: (actualizada) => {
        this.avisos.exito(this.t('pos.avisoQuitado', { platillo: detalle.platilloNombre ?? '' }));
        this.trasEditar(actualizada);
      },
      error: () => this.guardandoLinea.set(false),
    });
  }

  protected elegirPlatilloParaAgregar(id: string): void {
    this.platilloParaAgregar.set(id);
  }

  protected agregarDetalle(): void {
    const orden = this.editando();
    const platilloId = this.platilloParaAgregar();
    if (!orden?.id || !platilloId || this.guardandoLinea()) return;

    this.guardandoLinea.set(true);
    this.comandasApi
      .agregarDetalle({
        id: orden.id,
        itemOrdenRequestDto: { platilloId, cantidad: 1 },
      })
      .subscribe({
        next: (actualizada) => {
          this.platilloParaAgregar.set('');
          this.trasEditar(actualizada);
        },
        error: () => this.guardandoLinea.set(false),
      });
  }
}
