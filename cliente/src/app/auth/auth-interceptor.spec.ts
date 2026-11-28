import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { OAuthService } from 'angular-oauth2-oidc';
import { authInterceptor } from './auth-interceptor';
import { AuthService } from './auth-service';
import { OAuthFalso, fabricarToken } from './oauth-falso';
import { environment } from '../../environments/environment';

describe('authInterceptor', () => {
  let oauth: OAuthFalso;
  let http: HttpClient;
  let controlador: HttpTestingController;
  let auth: AuthService;

  const INVENTARIO = `${environment.apiUrl}/unidades`;
  const PRESTAMOS = `${environment.prestamosApiUrl}/prestamos`;

  beforeEach(async () => {
    oauth = new OAuthFalso();
    oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
    TestBed.configureTestingModule({
      providers: [
        { provide: OAuthService, useValue: oauth },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    controlador = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    await auth.iniciar();
  });

  afterEach(() => controlador.verify());

  describe('CA-3 · el token viaja a los dos servicios', () => {
    it('a inventario lleva Authorization: Bearer', () => {
      http.get(INVENTARIO).subscribe();
      const peticion = controlador.expectOne(INVENTARIO);
      expect(peticion.request.headers.get('Authorization')).toBe(`Bearer ${oauth.token}`);
      peticion.flush({});
    });

    it('a préstamos lleva Authorization: Bearer', () => {
      http.get(PRESTAMOS).subscribe();
      const peticion = controlador.expectOne(PRESTAMOS);
      expect(peticion.request.headers.get('Authorization')).toBe(`Bearer ${oauth.token}`);
      peticion.flush({});
    });

    it('a cualquier otro destino NO lleva el token', () => {
      const otro = 'http://otro-servidor.test/datos';
      http.get(otro).subscribe();
      const peticion = controlador.expectOne(otro);
      expect(peticion.request.headers.has('Authorization')).toBe(false);
      peticion.flush({});
    });

    it('sin sesión no inventa cabecera', () => {
      oauth.token = null;
      http.get(INVENTARIO).subscribe({ error: () => undefined });
      const peticion = controlador.expectOne(INVENTARIO);
      expect(peticion.request.headers.has('Authorization')).toBe(false);
      peticion.flush({}, { status: 401, statusText: 'Unauthorized' });
    });
  });

  describe('CA-6 · el servicio rechaza el token', () => {
    it('un 401 con token deja la sesión vencida y el error llega a quien pidió', () => {
      let estado: number | undefined;
      http.get(INVENTARIO).subscribe({ error: e => (estado = e.status) });

      controlador.expectOne(INVENTARIO).flush({}, { status: 401, statusText: 'Unauthorized' });

      expect(estado).toBe(401);
      expect(auth.sesionVencida()).toBe(true);
      expect(auth.autenticado()).toBe(false);
    });

    it('un 401 de préstamos también', () => {
      http.get(PRESTAMOS).subscribe({ error: () => undefined });
      controlador.expectOne(PRESTAMOS).flush({}, { status: 401, statusText: 'Unauthorized' });
      expect(auth.sesionVencida()).toBe(true);
    });

    it('un 403 NO vence la sesión: la persona sí es quien dice ser', () => {
      http.get(INVENTARIO).subscribe({ error: () => undefined });
      controlador.expectOne(INVENTARIO).flush({}, { status: 403, statusText: 'Forbidden' });
      expect(auth.sesionVencida()).toBe(false);
      expect(auth.autenticado()).toBe(true);
    });

    it('un 401 de otro servidor no toca la sesión', () => {
      const otro = 'http://otro-servidor.test/datos';
      http.get(otro).subscribe({ error: () => undefined });
      controlador.expectOne(otro).flush({}, { status: 401, statusText: 'Unauthorized' });
      expect(auth.sesionVencida()).toBe(false);
    });
  });
});
