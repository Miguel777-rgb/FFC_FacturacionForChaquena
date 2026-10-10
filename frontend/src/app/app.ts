import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SesionService } from './nucleo/sesion/sesion.service';
import { AvisosService } from './nucleo/http/avisos.service';
import { TemaService } from './nucleo/tema/tema.service';
import { IconoPestanaService } from './nucleo/marca/icono-pestana.service';
import { AvisoInactividad } from './disenio/aviso-inactividad';
import { AvisoLlamado } from './disenio/aviso-llamado';
import { PanelLateral } from './disenio/panel-lateral';
import { BarraSuperior } from './disenio/barra-superior';
import { PilaAvisos } from './disenio/pila-avisos';
import { SelectorIdioma } from './disenio/selector-idioma';

@Component({
  selector: 'app-root',
  imports: [
    RouterOutlet,
    AvisoInactividad,
    AvisoLlamado,
    PanelLateral,
    BarraSuperior,
    PilaAvisos,
    SelectorIdioma,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly sesion = inject(SesionService);
  protected readonly avisos = inject(AvisosService);
  protected readonly esMozo = computed(() => this.sesion.tieneAlgunRol(['MOZO']));

  constructor() {
    // Se construye al arrancar para aplicar el tema guardado antes de pintar
    // nada, tambien en la pantalla de entrar.
    inject(TemaService);
    // Construirlo es lo que pone el logo del local en la pestana al entrar y
    // devuelve el propio al salir.
    inject(IconoPestanaService);
  }
}
