# Medición RQ-02 · Testabilidad

Cómo medir, con números repetibles, que la capa de dominio de los dos servicios se prueba sin infraestructura
y sin depender de la fecha de la máquina. Un solo comando ejecuta las cinco mediciones y deja los resultados
como archivos.

## Cómo repetirla

Requisitos: Java 21, Maven 3.9, Docker encendido y acceso a internet la primera vez (descarga dependencias).

```bash
docs/mediciones/rq-02/medir.sh
```

Escribe en `docs/mediciones/rq-02/resultados/` (otra carpeta: `medir.sh /ruta/de/salida`). Termina con código 0 si
las cinco mediciones cumplen y con 1 si alguna falla, diciendo cuál. Tarda unos 4 minutos: corre cada servicio
una vez de calentamiento (sin medir) y otra midiendo.

Limpieza de lo que crea en Docker (opcional): `docker rmi gestilab-rq02-faketime` y `docker volume rm gestilab-rq02-m2`.

## Qué mide y cuándo cumple

| Medición | Cómo | Cumple si | Archivo |
|---|---|---|---|
| **Cobertura del dominio** (CA-1) | `mvn clean verify` de cada servicio; suma las instrucciones de los paquetes `domain` de `jacoco.csv` | ≥ 70 % en cada servicio | `cobertura.txt`, `jacoco-<servicio>.csv` |
| **Tiempo** (CA-2) | Reloj de pared del `mvn clean verify` completo, con la caché de Maven ya llena; incluye compilar | < 60 s en cada servicio | `tiempos.txt`, `maven-<servicio>.log` |
| **Sin reloj del sistema** (CA-3) | `grep -rnE` en la carpeta `domain` de cada servicio de `LocalDate.now()`, `Instant.now()`, `Clock.system…`, `System.currentTimeMillis` y similares (el patrón completo está en `medir.sh`). `now(clock)` con el reloj inyectado es válido y no cuenta; `now()` y `now(zona)` sí | 0 coincidencias | `reloj-sistema.txt` |
| **Fecha adelantada un año** (CA-4) | En un contenedor Linux con libfaketime (`FAKETIME=+365d`) corre las pruebas con la fecha normal y con la adelantada, y compara el resultado de cada clase de prueba | Mismo resultado, clase por clase, y ambas en verde | `faketime.txt`, `faketime-<servicio>-*.log` |
| **Regla invertida** (CA-5) | En una **copia** de `prestamos-service` cambia `.isBefore(hoy)` por `.isAfter(hoy)` en `PrestamoRetrasado` y corre las pruebas | Al menos una prueba falla y su nombre dice la regla (`reglaRetraso`, `Retrasos…`) | `regla-invertida.txt`, `regla-invertida.log` |

`resumen.md` junta todo en una tabla, con el commit medido y la fecha. `entorno.txt` guarda las versiones usadas.

## Cosas que conviene saber

- **El contenedor** parte de `maven:3.9-eclipse-temurin-21-alpine` (la imagen de Maven de referencia del proyecto) y
  agrega `libfaketime`; su Dockerfile está en esta carpeta y no en los servicios. Las pruebas usan H2, así que no hace falta
  Docker dentro de Docker. Con la fecha adelantada corre sin red (`mvn -o`), porque con otra fecha fallaría la
  validación de los certificados de descarga.
- **Si la JVM se cuelga** con libfaketime, el script lo detecta a los 15 minutos y repite con
  `FAKETIME_DONT_FAKE_MONOTONIC=1`; el archivo `faketime.txt` dice qué variante se usó.
- **La regla invertida no toca el repositorio:** trabaja en una copia temporal que borra al terminar. Si la regla
  cambia de archivo o de texto, se actualizan las tres variables `REGLA_*` al inicio de `medir.sh`; mientras no
  se encuentre el texto original, la medición falla en lugar de dar un resultado falso.
- **Se mide al final**, con el código de todas las historias ya integrado en `main`. Una corrida antes de eso sirve
  de ensayo, pero sus números no son los del informe.
- **Sistemas:** probado en macOS y Linux; en Windows se ejecuta desde WSL.
- **Los números dependen de la máquina** (sobre todo el tiempo): el informe debe decir en cuál se midió (`entorno.txt`).
- **Un hallazgo no es un fallo de la medición:** si la cobertura queda bajo 70 %, se registra como hallazgo y no se
  escriben pruebas nuevas solo para subirla.

## Tabla final

Se completa al terminar el Sprint 3, con el `resumen.md` de la corrida sobre `main`.

| Medición | Resultado | Umbral |
|---|---|---|
| Cobertura del dominio, `inventario-service` | _pendiente_ | ≥ 70 % |
| Cobertura del dominio, `prestamos-service` | _pendiente_ | ≥ 70 % |
| Tiempo del conjunto completo, `inventario-service` | _pendiente_ | < 60 s |
| Tiempo del conjunto completo, `prestamos-service` | _pendiente_ | < 60 s |
| Llamadas al reloj del sistema en el dominio | _pendiente_ | 0 |
| Pruebas con la fecha adelantada un año | _pendiente_ | idéntico |
| Regla del retraso invertida | _pendiente_ | al menos una prueba falla nombrando la regla |
