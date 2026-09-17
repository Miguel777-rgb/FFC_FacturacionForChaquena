# backend-logistica

Spring Boot 4 (Java 21). Paquete raíz `com.chaquena.backend_logistica`.

## Arranque

El compose vive en la raíz del repositorio y levanta los tres servicios:
backend, base de datos y Redis.

Para desarrollar el backend desde el IDE, solo sus dependencias:

```bash
docker compose up -d bd-logistica redis-logistica   # desde la raiz
cd backend && ./mvnw spring-boot:run
```

## El flujo principal

El ciclo completo de una comanda —pedir, cocinar, entregar, cobrar, cerrar—
corre en local con datos de prueba y sin ningún servicio externo de por medio:
arranca con `app.seed.demo=true` y usa las credenciales del
[README de la raíz](../README.md).

La colección `end_points.json` abre con la carpeta
`Flujo principal (recorrido completo)`, que hace ese recorrido entero
encadenando las variables sola. Desde la terminal:

```bash
pnpm dlx newman run end_points.json \
  --folder "Flujo principal (recorrido completo)"
```

## Lo que se agregó con el reajuste de la interfaz

Todas las rutas cuelgan de `/api/v1`; el contrato completo está en `/v3/api-docs`
y cada una tiene su petición de ejemplo en `end_points.json` (carpetas 25 a 32).
Leer los catálogos (local, niveles, proveedores, alérgenos) es de cualquier
sesión; la columna «Quién» dice quién puede cambiarlos.

| Módulo | Endpoints | Quién | Lo que hay que saber |
|---|---|---|---|
| Datos del local e IGV (`local`) | `GET` y `PUT /local` · `PUT` y `DELETE /local/logo` | ADMIN | Los precios ya incluyen el IGV. Cada comanda guarda la tasa con la que se vendió (`ordenes.porcentaje_igv`): cambiarla no reescribe las ventas pasadas |
| Niveles de lealtad (`fidelizacion`) | `/niveles-lealtad` · `GET /clientes/{id}/fidelizacion` | ADMIN | El descuento del nivel lo aplica el servidor al crear la comanda en el POS y no se suma al cupón: gana el que más rebaja |
| Proveedores y lotes (`inventario`) | `/proveedores` · `GET /inventario/lotes/{insumoId}` · `GET /inventario/valorizado` · `GET /insumos/alertas` | ADMIN, ALMACEN | Cada compra crea un lote con proveedor, costo y vencimiento, todos opcionales. El consumo descuenta primero lo que vence antes; `stock_actual` sigue siendo el total |
| Fotos y logo (`archivos`) | `POST /archivos` · `GET /archivos/{id}` | subir: ADMIN, ALMACEN; ver: público | WebP, PNG o JPEG hasta 2 MB. El tipo se lee de los bytes, no de lo que dice la subida, y SVG se rechaza porque puede llevar scripts. Se sirven por UUID sin token, porque un `<img>` no manda cabeceras |
| Alérgenos (`inventario`) | `/alergenos` | ADMIN, ALMACEN | Llega sembrado con los catorce habituales. El costo y el margen de un platillo no se guardan: se calculan con su receta y la última compra de cada insumo |
| Reservas y plano (`mesas`) | `GET` y `POST /reservas` · `PATCH /reservas/{id}/estado` · `PUT /mesas/plano` | reservas: ADMIN, MOZO, CAJA; plano: ADMIN | Una mesa no guarda que está reservada: lo calcula la agenda, desde una hora antes de la reserva. El plano se guarda entero de una vez, porque dos mesas que intercambian sitio se pisarían guardadas por separado |
| Turnos, asistencia y desempeño (`asistencia`) | `/turnos` · `POST /asistencia/entrada` y `/salida` · `GET /asistencia/mia` · `GET /asistencia/dia` · `GET /trabajadores/{id}/desempeno` | marcar: cada sesión sobre sí misma; lo demás: ADMIN | La tardanza y la falta no se escriben: se calculan comparando turnos y marcaciones, con diez minutos de tolerancia |
| Tiempo real (`tiemporeal`) | `GET /eventos/stream` | cualquier sesión, filtrado por cargo | Ver abajo |
| Reportes (`reportes`) | `GET /reportes/ventas-por-metodo-pago` · `GET /reportes/{ventas,productos,inventario,asistencia}/exportar` | los cargos de cada pantalla | Ver abajo |
| Lectura de la carta (`inventario`) | `GET /carta/lector` · `POST /carta/lecturas` · `POST /carta/importaciones` | ADMIN | Ver abajo |

### Tiempo real

`GET /eventos/stream` es un flujo de **Server-Sent Events**. Emite `listo` al
conectar, un `aviso` con el tema que cambió (`COMANDAS`, `COCINA`, `REPARTO`,
`MESAS`, `RESERVAS`, `CAJA`, `STOCK`) y un comentario cada 25 segundos. El aviso
no lleva datos: la pantalla vuelve a pedir lo suyo por el endpoint de siempre,
que es el que aplica los permisos. Aun así se filtra por cargo, y ADMIN recibe
todos los temas.

