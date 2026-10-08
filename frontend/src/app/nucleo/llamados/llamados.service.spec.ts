import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { Configuration } from '../../api/configuration';
import { AvisosService } from '../http/avisos.service';
import { SesionService } from '../sesion/sesion.service';
import type { AvisoLlamado, LlamadoCocina } from './llamado';
import {
  FABRICA_CLIENTE_STOMP,
  LlamadosService,
  type ClienteStomp,
  type ConfigClienteStomp,
} from './llamados.service';

/** Un cliente STOMP que la prueba maneja a mano: conecta, cae y recibe cuando ella dice. */
class ClienteFalso implements ClienteStomp {
  activo = false;
  readonly publicados: { destino: string; cuerpo: unknown }[] = [];
  readonly suscripciones = new Map<string, (cuerpo: string) => void>();

  constructor(readonly config: ConfigClienteStomp) {}

  activar(): void {
    this.activo = true;
  }
  desactivar(): void {
    this.activo = false;
  }
  publicar(destino: string, cuerpo: unknown): void {
    this.publicados.push({ destino, cuerpo });
  }
  suscribir(destino: string, alRecibir: (cuerpo: string) => void): void {
    this.suscripciones.set(destino, alRecibir);
  }

  /** El servidor acepta el CONNECT. */
  conectar(): void {
    this.config.alConectar();
  }

  /** El servidor manda un aviso por uno de los destinos suscritos. */
  recibir(destino: string, aviso: Partial<AvisoLlamado>): void {
    const alRecibir = this.suscripciones.get(destino);
    if (!alRecibir) throw new Error(`Nadie escucha ${destino}`);
    alRecibir(JSON.stringify({ llamados: [], mozosConectados: null, mensaje: null, ...aviso }));
  }
}

