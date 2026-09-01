import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, interval } from 'rxjs';

import {
  CocinaKDSApi,
  ComandaKdsDtoEstadoEnum,
  type ComandaKdsDto,
  type KpisCocinaDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';

/**
 * Cada cuanto se repinta la cola. En cocina nadie va a pulsar "actualizar" con
 * las manos ocupadas, asi que la pantalla se refresca sola. Quince segundos es
 * suficiente para un cronometro que se lee en minutos y no castiga al servidor.
 */
const REFRESCO_MS = 15_000;

/**
 * Pantalla de cocina: la cola por orden de llegada y los dos gestos que mueven
 * la comanda —tomarla y darla por lista.
 *
 * Version minima del flujo principal. Falta la promesa de tiempo
 * (`PATCH /kds/ordenes/{id}/estimar`) y el aviso de insumo faltante: los dos
 * existen en el backend, pero el cliente generado en `src/app/api` viene de un
 * contrato anterior y todavia no los declara. Se recuperan regenerandolo con
 * `pnpm run api:sync` contra el backend en marcha.
 */
@Component({
  selector: 'app-kds',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './kds.page.html',
  styleUrl: './kds.page.scss',
})
export class KdsPage implements OnInit {
  private readonly kdsApi = inject(CocinaKDSApi);
  private readonly avisos = inject(AvisosService);

  protected readonly cargando = signal(true);
  protected readonly cola = signal<ComandaKdsDto[]>([]);
  protected readonly kpis = signal<KpisCocinaDto | null>(null);

  /** Comanda sobre la que hay una peticion en vuelo, para no pulsar dos veces. */
  protected readonly ocupada = signal<string | null>(null);

  constructor() {
    interval(REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));
  }

  ngOnInit(): void {
    this.cargar();
  }

  /**
   * `silencioso` es lo que separa el refresco automatico de la carga inicial:
   * el temporizador no debe vaciar la pantalla cada quince segundos, que en
   * cocina se leeria como que la cola desaparecio.
   */
  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);

    forkJoin({
      cola: this.kdsApi.cola(),
      kpis: this.kdsApi.kpis(),
    }).subscribe({
      next: ({ cola, kpis }) => {
        this.cola.set(cola);
        this.kpis.set(kpis);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /**
   * Cocina se hace cargo. Arranca el cronometro de preparacion y cierra la
   * etapa de recepcion: a partir de aqui el tiempo que pase cuenta como tiempo
   * de cocina, no como espera.
   */
  protected tomar(comanda: ComandaKdsDto): void {
    if (!comanda.ordenId || this.ocupada()) return;

    this.ocupada.set(comanda.ordenId);
    this.kdsApi.tomar({ id: comanda.ordenId }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.avisos.exito(`Comanda ${comanda.correlativo ?? ''} en preparacion.`);
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  /**
   * La comanda esta lista. Sella `tiempoCierrePlatillo`, que es lo que mide
   * cuanto tardo de verdad el plato, y deja el pase al mozo.
   */
  protected listo(comanda: ComandaKdsDto): void {
    if (!comanda.ordenId || this.ocupada()) return;

    this.ocupada.set(comanda.ordenId);
    this.kdsApi.listo({ id: comanda.ordenId }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.avisos.exito(`Comanda ${comanda.correlativo ?? ''} lista para servir.`);
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  protected esperando(comanda: ComandaKdsDto): boolean {
    return comanda.estado === ComandaKdsDtoEstadoEnum.ENCOLADO;
  }

  protected enFuego(comanda: ComandaKdsDto): boolean {
    return comanda.estado === ComandaKdsDtoEstadoEnum.EN_PREPARACION;
  }
}
