import { Component, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, map, skip, startWith } from 'rxjs';
import { CatalogoService } from '../../services/catalogo.service';
import { ETIQUETA_ESTADO } from '../../models/unidad';

// Este componente SOLO muestra lo que dice el servicio y le avisa lo que el
// usuario hace. No guarda copias del estado ni decide reglas de negocio (CA-6).
@Component({
  selector: 'app-catalogo',
  styles: [`
    .filtros { display: flex; flex-wrap: wrap; gap: 1rem; align-items: end; margin-bottom: 1rem; }
    .filtros label { display: flex; flex-direction: column; gap: 0.25rem; }
    .filtros label.casilla { flex-direction: row; align-items: center; gap: 0.5rem; }
    button, select, input[type='search'] { min-height: 44px; padding: 0 0.75rem; }
    input[type='checkbox'] { width: 1.5rem; height: 1.5rem; }
    .tabla { overflow-x: auto; }
    table { border-collapse: collapse; width: 100%; }
    th, td { text-align: left; padding: 0.4rem 0.6rem; border-bottom: 1px solid #ddd; }
    .paginacion { display: flex; gap: 1rem; align-items: center; margin-top: 1rem; }
    .solo-lector { position: absolute; width: 1px; height: 1px; overflow: hidden;
                   clip: rect(0 0 0 0); white-space: nowrap; }
  `],
  template: `
    <section>
      <h2>Catálogo de equipos</h2>

      <!-- Filtros (CA-7, CA-8) -->
      <div class="filtros">
        <label>
          Tipo
          <select (change)="alElegirTipo($event)">
            <option value="">Todos</option>
            @for (tipo of catalogo.tipos(); track tipo.id) {
              <option [value]="tipo.id">{{ tipo.nombre }}</option>
            }
          </select>
        </label>

        <label>
          Buscar por nombre
          <input type="search" placeholder="Ej. multi" (input)="alEscribir($event)" />
        </label>

        <label class="casilla">
          <input type="checkbox" (change)="alMarcarDisponibles($event)" />
          Solo disponibles
        </label>
      </div>

      <!-- Regiones que siempre están en pantalla, para que el lector de pantalla anuncie los cambios (CA-4, CA-10) -->
      <p role="status" aria-live="polite">
        @if (catalogo.cargando()) {
          Cargando catálogo…
        } @else if (catalogo.sinResultados()) {
          No hay equipos que coincidan con los filtros.
        } @else if (!catalogo.error()) {
          Mostrando {{ catalogo.unidades().length }} de {{ catalogo.totalElementos() }} equipos
        }
      </p>

      <!-- Error y reintento (CA-5) -->
      <div role="alert">
        @if (catalogo.error()) {
          <p>{{ catalogo.error() }}</p>
        }
      </div>
      @if (catalogo.error()) {
        <button type="button" (click)="catalogo.reintentar()">Reintentar</button>
      }

      <!-- Lista (CA-1) -->
      @if (catalogo.mostrarLista()) {
        <div class="tabla">
          <table>
            <caption class="solo-lector">Equipos del catálogo</caption>
            <thead>
              <tr>
                <th scope="col">Tipo</th>
                <th scope="col">N.º de inventario</th>
                <th scope="col">Estado</th>
              </tr>
            </thead>
            <tbody>
              @for (unidad of catalogo.unidades(); track unidad.id) {
                <tr>
                  <td>{{ unidad.tipo.nombre }}</td>
                  <td>{{ unidad.numeroInventario }}</td>
                  <td>{{ etiquetaEstado[unidad.estado] ?? unidad.estado }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }

      <!-- Paginación (CA-2, CA-3, CA-9) -->
      <nav class="paginacion" aria-label="Paginación del catálogo">
        <button type="button" (click)="catalogo.anterior()" [disabled]="catalogo.anteriorDeshabilitado()">
          Anterior
        </button>
        <span aria-live="polite">
          @if (catalogo.totalPaginas() > 0) {
            Página {{ catalogo.paginaActual() + 1 }} de {{ catalogo.totalPaginas() }}
          }
        </span>
        <button type="button" (click)="catalogo.siguiente()" [disabled]="catalogo.siguienteDeshabilitado()">
          Siguiente
        </button>
      </nav>
    </section>
  `,
})
export class Catalogo implements OnInit {

  protected readonly catalogo = inject(CatalogoService);
  // Se tipa como parcial porque el backend puede traer un estado que el cliente aún no conoce.
  protected readonly etiquetaEstado: Record<string, string | undefined> = ETIQUETA_ESTADO;

  // Cada tecla entra aquí; solo pasa el último texto tras 300 ms de silencio (CA-8).
  private readonly texto$ = new Subject<string>();

  constructor() {
    this.texto$
      .pipe(
        map((texto) => texto.trim()),
        debounceTime(300),
        // El filtro arranca vacío: se siembra '' para que escribir solo espacios no cuente como cambio.
        startWith(''),
        distinctUntilChanged(),
        skip(1),                                              // descarta la semilla
        takeUntilDestroyed(),
      )
      .subscribe((texto) => this.catalogo.buscar(texto));
  }

  ngOnInit(): void {
    this.catalogo.cargarTipos();
    this.catalogo.limpiarFiltros();
    this.catalogo.cargarPagina();
  }

  alEscribir(evento: Event): void {
    this.texto$.next((evento.target as HTMLInputElement).value);
  }

  alElegirTipo(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.catalogo.cambiarTipo(valor === '' ? null : Number(valor));
  }

  alMarcarDisponibles(evento: Event): void {
    this.catalogo.cambiarSoloDisponibles((evento.target as HTMLInputElement).checked);
  }
}
