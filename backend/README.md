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
y cada una tiene su petición de ejemplo en `end_points.json` (carpetas 25 a 33).
Leer los catálogos (local, niveles, proveedores, alérgenos) es de cualquier
sesión; la columna «Quién» dice quién puede cambiarlos.

| Módulo | Endpoints | Quién | Lo que hay que saber |
|---|---|---|---|
| Datos del local e IGV (`local`) | `GET` y `PUT /local` · `PUT` y `DELETE /local/logo` | ADMIN | Los precios ya incluyen el IGV. Cada comanda guarda la tasa con la que se vendió (`ordenes.porcentaje_igv`): cambiarla no reescribe las ventas pasadas |
| Niveles de lealtad (`fidelizacion`) | `/niveles-lealtad` · `GET /clientes/{id}/fidelizacion` | ADMIN | El descuento del nivel lo aplica el servidor al crear la comanda en el POS y no se suma al cupón: gana el que más rebaja |
| Proveedores y lotes (`inventario`) | `/proveedores` · `GET /inventario/lotes/{insumoId}` · `GET /inventario/valorizado` · `GET /insumos/alertas` | ADMIN, ALMACEN | Cada compra crea un lote con proveedor, costo y vencimiento, todos opcionales. El consumo descuenta primero lo que vence antes; `stock_actual` sigue siendo el total |
| Fotos y logo (`archivos`) | `POST /archivos` · `GET /archivos/{id}` | subir: ADMIN, ALMACEN; ver: público | WebP, PNG o JPEG hasta 2 MB. El tipo se lee de los bytes, no de lo que dice la subida, y SVG se rechaza porque puede llevar scripts. Se guardan en WebP. Se sirven por UUID sin token, porque un `<img>` no manda cabeceras. Ver abajo |
| Alérgenos (`inventario`) | `/alergenos` | ADMIN, ALMACEN | Llega sembrado con los catorce habituales. El costo y el margen de un platillo no se guardan: se calculan con su receta y la última compra de cada insumo |
| Reservas y plano (`mesas`) | `GET` y `POST /reservas` · `PATCH /reservas/{id}/estado` · `PUT /mesas/plano` | reservas: ADMIN, MOZO, CAJA; plano: ADMIN | Una mesa no guarda que está reservada: lo calcula la agenda, desde una hora antes de la reserva. El plano se guarda entero de una vez, porque dos mesas que intercambian sitio se pisarían guardadas por separado |
| Turnos, asistencia y desempeño (`asistencia`) | `/turnos` · `POST /asistencia/entrada` y `/salida` · `GET /asistencia/mia` · `GET /asistencia/dia` · `GET /trabajadores/{id}/desempeno` | marcar: cada sesión sobre sí misma; lo demás: ADMIN | La tardanza y la falta no se escriben: se calculan comparando turnos y marcaciones, con diez minutos de tolerancia |
| Tiempo real (`tiemporeal`) | `GET /eventos/stream` | cualquier sesión, filtrado por cargo | Ver abajo |
| Llamado de cocina al mozo (`cocina`) | WebSocket `/ws` con STOMP | llama: COCINA, ADMIN; responde: MOZO | Ver abajo |
| Reportes (`reportes`) | `GET /reportes/ventas-por-metodo-pago` · `GET /reportes/{ventas,productos,inventario,asistencia}/exportar` | los cargos de cada pantalla | Ver abajo |
| Lectura de la carta (`inventario`) | `GET /carta/lector` · `POST /carta/lecturas` · `POST /carta/importaciones` | ADMIN | Ver abajo |
| Recuperar la contraseña (`auth`) | `POST /auth/recuperacion` · `POST /auth/recuperacion/confirmacion` | público | Ver abajo |

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

### Llamado de cocina al mozo

Cuando una comanda está lista, cocina llama al mozo desde su pantalla y el
primero que responde «Voy» se la lleva; a los demás se les quita el aviso. Va por
**WebSocket con STOMP** y no por el SSE de arriba porque necesita dos cosas que
SSE no da: que el navegador conteste por el mismo canal y que el servidor sepa
cuántos mozos están conectados.

