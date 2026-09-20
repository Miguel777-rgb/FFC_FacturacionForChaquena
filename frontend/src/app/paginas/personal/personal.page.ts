import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';

import { Icono } from '../../disenio/icono';
import { AsistenciaSeccion } from './asistencia.seccion';
import { DesempenoTrabajador } from './desempeno-trabajador';
import { TurnosSeccion } from './turnos.seccion';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';

import {
  CargosApi,
  RolesApi,
  TrabajadoresApi,
  type CargoResponseDto,
  type RolResponseDto,
  type TrabajadorResponseDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { problemaDe } from '../../nucleo/validacion/patrones';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';

/** Longitud minima que exige el servidor al restablecer una contrasena. */
const MINIMO_PASSWORD = 8;

/**
 * Que cajon esta abierto bajo una fila. Solo uno a la vez: tres paneles
 * desplegados en una tabla de veinte personas hacen perder de vista a quien se
 * estaba tocando.
 */
type PanelFila = 'ficha' | 'edicion' | 'password';

type Vista = 'personas' | 'turnos' | 'asistencia';

/**
 * Quien trabaja aqui, cuando le toca y si vino. Van en pestañas porque se miran
 * en momentos distintos: el horario se arma una vez por semana y la asistencia
 * se revisa cada dia.
 */
const VISTAS: ReadonlyArray<{ id: Vista; nombre: ClaveI18n }> = [
  { id: 'personas', nombre: 'personal.personas' },
  { id: 'turnos', nombre: 'personal.turnos' },
  { id: 'asistencia', nombre: 'personal.asistencia' },
];

/**
 * Personal: quien trabaja aqui, con que cargo, y que abre cada cargo.
 *
 * Tiene superficie propia porque administrar a las personas no se parece a nada
 * de lo que hay en el menu, el inventario o la configuracion. Alli se gestionan
 * cosas —stock, platillos, parametros—; aqui se reparten permisos, que es lo unico de esta aplicacion
 * que decide lo que los demas pueden hacer.
 *
 * El cargo no es un titulo decorativo: es lo que decide a que superficies entra
 * cada persona. Los `@PreAuthorize` del servidor piden roles —ADMIN, MOZO,
 * CAJA…—, y el puente entre uno y otro es la tabla `cargo_roles`. Por eso esta
 * pantalla administra las dos cosas juntas: dar de alta a alguien sin poder
 * revisar que abre su cargo es firmar un permiso a ciegas.
 *
 * Los roles no se crean desde aqui y no es un olvido. Un rol es la palabra que
 * aparece dentro de los `@PreAuthorize` del backend, asi que inventar
 * "SUPERVISOR" no abriria ninguna puerta: solo daria la impresion de haberla
 * abierto. El catalogo se lee; lo que se compone es el cargo.
 */
@Component({
  selector: 'app-personal',
  // El formulario de cargo se escribe una vez y se proyecta donde toque:
  // crear y editar son la misma operacion sobre el mismo objeto.
  imports: [NgTemplateOutlet, Icono, DesempenoTrabajador, TurnosSeccion, AsistenciaSeccion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './personal.page.html',
  styleUrls: ['../../disenio/secciones.scss', './personal.page.scss'],
})
export class PersonalPage implements OnInit {
  private readonly trabajadoresApi = inject(TrabajadoresApi);
  private readonly cargosApi = inject(CargosApi);
  private readonly rolesApi = inject(RolesApi);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;
  private readonly confirmacion = inject(ConfirmacionService);

  /** La plantilla lo escribe dentro del aviso de la contrasena nueva. */
  protected readonly MINIMO_PASSWORD = MINIMO_PASSWORD;

  protected readonly VISTAS = VISTAS;
  protected readonly vista = signal<Vista>('personas');

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);

  protected readonly trabajadores = signal<TrabajadorResponseDto[]>([]);
  protected readonly cargos = signal<CargoResponseDto[]>([]);
  protected readonly roles = signal<RolResponseDto[]>([]);

  // --- alta -----------------------------------------------------------------

  protected readonly altaAbierta = signal(false);
  protected readonly nombres = signal('');
  protected readonly apellidos = signal('');
  protected readonly dni = signal('');
  protected readonly celular = signal('');
  protected readonly correo = signal('');
  protected readonly username = signal('');
  protected readonly password = signal('');

  /**
   * Lo que cada campo tiene mal, por campo. Se calcula mientras se escribe,
   * pero el mensaje solo se pinta cuando el campo ya se toco: marcar en rojo lo
   * que todavia no se ha terminado de escribir es hostigar, no ayudar.
   */
  protected readonly tocados = signal<ReadonlySet<string>>(new Set());

  protected readonly errores = computed<Record<string, ClaveI18n | null>>(() => ({
    nombres: problemaDe('nombre', this.nombres()),
    apellidos: problemaDe('nombre', this.apellidos()),
    dni: problemaDe('dni', this.dni()),
    celular: problemaDe('celular', this.celular()),
    correo: problemaDe('correo', this.correo()),
    username: problemaDe('usuario', this.username()),
    password: problemaDe('contrasena', this.password()),
  }));

  /** El mensaje de un campo, solo si ya se toco. */
  protected errorDe(campo: string): string | null {
    const clave = this.errores()[campo];
    return clave && this.tocados().has(campo) ? this.t(clave) : null;
  }

  protected marcarTocado(campo: string): void {
    this.tocados.update((antes) => new Set(antes).add(campo));
  }

  private get hayErrores(): boolean {
    return Object.values(this.errores()).some((e) => e !== null);
  }

  protected readonly cargoId = signal<number | null>(null);

  // --- cargos ---------------------------------------------------------------

  /** Cargo que se esta componiendo: `0` es uno nuevo, un id es uno existente. */
  protected readonly cargoEnEdicion = signal<number | null>(null);
  protected readonly cargoNombre = signal('');
  protected readonly cargoDescripcion = signal('');
  protected readonly cargoRoles = signal<number[]>([]);

  // --- acciones de una fila --------------------------------------------------

  /** Persona con el cajon abierto, y que lleva dentro. */
  protected readonly filaAbierta = signal<string | null>(null);
  protected readonly panelFila = signal<PanelFila | null>(null);

  protected readonly passwordNueva = signal('');

  /** Campos de la ficha en edicion, tal como los pide el servidor. */
  protected readonly edicionNombres = signal('');
  protected readonly edicionApellidos = signal('');
  protected readonly edicionCorreo = signal('');
  protected readonly edicionCelular = signal('');
  protected readonly edicionCargoId = signal<number | null>(null);

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);

    // Los roles y los cargos caen por su cuenta: sin ellos la tabla de personas
    // se pinta igual, solo que sin poder decir que abre cada uno.
    forkJoin({
      trabajadores: this.trabajadoresApi.listarTrabajadores({
        pageable: { page: 0, size: 100, sort: ['username,asc'] },
      }),
      cargos: this.cargosApi.listarCargos().pipe(catchError(() => of([] as CargoResponseDto[]))),
      roles: this.rolesApi.listarRoles().pipe(catchError(() => of([] as RolResponseDto[]))),
    }).subscribe({
      next: ({ trabajadores, cargos, roles }) => {
        this.trabajadores.set(trabajadores.contenido ?? []);
        this.cargos.set(cargos);
        this.roles.set(roles);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /**
   * Los roles del cargo de una persona.
   *
   * Se busca por id y no por nombre: el nombre del cargo se puede cambiar desde
   * esta misma pantalla, y casar por texto dejaria la columna en blanco justo
   * despues de renombrarlo.
   */
  protected rolesDe(cargoId: number | undefined): string {
    const roles = this.cargos().find((c) => c.id === cargoId)?.rolesNombres ?? [];
    return roles.length > 0 ? roles.join(', ') : this.t('personal.sinRolCargo');
  }

  protected rolesDeCargo(cargo: CargoResponseDto): string {
    const roles = cargo.rolesNombres ?? [];
    return roles.length > 0 ? roles.join(', ') : this.t('personal.sinRolCargo');
  }

  /** El nombre entero, para los rotulos que lee un lector de pantalla. */
  protected nombreCompleto(trabajador: TrabajadorResponseDto): string {
    return `${trabajador.nombres ?? ''} ${trabajador.apellidos ?? ''}`.trim();
  }

  // --- alta -----------------------------------------------------------------

  protected abrirAlta(): void {
    this.altaAbierta.update((v) => !v);
    this.cargoId.set(this.cargos()[0]?.id ?? null);
  }

  protected anotarNombres(v: string): void {
    this.nombres.set(v);
  }

  protected anotarApellidos(v: string): void {
    this.apellidos.set(v);
  }

  protected anotarDni(v: string): void {
    this.dni.set(v);
  }

  protected anotarCelular(v: string): void {
    this.celular.set(v);
  }

  protected anotarCorreo(v: string): void {
    this.correo.set(v);
  }

  protected anotarUsername(v: string): void {
    this.username.set(v);
  }

  protected anotarPassword(v: string): void {
    this.password.set(v);
  }

  protected elegirCargo(v: string): void {
    this.cargoId.set(v === '' ? null : Number(v));
  }

  /**
   * El correo es opcional para el servidor, pero sin el la persona no puede
   * entrar con Google ni vincular su cuenta de Discord: las dos vias resuelven
   * la identidad por correo. Se pide igual y se avisa si se deja en blanco.
   */
  protected alta(): void {
    const cargoId = this.cargoId();
    const datos = {
      nombres: this.nombres().trim(),
      apellidos: this.apellidos().trim(),
      dni: this.dni().trim(),
      celular: this.celular().trim(),
      username: this.username().trim(),
      password: this.password(),
    };
    if (cargoId === null || Object.values(datos).some((v) => v.length === 0) || this.guardando()) {
      this.avisos.info(this.t('personal.avisoFaltanDatos'));
      return;
    }
    // El formato se comprueba aqui otra vez y no solo campo a campo: se puede
    // llegar al boton sin haber salido de un campo mal escrito.
    if (this.hayErrores) {
      this.tocados.set(new Set(Object.keys(this.errores())));
      this.avisos.error(this.t('validacion.revisa'));
      return;
    }

    this.guardando.set(true);
    this.trabajadoresApi
      .registrarTrabajador({
        registrarTrabajadorRequestDto: {
          ...datos,
          cargoId,
          correo: this.correo().trim() || undefined,
        },
      })
      .subscribe({
        next: (nuevo) => {
          this.guardando.set(false);
          this.altaAbierta.set(false);
          this.nombres.set('');
          this.apellidos.set('');
          this.dni.set('');
          this.celular.set('');
          this.correo.set('');
          this.username.set('');
          this.password.set('');
          this.tocados.set(new Set());
          this.avisos.exito(
            this.t('personal.avisoAlta', {
              nombre: this.nombreCompleto(nuevo),
              cargo: nuevo.cargoNombre ?? '',
            }),
          );
          if (!nuevo.correo) {
            this.avisos.info(this.t('personal.avisoSinCorreo'));
          }
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }

  /**
   * Dar de baja cierra las dos puertas: ni con usuario y contrasena ni con
   * Google. No borra a la persona, que sigue siendo el autor de todo lo que
   * registro.
   */
  protected async cambiarActivo(trabajador: TrabajadorResponseDto): Promise<void> {
    if (!trabajador.id || this.guardando()) return;

    // Reactivar no pide nada; dar de baja cierra el acceso y se confirma.
    if (trabajador.activo) {
      const confirmado = await this.confirmacion.pedir({
        titulo: this.t('personal.darDeBajaA', { nombre: this.nombreCompleto(trabajador) }),
        mensaje: this.t('personal.bajaMensaje'),
        confirmar: this.t('comun.darDeBaja'),
      });
      if (!confirmado || this.guardando()) return;
    }

    this.guardando.set(true);
    this.trabajadoresApi
      .cambiarActivoTrabajador({ id: trabajador.id, activo: !trabajador.activo })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }

  // --- contrasena -----------------------------------------------------------

  protected panelAbierto(trabajador: TrabajadorResponseDto, panel: PanelFila): boolean {
    return this.filaAbierta() === trabajador.id && this.panelFila() === panel;
  }

  /**
   * Abre o cierra un cajon bajo la fila. Pulsar el mismo icono lo cierra;
   * pulsar otro cambia de panel sin obligar a cerrar antes.
   */
  protected alternarPanel(trabajador: TrabajadorResponseDto, panel: PanelFila): void {
    if (this.panelAbierto(trabajador, panel)) {
      this.cerrarPanel();
      return;
    }
    this.filaAbierta.set(trabajador.id ?? null);
    this.panelFila.set(panel);
    this.passwordNueva.set('');

    if (panel === 'edicion') {
      this.edicionNombres.set(trabajador.nombres ?? '');
      this.edicionApellidos.set(trabajador.apellidos ?? '');
      this.edicionCorreo.set(trabajador.correo ?? '');
      this.edicionCelular.set(trabajador.celular ?? '');
      this.edicionCargoId.set(trabajador.cargoId ?? null);
    }
  }

  protected cerrarPanel(): void {
    this.filaAbierta.set(null);
    this.panelFila.set(null);
    this.passwordNueva.set('');
  }

  protected anotarPasswordNueva(v: string): void {
    this.passwordNueva.set(v);
  }

  protected anotarEdicionNombres(v: string): void {
    this.edicionNombres.set(v);
  }

  protected anotarEdicionApellidos(v: string): void {
    this.edicionApellidos.set(v);
  }

  protected anotarEdicionCorreo(v: string): void {
    this.edicionCorreo.set(v);
  }

  protected anotarEdicionCelular(v: string): void {
    this.edicionCelular.set(v);
  }

  protected elegirEdicionCargo(v: string): void {
    this.edicionCargoId.set(v === '' ? null : Number(v));
  }

  /**
   * Guarda los datos de una persona ya dada de alta.
   *
   * El usuario y el documento no se tocan: son las dos llaves con las que el
   * resto del sistema la identifica —el kardex, las comandas y el inicio de
   * sesion—, y el servidor no acepta cambiarlos. El cargo si, y es el cambio
   * que de verdad importa: mover a alguien de cargo le abre o le cierra
   * pantallas.
   */
  protected guardarEdicion(trabajador: TrabajadorResponseDto): void {
    const cargoId = this.edicionCargoId();
    const nombres = this.edicionNombres().trim();
    const apellidos = this.edicionApellidos().trim();

    if (!trabajador.id || cargoId === null || nombres.length === 0 || apellidos.length === 0) {
      this.avisos.info(this.t('personal.avisoObligatorios'));
      return;
    }
    if (this.guardando()) return;

    const cambiaDeCargo = trabajador.cargoId !== cargoId;

    this.guardando.set(true);
    this.trabajadoresApi
      .actualizarTrabajador({
        id: trabajador.id,
        actualizarTrabajadorRequestDto: {
          nombres,
          apellidos,
          cargoId,
          correo: this.edicionCorreo().trim() || undefined,
          celular: this.edicionCelular().trim() || undefined,
        },
      })
      .subscribe({
        next: (actualizado) => {
          this.guardando.set(false);
          this.cerrarPanel();
          this.avisos.exito(
            this.t('personal.avisoFichaGuardada', { nombre: this.nombreCompleto(actualizado) }),
          );
          if (cambiaDeCargo) {
            this.avisos.info(
              this.t('personal.avisoNuevoCargo', { cargo: actualizado.cargoNombre ?? '' }),
            );
          }
          this.cargar();
        },
        error: () => this.guardando.set(false),
      });
  }

  /**
   * Restablece la contrasena de otra persona. No pide la anterior porque el
   * caso real no es alguien cambiando la suya, sino administracion devolviendo
   * el acceso a quien lo perdio a mitad de turno; el endpoint solo lo permite a
   * un administrador por esa misma razon.
   */
  protected restablecerPassword(trabajador: TrabajadorResponseDto): void {
    const nueva = this.passwordNueva();
    if (!trabajador.id || this.guardando()) return;
    if (nueva.length < MINIMO_PASSWORD) {
      this.avisos.info(this.t('personal.avisoPasswordCorta', { min: MINIMO_PASSWORD }));
      return;
    }

    this.guardando.set(true);
    this.trabajadoresApi
      .restablecerPasswordTrabajador({
        id: trabajador.id,
        cambiarPasswordRequestDto: { passwordNueva: nueva },
      })
      .subscribe({
        next: () => {
          this.guardando.set(false);
          this.cerrarPanel();
          this.avisos.exito(
            this.t('personal.avisoPasswordCambiada', { usuario: trabajador.username ?? '' }),
          );
        },
        error: () => this.guardando.set(false),
      });
  }

  // --- cargos ---------------------------------------------------------------

  protected nuevoCargo(): void {
    if (this.cargoEnEdicion() === 0) {
      this.cerrarCargo();
      return;
    }
    this.cargoEnEdicion.set(0);
    this.cargoNombre.set('');
    this.cargoDescripcion.set('');
    this.cargoRoles.set([]);
  }

  protected editarCargo(c: CargoResponseDto): void {
    if (this.cargoEnEdicion() === c.id) {
      this.cerrarCargo();
      return;
    }
    this.cargoEnEdicion.set(c.id ?? null);
    this.cargoNombre.set(c.nombre ?? '');
    this.cargoDescripcion.set(c.descripcion ?? '');

    // El cargo llega con los nombres de sus roles, no con los ids; se cruzan
    // contra el catalogo para marcar las casillas.
    const nombres = new Set((c.rolesNombres ?? []).map((n) => n.toUpperCase()));
    this.cargoRoles.set(
      this.roles()
        .filter((r) => r.id != null && nombres.has((r.nombre ?? '').toUpperCase()))
        .map((r) => r.id as number),
    );
  }

  protected cerrarCargo(): void {
    this.cargoEnEdicion.set(null);
    this.cargoNombre.set('');
    this.cargoDescripcion.set('');
    this.cargoRoles.set([]);
  }

  protected anotarCargoNombre(v: string): void {
    this.cargoNombre.set(v);
  }

  protected anotarCargoDescripcion(v: string): void {
    this.cargoDescripcion.set(v);
  }

  protected tieneRol(rolId: number | undefined): boolean {
    return rolId != null && this.cargoRoles().includes(rolId);
  }

  protected alternarRol(rolId: number | undefined): void {
    if (rolId == null) return;
    this.cargoRoles.update((actuales) =>
      actuales.includes(rolId) ? actuales.filter((r) => r !== rolId) : [...actuales, rolId],
    );
  }

  /**
   * Guarda el cargo con exactamente los roles marcados.
   *
   * Se avisa de dos cosas que el servidor acepta sin rechistar y el local
   * lamenta despues. Un cargo sin ningun rol deja a esa persona dentro del
   * sistema y fuera de todas las pantallas. Y quitar un rol no expulsa a quien
   * ya inicio sesion: los roles viajan dentro del token, asi que el cambio se
   * nota cuando esa persona vuelve a entrar.
   */
  protected guardarCargo(): void {
    const id = this.cargoEnEdicion();
    const nombre = this.cargoNombre().trim();
    if (id === null || nombre.length === 0 || this.guardando()) {
      this.avisos.info(this.t('personal.avisoNombreCargo'));
      return;
    }

    const cuerpo = {
      nombre,
      descripcion: this.cargoDescripcion().trim() || undefined,
      rolIds: this.cargoRoles(),
    };

    this.guardando.set(true);
    const peticion =
      id === 0
        ? this.cargosApi.crearCargo({ crearCargoRequestDto: cuerpo })
        : this.cargosApi.actualizarCargo({ id, actualizarCargoRequestDto: cuerpo });

    peticion.subscribe({
      next: (c) => {
        this.guardando.set(false);
        this.cerrarCargo();

        if ((c.rolesNombres ?? []).length === 0) {
          this.avisos.info(this.t('personal.avisoCargoSinRoles', { cargo: c.nombre ?? '' }));
        } else {
          this.avisos.exito(
            this.t('personal.avisoCargoAbre', {
              cargo: c.nombre ?? '',
              roles: (c.rolesNombres ?? []).join(', '),
            }),
          );
        }
        if (id !== 0) {
          this.avisos.info(this.t('personal.avisoSesionAbierta'));
        }
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }
}
