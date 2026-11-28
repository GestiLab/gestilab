import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { AuthService } from './auth-service';

// CA-4: sin sesión no se entra a una ruta protegida; se lleva a iniciar sesión.
// Si la sesión acaba de vencer, no se redirige sola: la cabecera ya muestra el aviso (CA-6).
export const sesionIniciada: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.autenticado()) return true;
  if (!auth.sesionVencida()) auth.entrar();
  return false;
};
