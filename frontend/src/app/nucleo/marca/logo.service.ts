import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

// Del archivo concreto y no del barril `api`: este servicio carga con el panel
// al arrancar, y el barril arrastraria todos los servicios generados.
import { ArchivosApi } from '../../api/api/archivos.api';
import { LocalApi } from '../../api/api/local.api';
import { I18nService } from '../i18n/i18n.service';
import { SesionService } from '../sesion/sesion.service';
import { problemaDeImagen, urlDeArchivo } from './archivos';

/** Donde vivia el logo cuando era de cada dispositivo. Se borra al arrancar. */
const CLAVE_ANTIGUA = 'chaquena.logo';

/**
 * Logo del local, el mismo en todas las pantallas.
 *
 * Antes vivia en el localStorage de cada dispositivo: cada tablet llevaba el
 * suyo y un celular nuevo arrancaba sin logo. Ahora es un dato del local: lo
 * cambia el administrador y lo ven todos.
 *
 * Se pide al abrirse la sesion, porque leer los datos del local exige estar
 * dentro. La imagen en si se sirve sin token.
 */
@Injectable({ providedIn: 'root' })
export class LogoService {
  private readonly t = inject(I18nService).t;
  private readonly sesion = inject(SesionService);
  private readonly localApi = inject(LocalApi);
  private readonly archivosApi = inject(ArchivosApi);

  private readonly logoId = signal<string | null>(null);

  /** URL del logo, o null si el local no tiene. */
  readonly logo = computed(() => urlDeArchivo(this.logoId()));

  /** Cambiarlo es del administrador: el servidor lo exige y la pantalla no se lo ofrece a nadie mas. */
  readonly puedeCambiar = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));

  constructor() {
    try {
      localStorage.removeItem(CLAVE_ANTIGUA);
    } catch {
      /* sin almacenamiento no hay nada que borrar */
    }

    // Cada sesion nueva lo vuelve a pedir: quien entra despues puede ser de otro turno.
    effect(() => {
      if (!this.sesion.sesion()) {
        this.logoId.set(null);
        return;
      }
      this.localApi.obtenerDatosLocal().subscribe({
        next: (datos) => this.logoId.set(datos.logoId ?? null),
        // Sin logo la barra muestra el nombre del local: no hay nada que avisar.
        error: () => this.logoId.set(null),
      });
    });
  }

  /**
   * Sube la imagen y la pone como logo. Devuelve el motivo si no se puede
   * subir, o null. Un fallo del servidor ya lo avisa el interceptor.
   */
  async cargar(archivo: File): Promise<string | null> {
    const problema = problemaDeImagen(archivo);
    if (problema) {
      return this.t(problema.clave, { kb: problema.kb });
    }

    try {
      const subido = await firstValueFrom(this.archivosApi.subirArchivo({ archivo }));
      if (!subido.id) return null;
      const datos = await firstValueFrom(this.localApi.cambiarLogoLocal({ archivoId: subido.id }));
      this.logoId.set(datos.logoId ?? null);
    } catch {
      /* el interceptor ya mostro el mensaje del servidor */
    }
    return null;
  }

  quitar(): void {
    this.localApi.quitarLogoLocal().subscribe({
      next: (datos) => this.logoId.set(datos.logoId ?? null),
    });
  }
}
