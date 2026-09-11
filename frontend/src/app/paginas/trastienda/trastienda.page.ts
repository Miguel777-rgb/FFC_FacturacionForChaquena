import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { SesionService } from '../../nucleo/sesion/sesion.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import type { Rol } from '../../nucleo/sesion/rol';
import { BotsSeccion } from './bots.seccion';
import { CartaSeccion } from './carta.seccion';
import { InventarioSeccion } from './inventario.seccion';
import { LocalSeccion } from './local.seccion';
import { OutboxSeccion } from './outbox.seccion';

type Pestana = 'inventario' | 'carta' | 'local' | 'bots' | 'outbox';

/**
 * Quien puede abrir cada pestana, copiado de los `@PreAuthorize` de su
 * controlador. Almacen entra a la trastienda pero no a los parametros del
 * local, a los bots ni al outbox: esos son solo del administrador, y ensenarle
 * la pestana solo le regalaria un 403 en mitad del turno.
 */
const ROLES_POR_PESTANA: Record<Pestana, Rol[]> = {
  inventario: ['ALMACEN', 'ADMIN'],
  carta: ['ALMACEN', 'ADMIN'],
  local: ['ADMIN'],
  bots: ['ADMIN'],
  outbox: ['ADMIN'],
};

/**
 * Trastienda: lo que el local necesita para no depender de que alguien abra la
 * base de datos.
 *
 * Cinco secciones que no se parecen en nada entre si —el stock, la carta, los
 * parametros del local, la salud de los bots y la bandeja de eventos—, asi que
 * comparten cascaron y nada mas. Cada una pide sus propios datos cuando se la
 * abre: entrar a corregir un precio no tiene por que descargar el kardex
 * entero.
 *
 * El personal y los indicadores estuvieron aqui y se fueron a su propia
 * superficie. La trastienda es donde se administran las cosas del local; medir
 * el negocio y repartir permisos no son cosas, y amontonarlas aqui hacia que
 * una sola entrada del panel cargara con media aplicacion.
 */
@Component({
  selector: 'app-trastienda',
  imports: [InventarioSeccion, CartaSeccion, LocalSeccion, BotsSeccion, OutboxSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './trastienda.page.html',
  styleUrl: './trastienda.page.scss',
})
export class TrastiendaPage {
  private readonly sesion = inject(SesionService);

  protected readonly t = inject(I18nService).t;

  /** El rotulo va como clave: la pestana abierta cambia de idioma en el sitio. */
  private readonly todas: ReadonlyArray<{ id: Pestana; nombre: ClaveI18n }> = [
    { id: 'inventario', nombre: 'trastienda.inventario' },
    { id: 'carta', nombre: 'trastienda.carta' },
    { id: 'local', nombre: 'trastienda.local' },
    { id: 'bots', nombre: 'trastienda.bots' },
    { id: 'outbox', nombre: 'trastienda.outbox' },
  ];

  protected readonly pestanas = computed(() =>
    this.todas.filter((p) => this.sesion.tieneAlgunRol(ROLES_POR_PESTANA[p.id])),
  );

  /**
   * Se abre por la primera pestana a la que la persona tiene derecho, no por
   * una fija: al administrador le toca inventario, pero si manana la primera
   * pasara a ser de ADMIN, el almacenero no aterrizaria en un 403.
   */
  protected readonly activa = signal<Pestana>(this.pestanas()[0]?.id ?? 'inventario');

  protected readonly titulo = computed(() => {
    const abierta = this.pestanas().find((p) => p.id === this.activa());
    return this.t(abierta?.nombre ?? 'trastienda.marca');
  });

  protected abrir(id: Pestana): void {
    this.activa.set(id);
  }
}
