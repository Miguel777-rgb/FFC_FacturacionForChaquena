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
