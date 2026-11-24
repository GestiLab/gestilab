// Entorno de DESARROLLO (el que usa `ng serve`).
// Mismo nombre de campos que environment.ts: solo cambian los valores.
export const environment = {
  apiUrl: 'http://localhost:8080/api/v1/inventario-service',
  prestamosApiUrl: 'http://localhost:8090/api/v1/prestamos-service',
  keycloak: {
    issuer: 'http://localhost:8180/realms/gestilab',
    clientId: 'gestilab-web',
    // false porque Keycloak corre en http://localhost. Con un servidor real debe ser true.
    requireHttps: false,
  },
};
