#!/usr/bin/env bash
# Medición RQ-02 Testabilidad (SCRUM-25). Ejecutar desde cualquier carpeta:
#     docs/mediciones/rq-02/medir.sh [carpeta-de-salida]
# Requisitos: Java 21, Maven 3.9, git y Docker encendido. Escribe los resultados como archivos
# (por defecto en docs/mediciones/rq-02/resultados/) y termina con código 1 si alguna medición falla.
set -uo pipefail

RAIZ=$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)
IMAGEN=gestilab-rq02-faketime
VOLUMEN_M2=gestilab-rq02-m2
SERVICIOS=(inventario-service prestamos-service)
LIMITE_COBERTURA=70
LIMITE_SEGUNDOS=60

# CA-5: la regla que se invierte a propósito. Si cambia de lugar, se actualizan estas tres líneas.
REGLA_ARCHIVO="prestamos-service/src/main/java/com/tedesoft/prestamosservice/domain/models/PrestamoRetrasado.java"
REGLA_ORIGINAL='.isBefore(hoy)'
REGLA_INVERTIDA='.isAfter(hoy)'

# CA-3: llamadas a la fecha o la hora del sistema que no pueden aparecer en el dominio.
# `now()` y `now(zona)` leen el reloj del sistema; `now(clock)` con el reloj inyectado es válido y se descarta.
PATRON_RELOJ='(LocalDate|LocalDateTime|LocalTime|Instant|ZonedDateTime|OffsetDateTime|Year|YearMonth)\.now\(|System\.currentTimeMillis|System\.nanoTime|Clock\.system|new Date\(|Calendar\.getInstance'
PERMITIDO_RELOJ='\.now\((this\.)?[A-Za-z_]*([Cc]lock|[Rr]eloj)\)'

# Imprime "archivo:línea:texto" de cada llamada al reloj del sistema bajo la carpeta dada (nada si no hay)
buscar_reloj() {
    grep -rnE --include='*.java' "$PATRON_RELOJ" "$1" 2>/dev/null \
        | sed -E "s/$PERMITIDO_RELOJ/.ahoraConRelojInyectado()/g" | grep -E "$PATRON_RELOJ" || true
}

# Para probar las funciones sin ejecutar la medición: MEDIR_SOLO_FUNCIONES=1 source medir.sh
if [ "${MEDIR_SOLO_FUNCIONES:-}" = 1 ]; then return 0 2>/dev/null || exit 0; fi

SALIDA=$(mkdir -p "${1:-$RAIZ/docs/mediciones/rq-02/resultados}" && cd "${1:-$RAIZ/docs/mediciones/rq-02/resultados}" && pwd)

FALLOS=0
: > "$SALIDA/resumen.md"
fallar() { FALLOS=$((FALLOS + 1)); }

# Primero, de verdad: ¿qué commit y qué fecha se miden?
COMMIT=$(git -C "$RAIZ" rev-parse --short HEAD 2>/dev/null || echo "sin-git")
SUCIO=$(git -C "$RAIZ" status --porcelain 2>/dev/null | wc -l | tr -d ' ')
{
    echo "commit medido : $COMMIT (archivos sin commitear: $SUCIO)"
    echo "fecha         : $(date '+%Y-%m-%d %H:%M:%S %Z')"
    echo "java          : $(java -version 2>&1 | head -1)"
    echo "maven         : $(mvn -v 2>&1 | head -1)"
} > "$SALIDA/entorno.txt"

segundos_maven() { grep -a "Total time:" "$1" | tail -1 | sed -E 's/.*Total time:[[:space:]]+([0-9.:]+)( min| s).*/\1\2/'; }

