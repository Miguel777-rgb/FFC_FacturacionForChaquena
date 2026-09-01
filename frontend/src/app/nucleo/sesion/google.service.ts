import { Injectable, signal } from '@angular/core';

import { environment } from '../../../environments/environment';

/**
 * Trozo de Google Identity Services que se usa aqui. Se declara a mano en vez
 * de instalar @types/google.accounts: es una superficie de tres campos y no
 * merece una dependencia mas en el arbol.
 */
interface RespuestaToken {
  access_token?: string;
  error?: string;
  error_description?: string;
}

interface ClienteToken {
  requestAccessToken(): void;
}

interface GoogleGlobal {
  accounts: {
    oauth2: {
      initTokenClient(config: {
        client_id: string;
        scope: string;
        callback: (respuesta: RespuestaToken) => void;
        error_callback?: (error: { type?: string }) => void;
      }): ClienteToken;
    };
  };
}

declare const google: GoogleGlobal | undefined;

/**
 * Consigue un access token de Google para canjearlo en `/api/v1/auth/google`.
 *
 * El backend NO es un servidor OAuth: quien emite el token es Google, y el
 * backend solo lo verifica —comprueba que la audiencia sea su propio client id—
 * y a cambio devuelve su JWT. Por eso aqui se pide un **access token** y no un
 * ID token: el backend lo valida contra `oauth2.googleapis.com/tokeninfo`, que
 * es el unico endpoint que devuelve la audiencia.
 *
 * `scope` lleva `email` porque el backend busca al trabajador por su correo;
 * sin ese permiso `tokeninfo` no lo devuelve y el inicio de sesion se rechaza.
 *
 * NO lleva `openid`, aunque sea lo habitual en otros flujos de Google.
 * `initTokenClient` usa el flujo implicito (`response_type=token`), y el
 * endpoint de Google rechaza `openid` ahi: es un scope de OpenID Connect y
 * exige `response_type=id_token` o `code`. Pedirlo devuelve un 400 generico
 * («the server cannot process the request because it is malformed») que no
 * dice cual de los parametros sobra.
 */
@Injectable({ providedIn: 'root' })
export class GoogleService {
  /**
   * Falso cuando no hay client id configurado o la libreria no cargo. La
   * pantalla de login esconde el boton en ese caso: es preferible no ofrecerlo
   * a ofrecer uno que falla al pulsarlo.
   */
  readonly disponible = signal(false);

  private readonly clientId = environment.googleClientId;

  constructor() {
    if (!this.clientId) return;
    // El script va con `defer`, asi que puede no estar listo cuando Angular
    // arranca. Se comprueba unas cuantas veces y se abandona sin ruido.
    let intentos = 0;
    const mirar = () => {
      if (typeof google !== 'undefined' && google?.accounts?.oauth2) {
        this.disponible.set(true);
      } else if (++intentos < 40) {
        setTimeout(mirar, 100);
      }
    };
    mirar();
  }

  /** Abre el dialogo de Google y resuelve con el access token. */
  pedirAccessToken(): Promise<string> {
    return new Promise((resolver, rechazar) => {
      if (typeof google === 'undefined' || !google?.accounts?.oauth2) {
        rechazar(new Error('No se pudo cargar el inicio de sesion de Google.'));
        return;
      }

      const cliente = google.accounts.oauth2.initTokenClient({
        client_id: this.clientId,
        scope: 'email profile',
        callback: (respuesta) => {
          if (respuesta.access_token) {
            resolver(respuesta.access_token);
          } else {
            rechazar(
              new Error(respuesta.error_description ?? 'Google no devolvio ningun token.'),
            );
          }
        },
        // Se dispara cuando la persona cierra la ventana o el navegador bloquea
        // el emergente. No es un fallo que merezca un cartel de error rojo.
        error_callback: (error) => {
          rechazar(
            new Error(
              error.type === 'popup_closed'
                ? 'Cancelaste el inicio de sesion con Google.'
                : 'No se pudo abrir la ventana de Google. Revisa el bloqueo de emergentes.',
            ),
          );
        },
      });

      cliente.requestAccessToken();
    });
  }
}
