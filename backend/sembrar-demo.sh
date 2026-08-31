#!/usr/bin/env bash
#
# Levanta el backend con los datos de demostracion activados.
#
# Siembra personal (uno por cargo), la carta con recetas, stock inicial con su
# kardex, mesas, clientes, un transportista con vehiculo y tres comandas en
# distintos puntos del ciclo, para poder recorrer la aplicacion sin toparse con
# listados vacios.
#
# Es idempotente: si ya hay platillos cargados no siembra nada, asi que se
# puede volver a ejecutar sin duplicar datos.
#
# Uso:
#   ./sembrar-demo.sh              # arranca el backend y siembra
#   ./sembrar-demo.sh --solo-datos # siembra y apaga el backend al terminar

set -euo pipefail
cd "$(dirname "$0")"

echo "Comprobando que la base y Redis esten arriba..."
if ! docker ps --format '{{.Names}}' | grep -q '^bd-logistica$'; then
    echo "  bd-logistica no esta corriendo. Levantalo con: docker compose up -d"
    exit 1
fi

echo "Arrancando backend-logistica con SEED_DEMO=true..."
echo
echo "  Usuarios de prueba (clave unica: Chaquena2001)"
echo "    admin        - ADMINISTRADOR, acceso total"
echo "    mozo1        - MOZO, toma comandas y cobra"
echo "    chef1        - JEFE DE COCINA, pantalla de cocina"
echo "    caja1        - CAJERO, pagos y arqueo"
echo "    almacen1     - ALMACENERO, inventario y kardex"
echo "    repartidor1  - REPARTIDOR, despacho y OTP"
echo
echo "  Contrato de la API: http://localhost:8080/swagger-ui.html"
echo

if [[ "${1:-}" == "--solo-datos" ]]; then
    # Arranca, espera a que termine de sembrar y se apaga.
    SEED_DEMO=true ./mvnw spring-boot:run &
    PID=$!
    for _ in $(seq 1 60); do
        if curl -sf --max-time 2 http://localhost:8080/actuator/health >/dev/null 2>&1; then
            echo "Datos sembrados. Apagando el backend."
            kill "$PID" 2>/dev/null || true
            wait "$PID" 2>/dev/null || true
            exit 0
        fi
        sleep 2
    done
    echo "El backend no respondio a tiempo."
    kill "$PID" 2>/dev/null || true
    exit 1
fi

SEED_DEMO=true ./mvnw spring-boot:run
