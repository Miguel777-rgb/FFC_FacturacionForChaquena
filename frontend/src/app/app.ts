import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SesionService } from './nucleo/sesion/sesion.service';
import { AvisosService } from './nucleo/http/avisos.service';
import { TemaService } from './nucleo/tema/tema.service';
import { PanelLateral } from './disenio/panel-lateral';
import { PilaAvisos } from './disenio/pila-avisos';
import { BarraIdiomas } from './disenio/barra-idiomas';
import { Confirmacion } from './disenio/confirmacion';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PanelLateral, PilaAvisos, BarraIdiomas, Confirmacion],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly sesion = inject(SesionService);
  protected readonly avisos = inject(AvisosService);

  constructor() {
    // Se construye al arrancar para aplicar el tema guardado antes de pintar
    // nada, tambien en la pantalla de entrar.
    inject(TemaService);
  }
}
