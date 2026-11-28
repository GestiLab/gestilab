// Entorno de PRODUCCIÓN (el que usa `ng build`).
// Las direcciones viven aquí y en ningún otro archivo.
export const environment = {
  apiUrl: 'http://localhost:8080/api/v1/inventario-service',
  prestamosApiUrl: 'http://localhost:8090/api/v1/prestamos-service',
  // Keycloak: el realm y el client público de la aplicación (flujo Authorization Code + PKCE).
  keycloak: {
    issuer: 'http://localhost:8180/realms/gestilab',
    clientId: 'gestilab-web',
    // false solo porque Keycloak corre en http://localhost; con un servidor real debe ser true.
    requireHttps: false,
  },
};
