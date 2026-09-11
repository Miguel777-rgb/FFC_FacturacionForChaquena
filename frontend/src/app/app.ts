import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SesionService } from './nucleo/sesion/sesion.service';
import { AvisosService } from './nucleo/http/avisos.service';
import { PanelLateral } from './disenio/panel-lateral';
import { PilaAvisos } from './disenio/pila-avisos';
import { BarraIdiomas } from './disenio/barra-idiomas';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PanelLateral, PilaAvisos, BarraIdiomas],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected readonly sesion = inject(SesionService);
  protected readonly avisos = inject(AvisosService);
}