La conexión es `ws://localhost:8080/api/v1/ws`, WebSocket puro (sin SockJS) con
latidos cada 10 segundos. El navegador no deja poner cabeceras al abrir un
WebSocket, así que el saludo HTTP es público y el JWT viaja en la cabecera
`Authorization` de la trama `CONNECT`. Lo valida `AutenticacionStomp` con la
misma extracción de roles que el filtro HTTP (`AutoridadesDelToken`), y el mismo
interceptor decide qué puede hacer cada rol, porque los mensajes no pasan por los
`@PreAuthorize`. Es una lista blanca: un destino que no figura se rechaza.

| Trama | Destino | Quién | Qué lleva |
|---|---|---|---|
| `SUBSCRIBE` | `/app/llamados/estado` | MOZO, COCINA, ADMIN | Una sola vez, al suscribirse: al mozo, los llamados pendientes; a cocina, los de las comandas en el pase. A los dos, cuántos mozos hay conectados |
| `SUBSCRIBE` | `/topic/llamados/mozos` | MOZO | Llamados nuevos, atendidos y cerrados |
| `SUBSCRIBE` | `/topic/llamados/cocina` | COCINA, ADMIN | Lo mismo, y la presencia cada vez que cambia |
| `SUBSCRIBE` | `/user/queue/llamados` | MOZO, COCINA, ADMIN | Los errores de la propia sesión |
| `SEND` | `/app/llamados/llamar` con `{"ordenId": "…"}` | COCINA, ADMIN | Solo con la comanda marcada «Lista» |
| `SEND` | `/app/llamados/atender` con `{"llamadoId": "…"}` | MOZO | El «Voy» |

Un fallo de autenticación o de permisos responde con una trama `ERROR` y cierra
la conexión. Un error de negocio —llamar por una comanda que no está lista,
responder a un llamado que ya tomó otro mozo— llega solo a quien lo provocó, por
`/user/queue/llamados`, y la conexión sigue abierta.

Cada llamado queda en `llamados_cocina`: quién llamó y cuándo, quién respondió y
cuándo. El primer «Voy» gana en la base de datos, con un `UPDATE … WHERE estado =
'PENDIENTE'`: si dos mozos pulsan a la vez, pasa uno solo, y al otro se le dice
quién va. Si no hay mozos conectados, el llamado espera y lo recibe el primero
que se suscriba. Cuando la comanda sale del pase (entregada, a despacho o
cancelada), `MaquinaEstadosOrden` publica `EstadoOrdenCambiadoEvent` y, después
de confirmar la transacción, sus llamados pendientes pasan a `CERRADO`.

El broker es el simple de Spring, en memoria: como el SSE, sirve con una sola
instancia del backend. Cualquier proxy que vaya delante tiene que dejar pasar
`Upgrade` y `Connection: upgrade` en `/api/v1/ws`; el `nginx.conf` del frontend
le da su propio bloque, porque el de `/api/` fuerza `Connection ""` para el SSE.

### Exportaciones

`GET /reportes/{ventas|productos|inventario|asistencia}/exportar?formato=PDF|XLSX`
devuelve el archivo como adjunto. Ventas y platillos aceptan `desde` y `hasta`
como el resto de reportes; asistencia, dos días (`2026-09-14`), hasta un año.
Cada reporte se arma una sola vez y se escribe con Apache POI o con OpenPDF, así
que el PDF y el Excel del mismo rango nunca dicen cosas distintas. Los textos
salen en español, inglés o portugués según `Accept-Language`; sin esa cabecera,
en español.

### Fotos y logo

Toda imagen que se sube se guarda en WebP, llegue como llegue: pesa menos para
el celular del mozo y todas se sirven igual. La convierte el programa `cwebp`,
llamado como proceso igual que Tesseract (`ConversorWebp`): las fotos JPEG van
con pérdida y calidad 82, los PNG —logos y dibujos de bordes netos— sin
pérdida, y en ningún caso pasan los metadatos, que en una foto de celular
incluyen la ubicación. El límite de subida sigue en 2 MB y no se cambia el
tamaño de la imagen. Sin `cwebp` (el backend corriendo fuera de Docker) se
guarda tal como llega y se avisa en el log; `app.archivos.cwebp` apunta al
programa.

