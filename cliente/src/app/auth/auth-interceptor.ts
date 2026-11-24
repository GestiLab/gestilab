import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../environments/environment';
import { AuthService } from './auth-service';

// Solo las peticiones a nuestros dos servicios llevan el token (CA-3).
// Mandarlo a cualquier otro destino regalaría la identidad de la persona.
const DESTINOS = [environment.apiUrl, environment.prestamosApiUrl];

export const authInterceptor: HttpInterceptorFn = (req, next) => {

  // 1. ¿Va a inventario o a préstamos? Si no, pasa intacta.
  if (!DESTINOS.some(destino => req.url.startsWith(destino))) {
    return next(req);
  }

  const auth = inject(AuthService);
  const token = auth.token;

  // 2. La petición es inmutable: se clona con la cabecera añadida.
  const peticion = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  // 3. Si el servicio rechaza un token que sí se envió, la sesión ya no sirve (CA-6).
  //    El error se vuelve a lanzar: quien pidió los datos debe enterarse.
  return next(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && token) auth.sesionRechazada();
      return throwError(() => error);
    })
  );
};
