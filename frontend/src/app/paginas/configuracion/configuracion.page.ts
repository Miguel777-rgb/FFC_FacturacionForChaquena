import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';

import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { BotsSeccion } from './bots.seccion';
import { DatosLocalSeccion } from './datos-local.seccion';
import { LocalSeccion } from './local.seccion';
import { NivelesSeccion } from './niveles.seccion';
import { OutboxSeccion } from './outbox.seccion';

type Seccion = 'datos' | 'local' | 'niveles' | 'bots' | 'outbox';

const SECCIONES: ReadonlyArray<{ id: Seccion; nombre: ClaveI18n }> = [
  { id: 'datos', nombre: 'configuracion.datosLocal' },
  { id: 'local', nombre: 'configuracion.local' },
  { id: 'niveles', nombre: 'configuracion.niveles' },
  { id: 'bots', nombre: 'configuracion.bots' },
  { id: 'outbox', nombre: 'configuracion.outbox' },
];

/**
 * Configuracion: quien es el local, las reglas que gobiernan el turno, los
 * niveles de lealtad, la salud de los bots y la bandeja de eventos. Todo es del
 * administrador y nada se toca a diario.
 *
 * Abre por los datos del local porque es lo primero que se completa al
 * instalar: sin RUC ni IGV, el ticket no tiene encabezado ni desglose.
 *
 * Cada seccion pide sus datos al montarse: `@switch` y no `[hidden]`, para que
 * mirar un parametro no descargue la bandeja de eventos entera.
 */
@Component({
  selector: 'app-configuracion',
  imports: [DatosLocalSeccion, LocalSeccion, NivelesSeccion, BotsSeccion, OutboxSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './configuracion.page.html',
})
export class ConfiguracionPage {
  protected readonly t = inject(I18nService).t;

  protected readonly SECCIONES = SECCIONES;
  protected readonly activa = signal<Seccion>('datos');
}