Las que se subieron antes de esto se convierten solas al arrancar
(`MigracionWebp`), en su sitio y con el mismo id, así que los platos y el logo
siguen apuntando a ellas. Se sirven con caché de un año y como inmutables: quien
ya tenía la versión anterior sigue viendo la misma foto.

Los platillos sin foto reciben una del catálogo al arrancar
(`FotosDelCatalogo`): 55 fotos con licencia libre que viajan dentro de la
aplicación, en `src/main/resources/carta/fotos/`, elegidas por el nombre del
platillo. Cada uno recibe su propia copia y la que suba alguien nunca se
reemplaza; a un platillo del catálogo al que se le quita la foto a mano le
vuelve una en el siguiente arranque. El log dice cuántos la recibieron y cuáles
no están en el catálogo. Autores y licencias, en el
[README de esa carpeta](src/main/resources/carta/fotos/README.md).

**El volumen de las imágenes tiene que ser del usuario de la aplicación**, el
uid 999, que la imagen fija. Un volumen creado con la imagen anterior, en
Alpine, quedó del uid 100 y toda subida devolvía 500. Al arrancar, el backend
comprueba que puede escribir en la carpeta y, si no, lo dice en el log con los
dos uid (`ComprobacionCarpetaArchivos`). Se arregla una sola vez:

```bash
docker exec -u root <contenedor-del-backend> chown -R chaquena:chaquena /aplicacion/archivos
```

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

### «Olvidé mi contraseña»

Hasta ahora, quien olvidaba su contraseña dependía de que un administrador se la
restableciera. `POST /auth/recuperacion` manda al correo de la cuenta un enlace a
`/restablecer?token=…`, y `POST /auth/recuperacion/confirmacion` cambia la
contraseña con ese token. Los dos son públicos, porque quien los usa —por
definición— no puede iniciar sesión.

Tres cautelas gobiernan el mecanismo, y las tres son deliberadas:

1. **Pedirlo nunca confirma nada.** La respuesta es 202 y el mismo texto exista o
   no el correo. Si dijera «ese correo no está registrado», el formulario sería
   un comprobador de quién trabaja en el local: basta una lista de direcciones.
2. **Lo que se guarda es la huella, no el token.** El token viaja en el enlace y
   no queda escrito en el servidor; en `tokens_recuperacion` está su SHA-256. Un
   volcado de la base no sirve para entrar.
3. **Un enlace, un uso.** Caduca a los 30 minutos, se marca gastado al usarse y
   pedir otro anula los anteriores. Hay además un tope de tres por persona y
   hora, porque sin freno el botón es un cañón de correos hacia una dirección
   ajena. La contraseña nueva pasa la misma expresión regular que el alta.

El correo sale por SMTP con **STARTTLS en el 587** y va en texto plano, en el
idioma que traiga `Accept-Language` —español, inglés o portugués—, la misma
cabecera que ya decide el idioma de los PDF exportados. La configuración vive en
el `.env` (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USUARIO`, `SMTP_CLAVE`,
`CORREO_REMITENTE` y `RECUPERACION_URL_BASE`, que es el dominio público que se
pega en el enlace). Con Gmail hace falta una contraseña de aplicación, no la del
correo.

Dos detalles operativos que cuestan un rato entender si no se dicen. El envío
**no puede tumbar la petición**: si el servidor de correo no contesta, quien lo
pidió ve el mismo mensaje de siempre y el fallo queda en el registro, porque lo
contrario contaría por la puerta de atrás lo que el 202 calla. Y la sonda de
salud **no mira el correo** (`management.health.mail.enabled=false`): Spring Boot
abre si no una conexión SMTP en cada consulta a `/actuator/health`, y con eso un
servidor de correo caído deja el contenedor «unhealthy» y el local sin sistema
por algo que no impide cobrar.

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
