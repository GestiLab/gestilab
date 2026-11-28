import { Component, ElementRef, OnInit, inject, viewChild } from '@angular/core';
import { RetrasosService } from '../../services/retrasos.service';

// Este componente SOLO muestra lo que dice el servicio y le avisa lo que la persona hace.
// Los días de retraso salen de la respuesta: aquí no se calcula nada con la fecha del navegador.
@Component({
  selector: 'app-retrasos',
  styles: [`
    button { min-height: 44px; padding: 0 0.75rem; }
    .tabla { overflow-x: auto; }
    table { border-collapse: collapse; width: 100%; }
    th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid #ddd; }
    .solo-lector { position: absolute; width: 1px; height: 1px; overflow: hidden;
                   clip: rect(0 0 0 0); white-space: nowrap; }
  `],
  template: `
    <section>
      <h2 #encabezado tabindex="-1">Préstamos retrasados</h2>

      <!-- Región siempre presente para que el lector de pantalla anuncie los cambios -->
      <p role="status" aria-live="polite">
        @if (retrasos.cargando()) {
          Cargando retrasos…
        } @else if (retrasos.sinRetrasos()) {
          No hay préstamos retrasados.
        } @else if (!retrasos.error()) {
          Hay {{ retrasos.retrasos().length }} {{ retrasos.retrasos().length === 1 ? 'préstamo retrasado' : 'préstamos retrasados' }}
        }
      </p>

      <!-- Error y reintento (CA-3) -->
      <div role="alert">
        @if (retrasos.error()) {
          <p>{{ retrasos.error() }}</p>
        }
      </div>
      @if (retrasos.error()) {
        <button type="button" (click)="reintentar()">Reintentar</button>
      }

      <!-- Lista (CA-1) -->
      @if (retrasos.mostrarLista()) {
        <div class="tabla">
          <table>
            <caption class="solo-lector">Préstamos retrasados</caption>
            <thead>
              <tr>
                <th scope="col">Unidad</th>
                <th scope="col">Persona</th>
                <th scope="col">Fecha de devolución</th>
                <th scope="col">Días de retraso</th>
              </tr>
            </thead>
            <tbody>
              @for (retraso of retrasos.retrasos(); track retraso.prestamoId) {
                <tr>
                  <td>{{ retraso.unidadId }}</td>
                  <td>{{ retraso.solicitanteId }}</td>
                  <td>{{ formatearFecha(retraso.fechaDevolucionPrevista) }}</td>
                  <td>{{ retraso.diasRetraso }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </section>
  `,
})
export class Retrasos implements OnInit {

  protected readonly retrasos = inject(RetrasosService);

  // El encabezado de la pantalla: a donde se manda el foco cuando el botón Reintentar desaparece.
  private readonly encabezado = viewChild<ElementRef<HTMLElement>>('encabezado');

  ngOnInit(): void {
    this.retrasos.cargar();
  }

  // Al pulsar Reintentar el error se limpia y el botón sale de la pantalla. Sin esto, quien usa
  // teclado o lector de pantalla se quedaría con el foco perdido. Se pasa al encabezado, y la
  // región de estado anuncia "Cargando retrasos…".
  protected reintentar(): void {
    this.retrasos.reintentar();
    this.encabezado()?.nativeElement.focus();
  }

  // "2026-11-20" → "20/11/2026". Solo reordena el texto: no usa Date, porque Date interpretaría
  // la fecha en otra zona horaria y podría mostrar el día anterior.
  protected formatearFecha(fecha: string): string {
    const partes = fecha.split('-');
    return partes.length === 3 ? partes.reverse().join('/') : fecha;
  }
}
