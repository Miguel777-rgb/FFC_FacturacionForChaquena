import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';

import {
  CatalogoPromocionesApi,
  type PromocionRequestDto,
  type PromocionResponseDto,
} from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { fechaIsoLocal } from '../../nucleo/i18n/formatos';
import { I18nService } from '../../nucleo/i18n/i18n.service';
import type { ClaveI18n } from '../../nucleo/i18n/traducciones/es';
import { SesionService } from '../../nucleo/sesion/sesion.service';

type Vigencia = 'vigente' | 'programada' | 'pausada' | 'vencida';
type Modo = 'porcentaje' | 'monto';

/** Primero lo que hoy rebaja precios; al final, lo que ya no. */
const ORDEN: Record<Vigencia, number> = { vigente: 0, programada: 1, pausada: 2, vencida: 3 };

const ROTULO: Record<Vigencia, ClaveI18n> = {
  vigente: 'promociones.vigente',
  programada: 'promociones.programada',
  pausada: 'promociones.pausada',
  vencida: 'promociones.vencida',
};

function vigenciaDe(p: PromocionResponseDto): Vigencia {
  const ahora = Date.now();
  if (p.fechaFin && new Date(p.fechaFin).getTime() < ahora) return 'vencida';
  if (!p.activa) return 'pausada';
  if (p.fechaInicio && new Date(p.fechaInicio).getTime() > ahora) return 'programada';
  return 'vigente';
}

/** Un `input type=date` da el dia; la promocion empieza al abrir y acaba al cerrar ese dia. */
function diaDeCampo(valor: string, alCierre: boolean): Date | null {
  const [anio, mes, dia] = valor.split('-').map(Number);
  if (!anio || !mes || !dia) return null;
  return alCierre ? new Date(anio, mes - 1, dia, 23, 59, 59) : new Date(anio, mes - 1, dia);
}

function campoDeDia(fecha: Date | string | undefined): string {
  if (!fecha) return '';
  const f = new Date(fecha);
  if (Number.isNaN(f.getTime())) return '';
  const dos = (n: number) => String(n).padStart(2, '0');
  return `${f.getFullYear()}-${dos(f.getMonth() + 1)}-${dos(f.getDate())}`;
}

/**
 * Promociones: las rebajas con fecha de inicio y de fin.
 *
 * El estado que se lee no es el interruptor del servidor sino lo que pasa hoy:
 * una promocion activa que empieza el lunes todavia no rebaja nada, y una que
 * vencio ayer ya no, aunque nadie la haya apagado.
 *
 * Crearlas y pausarlas es del administrador (`@PreAuthorize` de
 * `PromocionController`); quien lleva el almacen las consulta para saber que
 * insumo extra va a salir mas esta semana.
 */
