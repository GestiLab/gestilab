# Cliente Angular · GestiLab

Interfaz web de GestiLab (préstamo de equipos de laboratorio). Usa componentes standalone y señales; no usa NgModule ni NgRx.

## Requisitos

- Node 20.19 o superior
- npm 11 (`npm install -g npm@11` si tu versión es anterior)

## Comandos

Todos se ejecutan dentro de `cliente/`.

| Comando | Qué hace |
|---|---|
| `npm install` | Instala las dependencias |
| `npm start` | Levanta la app en http://localhost:4200 (usa `environment.development.ts`) |
| `npm test -- --watch=false` | Ejecuta las pruebas una vez |
| `npm run build` | Compila para producción (usa `environment.ts`) |

## CI

El job `cliente` de `.github/workflows/ci.yml` corre en cada push y pull request, con Node 24 (npm 11): `npm ci`, `npm test -- --watch=false` y `npm run build`. Si una prueba o la compilación falla, el job termina en `failure`.

## Dirección de la API

La dirección vive solo en `src/environments/`:

- `environment.ts`: producción.
- `environment.development.ts`: desarrollo.

Para apuntar a otro servidor, cambia `apiUrl` en esos archivos; no hay que tocar código.

## Estructura

```
src/app/
  auth/         inicio de sesión con Keycloak: servicio de sesión, interceptor y guardas
  components/   una carpeta por pantalla (plantilla y estilos dentro del .ts)
  models/       forma de los datos del contrato
  services/     estado compartido con señales y llamadas a la API
```
Las pruebas (`*.spec.ts`) van junto al archivo que prueban.
