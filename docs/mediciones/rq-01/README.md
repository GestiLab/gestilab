# Medición RQ-01 · Modificabilidad

Cuánto cuesta agregar un tipo de equipo con una regla nueva. La hipótesis del diseño (decisión D-01) es que las
reglas viajan con el tipo, así que un tipo nuevo se crea como dato, sin tocar `prestamos-service` ni escribir código
en `inventario-service`. Esta medición lo demuestra con números: **cuántos archivos cambian y cuántos minutos tarda**.

## Qué se hace y cuándo cumple

Se parte de una **etiqueta de git** sobre el sistema terminado. Con el sistema levantado, se crea por la API
administrativa de inventario, con un token de `auxiliar`, un tipo con **duración distinta** de los sembrados
(7, 1 y 3 días; por omisión 5) y **autorización obligatoria**, más una unidad de ese tipo. Después se solicita esa
unidad, el auxiliar la autoriza y se comprueba la fecha de devolución.

| Medición | Cumple si | Archivo |
|---|---|---|
| **Punto de partida** (CA-1) | La etiqueta existe, y el repositorio está parado en ella | `etiqueta.txt` |
| **Tipo operativo** (CA-2) | El tipo nuevo y su unidad quedan operativos en < 30 min | `tiempos.txt`, `procedimiento.txt` |
| **Cero cambios en préstamos** (CA-3) | `git diff` frente a la etiqueta no toca ningún archivo de `prestamos-service` | `git-diff-stat.txt`, `archivos-modificados.txt` |
| **Inventario sin código** (CA-4) | Ningún archivo de código cambia en `inventario-service`; a lo sumo 1 registro de datos | `archivos-modificados.txt` |
| **El tipo funciona** (CA-5) | La solicitud queda `PENDIENTE_AUTORIZACION`, al autorizarla pasa a `SOLICITADO` y la devolución es a la duración del tipo | `procedimiento.txt` |
| **Pruebas en verde** (CA-6) | El conjunto completo de los dos servicios pasa, con el cambio hecho | `pruebas.txt`, `maven-<servicio>.log` |

Un **registro de datos** es una línea nueva `new SemillaTipo(...)` en la siembra de inventario, o una línea
agregada en `inventario-service/src/main/resources/data/`. Cualquier otro cambio en un servicio cuenta como código
(también editar o borrar un registro existente). Crear el tipo por la API no cambia ningún archivo: es el mejor caso.

## Cómo repetirla

Requisitos: el sistema terminado levantado con `docker compose up`, `bash`, `git`, `curl`, `python3`, Java 21 y Maven.

1. **Etiqueta** (CA-1): sobre el `main` terminado, y con el árbol sin cambios:
   ```bash
   git tag -a rq01-base -m "Punto de partida de la medición RQ-01"
   git push origin rq01-base
   ```
2. **Tokens** de un `auxiliar` y de un `usuario` desde Keycloak, con el procedimiento que documente la historia del realm
   (SCRUM-13). Sin seguridad activa se pueden omitir.
3. **Cronómetro:** al empezar, `export INICIO_EPOCH=$(date +%s)`. El tiempo medido corre desde ahí hasta que el tipo
   queda operativo.
4. **Medir:**
   ```bash
   export TOKEN_AUXILIAR=... TOKEN_USUARIO=...
   docs/mediciones/rq-01/medir.sh rq01-base
   ```
   Termina con código 0 si todo cumple y 1 si algo falla, diciendo qué.

Variables opcionales: `MEDIR_INVENTARIO_URL` y `MEDIR_PRESTAMOS_URL` (por omisión `localhost:8080` y `localhost:8090`),
`NOMBRE_TIPO` (`Osciloscopio`), `DURACION_DIAS` (`5`), `NUMERO_UNIDAD` (`OSC-001`) y `RUTA_AUTORIZAR`
(`/prestamos/{id}/autorizar`, con `PATCH`, la ruta que dejó SCRUM-31). El solicitante del préstamo sale del token del
`usuario` (SCRUM-24): ya no se manda en el cuerpo.

**Para repetirla hay que partir de una base limpia** (`docker compose down -v`): si el tipo ya existe de otra corrida,
crearlo responde 409 y la medición lo dice. Un ensayo anterior puede haber dejado el tipo `Osciloscopio` (le pasó a la
corrida del 28 de noviembre): sin borrar los volúmenes, se usa otro `NOMBRE_TIPO` y otro `NUMERO_UNIDAD`.

## Cosas que conviene saber

- **Se mide al final**, con todo el sistema terminado e integrado. Antes de eso es un ensayo. La autorización previa
  (SCRUM-31) ya está en `main`, así que el paso "El tipo funciona" cumple; si falta, el script lo dice sin disimularlo.
- **El número de minutos incluye la parte humana** solo si se arranca el cronómetro con `INICIO_EPOCH`; el script solo
  puede medir sus propios pasos. `tiempos.txt` guarda las dos cifras.
- **Las pruebas corren con el cambio hecho**, con Maven aislado de variables como `INVENTARIO_URL` o `DB_URL`, que
  la configuración de los servicios lee y que harían fallar las pruebas de configuración.
- **Antes de medir, el árbol de los servicios debe estar sin cambios** y parado en la etiqueta; si no, el script se detiene
  (código 2) en lugar de dar una medición falsa. El mismo código 2 indica una variable inválida o un servicio que no responde.
- **Los tokens** se pasan a `curl` por un archivo temporal, no en la línea de comandos, para que no se vean con `ps`.
- **`OMITIR_PRUEBAS=1`** sirve para ensayar el resto más rápido, pero cuenta como fallo: la medición final no puede omitirlas.
- **El diseño se defiende solo si el tipo es de verdad nuevo:** por eso la duración por omisión es 5 y no 3, que ya la
  tiene el tipo Cámara de la siembra.

## Tabla final

Pendiente: la corrida **oficial** sobre la etiqueta del `main` terminado, al cerrar el Sprint 3 (nota en SCRUM-25).
Mientras tanto, esta es la del **ensayo del 28 de noviembre de 2026**, hecha con el sistema levantado por separado
(los dos servicios con `java -jar`, PostgreSQL 16, RabbitMQ y Keycloak en Docker; el `docker compose` único es SCRUM-28)
y con una **etiqueta provisional** local, `rq01-base-provisional`, sobre el commit `5991a0e` (el merge del PR 31).
Los archivos de la corrida están en `resultados/`.

| Medición | Resultado (ensayo 28-nov) | Umbral |
|---|---|---|
| Etiqueta y fecha | `rq01-base-provisional`, commit `5991a0e`, 2026-11-28 01:45 (-05:00); provisional, sin subir | registrada |
| Archivos modificados en `prestamos-service` | 0 | 0 |
| Archivos de código modificados en `inventario-service` | 0 | 0 |
| Registros de datos nuevos en `inventario-service` | 0 (el tipo se creó por la API) | ≤ 1 |
| Minutos hasta que el tipo quedó operativo | 0,0 (1 s de la parte automática; no incluye el tiempo de una persona) | < 30 |
| Préstamo con autorización y devolución a la duración del tipo | cumple: `PENDIENTE_AUTORIZACION` → `SOLICITADO`, devolución a 5 días | cumple |
| Pruebas de los dos servicios | 2 de 2 en verde: inventario 268, préstamos 330 (2 omitidas) | 2 de 2 en verde |

Tipo agregado en el ensayo: `Analizador de espectro`, 5 días, autorización obligatoria, con la unidad `ANA-001`.
