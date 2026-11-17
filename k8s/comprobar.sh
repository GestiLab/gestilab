#!/usr/bin/env bash
# Comprueba el despliegue de GestiLab en el clúster local (SCRUM-41).
#
# Recorre el flujo que hoy existe en main contra el clúster: piezas listas, realm importado, catálogo
# sembrado, solicitud, entrega con foto, foto recuperada byte a byte, devolución con foto y exchange de
# RabbitMQ declarado. Termina con error en el primer paso que falle y dice cuál fue.
#
# Uso: ./k8s/comprobar.sh        (después de ./k8s/desplegar.sh)
set -euo pipefail

CONTEXTO="${CONTEXTO:-kind-gestilab}"
NS="gestilab"
DIR="$(cd "$(dirname "$0")" && pwd)"
FOTO="$DIR/comprobacion/foto-ejemplo.png"
PUERTO_INV=18080
PUERTO_PRE=18090
PUERTO_KC=18180
TMP="$(mktemp -d)"
PIDS=()

kc() { kubectl --context "$CONTEXTO" -n "$NS" "$@"; }

limpiar() {
  for p in "${PIDS[@]:-}"; do [ -n "$p" ] && kill "$p" 2>/dev/null || true; done
  rm -rf "$TMP"
}
trap limpiar EXIT

paso=0
ok()    { echo "  OK    $1"; }
falla() { echo "  FALLA paso $paso: $1" >&2; exit 1; }
inicio() { paso=$((paso + 1)); echo "[$paso] $1"; }

for herramienta in kubectl curl jq; do
  command -v "$herramienta" >/dev/null || { echo "Falta '$herramienta' en el PATH" >&2; exit 1; }
done

inicio "El clúster '$CONTEXTO' responde y el espacio de nombres '$NS' existe"
kc get namespace "$NS" >/dev/null 2>&1 || falla "no hay clúster o no está desplegado (ejecuta ./k8s/desplegar.sh)"
ok "clúster y espacio de nombres"

inicio "Todas las piezas están listas"
for recurso in statefulset/postgres-inventario statefulset/postgres-prestamos statefulset/seaweedfs \
               deployment/keycloak deployment/rabbitmq deployment/inventario-service deployment/prestamos-service; do
  kc rollout status "$recurso" --timeout=180s >/dev/null 2>&1 || falla "$recurso no quedó listo"
done
kc wait --for=condition=complete job/crear-bucket-fotos --timeout=120s >/dev/null 2>&1 \
  || falla "el trabajo que crea el bucket de fotos no terminó"
ok "bases, Keycloak, RabbitMQ, SeaweedFS, bucket y los dos servicios"

reenviar() { # servicio puerto-local puerto-remoto
  kc port-forward "service/$1" "$2:$3" >"$TMP/pf-$1.log" 2>&1 &
  PIDS+=("$!")
  for _ in $(seq 1 30); do
    curl -s -o /dev/null "http://127.0.0.1:$2" && return 0
    sleep 1
  done
  return 1
}

inicio "Acceso con port-forward a inventario, préstamos y Keycloak"
reenviar inventario-service "$PUERTO_INV" 8080 || falla "no se pudo reenviar inventario-service"
reenviar prestamos-service "$PUERTO_PRE" 8090 || falla "no se pudo reenviar prestamos-service"
reenviar keycloak "$PUERTO_KC" 8080 || falla "no se pudo reenviar keycloak"
ok "localhost:$PUERTO_INV, localhost:$PUERTO_PRE y localhost:$PUERTO_KC"

INV="http://127.0.0.1:$PUERTO_INV/api/v1/inventario-service"
PRE="http://127.0.0.1:$PUERTO_PRE/api/v1/prestamos-service"

inicio "Keycloak importó el realm gestilab"
emisor="$(curl -sf "http://127.0.0.1:$PUERTO_KC/realms/gestilab/.well-known/openid-configuration" | jq -r .issuer)" \
  || falla "el realm gestilab no responde"