Los avisos no los publica cada servicio: salen de los oyentes de Hibernate que
se ejecutan **después de confirmar la transacción**, según la tabla que se
escribió. Así ningún camino se olvida de avisar —una comanda del bot del mozo
avisa igual que una del POS— y una venta que se deshace no avisa a nadie. Los
cambios se juntan en ráfagas de 400 ms, y el servidor cierra cada conexión a
los diez minutos para que el navegador reconecte con el token vigente. Vive en
memoria: sirve con una sola instancia del backend.

```bash
curl -N http://localhost:8080/api/v1/eventos/stream -H "Authorization: Bearer $TOKEN"
```

### Exportaciones

`GET /reportes/{ventas|productos|inventario|asistencia}/exportar?formato=PDF|XLSX`
devuelve el archivo como adjunto. Ventas y platillos aceptan `desde` y `hasta`
como el resto de reportes; asistencia, dos días (`2026-09-14`), hasta un año.
Cada reporte se arma una sola vez y se escribe con Apache POI o con OpenPDF, así
que el PDF y el Excel del mismo rango nunca dicen cosas distintas. Los textos
salen en español, inglés o portugués según `Accept-Language`; sin esa cabecera,
en español.

### Leer la carta desde fotos

La carta del local existe impresa antes que en el sistema, y cargarla a mano son
decenas de platillos con su precio y su descripción. `POST /carta/lecturas`
recibe una foto y devuelve lo que reconoció —secciones, platillos, precios,
descripciones, el icono vegetariano y los adicionales del tipo «+5 con chaufa»—
**sin guardar nada**. Se revisa en pantalla y después `POST /carta/importaciones`
crea o actualiza solo las filas que quedaron marcadas.

El reconocimiento es Tesseract, que corre como programa en la propia imagen del
servidor: la foto se agranda y se pasa a grises, se parte en columnas por donde
no hay tinta, y las reglas de `inventario/service/lectura/` leen la caja de cada
palabra para decidir qué es un título, qué un nombre, qué una descripción y qué
un precio. Los precios se vuelven a leer en una segunda pasada solo con dígitos,
porque una cifra suelta al borde de la página se confunde con letras. Lo que la
foto no dejó claro viaja marcado como dudoso y con el recorte de donde salió,
para que quien revisa lo compare sin volver a la carta de papel.

Dos cosas no cambian nunca al importar: un platillo que ya existe conserva su
nombre y su sección —solo se le actualizan el precio y la descripción—, y nada se
borra. El reconocimiento por nombre ignora tildes y mayúsculas, así que «PARRILLA
DE RES» y «Parrilla de Res» son el mismo plato.

La imagen del backend trae `tesseract-ocr` y el modelo de español fijado a un
commit y comprobado por hash, porque las reglas se calibraron con ese modelo.
Fuera de Docker hacen falta `app.carta.tesseract` (el programa) y
`app.carta.tessdata` (la carpeta del modelo); si falta cualquiera de los dos,
`GET /carta/lector` lo dice y la pantalla no ofrece subir fotos.

## Los bots

El sistema opera dos bots de **Discord**: uno interno para el personal (stock,
comandas del mozo, tablero de cocina) y otro de cara al cliente (carta, pedidos,
delivery y código OTP). El servicio externo es intercambiable: `WhatsApp` sigue
implementado como adaptador de reserva.

Los pasos del portal de Discord —crear cada aplicación, sacar su token,
encender el *Message Content Intent* e invitar los bots al servidor— están
comentados uno a uno en [`.env.example`](.env.example).

Resumen de configuración en `backend/.env` (plantilla en `.env.example`):

```bash
MENSAJERIA_PROVEEDOR=discord
DISCORD_BOT_IN_TOKEN=...
DISCORD_BOT_OUT_TOKEN=...
DISCORD_GUILD_ID=...
DISCORD_CANAL_COCINA=...
```

**No hace falta túnel ni URL pública.** Discord se conecta por WebSocket desde el
backend hacia la pasarela, así que la demostración corre desde `localhost`.

### Si se vuelve a WhatsApp

Con `MENSAJERIA_PROVEEDOR=whatsapp` reaparecen los webhooks de Meta y vuelve a
hacer falta exponerlos a internet:

```bash
sudo pacman -S cloudflared        # o: paru -S cloudflared
cloudflared tunnel --url http://localhost:8080
```

La URL que hay que dar de alta en Meta es
`https://<tu-url-cloudflared>/api/v1/whatsapp/in/webhook` (y `/out/webhook` para
el bot de clientes). El verify token se define en `backend/.env`
(`WHATSAPP_BOT_IN_VERIFY_TOKEN`), que no se versiona.

Prueba local del webhook sin Meta de por medio:

```bash
curl -X POST http://localhost:8080/api/v1/whatsapp/in/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "entry": [{
      "changes": [{
        "value": {
          "messages": [{
            "from": "51900000000",
            "type": "text",
            "text": { "body": "hola" }
          }]
        }
      }]
    }]
  }'
```

## Nota operativa

En caso de robo se deshabilita el número o la cuenta hasta próximo aviso. El
motivo de la deshabilitación se indica siempre en el sistema.
