### Watch
./watch_schema.sh  

### INSERT WITH DOCKER:

╭─    ~/Documentos/Facturas ······················· 1 ✘  at 00:17:34  
╰─ docker exec -it bd-logistica psql -U admin_logistica -d logistica_db -c "INSERT INTO cargos (nombre, descripcion, created_by, date_created) VALUES ('Cocinero de Almacén', 'Encargado de inventario', 'SYSTEM', CURRENT_TIMESTAMP);"
INSERT 0 1

### 1. ¿Por qué `outbox_events` está "suelta" (sin líneas)?
* **Razón:** Es la tabla del **Patrón Transactional Outbox**. Su único trabajo es funcionar como una **cola de mensajes interna**.
* Guarda el ID de la orden y un payload en `JSONB`. **No debe tener llaves foráneas SQL (FK)** con `ordenes` para que la escritura del evento sea ultrarrápida y para que, si un pedido se archiva o limpia en el futuro, no bloquee ni borre el historial de la cola de eventos.

---

### 2. ¿Por qué el grupo de `trabajadores` / `roles` / `permisos` está separado y no tiene líneas hacia `ordenes` o `controles_insumo`?
* **Razón (Aislamiento de Dominios en DDD):** El módulo de **Autenticación/Seguridad** es un dominio autónomo.
* En la tabla `ordenes` guardamos el `mozo_id` (UUID), y en `controles_insumo` guardamos el `trabajador_id` (UUID). 
* **¿Por qué por ID y no con una línea de FK SQL?** Porque si el día de mañana decides migrar la Autenticación a un servidor independiente de usuarios (como Auth0, Keycloak o un microservicio `auth-service`), **no tendrás que romper la base de datos**; las órdenes seguirán recordando qué ID de trabajador las atendió sin depender de una tabla física en la misma BD.

---

### 3. ¿Por qué se ven 4 "islas" o grupos claros de tablas?
Las agrupaciones que se formaron automáticamente en tu diagrama corresponden exactamente a los **módulos de dominio** que definimos en el código Java:

```text
┌──────────────────────────────────────────────┐
│ 1. GRUPO SEGURIDAD (Auth Domain)             │
│    roles ── rol_permisos ── permisos         │
│      │                                       │
│    trabajadores                              │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ 2. GRUPO POS & DELIVERY (Order Domain)       │
│    clientes ── ordenes ── orden_detalles...  │
│       │                                      │
│    whatsapp_sesiones                         │
└──────────────────────────────────────────────┘

┌──────────────────────────────────────────────┐
│ 3. GRUPO MENÚ & INVENTARIO (Inventory)       │
│    categorias ── platillos ── insumos        │
│    promociones ── controles_insumo...        │
└──────────────────────────────────────────────┘

┌──────────────────────┐  ┌────────────────────┐
│ 4. GRUPO FEEDBACK    │  │ 5. GRUPO EVENTOS   │
│ calificaciones       │  │ outbox_events      │
└──────────────────────┘  └────────────────────┘
```

---

### 4. Tablas que agregó el reajuste de la interfaz

Hibernate las crea al arrancar (`ddl-auto=update`). Todas siguen la convención
del resto: auditoría y cantidades `NOT NULL`, y nulo solo donde dice algo.

| Tabla | Módulo | Qué guarda | Nulo con sentido |
|---|---|---|---|
| `datos_local`, `horarios_local` | local | Nombre comercial, RUC, contacto, IGV y logo; el horario de los siete días | Un día sin horas es un día sin definir, no un día cerrado (`cerrado` es aparte) |
| `niveles_lealtad` | fidelizacion | Escalones por puntos y el descuento de cada uno | — |
| `proveedores` | inventario | A quién se le compra | RUC, contacto |
| `lotes_insumo` | inventario | Cada compra con su cantidad inicial y restante, costo y vencimiento | Costo y vencimiento cuando no se conocen; `control_origen_id` nulo marca el lote inicial |
| `archivos` | archivos | Metadatos de fotos y logo; los bytes van en el volumen `archivos_logistica` | `nombre_original` |
| `alergenos`, `platillo_alergenos` | inventario | El catálogo y qué platillo lleva cuál | — |
| `reservas` | mesas | Mesa, a nombre de quién, personas, inicio, duración y estado | Celular y nota |
| `turnos` | asistencia | El horario planificado de cada trabajador | Nota |
| `marcaciones` | asistencia | Cada entrada y su salida | `salida`: la persona sigue dentro |

`ordenes` ganó `porcentaje_igv` (la tasa con la que se vendió) y
`nivel_lealtad_nombre`; `platillos`, la foto y el tiempo de preparación; `mesas`,
su lugar en el plano (`columna`, `fila`, `ancho`, `alto`, `forma`).

### 5. Migraciones 05 a 07: el orden importa

| Migración | Cuándo | Qué hace |
|---|---|---|
| `migracion_05_igv_y_nivel_en_ordenes.sql` | **Antes** de arrancar el backend nuevo | Agrega `ordenes.porcentaje_igv` con 18,00 para las comandas existentes. Hibernate no puede crear una columna `NOT NULL` en una tabla con filas |
| `migracion_06_lotes_iniciales.sql` | **Después** de arrancar | Mete el stock que ya había en un lote inicial por insumo, sin costo ni vencimiento, para que la suma de los lotes cuadre con `stock_actual` |
| `migracion_07_reservas_y_plano.sql` | **Después** de arrancar | Pasa las reservas que vivían en `mesas` a su tabla, deja libres las mesas marcadas como reservadas y reparte en el plano las mesas que llegan apiladas |

Las tres se pueden volver a ejecutar sin efecto.

---

### 💡 Conclusión
Tu base de datos **está diseñada como un profesional de software senior**:
1. **Mantiene integridad referencial** donde importa (dentro del mismo flujo: Orden -> Detalles -> Complementos).
2. **Desacopla dominios transversales** (Seguridad, Eventos Outbox, Feedback) mediante referencias por ID (`UUID`) para permitir que la aplicación escale sin bloqueos en PostgreSQL.