# ---------- CA-1 y CA-2: cobertura del dominio y tiempo del conjunto completo ----------
: > "$SALIDA/cobertura.txt"; : > "$SALIDA/tiempos.txt"
# Arreglos indexados, no asociativos: el bash 3.2 de macOS no los tiene
COBERTURA=(); TIEMPO=()
for i in "${!SERVICIOS[@]}"; do
    s=${SERVICIOS[$i]}
    echo ">> $s: calentando la caché de Maven (no se mide)"
    mvn -B -ntp -q -f "$RAIZ/$s/pom.xml" clean verify > "$SALIDA/calentamiento-$s.log" 2>&1
    echo ">> $s: midiendo"
    inicio=$(date +%s)
    mvn -B -ntp -f "$RAIZ/$s/pom.xml" clean verify > "$SALIDA/maven-$s.log" 2>&1
    codigo=$?
    fin=$(date +%s)
    pared=$((fin - inicio))
    rm -f "$SALIDA/calentamiento-$s.log"
    cp "$RAIZ/$s/target/site/jacoco/jacoco.csv" "$SALIDA/jacoco-$s.csv" 2>/dev/null
    pct=$(awk -F, 'NR>1 && $2 ~ /\.domain/ {m+=$4; c+=$5} END {if (m+c>0) printf "%.1f", 100*c/(m+c); else print "0.0"}' "$SALIDA/jacoco-$s.csv" 2>/dev/null)
    pruebas=$(grep -a -E "Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$" "$SALIDA/maven-$s.log" | tail -1 | sed -E 's/^\[[A-Z]+\] //')
    COBERTURA[$i]=$pct; TIEMPO[$i]=$pared
    echo "$s: cobertura de instrucciones del dominio = $pct % (mínimo $LIMITE_COBERTURA %) | $pruebas | verify código $codigo" >> "$SALIDA/cobertura.txt"
    echo "$s: $pared s de reloj de pared (Maven informa $(segundos_maven "$SALIDA/maven-$s.log"); límite $LIMITE_SEGUNDOS s)" >> "$SALIDA/tiempos.txt"
    [ "$codigo" -eq 0 ] || { echo "   FALLA: el verify de $s no terminó bien"; fallar; }
    awk -v p="$pct" -v l="$LIMITE_COBERTURA" 'BEGIN {exit !(p+0 >= l+0)}' || { echo "   FALLA: cobertura de $s bajo $LIMITE_COBERTURA %"; fallar; }
    [ "$pared" -lt "$LIMITE_SEGUNDOS" ] || { echo "   FALLA: $s tardó $pared s"; fallar; }
done

# ---------- CA-3: cero llamadas al reloj del sistema en el dominio ----------
: > "$SALIDA/reloj-sistema.txt"
{
    echo "Búsqueda (desde la raíz del repositorio):"
    echo "  grep -rnE --include='*.java' '$PATRON_RELOJ' <servicio>/src/main/java/**/domain"
    echo "  (se descarta now(clock) con el reloj inyectado; now() y now(zona) sí cuentan)"
    echo
} >> "$SALIDA/reloj-sistema.txt"
total_reloj=0
for s in "${SERVICIOS[@]}"; do
    dominio=$(find "$RAIZ/$s/src/main/java" -type d -name domain | head -1)
    [ -n "$dominio" ] || { echo "   FALLA: no se encontró la carpeta domain de $s"; fallar; continue; }
    hallazgos=$(buscar_reloj "$dominio" | sed "s|$RAIZ/||")
    cuenta=$(printf '%s' "$hallazgos" | grep -c . || true)
    total_reloj=$((total_reloj + cuenta))
    echo "$s: $cuenta llamadas" >> "$SALIDA/reloj-sistema.txt"
    [ "$cuenta" -eq 0 ] || printf '%s\n' "$hallazgos" | sed 's/^/    /' >> "$SALIDA/reloj-sistema.txt"
done
echo "TOTAL: $total_reloj" >> "$SALIDA/reloj-sistema.txt"
[ "$total_reloj" -eq 0 ] || { echo "   FALLA: hay $total_reloj llamadas al reloj del sistema en el dominio"; fallar; }

# ---------- CA-4: fecha adelantada un año, en un contenedor Linux con libfaketime ----------
echo ">> fecha adelantada un año (Docker)"
if docker build -q -t "$IMAGEN" "$RAIZ/docs/mediciones/rq-02" > /dev/null 2>&1; then
    docker volume create "$VOLUMEN_M2" > /dev/null
    docker run --rm -e HOST_UID="$(id -u)" -e HOST_GID="$(id -g)" \
        -v "$RAIZ":/src:ro -v "$SALIDA":/salida -v "$VOLUMEN_M2":/root/.m2 "$IMAGEN" \
        bash /src/docs/mediciones/rq-02/dentro-del-contenedor.sh
    grep -q '^VEREDICTO_FAKETIME=IDENTICO$' "$SALIDA/faketime.txt" 2>/dev/null || { echo "   FALLA: con la fecha adelantada el resultado no es idéntico"; fallar; }
else
    echo "No se pudo construir la imagen: ¿Docker está encendido?" > "$SALIDA/faketime.txt"
    echo "   FALLA: no se pudo construir la imagen de libfaketime"; fallar
fi

