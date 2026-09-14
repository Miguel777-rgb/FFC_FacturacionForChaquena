import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';

import { InventarioProveedoresApi, type ProveedorDto } from '../../api';
import { Dialogo } from '../../disenio/dialogo';
import { Icono } from '../../disenio/icono';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

/**
 * Proveedores: a quien se le compra.
 *
 * Se dan de baja, no se borran. Un proveedor con el que ya no se trabaja deja
 * de ofrecerse al registrar una compra, pero sus lotes siguen diciendo de donde
 * vinieron; por eso la lista muestra tambien los dados de baja, apagados.
 */
@Component({
  selector: 'app-proveedores-seccion',
  imports: [Dialogo, Icono],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './proveedores.seccion.html',
  styleUrl: '../../disenio/secciones.scss',
})
export class ProveedoresSeccion implements OnInit {
  private readonly proveedoresApi = inject(InventarioProveedoresApi);
  private readonly avisos = inject(AvisosService);

  protected readonly t = inject(I18nService).t;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly proveedores = signal<ProveedorDto[]>([]);

  // --- formulario -----------------------------------------------------------
  protected readonly formularioAbierto = signal(false);
  protected readonly editandoId = signal<string | null>(null);
  protected readonly nombre = signal('');
  protected readonly ruc = signal('');
  protected readonly contacto = signal('');
  protected readonly telefono = signal('');
  protected readonly correo = signal('');

  protected readonly tituloFormulario = computed(() =>
    this.t(this.editandoId() ? 'proveedores.editar' : 'proveedores.nuevo'),
  );

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.proveedoresApi.listarProveedores({ soloActivos: false }).subscribe({
      next: (lista) => {
        this.proveedores.set(lista);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  /** Contacto, telefono y correo en una linea; lo que falte no deja separadores sueltos. */
  protected contactoDe(p: ProveedorDto): string {
    return [p.contacto, p.telefono, p.correo].filter(Boolean).join(' · ');
  }

  protected abrirNuevo(): void {
    this.editandoId.set(null);
    this.nombre.set('');
    this.ruc.set('');
    this.contacto.set('');
    this.telefono.set('');
    this.correo.set('');
    this.formularioAbierto.set(true);
  }

  protected abrirEdicion(p: ProveedorDto): void {
    this.editandoId.set(p.id ?? null);
    this.nombre.set(p.nombre);
    this.ruc.set(p.ruc ?? '');
    this.contacto.set(p.contacto ?? '');
    this.telefono.set(p.telefono ?? '');
    this.correo.set(p.correo ?? '');
    this.formularioAbierto.set(true);
  }

  protected anotarRuc(valor: string): void {
    this.ruc.set(valor.replace(/\D/g, '').slice(0, 11));
  }

  protected guardar(): void {
    const nombre = this.nombre().trim();
    const ruc = this.ruc().trim();
    if (!nombre || this.guardando()) return;
    if (ruc && ruc.length !== 11) {
      this.avisos.info(this.t('proveedores.avisoRuc'));
      return;
    }

    const proveedorDto = {
      nombre,
      ruc: ruc || undefined,
      contacto: this.contacto().trim() || undefined,
      telefono: this.telefono().trim() || undefined,
      correo: this.correo().trim() || undefined,
    };

    const id = this.editandoId();
    this.guardando.set(true);
    const peticion = id
      ? this.proveedoresApi.actualizarProveedor({ id, proveedorDto })
      : this.proveedoresApi.crearProveedor({ proveedorDto });

    peticion.subscribe({
      next: () => {
        this.guardando.set(false);
        this.formularioAbierto.set(false);
        this.avisos.exito(
          this.t(id ? 'proveedores.avisoGuardado' : 'proveedores.avisoAlta', { nombre }),
        );
        this.cargar();
      },
      error: () => this.guardando.set(false),
    });
  }

  protected alternarActivo(p: ProveedorDto): void {
    if (!p.id || this.guardando()) return;

    const activo = !p.activo;
    this.guardando.set(true);
    this.proveedoresApi.cambiarActivoProveedor({ id: p.id, activo }).subscribe({
      next: (actualizado) => {
        this.guardando.set(false);
        this.proveedores.update((lista) => lista.map((x) => (x.id === p.id ? actualizado : x)));
        this.avisos.exito(
          this.t(activo ? 'proveedores.avisoReactivado' : 'proveedores.avisoBaja', {
            nombre: p.nombre,
          }),
        );
      },
      error: () => this.guardando.set(false),
    });
  }
}
