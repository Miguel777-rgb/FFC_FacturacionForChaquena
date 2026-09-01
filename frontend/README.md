# frontend-logistica

La cara web del backend de logística. Angular 22, sin zonas (`provideZonelessChangeDetection`),
señales, control de flujo `@if`/`@for` y el cliente HTTP generado desde el
contrato OpenAPI del backend (`src/app/api`, no se edita a mano).

## Qué está hecho

Tres de las cinco superficies, las que hacen falta para recorrer el **flujo
principal** de una comanda de mesa —pedir, cocinar, entregar, cobrar, cerrar—
sin ningún servicio externo. Es el mismo recorrido que documenta
[`backend/FLUJO_PRINCIPAL.md`](../backend/FLUJO_PRINCIPAL.md) y que la carpeta
`Flujo principal` de la colección de Postman ejecuta a golpe de *Run folder*.

| Ruta | Rol | Qué hace |
|---|---|---|
| `/pos` | MOZO, ADMIN | Mapa de mesas, carta con la disponibilidad ya resuelta contra el stock, comanda con cantidades y notas, envío a cocina, y la entrega en la mesa |
| `/kds` | COCINA, ADMIN | Cola por orden de llegada con cronómetro, KPIs de cocina, y los dos gestos que mueven la comanda: tomarla y darla por lista |
| `/caja` | CAJA, ADMIN | Cuenta de la comanda entregada, cobro en efectivo con vuelto, cierre del ciclo y arqueo del día |

`/despacho` y `/trastienda` siguen siendo marcadores de posición que declaran
qué endpoints consumirán (`SuperficiePendientePage`).

## Cómo verlo

Con las dependencias y el backend en pie, y datos de prueba sembrados:

```bash
docker compose up -d bd-logistica redis-logistica   # desde la raíz del repo
cd backend && ./sembrar-demo.sh                     # backend con datos de demo
cd ../frontend && pnpm install && pnpm start        # http://localhost:4200
```

El proyecto usa **pnpm**. El CORS del backend ya permite `http://localhost:4200`
(`app.cors.allowed-origins`), así que el dev server habla directo con el 8080;
en producción el Nginx del puerto 81 hace de proxy y la base de la API va vacía.

Los usuarios sembrados comparten la clave `Chaquena2001`. `admin` recorre el
flujo entero por sí solo y por eso aterriza en `/pos`, que es donde empieza.

### En Docker

La pila completa —frontend, backend, Postgres y Redis— vive en
`compose.local.yml`, no en el `compose.yml` de la raíz: ese publica solo lo
vital (backend, base y Redis) y **no levanta el frontend**. Es una decisión del
repositorio, no un olvido, así que el arranque completo lleva la bandera:

```bash
docker compose -f compose.local.yml up -d --build   # http://localhost:81
```

Para reconstruir solo el frontend después de tocar el código:

```bash
docker compose -f compose.local.yml up -d --build frontend-logistica
```

La imagen es de dos etapas: Node compila el bundle con pnpm y Nginx sirve los
estáticos, sin Node ni `node_modules` en la imagen final. Ese Nginx hace además
de proxy de `/api/` hacia `backend-logistica`, y es lo que permite que
`environment.ts` de producción deje `apiBasePath` vacío: el navegador habla con
un solo origen y CORS no llega a intervenir. También resuelve el enrutado del
lado del cliente, así que recargar en `/kds` no da un 404 de Nginx.

### El recorrido, pantalla por pantalla

1. **`/pos`** — elige una mesa, toca platillos de la carta, ajusta cantidades y
   escribe la nota del comensal. *Enviar a cocina* valida el stock, descuenta
   los insumos, calcula el total de verdad y ocupa la mesa.
2. **`/kds`** — la comanda aparece en la cola. *Tomar* arranca el cronómetro de
   preparación; *Listo* sella lo que tardó el plato. La cola se refresca sola
   cada quince segundos.
3. **`/pos`** — en «En el salón», *Entregar* sella el despacho y deja la comanda
   lista para cobrar.
4. **`/caja`** — abre la cuenta, escribe el efectivo recibido y cobra. El vuelto
   lo calcula el servidor. La comanda pasa a PAGADO **sola**, cuando lo
   confirmado cubre el total; entonces *Cerrar comanda* libera la mesa y el
   arqueo del día ya incluye el cobro.

## Qué falta a propósito

Esta es la versión mínima del flujo, no la superficie completa del plan
(`PLAN.md`). Fuera quedan, y todas caben en los mismos endpoints:

- **Complementos, promociones y cupones** en la comanda, y el cliente
  identificado. El POS crea comandas de mesa anónimas y en efectivo.
- **La promesa de tiempo de cocina** (`PATCH /kds/ordenes/{id}/estimar`) y el
  aviso de insumo faltante. Existen en el backend, pero el cliente generado
  viene de un contrato anterior y todavía no los declara: vuelven con
  `pnpm run api:sync` contra el backend en marcha.
- **Billetera y tarjeta** en la caja. Quedan en `PENDIENTE` hasta que un cajero
  las acredita, y esa bandeja de confirmación es otra pantalla.
- **Editar una comanda ya enviada**, cancelarla, y las superficies de despacho
  y trastienda.

## Convenciones que conviene respetar

- El token lo pone el **cliente generado** vía `credentials.bearerAuth`, no un
  interceptor: así solo viaja a los endpoints que declaran seguridad.
- Los errores los traduce `erroresInterceptor` a un aviso legible. Una pantalla
  no debe inventar el texto de un fallo: el mensaje del servidor ya explica, por
  ejemplo, qué transiciones de estado sí se permiten.
- Los roles de cada guarda están copiados de los `@PreAuthorize` del controlador
  correspondiente. Esconder un destino es comodidad, no seguridad.
- `.bloque`, `.chip`, `.cabecera` y `.vacio` viven en `src/styles.scss` porque
  las tres superficies los pintan igual. La regla de color —el rojo relleno es
  la acción principal, lo destructivo va contorneado— está explicada ahí arriba
  del todo y no es negociable.
