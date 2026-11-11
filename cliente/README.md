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

## Dirección de la API

La dirección vive solo en `src/environments/`:

- `environment.ts`: producción.
- `environment.development.ts`: desarrollo.

Para apuntar a otro servidor, cambia `apiUrl` en esos archivos; no hay que tocar código.

## Estructura

```
src/app/
  components/   una carpeta por pantalla (plantilla y estilos dentro del .ts)
  models/       forma de los datos del contrato
  services/     estado compartido con señales y llamadas a la API
```
Las pruebas (`*.spec.ts`) van junto al archivo que prueban.
