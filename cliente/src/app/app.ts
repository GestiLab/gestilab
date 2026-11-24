import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { AuthService } from './auth/auth-service';
import { OPCIONES_MENU } from './auth/roles';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  styles: [`
    header { display: flex; flex-wrap: wrap; gap: 1rem; align-items: center;
             justify-content: space-between; border-bottom: 1px solid #ddd; padding-bottom: 0.5rem; }
    nav ul { display: flex; gap: 1rem; list-style: none; margin: 0; padding: 0; }
    .sesion { display: flex; gap: 0.75rem; align-items: center; }
    button { min-height: 44px; padding: 0 0.75rem; }
    .aviso { background: #fff4e5; border: 1px solid #f0b46c; padding: 0.5rem 0.75rem; margin: 0.75rem 0; }
  `],
  template: `
    <header>
      <nav aria-label="Principal">
        <ul>
          <li><a routerLink="/">Inicio</a></li>
          @for (opcion of opciones(); track opcion.ruta) {
            <li><a [routerLink]="opcion.ruta">{{ opcion.texto }}</a></li>
          }
        </ul>
      </nav>

      <div class="sesion">
        @if (auth.autenticado()) {
          <span>Sesión de {{ auth.usuario() }}, rol {{ auth.rol() ?? 'sin rol' }}</span>
          <button type="button" (click)="auth.salir()">Cerrar sesión</button>
        } @else {
          <button type="button" (click)="auth.entrar()">Iniciar sesión</button>
        }
      </div>
    </header>

    <!-- Región siempre presente para que el lector de pantalla anuncie los avisos -->
    <div role="alert">
      @if (auth.sesionVencida()) {
        <p class="aviso">Tu sesión venció o fue rechazada. Inicia sesión de nuevo para continuar.</p>
      }
      @if (auth.sinConexion()) {
        <p class="aviso">No se pudo contactar al servicio de inicio de sesión. Intenta de nuevo más tarde.</p>
      }
    </div>

    <main>
      <h1>GestiLab</h1>
      <router-outlet />
    </main>
  `,
})
export class App {
  protected readonly auth = inject(AuthService);

  // CA-5: solo las opciones del rol de la persona. Se recalcula sola al entrar o salir.
  protected readonly opciones = computed(() => {
    const rol = this.auth.rol();
    return OPCIONES_MENU.filter(opcion => rol !== null && opcion.roles.includes(rol));
  });
}
