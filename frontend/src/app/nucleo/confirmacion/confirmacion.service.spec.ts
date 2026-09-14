import { TestBed } from '@angular/core/testing';
import { provideZonelessChangeDetection } from '@angular/core';
import { describe, it, expect, beforeEach } from 'vitest';

import { ConfirmacionService } from './confirmacion.service';

const PETICION = { titulo: 'Dar de baja a Luis', mensaje: 'Deja de aparecer.', confirmar: 'Dar de baja' };

/**
 * La confirmacion es lo que separa un toque de un error de servicio. Si la
 * promesa no se resuelve, la accion no ocurre nunca y nadie ve por que.
 */
describe('ConfirmacionService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideZonelessChangeDetection()] });
  });

  it('abre la peticion y la cierra con la respuesta', async () => {
    const confirmacion = TestBed.inject(ConfirmacionService);

    const respuesta = confirmacion.pedir(PETICION);
    expect(confirmacion.abierta()?.confirmar).toBe('Dar de baja');

    confirmacion.responder(true);

    await expect(respuesta).resolves.toBe(true);
    expect(confirmacion.abierta()).toBeNull();
  });

  it('una peticion nueva da por rechazada la que seguia abierta', async () => {
    const confirmacion = TestBed.inject(ConfirmacionService);

    const primera = confirmacion.pedir(PETICION);
    const segunda = confirmacion.pedir({ ...PETICION, titulo: 'Liberar la cuenta' });

    await expect(primera).resolves.toBe(false);
    expect(confirmacion.abierta()?.titulo).toBe('Liberar la cuenta');

    confirmacion.responder(false);
    await expect(segunda).resolves.toBe(false);
  });
});