# ---------- CA-5: regla del retraso invertida a propósito ----------
echo ">> regla del retraso invertida"
TEMPORAL=$(mktemp -d)
trap 'rm -rf "$TEMPORAL"' EXIT
servicio_regla=${REGLA_ARCHIVO%%/*}
mkdir -p "$TEMPORAL/$servicio_regla"
(cd "$RAIZ/$servicio_regla" && tar --exclude=./target -cf - .) | tar -xf - -C "$TEMPORAL/$servicio_regla"
# Una prueba del cliente lee el contrato de ../docs/contrato: sin esa carpeta fallaría por otra causa
mkdir -p "$TEMPORAL/docs" && cp -R "$RAIZ/docs/contrato" "$TEMPORAL/docs/contrato"
{
    echo "Regla: $REGLA_ARCHIVO"
    echo "Cambio: '$REGLA_ORIGINAL' por '$REGLA_INVERTIDA' (en una copia; el repositorio no se toca)"
} > "$SALIDA/regla-invertida.txt"
if grep -qF "$REGLA_ORIGINAL" "$TEMPORAL/$REGLA_ARCHIVO"; then
    sed -i.bak "s/$(printf '%s' "$REGLA_ORIGINAL" | sed 's/[.[\*^$/]/\\&/g')/$(printf '%s' "$REGLA_INVERTIDA" | sed 's/[&/\]/\\&/g')/" "$TEMPORAL/$REGLA_ARCHIVO"
    rm -f "$TEMPORAL/$REGLA_ARCHIVO.bak"
    mvn -B -ntp -f "$TEMPORAL/$servicio_regla/pom.xml" clean test -Djacoco.skip=true > "$SALIDA/regla-invertida.log" 2>&1
    fallidas=$(grep -a -E '^\[ERROR\]   [A-Za-z0-9_]+\.[A-Za-z0-9_]+' "$SALIDA/regla-invertida.log" | sed -E 's/^\[ERROR\]   //; s/:[0-9]+.*//' | sort -u)
    cuenta=$(printf '%s' "$fallidas" | grep -c . || true)
    echo "Pruebas que fallan: $cuenta" >> "$SALIDA/regla-invertida.txt"
    printf '%s\n' "$fallidas" | sed 's/^/    /' >> "$SALIDA/regla-invertida.txt"
    if [ "$cuenta" -ge 1 ] && printf '%s' "$fallidas" | grep -qi -E 'regla|retras'; then
        echo "VEREDICTO_REGLA=DETECTADA" >> "$SALIDA/regla-invertida.txt"
    else
        echo "VEREDICTO_REGLA=NO_DETECTADA" >> "$SALIDA/regla-invertida.txt"
        echo "   FALLA: ninguna prueba que nombre la regla detectó la inversión"; fallar
    fi
else
    echo "VEREDICTO_REGLA=NO_SE_PUDO_INVERTIR (no se encontró el texto original en la regla)" >> "$SALIDA/regla-invertida.txt"
    echo "   FALLA: no se encontró '$REGLA_ORIGINAL' en $REGLA_ARCHIVO"; fallar
fi

# ---------- Resumen ----------
veredicto() { [ "$1" -eq 0 ] && echo "cumple" || echo "NO cumple"; }
{
    echo "# Resultados RQ-02 Testabilidad"
    echo
    echo "Commit medido: \`$COMMIT\` (archivos sin commitear: $SUCIO) · $(date '+%Y-%m-%d %H:%M')"
    echo
    echo "| Medición | Resultado | Umbral |"
    echo "|---|---|---|"
    for i in "${!SERVICIOS[@]}"; do
        s=${SERVICIOS[$i]}
        echo "| Cobertura de instrucciones del dominio, $s | ${COBERTURA[$i]:-?} % | ≥ $LIMITE_COBERTURA % |"
    done
    for i in "${!SERVICIOS[@]}"; do
        s=${SERVICIOS[$i]}
        echo "| Tiempo del conjunto completo, $s | ${TIEMPO[$i]:-?} s | < $LIMITE_SEGUNDOS s |"
    done
    echo "| Llamadas al reloj del sistema en el dominio | $total_reloj | 0 |"
    veredicto_faketime=$(grep -o 'VEREDICTO_FAKETIME=.*' "$SALIDA/faketime.txt" 2>/dev/null | cut -d= -f2 | tr 'A-Z' 'a-z' | sed 's/identico/idéntico/')
    echo "| Pruebas con la fecha adelantada un año | ${veredicto_faketime:-no medido} respecto a la fecha normal | idéntico |"
    echo "| Regla del retraso invertida | $(grep -o 'VEREDICTO_REGLA=.*' "$SALIDA/regla-invertida.txt" | cut -d= -f2 | tr 'A-Z_' 'a-z ') | al menos una prueba falla nombrando la regla |"
    echo
    echo "Archivos: \`cobertura.txt\`, \`jacoco-*.csv\`, \`tiempos.txt\`, \`reloj-sistema.txt\`, \`faketime.txt\`, \`regla-invertida.txt\`, \`entorno.txt\`."
} > "$SALIDA/resumen.md"

echo
cat "$SALIDA/resumen.md"
if [ "$FALLOS" -eq 0 ]; then echo; echo "RQ-02: todas las mediciones cumplen."; exit 0; fi
echo; echo "RQ-02: $FALLOS medición(es) no cumplen. Detalle en $SALIDA"; exit 1
