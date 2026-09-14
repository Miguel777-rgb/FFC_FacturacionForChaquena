import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { BotsSeccion } from './bots.seccion';
import { LocalSeccion } from './local.seccion';
import { OutboxSeccion } from './outbox.seccion';

type Seccion = 'local' | 'bots' | 'outbox';

const SECCIONES: ReadonlyArray<{ id: Seccion; nombre: ClaveI18n }> = [
  { id: 'local', nombre: 'configuracion.local' },
  { id: 'bots', nombre: 'configuracion.bots' },
  { id: 'outbox', nombre: 'configuracion.outbox' },
];

/**
 * Configuracion: los parametros del local, la salud de los bots y la bandeja de
 * eventos. Las tres eran pestanas de la trastienda solo para el administrador;
 * juntas aqui porque las tres son del administrador y ninguna se usa a diario.
 *
 * Cada seccion pide sus datos al montarse: `@switch` y no `[hidden]`, para que
 * mirar un parametro no descargue la bandeja de eventos entera.
 */
@Component({
  selector: 'app-configuracion',
  imports: [LocalSeccion, BotsSeccion, OutboxSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './configuracion.page.html',
})
export class ConfiguracionPage {
  protected readonly t = inject(I18nService).t;

  protected readonly SECCIONES = SECCIONES;
  protected readonly activa = signal<Seccion>('local');
}
