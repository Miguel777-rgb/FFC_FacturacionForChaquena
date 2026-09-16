import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { nombreDeAdjunto } from './descarga';

function respuestaCon(disposicion?: string): HttpResponse<Blob> {
  const headers = disposicion
    ? new HttpHeaders({ 'Content-Disposition': disposicion })
    : new HttpHeaders();
  return new HttpResponse({ body: new Blob(), headers });
}

describe('nombreDeAdjunto', () => {
  it('prefiere el nombre codificado, que admite tildes', () => {
    const respuesta = respuestaCon(
      `attachment; filename="=?UTF-8?Q?x?="; filename*=UTF-8''chaquena-asistencia-se%C3%B1al.pdf`,
    );
    expect(nombreDeAdjunto(respuesta, 'respaldo.pdf')).toBe('chaquena-asistencia-señal.pdf');
  });

  it('usa el nombre simple si no hay otro, y el de respaldo si no hay ninguno', () => {
    expect(nombreDeAdjunto(respuestaCon('attachment; filename="ventas.xlsx"'), 'x.xlsx')).toBe(
      'ventas.xlsx',
    );
    expect(nombreDeAdjunto(respuestaCon(), 'chaquena-ventas.pdf')).toBe('chaquena-ventas.pdf');
  });
});
