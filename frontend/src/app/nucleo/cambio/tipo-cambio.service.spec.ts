import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { TipoCambioService } from './tipo-cambio.service';

const CLAVE = 'chaquena.tipoCambio';

/** Una respuesta como la que devuelve open.er-api.com, recortada a lo que se lee. */
function respuestaBuena(soles: number, publicado = Date.now()) {
  return {
    ok: true,
    json: async () => ({
      result: 'success',
      time_last_update_unix: Math.floor(publicado / 1000),
      rates: { PEN: soles, EUR: 0.9 },
    }),
  };
}

function servicio(): TipoCambioService {
  TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  return TestBed.inject(TipoCambioService);
}

describe('TipoCambioService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.resetTestingModule();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('convierte soles a dolares con la cotizacion traida', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaBuena(4)));
    const s = servicio();

    await s.asegurar();

    expect(s.aDolares(100)).toBeCloseTo(25);
    expect(s.fresco()).toBe(true);
  });

  it('sin cotizacion no inventa un cero', () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')));

    expect(servicio().aDolares(100)).toBeNull();
  });

  it('una sola peticion aunque varias pantallas la pidan a la vez', async () => {
    const red = vi.fn().mockResolvedValue(respuestaBuena(3.5));
    vi.stubGlobal('fetch', red);
    const s = servicio();

    await Promise.all([s.asegurar(), s.asegurar(), s.asegurar()]);

    expect(red).toHaveBeenCalledTimes(1);
  });

  it('con el dato fresco no vuelve a salir a la red', async () => {
    const red = vi.fn().mockResolvedValue(respuestaBuena(3.5));
    vi.stubGlobal('fetch', red);
    const s = servicio();

    await s.asegurar();
    await s.asegurar();

    expect(red).toHaveBeenCalledTimes(1);
  });

  it('si la red falla conserva lo ultimo guardado y lo marca como viejo', async () => {
    const anteayer = Date.now() - 48 * 60 * 60 * 1000;
    localStorage.setItem(
      CLAVE,
      JSON.stringify({ solesPorDolar: 3.2, consultadoEn: new Date(anteayer).toISOString() }),
    );
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')));
    const s = servicio();

    await s.asegurar();

    expect(s.aDolares(32)).toBeCloseTo(10);
    expect(s.fresco()).toBe(false);
  });

  it('una respuesta sin exito no pisa la cotizacion guardada', async () => {
    localStorage.setItem(
      CLAVE,
      JSON.stringify({ solesPorDolar: 3.2, consultadoEn: new Date(0).toISOString() }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => ({ result: 'error' }) }),
    );
    const s = servicio();

    await s.asegurar();

    expect(s.cambio()?.solesPorDolar).toBe(3.2);
  });

  it('una cotizacion de cero o negativa se descarta', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaBuena(0)));
    const s = servicio();

    await s.asegurar();

    expect(s.cambio()).toBeNull();
  });

  it('un almacenamiento corrupto no impide arrancar', () => {
    localStorage.setItem(CLAVE, 'esto no es json');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')));

    expect(servicio().cambio()).toBeNull();
  });

  it('guarda la hora de publicacion, no la de la consulta', async () => {
    const estaManana = Date.now() - 10 * 60 * 60 * 1000;
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaBuena(3.4, estaManana)));
    const s = servicio();

    await s.asegurar();

    expect(s.cambio()?.consultadoEn.getTime()).toBeCloseTo(estaManana, -4);
  });

  it('la cotizacion del dia sigue siendo vigente aunque tenga horas', async () => {
    // El servicio publica una vez al dia: doce horas no la vuelven vieja.
    const estaManana = Date.now() - 12 * 60 * 60 * 1000;
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaBuena(3.4, estaManana)));
    const s = servicio();

    await s.asegurar();

    expect(s.fresco()).toBe(true);
  });

  it('pasadas mas de veintiseis horas deja de darse por vigente', async () => {
    const anteayer = Date.now() - 48 * 60 * 60 * 1000;
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaBuena(3.4, anteayer)));
    const s = servicio();

    await s.asegurar();

    expect(s.fresco()).toBe(false);
  });

  it('la cotizacion sobrevive a recargar la pagina', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(respuestaBuena(3.8)));
    await servicio().asegurar();

    TestBed.resetTestingModule();
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('sin red')));

    expect(servicio().aDolares(38)).toBeCloseTo(10);
  });
});
