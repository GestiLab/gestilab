#!/usr/bin/env bash
# Despliega GestiLab en un clúster Kubernetes local con kind (SCRUM-41, parte 1 de 2).
#
# Crea el clúster si no existe, construye las imágenes de los dos servicios con sus Dockerfile, las carga en
# el clúster y aplica los manifiestos de k8s/. Se puede repetir: lo que ya existe no se duplica.
#
# Uso: ./k8s/desplegar.sh
#      SIN_IMAGENES=1 ./k8s/desplegar.sh   (no reconstruye ni recarga las imágenes)
set -euo pipefail

CLUSTER="${CLUSTER:-gestilab}"
CONTEXTO="kind-$CLUSTER"
NS="gestilab"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
SERVICIOS=(inventario-service prestamos-service)

for herramienta in docker kind kubectl; do
  command -v "$herramienta" >/dev/null || { echo "Falta '$herramienta' en el PATH (ver k8s/README.md)" >&2; exit 1; }
done
docker info >/dev/null 2>&1 || { echo "Docker no está encendido" >&2; exit 1; }

kc() { kubectl --context "$CONTEXTO" "$@"; }

echo "==> Clúster kind '$CLUSTER'"
if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
  echo "    ya existe"
else
  kind create cluster --name "$CLUSTER" --wait 120s
fi

if [ "${SIN_IMAGENES:-0}" != "1" ]; then
  echo "==> Imágenes de los servicios"
  for servicio in "${SERVICIOS[@]}"; do
    docker build -q -t "gestilab/$servicio:local" "$RAIZ/$servicio" >/dev/null
    kind load docker-image "gestilab/$servicio:local" --name "$CLUSTER" >/dev/null
    echo "    gestilab/$servicio:local construida y cargada"
  done
fi

echo "==> Manifiestos"
kc apply -f "$RAIZ/k8s/namespace.yaml" >/dev/null
# El realm vive en keycloak/ (una sola fuente); kustomize no lee archivos fuera de k8s/
kc -n "$NS" create configmap keycloak-realm \
  --from-file=gestilab-realm.json="$RAIZ/keycloak/gestilab-realm.json" \
  --dry-run=client -o yaml | kc apply -f - >/dev/null
# Un Job no se puede modificar: se recrea (crear el bucket es idempotente)
kc -n "$NS" delete job crear-bucket-fotos --ignore-not-found >/dev/null
kc apply -k "$RAIZ/k8s/" >/dev/null
if [ "${SIN_IMAGENES:-0}" != "1" ]; then
  # La etiqueta :local no cambia: sin reiniciar, el pod seguiría con la imagen anterior
  for servicio in "${SERVICIOS[@]}"; do
    kc -n "$NS" rollout restart "deployment/$servicio" >/dev/null
  done
fi

echo "==> Esperando a que todo quede listo (la primera vez tarda unos minutos)"
for recurso in statefulset/postgres-inventario statefulset/postgres-prestamos statefulset/seaweedfs \
               deployment/rabbitmq deployment/keycloak deployment/inventario-service deployment/prestamos-service; do
  kc -n "$NS" rollout status "$recurso" --timeout=300s >/dev/null
  echo "    $recurso listo"
done
kc -n "$NS" wait --for=condition=complete job/crear-bucket-fotos --timeout=180s >/dev/null
echo "    bucket gestilab-fotos creado"

cat <<EOF

GestiLab desplegado en el clúster '$CONTEXTO', espacio de nombres '$NS'.
  Comprobar todo:   ./k8s/comprobar.sh
  Usar la API:      kubectl --context $CONTEXTO -n $NS port-forward service/inventario-service 8080:8080
                    kubectl --context $CONTEXTO -n $NS port-forward service/prestamos-service 8090:8090
  Borrar la app:    kubectl --context $CONTEXTO delete -k k8s/
  Borrar el clúster: kind delete cluster --name $CLUSTER
EOF
