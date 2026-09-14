import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';

import { BotsApi, type EstadoBotsDto, type VinculacionBotDto } from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { Icono } from '../../disenio/icono';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';

/** Que hace cada identidad. El nombre del canal no lo dice por si solo. */
const AUDIENCIA: Record<string, ClaveI18n> = {
  IN: 'bots.audienciaIn',
  OUT: 'bots.audienciaOut',
};

/**
 * Estado de los bots.
 *
 * Los bots son la unica parte del sistema que se rompe sin romper nada: un
 * token caducado no tumba ninguna pantalla, solo hace que el almacenero escriba
 * `/stock` y no le conteste nadie, y eso se descubre siempre a mitad de
 * servicio. Esta pestana existe para que ese fallo se vea antes.
 *
 * Distingue tres capas que fallan por separado, y las pinta en ese orden porque
 * es el orden en que hay que descartarlas: el proveedor configurado, la conexion
 * de cada bot, y quien esta vinculado. Un bot impecablemente conectado tampoco
 * atiende a nadie si nadie corrio `/vincular`.
 */
@Component({
  selector: 'app-bots-seccion',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './bots.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class BotsSeccion implements OnInit {
  private readonly botsApi = inject(BotsApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);
  private readonly confirmacion = inject(ConfirmacionService);

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly estado = signal<EstadoBotsDto | null>(null);

  /**
   * Si lo que se pidio en el `.env` es lo que quedo en servicio. Cuando no
   * coinciden —se escribio "telegram" y quedo "ninguno"— ese es el fallo, y
   * ninguna otra cifra de esta pantalla importa hasta arreglarlo.
   */
  protected readonly proveedorCoincide = computed(() => {
    const e = this.estado();
    if (!e) return true;
    return (e.proveedorPedido ?? '') === (e.proveedorActivo ?? '');
  });

  /** Bots con credenciales que aun asi no estan hablando con el proveedor. */
  protected readonly caidos = computed(
    () => (this.estado()?.canales ?? []).filter((c) => c.configurado && !c.conectado).length,
  );

  protected readonly sinVincular = computed(() => {
    const e = this.estado();
    if (!e) return 0;
    return Math.max((e.trabajadoresActivos ?? 0) - (e.trabajadoresVinculados ?? 0), 0);
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected audienciaDe(canal: string | undefined): string {
    const clave = AUDIENCIA[canal ?? ''];
    return clave ? this.t(clave) : '';
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.botsApi.estadoBots().subscribe({
      next: (estado) => {
        this.estado.set(estado);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /**
   * Suelta la cuenta de mensajeria de una persona.
   *
   * Es la contraparte que le faltaba a `/vincular`: cuando el bot dice "esta
   * cuenta ya esta vinculada a otro, pide al administrador que la libere", este
   * es el boton que lo libera. No da de baja a nadie: quien queda desvinculado
   * sigue entrando al front con su usuario, solo pierde el atajo por chat.
   */
  protected async desvincular(v: VinculacionBotDto): Promise<void> {
    if (!v.trabajadorId || this.guardando()) return;

    const confirmado = await this.confirmacion.pedir({
      titulo: this.t('bots.liberarTitulo', { usuario: v.username ?? '' }),
      mensaje: this.t('bots.liberarMensaje'),
      confirmar: this.t('bots.liberar'),
    });
    if (!confirmado || this.guardando()) return;

    this.guardando.set(true);
    this.botsApi.desvincularCuentaBot({ trabajadorId: v.trabajadorId }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.avisos.exito(this.t('bots.avisoDesvinculado', { usuario: v.username ?? '' }));
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }
}
