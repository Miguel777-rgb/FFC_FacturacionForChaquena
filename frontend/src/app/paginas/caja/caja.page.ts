import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { forkJoin } from 'rxjs';

import {
  CajaArqueoYFraudeApi,
  CajaPagosApi,
  CambioEstadoRequestDtoEstadoEnum,
  ComandasApi,
  OrdenResponseDtoEstadoEnum,
  OrdenResumenDtoEstadoEnum,
  RegistrarPagoRequestDtoTipoPagoEnum,
  type ArqueoCajaDto,
  type OrdenResponseDto,
  type OrdenResumenDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';

/**
 * Caja: cobrar la comanda entregada y cerrar el ciclo.
 *
 * Solo cobra en efectivo, que es lo que recorre el flujo principal. Billetera
 * y tarjeta quedan fuera a proposito: el backend las deja en PENDIENTE hasta
 * que un cajero las acredita con `POST /caja/pagos/{id}/confirmar`, y esa
 * segunda pantalla es otra historia que esta version no cuenta.
 *
 * La comanda **no** se marca pagada a mano. El backend la pasa a PAGADO solo
 * cuando lo confirmado alcanza el total; aqui se registra el cobro y se vuelve
 * a leer la comanda para ver que decidio el servidor.
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
  private readonly avisos = inject(AvisosService);

  protected readonly cargando = signal(true);
  protected readonly cobrando = signal(false);

  protected readonly enSalon = signal<OrdenResumenDto[]>([]);
  protected readonly arqueo = signal<ArqueoCajaDto | null>(null);

  /**
   * La comanda que se esta cobrando. Se guarda entera y no como id porque en
   * cuanto se paga desaparece de `GET /ordenes/activas` —esa lista llega hasta
   * ENTREGADO—, y aun hace falta tenerla delante para cerrarla.
   */
  protected readonly seleccionada = signal<OrdenResponseDto | null>(null);
  protected readonly entregado = signal<number | null>(null);
  protected readonly ultimoVuelto = signal<number | null>(null);

  protected readonly vuelto = computed(() => {
    const orden = this.seleccionada();
    const puesto = this.entregado();
    if (!orden || puesto === null) return null;
    return puesto - (orden.montoTotal ?? 0);
  });

  protected readonly puedeCobrar = computed(() => {
    const orden = this.seleccionada();
    const vuelto = this.vuelto();
    return (
      orden?.estado === OrdenResponseDtoEstadoEnum.ENTREGADO &&
      vuelto !== null &&
      vuelto >= 0 &&
      !this.cobrando()
    );
  });

  protected readonly puedeCerrar = computed(
    () => this.seleccionada()?.estado === OrdenResponseDtoEstadoEnum.PAGADO && !this.cobrando(),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    forkJoin({
      salon: this.comandasApi.activas(),
      arqueo: this.cajaApi.arqueo(),
    }).subscribe({
      next: ({ salon, arqueo }) => {
        this.enSalon.set(salon);
        this.arqueo.set(arqueo);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /**
   * Abre la cuenta. Se pide el detalle completo en vez de reutilizar el resumen
   * de la lista: el resumen no trae las lineas, y una cuenta sin lineas no se
   * puede repasar con el comensal delante.
   */
  protected seleccionar(orden: OrdenResumenDto): void {
    if (!orden.id) return;

    this.limpiarCobro();
    this.comandasApi.obtener10({ id: orden.id }).subscribe({
      next: (completa) => this.seleccionada.set(completa),
    });
  }

  protected cerrarCuenta(): void {
    this.seleccionada.set(null);
    this.limpiarCobro();
  }

  protected anotarEntregado(valor: string): void {
    const numero = Number.parseFloat(valor);
    this.entregado.set(Number.isFinite(numero) ? numero : null);
  }

  /** Deja el importe exacto: el caso mas comun cuando se paga con tarjeta o justo. */
  protected importeExacto(): void {
    this.entregado.set(this.seleccionada()?.montoTotal ?? null);
  }

  // --- cobro y cierre -------------------------------------------------------

  protected cobrar(): void {
    const orden = this.seleccionada();
    const puesto = this.entregado();
    if (!orden?.id || puesto === null || !this.puedeCobrar()) return;

    this.cobrando.set(true);

    this.pagosApi
      .registrar1({
        ordenId: orden.id,
        registrarPagoRequestDto: {
          tipoPago: RegistrarPagoRequestDtoTipoPagoEnum.EFECTIVO,
          monto: orden.montoTotal ?? 0,
          montoEntregado: puesto,
        },
      })
      .subscribe({
        next: (pago) => {
          this.ultimoVuelto.set(pago.vuelto ?? 0);
          this.avisos.exito(`Cobrado. Vuelto: S/ ${(pago.vuelto ?? 0).toFixed(2)}`);
          // Quien decide si la comanda quedo PAGADA es el servidor, al comprobar
          // que lo confirmado cubre el total. Se relee para verlo, no se supone.
          this.recargarSeleccionada(orden.id!);
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
      .cambiarEstado({
        id: orden.id,
        cambioEstadoRequestDto: { estado: CambioEstadoRequestDtoEstadoEnum.CONCLUIDO },
      })
      .subscribe({
        next: () => {
          this.cobrando.set(false);
          this.avisos.exito(`Comanda de la mesa ${orden.mesaNumero ?? '—'} cerrada. Mesa libre.`);
          this.cerrarCuenta();
          this.cargar();
        },
        error: () => this.cobrando.set(false),
      });
  }

  private recargarSeleccionada(id: string): void {
    this.comandasApi.obtener10({ id }).subscribe({
      next: (completa) => {
        this.seleccionada.set(completa);
        this.cobrando.set(false);
        // El arqueo acaba de cambiar con este cobro.
        this.cargar();
      },
      error: () => this.cobrando.set(false),
    });
  }

  private limpiarCobro(): void {
    this.entregado.set(null);
    this.ultimoVuelto.set(null);
  }

  /** Solo se cobra lo entregado: antes de eso la comida no ha llegado a la mesa. */
  protected seCobra(orden: OrdenResumenDto): boolean {
    return orden.estado === OrdenResumenDtoEstadoEnum.ENTREGADO;
  }
}
