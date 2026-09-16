import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { SesionService } from '../sesion/sesion.service';
import { TiempoRealService } from './tiempo-real.service';

function tokenDeCocina(): string {
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const claims = {
    sub: 'chef1@chaquena.pe',
    roles: ['COCINA'],
    exp: Math.floor(Date.now() / 1000) + 3600,
  };
  return `${b64({ alg: 'HS256' })}.${b64(claims)}.firma`;
}

/** Un stream SSE que la prueba va escribiendo a mano. */
function streamDePrueba() {
  let control!: ReadableStreamDefaultController<Uint8Array>;
  const cuerpo = new ReadableStream<Uint8Array>({ start: (c) => (control = c) });
  const bytes = new TextEncoder();
  return {
    respuesta: new Response(cuerpo, { status: 200 }),
    escribir: (texto: string) => control.enqueue(bytes.encode(texto)),
    cortar: () => control.close(),
  };
}

describe('TiempoRealService', () => {
  let servicio: TiempoRealService;
  let fetchFalso: ReturnType<typeof vi.fn>;
  let token: string;

  beforeEach(() => {
    sessionStorage.clear();
    fetchFalso = vi.fn();
    vi.stubGlobal('fetch', fetchFalso);
    TestBed.configureTestingModule({});
    token = tokenDeCocina();
    TestBed.inject(SesionService).abrir(token);
    servicio = TestBed.inject(TiempoRealService);
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.unstubAllGlobals();
  });

  it('abre el stream con el token y avisa solo de los temas que se escuchan', async () => {
    const stream = streamDePrueba();
    fetchFalso.mockResolvedValue(stream.respuesta);
    let recargas = 0;
    const suscripcion = servicio.cambios(['COCINA'], 60_000).subscribe(() => recargas++);

    await vi.waitFor(() => expect(fetchFalso).toHaveBeenCalledOnce());
    const [url, opciones] = fetchFalso.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/eventos/stream');
    expect((opciones.headers as Record<string, string>)['Authorization']).toBe(`Bearer ${token}`);

    stream.escribir('event:listo\ndata:{}\n\n');
    await vi.waitFor(() => expect(servicio.conectado()).toBe(true));

    // Un latido, un tema ajeno y el propio partido en dos trozos.
    stream.escribir(':latido\n\nevent:aviso\ndata:{"tema":"CAJA"}\n\nevent:aviso\ndata:{"te');
    stream.escribir('ma":"COCINA"}\n\n');
    await vi.waitFor(() => expect(recargas).toBe(1));

    suscripcion.unsubscribe();
  });

  it('sin conexion vuelve al refresco periodico', async () => {
    fetchFalso.mockRejectedValue(new TypeError('Failed to fetch'));
    let recargas = 0;
    const suscripcion = servicio.cambios(['COCINA'], 20).subscribe(() => recargas++);

    await vi.waitFor(() => expect(recargas).toBeGreaterThanOrEqual(2));
    expect(servicio.conectado()).toBe(false);

    suscripcion.unsubscribe();
  });

  it('al volver despues de una caida pide una recarga, por lo que cambio en el hueco', async () => {
    const primero = streamDePrueba();
    const segundo = streamDePrueba();
    fetchFalso.mockResolvedValueOnce(primero.respuesta).mockResolvedValueOnce(segundo.respuesta);
    let recargas = 0;
    const suscripcion = servicio.cambios(['MESAS'], 60_000).subscribe(() => recargas++);

    await vi.waitFor(() => expect(fetchFalso).toHaveBeenCalledOnce());
    primero.escribir('event:listo\ndata:{}\n\n');
    await vi.waitFor(() => expect(servicio.conectado()).toBe(true));
    expect(recargas).toBe(0);

    primero.cortar();
    await vi.waitFor(() => expect(fetchFalso).toHaveBeenCalledTimes(2));
    segundo.escribir('event:listo\ndata:{}\n\n');
    await vi.waitFor(() => expect(recargas).toBe(1));

    suscripcion.unsubscribe();
  });
});
