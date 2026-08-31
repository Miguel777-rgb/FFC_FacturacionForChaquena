#!/usr/bin/env bash

CONTAINER_NAME="bd-logistica"
DB_USER="admin_logistica"
DB_NAME="logistica_db"
OUTPUT_FILE="schema.sql"

echo "Exportando esquema de '$DB_NAME'..."

docker exec -i "$CONTAINER_NAME" pg_dump \
    -U "$DB_USER" \
    -d "$DB_NAME" \
    --schema-only \
    --no-owner \
    --no-privileges > "$OUTPUT_FILE"

if [ $? -eq 0 ]; then
    echo "¡Esquema actualizado correctamente en $OUTPUT_FILE!"
else
    echo "Error al exportar el esquema."
fi