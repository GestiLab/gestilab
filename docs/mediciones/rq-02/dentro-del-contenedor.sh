#!/usr/bin/env bash
# Se ejecuta DENTRO del contenedor (lo lanza medir.sh). Corre las pruebas de cada servicio con la fecha
# normal y con la fecha adelantada un año, y compara el resultado de cada clase de prueba.
set -uo pipefail

SERVICIOS=(inventario-service prestamos-service)
DESPLAZAMIENTO="${DESPLAZAMIENTO:-+365d}"
SALIDA=/salida
mkdir -p "$SALIDA" /trabajo
# Las pruebas de contrato leen ../docs/contrato (relativo a la carpeta del servicio)
mkdir -p /trabajo/docs && cp -R /src/docs/contrato /trabajo/docs/contrato
LIB=$(find /usr/lib -name 'libfaketime.so.1' 2>/dev/null | head -1)
[ -n "$LIB" ] || { echo "No se encontró libfaketime.so.1 en la imagen" >&2; exit 2; }

# Una línea por clase de prueba, sin el tiempo (que cambia entre corridas), ordenada
resultado_por_clase() {
    grep -a -E "Tests run:.* -- in " "$1" | sed -E 's/, Time elapsed: [0-9.]+ s//; s/^\[[A-Z]+\] //' | sort
}
resumen_final() {
    grep -a -E "^\[(INFO|WARNING|ERROR)\] Tests run: [0-9]+, Failures: [0-9]+, Errors: [0-9]+, Skipped: [0-9]+$" "$1" | tail -1 | sed -E 's/^\[[A-Z]+\] //'
}

{
    echo "fecha real del contenedor : $(date -u '+%Y-%m-%d %H:%M:%S')"
    echo "fecha con libfaketime ($DESPLAZAMIENTO): $(LD_PRELOAD="$LIB" FAKETIME="$DESPLAZAMIENTO" date -u '+%Y-%m-%d %H:%M:%S')"
    echo "libfaketime               : $LIB"
    echo
} > "$SALIDA/faketime.txt"

veredicto_global=IDENTICO
for s in "${SERVICIOS[@]}"; do
    rm -rf "/trabajo/$s"; mkdir -p "/trabajo/$s"
    (cd "/src/$s" && tar --exclude=./target -cf - .) | tar -xf - -C "/trabajo/$s"
    cd "/trabajo/$s" || exit 2

    mvn -B -ntp clean test > "$SALIDA/faketime-$s-normal.log" 2>&1
    codigo_normal=$?

    # Con la fecha adelantada: sin red (-o), porque con otra fecha los certificados de descarga fallarían.
    usado="sin variables extra"
    LD_PRELOAD="$LIB" FAKETIME="$DESPLAZAMIENTO" timeout 900 mvn -o -B -ntp clean test > "$SALIDA/faketime-$s-adelantada.log" 2>&1
    codigo_adelantada=$?
    # timeout devuelve 124 (GNU) o 143 (busybox, el de Alpine) cuando se acaba el tiempo
    if [ "$codigo_adelantada" -eq 124 ] || [ "$codigo_adelantada" -eq 143 ]; then
        # La JVM se cuelga con el reloj monotónico falseado: se reintenta sin falsearlo
        usado="FAKETIME_DONT_FAKE_MONOTONIC=1 (la primera vez la JVM se colgó)"
        LD_PRELOAD="$LIB" FAKETIME="$DESPLAZAMIENTO" FAKETIME_DONT_FAKE_MONOTONIC=1 timeout 900 \
            mvn -o -B -ntp clean test > "$SALIDA/faketime-$s-adelantada.log" 2>&1
        codigo_adelantada=$?
    fi

    r_normal=$(resultado_por_clase "$SALIDA/faketime-$s-normal.log")
    r_adelantada=$(resultado_por_clase "$SALIDA/faketime-$s-adelantada.log")
    if [ -n "$r_normal" ] && [ "$r_normal" == "$r_adelantada" ] && [ "$codigo_normal" -eq 0 ] && [ "$codigo_adelantada" -eq 0 ]; then
        veredicto=IDENTICO
    else
        veredicto=DISTINTO; veredicto_global=DISTINTO
    fi
    {
        echo "== $s"
        echo "fecha normal    : $(resumen_final "$SALIDA/faketime-$s-normal.log")  (código $codigo_normal)"
        echo "fecha adelantada: $(resumen_final "$SALIDA/faketime-$s-adelantada.log")  (código $codigo_adelantada, $usado)"
        echo "resultado       : $veredicto"
        if [ "$veredicto" == DISTINTO ]; then
            echo "diferencias por clase (< normal, > adelantada):"
            diff <(echo "$r_normal") <(echo "$r_adelantada") | sed 's/^/   /'
        fi
        echo
    } >> "$SALIDA/faketime.txt"
done
echo "VEREDICTO_FAKETIME=$veredicto_global" >> "$SALIDA/faketime.txt"
# Lo escrito en /salida queda a nombre del usuario que lanzó medir.sh, no de root
if [ -n "${HOST_UID:-}" ]; then chown -R "$HOST_UID:${HOST_GID:-$HOST_UID}" "$SALIDA" 2>/dev/null || true; fi
