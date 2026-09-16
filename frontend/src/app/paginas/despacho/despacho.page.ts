import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, of } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';

import {
  ComandasApi,
  DespachoRepartoApi,
  DespachoTransportistasApi,
  OrdenResumenDtoEstadoEnum,
  OrdenResumenDtoTipoOrdenEnum,
  OrdenResumenDtoTransicionesPermitidasEnum,
  VehiculoRequestDtoTipoVehiculoEnum,
  type DeliveryInfoDto,
  type OrdenResumenDto,
  type TransportistaResponseDto,
  type VehiculoResponseDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import { TiempoRealService } from '../../nucleo/tiempo-real/tiempo-real.service';
import { formatearDuracion } from '../../nucleo/i18n/formatos';
import { ConfirmacionService } from '../../nucleo/confirmacion/confirmacion.service';
import { EnVivo } from '../../disenio/en-vivo';
import { Icono } from '../../disenio/icono';

/** Un mostrador con movimiento constante: sin tiempo real, la lista se refresca sola. */
const REFRESCO_MS = 20_000;

const TIPOS_VEHICULO = VehiculoRequestDtoTipoVehiculoEnum;

/**
 * Despacho: el tramo del reparto que ocurre dentro del local.
 *
 * El reparto lo opera una empresa externa. El restaurante no reparte y no
 * sigue al repartidor: lo que hace aqui es **reconocer al conductor** —quien
 * es, su documento y con que vehiculo llego—, entregarle la comanda y cerrar
 * la entrega cuando el cliente le dicta su codigo.
 *
 * Por eso esta pantalla no tiene mapa, ni posicion, ni tiempo estimado de
 * llegada, ni metricas del conductor: ese dato pertenece a la empresa de
 * reparto y el local no lo tiene. Pintarlo seria inventarlo.
 */
@Component({
  selector: 'app-despacho',
  imports: [Icono, EnVivo],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './despacho.page.html',
  styleUrl: './despacho.page.scss',
})
export class DespachoPage implements OnInit {
  private readonly comandasApi = inject(ComandasApi);
  private readonly repartoApi = inject(DespachoRepartoApi);
  private readonly transportistasApi = inject(DespachoTransportistasApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);
  private readonly confirmacion = inject(ConfirmacionService);
  protected readonly duracion = formatearDuracion;

  protected readonly t = this.i18n.t;
  protected readonly tp = this.i18n.tp;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly TIPOS_VEHICULO = Object.values(TIPOS_VEHICULO);

  protected readonly cargando = signal(true);
  protected readonly ocupada = signal<string | null>(null);

  protected readonly comandas = signal<OrdenResumenDto[]>([]);
  protected readonly enRuta = signal<DeliveryInfoDto[]>([]);
  protected readonly conductores = signal<TransportistaResponseDto[]>([]);

  /** Conductor y vehiculo elegidos para cada comanda, por id de comanda. */
  protected readonly conductorPara = signal<Record<string, string>>({});
  protected readonly vehiculoPara = signal<Record<string, string>>({});

  /** Codigo que el cliente le dicta al conductor, por id de comanda. */
  protected readonly codigos = signal<Record<string, string>>({});

  // --- alta de conductores y vehiculos --------------------------------------

  protected readonly altaAbierta = signal(false);
  protected readonly nombres = signal('');
  protected readonly apellidos = signal('');
  protected readonly dni = signal('');
  protected readonly celular = signal('');
  protected readonly empresa = signal('');

  /** Conductor al que se le esta anotando un vehiculo. */
  protected readonly altaVehiculoPara = signal<string | null>(null);
  protected readonly placa = signal('');
  protected readonly marcaModelo = signal('');
  protected readonly tipoVehiculo = signal<VehiculoRequestDtoTipoVehiculoEnum>(TIPOS_VEHICULO.MOTO);

  // --- derivados ------------------------------------------------------------

  /**
   * Comandas a domicilio que todavia no salieron. El tablero solo devuelve las
   * ya despachadas, asi que estas se sacan de las comandas abiertas.
   *
   * `GET /ordenes/activas` llega hasta ENTREGADO, de modo que incluye tambien
   * las que ya se llevo un conductor. Se filtran por estado: sin esto la misma
   * comanda aparece a la vez esperando en el mostrador y en manos de alguien.
   */
  protected readonly esperando = computed(() =>
    this.comandas().filter(
      (o) =>
        o.tipoOrden === OrdenResumenDtoTipoOrdenEnum.DELIVERY &&
        (o.estado === OrdenResumenDtoEstadoEnum.ENCOLADO ||
          o.estado === OrdenResumenDtoEstadoEnum.EN_PREPARACION),
    ),
  );

  protected readonly hayConductores = computed(() =>
    this.conductores().some((c) => c.activo && (c.vehiculos?.length ?? 0) > 0),
  );

  constructor() {
    // Se repinta con cada aviso del servidor; si el tiempo real se cae, cada
    // REFRESCO_MS como antes.
    inject(TiempoRealService)
      .cambios(['REPARTO'], REFRESCO_MS)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.cargar(true));
  }

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(silencioso = false): void {
    if (!silencioso) this.cargando.set(true);

    forkJoin({
      comandas: this.comandasApi.activas(),
      enRuta: this.repartoApi.tablero(),
      // Hacen falta las dos listas, y no es redundancia.
      //
      // `listarTransportistas` trae a todos —dar de baja a alguien no debe
      // hacerlo desaparecer de la libreta, o no habria forma de reactivarlo
      // cuando la empresa lo mande otra vez— pero omite los vehiculos a
      // proposito, para no disparar una consulta por fila.
      //
      // `listarTransportistasActivos` si los trae, que es justo lo que hace
      // falta para poder asignar. Se combinan por id.
      conductores: this.transportistasApi
        .listarTransportistas({ pageable: { page: 0, size: 100, sort: ['nombres,asc'] } })
        .pipe(catchError(() => of({ contenido: [] as TransportistaResponseDto[] }))),
      conVehiculos: this.transportistasApi
        .listarTransportistasActivos()
        .pipe(catchError(() => of([] as TransportistaResponseDto[]))),
    }).subscribe({
      next: ({ comandas, enRuta, conductores, conVehiculos }) => {
        this.comandas.set(comandas);
        this.enRuta.set(enRuta);

        const flota = new Map(conVehiculos.map((c) => [c.id, c.vehiculos ?? []]));
        this.conductores.set(
          (conductores.contenido ?? []).map((c) => ({ ...c, vehiculos: flota.get(c.id) })),
        );
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  // --- entregar la comanda al conductor -------------------------------------

  protected elegirConductor(ordenId: string, conductorId: string): void {
    this.conductorPara.update((m) => ({ ...m, [ordenId]: conductorId }));
    // El vehiculo tiene que ser de ese conductor: el servidor lo rechaza si no,
    // asi que al cambiar de conductor se suelta el vehiculo anterior.
    this.vehiculoPara.update((m) => ({ ...m, [ordenId]: '' }));
  }

  protected elegirVehiculo(ordenId: string, vehiculoId: string): void {
    this.vehiculoPara.update((m) => ({ ...m, [ordenId]: vehiculoId }));
  }

  protected vehiculosDe(conductorId: string | undefined): VehiculoResponseDto[] {
    if (!conductorId) return [];
    return this.conductores().find((c) => c.id === conductorId)?.vehiculos ?? [];
  }

  /**
   * La comanda ya esta lista y el conductor esta en el mostrador: se anota
   * quien se la lleva y sale, en un solo gesto.
   *
   * Son dos llamadas encadenadas porque el backend separa reconocer al
   * conductor de darle la comanda, pero en el mostrador es un unico momento y
   * partirlo en dos botones solo invita a dejar comandas asignadas que nunca
   * salieron.
   */
  protected entregarAlConductor(orden: OrdenResumenDto): void {
    const ordenId = orden.id;
    if (!ordenId || this.ocupada()) return;

    const transportistaId = this.conductorPara()[ordenId];
    const vehiculoId = this.vehiculoPara()[ordenId];
    if (!transportistaId || !vehiculoId) return;

    this.ocupada.set(ordenId);
    this.repartoApi
      .asignar({ ordenId, asignarDeliveryRequestDto: { transportistaId, vehiculoId } })
      .pipe(switchMap(() => this.repartoApi.despachar({ ordenId })))
      .subscribe({
        next: (info) => {
          this.ocupada.set(null);
          this.avisos.exito(
            this.t('despacho.avisoEntregada', {
              conductor: info.transportistaNombre ?? this.t('despacho.conductorGenerico'),
              placa: info.placaVehiculo ?? '—',
            }),
          );
          this.cargar(true);
        },
        error: () => this.ocupada.set(null),
      });
  }

  /**
   * Solo se ofrece salir cuando la maquina de estados lo permite: una comanda
   * que cocina todavia no termino no puede irse con nadie.
   */
  protected puedeSalir(orden: OrdenResumenDto): boolean {
    return (
      orden.transicionesPermitidas?.includes(
        OrdenResumenDtoTransicionesPermitidasEnum.EN_DESPACHO,
      ) ?? false
    );
  }

  protected listaParaSalir(orden: OrdenResumenDto): boolean {
    const id = orden.id ?? '';
    return (
      this.puedeSalir(orden) &&
      !!this.conductorPara()[id] &&
      !!this.vehiculoPara()[id] &&
      this.ocupada() === null
    );
  }

  // --- cerrar la entrega ----------------------------------------------------

  protected anotarCodigo(ordenId: string, codigo: string): void {
    this.codigos.update((m) => ({ ...m, [ordenId]: codigo }));
  }

  /**
   * El codigo lo dicta el cliente al conductor y el conductor lo repite aqui.
   * Es la unica prueba de que la comanda llego a su destinatario, y por eso el
   * repartidor —que es de fuera— nunca lo ve en pantalla antes de que se lo
   * digan.
   */
  protected cerrarEntrega(info: DeliveryInfoDto): void {
    const ordenId = info.ordenId;
    if (!ordenId || this.ocupada()) return;

    const codigo = (this.codigos()[ordenId] ?? '').trim();
    if (codigo.length === 0) return;

    this.ocupada.set(ordenId);
    this.repartoApi.verificarOtp({ ordenId, verificarOtpRequestDto: { codigo } }).subscribe({
      next: () => {
        this.ocupada.set(null);
        this.codigos.update((m) => ({ ...m, [ordenId]: '' }));
        this.avisos.exito(this.t('despacho.avisoConfirmada'));
        this.cargar(true);
      },
      error: () => this.ocupada.set(null),
    });
  }

  /** Si el cliente perdio el mensaje, se le vuelve a mandar por su canal. */
  protected reenviarCodigo(info: DeliveryInfoDto): void {
    const ordenId = info.ordenId;
    if (!ordenId || this.ocupada()) return;

    this.ocupada.set(ordenId);
    this.repartoApi.reenviarOtp({ ordenId }).subscribe({
      next: (respuesta) => {
        this.ocupada.set(null);
        this.avisos.info(respuesta.mensaje ?? this.t('despacho.avisoReenviado'));
      },
      error: () => this.ocupada.set(null),
    });
  }

  /** Minutos desde que salio del local. Es un dato nuestro, no del conductor. */
  protected desdeQueSalio(info: DeliveryInfoDto): number | null {
    if (!info.horaDespacho) return null;
    const salida = Date.parse(info.horaDespacho);
    if (Number.isNaN(salida)) return info.minutosEnRuta ?? null;
    return Math.max(0, Math.floor((Date.now() - salida) / 60_000));
  }

  // --- libreta de conductores -----------------------------------------------

  protected abrirAlta(): void {
    this.altaAbierta.update((v) => !v);
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

  protected anotarEmpresa(v: string): void {
    this.empresa.set(v);
  }

  /**
   * Alta de un conductor de la empresa de reparto. Se guarda lo justo para
   * reconocerlo: nombre, documento, telefono y de que empresa viene. No es una
   * ficha de empleado; esta persona no trabaja aqui.
   */
  protected altaConductor(): void {
    if (this.ocupada()) return;
    const dni = this.dni().trim();
    const nombres = this.nombres().trim();
    const apellidos = this.apellidos().trim();
    const celular = this.celular().trim();
    const empresaTransporte = this.empresa().trim();
    if (!dni || !nombres || !apellidos || !celular || !empresaTransporte) {
      this.avisos.info(this.t('despacho.avisoFaltanDatos'));
      return;
    }

    this.ocupada.set('alta');
    this.transportistasApi
      .crearTransportista({
        transportistaRequestDto: { nombres, apellidos, dni, celular, empresaTransporte },
      })
      .subscribe({
        next: (nuevo) => {
          this.ocupada.set(null);
          this.avisos.exito(
            this.t('despacho.avisoConductorAnotado', {
              nombre: nuevo.nombreCompleto ?? nombres,
            }),
          );
          this.nombres.set('');
          this.apellidos.set('');
          this.dni.set('');
          this.celular.set('');
          this.empresa.set('');
          this.altaAbierta.set(false);
          this.altaVehiculoPara.set(nuevo.id ?? null);
          this.cargar(true);
        },
        error: () => this.ocupada.set(null),
      });
  }

  protected abrirAltaVehiculo(conductorId: string): void {
    this.altaVehiculoPara.set(this.altaVehiculoPara() === conductorId ? null : conductorId);
    this.placa.set('');
    this.marcaModelo.set('');
  }

  protected anotarPlaca(v: string): void {
    this.placa.set(v.toUpperCase());
  }

  protected anotarMarcaModelo(v: string): void {
    this.marcaModelo.set(v);
  }

  protected elegirTipoVehiculo(v: string): void {
    this.tipoVehiculo.set(v as VehiculoRequestDtoTipoVehiculoEnum);
  }

  /**
   * Sin vehiculo el conductor no puede recibir comandas: el servidor exige que
   * la placa asignada sea suya, y la placa es lo que queda anotado de por donde
   * salio el pedido.
   */
  protected altaVehiculo(): void {
    const conductorId = this.altaVehiculoPara();
    const placa = this.placa().trim();
    if (!conductorId || placa.length === 0 || this.ocupada()) return;

    this.ocupada.set('alta');
    this.transportistasApi
      .registrarVehiculo({
        id: conductorId,
        vehiculoRequestDto: {
          placa,
          tipoVehiculo: this.tipoVehiculo(),
          marcaModelo: this.marcaModelo().trim() || undefined,
          activo: true,
        },
      })
      .subscribe({
        next: (v) => {
          this.ocupada.set(null);
          this.altaVehiculoPara.set(null);
          this.avisos.exito(this.t('despacho.avisoVehiculoAnotado', { placa: v.placa ?? '' }));
          this.cargar(true);
        },
        error: () => this.ocupada.set(null),
      });
  }

  /**
   * Dar de baja a un conductor no borra nada: las comandas que ya se llevo
   * conservan su nombre y su placa. Solo deja de poder recibir comandas nuevas.
   */
  protected async cambiarActivo(conductor: TransportistaResponseDto): Promise<void> {
    if (!conductor.id || this.ocupada()) return;

    // Reactivar no pide nada; dar de baja se confirma, con el nombre delante.
    if (conductor.activo) {
      const confirmado = await this.confirmacion.pedir({
        titulo: this.t('despacho.bajaTitulo', { nombre: conductor.nombreCompleto ?? '' }),
        mensaje: this.t('despacho.bajaMensaje'),
        confirmar: this.t('comun.darDeBaja'),
      });
      if (!confirmado || this.ocupada()) return;
    }

    this.ocupada.set(conductor.id);
    this.transportistasApi
      .cambiarActivoTransportista({ id: conductor.id, activo: !conductor.activo })
      .subscribe({
        next: () => {
          this.ocupada.set(null);
          this.cargar(true);
        },
        error: () => this.ocupada.set(null),
      });
  }
}
