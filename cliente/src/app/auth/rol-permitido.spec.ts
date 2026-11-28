import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { rolPermitido } from './rol-permitido';
import { OAuthFalso, fabricarToken } from './oauth-falso';
import { AuthService } from './auth-service';
import { ROLES_CATALOGO, Rol } from './roles';
describe('rolPermitido', () => {
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

  describe('CA-5 · rolPermitido', () => {
    it.each(['usuario', 'auxiliar'])('deja pasar al rol %s en una ruta de ambos', async (rol) => {
      await conSesion([rol]);
      const resultado = TestBed.runInInjectionContext(() => rolPermitido(ruta(ROLES_CATALOGO), estado));
      expect(resultado).toBe(true);
    });

    it('un rol que la ruta no lista va a la pantalla de inicio', async () => {
      await conSesion(['usuario']);
      const resultado = TestBed.runInInjectionContext(() => rolPermitido(ruta(['auxiliar']), estado));
      expect(resultado instanceof UrlTree).toBe(true);
      expect((resultado as UrlTree).toString()).toBe('/');
    });

    it('sin rol va a la pantalla de inicio', async () => {
      await conSesion([]);
      const resultado = TestBed.runInInjectionContext(() => rolPermitido(ruta(ROLES_CATALOGO), estado));
      expect(resultado instanceof UrlTree).toBe(true);
    });

    it('una ruta que no declara roles no deja pasar a nadie', async () => {
      await conSesion(['auxiliar']);
      const resultado = TestBed.runInInjectionContext(() => rolPermitido(ruta(), estado));
      expect(resultado instanceof UrlTree).toBe(true);
    });
  });
});
