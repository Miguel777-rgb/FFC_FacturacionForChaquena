import { Component, ChangeDetectionStrategy, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';

import { AutenticacionApi, type AuthResponseDto } from '../../api';
import { mensajeDe } from '../../nucleo/http/errores.interceptor';
import { SesionService } from '../../nucleo/sesion/sesion.service';
import { GoogleService } from '../../nucleo/sesion/google.service';
import { INICIO_POR_ROL } from '../../nucleo/sesion/rol';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
})
export class LoginPage {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AutenticacionApi);
  private readonly sesion = inject(SesionService);
  protected readonly google = inject(GoogleService);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly contrasenaVisible = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    usernameOrEmail: ['', [Validators.required]],
    password: ['', [Validators.required]],
  });

  /** Mostrar/ocultar la contrasena en claro mientras se escribe. */
  protected alternarVisibilidad(): void {
    this.contrasenaVisible.update((v) => !v);
  }

  protected entrar(): void {
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    this.auth.login({ loginRequestDto: this.formulario.getRawValue() }).subscribe({
      next: (respuesta) => this.entrarConRespuesta(respuesta),
      // El interceptor no muestra cartel para el 401 sin sesion previa: en una
      // pantalla de login el error va en la propia pantalla.
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(this.textoDeError(e, 'No se pudo iniciar sesion. Intenta de nuevo.'));
      },
    });
  }

  /**
   * Entrar con Google. El token lo emite Google, no esta API: se pide en el
   * navegador y se canjea en `/api/v1/auth/google`, que lo verifica y devuelve
   * el JWT propio del backend.
   *
   * Google solo identifica; no da de alta. Un correo que no corresponde a
   * ningun trabajador se rechaza con 401, y el mensaje del servidor —que dice
   * que hay que pedirle el alta a un administrador— es el que se muestra.
   */
  protected async entrarConGoogle(): Promise<void> {
    if (this.enviando()) return;

    this.enviando.set(true);
    this.error.set(null);

    let accessToken: string;
    try {
      accessToken = await this.google.pedirAccessToken();
    } catch (e: unknown) {
      this.enviando.set(false);
      this.error.set(e instanceof Error ? e.message : 'No se pudo entrar con Google.');
      return;
    }

    this.auth.autenticarConGoogle({ authorization: `Bearer ${accessToken}` }).subscribe({
      next: (respuesta) => this.entrarConRespuesta(respuesta),
      error: (e: unknown) => {
        this.enviando.set(false);
        this.error.set(this.textoDeError(e, 'No se pudo entrar con Google.'));
      },
    });
  }

  /** Abre la sesion y navega. Comun a las dos vias de entrada. */
  private entrarConRespuesta(respuesta: AuthResponseDto): void {
    if (!respuesta.token) {
      this.enviando.set(false);
      this.error.set('El servidor no devolvio un token. Avisa al administrador.');
      return;
    }

    const nombre = [respuesta.nombres, respuesta.apellidos].filter(Boolean).join(' ');
    this.sesion.abrir(respuesta.token, nombre);

    // Si la guarda lo mando aqui desde otra pantalla, se vuelve alli.
    const volverA = this.ruta.snapshot.queryParamMap.get('volverA');
    const primerRol = this.sesion.roles()[0];
    const destino = volverA ?? (primerRol ? INICIO_POR_ROL[primerRol] : '/inicio');

    void this.router.navigateByUrl(destino);
  }

  /**
   * Muestra el texto del servidor en vez de deducirlo del codigo. Antes se
   * deducia, y el mapeo quedo al reves cuando el backend empezo a distinguir la
   * cuenta dada de baja de la contrasena incorrecta.
   */
  private textoDeError(e: unknown, porDefecto: string): string {
    if (!(e instanceof HttpErrorResponse)) return porDefecto;
    if (e.status === 0) return 'Sin conexion con el servidor. Revisa la red y vuelve a intentar.';
    return mensajeDe(e, porDefecto);
  }
}
