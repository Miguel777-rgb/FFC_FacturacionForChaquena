import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs/operators';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { SesionService } from './sesion.service';

/** Media hora sin tocar nada cierra la sesion. */
export const LIMITE_MS = 30 * 60 * 1000;

/** Los ultimos cinco minutos se avisan con la cuenta atras a la vista. */
export const AVISO_MS = 5 * 60 * 1000;

/**
 * Pantallas que viven abiertas todo el turno sin que nadie las toque: la cola
 * de cocina y el tablero de reparto son paneles de pared. Cerrarles la sesion a
 * media noche de servicio dejaria la cocina sin comandas hasta que alguien se
 * acuerde de volver a entrar.
 */
const EXENTAS = ['/kds', '/despacho'];

/** Lo que cuenta como "sigo aqui". Pasivos: no bloquean el desplazamiento. */
const SENALES = ['pointerdown', 'keydown', 'wheel', 'touchstart'] as const;

/**
 * Cierra la sesion tras media hora sin actividad.
 *
 * Es para el terminal compartido: la caja y el POS se quedan abiertos entre
 * cliente y cliente, y quien se siente despues hereda la sesion del turno
 * anterior. Con esto, la hereda como mucho treinta minutos.
 *
 * No mide con un temporizador que se reinicia, sino con la marca de tiempo del
 * ultimo gesto: un navegador en segundo plano estrangula los intervalos, y si
 * la cuenta dependiera de cuantas veces corrio el intervalo, una pestana
 * dormida nunca llegaria al limite.
 */
@Injectable({ providedIn: 'root' })
export class InactividadService {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  private readonly ultimaSenal = signal(Date.now());
  private readonly ahora = signal(Date.now());
  private readonly exenta = signal(false);

  /** Milisegundos que faltan para el cierre; null cuando no corre la cuenta. */
  readonly restante = computed(() => {
    if (!this.sesion.autenticado() || this.exenta()) return null;
    return Math.max(0, LIMITE_MS - (this.ahora() - this.ultimaSenal()));
  });

  /** Segundos que faltan, ya en la franja del aviso; null el resto del tiempo. */
  readonly avisando = computed(() => {
    const restante = this.restante();
    return restante !== null && restante <= AVISO_MS ? Math.ceil(restante / 1000) : null;
  });

  constructor() {
    const destruir = inject(DestroyRef);

    for (const senal of SENALES) {
      const anotar = () => this.sigoAqui();
      document.addEventListener(senal, anotar, { passive: true });
      destruir.onDestroy(() => document.removeEventListener(senal, anotar));
    }

    const reloj = setInterval(() => this.revisar(), 1000);
    destruir.onDestroy(() => clearInterval(reloj));

    this.mirarRuta(this.router.url);
    this.router.events
      .pipe(
        filter((e): e is NavigationEnd => e instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe((e) => {
        this.mirarRuta(e.urlAfterRedirects);
        // Navegar es actividad, y ademas evita que al salir de una pantalla
        // exenta la cuenta arranque ya vencida.
        this.sigoAqui();
      });
  }

  /** «Sigo aqui»: lo llama cada gesto y el boton del aviso. */
  sigoAqui(): void {
    this.ultimaSenal.set(Date.now());
  }

  private mirarRuta(url: string): void {
    this.exenta.set(EXENTAS.some((ruta) => url.startsWith(ruta)));
  }

  private revisar(): void {
    this.ahora.set(Date.now());
    if (this.restante() !== 0) return;

    const volverA = this.router.url;
    this.sesion.cerrar();
    this.sigoAqui();
    void this.router.navigate(['/entrar'], {
      queryParams: { volverA, motivo: 'inactividad' },
      replaceUrl: true,
    });
  }
}
