# Guía de errores

Formato común de las respuestas de error de GestiLab. Lo implementan hoy `inventario-service` y `prestamos-service` con clases idénticas (salvo el paquete).

## Por qué existe esta guía

Sin un formato común, cada servicio respondería sus errores a su manera, y el cliente Angular tendría que interpretar cada caso por separado. Con esta guía, todo error tiene la misma forma: quien consume la API siempre sabe dónde leer el código y el mensaje.

> **Regla:** los servicios nuevos **copian este formato** tal cual: las mismas clases `ApiErrorResponse` y `GlobalExceptionHandler` (en `delivery/exception/`) y las mismas excepciones `RecursoNoEncontradoException` y `ReglaNegocioException` (en `domain/exception/`), cambiando solo el paquete. No se inventan campos ni códigos nuevos por servicio. Si hace falta uno, se acuerda aquí primero.

## Formato del cuerpo

Toda respuesta de error es un JSON con estos campos:

| Campo | Tipo | Presencia | Descripción |
|---|---|---|---|
| `status` | número | siempre | Código HTTP de la respuesta. |
| `error` | texto | siempre | Frase estándar del código HTTP (`Not Found`, `Conflict`, `Bad Request`...). |
| `mensaje` | texto | siempre | Explicación legible para la persona que consume la API. |
| `timestamp` | texto | siempre | Fecha y hora del servidor en ISO-8601, **sin zona horaria** (por ejemplo `2026-11-01T10:40:29.843437`). |
| `errores` | objeto | **solo en 400 de validación** (cuerpo o parámetro de ruta) | Mapa `campo → mensaje`. En los demás casos el campo no aparece (no se envía como `null`). |

Reglas del formato:
- El cuerpo **nunca** incluye stack trace, nombres de clases ni detalles internos.
- En `errores` se guarda un solo mensaje por campo: el primero que reporte la validación.
- Las rutas de la API van en `/api/v1/...`; el formato aplica a todas ellas y también a rutas inexistentes.

## Catálogo

### Cómo elegir el código

- **400:** la petición está mal formada. Ejemplo: el número de inventario llega vacío.
- **404:** lo que se pide no existe. Ejemplo: no hay una unidad con id 99.
- **409:** la petición está bien, pero una regla de negocio no la permite. Ejemplo: el número de inventario MULT-014 ya existe.
- **503:** el servicio funciona, pero otro del que depende no responde. Ejemplo: `prestamos-service` no obtiene respuesta de inventario.
- **500:** ocurrió un error que nadie previó. El mensaje es genérico porque el detalle real podría revelar información interna, como rutas o la estructura de la base de datos.

### Tabla de errores

| HTTP | Cuándo ocurre | Origen | `mensaje` |
|---|---|---|---|
| 404 | No existe el recurso pedido. | `RecursoNoEncontradoException` | El texto con que se lanzó la excepción. |
| 409 | Se viola una regla de negocio. | `ReglaNegocioException` | El texto con que se lanzó la excepción. |
| 400 | El cuerpo no cumple las validaciones (`@Valid`). | `MethodArgumentNotValidException` | `Datos de entrada inválidos` (más `errores`). |
| 400 | El cuerpo no es un JSON legible. | `HttpMessageNotReadableException` | `El cuerpo de la petición no es un JSON válido` |
| 400 | Un parámetro de la ruta no tiene el formato esperado (por ejemplo `/prestamos/abc`). | `MethodArgumentTypeMismatchException` | `Parámetro de ruta inválido` (más `errores`). |
| 404, 405, ... | Spring ya sabe qué código corresponde: por ejemplo, la ruta no existe (404) o el método no está permitido (405). | Excepciones que implementan `ErrorResponse` | La frase estándar del código (igual que `error`). |
| 503 | `prestamos-service` no logra respuesta de `inventario-service` (sin respuesta, tiempo agotado, 5xx o una respuesta que incumple el contrato). | `DependenciaNoDisponibleException` (implementada en `prestamos-service`) | El texto con que se lanzó la excepción: `inventario no disponible` (así lo exige SCRUM-14, CA-5). |
| 500 | Cualquier otra excepción no prevista. | `Exception` | `Error inesperado. Por favor contacta al administrador.` |

Cómo usarlo desde el código:
- Lanza `RecursoNoEncontradoException("Unidad 99 no encontrada")` cuando algo no existe. El mensaje debe decir qué recurso y cuál identificador.
- Lanza `ReglaNegocioException("...")` cuando la petición es válida pero el negocio no la permite.
- Ambas excepciones viven en `domain/`, que no depende de Spring Web. El handler que las traduce a HTTP vive en `delivery/`.

## Ejemplos

### 404: recurso no encontrado
```json
{
  "status": 404,
  "error": "Not Found",
  "mensaje": "Unidad 99 no encontrada",
  "timestamp": "2026-11-01T10:40:29.843437"
}
```

### 409: regla de negocio (ejemplo: número de inventario repetido)
```json
{
  "status": 409,
  "error": "Conflict",
  "mensaje": "Ya existe una unidad con número de inventario MULT-014",
  "timestamp": "2026-11-01T10:40:29.843437"
}
```

### 400: validación de campos
Con un cuerpo `{"nombre":""}` y un campo `nombre` marcado `@NotBlank(message = "no puede estar vacío")`:
```json
{
  "status": 400,
  "error": "Bad Request",
  "mensaje": "Datos de entrada inválidos",
  "timestamp": "2026-11-01T10:40:29.843437",
  "errores": {
    "nombre": "no puede estar vacío"
  }
}
```

### 400: JSON ilegible
```json
{
  "status": 400,
  "error": "Bad Request",
  "mensaje": "El cuerpo de la petición no es un JSON válido",
  "timestamp": "2026-11-01T10:40:29.843437"
}
```

### 404: ruta inexistente (Spring ya sabe qué código corresponde)
```json
{
  "status": 404,
  "error": "Not Found",
  "mensaje": "Not Found",
  "timestamp": "2026-11-01T10:40:29.843437"
}
```

### 503: servicio dependiente no disponible
Lo devuelve `prestamos-service` cuando su llamada a `inventario-service` falla. Si inventario responde 404, prestamos responde 404; si no responde, se agota el tiempo o devuelve 5xx, prestamos responde 503 y **no reenvía** el cuerpo que recibió, porque podría contener detalles internos de inventario.
```json
{
  "status": 503,
  "error": "Service Unavailable",
  "mensaje": "inventario no disponible",
  "timestamp": "2026-11-01T10:40:29.843437"
}
```

### 500: error inesperado
```json
{
  "status": 500,
  "error": "Internal Server Error",
  "mensaje": "Error inesperado. Por favor contacta al administrador.",
  "timestamp": "2026-11-01T10:40:29.843437"
}
```
El detalle y el stack trace **solo se escriben en el log del servidor** (`log.error`); nunca llegan al cliente.

## Pruebas de referencia

`GlobalExceptionHandlerTest` (en cada servicio) comprueba con un controlador de prueba que el formato no cambie: un 404 con el formato común y sin `trace`, y un 400 que indica el campo en `errores`. Un servicio nuevo debe incluir la misma prueba.
