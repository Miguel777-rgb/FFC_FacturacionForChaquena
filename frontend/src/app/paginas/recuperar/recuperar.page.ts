import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AutenticacionRecuperacionApi } from '../../api/api/autenticacion-recuperacion.api';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { PATRONES } from '../../nucleo/validacion/patrones';

/**
 * «Olvidé mi contraseña»: se pide el correo y el servidor manda un enlace.
 *
 * La respuesta es la misma exista o no ese correo, y la pantalla lo dice con
 * esas palabras —«si ese correo pertenece a una cuenta»—. No es vaguedad: si
 * contestara «no existe», este formulario serviría para averiguar quién tiene
 * cuenta en el sistema probando una lista de direcciones.
 */
@Component({
  selector: 'app-recuperar',
  imports: [ReactiveFormsModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './recuperar.page.html',
  styleUrl: '../login/login.page.scss',
})
export class RecuperarPage {
  private readonly fb = inject(FormBuilder);
  private readonly api = inject(AutenticacionRecuperacionApi);
  private readonly router = inject(Router);

  protected readonly t = inject(I18nService).t;

  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);

  protected readonly formulario = this.fb.nonNullable.group({
    correo: ['', [Validators.required, Validators.pattern(PATRONES.correo)]],
  });

  protected get correoInvalido(): boolean {
    const campo = this.formulario.controls.correo;
    return campo.touched && campo.invalid;
  }

  protected pedir(): void {
    if (this.formulario.invalid || this.enviando()) {
      this.formulario.markAllAsTouched();
      return;
    }

    this.enviando.set(true);
    this.api
      .solicitarRecuperacion({
        solicitarRecuperacionRequestDto: { correo: this.formulario.controls.correo.value.trim() },
      })
      .subscribe({
        // El mismo desenlace en los dos casos, tambien si el servidor falla:
        // lo contrario contaria por la puerta de atras lo que el 202 calla.
        next: () => this.acabar(),
        error: () => this.acabar(),
      });
  }

  private acabar(): void {
    this.enviando.set(false);
    this.enviado.set(true);
  }

  protected volver(): void {
    void this.router.navigateByUrl('/entrar');
  }
}
