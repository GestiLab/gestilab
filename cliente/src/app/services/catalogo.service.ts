import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { environment } from '../../environments/environment';
import { PaginaUnidades, TipoEquipo, Unidad } from '../models/unidad';

// Unidades por página: el criterio de aceptación pide 20.
const TAMANO_PAGINA = 20;

// providedIn: 'root' → UNA sola instancia para toda la app. Así el estado
// del catálogo vive en un solo lugar y no se copia en cada componente.
@Injectable({ providedIn: 'root' })
export class CatalogoService {

  private readonly http = inject(HttpClient);

  // La dirección sale del entorno: cambiarla no exige tocar este código.
  private readonly apiUrl = `${environment.apiUrl}/unidades`;
  private readonly tiposUrl = `${environment.apiUrl}/tipos-equipo`;   // (propuesta) la lista aún no existe en inventario

  // ── Estado privado: solo este servicio puede escribirlo ─────────────────
  private readonly _unidades        = signal<Unidad[]>([]);
  private readonly _tipos           = signal<TipoEquipo[]>([]);
  private readonly _paginaActual    = signal(0);
  private readonly _tamano          = signal(TAMANO_PAGINA);
  private readonly _totalElementos  = signal(0);
  private readonly _cargando        = signal(false);
  private readonly _error           = signal<string | null>(null);
  private readonly _consultado      = signal(false);   // ya hubo al menos una respuesta
  // Filtros elegidos por el usuario
  private readonly _tipoId            = signal<number | null>(null);
  private readonly _soloDisponibles   = signal(false);
  private readonly _busqueda          = signal('');

  // Para cancelar la petición anterior si el usuario pide otra antes de que responda.
  private peticionActual?: Subscription;

  // ── API pública: señales de SOLO LECTURA ────────────────────────────────
  // Un componente las lee llamándolas (catalogo.cargando()) y no guarda copias.
  readonly unidades        = this._unidades.asReadonly();
  readonly tipos           = this._tipos.asReadonly();
  readonly paginaActual    = this._paginaActual.asReadonly();
  readonly totalElementos  = this._totalElementos.asReadonly();
  readonly cargando        = this._cargando.asReadonly();
  readonly error           = this._error.asReadonly();

  // ── Estado calculado (CA-9): se recalcula solo cuando cambian las señales ─
  // 120 unidades / 20 por página = 6 páginas. Sin resultados = 0 páginas.
  readonly totalPaginas = computed(() => Math.ceil(this._totalElementos() / this._tamano()));
  // Mientras llega una respuesta los botones quedan deshabilitados: evita saltar páginas con clics rápidos.
  readonly anteriorDeshabilitado  = computed(() => this._cargando() || this._paginaActual() <= 0);
  readonly siguienteDeshabilitado = computed(() => this._cargando() || this._paginaActual() + 1 >= this.totalPaginas());
  readonly sinResultados = computed(
    () => this._consultado() && !this._cargando() && !this._error() && this._totalElementos() === 0,
  );
  readonly mostrarLista = computed(
    () => this._consultado() && !this._cargando() && !this._error() && this._totalElementos() > 0,
  );

  // Pide una página al servicio de inventario y deja el resultado en las señales.
  cargarPagina(pagina = 0, tamano = TAMANO_PAGINA): void {
    this.peticionActual?.unsubscribe();   // si había una petición en vuelo, se descarta
    this._cargando.set(true);
    this._error.set(null);
    this._paginaActual.set(pagina);       // así "Reintentar" repite ESTA página
    this._tamano.set(tamano);

    this.peticionActual = this.http
      .get<PaginaUnidades>(this.apiUrl, { params: this.armarParametros(pagina, tamano) })
      .subscribe({
        next: (respuesta) => {
          this._unidades.set(respuesta.content);
          this._paginaActual.set(respuesta.number);
          this._totalElementos.set(respuesta.totalElements);
          this._cargando.set(false);
          this._consultado.set(true);
        },
        error: (err: HttpErrorResponse) => {
          this._error.set(this.describirError(err));
          this._cargando.set(false);
        },
      });
  }

  // (propuesta) GET /tipos-equipo para llenar el selector. Si falla, el selector queda
  // vacío pero el catálogo sigue funcionando.
  cargarTipos(): void {
    this.http.get<TipoEquipo[]>(this.tiposUrl).subscribe({
      next: (tipos) => this._tipos.set(tipos),
      error: () => this._tipos.set([]),
    });
  }

  // ── Acciones del usuario. Cambiar un filtro SIEMPRE vuelve a la página 1 ─
  cambiarTipo(tipoId: number | null): void {
    this._tipoId.set(tipoId);
    this.cargarPagina(0);
  }

  cambiarSoloDisponibles(valor: boolean): void {
    this._soloDisponibles.set(valor);
    this.cargarPagina(0);
  }

  buscar(texto: string): void {
    this._busqueda.set(texto);
    this.cargarPagina(0);
  }

  // Al entrar a la pantalla los controles aparecen vacíos, así que los filtros
  // guardados en este servicio (que vive toda la sesión) también se limpian.
  limpiarFiltros(): void {
    this._tipoId.set(null);
    this._soloDisponibles.set(false);
    this._busqueda.set('');
  }

  siguiente(): void {
    if (!this.siguienteDeshabilitado()) this.cargarPagina(this._paginaActual() + 1);
  }

  anterior(): void {
    if (!this.anteriorDeshabilitado()) this.cargarPagina(this._paginaActual() - 1);
  }

  reintentar(): void {
    this.cargarPagina(this._paginaActual());
  }

  // Solo se envían los filtros que el usuario realmente puso.
  // (propuesta) nombres de parámetros: tipoId, estado, nombre.
  private armarParametros(pagina: number, tamano: number): HttpParams {
    let params = new HttpParams().set('page', pagina).set('size', tamano);
    const tipoId = this._tipoId();
    if (tipoId !== null)           params = params.set('tipoId', tipoId);
    if (this._soloDisponibles())   params = params.set('estado', 'DISPONIBLE');
    if (this._busqueda() !== '')   params = params.set('nombre', this._busqueda());
    return params;
  }

  // El usuario no entiende "HttpErrorResponse status 0". Entiende un mensaje claro.
  private describirError(err: HttpErrorResponse): string {
    switch (err.status) {
      case 0:
        return 'No hay conexión con el servicio de inventario. Intenta de nuevo más tarde.';
      case 503:
        return 'El servicio de inventario no está disponible. Intenta más tarde.';
      default:
        // Los servicios devuelven { status, error, mensaje, timestamp }.
        return `Error ${err.status}: ${err.error?.mensaje ?? err.statusText}`;
    }
  }
}
