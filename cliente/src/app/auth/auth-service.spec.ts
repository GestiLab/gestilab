import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { AuthService } from './auth-service';
import { OAuthFalso, fabricarToken } from './oauth-falso';

describe('AuthService', () => {
  let oauth: OAuthFalso;

  function crear(): AuthService {
    TestBed.configureTestingModule({
      providers: [{ provide: OAuthService, useValue: oauth }],
    });
    return TestBed.inject(AuthService);
  }

  beforeEach(() => {
    oauth = new OAuthFalso();
  });

  describe('CA-2 · el rol se lee de señales', () => {
    it('sin sesión: no hay usuario ni rol', async () => {
      const auth = crear();
      await auth.iniciar();
      expect(auth.autenticado()).toBe(false);
      expect(auth.usuario()).toBe('');
      expect(auth.rol()).toBeNull();
    });

    it('un usuario con el rol usuario', async () => {
      oauth.token = fabricarToken({ preferred_username: 'usuario.prueba', realm_access: { roles: ['usuario'] } });
      const auth = crear();
      await auth.iniciar();
      expect(auth.autenticado()).toBe(true);
      expect(auth.usuario()).toBe('usuario.prueba');
      expect(auth.rol()).toBe('usuario');
    });

    it('un auxiliar con el rol auxiliar', async () => {
      oauth.token = fabricarToken({ preferred_username: 'auxiliar.prueba', realm_access: { roles: ['auxiliar'] } });
      const auth = crear();
      await auth.iniciar();
      expect(auth.rol()).toBe('auxiliar');
    });

    it('si trae los dos roles, manda auxiliar', async () => {
      oauth.token = fabricarToken({ preferred_username: 'x', realm_access: { roles: ['usuario', 'auxiliar'] } });
      const auth = crear();
      await auth.iniciar();
      expect(auth.rol()).toBe('auxiliar');
    });

    it('un token sin realm_access no rompe: queda sin rol', async () => {
      oauth.token = fabricarToken({ preferred_username: 'x' });
      const auth = crear();
      await auth.iniciar();
      expect(auth.autenticado()).toBe(true);
      expect(auth.roles()).toEqual([]);
      expect(auth.rol()).toBeNull();
    });

    it('lee bien un nombre con tildes', async () => {
      oauth.token = fabricarToken({ preferred_username: 'José Núñez', realm_access: { roles: ['usuario'] } });
      const auth = crear();
      await auth.iniciar();
      expect(auth.usuario()).toBe('José Núñez');
    });

    it('un token ilegible se trata como sin sesión', async () => {
      oauth.token = 'esto-no-es-un-token';
      const auth = crear();
      await auth.iniciar();
      expect(auth.autenticado()).toBe(false);
    });

    it('cuando la renovación silenciosa trae un token nuevo, la señal se actualiza', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      const auth = crear();
      await auth.iniciar();
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['auxiliar'] } });
      oauth.events.next({ type: 'token_received' });
      expect(auth.rol()).toBe('auxiliar');
    });
  });

  describe('CA-1 · iniciar sesión', () => {
    it('entrar() manda a Keycloak con el flujo de código', () => {
      const auth = crear();
      auth.entrar();
      expect(oauth.llamadas).toContain('initCodeFlow');
    });

    it('al arrancar configura la librería y programa la renovación', async () => {
      const auth = crear();
      await auth.iniciar();
      expect(oauth.llamadas).toEqual(['configure', 'renovacion']);
    });

    it('con Keycloak caído, entrar() no llama a la librería y el aviso sigue visible', async () => {
      oauth.fallaElDescubrimiento = true;
      const auth = crear();
      await auth.iniciar();
      auth.entrar();
      expect(oauth.llamadas).not.toContain('initCodeFlow');
      expect(auth.sinConexion()).toBe(true);
    });

    it('si Keycloak no responde, la app arranca sin sesión y lo avisa', async () => {
      oauth.fallaElDescubrimiento = true;
      const auth = crear();
      await auth.iniciar();
      expect(auth.sinConexion()).toBe(true);
      expect(auth.autenticado()).toBe(false);
    });
  });

  describe('CA-6 · sesión vencida', () => {
    it('un token guardado que ya venció deja la sesión vencida', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      oauth.valido = false;
      const auth = crear();
      await auth.iniciar();
      expect(auth.autenticado()).toBe(false);
      expect(auth.sesionVencida()).toBe(true);
    });

    it('sesionRechazada() borra la sesión local, avisa y no va a Keycloak', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      const auth = crear();
      await auth.iniciar();

      auth.sesionRechazada();

      expect(auth.autenticado()).toBe(false);
      expect(auth.sesionVencida()).toBe(true);
      expect(oauth.llamadas).toContain('logOut-local');
      expect(oauth.llamadas).not.toContain('logOut');
    });

    it('sesionRechazada() lleva a Inicio para no dejar una pantalla protegida abierta', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      const auth = crear();
      await auth.iniciar();
      const irA = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      auth.sesionRechazada();

      expect(irA).toHaveBeenCalledWith('/');
    });

    it('si falla la renovación silenciosa, la sesión queda vencida', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      const auth = crear();
      await auth.iniciar();
      oauth.events.next({ type: 'token_refresh_error' });
      expect(auth.sesionVencida()).toBe(true);
      expect(auth.autenticado()).toBe(false);
    });
  });

  describe('CA-7 · cerrar sesión', () => {
    it('salir() vacía la sesión y cierra en Keycloak', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['auxiliar'] } });
      const auth = crear();
      await auth.iniciar();
      expect(auth.autenticado()).toBe(true);

      auth.salir();

      expect(auth.autenticado()).toBe(false);
      expect(auth.rol()).toBeNull();
      expect(oauth.llamadas).toContain('logOut');
    });

    it('salir() también quita un aviso de sesión vencida anterior', () => {
      const auth = crear();
      auth.sesionRechazada();
      expect(auth.sesionVencida()).toBe(true);
      auth.salir();
      expect(auth.sesionVencida()).toBe(false);
    });
  });

  describe('token', () => {
    it('se pregunta a la librería en el momento y es null si no hay', async () => {
      const auth = crear();
      expect(auth.token).toBeNull();
      oauth.token = 'abc.def.ghi';
      expect(auth.token).toBe('abc.def.ghi');
    });
  });
});
