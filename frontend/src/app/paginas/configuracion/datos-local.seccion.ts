import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';

import {
  HorarioLocalDtoDiaEnum,
  LocalApi,
  type DatosLocalDto,
} from '../../api';
import { AvisosService } from '../../nucleo/http/avisos.service';
import { I18nService } from '../../nucleo/i18n/i18n.service';

type Dia = HorarioLocalDtoDiaEnum;
type CampoTexto = 'nombreComercial' | 'ruc' | 'direccion' | 'telefono' | 'correo';

const DIAS = Object.values(HorarioLocalDtoDiaEnum);

/** Un dia tal como lo edita el formulario: horas en «HH:mm» y cadena vacia cuando no hay. */
interface FilaHorario {
  dia: Dia;
  cerrado: boolean;
  abre: string;
  cierra: string;
}

interface Formulario {
  nombreComercial: string;
  ruc: string;
  direccion: string;
  telefono: string;
  correo: string;
  porcentajeIgv: number | null;
  horarios: FilaHorario[];
}

/** El servidor manda «12:00:00»; un `input type=time` quiere «12:00». */
function horaCorta(valor: string | undefined): string {
  return valor ? valor.slice(0, 5) : '';
}

function desdeServidor(d: DatosLocalDto): Formulario {
  const porDia = new Map((d.horarios ?? []).map((h) => [h.dia, h]));
  return {
    nombreComercial: d.nombreComercial ?? '',
    ruc: d.ruc ?? '',
    direccion: d.direccion ?? '',
    telefono: d.telefono ?? '',
    correo: d.correo ?? '',
    porcentajeIgv: d.porcentajeIgv ?? null,
    horarios: DIAS.map((dia) => {
      const h = porDia.get(dia);
      return { dia, cerrado: !!h?.cerrado, abre: horaCorta(h?.abre), cierra: horaCorta(h?.cierra) };
    }),
  };
}

/**
 * Datos del local: quien es, cuanto IGV llevan sus precios y cuando abre.
 *
 * Todo el formulario vive en una sola senal y se compara con lo que llego
 * del servidor, asi el boton de guardar solo se enciende cuando de verdad
 * cambio algo y «Descartar» vuelve exactamente a lo guardado.
 *
 * Un dia abierto con una sola hora se marca en la tabla antes de enviar: el
 * servidor lo rechazaria, y es mas facil corregirlo viendo la fila.
 */
@Component({
  selector: 'app-datos-local-seccion',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './datos-local.seccion.html',
  styleUrls: ['../../disenio/secciones.scss', './datos-local.seccion.scss'],
})
export class DatosLocalSeccion implements OnInit {
  private readonly localApi = inject(LocalApi);
  private readonly avisos = inject(AvisosService);
  private readonly i18n = inject(I18nService);

  protected readonly t = this.i18n.t;
  protected readonly tEnum = this.i18n.tEnum;

  protected readonly cargando = signal(true);
  protected readonly guardando = signal(false);
  protected readonly formulario = signal<Formulario | null>(null);

  /** Lo guardado, serializado: comparar dos JSON es la forma honesta de saber si algo cambio. */
  private readonly original = signal('');

  protected readonly hayCambios = computed(() => {
    const f = this.formulario();
    return !!f && JSON.stringify(f) !== this.original();
  });

  /** Dias abiertos con hora de apertura o de cierre, pero no las dos. */
  protected readonly diasIncompletos = computed(() =>
    (this.formulario()?.horarios ?? [])
      .filter((h) => !h.cerrado && !h.abre !== !h.cierra)
      .map((h) => h.dia),
  );

  protected readonly rucInvalido = computed(() => {
    const ruc = this.formulario()?.ruc.trim() ?? '';
    return ruc.length > 0 && !/^\d{11}$/.test(ruc);
  });

  ngOnInit(): void {
    this.cargar();
  }

  protected cargar(): void {
    this.cargando.set(true);
    this.localApi.obtenerDatosLocal().subscribe({
      next: (datos) => {
        this.aplicar(datos);
        this.cargando.set(false);
      },
      error: () => this.cargando.set(false),
    });
  }

  private aplicar(datos: DatosLocalDto): void {
    const formulario = desdeServidor(datos);
    this.formulario.set(formulario);
    this.original.set(JSON.stringify(formulario));
  }

  protected anotar(campo: CampoTexto, valor: string): void {
    const limpio = campo === 'ruc' ? valor.replace(/\D/g, '').slice(0, 11) : valor;
    this.formulario.update((f) => (f ? { ...f, [campo]: limpio } : f));
  }

  protected anotarIgv(valor: string): void {
    const n = Number.parseFloat(valor);
    this.formulario.update((f) => (f ? { ...f, porcentajeIgv: Number.isFinite(n) ? n : null } : f));
  }

  protected anotarHorario(dia: Dia, cambio: Partial<FilaHorario>): void {
    this.formulario.update((f) =>
      f ? { ...f, horarios: f.horarios.map((h) => (h.dia === dia ? { ...h, ...cambio } : h)) } : f,
    );
  }

  protected descartar(): void {
    const guardado = this.original();
    if (guardado) this.formulario.set(JSON.parse(guardado) as Formulario);
  }

  protected guardar(): void {
    const f = this.formulario();
    if (!f || !this.hayCambios() || this.guardando()) return;

    if (this.rucInvalido()) {
      this.avisos.info(this.t('datosLocal.avisoRuc'));
      return;
    }
    if (this.diasIncompletos().length > 0) {
      this.avisos.info(this.t('datosLocal.avisoHorario'));
      return;
    }
    if (f.porcentajeIgv === null || f.porcentajeIgv < 0 || f.porcentajeIgv > 100) {
      this.avisos.info(this.t('datosLocal.avisoIgv'));
      return;
    }

    const texto = (valor: string) => valor.trim() || undefined;

    this.guardando.set(true);
    this.localApi
      .actualizarDatosLocal({
        datosLocalDto: {
          nombreComercial: texto(f.nombreComercial),
          ruc: texto(f.ruc),
          direccion: texto(f.direccion),
          telefono: texto(f.telefono),
          correo: texto(f.correo),
          porcentajeIgv: f.porcentajeIgv,
          horarios: f.horarios.map((h) => ({
            dia: h.dia,
            cerrado: h.cerrado,
            abre: h.cerrado ? undefined : h.abre || undefined,
            cierra: h.cerrado ? undefined : h.cierra || undefined,
          })),
        },
      })
      .subscribe({
        next: (datos) => {
          this.guardando.set(false);
          this.aplicar(datos);
          this.avisos.exito(this.t('datosLocal.avisoGuardado'));
        },
        error: () => this.guardando.set(false),
      });
  }
}
