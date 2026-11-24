import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, provideRouter } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { sesionIniciada } from './sesion-iniciada';
import { OAuthFalso, fabricarToken } from './oauth-falso';
import { AuthService } from './auth-service';
import { Rol } from './roles';
describe('sesionIniciada', () => {
  let oauth: OAuthFalso;
  let auth: AuthService;

  const estado = {} as RouterStateSnapshot;
  const ruta = (roles?: Rol[]) => ({ data: { roles } }) as unknown as ActivatedRouteSnapshot;

  async function conSesion(roles: string[]) {
    oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles } });
    await auth.iniciar();
  }

  beforeEach(() => {
    oauth = new OAuthFalso();
    TestBed.configureTestingModule({
      providers: [{ provide: OAuthService, useValue: oauth }, provideRouter([])],
    });
    auth = TestBed.inject(AuthService);
  });

  describe('CA-4 · sesionIniciada', () => {
    it('sin sesión no deja pasar y lleva a iniciar sesión', async () => {
      await auth.iniciar();
      const resultado = TestBed.runInInjectionContext(() => sesionIniciada(ruta(), estado));
      expect(resultado).toBe(false);
      expect(oauth.llamadas).toContain('initCodeFlow');
    });

    it('con sesión deja pasar y no va a Keycloak', async () => {
      await conSesion(['usuario']);
      const resultado = TestBed.runInInjectionContext(() => sesionIniciada(ruta(), estado));
      expect(resultado).toBe(true);
      expect(oauth.llamadas).not.toContain('initCodeFlow');
    });

    it('con la sesión recién vencida no redirige sola: deja que se lea el aviso', async () => {
      await auth.iniciar();
      auth.sesionRechazada();
      const resultado = TestBed.runInInjectionContext(() => sesionIniciada(ruta(), estado));
      expect(resultado).toBe(false);
      expect(oauth.llamadas).not.toContain('initCodeFlow');
    });
  });
});
