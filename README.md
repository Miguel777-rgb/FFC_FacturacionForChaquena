# FFC_FacturacionForChaquena

Sistema de logística, POS y facturación electrónica (SUNAT, Perú) para
restaurante. Este repositorio versiona el código del servidor y el esquema de la
base de datos:

| Carpeta | Qué es |
|---|---|
| `backend/` | `backend-logistica` — Spring Boot 4 sobre Java 21. 124 endpoints en 24 controladores. |
| `bd/` | Volcado del esquema de PostgreSQL, sincronizado con `bd/watch_schema.sh`, más las migraciones aplicadas. |

## Arranque

```bash
cp backend/.env.example backend/.env    # y rellenar los valores
docker compose up -d --build
```

Levanta `bd-logistica` (PostgreSQL 16), `redis-logistica` (Redis 7) y
`backend-logistica` (puerto 8080). Para desarrollar desde el IDE con solo las
dependencias en Docker:

```bash
docker compose up -d bd-logistica redis-logistica
cd backend && ./mvnw spring-boot:run
```

Todos los secretos salen de `backend/.env`, que no se versiona. La plantilla
comentada es [backend/.env.example](backend/.env.example) e incluye qué hace
falta para cada pieza (base de datos, bots de Discord, Google OAuth y el
`JWT_SECRET`, que se genera con `openssl rand -base64 48`).

---

## Qué funciona hoy

**El ciclo completo de una comanda.** De `POST /api/v1/ordenes` a `CONCLUIDO`,
pasando por cocina, despacho, caja y feedback. Crear la comanda es una sola
transacción: valida el score de fraude, descuenta los insumos por receta, aplica
promoción o cupón y escribe el evento de facturación. Si el stock no alcanza, la
respuesta es `422` y no queda ni media comanda ni insumo descontado.

La colección [backend/end_points.json](backend/end_points.json) abre con la
carpeta `Flujo principal (recorrido completo)`, que recorre ese ciclo entero
encadenando las variables sola:

```bash
pnpm dlx newman run backend/end_points.json \
  --folder "Flujo principal (recorrido completo)"
```

**Los bots viven en Discord, no en WhatsApp.** La mensajería es un puerto con
dos adaptadores, y el proveedor se elige con `app.mensajeria.proveedor`
(`discord` | `whatsapp` | `ninguno`). Con Discord no hace falta túnel: el backend
abre la conexión hacia afuera. Son dos identidades separadas —bot IN para el
personal, bot OUT para los clientes— y no son intercambiables. Detalle en
[backend/README.md](backend/README.md).

**La carta real está sembrada.** `DatosInicialesSeeder` crea las 22 secciones del
menú del local (Parrillas, Cordero, Broaster, Cuy, Chicharrones, Chaufas…) en cada
arranque, sección por sección y sin pisar las que ya existen. Es dato del negocio,
no de demostración: se siembra siempre, con `app.seed.enabled` (encendido por
defecto). Los platillos de ejemplo, en cambio, son parte de `DatosDemoSeeder` y
solo aparecen con `app.seed.demo=true`.

**Todavía no hay `backend-facturacion`.** Cada venta escribe un evento
`FACTURA_REQUERIDA` en `outbox_events`, dentro de la misma transacción, y
`OutboxWorker` sabe despacharlo con reintentos y cola muerta — pero queda apagado
con `app.outbox.enabled=false` hasta que exista un destino al que enviarlo.

---

## Credenciales de prueba (solo desarrollo local)

> **Estas credenciales son de la siembra de demostración de un entorno local.**
> Existen únicamente cuando `app.seed.demo=true` y la base de datos está vacía.
> No sirven para ningún despliegue real y no deben crearse en uno: antes de
> exponer esto a una red, hay que borrar estos usuarios y crear el primer
> administrador con `POST /api/v1/auth/bootstrap`, que solo funciona con la
> tabla de trabajadores vacía.

Todos comparten la contraseña `Chaquena2001`. Se entra por
`POST /api/v1/auth/login` con `usernameOrEmail` y `password`.

| Usuario | Correo | Cargo | Rol efectivo | Qué superficie abre |
|---|---|---|---|---|
| `admin` | admin@chaquena.pe | ADMINISTRADOR | `ADMIN` | Todas |
| `mozo1` | mozo@chaquena.pe | MOZO | `MOZO` | POS, despacho |
| `chef1` | cocina@chaquena.pe | JEFE DE COCINA | `COCINA` | Cocina (KDS) |
| `caja1` | caja@chaquena.pe | CAJERO | `CAJA` | Caja |
| `almacen1` | almacen@chaquena.pe | ALMACENERO | `ALMACEN` | Trastienda |
| `repartidor1` | reparto@chaquena.pe | REPARTIDOR | `DELIVERY` | Despacho |

Para comprobar el control de acceso, entra con `chef1` e intenta crear una
promoción: responde `403`.

### El cargo no es el rol

Son dos cosas distintas y confundirlas explica casi todos los "no tengo
permisos" del sistema. El **cargo** es el puesto (`ADMINISTRADOR`, `CAJERO`); el
**rol** es lo que evalúan los 70 `@PreAuthorize` del backend (`ADMIN`, `CAJA`).
El puente entre ambos es la tabla `cargo_roles`.

**Un cargo sin fila en `cargo_roles` deja al usuario sin ningún rol**, y entonces
no abre ninguna superficie. Para comprobarlo:

```sql
SELECT c.nombre AS cargo,
       COALESCE(string_agg(r.nombre, ', '), '(SIN ROLES)') AS roles
FROM cargos c
LEFT JOIN cargo_roles cr ON cr.cargo_id = c.id
LEFT JOIN roles r ON r.id = cr.rol_id
GROUP BY c.nombre ORDER BY c.nombre;
```

### Entrar con Google

Google **identifica, no da de alta**: un correo que no corresponda a un
trabajador ya registrado se rechaza con `401`. Para usarlo con tu propia cuenta,
date de alta primero con `POST /api/v1/trabajadores` usando un cargo que sí tenga
roles. Requiere `GOOGLE_OAUTH_CLIENT_ID` en `backend/.env`; si está vacío, el
backend rechaza todos los inicios con Google y el resto del sistema sigue igual.
