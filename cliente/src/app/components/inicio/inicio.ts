import { Component, inject } from '@angular/core';
import { AuthService } from '../../auth/auth-service';

// Pantalla pública: a ella vuelve la persona al cerrar sesión (CA-7).
@Component({
  selector: 'app-inicio',
  template: `
    <section>
      <h2>Inicio</h2>
      @if (auth.autenticado()) {
        <p>Hola, {{ auth.usuario() }}. Usa el menú para ir a tus pantallas.</p>
      } @else {
        <p>Inicia sesión para ver las pantallas que corresponden a tu rol.</p>
      }
    </section>
  `,
})
export class Inicio {
  protected readonly auth = inject(AuthService);
}
