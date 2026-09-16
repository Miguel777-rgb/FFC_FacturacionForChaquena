import type { HttpResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import type { Observable } from 'rxjs';

import { AvisosService } from '../nucleo/http/avisos.service';
import { guardarArchivo, nombreDeAdjunto } from '../nucleo/http/descarga';
import { I18nService } from '../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../nucleo/i18n/traducciones/es';
import { Icono } from './icono';

export type FormatoDescarga = 'PDF' | 'XLSX';

/** Lo que la pantalla sabe hacer: pedir el archivo en un formato, con su rango y filtros. */
export type PedirArchivo = (formato: FormatoDescarga) => Observable<HttpResponse<Blob>>;

const FORMATOS: ReadonlyArray<{
  id: FormatoDescarga;
  nombre: ClaveI18n;
  aria: ClaveI18n;
  extension: string;
}> = [
  { id: 'PDF', nombre: 'descarga.pdf', aria: 'descarga.pdfAria', extension: 'pdf' },
  { id: 'XLSX', nombre: 'descarga.excel', aria: 'descarga.excelAria', extension: 'xlsx' },
];

/**
 * Los dos botones de descarga de un reporte: PDF para imprimir o archivar, Excel
 * para sumar y filtrar.
 *
 * Dos botones y no un menu: son dos formatos fijos, y un menu desplegable en el
 * celular es un toque mas y un panel que tapa lo que se quiere bajar.
 *
 * El nombre del archivo lo decide el servidor, que conoce el rango que de
 * verdad uso; la pantalla solo lo pide.
 */
@Component({
  selector: 'app-descarga',
  imports: [Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="descarga" role="group" [attr.aria-label]="t(rotulo() ?? 'descarga.grupo')">
      @if (rotulo(); as r) {
        <span class="rotulo">{{ t(r) }}</span>
      }
      @for (f of FORMATOS; track f.id) {
        <button
          type="button"
          class="secundario"
          [disabled]="ocupado() !== null"
          [attr.aria-busy]="ocupado() === f.id"
          [attr.aria-label]="t(f.aria)"
          [attr.title]="t(f.aria)"
          (click)="bajar(f.id, f.extension)"
        >
          <app-icono nombre="descargar" [tamano]="18" />
          {{ t(ocupado() === f.id ? 'descarga.preparando' : f.nombre) }}
        </button>
      }
    </div>
  `,
  styles: `
    .descarga {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: var(--e2);
    }

    .rotulo {
      font-size: var(--t-chico);
      color: var(--tenue);
    }
  `,
})
export class Descarga {
  private readonly avisos = inject(AvisosService);
  protected readonly t = inject(I18nService).t;

  /** Como se pide el archivo. */
  readonly archivo = input.required<PedirArchivo>();
  /** Que se baja, cuando en la misma pantalla hay mas de una descarga. */
  readonly rotulo = input<ClaveI18n | null>(null);
  /** Nombre por si el servidor no manda uno, sin extension. */
  readonly respaldo = input('chaquena');

  protected readonly FORMATOS = FORMATOS;
  protected readonly ocupado = signal<FormatoDescarga | null>(null);

  protected bajar(formato: FormatoDescarga, extension: string): void {
    if (this.ocupado()) return;
    this.ocupado.set(formato);

    this.archivo()(formato).subscribe({
      next: (respuesta) => {
        if (!respuesta.body) return;
        const nombre = nombreDeAdjunto(respuesta, `${this.respaldo()}.${extension}`);
        guardarArchivo(respuesta.body, nombre);
        this.avisos.exito(this.t('descarga.lista', { nombre }));
      },
      // El interceptor de errores ya avisa; aqui solo se suelta el boton.
      error: () => this.ocupado.set(null),
      complete: () => this.ocupado.set(null),
    });
  }
}
