import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth-service';
import { Rol } from './roles';

// CA-5: la ruta declara en `data.roles` quién puede verla. Con otro rol, a la pantalla de inicio.
// Esto es comodidad: la autorización real la hace cada servicio.
export const rolPermitido: CanActivateFn = (ruta) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const permitidos = (ruta.data['roles'] as Rol[] | undefined) ?? [];
  const rol = auth.rol();
  return rol !== null && permitidos.includes(rol) ? true : router.parseUrl('/');
};
