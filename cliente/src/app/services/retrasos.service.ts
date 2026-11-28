import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { environment } from '../../environments/environment';
import { Retraso } from '../models/retraso';

// providedIn: 'root' → una sola instancia para toda la app, igual que el catálogo.
@Injectable({ providedIn: 'root' })
export class RetrasosService {

  private readonly http = inject(HttpClient);

  // La dirección sale del entorno: cambiarla no exige tocar este código.
  private readonly apiUrl = `${environment.prestamosApiUrl}/prestamos/retrasos`;

  // ── Estado privado: solo este servicio puede escribirlo ─────────────────
  private readonly _retrasos   = signal<Retraso[]>([]);
  private readonly _cargando   = signal(false);
  private readonly _error      = signal<string | null>(null);
  private readonly _consultado = signal(false);   // ¿ya llegó al menos una respuesta?

  private peticionActual?: Subscription;

  // ── Estado público: de solo lectura ─────────────────────────────────────
  readonly retrasos = this._retrasos.asReadonly();
  readonly cargando = this._cargando.asReadonly();
  readonly error    = this._error.asReadonly();

  // Respondió bien y no hay préstamos vencidos.
  readonly sinRetrasos = computed(
    () => this._consultado() && !this._cargando() && !this._error() && this._retrasos().length === 0,
  );
  readonly mostrarLista = computed(
    () => this._consultado() && !this._cargando() && !this._error() && this._retrasos().length > 0,
  );

  // Pide la lista al servicio de préstamos. Los días de retraso se guardan tal como llegan.
  cargar(): void {
    this.peticionActual?.unsubscribe();   // si había una petición en vuelo, se descarta
    this._cargando.set(true);
    this._error.set(null);

    this.peticionActual = this.http.get<Retraso[]>(this.apiUrl).subscribe({
      next: (lista) => {
        this._retrasos.set(lista);
        this._cargando.set(false);
        this._consultado.set(true);
      },
      error: (err: HttpErrorResponse) => {
        this._error.set(this.describirError(err));
        this._cargando.set(false);
      },
    });
  }

  reintentar(): void {
    this.cargar();
  }

  // El auxiliar no entiende "HttpErrorResponse status 0". Entiende un mensaje claro.
  private describirError(err: HttpErrorResponse): string {
    switch (err.status) {
      case 0:
        return 'No hay conexión con el servicio de préstamos. Intenta de nuevo más tarde.';
      case 403:
        return 'No tienes permiso para ver los retrasos.';
      case 503:
        return 'El servicio de préstamos no está disponible. Intenta más tarde.';
      default:
        // Los servicios devuelven { status, error, mensaje, timestamp }.
        return `Error ${err.status}: ${err.error?.mensaje ?? err.statusText}`;
    }
  }
}