[[ "$emisor" == */realms/gestilab ]] || falla "emisor inesperado: $emisor"
ok "emisor $emisor"

inicio "El catálogo de inventario trae los datos sembrados"
total="$(curl -sf "$INV/unidades?size=1" | jq -r .totalElements)" || falla "GET /unidades no respondió"
[ "$total" -ge 120 ] || falla "se esperaban al menos 120 unidades y hay $total"
ok "$total unidades"

inicio "Solicitar una unidad disponible de un tipo sin autorización previa"
tipo="$(curl -sf "$INV/tipos-equipo" | jq -r '[.[] | select(.requiereAutorizacion == false)][0].id')"
[ -n "$tipo" ] && [ "$tipo" != "null" ] || falla "no hay tipos sin autorización previa"
candidatas="$(curl -sf "$INV/unidades?tipoId=$tipo&estado=DISPONIBLE&size=30" | jq -r '.content[].id')"
[ -n "$candidatas" ] || falla "no hay unidades disponibles del tipo $tipo"
persona="comprobacion-k8s-$(date +%s)"
# Inventario aún no consume el evento (SCRUM-21): una unidad con un préstamo abierto de una corrida anterior
# sigue saliendo DISPONIBLE y préstamos la rechaza con 409. Se prueba con la siguiente.
prestamo=""
for unidad in $candidatas; do
  respuesta="$(curl -s -w '\n%{http_code}' -H 'Content-Type: application/json' \
    -d "{\"unidadId\": $unidad, \"solicitanteId\": \"$persona\"}" "$PRE/prestamos")"
  codigo="$(tail -n1 <<<"$respuesta")"
  if [ "$codigo" = "201" ]; then prestamo="$(head -n1 <<<"$respuesta" | jq -r .id)"; break; fi
  [ "$codigo" = "409" ] || falla "POST /prestamos respondió $codigo: $(head -n1 <<<"$respuesta")"
done
[ -n "$prestamo" ] || falla "ninguna de las unidades disponibles aceptó la solicitud"
ok "préstamo $prestamo de la unidad $unidad"

cambiar() { # operacion estado-esperado
  local r c
  r="$(curl -s -w '\n%{http_code}' -X PATCH -F "foto=@$FOTO;type=image/png" "$PRE/prestamos/$prestamo/$1")"
  c="$(tail -n1 <<<"$r")"
  [ "$c" = "200" ] || falla "PATCH /$1 respondió $c: $(head -n1 <<<"$r")"
  [ "$(head -n1 <<<"$r" | jq -r .estado)" = "$2" ] || falla "tras $1 el estado no es $2"
}

recuperar() { # foto-entrega | foto-devolucion
  local c
  c="$(curl -s -o "$TMP/$1.png" -w '%{http_code} %{content_type}' "$PRE/prestamos/$prestamo/$1")"
  [ "$c" = "200 image/png" ] || falla "GET /$1 respondió $c"
  cmp -s "$FOTO" "$TMP/$1.png" || falla "la $1 recuperada no es la misma imagen que se subió"
}

inicio "Entregar con foto y recuperarla del almacenamiento S3"
cambiar entregar ENTREGADO
recuperar foto-entrega
ok "ENTREGADO; la foto de entrega vuelve idéntica desde SeaweedFS"

inicio "Recibir con foto y recuperarla"
cambiar recibir DEVUELTO
recuperar foto-devolucion
ok "DEVUELTO; la foto de devolución vuelve idéntica"

inicio "RabbitMQ tiene el exchange de eventos que declara prestamos-service"
kc exec deployment/rabbitmq -- rabbitmqctl -q list_exchanges name 2>/dev/null | grep -qx 'gestilab.unidades.eventos' \
  || falla "no existe el exchange gestilab.unidades.eventos"
ok "exchange gestilab.unidades.eventos (los eventos esperan a SCRUM-21 y a la parte 2)"

echo
echo "Todo en orden: $paso pasos comprobados contra el clúster '$CONTEXTO'."
