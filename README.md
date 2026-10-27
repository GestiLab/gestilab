
# GestiLab

**Sistema de préstamo de equipos de laboratorio.**

GestiLab es un sistema desarrollado por el equipo TEDE-SOFT para gestionar el inventario y los préstamos de equipos de laboratorio, controlar quién tiene cada unidad y durante cuánto tiempo, y conservar evidencias fotográficas de las entregas y devoluciones. Su atributo de calidad es la **modificabilidad (RQ-01)**, que busca facilitar la incorporación de nuevos tipos de equipo y sus reglas de préstamo sin modificar innecesariamente el código existente.

> Desarrollo de Software III · Equipo TEDE-SOFT · Atributo de calidad: **Modificabilidad (RQ-01)**.

## Arquitectura

GestiLab utiliza una arquitectura de dos microservicios independientes, cada uno con su propia base de datos. Se comunican mediante HTTP cuando necesitan una respuesta inmediata y mediante eventos cuando la información puede actualizarse de forma asíncrona.

```mermaid
flowchart LR
    UI[Cliente Angular] -->|HTTP| P[prestamos-service]
    P -->|GET /unidades/{id}| I[inventario-service]
    P -.->|Evento UnidadCambioDeEstado| B[(Corredor de mensajes)]
    B -.-> I
    P --- PDB[(BD de préstamos)]
    I --- IDB[(BD de inventario)]
    P --- S3[(Almacenamiento de objetos)]
    K[Keycloak] -.->|Emite el token| UI
```

| Servicio | Responsabilidad |
|---|---|
| `inventario-service` | Gestiona los tipos de equipo, las unidades, sus estados, el historial y las reglas de préstamo de cada tipo. |
| `prestamos-service` | Gestiona las solicitudes, las entregas, las devoluciones, los retrasos y el límite de unidades prestadas por persona. |

**Principio de diseño:** la duración máxima del préstamo y la necesidad de autorización previa son reglas asociadas al tipo de equipo y se administran en `inventario-service`. El servicio de préstamos consulta estas reglas y las aplica sin depender de una lista fija de tipos de equipo. Esta decisión favorece la modificabilidad y está registrada en `docs/bitacora.md` (D-01).


## Estructura del repositorio

```text
gestilab/
├── inventario-service/        # Tipos, unidades, estados y reglas
├── prestamos-service/         # Solicitudes, entregas, devoluciones y retrasos
├── docs/
│   ├── acuerdos-previos.md    # Acuerdos iniciales y guía de aplicación
│   ├── contrato/
│   │   └── openapi.yaml       # Contrato de GET /unidades/{id}
│   ├── guia-errores.md        # Formato común de errores
│   └── bitacora.md            # Decisiones de diseño
└── README.md
```

Cada servicio tiene su propia carpeta, su propio `pom.xml` y su propia base de datos. **Ningún servicio consulta directamente las tablas del otro**: cuando necesita información externa a su responsabilidad, la solicita mediante HTTP o la recibe a través de un evento.

## Versiones y herramientas

El equipo utilizará las siguientes versiones de referencia:

| Herramienta | Versión |
|---|---|
| Java (Eclipse Temurin JDK) | 21 LTS |
| Maven | 3.9.x |
| Spring Boot | Versión estable seleccionada para el proyecto |
| PostgreSQL | 16 (`postgres:16-alpine`) |
| Docker | Versión compatible con Docker Compose |
| Git | 2.x |

**Spring Boot:** la versión seleccionada para `inventario-service` será la referencia para `prestamos-service`. Ambos servicios utilizarán la misma versión, que se confirmará al crear el primer servicio.

Imágenes de referencia para Docker y CI:

- Compilación: `maven:3.9-eclipse-temurin-21-alpine`
- Ejecución: `eclipse-temurin:21-jre-alpine`
- Base de datos: `postgres:16-alpine`

Acciones previstas para GitHub Actions: `actions/checkout@v4`, `actions/setup-java@v4` y `docker/build-push-action@v6`. La publicación de imágenes se realizará en GitHub Container Registry (GHCR).

## Puertos

| Servicio | API REST | Actuator |
|---|---:|---:|
| `inventario-service` | 8080 | 8081 |
| `prestamos-service` | 8090 | 8091 |

## Convenciones de código

- **Organización por capas:** `domain/` contiene la lógica del dominio y sus excepciones; `delivery/` contiene los controladores REST y el manejo de errores.
- **Separación de responsabilidades:** el dominio no debe depender de la capa `delivery/`.
- **Rutas REST:** utilizar el prefijo `/api/v1/` seguido del nombre del servicio y del recurso.
- **Manejo de errores:** seguir el formato definido en `docs/guia-errores.md`.
- **Variables de entorno:** almacenar las credenciales y las URL de conexión en variables de entorno.
- **Protección de secretos:** no subir archivos `.env` al repositorio. Mantener un `.env.example` con los nombres de las variables y valores de ejemplo sin credenciales reales.

## Flujo de trabajo en equipo

1. `main` debe mantenerse compilable y con las pruebas aprobadas. No se permite hacer `push` directo a esta rama.
2. Cada tarea se desarrolla en una rama corta y específica, por ejemplo, `docs/acuerdos-previos` o `feat/contrato-unidad`.
3. Todos los cambios se integran mediante *pull requests*, revisados y aprobados por al menos una persona distinta de quien realizó el cambio.
4. Los commits deben tener mensajes claros y utilizar los siguientes prefijos:
   - `feat:` nuevas funcionalidades.
   - `fix:` correcciones de errores.
   - `docs:` cambios en la documentación.
   - `test:` incorporación o modificación de pruebas.
   - `ci:` cambios en integración continua.

## Documentación

| Documento | Propósito |
|---|---|
| [`docs/acuerdos-previos.md`](docs/acuerdos-previos.md) | Explica los acuerdos iniciales y cómo aplicarlos. |
| [`docs/contrato/openapi.yaml`](docs/contrato/openapi.yaml) | Define el contrato del endpoint `GET /unidades/{id}`. |
| [`docs/guia-errores.md`](docs/guia-errores.md) | Define el formato común de errores y su catálogo. |
| [`docs/bitacora.md`](docs/bitacora.md) | Registra las decisiones de diseño, sus justificaciones y las alternativas descartadas. |


## Equipo

**TEDE-SOFT**

Proyecto académico de la asignatura Desarrollo de Software III.
