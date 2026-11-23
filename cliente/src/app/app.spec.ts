import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { OAuthService } from 'angular-oauth2-oidc';
import { App } from './app';
import { routes } from './app.routes';
import { AuthService } from './auth/auth-service';
import { OAuthFalso, fabricarToken } from './auth/oauth-falso';

describe('App', () => {
  let oauth: OAuthFalso;
  let fixture: ComponentFixture<App>;

  async function arrancar(roles?: string[]) {
    if (roles) oauth.token = fabricarToken({ preferred_username: 'persona.prueba', realm_access: { roles } });
    await TestBed.inject(AuthService).iniciar();
    fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  beforeEach(() => {
    oauth = new OAuthFalso();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: OAuthService, useValue: oauth },
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
  });

  it('muestra el título de la aplicación', async () => {
    const compiled = await arrancar();
    expect(compiled.querySelector('h1')?.textContent).toContain('GestiLab');
  });

  describe('cabecera', () => {
    it('sin sesión: ofrece iniciar sesión y no muestra el catálogo en el menú', async () => {
      const compiled = await arrancar();
      expect(compiled.querySelector('button')?.textContent).toContain('Iniciar sesión');
      expect(compiled.querySelector('nav')?.textContent).not.toContain('Catálogo');
    });

    it('CA-1: el botón Iniciar sesión manda a Keycloak', async () => {
      const compiled = await arrancar();
      compiled.querySelector('button')!.click();
      expect(oauth.llamadas).toContain('initCodeFlow');
    });

    it('CA-5: un usuario ve su nombre, su rol y la opción Catálogo', async () => {
      const compiled = await arrancar(['usuario']);
      expect(compiled.querySelector('header')?.textContent).toContain('Sesión de persona.prueba, rol usuario');
      expect(compiled.querySelector('nav')?.textContent).toContain('Catálogo');
    });

    it('CA-5: un auxiliar ve su rol y la opción Catálogo', async () => {
      const compiled = await arrancar(['auxiliar']);
      expect(compiled.querySelector('header')?.textContent).toContain('rol auxiliar');
      expect(compiled.querySelector('nav')?.textContent).toContain('Catálogo');
    });

    it('CA-5: una persona sin rol conocido no ve ninguna opción', async () => {
      const compiled = await arrancar([]);
      expect(compiled.querySelector('nav')?.textContent).not.toContain('Catálogo');
    });

    it('CA-7: Cerrar sesión la termina y vuelve a mostrar Iniciar sesión', async () => {
      const compiled = await arrancar(['usuario']);
      const boton = Array.from(compiled.querySelectorAll('button')).find(b => b.textContent?.includes('Cerrar sesión'))!;
      boton.click();
      await fixture.whenStable();
      expect(oauth.llamadas).toContain('logOut');
      expect(compiled.querySelector('button')?.textContent).toContain('Iniciar sesión');
    });
  });

  describe('avisos', () => {
    it('CA-6: con la sesión vencida muestra un mensaje claro', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      oauth.valido = false;
      const compiled = await arrancar();
      expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('Inicia sesión de nuevo');
    });

    it('si Keycloak no responde, lo dice en vez de quedarse en blanco', async () => {
      oauth.fallaElDescubrimiento = true;
      const compiled = await arrancar();
      expect(compiled.querySelector('[role="alert"]')?.textContent).toContain('No se pudo contactar');
    });
  });

  describe('rutas', () => {
    it('CA-4: abrir el catálogo sin sesión lleva a iniciar sesión y no entra', async () => {
      await TestBed.inject(AuthService).iniciar();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/catalogo');
      expect(oauth.llamadas).toContain('initCodeFlow');
      expect(TestBed.inject(Router).url).not.toBe('/catalogo');
    });

    it('CA-4/5: con sesión de usuario entra al catálogo', async () => {
      oauth.token = fabricarToken({ preferred_username: 'a', realm_access: { roles: ['usuario'] } });
      await TestBed.inject(AuthService).iniciar();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/catalogo');
      expect(TestBed.inject(Router).url).toBe('/catalogo');
    });

    it('CA-7: la raíz es pública y no pide iniciar sesión', async () => {
      await TestBed.inject(AuthService).iniciar();
      const harness = await RouterTestingHarness.create();
      await harness.navigateByUrl('/');
      expect(oauth.llamadas).not.toContain('initCodeFlow');
    });
  });
});
