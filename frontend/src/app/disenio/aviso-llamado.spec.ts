import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../nucleo/sesion/sesion.service';
import type { AvisoLlamado as Aviso, LlamadoCocina } from '../nucleo/llamados/llamado';
import {
  FABRICA_CLIENTE_STOMP,
  type ClienteStomp,
  type ConfigClienteStomp,
} from '../nucleo/llamados/llamados.service';
import { SilencioLlamadosService } from '../nucleo/llamados/silencio.service';
import { AvisoLlamado } from './aviso-llamado';

class ClienteFalso implements ClienteStomp {
  readonly publicados: { destino: string; cuerpo: unknown }[] = [];
  readonly suscripciones = new Map<string, (cuerpo: string) => void>();
  constructor(readonly config: ConfigClienteStomp) {}
  activar(): void {}
  desactivar(): void {}
  publicar(destino: string, cuerpo: unknown): void {
    this.publicados.push({ destino, cuerpo });
  }
  suscribir(destino: string, alRecibir: (cuerpo: string) => void): void {
    this.suscripciones.set(destino, alRecibir);
  }
  recibir(destino: string, aviso: Partial<Aviso>): void {
    this.suscripciones.get(destino)!(
      JSON.stringify({ llamados: [], mozosConectados: null, mensaje: null, ...aviso }),
    );
  }
}

function tokenDeMozo(): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'mozo1@chaquena.pe',
    username: 'mozo1',
    nombres: 'Rosa',
    cargo: 'MOZO',
    roles: ['MOZO'],
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.firma`;
}

/** El llamado numero `n`: mesa Mn, llamado n minutos despues de las tres. */
function llamado(n: number): LlamadoCocina {
  return {
    id: `l${n}`,
    ordenId: `o${n}`,
    correlativo: `0000000${n}`,
    tipoOrden: 'MESA',
    mesaNumero: `M${n}`,
    llamadoPor: 'Julio',
    llamadoEn: `2026-10-08T15:0${n}:00Z`,
    estado: 'PENDIENTE',
    atendidoPor: null,
    atendidoEn: null,
    segundosRespuesta: null,
  };
}

describe('AvisoLlamado', () => {
  let clientes: ClienteFalso[];

  beforeEach(() => {
    sessionStorage.clear();
    localStorage.clear();
    clientes = [];
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
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
    TestBed.inject(SesionService).abrir(tokenDeMozo());
  });

  afterEach(() => {
    Reflect.deleteProperty(navigator, 'vibrate');
    TestBed.resetTestingModule();
  });

  async function montar() {
    const fixture = TestBed.createComponent(AvisoLlamado);
    await vi.waitFor(() => expect(clientes).toHaveLength(1));
    clientes[0].config.alConectar();
    return { fixture, cliente: clientes[0] };
  }

  it('muestra los tres que mas esperan, cuenta el resto y responde «Voy» por el primero', async () => {
    const { fixture, cliente } = await montar();
    cliente.recibir('/app/llamados/estado', {
      tipo: 'estado',
      llamados: [llamado(4), llamado(2), llamado(1), llamado(3)],
    });
    await fixture.whenStable();

    const filas = Array.from(fixture.nativeElement.querySelectorAll('.llamado')) as HTMLElement[];
    expect(filas).toHaveLength(3);
    expect(filas[0].textContent).toContain('Cocina te llama');
    expect(filas[0].textContent).toContain('Mesa M1 · comanda 00000001 · llamó Julio');
    expect(fixture.nativeElement.querySelector('.mas').textContent.trim()).toBe('Y 1 llamado más');

    const voy = filas[0].querySelector('button') as HTMLButtonElement;
    expect(voy.getAttribute('aria-label')).toBe('Voy: Mesa M1, comanda 00000001');
    voy.click();
    await fixture.whenStable();

    expect(cliente.publicados).toEqual([
      { destino: '/app/llamados/atender', cuerpo: { llamadoId: 'l1' } },
    ]);
    expect(voy.disabled).toBe(true);
  });

  it('desaparece en cuanto alguien responde', async () => {
    const { fixture, cliente } = await montar();
    cliente.recibir('/app/llamados/estado', { tipo: 'estado', llamados: [llamado(1)] });
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();

    cliente.recibir('/topic/llamados/mozos', {
      tipo: 'atendido',
      llamados: [{ ...llamado(1), estado: 'ATENDIDO', atendidoPor: 'Ana' }],
    });
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelector('[role="alert"]')).toBeNull();
  });

  it('vibra al llegar un llamado, y deja de hacerlo si el mozo lo silencia', async () => {
    const vibrar = vi.fn(() => true);
    Object.defineProperty(navigator, 'vibrate', { value: vibrar, configurable: true });
    const { cliente } = await montar();

    cliente.recibir('/topic/llamados/mozos', { tipo: 'llamado', llamados: [llamado(1)] });
    expect(vibrar).toHaveBeenCalledOnce();
    expect(vibrar).toHaveBeenCalledWith([300, 150, 300]);

    TestBed.inject(SilencioLlamadosService).alternar();
    cliente.recibir('/topic/llamados/mozos', { tipo: 'llamado', llamados: [llamado(2)] });
    expect(vibrar).toHaveBeenCalledOnce();
  });
});
