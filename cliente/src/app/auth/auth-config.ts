import { AuthConfig } from 'angular-oauth2-oidc';
import { environment } from '../../environments/environment';

// Configuración de la librería. Con responseType 'code' la librería usa
// Authorization Code + PKCE: la contraseña solo se escribe en Keycloak.
export const authConfig: AuthConfig = {
  issuer: environment.keycloak.issuer,
  clientId: environment.keycloak.clientId,
  // Vuelve a la raíz: allí está la pantalla de inicio, que es pública.
  // Si apuntara a una ruta protegida, la guarda podría provocar un bucle.
  redirectUri: window.location.origin + '/',
  postLogoutRedirectUri: window.location.origin + '/',
  responseType: 'code',
  scope: 'openid profile email',
  // Viene del entorno: es false solo porque Keycloak corre en http://localhost.
  // Con un servidor real debe ser true (el token viaja por HTTPS).
  requireHttps: environment.keycloak.requireHttps,
  showDebugInformation: false,
};
