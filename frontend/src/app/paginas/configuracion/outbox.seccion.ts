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

import { OutboxApi, OutboxEventDtoStatusEnum, type OutboxEventDto } from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { Icono } from '../../disenio/icono';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';

const ESTADOS = OutboxEventDtoStatusEnum;

/** Los cuatro estados, en el orden en que se miran cuando algo va mal. */
const FILTROS: ReadonlyArray<{ id: OutboxEventDtoStatusEnum | ''; nombre: ClaveI18n }> = [
  { id: '', nombre: 'outbox.todos' },
  { id: ESTADOS.ERROR, nombre: 'outbox.conError' },
  { id: ESTADOS.DEAD_LETTER, nombre: 'outbox.colaMuerta' },
  { id: ESTADOS.PENDIENTE, nombre: 'outbox.pendientes' },
  { id: ESTADOS.PROCESADO, nombre: 'outbox.procesados' },
];

/**
 * La bandeja del outbox: lo que el local tiene que mandar a facturacion y
 * todavia no ha salido.
 *
 * Un evento aqui no es un error del sistema, es una venta que existe y una
 * factura que aun no. Por eso la pantalla no es un log: se ordena por lo que
 * hay que arreglar —errores y cola muerta primero— y el unico gesto que ofrece
 * es devolver un evento a la cola.
 *
 * Mientras `backend-facturacion` no exista, el worker esta apagado
 * (`app.outbox.enabled=false`) y todo se queda en PENDIENTE. Eso no es una
 * averia y la pantalla lo dice, para que nadie reintente cien veces un evento
 * que no tiene a donde ir.
 */
@Component({
  selector: 'app-outbox-seccion',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './outbox.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class OutboxSeccion implements OnInit {
  private readonly outboxApi = inject(OutboxApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;
  protected readonly fecha = this.i18n.fecha;

  protected readonly FILTROS = FILTROS;
  protected readonly ESTADOS = ESTADOS;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  protected readonly eventos = signal<OutboxEventDto[]>([]);
  protected readonly salud = signal<Record<string, number>>({});
  protected readonly filtro = signal<OutboxEventDtoStatusEnum | ''>('');

  /** Evento desplegado, ya con su payload completo: la lista no lo trae. */
  protected readonly detalleDe = signal<OutboxEventDto | null>(null);

  /**
   * Lo que hay que mirar. Un solo numero: si es cero, la bandeja esta al dia y
   * no hace falta leer ninguna fila.
   */
  protected readonly atascados = computed(
    () => (this.salud()['errores'] ?? 0) + (this.salud()['colaMuerta'] ?? 0),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    forkJoin({
      eventos: this.outboxApi.listarEventosOutbox({
        pageable: { page: 0, size: 50 },
        status: this.filtro() || undefined,
      }),
      salud: this.outboxApi.salud().pipe(catchError(() => of({} as Record<string, number>))),
    }).subscribe({
      next: ({ eventos, salud }) => {
        this.eventos.set(eventos.contenido ?? []);
        this.salud.set(salud);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected filtrar(valor: string): void {
    this.filtro.set(valor as OutboxEventDtoStatusEnum | '');
    this.detalleDe.set(null);
    this.cargar();
  }

  /**
   * El payload no viaja en la lista: es el cuerpo entero del evento y son
   * cincuenta por pagina. Se pide solo el que se abre.
   */
  protected abrirDetalle(evento: OutboxEventDto): void {
    if (this.detalleDe()?.id === evento.id) {
      this.detalleDe.set(null);
      return;
    }
    if (!evento.id) return;

    this.detalleDe.set(evento);
    this.outboxApi.obtenerEventoOutbox({ id: evento.id }).subscribe({
      next: (completo) => this.detalleDe.set(completo),
    });
  }

  /**
   * El cuerpo del evento, ya formateado. Es un `computed` y no un metodo porque
   * la plantilla lo lee en cada ciclo, y serializar el payload entero cada vez
   * no hace falta: solo cambia cuando cambia el evento abierto.
   */
  protected readonly payload = computed(() => {
    const abierto = this.detalleDe();
    return abierto?.payload ? JSON.stringify(abierto.payload, null, 2) : '';
  });

  /** Solo lo atascado se puede reintentar: lo procesado el servidor lo rechaza. */
  protected sePuedeReintentar(evento: OutboxEventDto): boolean {
    return evento.status === ESTADOS.ERROR || evento.status === ESTADOS.DEAD_LETTER;
  }

  /**
   * Reintentar devuelve el evento a la cola con la cuenta de intentos a cero.
   * No lo manda: de eso se encarga el worker cuando le toque. Lo que hace es
   * darle otra oportunidad a algo que se habia rendido.
   */
  protected reintentar(evento: OutboxEventDto): void {
    if (!evento.id || this.guardando()) return;

    this.guardando.set(true);
    this.outboxApi.reintentar({ id: evento.id }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.exito(this.t('outbox.avisoReintentado'));
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }
}
