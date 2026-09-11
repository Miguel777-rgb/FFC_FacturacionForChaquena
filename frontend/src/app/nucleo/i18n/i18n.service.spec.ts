import { TestBed } from '@angular/core/testing';
import { describe, it, expect, beforeEach } from 'vitest';

import { I18nService } from './i18n.service';
import { IDIOMAS } from './idioma';
import { ES } from './traducciones/es';
import { EN } from './traducciones/en';
import { PT } from './traducciones/pt';

const DICCIONARIOS = { es: ES, en: EN, pt: PT } as const;

/** Los `{huecos}` que declara un texto, en orden y sin repetir. */
function huecos(texto: string): string[] {
  return [...new Set(texto.match(/\{\w+\}/g) ?? [])].sort();
}

describe('diccionarios', () => {
  // Que las claves coincidan ya lo garantiza el tipo `Record<ClaveI18n, string>`.
  // Lo que se comprueba aqui es lo que el compilador no ve.

  it('ninguna traduccion esta en blanco', () => {
    for (const [idioma, diccionario] of Object.entries(DICCIONARIOS)) {
      const vacias = Object.entries(diccionario)
        .filter(([, texto]) => texto.trim().length === 0)
        .map(([clave]) => clave);
      expect(vacias, `claves vacias en ${idioma}`).toEqual([]);
    }
  });

  it('cada traduccion lleva los mismos huecos que el espanol', () => {
    // Un `{total}` perdido en la traduccion no rompe nada: pinta una frase sin
    // el importe, que es peor que un error, porque parece correcta.
    for (const [idioma, diccionario] of Object.entries(DICCIONARIOS)) {
      const desajustadas = Object.entries(ES)
        .filter(([clave, textoEs]) => {
          const traducido = (diccionario as Record<string, string>)[clave];
          return huecos(textoEs).join() !== huecos(traducido).join();
        })
        .map(([clave]) => clave);
      expect(desajustadas, `huecos distintos en ${idioma}`).toEqual([]);
    }
  });

  it('todo plural tiene su pareja uno/otros', () => {
    const bases = Object.keys(ES)
      .filter((c) => c.endsWith('.uno'))
      .map((c) => c.slice(0, -'.uno'.length));
    expect(bases.length).toBeGreaterThan(0);

    for (const [idioma, diccionario] of Object.entries(DICCIONARIOS)) {
      const sinPareja = bases.filter((b) => !(`${b}.otros` in diccionario));
      expect(sinPareja, `plurales incompletos en ${idioma}`).toEqual([]);
    }
  });

  it('los tres idiomas del selector tienen diccionario', () => {
    expect(IDIOMAS.map((i) => i.codigo).sort()).toEqual(Object.keys(DICCIONARIOS).sort());
  });
});

describe('I18nService', () => {
  let i18n: I18nService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    i18n = TestBed.inject(I18nService);
  });

  it('arranca en espanol', () => {
    expect(i18n.idioma()).toBe('es');
    expect(i18n.t('comun.guardar')).toBe('Guardar');
  });

  it('cambia de idioma y lo recuerda al recargar', async () => {
    await i18n.cambiar('pt');
    expect(i18n.t('comun.guardar')).toBe('Salvar');

    // Un servicio nuevo lee lo que quedo guardado, como al recargar la pagina;
    // `precargar` es lo que trae su diccionario antes de pintar.
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({});
    const recargado = TestBed.inject(I18nService);
    await recargado.precargar();

    expect(recargado.idioma()).toBe('pt');
    expect(recargado.t('comun.guardar')).toBe('Salvar');
  });

  it('ignora un idioma que no existe', async () => {
    await i18n.cambiar('de' as never);
    expect(i18n.idioma()).toBe('es');
  });

  it('marca el idioma en el documento', async () => {
    await i18n.cambiar('en');
    expect(document.documentElement.lang).toBe('en');
  });

  it('sustituye los parametros', () => {
    expect(i18n.t('comun.mesa', { numero: 7 })).toBe('Mesa 7');
  });

  it('deja el hueco a la vista cuando falta el parametro', () => {
    // Un hueco sin rellenar se ve y se reporta; una frase con un importe
    // borrado parece correcta y no la reporta nadie.
    expect(i18n.t('comun.mesa')).toBe('Mesa {numero}');
  });

  it('elige singular o plural', async () => {
    expect(i18n.tp('comun.items', 1)).toBe('1 ítem');
    expect(i18n.tp('comun.items', 3)).toBe('3 ítems');

    await i18n.cambiar('en');
    expect(i18n.tp('comun.items', 1)).toBe('1 item');
    expect(i18n.tp('comun.items', 0)).toBe('0 items');
  });

  it('traduce los valores del contrato', async () => {
    expect(i18n.tEnum('estado', 'EN_PREPARACION')).toBe('En preparación');
    await i18n.cambiar('pt');
    expect(i18n.tEnum('estado', 'EN_PREPARACION')).toBe('Em preparo');
  });

  it('devuelve crudo un valor del contrato que no conoce', () => {
    // El dia que el backend anada un estado, la pantalla lo ensena tal cual en
    // vez de dejar el hueco vacio.
    expect(i18n.tEnum('estado', 'EN_TRANSITO')).toBe('EN_TRANSITO');
    expect(i18n.tEnum('estado', undefined)).toBe('—');
  });
});

describe('formato de fecha', () => {
  let i18n: I18nService;

  // Se construye en hora local, no desde una cadena ISO con zona, para que la
  // prueba diga lo mismo en la maquina de quien la ejecute.
  const INSTANTE = new Date(2026, 8, 11, 21, 18);

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    i18n = TestBed.inject(I18nService);
  });

  it('escribe el mismo instante distinto en cada idioma', async () => {
    // Es el punto entero de la funcion: 11/9 y 09/11 son el mismo dia escrito
    // por un peruano y por un estadounidense.
    const es = i18n.fecha(INSTANTE);

    await i18n.cambiar('en');
    const en = i18n.fecha(INSTANTE);

    await i18n.cambiar('pt');
    const pt = i18n.fecha(INSTANTE);

    expect(es).toMatch(/^11\/0?9/);
    expect(en).toMatch(/^09\/11/);
    expect(pt).toMatch(/^11\/09/);
    expect(new Set([es, en, pt]).size).toBe(3);
  });

  it('escribe el mes con letras del idioma en el estilo largo', async () => {
    expect(i18n.fecha(INSTANTE, 'larga')).toContain('setiembre');

    await i18n.cambiar('en');
    expect(i18n.fecha(INSTANTE, 'larga')).toContain('September');

    await i18n.cambiar('pt');
    expect(i18n.fecha(INSTANTE, 'larga')).toContain('setembro');
  });

  it('acepta la cadena ISO que manda el backend', () => {
    expect(i18n.fecha('2026-09-11T21:18:00')).toBe(i18n.fecha(INSTANTE));
  });

  it('devuelve la raya cuando no hay fecha o no es valida', () => {
    // «Invalid Date» es un mensaje para quien programa, no para quien mira la
    // pantalla.
    expect(i18n.fecha(null)).toBe('—');
    expect(i18n.fecha(undefined)).toBe('—');
    expect(i18n.fecha('')).toBe('—');
    expect(i18n.fecha('no es una fecha')).toBe('—');
  });
});
