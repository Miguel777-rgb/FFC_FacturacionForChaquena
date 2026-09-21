import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AutenticacionRecuperacionApi } from '../../api/api/autenticacion-recuperacion.api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { PATRONES } from '../../nucleo/validacion/patrones';

/**
 * La pantalla a la que lleva el enlace del correo: elegir contraseña nueva.
 *
 * El token viaja en la URL y no se enseña ni se guarda en ninguna parte del
 * navegador: se manda con la contraseña y se olvida. Al terminar se va al
 * acceso, porque el servidor no abre sesión aquí; cambiar la contraseña y
 * entrar con ella son dos pasos a propósito.
 */
@Component({
  selector: 'app-restablecer',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './restablecer.page.html',
  styleUrl: '../login/login.page.scss',
})
export class RestablecerPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AutenticacionRecuperacionApi);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;

  private readonly token = this.ruta.snapshot.queryParamMap.get('token') ?? '';
  protected readonly sinToken = computed(() => this.token.trim().length === 0);

  protected readonly enviando = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly formulario = this.fb.nonNullable.group({
    password: ['', [Validators.required, Validators.pattern(PATRONES.contrasena)]],
    repetida: ['', [Validators.required]],
  });

  protected get passwordInvalida(): boolean {
    const campo = this.formulario.controls.password;
    return campo.touched && campo.invalid;
  }

  /** No basta con que la contraseña valga: hay que haberla escrito dos veces igual. */
  protected get noCoinciden(): boolean {
    const { password, repetida } = this.formulario.controls;
    return repetida.touched && repetida.value.length > 0 && repetida.value !== password.value;
  }

  protected guardar(): void {
    if (this.formulario.invalid || this.noCoinciden || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.error.set(null);
    this.api
      .restablecerPasswordConToken({
        restablecerPasswordRequestDto: {
          token: this.token,
          password: this.formulario.controls.password.value,
        },
      })
      .subscribe({
        next: () => {
          this.enviando.set(false);
          this.avisos.exito(this.t('restablecer.listo'));
          void this.router.navigateByUrl('/entrar', { replaceUrl: true });
        },
        error: (e: unknown) => {
          this.enviando.set(false);
          // El servidor explica si el enlace caducó, si ya se usó o si no vale;
          // repetirlo aquí en otras palabras solo confundiría.
          const mensaje = (e as { error?: { message?: string } })?.error?.message;
          this.error.set(mensaje ?? this.t('restablecer.errorGenerico'));
        },
      });
  }
}
