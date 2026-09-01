import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { forkJoin } from 'rxjs';

import {
  CambioEstadoRequestDtoEstadoEnum,
  CatalogoPlatillosApi,
  ComandasApi,
  CrearOrdenRequestDtoCanalOrigenEnum,
  CrearOrdenRequestDtoTipoOrdenEnum,
  CrearOrdenRequestDtoTipoPagoEnum,
  MesaResponseDtoEstadoEnum,
  OrdenResumenDtoEstadoEnum,
  SalonMesasApi,
  type MesaResponseDto,
  type OrdenResumenDto,
  type PlatilloDisponibleDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';

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
}

/**
 * Punto de venta: el paso donde nace la comanda y el paso donde el mozo la
 * entrega en la mesa.
 *
 * Es la version minima del flujo principal —mesa, carta, comanda, entrega—,
 * sin complementos, promociones ni cliente identificado. Todo eso cabe en los
 * mismos endpoints y esta pendiente de la fase 2 del plan.
 */
@Component({
  selector: 'app-pos',
  imports: [DecimalPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pos.page.html',
  styleUrl: './pos.page.scss',
})
export class PosPage implements OnInit {
  private readonly mesasApi = inject(SalonMesasApi);
  private readonly platillosApi = inject(CatalogoPlatillosApi);
  private readonly comandasApi = inject(ComandasApi);
  private readonly avisos = inject(AvisosService);

  protected readonly cargando = signal(true);
  protected readonly enviando = signal(false);

  protected readonly mesas = signal<MesaResponseDto[]>([]);
  protected readonly carta = signal<PlatilloDisponibleDto[]>([]);
  protected readonly enSalon = signal<OrdenResumenDto[]>([]);

  protected readonly mesaElegida = signal<MesaResponseDto | null>(null);
  protected readonly lineas = signal<LineaComanda[]>([]);

  protected readonly total = computed(() =>
    this.lineas().reduce((suma, l) => suma + (l.platillo.precioVentaBase ?? 0) * l.cantidad, 0),
  );

  protected readonly puedeEnviar = computed(
    () => this.mesaElegida() !== null && this.lineas().length > 0 && !this.enviando(),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    forkJoin({
      mesas: this.mesasApi.mapa(),
      carta: this.platillosApi.menuDisponible(),
      salon: this.comandasApi.activas(),
    }).subscribe({
      next: ({ mesas, carta, salon }) => {
        this.mesas.set(mesas);
        this.carta.set(carta);
        this.enSalon.set(salon);
        this.cargando.set(false);
      },
      // El interceptor ya publico el aviso con el motivo; aqui solo hay que
      // soltar el indicador para que la pantalla no quede cargando para siempre.
      error: () => this.cargando.set(false),
    });
  }

  // --- armado de la comanda -------------------------------------------------

  protected elegirMesa(mesa: MesaResponseDto): void {
    if (mesa.estado === MesaResponseDtoEstadoEnum.INHABILITADA) return;
    this.mesaElegida.set(this.mesaElegida()?.id === mesa.id ? null : mesa);
  }

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

    this.lineas.update((lista) => [...lista, { platillo, cantidad: 1, nota: '' }]);
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
  }

  protected vaciar(): void {
    this.lineas.set([]);
    this.mesaElegida.set(null);
  }

  // --- envio y entrega ------------------------------------------------------

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
    const mesa = this.mesaElegida();
    if (!mesa || this.lineas().length === 0 || this.enviando()) return;

    this.enviando.set(true);

    this.comandasApi
      .crear3({
        crearOrdenRequestDto: {
          tipoOrden: CrearOrdenRequestDtoTipoOrdenEnum.MESA,
          canalOrigen: CrearOrdenRequestDtoCanalOrigenEnum.POS,
          tipoPago: CrearOrdenRequestDtoTipoPagoEnum.EFECTIVO,
          mesaId: mesa.id,
          items: this.lineas().map((l) => ({
            platilloId: l.platillo.id!,
            cantidad: l.cantidad,
            excepcionesNota: l.nota.trim() || undefined,
          })),
        },
      })
      .subscribe({
        next: (orden) => {
          this.enviando.set(false);
          this.vaciar();
          this.avisos.exito(
            `Comanda enviada a cocina · mesa ${orden.mesaNumero ?? mesa.numero} · S/ ${(orden.montoTotal ?? 0).toFixed(2)}`,
          );
          // La mesa quedo ocupada y la carta perdio stock: las dos listas que se
          // acaban de quedar viejas se recargan juntas.
          this.cargar();
        },
        error: () => this.enviando.set(false),
      });
  }

  /**
   * La comanda llego a la mesa. Sella el cronometro de despacho y la deja
   * ENTREGADO, que es el estado desde el que la caja puede cobrarla.
   */
  protected entregar(orden: OrdenResumenDto): void {
    if (!orden.id) return;

    this.comandasApi
      .cambiarEstado({
        id: orden.id,
        cambioEstadoRequestDto: { estado: CambioEstadoRequestDtoEstadoEnum.ENTREGADO },
      })
      .subscribe({
        next: () => {
          this.avisos.exito(`Comanda de la mesa ${orden.mesaNumero ?? '—'} entregada.`);
          this.cargar();
        },
      });
  }

  /**
   * Solo se ofrece entregar donde la maquina de estados lo permite: desde
   * EN_PREPARACION y desde EN_DESPACHO. Una comanda que cocina todavia no ha
   * tomado no se puede entregar, y ofrecerlo solo llevaria a un 409.
   */
  protected sePuedeEntregar(orden: OrdenResumenDto): boolean {
    return (
      orden.estado === OrdenResumenDtoEstadoEnum.EN_PREPARACION ||
      orden.estado === OrdenResumenDtoEstadoEnum.EN_DESPACHO
    );
  }

  protected esMesaElegida(mesa: MesaResponseDto): boolean {
    return this.mesaElegida()?.id === mesa.id;
  }
}
