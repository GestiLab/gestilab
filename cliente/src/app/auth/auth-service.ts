import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { OAuthService } from 'angular-oauth2-oidc';
import { authConfig } from './auth-config';
import { Rol } from './roles';

// Lo único que se usa del token. Los campos son opcionales: un token sin roles
// no trae realm_access y el código no debe romperse.
interface CargaDelToken {
  preferred_username?: string;
  realm_access?: { roles?: string[] };
}

// Abre el contenido del token (el bloque del medio, en base64url).
// Leer no es comprobar: sirve para dibujar la pantalla; quien autoriza es cada servicio.
function leerCarga(token: string): CargaDelToken | null {
  try {
    const base64url = token.split('.')[1];
    const base64 = base64url.replace(/-/g, '+').replace(/_/g, '/');
    const relleno = '='.repeat((4 - (base64.length % 4)) % 4);
    const texto = new TextDecoder().decode(Uint8Array.from(atob(base64 + relleno), c => c.charCodeAt(0)));
    return JSON.parse(texto) as CargaDelToken;
  } catch {
    return null;
  }
}

// UNA sola instancia para toda la app: la cabecera, las guardas y el interceptor
// ven la misma sesión. El estado vive en señales; nadie guarda copias (CA-2).
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly oauth = inject(OAuthService);
  private readonly router = inject(Router);

  // ── Estado privado: solo este servicio lo escribe ───────────────────────
  private readonly _carga = signal<CargaDelToken | null>(null);
  private readonly _sesionVencida = signal(false);
  private readonly _sinConexion = signal(false);

  // ── Lo que el resto de la app consulta, todo derivado de lo anterior ────
  readonly autenticado = computed(() => this._carga() !== null);
  readonly usuario = computed(() => this._carga()?.preferred_username ?? '');
  readonly roles = computed(() => this._carga()?.realm_access?.roles ?? []);
  // El rol con el que se decide qué ve la persona. Si trae los dos, manda auxiliar.
  readonly rol = computed<Rol | null>(() => {
    const roles = this.roles();
    if (roles.includes('auxiliar')) return 'auxiliar';
    if (roles.includes('usuario')) return 'usuario';
    return null;
  });
  readonly sesionVencida = this._sesionVencida.asReadonly();
  readonly sinConexion = this._sinConexion.asReadonly();

  // Se llama una vez al arrancar la app, antes de pintar la primera pantalla.
  async iniciar(): Promise<void> {
    this.oauth.configure(authConfig);
    this.oauth.events.subscribe(evento => {
      if (evento.type === 'token_received') this.sincronizar();
      if (evento.type === 'token_refresh_error') this.sesionRechazada();
    });
    try {
      // Descarga las direcciones del realm y, si la página vuelve de Keycloak,
      // canjea el código por el token. Sin código, no hace nada.
      await this.oauth.loadDiscoveryDocumentAndTryLogin();
      this.oauth.setupAutomaticSilentRefresh();
    } catch {
      // Keycloak no responde: la app arranca sin sesión y lo avisa (no se queda en blanco).
      this._sinConexion.set(true);
    }
    this.sincronizar();
  }

  // CA-1: manda el navegador a Keycloak (PKCE). La página actual deja de existir.
  // Si Keycloak no respondió al arrancar, la librería quedaría esperando sin hacer nada:
  // mejor no llamarla y dejar visible el aviso de "sin conexión".
  entrar(): void {
    if (this._sinConexion()) return;
    this.oauth.initCodeFlow();
  }

  // CA-7: primero se vacía el estado propio y luego se cierra la sesión en Keycloak,
  // que devuelve a la pantalla de inicio.
  salir(): void {
    this._carga.set(null);
    this._sesionVencida.set(false);
    this.oauth.logOut();
  }

  // CA-6: el servicio rechazó el token (401) o la renovación falló.
  // Se borra la sesión local (sin ir a Keycloak) y se avisa a la persona.
  // Las guardas solo corren al navegar: se vuelve a Inicio para no dejar a la persona
  // viendo una pantalla protegida sin sesión.
  sesionRechazada(): void {
    this._carga.set(null);
    this._sesionVencida.set(true);
    this.oauth.logOut(true);
    void this.router.navigateByUrl('/');
  }

  // Getter: se pregunta el token en el momento, no se guarda copia, porque la
  // renovación silenciosa lo cambia sin avisar.
  get token(): string | null {
    return this.oauth.getAccessToken() || null;
  }

  // Único puente entre la librería y las señales.
  private sincronizar(): void {
    const token = this.oauth.getAccessToken();
    if (!token) {
      this._carga.set(null);
      return;
    }
    if (!this.oauth.hasValidAccessToken()) {
      // Había un token guardado pero ya venció.
      this._carga.set(null);
      this._sesionVencida.set(true);
      return;
    }
    this._carga.set(leerCarga(token));
  }
}