@Component({
  selector: 'app-promociones-seccion',
  imports: [Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './promociones.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class PromocionesSeccion implements OnInit {
  private readonly promocionesApi = inject(CatalogoPromocionesApi);
  private readonly avisos = inject(AvisosService);
  private readonly sesion = inject(SesionService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly fecha = this.i18n.fecha;
  protected readonly ROTULO = ROTULO;

  protected readonly esAdmin = computed(() => this.sesion.tieneAlgunRol(['ADMIN']));

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly promociones = signal<PromocionResponseDto[]>([]);

  // --- formulario -----------------------------------------------------------
  protected readonly formularioAbierto = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly nombre = signal('');
  protected readonly descripcion = signal('');
  protected readonly inicio = signal('');
  protected readonly fin = signal('');
  protected readonly modo = signal<Modo>('porcentaje');
  protected readonly valor = signal<number | null>(null);

  protected readonly filas = computed(() =>
    this.promociones()
      .map((promocion) => ({ promocion, vigencia: vigenciaDe(promocion) }))
      .sort(
        (a, b) =>
          ORDEN[a.vigencia] - ORDEN[b.vigencia] ||
          (b.promocion.fechaInicio ?? '').localeCompare(a.promocion.fechaInicio ?? ''),
      ),
  );

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editandoId() ? 'promociones.editar' : 'promociones.nueva'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.promocionesApi.listarPromociones({}).subscribe({
      next: (lista) => {
        this.promociones.set(lista);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  protected descuentoDe(p: PromocionResponseDto): string {
    if (p.porcentajeDescuento) return `${p.porcentajeDescuento} %`;
    if (p.montoDescuento) return `S/ ${p.montoDescuento.toFixed(2)}`;
    return '—';
  }

  // --- formulario -----------------------------------------------------------

  protected abrirNueva(): void {
    const hoy = new Date();
    const enUnMes = new Date(hoy);
    enUnMes.setDate(enUnMes.getDate() + 30);

    this.editandoId.set(null);
    this.nombre.set('');
    this.descripcion.set('');
    this.inicio.set(campoDeDia(hoy));
    this.fin.set(campoDeDia(enUnMes));
    this.modo.set('porcentaje');
    this.valor.set(null);
    this.formularioAbierto.set(true);
  }

  protected abrirEdicion(p: PromocionResponseDto): void {
    this.editandoId.set(p.id ?? null);
    this.nombre.set(p.nombre ?? '');
    this.descripcion.set(p.descripcion ?? '');
    this.inicio.set(campoDeDia(p.fechaInicio));
    this.fin.set(campoDeDia(p.fechaFin));
    this.modo.set(p.montoDescuento && !p.porcentajeDescuento ? 'monto' : 'porcentaje');
    this.valor.set(p.porcentajeDescuento ?? p.montoDescuento ?? null);
    this.formularioAbierto.set(true);
  }

  protected anotarValor(v: string): void {
    const n = Number.parseFloat(v);
    this.valor.set(Number.isFinite(n) && n > 0 ? n : null);
  }

  protected guardar(): void {
    const nombre = this.nombre().trim();
    const valor = this.valor();
    const inicio = diaDeCampo(this.inicio(), false);
    const fin = diaDeCampo(this.fin(), true);
    if (!nombre || valor === null || !inicio || !fin || this.guardando()) return;

    if (fin < inicio) {
      this.avisos.info(this.t('promociones.avisoFechas'));
      return;
    }
    if (this.modo() === 'porcentaje' && valor > 100) {
      this.avisos.info(this.t('promociones.avisoPorcentaje'));
      return;
    }

    // El `PUT` reemplaza: lo que este formulario no toca —el insumo extra, si
    // estaba activa— viaja como estaba.
    const id = this.editandoId();
    const actual = this.promociones().find((p) => p.id === id);
    const cuerpo: PromocionRequestDto = {
      nombre,
      descripcion: this.descripcion().trim() || undefined,
      fechaInicio: fechaIsoLocal(inicio),
      fechaFin: fechaIsoLocal(fin),
      porcentajeDescuento: this.modo() === 'porcentaje' ? valor : undefined,
      montoDescuento: this.modo() === 'monto' ? valor : undefined,
      activa: actual?.activa ?? true,
      requiereInsumoExtra: actual?.requiereInsumoExtra,
      insumoExtraId: actual?.insumoExtraId,
    };

    this.guardando.set(true);
    const peticion = id
      ? this.promocionesApi.actualizarPromocion({ id, promocionRequestDto: cuerpo })
      : this.promocionesApi.crearPromocion({ promocionRequestDto: cuerpo });

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t(id ? 'promociones.avisoGuardada' : 'promociones.avisoAlta', { nombre }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  protected alternarActiva(p: PromocionResponseDto): void {
    if (!p.id || this.guardando()) return;

    const activa = !p.activa;
    this.guardando.set(true);
    this.promocionesApi.cambiarActiva({ id: p.id, activa }).subscribe({
      next: () => {
        this.guardando.set(false);
        this.promociones.update((lista) =>
          lista.map((x) => (x.id === p.id ? { ...x, activa } : x)),
        );
        this.avisos.exito(
          this.t(activa ? 'promociones.avisoReanudada' : 'promociones.avisoPausada', {
            nombre: p.nombre ?? '',
          }),
        );
      },
      error: () => this.guardando.set(false),
    });
  }
}
