#!/usr/bin/env bash
# Medición RQ-01 Modificabilidad (SCRUM-26): costo de agregar un tipo de equipo con regla nueva.
#     docs/mediciones/rq-01/medir.sh <etiqueta-de-git> [carpeta-de-salida]
# Con el sistema terminado y levantado (docker compose up) y el repositorio parado en la etiqueta:
#   1. crea el tipo nuevo y una unidad por la API administrativa de inventario y los cronometra,
#   2. solicita una unidad del tipo, la autoriza y comprueba la fecha de devolución,
#   3. mide con `git diff` frente a la etiqueta cuántos archivos cambiaron en cada servicio,
#   4. corre el conjunto completo de pruebas de los dos servicios.
# Requisitos: bash, git, curl, python3, Java 21 y Maven 3.9. Termina con código 1 si alguna medición no cumple.
set -uo pipefail

RAIZ=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)

MEDIR_INVENTARIO_URL=${MEDIR_INVENTARIO_URL:-http://localhost:8080/api/v1/inventario-service}
MEDIR_PRESTAMOS_URL=${MEDIR_PRESTAMOS_URL:-http://localhost:8090/api/v1/prestamos-service}
TOKEN_AUXILIAR=${TOKEN_AUXILIAR:-}   # token del rol auxiliar; vacío solo mientras no haya seguridad
TOKEN_USUARIO=${TOKEN_USUARIO:-}     # token del rol usuario
NOMBRE_TIPO=${NOMBRE_TIPO:-Osciloscopio}
# Duración distinta de las de los tipos ya sembrados (7, 1 y 3 días), para que el tipo sea de verdad nuevo
DURACION_DIAS=${DURACION_DIAS:-5}
NUMERO_UNIDAD=${NUMERO_UNIDAD:-OSC-001}
SOLICITANTE=${SOLICITANTE:-medicion-rq01}
# Ruta para autorizar un préstamo ({id} se reemplaza). Pendiente de confirmar con la historia de autorización (SCRUM-31).
# (en dos pasos: dentro de ${VAR:-...} la llave de {id} cerraría la expansión antes de tiempo)
RUTA_AUTORIZAR=${RUTA_AUTORIZAR:-}
[ -n "$RUTA_AUTORIZAR" ] || RUTA_AUTORIZAR='/prestamos/{id}/autorizacion'
LIMITE_MINUTOS=30
# Segundo Unix en que se empezó a cronometrar (`export INICIO_EPOCH=$(date +%s)`); por omisión, el inicio del script
INICIO_EPOCH=${INICIO_EPOCH:-}

# Maven con el entorno limpio de variables que la configuración de los servicios lee (INVENTARIO_URL, DB_URL...):
# si el usuario las tiene exportadas para correr la aplicación, las pruebas de configuración fallarían sin motivo.
mvn_aislado() { env -u INVENTARIO_URL -u INVENTARIO_TIMEOUT -u DB_URL -u DB_USERNAME -u DB_PASSWORD mvn "$@"; }

# ---------- Clasificación de los cambios frente a la etiqueta (CA-3 y CA-4) ----------
# Imprime una línea por archivo cambiado: "<agregadas>\t<quitadas>\t<ruta>" (lo no versionado va con "-").
cambios_desde() {
    git -C "$RAIZ" diff --numstat "$1" -- . 2>/dev/null
    git -C "$RAIZ" ls-files --others --exclude-standard 2>/dev/null | sed 's/^/-\t-\t/'
}

# Un "registro de datos" es una línea nueva `new SemillaTipo(...)` en la siembra de inventario, o una línea
# agregada en un archivo de src/main/resources/data/. Cualquier otro cambio en un servicio es código.
SIEMBRA='inventario-service/src/main/java/com/tedesoft/inventarioservice/delivery/seed/DataInitializer.java'

# ¿El cambio en la siembra son solo registros nuevos? Imprime "dato N" o "codigo". Agregar un registro al final de la
# lista también cambia el cierre de la línea anterior (`));` pasa a `),`): eso no es código, por eso se compara sin cierres.
evaluar_siembra() {
    git -C "$RAIZ" diff -U0 "$1" -- "$2" | python3 -c '
import sys
nuevas, quitadas = [], []
for linea in sys.stdin.read().splitlines():
    if linea.startswith(("+++", "---")):
        continue
    if linea[:1] in "+-":
        (nuevas if linea[0] == "+" else quitadas).append(linea[1:])
norm = lambda l: l.strip().rstrip(",;) ")
todo_registro = all("new SemillaTipo(" in l for l in nuevas + quitadas)
solo_cierres = all(norm(q) in {norm(n) for n in nuevas} for q in quitadas)
registros = len(nuevas) - len(quitadas)
print(f"dato {registros}" if todo_registro and solo_cierres and registros >= 1 else "codigo")'
}

# Imprime: <archivos_prestamos> <archivos_codigo_inventario> <registros_de_datos>, y el detalle en el descriptor 3
clasificar_cambios() {
    local etiqueta=$1 prestamos=0 codigo=0 registros=0 a r ruta
    while IFS=$'\t' read -r a r ruta; do
        [ -n "$ruta" ] || continue
        case "$ruta" in
            prestamos-service/*)
                prestamos=$((prestamos + 1)); echo "prestamos-service  CÓDIGO  $ruta" >&3 ;;
            "$SIEMBRA")
                local veredicto
                veredicto=$(evaluar_siembra "$etiqueta" "$ruta")
                case "$veredicto" in
                    dato\ *) registros=$((registros + ${veredicto#dato })); echo "inventario-service DATO    $ruta (+${veredicto#dato } registro(s))" >&3 ;;
                    *) codigo=$((codigo + 1)); echo "inventario-service CÓDIGO  $ruta (+$a -$r, no solo registros nuevos)" >&3 ;;
                esac ;;
            inventario-service/src/main/resources/data/*)
                if [ "$a" = "-" ]; then a=$(wc -l < "$RAIZ/$ruta" | tr -d ' '); fi
                registros=$((registros + a)); echo "inventario-service DATO    $ruta (+$a registro(s))" >&3 ;;
            inventario-service/*)
                codigo=$((codigo + 1)); echo "inventario-service CÓDIGO  $ruta" >&3 ;;
            *) echo "fuera de los servicios  -      $ruta" >&3 ;;
        esac
    done < <(cambios_desde "$etiqueta")
    echo "$prestamos $codigo $registros"
}

# ---------- Ayudas de la API ----------
CUERPO=
CABECERAS=
json_get() { python3 -c 'import sys, json
d = json.load(open(sys.argv[1]))
v = d
for k in sys.argv[2].split("."):
    v = v.get(k, "") if isinstance(v, dict) else ""
print("" if v is None else str(v).lower() if isinstance(v, bool) else v)' "$CUERPO" "$1" 2>/dev/null; }
dias_entre() { python3 -c 'import sys, datetime
a, b = (datetime.date.fromisoformat(x) for x in sys.argv[1:3])
print((b - a).days)' "$1" "$2" 2>/dev/null; }
# api METODO URL TOKEN [CUERPO_JSON] -> código HTTP (000 si no hubo respuesta); el cuerpo queda en $CUERPO
# El token va en un archivo temporal (-H @archivo) y no en la línea de comandos, donde otro usuario de la máquina lo vería con `ps`.
api() {
    local metodo=$1 url=$2 token=$3 datos=${4:-} codigo
    # --globoff: sin él curl interpreta las llaves de la URL como un patrón y cambia la ruta en silencio
    local args=(-s -g -o "$CUERPO" -w '%{http_code}' -X "$metodo" -H 'Accept: application/json')
    [ -z "$datos" ] || args+=(-H 'Content-Type: application/json' -d "$datos")
    if [ -n "$token" ]; then
        printf 'Authorization: Bearer %s\n' "$token" > "$CABECERAS"
        args+=(-H "@$CABECERAS")
    fi
    codigo=$(curl "${args[@]}" "$url" 2>/dev/null) || true
    echo "${codigo:-000}"
}

# Para probar las funciones sin ejecutar la medición: MEDIR_SOLO_FUNCIONES=1 source medir.sh
if [ "${MEDIR_SOLO_FUNCIONES:-}" = 1 ]; then return 0 2>/dev/null || exit 0; fi

ETIQUETA=${1:-}
[ -n "$ETIQUETA" ] || { echo "Uso: $0 <etiqueta-de-git> [carpeta-de-salida]" >&2; exit 2; }
SALIDA=$(mkdir -p "${2:-$RAIZ/docs/mediciones/rq-01/resultados}" && cd "${2:-$RAIZ/docs/mediciones/rq-01/resultados}" && pwd)
for orden in git curl python3 mvn java; do
    command -v "$orden" > /dev/null || { echo "Falta '$orden' en el PATH." >&2; exit 2; }
done
# Los valores van dentro de un JSON: solo letras, números, espacios, guion y guion bajo
for variable in NOMBRE_TIPO NUMERO_UNIDAD SOLICITANTE; do
    [[ "${!variable}" =~ ^[[:alnum:]\ _-]+$ ]] || { echo "$variable='${!variable}' tiene caracteres no permitidos (solo letras, números, espacios, - y _)." >&2; exit 2; }
done
[[ "$DURACION_DIAS" =~ ^[0-9]+$ ]] && [ "$DURACION_DIAS" -ge 1 ] && [ "$DURACION_DIAS" -le 365 ] \
    || { echo "DURACION_DIAS debe ser un número entre 1 y 365 (es '$DURACION_DIAS')." >&2; exit 2; }
[[ -z "$INICIO_EPOCH" || "$INICIO_EPOCH" =~ ^[0-9]+$ ]] && { [ -z "$INICIO_EPOCH" ] || [ "$INICIO_EPOCH" -le "$(date +%s)" ]; } \
    || { echo "INICIO_EPOCH debe ser un segundo Unix pasado (export INICIO_EPOCH=\$(date +%s))." >&2; exit 2; }
case "$RUTA_AUTORIZAR" in /*) ;; *) RUTA_AUTORIZAR="/$RUTA_AUTORIZAR" ;; esac

CUERPO=$(mktemp)
CABECERAS=$(mktemp)
chmod 600 "$CABECERAS"
trap 'rm -f "$CUERPO" "$CABECERAS"' EXIT
FALLOS=0
fallar() { FALLOS=$((FALLOS + 1)); echo "   FALLA: $1"; }
: > "$SALIDA/procedimiento.txt"
paso() { echo "$*" | tee -a "$SALIDA/procedimiento.txt"; }

# ---------- CA-1: punto de partida ----------
COMMIT_ETIQUETA=$(git -C "$RAIZ" rev-parse -q --verify "refs/tags/$ETIQUETA^{commit}") \
    || { echo "La etiqueta '$ETIQUETA' no existe. Se crea con: git tag -a $ETIQUETA -m \"Punto de partida RQ-01\"" >&2; exit 2; }
FECHA_ETIQUETA=$(git -C "$RAIZ" for-each-ref --format='%(creatordate:iso)' "refs/tags/$ETIQUETA")
{
    echo "etiqueta : $ETIQUETA"
    echo "commit   : $COMMIT_ETIQUETA"
    echo "creada   : $FECHA_ETIQUETA"
    echo "HEAD     : $(git -C "$RAIZ" rev-parse HEAD)"
} > "$SALIDA/etiqueta.txt"
if [ "$(git -C "$RAIZ" rev-parse HEAD)" != "$COMMIT_ETIQUETA" ]; then
    echo "El repositorio no está parado en la etiqueta '$ETIQUETA' (git checkout $ETIQUETA): el diff no mediría el cambio." >&2
    exit 2
fi
SUCIOS=$(git -C "$RAIZ" status --porcelain -- inventario-service prestamos-service | wc -l | tr -d ' ')
if [ "$SUCIOS" -ne 0 ]; then
    echo "Hay $SUCIOS archivo(s) sin commitear en los servicios antes de medir: el diff no mediría solo el cambio." >&2
    git -C "$RAIZ" status --porcelain -- inventario-service prestamos-service >&2
    exit 2
fi
paso "Punto de partida: etiqueta $ETIQUETA ($FECHA_ETIQUETA, commit ${COMMIT_ETIQUETA:0:7})"

# ¿Los servicios responden? Un 404 también es una respuesta; 000 es que no hay nadie.
for url in "$MEDIR_INVENTARIO_URL/unidades/1" "$MEDIR_PRESTAMOS_URL/prestamos/1"; do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$url" 2>/dev/null)" != 000 ] \
        || { echo "No responde $url: levanta el sistema antes de medir." >&2; exit 2; }
done
INICIO=$(date +%s)
[ -n "$INICIO_EPOCH" ] || INICIO_EPOCH=$INICIO

# ---------- CA-2: tipo nuevo operativo, por la API administrativa ----------
paso "Creando el tipo '$NOMBRE_TIPO' ($DURACION_DIAS días, autorización obligatoria) con el token de auxiliar..."
codigo=$(api POST "$MEDIR_INVENTARIO_URL/tipos-equipo" "$TOKEN_AUXILIAR" \
    "{\"nombre\":\"$NOMBRE_TIPO\",\"descripcion\":\"Tipo agregado por la medición RQ-01\",\"duracionMaximaDias\":$DURACION_DIAS,\"requiereAutorizacion\":true}")
TIPO_ID=$(json_get id)
if [ "$codigo" != 201 ] || [ -z "$TIPO_ID" ]; then
    fallar "crear el tipo respondió $codigo ($(json_get mensaje)); ¿el tipo ya existe de otra corrida? Usa una base limpia"
else
    paso "  tipo creado: id $TIPO_ID"
    codigo=$(api POST "$MEDIR_INVENTARIO_URL/unidades" "$TOKEN_AUXILIAR" \
        "{\"numeroInventario\":\"$NUMERO_UNIDAD\",\"estado\":\"DISPONIBLE\",\"observaciones\":\"Unidad de la medición RQ-01\",\"tipoEquipoId\":$TIPO_ID}")
    UNIDAD_ID=$(json_get id)
    if [ "$codigo" != 201 ] || [ -z "$UNIDAD_ID" ]; then
        fallar "crear la unidad respondió $codigo ($(json_get mensaje))"
    else
        paso "  unidad creada: id $UNIDAD_ID ($NUMERO_UNIDAD)"
    fi
fi

OPERATIVO=no
if [ -n "${UNIDAD_ID:-}" ]; then
    # Operativo = la consulta que usa préstamos devuelve la unidad con las reglas del tipo nuevo
    codigo=$(api GET "$MEDIR_INVENTARIO_URL/unidades/$UNIDAD_ID" "$TOKEN_USUARIO")
    if [ "$codigo" = 200 ] && [ "$(json_get tipo.duracionMaximaDias)" = "$DURACION_DIAS" ] \
        && [ "$(json_get tipo.requiereAutorizacion)" = true ]; then
        OPERATIVO=si
    else
        fallar "la unidad no sale con las reglas del tipo nuevo (HTTP $codigo, duración $(json_get tipo.duracionMaximaDias), autorización $(json_get tipo.requiereAutorizacion))"
    fi
fi
FIN=$(date +%s)
SEGUNDOS=$((FIN - INICIO)); TOTAL_SEGUNDOS=$((FIN - INICIO_EPOCH))
MINUTOS=$(awk -v s="$TOTAL_SEGUNDOS" 'BEGIN {printf "%.1f", s/60}')
{
    echo "parte automática : $SEGUNDOS s"
    echo "cronometrado     : $TOTAL_SEGUNDOS s ($MINUTOS min) desde el inicio del cronómetro hasta que el tipo quedó operativo"
    echo "operativo        : $OPERATIVO"
} > "$SALIDA/tiempos.txt"
paso "Tipo operativo: $OPERATIVO, $MINUTOS min (límite $LIMITE_MINUTOS)"
awk -v m="$MINUTOS" -v l="$LIMITE_MINUTOS" 'BEGIN {exit !(m+0 < l+0)}' || fallar "pasaron $MINUTOS min (límite $LIMITE_MINUTOS)"

# ---------- CA-5: el tipo funciona (solicitar, autorizar, fecha de devolución) ----------
CA5=no
if [ "$OPERATIVO" = si ]; then
    paso "Solicitando la unidad como usuario..."
    codigo=$(api POST "$MEDIR_PRESTAMOS_URL/prestamos" "$TOKEN_USUARIO" "{\"unidadId\":$UNIDAD_ID,\"solicitanteId\":\"$SOLICITANTE\"}")
    PRESTAMO_ID=$(json_get id); ESTADO=$(json_get estado)
    FECHA_SOLICITUD=$(json_get fechaSolicitud); FECHA_PREVISTA=$(json_get fechaDevolucionPrevista)
    if [ "$codigo" != 201 ] || [ -z "$PRESTAMO_ID" ]; then
        fallar "solicitar la unidad respondió $codigo ($(json_get mensaje))"
    elif [ "$ESTADO" != PENDIENTE_AUTORIZACION ]; then
        paso "  el préstamo quedó en $ESTADO, no en PENDIENTE_AUTORIZACION"
        fallar "el tipo exige autorización y el préstamo no la esperó: falta la historia de autorización previa (SCRUM-31)"
    else
        paso "  préstamo $PRESTAMO_ID en PENDIENTE_AUTORIZACION; autorizando como auxiliar..."
        codigo=$(api POST "$MEDIR_PRESTAMOS_URL${RUTA_AUTORIZAR//\{id\}/$PRESTAMO_ID}" "$TOKEN_AUXILIAR")
        if [ "$codigo" != 200 ]; then
            fallar "autorizar respondió $codigo ($(json_get mensaje)); revisa RUTA_AUTORIZAR=$RUTA_AUTORIZAR"
        else
            codigo=$(api GET "$MEDIR_PRESTAMOS_URL/prestamos/$PRESTAMO_ID" "$TOKEN_AUXILIAR")
            ESTADO=$(json_get estado); FECHA_PREVISTA=$(json_get fechaDevolucionPrevista)
            DIAS=$(dias_entre "$FECHA_SOLICITUD" "$FECHA_PREVISTA")
            paso "  después de autorizar: $ESTADO, devolución $FECHA_PREVISTA ($DIAS días desde la solicitud)"
            if [ "$ESTADO" = SOLICITADO ] && [ "$DIAS" = "$DURACION_DIAS" ]; then CA5=si
            else fallar "esperaba SOLICITADO con devolución a $DURACION_DIAS días y obtuvo $ESTADO a ${DIAS:-?} días"; fi
        fi
    fi
fi

# ---------- CA-3 y CA-4: cuánto cambió en el repositorio ----------
paso "Comparando con la etiqueta $ETIQUETA..."
git -C "$RAIZ" diff --stat "$ETIQUETA" -- . > "$SALIDA/git-diff-stat.txt" 2>&1
git -C "$RAIZ" ls-files --others --exclude-standard | sed 's/^/(sin versionar) /' >> "$SALIDA/git-diff-stat.txt"
exec 3> "$SALIDA/archivos-modificados.txt"
read -r N_PRESTAMOS N_CODIGO N_DATOS <<< "$(clasificar_cambios "$ETIQUETA")"
exec 3>&-
paso "  prestamos-service: $N_PRESTAMOS archivo(s) | inventario-service: $N_CODIGO de código, $N_DATOS registro(s) de datos"
[ "$N_PRESTAMOS" -eq 0 ] || fallar "hay $N_PRESTAMOS archivo(s) modificados en prestamos-service"
[ "$N_CODIGO" -eq 0 ] || fallar "hay $N_CODIGO archivo(s) de código modificados en inventario-service"
[ "$N_DATOS" -le 1 ] || fallar "hay $N_DATOS registros de datos (máximo 1)"

# ---------- CA-6: pruebas en verde con el cambio hecho ----------
: > "$SALIDA/pruebas.txt"
for servicio in inventario-service prestamos-service; do
    if [ "${OMITIR_PRUEBAS:-}" = 1 ]; then
        # Solo para ensayar el resto de la medición más rápido: la medición final no puede omitirlas
        echo "$servicio: omitidas (OMITIR_PRUEBAS=1)" >> "$SALIDA/pruebas.txt"
        fallar "las pruebas de $servicio se omitieron: la medición final las exige"
        continue
    fi
    paso "Pruebas de $servicio..."
    mvn_aislado -B -ntp -f "$RAIZ/$servicio/pom.xml" clean verify > "$SALIDA/maven-$servicio.log" 2>&1
    codigo=$?
    resumen=$(grep -a -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$" "$SALIDA/maven-$servicio.log" | tail -1 | sed -E 's/^\[[A-Z]+\] //')
    echo "$servicio: $resumen (código $codigo)" >> "$SALIDA/pruebas.txt"
    [ "$codigo" -eq 0 ] || fallar "las pruebas de $servicio fallan"
done

# ---------- Resumen (CA-7) ----------
sino() { [ "$1" = si ] && echo cumple || echo "NO cumple"; }
{
    echo "# Resultados RQ-01 Modificabilidad"
    echo
    echo "Etiqueta: \`$ETIQUETA\` (commit \`${COMMIT_ETIQUETA:0:7}\`, creada $FECHA_ETIQUETA) · medido $(date '+%Y-%m-%d %H:%M')"
    echo "Tipo agregado: \`$NOMBRE_TIPO\`, $DURACION_DIAS días, autorización obligatoria"
    echo
    echo "| Medición | Resultado | Umbral |"
    echo "|---|---|---|"
    echo "| Archivos modificados en \`prestamos-service\` | $N_PRESTAMOS | 0 |"
    echo "| Archivos de código modificados en \`inventario-service\` | $N_CODIGO | 0 |"
    echo "| Registros de datos nuevos en \`inventario-service\` | $N_DATOS | ≤ 1 |"
    echo "| Tiempo hasta que el tipo quedó operativo | $MINUTOS min | < $LIMITE_MINUTOS min |"
    echo "| Préstamo con autorización y devolución a $DURACION_DIAS días | $(sino "$CA5") | cumple |"
    echo "| Pruebas de los dos servicios | $(grep -c 'código 0' "$SALIDA/pruebas.txt") de 2 en verde | 2 de 2 |"
    echo
    echo "Archivos: \`etiqueta.txt\`, \`procedimiento.txt\`, \`tiempos.txt\`, \`git-diff-stat.txt\`, \`archivos-modificados.txt\`, \`pruebas.txt\`."
} > "$SALIDA/resumen.md"

echo; cat "$SALIDA/resumen.md"; echo
if [ "$FALLOS" -eq 0 ]; then echo "RQ-01: todas las mediciones cumplen."; exit 0; fi
echo "RQ-01: $FALLOS medición(es) no cumplen. Detalle en $SALIDA"; exit 1