function tokenDe(roles: string[]): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'quien@chaquena.pe',
    username: 'quien',
    nombres: 'Rosa',
    cargo: roles[0],
    roles,
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.firma`;
}

function llamado(parcial: Partial<LlamadoCocina> = {}): LlamadoCocina {
  return {
    id: 'l1',
    ordenId: 'o1',
    correlativo: '493F9AA1',
    tipoOrden: 'MESA',
    mesaNumero: 'M3',
    llamadoPor: 'Julio',
    llamadoEn: '2026-10-08T15:00:00Z',
    estado: 'PENDIENTE',
    atendidoPor: null,
    atendidoEn: null,
    segundosRespuesta: null,
    ...parcial,
  };
}

describe('LlamadosService', () => {
  let clientes: ClienteFalso[];
  let token: string;

  function preparar(roles: string[], basePath = 'http://localhost:8080'): LlamadosService {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        { provide: Configuration, useValue: new Configuration({ basePath }) },
        {
          provide: FABRICA_CLIENTE_STOMP,
          useValue: async (config: ConfigClienteStomp) => {
            const cliente = new ClienteFalso(config);
            clientes.push(cliente);
            return cliente;
          },
        },
      ],
    });
    token = tokenDe(roles);
    TestBed.inject(SesionService).abrir(token);
    return TestBed.inject(LlamadosService);
  }

  /** Lo que hace un componente al nacer: pedir el canal. Devuelve el cliente ya conectado. */
  async function abrirCanal(servicio: LlamadosService): Promise<ClienteFalso> {
    TestBed.runInInjectionContext(() => servicio.usar());
    await vi.waitFor(() => expect(clientes).toHaveLength(1));
    expect(clientes[0].activo).toBe(true);
    clientes[0].conectar();
    return clientes[0];
  }

  beforeEach(() => {
    sessionStorage.clear();
    clientes = [];
  });

  afterEach(() => {
    vi.useRealTimers();
    TestBed.resetTestingModule();
  });

  it('el mozo abre el canal con su token y escucha su tema, sus errores y la foto', async () => {
    const cliente = await abrirCanal(preparar(['MOZO']));

    expect(cliente.config.url).toBe('ws://localhost:8080/api/v1/ws');
    expect(cliente.config.cabeceras()).toEqual({ Authorization: `Bearer ${token}` });
    expect([...cliente.suscripciones.keys()]).toEqual([
      '/user/queue/llamados',
      '/topic/llamados/mozos',
      '/app/llamados/estado',
    ]);
  });

  it('cocina escucha el tema de cocina y no el de los mozos', async () => {
    const cliente = await abrirCanal(preparar(['COCINA']));

    expect(cliente.suscripciones.has('/topic/llamados/cocina')).toBe(true);
    expect(cliente.suscripciones.has('/topic/llamados/mozos')).toBe(false);
  });

  it('quien ni llama ni responde no abre el canal', async () => {
    const servicio = preparar(['CAJA']);
    TestBed.runInInjectionContext(() => servicio.usar());
    await Promise.resolve();

    expect(clientes).toHaveLength(0);
  });

  it('en produccion usa el origen de la pagina, con wss si va por https', async () => {
    const cliente = await abrirCanal(preparar(['MOZO'], 'https://chaquena.example'));

    expect(cliente.config.url).toBe('wss://chaquena.example/api/v1/ws');
  });

  it('la foto ordena los pendientes por antiguedad y hace sonar al mozo que entra', async () => {
    const servicio = preparar(['MOZO']);
    let timbres = 0;
    servicio.timbre.subscribe(() => timbres++);
    const cliente = await abrirCanal(servicio);

    const nuevo = llamado({ id: 'l2', ordenId: 'o2', llamadoEn: '2026-10-08T15:04:00Z' });
    cliente.recibir('/app/llamados/estado', { tipo: 'estado', llamados: [nuevo, llamado()] });

    expect(servicio.pendientes().map((l) => l.id)).toEqual(['l1', 'l2']);
    expect(timbres).toBe(1);
  });

  it('en cocina un llamado no suena: solo pone al dia la comanda y los mozos', async () => {
    const servicio = preparar(['COCINA']);
    let timbres = 0;
    servicio.timbre.subscribe(() => timbres++);
    const cliente = await abrirCanal(servicio);

    cliente.recibir('/topic/llamados/cocina', {
      tipo: 'llamado',
      llamados: [llamado()],
      mozosConectados: 2,
    });

    expect(timbres).toBe(0);
    expect(servicio.deOrden('o1')?.id).toBe('l1');
    expect(servicio.mozosConectados()).toBe(2);
  });

  it('«Voy» manda el id una sola vez, espera la confirmacion y la anuncia', async () => {
    const servicio = preparar(['MOZO']);
    const cliente = await abrirCanal(servicio);
    cliente.recibir('/app/llamados/estado', { tipo: 'estado', llamados: [llamado()] });

    servicio.atender('l1');
    servicio.atender('l1');

    expect(cliente.publicados).toEqual([
      { destino: '/app/llamados/atender', cuerpo: { llamadoId: 'l1' } },
    ]);
    expect(servicio.respondiendo().has('l1')).toBe(true);

    cliente.recibir('/topic/llamados/mozos', {
      tipo: 'atendido',
      llamados: [llamado({ estado: 'ATENDIDO', atendidoPor: 'Rosa' })],
    });

    expect(servicio.pendientes()).toEqual([]);
    expect(servicio.respondiendo().size).toBe(0);
    expect(TestBed.inject(AvisosService).avisos().at(-1)).toMatchObject({
      tono: 'exito',
      texto: 'Vas en camino: Mesa M3.',
    });
  });

  it('si otro mozo llego antes, el rechazo se lee como informacion y no como error', async () => {
    const servicio = preparar(['MOZO']);
    const cliente = await abrirCanal(servicio);
    cliente.recibir('/app/llamados/estado', { tipo: 'estado', llamados: [llamado()] });

    servicio.atender('l1');
    cliente.recibir('/user/queue/llamados', { tipo: 'error', mensaje: 'Rosa ya va en camino.' });

    expect(servicio.respondiendo().size).toBe(0);
    expect(TestBed.inject(AvisosService).avisos().at(-1)).toMatchObject({
      tono: 'info',
      texto: 'Rosa ya va en camino.',
    });
  });

  it('cocina llama y el boton espera hasta que el servidor devuelve el llamado', async () => {
    const servicio = preparar(['COCINA']);
    const cliente = await abrirCanal(servicio);

    servicio.llamar('o1');

    expect(cliente.publicados).toEqual([
      { destino: '/app/llamados/llamar', cuerpo: { ordenId: 'o1' } },
    ]);
    expect(servicio.llamando().has('o1')).toBe(true);

    cliente.recibir('/topic/llamados/cocina', { tipo: 'llamado', llamados: [llamado()] });

    expect(servicio.llamando().size).toBe(0);
  });

  it('sin canal no se llama: el clic no se pierde en un socket cerrado', async () => {
    const servicio = preparar(['COCINA']);
    TestBed.runInInjectionContext(() => servicio.usar());
    await vi.waitFor(() => expect(clientes).toHaveLength(1));

    servicio.llamar('o1');

    expect(clientes[0].publicados).toEqual([]);
  });

  it('al cerrar la sesion corta el canal y olvida los llamados', async () => {
    const servicio = preparar(['MOZO']);
    const cliente = await abrirCanal(servicio);
    cliente.recibir('/app/llamados/estado', { tipo: 'estado', llamados: [llamado()] });

    TestBed.inject(SesionService).cerrar();
    TestBed.tick();

    expect(cliente.activo).toBe(false);
    expect(servicio.conectado()).toBe(false);
    expect(servicio.llamados()).toEqual([]);
  });

  it('el mozo se entera de la caida solo si dura mas de diez segundos', async () => {
    const servicio = preparar(['MOZO']);
    const cliente = await abrirCanal(servicio);
    vi.useFakeTimers();

    cliente.config.alCaerse();
    vi.advanceTimersByTime(9_000);
    expect(servicio.sinConexion()).toBe(false);

    vi.advanceTimersByTime(1_500);
    expect(servicio.sinConexion()).toBe(true);

    cliente.conectar();
    expect(servicio.sinConexion()).toBe(false);
  });
});
