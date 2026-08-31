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

### 💡 Conclusión
Tu base de datos **está diseñada como un profesional de software senior**:
1. **Mantiene integridad referencial** donde importa (dentro del mismo flujo: Orden -> Detalles -> Complementos).
2. **Desacopla dominios transversales** (Seguridad, Eventos Outbox, Feedback) mediante referencias por ID (`UUID`) para permitir que la aplicación escale sin bloqueos en PostgreSQL.