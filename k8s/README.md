# GestiLab en Kubernetes

Despliegue del backend en un clúster Kubernetes local con [kind](https://kind.sigs.k8s.io/) (SCRUM-41,
parte 1 de 2). Levanta la infraestructura y los dos servicios tal como están en `main`. La parte 2 (SCRUM-42)
suma la seguridad con token, el consumo del evento en inventario y el acceso desde el cliente publicado.

## Qué levanta

Todo queda en el espacio de nombres `gestilab`.

| Pieza | Imagen | Tipo | Puertos dentro del clúster |
|---|---|---|---|
| `postgres-inventario` | `postgres:16-alpine` | StatefulSet con volumen de 1 GiB | 5432 |
| `postgres-prestamos` | `postgres:16-alpine` | StatefulSet con volumen de 1 GiB | 5432 |
| `keycloak` | `quay.io/keycloak/keycloak:26.0.8` | Deployment, realm importado desde `keycloak/gestilab-realm.json` | 8080 |
| `rabbitmq` | `rabbitmq:3.13-management-alpine` | Deployment (los eventos pendientes viven en la base de préstamos) | 5672, 15672 |
| `seaweedfs` | `chrislusf/seaweedfs:4.05` | StatefulSet con volumen de 2 GiB, pasarela S3 | 8333 (S3) |
| `crear-bucket-fotos` | `chrislusf/seaweedfs:4.05` | Job que crea el bucket `gestilab-fotos` | |
| `inventario-service` | `gestilab/inventario-service:local` | Deployment, imagen de su `Dockerfile` | 8080 (API), 8081 (`/salud`) |
| `prestamos-service` | `gestilab/prestamos-service:local` | Deployment, imagen de su `Dockerfile` | 8090 (API), 8091 (`/salud`) |

Cada servicio espera a sus dependencias antes de arrancar y tiene sondas sobre `/salud`. La configuración va en
un ConfigMap por servicio (las mismas variables que leen sus `application.properties`) y las credenciales en el
Secret `gestilab-credenciales`.

## Requisitos

- Docker Desktop encendido, con al menos 6 GB de memoria asignada. El clúster completo usa unos 2,3 GB.
- `kind` (`brew install kind`) y `kubectl`.
- `curl` y `jq` para el guion de comprobación.
- En Windows, los guiones se ejecutan desde WSL o Git Bash.

## Desplegar

```bash
./k8s/desplegar.sh
```

Crea el clúster `gestilab` si no existe, construye las dos imágenes, las carga en el clúster, aplica los
manifiestos y espera a que todo quede listo. La primera vez tarda unos 2 minutos, más la construcción de las
imágenes. Se puede repetir: lo que ya existe no se duplica y los servicios se reinician con la imagen nueva.
Con `SIN_IMAGENES=1` no reconstruye las imágenes.

Los manifiestos se aplican con kustomize (`kubectl apply -k k8s/`). El ConfigMap del realm lo crea el guion,
porque kustomize no lee archivos fuera de `k8s/` y el realm tiene una sola fuente: `keycloak/gestilab-realm.json`.

## Comprobar

```bash
./k8s/comprobar.sh
```

Recorre nueve pasos contra el clúster y termina con error en el primero que falle, diciendo cuál:

1. El clúster y el espacio de nombres existen.
2. Todas las piezas están listas y el bucket está creado.
3. Abre `port-forward` a inventario (18080), préstamos (18090) y Keycloak (18180).
4. Keycloak importó el realm `gestilab`.
5. El catálogo trae los datos sembrados (120 unidades).
6. Solicita una unidad disponible de un tipo sin autorización previa.
7. La entrega con foto y la foto recuperada idéntica, byte a byte, desde SeaweedFS.
8. La devolución con foto y su recuperación.
9. El exchange `gestilab.unidades.eventos` existe en RabbitMQ.

## Usar la API

```bash
kubectl --context kind-gestilab -n gestilab port-forward service/inventario-service 8080:8080
kubectl --context kind-gestilab -n gestilab port-forward service/prestamos-service 8090:8090
```

Con eso, las rutas de los README de cada servicio funcionan igual que en local. Keycloak y RabbitMQ se alcanzan
de la misma forma (`service/keycloak 8180:8080`, `service/rabbitmq 15672:15672`).

## Borrar

```bash
kubectl --context kind-gestilab delete -k k8s/   # la aplicación y sus datos
kind delete cluster --name gestilab               # el clúster completo
```

## Credenciales

`k8s/valores-de-prueba.env` tiene credenciales de prueba para este clúster local y nada más. Para cualquier otro
entorno se reemplaza por un archivo propio que no se versiona.

## Lo que todavía no hace (parte 2, SCRUM-42)

- Los servicios no validan el token: la seguridad llega con SCRUM-20.
- Inventario no consume el evento (SCRUM-21): los eventos quedan pendientes en el buzón de préstamos y
  RabbitMQ los devuelve porque no hay cola enlazada. Mientras tanto, `prestamos-service` registra cada 5 s un
  aviso `Returned message but no callback available`, y el catálogo sigue mostrando la unidad como disponible.
- No hay acceso externo fijo (Ingress) ni orígenes de CORS para el cliente publicado (SCRUM-29).
- El emisor de los tokens de Keycloak depende de por dónde se llame (`port-forward` o dentro del clúster): se
  ajusta en la parte 2 junto con la seguridad.

## Problemas conocidos y por qué

- **SeaweedFS 4.05 o más nueva.** El SDK de AWS sube las fotos con codificación `aws-chunked` firmada y un
  checksum al final. SeaweedFS 3.97 guardaba esa envoltura tal cual y la foto recuperada no era la imagen
  (decisión D-15 de la bitácora).
- **RabbitMQ con sonda de puerto.** `rabbitmq-diagnostics` corre como root; si se adelanta al servidor, crea la
  cookie de Erlang con dueño root y RabbitMQ no arranca.
- **El exchange aparece unos segundos después.** `prestamos-service` lo declara al abrir la conexión, en el
  primer ciclo del buzón de eventos (cada 5 s), no al arrancar.
