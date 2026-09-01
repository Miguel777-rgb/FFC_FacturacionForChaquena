import { Injectable, computed, signal } from '@angular/core';

import { normalizarRol, type Rol, esRol } from './rol';

/**
 * Claims que el backend mete en el JWT. `cargo` es el que siempre viene;
 * `roles` y `permisos` los lee `JwtAuthenticationFilter` si estan presentes.
 */
interface ClaimsToken {
  /** El backend firma con el correo como subject, no con el username. */
  sub?: string;
  username?: string;
  nombres?: string;
  apellidos?: string;
  /**
   * El puesto: "ADMINISTRADOR", "JEFE DE COCINA", "CAJERO". No es el rol de
   * autorizacion, aunque en MOZO coincidan por casualidad.
   */
  cargo?: string;
  /**
   * Los roles de verdad, que salen de la tabla cargo_roles: ADMIN, COCINA,
   * CAJA... Son los que evaluan los `@PreAuthorize`.
   */
  roles?: string[];
  permisos?: string[];
  exp?: number;
}

export interface Sesion {
  token: string;
  correo: string;
  username: string;
  nombre: string;
  cargo: string;
  roles: Rol[];
  permisos: string[];
  expiraEn: Date | null;
}

const CLAVE = 'chaquena.sesion';

/**
 * Guarda la sesion y responde quien es el usuario.
 *
 * El token se **decodifica, nunca se verifica**. El backend lo firma con HMAC
 * simetrico (`JwtService` usa `Keys.hmacShaKeyFor`), asi que el secreto no
 * esta —ni puede estar— en el navegador. Lo que sale de aqui sirve para
 * decidir que pintar; quien decide que se puede hacer son los 69
 * `@PreAuthorize` del servidor.
 *
 * Se persiste en `sessionStorage` y no en `localStorage`: en un POS compartido,
 * cerrar la pestaña tiene que cerrar el turno.
 */
@Injectable({ providedIn: 'root' })
export class SesionService {
  private readonly _sesion = signal<Sesion | null>(this.leerDeStorage());

  readonly sesion = this._sesion.asReadonly();
  readonly autenticado = computed(() => this._sesion() !== null);
  readonly roles = computed<Rol[]>(() => this._sesion()?.roles ?? []);
  readonly nombre = computed(() => this._sesion()?.nombre ?? '');

  /**
   * Lo lee el cliente generado a traves de `credentials.bearerAuth`. Devuelve
   * `undefined` cuando no hay sesion para que la peticion salga sin cabecera
   * en vez de mandar la cadena "undefined".
   */
  readonly tokenActual = (): string | undefined => this._sesion()?.token;

  tieneAlgunRol(permitidos: readonly Rol[]): boolean {
    if (permitidos.length === 0) return true;
    const mios = this.roles();
    return permitidos.some((r) => mios.includes(r));
  }

  /** Guarda la sesion a partir de la respuesta de `/api/v1/auth/login`. */
  abrir(token: string, nombreLegible?: string): void {
    const claims = this.decodificar(token);
    if (!claims) throw new Error('El servidor devolvio un token que no se puede leer.');

    const delToken = [claims.nombres, claims.apellidos].filter(Boolean).join(' ').trim();

    this._sesion.set({
      token,
      correo: claims.sub ?? '',
      username: claims.username ?? '',
      nombre: nombreLegible?.trim() || delToken || claims.username || claims.sub || '',
      cargo: claims.cargo ?? '',
      roles: this.extraerRoles(claims),
      permisos: (claims.permisos ?? []).map(normalizarRol),
      expiraEn: claims.exp ? new Date(claims.exp * 1000) : null,
    });
    this.escribirEnStorage();
  }

  cerrar(): void {
    this._sesion.set(null);
    try {
      sessionStorage.removeItem(CLAVE);
    } catch {
      /* modo privado o almacenamiento bloqueado: la sesion en memoria ya se limpio */
    }
  }

  /** Vencido tambien cuenta como no autenticado, sin esperar a un 401. */
  expirada(): boolean {
    const expira = this._sesion()?.expiraEn;
    return expira !== null && expira !== undefined && expira.getTime() <= Date.now();
  }

  /**
   * Reproduce como el backend arma las autoridades: el `cargo` cuenta como rol,
   * y ademas se suman los de la lista `roles`. Lo que no sea uno de los seis
   * roles conocidos se descarta.
   *
   * El descarte importa: los cargos sembrados se llaman ADMINISTRADOR, CAJERO
   * o ALMACENERO, mientras que los `@PreAuthorize` piden ADMIN, CAJA y ALMACEN.
   * El puente lo hace la tabla cargo_roles, que llega en el claim `roles`. Un
   * cargo sin rol asociado —los hay— deja al usuario sin ninguno, y entonces
   * ve la pantalla de sin permiso en vez de una superficie que le daria 403.
   */
  private extraerRoles(claims: ClaimsToken): Rol[] {
    const candidatos = [claims.cargo, ...(claims.roles ?? [])]
      .filter((v): v is string => typeof v === 'string' && v.length > 0)
      .map(normalizarRol)
      .filter(esRol);
    return [...new Set(candidatos)];
  }

  /** Decodifica el payload sin validar la firma. Devuelve null si no es un JWT. */
  private decodificar(token: string): ClaimsToken | null {
    const partes = token.split('.');
    if (partes.length !== 3) return null;
    try {
      const base64 = partes[1].replace(/-/g, '+').replace(/_/g, '/');
      const relleno = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
      const texto = new TextDecoder().decode(
        Uint8Array.from(atob(relleno), (c) => c.charCodeAt(0)),
      );
      return JSON.parse(texto) as ClaimsToken;
    } catch {
      return null;
    }
  }

  private leerDeStorage(): Sesion | null {
    let crudo: string | null = null;
    try {
      crudo = sessionStorage.getItem(CLAVE);
    } catch {
      return null;
    }
    if (!crudo) return null;

    try {
      const guardada = JSON.parse(crudo) as Sesion & { expiraEn: string | null };
      const sesion: Sesion = {
        ...guardada,
        expiraEn: guardada.expiraEn ? new Date(guardada.expiraEn) : null,
      };
      // Un token vencido en el almacenamiento no sirve para nada.
      if (sesion.expiraEn && sesion.expiraEn.getTime() <= Date.now()) return null;
      return sesion;
    } catch {
      return null;
    }
  }

  private escribirEnStorage(): void {
    try {
      sessionStorage.setItem(CLAVE, JSON.stringify(this._sesion()));
    } catch {
      /* sin almacenamiento la sesion vive solo en memoria: la app sigue funcionando */
    }
  }
}
