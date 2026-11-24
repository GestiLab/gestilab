import { Subject } from 'rxjs';

// SOLO PARA PRUEBAS: reemplaza a OAuthService para que ninguna prueba
// necesite un Keycloak real (CA-8). No se usa en la aplicación.
export class OAuthFalso {
  readonly events = new Subject<{ type: string }>();
  // Qué hizo la aplicación con la librería, en orden.
  readonly llamadas: string[] = [];
  token: string | null = null;
  valido = true;
  fallaElDescubrimiento = false;

  configure(): void { this.llamadas.push('configure'); }
  async loadDiscoveryDocumentAndTryLogin(): Promise<void> {
    if (this.fallaElDescubrimiento) throw new Error('sin conexión');
  }
  setupAutomaticSilentRefresh(): void { this.llamadas.push('renovacion'); }
  initCodeFlow(): void { this.llamadas.push('initCodeFlow'); }
  logOut(sinRedireccion?: boolean): void {
    this.llamadas.push(sinRedireccion ? 'logOut-local' : 'logOut');
    this.token = null;
  }
  getAccessToken(): string { return this.token ?? ''; }
  hasValidAccessToken(): boolean { return this.token !== null && this.valido; }
}

// Fabrica un token con la forma de uno de Keycloak (sin firma válida: solo se lee).
export function fabricarToken(carga: object): string {
  const codificar = (objeto: object) => {
    const bytes = new TextEncoder().encode(JSON.stringify(objeto));
    const binario = Array.from(bytes, b => String.fromCharCode(b)).join('');
    return btoa(binario).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  };
  return `${codificar({ alg: 'RS256' })}.${codificar(carga)}.firma`;
}
