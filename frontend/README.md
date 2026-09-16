# frontend-logistica

La cara web del `backend-logistica`. **Angular 22** sin zonas
(`provideZonelessChangeDetection`), señales, control de flujo `@if`/`@for`, y un
cliente HTTP **generado** desde el contrato OpenAPI del backend.

Trece superficies cubren el local, en dos grupos del panel. **Operación**:
`/tablero` (cómo va el día y qué hay pendiente), los cuatro puestos por los que
pasa una comanda —`/pos` la toma, `/kds` la cocina, `/caja` la cobra y la
cierra, y `/despacho` la entrega al conductor de la empresa de reparto— y dos
vistas de conjunto: `/ordenes` (todas las comandas, con su detalle) y `/mesas`
(el salón en plano por zonas y la agenda de reservas). **Gestión**: `/menu` (platillos con foto, costo y
alérgenos, complementos y promociones), `/inventario` (insumos con lotes y vencimientos, kardex, conteo y
proveedores), `/reportes` (ventas,
mozos y satisfacción), `/personal` (quién trabaja, qué abre su cargo, sus turnos, su asistencia y su desempeño),
`/clientes` (fichas, puntos, nivel y cupones) y `/configuracion` (datos del
local e IGV, parámetros, niveles de lealtad, bots y eventos). Cada usuario tiene además `/perfil`. El recorrido de una comanda es el mismo que documenta
[`backend/FLUJO_PRINCIPAL.md`](../backend/FLUJO_PRINCIPAL.md).

La interfaz habla **español, inglés y portugués**, y se cambia sin recargar:
ver [Idiomas](#idiomas).

---

## Once cosas que hay que saber antes de tocar nada

| # | Lo que hay que saber | Evidencia |
|---|---|---|
| 1 | **No hay zone.js.** La detección de cambios se dispara por señales, y *todos* los componentes son `OnPush`. Un valor que no sea señal no repinta la pantalla. | [app.config.ts:23](src/app/app.config.ts#L23) · `ChangeDetectionStrategy.OnPush` en los 43 componentes |
| 2 | **El cliente HTTP no se escribe: se genera.** 34 servicios y 128 modelos salen de 129 rutas / 168 operaciones del contrato. Editar a mano `src/app/api` se pierde en el siguiente `pnpm api:sync`. | [api/generator-config.json](api/generator-config.json) · [src/app/api/.openapi-generator/FILES](src/app/api/.openapi-generator/FILES) |
| 3 | **El token lo pone el cliente generado, no un interceptor.** Viaja solo a los endpoints que declaran `bearerAuth` en el contrato, no a toda petición saliente. | [app.config.ts:50](src/app/app.config.ts#L50) |
| 4 | **El JWT se decodifica, nunca se verifica.** El backend firma con HMAC simétrico; el secreto no está —ni puede estar— en el navegador. Sirve para decidir qué pintar, no qué permitir. | [sesion.service.ts:134](src/app/nucleo/sesion/sesion.service.ts#L134) |
| 5 | **El rol sale del claim `roles`, no del `cargo`.** El cargo se llama `ADMINISTRADOR`; el `@PreAuthorize` pide `ADMIN`. El puente es la tabla `cargo_roles`. | [sesion.service.ts:125](src/app/nucleo/sesion/sesion.service.ts#L125) · [sesion.service.spec.ts:44](src/app/nucleo/sesion/sesion.service.spec.ts#L44) |
| 6 | **Guardas y menús son comodidad, no seguridad.** Los roles están copiados de los `@PreAuthorize`; quien de verdad protege los datos es el servidor con su 403. | [guardas.ts:30](src/app/nucleo/sesion/guardas.ts#L30) · [panel-lateral.ts:29](src/app/disenio/panel-lateral.ts#L29) |
| 7 | **Los errores tienen un solo camino.** El interceptor traduce cada código a un aviso legible; una pantalla no inventa el texto de un fallo. | [errores.interceptor.ts:64](src/app/nucleo/http/errores.interceptor.ts#L64) |
| 8 | **Ningún estado de dominio se calcula aquí.** Qué gestos se ofrecen lo dice `transicionesPermitidas`; si una comanda quedó `PAGADO` lo decide el servidor y se relee. | [pos.page.ts:673](src/app/paginas/pos/pos.page.ts#L673) · [caja.page.ts:337](src/app/paginas/caja/caja.page.ts#L337) |
| 9 | **Cada superficie es un chunk aparte.** El celular del mozo no descarga el arqueo de caja: las diecisiete rutas con componente son `loadComponent`. | [app.routes.ts](src/app/app.routes.ts) · tabla de compilación más abajo |
| 10 | **La interfaz habla tres idiomas y cambia sin recargar**, desde tres banderas siempre a la vista: en el pie del panel, en la barra superior del celular y, sin sesión, en una esquina. Las plantillas llaman a `t('clave')`; el español viaja en el bundle, el inglés y el portugués se descargan al elegirlos. | [i18n.service.ts](src/app/nucleo/i18n/i18n.service.ts) · [Idiomas](#idiomas) |
| 11 | **Formularios: solo el login usa `ReactiveFormsModule`.** El resto son señales y manejadores `(input)`. No hay `ngModel`, ni `CommonModule`, ni `*ngIf`/`*ngFor` en todo el proyecto. | [login.page.ts:35](src/app/paginas/login/login.page.ts#L35) |

---

## Scripts

Todos se corren desde `frontend/`. El proyecto usa **pnpm**, no npm.

| Script | Qué hace | Detalle que importa | Evidencia |
|---|---|---|---|
| `pnpm start` | `ng serve` en `http://localhost:4200` | Usa la configuración *development*, que reemplaza `environment.ts` por `environment.development.ts` y apunta a `localhost:8080` | [angular.json](angular.json) · [environment.development.ts:7](src/environments/environment.development.ts#L7) |
| `pnpm build` | Compila a `dist/frontend-logistica/browser` | Configuración *production* por defecto: `outputHashing: all` y presupuestos de 500 kB (aviso) / 1 MB (error) para el bundle inicial | [angular.json](angular.json) |
| `pnpm watch` | `ng build --watch` en desarrollo | Sin optimizar y con *source maps*; útil cuando se sirve desde otro servidor | [package.json](package.json) |
| `pnpm test` | `ng test` con el builder `@angular/build:unit-test` | Corre sobre **Vitest 4 + jsdom**. Hoy: 26 archivos, 92 pruebas, ~5 s | [angular.json](angular.json) · [tsconfig.spec.json](tsconfig.spec.json) |
| `pnpm api:fetch` | Descarga `/v3/api-docs` del backend a `api/openapi.json` | Ordena las claves y borra el bloque `servers` para que dos descargas seguidas den diff vacío; aborta si el contrato no es OpenAPI 3.0 | [api/fetch-spec.mjs](api/fetch-spec.mjs) |
| `pnpm api:generate` | Genera el cliente en `src/app/api` | `typescript-angular`, `stringEnums`, `useSingleRequestParameter`, `providedInRoot` | [api/generator-config.json](api/generator-config.json) |
| `pnpm api:sync` | `api:fetch` + `api:generate` | **Es el único modo legítimo de cambiar `src/app/api`.** El backend tiene que estar arriba | [package.json](package.json) |

Sobre pnpm: los scripts de instalación solo corren para cinco paquetes
declarados a mano. Esa es la razón principal de estar en pnpm y no en npm —un
`postinstall` de una dependencia transitiva no se ejecuta sin aprobarlo—, y por
eso `pnpm-workspace.yaml` viaja al `Dockerfile`: sin él, esbuild no compila.
Evidencia: [pnpm-workspace.yaml](pnpm-workspace.yaml) · [.npmrc](.npmrc) ·
[Dockerfile](Dockerfile).

---

## El árbol

```
src/
├── index.html            carga Google Identity Services con `defer`
├── main.ts               bootstrapApplication(App, appConfig)
├── styles.scss           reúne los parciales de estilos/
├── estilos/              tokens, fuentes, botones, formularios, tablas, insignias…
├── environments/         apiBasePath y googleClientId, por entorno
└── app/
    ├── app.config.ts     providers raíz: zoneless, router, http, Configuration
    ├── app.ts / .html    cascarón: panel lateral o barra superior + <router-outlet> + avisos
    ├── app.routes.ts     doce rutas en diferido + cuatro redirecciones
    ├── api/              GENERADO — no se edita a mano
    ├── nucleo/           sesión, roles, guardas, errores, avisos, logo, idiomas, tema, confirmación, tiempo real
    │   ├── sesion/       sesion.service · rol · guardas · google.service
    │   ├── http/         errores.interceptor · avisos.service · idioma.interceptor · descarga
    │   ├── i18n/         i18n.service · idioma · titulo.strategy · formatos
    │   │   └── traducciones/   es (fuente de las claves) · en · pt
    │   ├── tema/         tema.service
    │   ├── confirmacion/ confirmacion.service
    │   ├── asistencia/   asistencia.service
    │   ├── tiempo-real/  tiempo-real.service
    │   └── marca/        logo.service · archivos
    ├── disenio/          panel-lateral · pila-avisos · icono · barra-idiomas
    │                     barra-superior · dialogo · confirmacion · selector-tema
    │                     en-vivo · descarga · metodos-pago
    │                     secciones.scss (menú, inventario, configuración, personal y reportes)
    └── paginas/          login · inicio · sin-permiso · pos · kds · caja · despacho
                          menu · inventario · configuracion (+3 secciones)
                          personal · reportes
```

---

## Componentes, uno por uno

### Arranque y cascarón

| Archivo | Qué es | Lo que hay que saber | Evidencia |
|---|---|---|---|
| [main.ts](src/main.ts) | Punto de entrada | `bootstrapApplication` standalone; no hay `AppModule` en el proyecto | 5 líneas |
| [app.config.ts](src/app/app.config.ts) | Los cuatro *providers* raíz | Zoneless, `withComponentInputBinding`, el interceptor de errores y la `Configuration` del cliente generado con el `bearerAuth` conectado a la sesión | [:23](src/app/app.config.ts#L23), [:34](src/app/app.config.ts#L34), [:50](src/app/app.config.ts#L50) |
| [app.ts](src/app/app.ts) + [app.html](src/app/app.html) | El cascarón | Con sesión pinta panel lateral + `<router-outlet>`; sin sesión, solo el outlet. La pila de avisos está **fuera** del `@if`: un error de login también se ve | [app.html](src/app/app.html) |
| [app.routes.ts](src/app/app.routes.ts) | Diecisiete rutas + dos redirecciones | Todas `loadComponent`. Toda ruta salvo el login lleva `sesionAbierta`, y las trece superficies además `exigeRol(...)` copiado del controlador; `/perfil` solo pide sesión | [app.routes.ts](src/app/app.routes.ts) |
| [index.html](src/index.html) | Documento base | Carga `accounts.google.com/gsi/client` con `async defer`. Si no hay red, el botón de Google no aparece y el login con contraseña sigue funcionando | [index.html:15](src/index.html#L15) |
| [environments/](src/environments/) | Dos constantes | En producción `apiBasePath` va **vacío**: el navegador habla con un solo origen y Nginx hace de proxy, así que CORS no interviene | [environment.ts:8](src/environments/environment.ts#L8) |

### Núcleo — lo que comparten todas las pantallas

| Archivo | Qué es | Lo que hay que saber | Evidencia |
|---|---|---|---|
| [sesion.service.ts](src/app/nucleo/sesion/sesion.service.ts) | Quién es el usuario | Decodifica el JWT sin validar la firma y lo guarda en **`sessionStorage`**, no en `localStorage`: en un POS compartido, cerrar la pestaña cierra el turno. Un token vencido en el almacenamiento no se restaura | [:41](src/app/nucleo/sesion/sesion.service.ts#L41), [:125](src/app/nucleo/sesion/sesion.service.ts#L125), [:165](src/app/nucleo/sesion/sesion.service.ts#L165) |
| [rol.ts](src/app/nucleo/sesion/rol.ts) | Los seis roles y a dónde entra cada uno | `normalizarRol` es **copia deliberada** de `JwtAuthenticationFilter.normalizar`: mejorarla aquí y no allá haría creer al front que tiene un permiso que el servidor le va a negar | [:27](src/app/nucleo/sesion/rol.ts#L27), [:38](src/app/nucleo/sesion/rol.ts#L38) |
| [guardas.ts](src/app/nucleo/sesion/guardas.ts) | `sesionAbierta` y `exigeRol(...)` | La primera guarda a dónde quería ir el usuario (`volverA`) para devolverlo ahí tras el login; la segunda manda a `/sin-permiso`, no a una página en blanco | [:11](src/app/nucleo/sesion/guardas.ts#L11), [:30](src/app/nucleo/sesion/guardas.ts#L30) |
| [google.service.ts](src/app/nucleo/sesion/google.service.ts) | El botón «Entrar con Google» | Pide un **access token**, no un ID token, porque el backend lo valida contra `tokeninfo`. El scope **no** lleva `openid`: en el flujo implícito Google lo rechaza con un 400 genérico | [:36](src/app/nucleo/sesion/google.service.ts#L36) |
| [errores.interceptor.ts](src/app/nucleo/http/errores.interceptor.ts) | Traductor único de fallos | 401 con sesión abierta la cierra y redirige; 409 y 422 muestran **el texto del servidor**, que ya explica qué transiciones sí se permiten. `mensajeDe` se exporta para que el login lea el mensaje igual | [:35](src/app/nucleo/http/errores.interceptor.ts#L35), [:79](src/app/nucleo/http/errores.interceptor.ts#L79) |
| [avisos.service.ts](src/app/nucleo/http/avisos.service.ts) | Cola de avisos | Los errores **no se van solos**: en un POS táctil nadie está mirando. Solo éxito (4 s) e info (6 s) caducan. Dos fallos idénticos no se apilan | [:44](src/app/nucleo/http/avisos.service.ts#L44) |
| [tiempo-real.service.ts](src/app/nucleo/tiempo-real/tiempo-real.service.ts) | Los avisos en vivo del backend | Lee `GET /eventos/stream` con `fetch` y no con `EventSource`, que no deja mandar el Bearer. Un aviso solo dice el tema que cambió; la pantalla vuelve a pedir lo suyo. `cambios(temas, respaldoMs)` emite con cada aviso, al reconectar después de una caída y, **mientras no hay conexión, cada `respaldoMs`**: el polling de antes quedó como red. Una conexión por pestaña, abierta solo mientras alguna pantalla escucha | [Tiempo real](#tiempo-real) |
| [descarga.ts](src/app/nucleo/http/descarga.ts) | Guardar un Blob como archivo | El nombre sale de `Content-Disposition` —`filename*` primero, que admite tildes—, así que lo decide el servidor, que sabe qué rango usó. La URL del Blob se libera a los 30 s y no en el acto: Safari cancela la descarga si se revoca antes | [descarga.spec.ts](src/app/nucleo/http/descarga.spec.ts) |
| [logo.service.ts](src/app/nucleo/marca/logo.service.ts) + [archivos.ts](src/app/nucleo/marca/archivos.ts) | Logo del local y URL de las imágenes subidas | El logo es un dato del local, el mismo en todas las pantallas: se pide al abrir la sesión y **solo ADMIN lo cambia**; los demás lo ven sin ranura. Importa `archivos.api` y `local.api` sueltos, no el barril, porque carga con el panel. Las imágenes se sirven por UUID **sin token** —un `<img>` no manda cabeceras—, y `problemaDeImagen` rechaza SVG y lo que pase de 2 MB antes de subir; el servidor lo vuelve a mirar en los bytes | [logo.service.ts](src/app/nucleo/marca/logo.service.ts), [archivos.ts](src/app/nucleo/marca/archivos.ts) |

### Diseño — las piezas compartidas

| Archivo | Qué es | Lo que hay que saber | Evidencia |
|---|---|---|---|
| [panel-lateral.ts](src/app/disenio/panel-lateral.ts) | Navegación entre superficies | Solo lista destinos que el rol permite, en dos grupos: **Operación** y **Gestión**. En PC se pliega a una regleta de iconos y lo recuerda por dispositivo; entre 768 y 1023 px es regleta siempre. En su pie van quién es el usuario —que lleva a `/perfil`—, el idioma, el tema y la salida. El mismo componente, con `cajon`, es el menú del celular | [panel-lateral.ts](src/app/disenio/panel-lateral.ts) |
| [barra-superior.ts](src/app/disenio/barra-superior.ts) | La barra del celular | Por debajo de 768 px el panel desaparece: una regleta se comía un sexto del ancho. La barra lleva el menú, la marca, el reloj para marcar entrada y salida, el idioma y el tema; el menú se abre en un `<dialog>` a la izquierda que se cierra al elegir destino | [barra-superior.ts](src/app/disenio/barra-superior.ts) |
| [pila-avisos.ts](src/app/disenio/pila-avisos.ts) | Los avisos en pantalla | `role="status"` + `aria-live="polite"`: el lector de pantalla los anuncia sin interrumpir. Se apilan en la esquina inferior derecha |
| [barra-idiomas.ts](src/app/disenio/barra-idiomas.ts) | Las tres banderas | `integrada` en el pie del panel y en la barra superior; solo en la pantalla de entrar flota en una esquina. Cada botón lleva el nombre del idioma en `aria-label`: una bandera no es un idioma | [barra-idiomas.ts](src/app/disenio/barra-idiomas.ts) |
| [icono.ts](src/app/disenio/icono.ts) + [iconos.ts](src/app/disenio/iconos.ts) | Iconos de un solo juego | Tabler Icons 3.46.0 (MIT), de contorno: se copian los `d` de los que se usan, sin instalar la librería. Nada de emojis ni glifos como `★` en lugar de iconos. `aria-hidden` va fijo, porque un icono nunca es la única forma de nombrar algo | [iconos.ts](src/app/disenio/iconos.ts) |
| [dialogo.ts](src/app/disenio/dialogo.ts) + [confirmacion.ts](src/app/disenio/confirmacion.ts) | Diálogo y confirmación de peligro | Sobre el `<dialog>` nativo: `showModal()` deja inerte el resto, atrapa el foco y cierra con Escape. Toda acción de peligro pasa por `ConfirmacionService.pedir(...)`; el foco arranca en «Dejarlo», no en el botón rojo | [confirmacion.service.ts](src/app/nucleo/confirmacion/confirmacion.service.ts) |
| [en-vivo.ts](src/app/disenio/en-vivo.ts) | «En vivo» junto al título | En cocina, despacho, órdenes, mesas y el tablero. Con la conexión caída dice «Se actualiza sola»: sin eso, una comanda que no aparece podría no existir o no haber llegado todavía | [en-vivo.ts](src/app/disenio/en-vivo.ts) |
| [descarga.ts](src/app/disenio/descarga.ts) | Los botones PDF y Excel | Dos botones y no un menú: son dos formatos fijos, y un desplegable en el celular es un toque más. La pantalla le pasa una función que pide el archivo con su rango; el botón se ocupa del estado, el nombre y el aviso | [descarga.ts](src/app/disenio/descarga.ts) |
| [metodos-pago.ts](src/app/disenio/metodos-pago.ts) | Lo cobrado por método | Barras horizontales, no torta: tres porciones parecidas no se distinguen a ojo. Los tres métodos siempre, también el que quedó en cero | [metodos-pago.ts](src/app/disenio/metodos-pago.ts) |
| [selector-tema.ts](src/app/disenio/selector-tema.ts) | Claro, oscuro o sistema | Un botón en el pie del panel que recorre los tres. `TemaService` escribe `data-tema` en `<html>` y lo recuerda por dispositivo, como el idioma | [tema.service.ts](src/app/nucleo/tema/tema.service.ts) |
| [styles.scss](src/styles.scss) + [estilos/](src/estilos/) | Tokens y piezas comunes | Diez parciales: fuentes, tokens de claro y oscuro, base, botones, formularios, tarjetas, insignias, alertas, tablas y utilidades. `.bloque`, `.chip`, `.tabla`, `.cabecera` y `.cifra` viven aquí porque varias superficies los pintan igual | [_tokens.scss](src/estilos/_tokens.scss) |

**La regla de color** ([_botones.scss](src/estilos/_botones.scss)). La marca es
el borgoña del logo (`#A41E34`). Relleno borgoña es la acción principal; gris,
lo secundario; contorno borgoña, lo importante que no es principal; **relleno
rojo, el peligro**; relleno verde, confirmar. Conviven dos rojos rellenos, así
que un botón de peligro nunca está a un solo toque: el gesto que lo abre es gris
y el rojo vive dentro de la confirmación, con un verbo explícito («Cancelar y
reponer stock»). Los tonos de peligro y éxito son los vecinos de la guía que sí
pasan 4,5:1 con texto blanco (`#D32F2F` y `#2D7A3C`). Sin sombras de color ni
botones que se elevan al pasar el ratón.

El mínimo táctil (`--toque: 44px`) rige en pantallas táctiles; el tamaño chico
(36 px) solo existe con puntero fino, porque `--control-chico` sube a 44 px con
`pointer: coarse`.

**Tipografía.** Poppins en los títulos, Inter en el texto y los botones, y
JetBrains Mono solo para cifras (importes, cronómetros, códigos). Las tres van
alojadas en [public/fuentes/](public/fuentes/) con sus licencias OFL: nada se pide
a Google Fonts. Ningún rótulo va en mayúsculas espaciadas ni en monoespaciada.

### Las superficies

| Ruta / archivo | Roles | Qué resuelve | Detalle clave | APIs |
|---|---|---|---|---|
| [login.page.ts](src/app/paginas/login/login.page.ts) | — | Dos vías de entrada: usuario/contraseña y Google | Muestra **el texto del servidor**, no uno deducido del código HTTP: cuando el backend empezó a distinguir «cuenta dada de baja» de «contraseña incorrecta», el mapeo anterior quedó al revés ([:122](src/app/paginas/login/login.page.ts#L122)) | `Autenticacion` |
| [inicio.page.ts](src/app/paginas/inicio/inicio.page.ts) | cualquiera | Reparte a cada rol a su superficie | Sin plantilla: existe para que `/` sea válida sin repetir el destino en cada redirección | — |
| [sin-permiso.page.ts](src/app/paginas/sin-permiso/sin-permiso.page.ts) | cualquiera | El 403 explicado | Dice con qué roles entró la persona, para que sepa qué pedirle al administrador | — |
| [tablero.page.ts](src/app/paginas/tablero/tablero.page.ts) | ADMIN, CAJA | Cómo va el día y qué hay que ir a resolver | Compara con **ayer a la misma hora**, no con el día entero de ayer: a las once de la mañana cualquier día pierde contra una noche completa. Cada pendiente enlaza a su pantalla solo si el rol puede entrar; si no, queda en cifra. Al lado de lo más vendido, lo cobrado hoy por método de pago. Se recarga con los avisos de comandas, caja, mesas y stock, como mucho una vez cada 5 s: son cinco consultas | `Reportes` |
| [pos.page.ts](src/app/paginas/pos/pos.page.ts) (794 líneas) | MOZO, ADMIN | Nace la comanda y se entrega en la mesa | Ver abajo | `SalonMesas`, `CatalogoPlatillos`, `CatalogoComplementos`, `CatalogoPromociones`, `Clientes`, `FeedbackYFidelizacion`, `Comandas` |
| [kds.page.ts](src/app/paginas/kds/kds.page.ts) | COCINA, ADMIN | La cola de cocina en tres columnas | Ver abajo | `CocinaKDS`, `InventarioInsumos` |
| [caja.page.ts](src/app/paginas/caja/caja.page.ts) | CAJA, ADMIN | Cobrar, acreditar, calificar y cerrar | Ver abajo | `Comandas`, `CajaPagos`, `CajaArqueoYFraude`, `Reportes`, `FeedbackYFidelizacion` |
| [despacho.page.ts](src/app/paginas/despacho/despacho.page.ts) | DELIVERY, MOZO, ADMIN | El tramo del reparto que ocurre dentro del local | Ver abajo | `Comandas`, `DespachoReparto`, `DespachoTransportistas` |
| [ordenes.page.ts](src/app/paginas/ordenes/ordenes.page.ts) | ADMIN, MOZO, CAJA | Todas las comandas, filtradas y paginadas en el servidor, con el detalle en un cajón | Los pasos salen de `transicionesPermitidas`, y cobrar o marcar fraude no se ofrecen: son de la caja. Cancelar pide motivo y deja elegir si se repone el stock. `?orden=` abre una comanda directamente | `Comandas` |
| [mesas.page.ts](src/app/paginas/mesas/mesas.page.ts) | ADMIN, MOZO, CAJA | El salón y la agenda de reservas del día, en pestañas: en PC un plano por zona, en el celular una lista | La ocupación la decide la comanda y «reservada» la agenda: la mesa se ve reservada desde una hora antes de su reserva, sin que nadie la marque a mano. Solo ADMIN mueve el plano, arrastrando o con las flechas; se valida contra las otras mesas de la zona y se guarda entero de una vez (`PUT /mesas/plano`), porque dos mesas que intercambian sitio se pisarían guardadas por separado. Los pasos de una reserva salen de `transicionesPermitidas`, y cancelarla pide confirmación | `SalonMesas`, `SalonReservas`, `Comandas` |
| [menu.page.ts](src/app/paginas/menu/menu.page.ts) | ALMACEN, ADMIN | Platillos con su receta, foto, costo y alérgenos; complementos, promociones y el catálogo de alérgenos, en pestañas | Ver abajo | `CatalogoCategorias`, `CatalogoPlatillos`, `CatalogoComplementos`, `CatalogoPromociones`, `CatalogoAlergenos`, `Archivos`, `InventarioInsumos` |
| [inventario.page.ts](src/app/paginas/inventario/inventario.page.ts) | ALMACEN, ADMIN | Insumos con sus lotes, vencimientos y valor, y proveedores, en pestañas | Ver abajo | `InventarioInsumos`, `InventarioMovimientos`, `InventarioProveedores`, `ReportesExportar` |
| [configuracion.page.ts](src/app/paginas/configuracion/configuracion.page.ts) | ADMIN | Datos del local e IGV, parámetros, niveles de lealtad, bots y eventos, en cinco pestañas | Ver abajo | — (cada sección pide lo suyo) |
| [personal.page.ts](src/app/paginas/personal/personal.page.ts) | ADMIN | Trabajadores, cargos y qué abre cada cargo; el horario de la semana y la asistencia del día, en pestañas | Los roles se **leen**, no se crean: un rol es la palabra dentro de un `@PreAuthorize`, así que inventar «SUPERVISOR» no abriría ninguna puerta. Quitar un rol **no expulsa a quien ya inició sesión**: los roles viajan en el token. Un turno que termina antes de empezar termina al día siguiente, y la ficha lo avisa. La tardanza y la falta **no se escriben**: las calcula el servidor comparando turnos y marcaciones (diez minutos de tolerancia; «faltó» solo cuando el turno ya terminó). La ficha de cada persona trae su desempeño de 30 días: venta, atención, turnos cumplidos, tardanzas, inasistencias y horas. La asistencia se baja por semanas —la del día elegido, de lunes a domingo— con el detalle diario y un resumen por persona | `Trabajadores`, `Cargos`, `Roles`, `PersonalTurnos`, `PersonalAsistencia`, `PersonalDesempeno`, `ReportesExportar` |
| [reportes.page.ts](src/app/paginas/reportes/reportes.page.ts) | ADMIN, CAJA | Ventas del rango, ventas por mozo y, solo para ADMIN, satisfacción | Manda las fechas en ISO **con desfase local**, no en `Z`: con UTC, la cena del sábado en Lima aparece repartida entre sábado y domingo. A la caja no se le pide la satisfacción, que le daría 403. Lo cobrado por método de pago va junto a lo más vendido. **Ventas del rango** baja en PDF o Excel el resumen, los canales, los métodos, los días y los mozos; **Todos los platillos**, la lista completa y no solo los ocho de la tabla. Lo que pasa ahora mismo vive en el tablero | `Reportes`, `ReportesExportar`, `FeedbackYFidelizacion` |
| [clientes.page.ts](src/app/paginas/clientes/clientes.page.ts) | ADMIN, CAJA | Buscar clientes y abrir su ficha: puntos, avance hacia el cupón, cupones, lo que suele pedir, empresas y últimas órdenes | Bloquear por fraude es de ADMIN y pide motivo. El nivel y lo que falta para el siguiente los calcula el servidor desde los puntos: la ficha no guarda ni deduce ningún nivel | `Clientes`, `FeedbackYFidelizacion` |
| [perfil.page.ts](src/app/paginas/perfil/perfil.page.ts) | cualquiera | Los datos propios, marcar entrada y salida, los turnos de los próximos días, el idioma, el tema y cómo vincular Discord | **La contraseña no se cambia aquí** y la pantalla lo dice: el servidor solo deja restablecerla a un administrador. El documento y el celular salen de `/trabajadores/activos`, el único listado de personal abierto a todos los cargos. Marcar es **siempre sobre uno mismo**: la petición no dice quién, el servidor lo saca del token; el perfil y la barra del celular comparten el mismo estado ([asistencia.service.ts](src/app/nucleo/asistencia/asistencia.service.ts)) | `Trabajadores`, `PersonalAsistencia` |

Cada superficie grande es un trío `.ts` + `.html` + `.scss` con el mismo
nombre; solo `inicio` y `sin-permiso` llevan la plantilla en línea, porque son
27 y 43 líneas; `menu` e `inventario` también, porque solo envuelven sus
secciones. Las pantallas de gestión y las vistas de conjunto comparten una sola
hoja, [secciones.scss](src/app/disenio/secciones.scss) —barra de filtros, KPIs,
formularios, la ficha en cajón (`.ficha`, `.datos`) y la paginación—, por la
misma razón por la que `.bloque` y `.chip` viven en `src/estilos/`: varias
copias del mismo formulario se desincronizan.

El detalle de una orden, la ficha de un cliente y el kardex de un insumo se
abren en un **cajón lateral** ([dialogo.ts](src/app/disenio/dialogo.ts) con
`modo="cajon"`); las altas y ediciones, en un diálogo. La lista queda debajo con
sus filtros, y en el celular no hay tablas dentro de tablas.

Hubo una `/trastienda` con cinco pestañas. Se repartió porque juntaba cosas que
no se parecían: cada función administrativa es ahora su propio destino del panel
([app.routes.ts](src/app/app.routes.ts)). `/trastienda` y `/kpis` siguen
existiendo como redirecciones a `/inventario` y `/reportes`.

#### `/pos` — tomar la comanda

- **Tres formas de pedir** (mesa, retiro, delivery) y cambiar de tipo suelta el
  destino que deja de tener sentido ([:257](src/app/paginas/pos/pos.page.ts#L257)).
- **El total que se ve es una estimación.** El importe que vale es el que
  devuelve `POST /ordenes`, calculado con los precios del servidor. El descuento
  del cupón **no** se estima: adivinarlo sería prometer un precio
  ([:183](src/app/paginas/pos/pos.page.ts#L183)).
- **El cupón necesita dos llamadas.** Crear la orden aplica el descuento pero
  deja el cupón `VIGENTE`; quien lo marca gastado es `POST /cupones/{codigo}/canjear`.
  Va **después** del envío: si el canje falla, la comida ya está en cocina y lo
  que corresponde es avisar, no fingir que la venta no ocurrió
  ([:513](src/app/paginas/pos/pos.page.ts#L513)).
- **El OTP de delivery aparece una sola vez.** Solo viaja en la respuesta de
  `POST /ordenes`; las lecturas posteriores lo omiten. Si no se dicta ahí, nadie
  del local puede volver a leerlo ([:166](src/app/paginas/pos/pos.page.ts#L166)).
- **Corregir una comanda ya enviada** está implementado, y solo si el servidor la
  declara `editable`. Cuidado: `PUT /ordenes/{id}/detalles/{detalleId}` reemplaza
  la línea y la que vuelve tiene **id nuevo**; por eso se repinta con la respuesta
  entera y no con el detalle viejo ([:724](src/app/paginas/pos/pos.page.ts#L724)).
- **El nivel de lealtad se muestra, no se estima.** Al identificar al cliente
  aparece su nivel y lo que rebaja, para decirlo en voz alta; el descuento lo
  aplica el servidor al crear la comanda, solo en el POS y sin sumarse al cupón:
  gana el que más rebaja. Si gana el nivel, la comanda vuelve sin `cuponCodigo`
  y por eso el cupón no se canjea.
- Un platillo agotado se pinta en gris y el botón no responde: pedirlo terminaría
  en un 422 de stock insuficiente ([:374](src/app/paginas/pos/pos.page.ts#L374)).

#### `/kds` — la cocina

- **Dos relojes distintos.** La cola se repinta con cada aviso de cocina —y cada
  15 s si se cae el tiempo real— ([:123](src/app/paginas/kds/kds.page.ts#L123)),
  y el cronómetro late cada segundo ([:129](src/app/paginas/kds/kds.page.ts#L129)):
  los minutos que manda el servidor ya están viejos en una tarjeta que lleva un
  rato en pantalla, así que se re-derivan de `recibida`.
- **El refresco automático es «silencioso»**: no vacía la pantalla, que en cocina
  se leería como que la cola desapareció ([:145](src/app/paginas/kds/kds.page.ts#L145)).
- **El aviso sonoro es opcional** y viene apagado: en una cocina con extractor
  puede no oírse y en una pequeña sobra. Son dos notas generadas con Web Audio,
  sin archivo que descargar; suenan cuando la cola trae una comanda que la
  pantalla no había visto, y la primera carga no cuenta
  ([:173](src/app/paginas/kds/kds.page.ts#L173)). Encenderlo lo hace sonar ahí
  mismo: el navegador solo deja sonar después de un gesto.
- Las tres columnas se derivan del estado más `flagCierrePlatillo`
  ([:103](src/app/paginas/kds/kds.page.ts#L103)).
- **«Tarde» se mide contra la promesa de la propia cocina**; solo si aún no hay
  promesa se respeta el veredicto del servidor ([:234](src/app/paginas/kds/kds.page.ts#L234)).
- El aviso de insumo faltante **no cambia el estado** de la comanda: va al mozo,
  que es quien puede hablar con el comensal ([:339](src/app/paginas/kds/kds.page.ts#L339)).

#### `/caja` — cobrar y cerrar

- **Los tres métodos no se comportan igual.** El efectivo se confirma solo y
  devuelve vuelto; tarjeta y billetera nacen `PENDIENTE` y hay que acreditarlas.
  Solo lo `CONFIRMADO` suma: sumar una promesa dejaría una cuenta que parece
  saldada con dinero que no llegó ([:142](src/app/paginas/caja/caja.page.ts#L142)).
- **La comanda no se marca pagada a mano.** El servidor la pasa a `PAGADO` cuando
  lo confirmado cubre el total; aquí se relee para verlo
  ([:337](src/app/paginas/caja/caja.page.ts#L337)).
- **La calificación va entre el cobro y el cierre**: antes sería calificar una
  comida a medias; después, el comensal ya se fue
  ([:188](src/app/paginas/caja/caja.page.ts#L188)). El cupón que emite aparece
  **una sola vez**, en esa respuesta.
- **El IGV se desglosa, no se suma.** Los precios de la carta ya lo incluyen:
  la cuenta muestra la base imponible y el IGV con la tasa que la comanda
  congeló al crearse, y debajo del descuento, de dónde sale (promoción, nivel o
  cupón).
- La alerta de billete falso lleva a un estado terminal, y por eso exige motivo
  escrito ([:466](src/app/paginas/caja/caja.page.ts#L466)).
- La comanda seleccionada se guarda **entera**, no como id: en cuanto se paga
  desaparece de `/ordenes/activas` y aún hace falta para cerrarla
  ([:95](src/app/paginas/caja/caja.page.ts#L95)).

#### `/despacho` — la entrega

- **No hay mapa, ni posición, ni tiempo estimado.** El reparto lo opera una
  empresa externa: el local no sabe dónde está el conductor y pintarlo sería
  inventarlo ([:35](src/app/paginas/despacho/despacho.page.ts#L35)).
- **Asignar y despachar son un solo gesto**, dos llamadas encadenadas con
  `switchMap`: en el mostrador es un único momento, y partirlo en dos botones
  invita a dejar comandas asignadas que nunca salieron
  ([:201](src/app/paginas/despacho/despacho.page.ts#L201)).
- **Se piden dos listas de conductores y no es redundancia**: la paginada trae a
  todos pero omite vehículos, y la de activos sí los trae. Se combinan por id
  ([:133](src/app/paginas/despacho/despacho.page.ts#L133)).
- El código lo dicta el cliente al conductor; el conductor lo repite aquí. Es la
  única prueba de que la comanda llegó ([:246](src/app/paginas/despacho/despacho.page.ts#L246)).

#### `/menu`, `/inventario` y `/configuracion`

Las tres reparten sus secciones en pestañas, y el `@switch` las monta y
desmonta: mirar un parámetro no descarga la bandeja de eventos entera
([configuracion.page.html](src/app/paginas/configuracion/configuracion.page.html)).

| Sección | Roles | Qué hace | Detalle clave |
|---|---|---|---|
| [inventario](src/app/paginas/inventario/inventario.seccion.ts) | ALMACEN, ADMIN | Insumos con su vencimiento y su valor, filtros por alerta, kardex con lotes, alta y edición, movimiento suelto y conteo físico | La cantidad va siempre en positivo: **el signo lo pone el motivo**. Proveedor, costo y vencimiento son del lote de una compra: si se escribieron y luego el motivo pasó a merma, no viajan, porque el servidor rechaza un lote en una salida. Qué está vencido o por vencer lo cuenta el servidor en días de Lima; la pantalla solo lo pinta, y lee «2026-09-16» como día local para no mostrarlo el 15 ([formatos.ts](src/app/nucleo/i18n/formatos.ts)). Los lotes del cajón van en el orden en que se consumen: primero lo que vence antes. El conteo solo envía las líneas escritas —un insumo no contado no es un insumo en cero— y deja los descuadres en pantalla después de aplicarlos. **Inventario al momento** baja todos los insumos, no solo los del filtro: es la foto del almacén |
| [proveedores](src/app/paginas/inventario/proveedores.seccion.ts) | ALMACEN, ADMIN | A quién se le compra | Se dan de baja, no se borran: uno inactivo deja de ofrecerse en la compra, pero sus lotes siguen diciendo de dónde vinieron. El RUC es opcional y, si se escribe, tiene 11 dígitos; se avisa antes de gastar un 400 |
| [platillos](src/app/paginas/menu/carta.seccion.ts) | ALMACEN, ADMIN | Categorías, platillos con foto, tiempo de preparación y alérgenos, costo y margen, y recetas | `PUT /platillos/{id}/receta` **reemplaza**, no parchea. Un platillo sin receta se vende pero no descuenta nada, y el inventario se separa de la realidad sin que nadie lo note. **El costo no se escribe**: lo calcula el servidor con la receta y la última compra de cada insumo; si falta el de alguno, la tabla dice cuál en vez de mostrar un costo parcial, y la ficha recalcula el margen con el precio que se está escribiendo. La foto se sube al elegirla y la ficha guarda solo su id. Un alérgeno dado de baja que el plato ya tenía se sigue ofreciendo marcado: esconderlo haría que guardar lo quitara |
| [alérgenos](src/app/paginas/menu/alergenos.seccion.ts) | ALMACEN, ADMIN | El catálogo que se marca en cada platillo | Llega sembrado con los catorce habituales. Renombrar uno cambia lo que dicen todos los platillos que lo llevan; darlo de baja no lo quita de ellos. El nombre repetido se avisa antes de gastar un 409 |
| [complementos](src/app/paginas/menu/complementos.seccion.ts) | ALMACEN, ADMIN | Lo que se suma a un plato, con su precio | Apagarlo es el gesto diario y va en la fila. El `PUT` reemplaza, así que al editar viaja también si está activo: si no, cambiar el precio de un complemento apagado lo volvería a ofrecer |
| [promociones](src/app/paginas/menu/promociones.seccion.ts) | ADMIN; ALMACEN solo consulta | Rebajas con fecha de inicio y de fin | El estado que se lee es **lo que pasa hoy** —vigente, programada, pausada o vencida—, no el interruptor del servidor |
| [datos del local](src/app/paginas/configuracion/datos-local.seccion.ts) | ADMIN | Nombre comercial, RUC, contacto, IGV y horario de los siete días | Un día **sin horas no es un día cerrado**: queda sin definir, y cerrado es una casilla aparte. Un día abierto con una sola hora se marca en la tabla antes de enviar. El IGV que se guarda aquí es el que congela cada comanda nueva; cambiarlo no reescribe las ventas pasadas. Leer los datos es de cualquier sesión, porque la caja los necesita para el ticket |
| [niveles de lealtad](src/app/paginas/configuracion/niveles.seccion.ts) | ADMIN | Escalones por puntos y el descuento de cada uno | La tabla muestra el tramo completo («5 a 14 puntos») calculado de los mínimos: no hay un «hasta» guardado que pueda contradecirlos. Dos niveles no pueden empezar en el mismo punto, y se avisa antes de enviar para no gastar un 409 |
| [parámetros](src/app/paginas/configuracion/local.seccion.ts) | ADMIN | Cuatro parámetros y registro de empresas | No son preferencias de pantalla: el umbral decide cuándo la caja emite cupón y el objetivo de cocina es la vara del KDS. Cambiar uno cambia lo que hacen tres superficies. El `PUT` reemplaza, así que los cuatro campos viajan siempre. La satisfacción pasó a Ventas y reportes |
| [bots](src/app/paginas/configuracion/bots.seccion.ts) | ADMIN | Salud de los dos bots de Discord y vinculaciones | Separa **tres capas que fallan por separado** y en el orden en que hay que descartarlas: proveedor configurado, conexión de cada bot, y quién está vinculado. Un bot perfectamente conectado tampoco atiende si nadie corrió `/vincular` ([:22](src/app/paginas/configuracion/bots.seccion.ts#L22)) |
| [eventos](src/app/paginas/configuracion/outbox.seccion.ts) | ADMIN | Bandeja del outbox | Un evento aquí **no es un error**: es una venta que existe y una factura que aún no. Con el worker apagado todo se queda en `PENDIENTE`, y la pantalla lo dice en vez de fingir una avería ([:30](src/app/paginas/configuracion/outbox.seccion.ts#L30)) |

`ALMACEN` entra al menú y al inventario, y no ve la configuración: sus tres
secciones piden `ADMIN` en el servidor, y enseñársela a un almacenero sería
regalarle un 403 en mitad del turno.

---

## El cliente generado

`src/app/api` **no se edita a mano**. Sale de `api/openapi.json`, que a su vez
sale del backend en marcha:

```bash
pnpm run api:sync     # descarga el contrato y regenera el cliente
```

Hoy el contrato trae **99 rutas, 129 operaciones y 109 modelos**, que el
generador convierte en 23 servicios (`ComandasApi`, `CocinaKDSApi`,
`CajaPagosApi`…) y 110 archivos de modelo. Los enums llegan como uniones de
cadenas (`stringEnums`) y cada operación recibe **un solo objeto de parámetros**
(`useSingleRequestParameter`), que es la forma que se ve en todas las llamadas
del proyecto.

Dos consecuencias prácticas:

- **El contrato no documenta los errores.** Ningún endpoint declara
  `@ApiResponse`, así que el generador no produce el modelo de error y
  `ErrorResponseDto` está escrito a mano en el interceptor
  ([errores.interceptor.ts:18](src/app/nucleo/http/errores.interceptor.ts#L18)).
  El día que se documenten, se reemplaza por el tipo generado.
- **El `basePath` no viene del contrato.** `fetch-spec.mjs` borra el bloque
  `servers` a propósito: la base la decide `src/environments`.

---

## Sesión, roles y guardas: el recorrido completo

1. `POST /auth/login` (o el canje del access token de Google) devuelve un JWT.
2. `SesionService.abrir` lo decodifica, saca `cargo`, `roles`, `permisos` y `exp`,
   y lo guarda en `sessionStorage`.
3. `extraerRoles` normaliza y **descarta lo que no sea uno de los seis roles
   conocidos**. Un cargo sin rol asociado deja a la persona sin ninguno, y
   entonces ve `/sin-permiso` en vez de una pantalla que le daría 403.
4. `INICIO_POR_ROL` decide dónde aterriza. `ADMIN` y `CAJA` van a `/tablero`,
   porque antes de tocar nada necesitan saber cómo va el día; `MOZO`, a `/pos`;
   `COCINA`, a `/kds`; `DELIVERY`, a `/despacho`, y `ALMACEN`, a `/inventario`. Una prueba comprueba que ningún rol
   aterrice en una redirección ([app.routes.spec.ts](src/app/app.routes.spec.ts)).
5. `sesionAbierta` + `exigeRol(...)` filtran cada ruta y el panel lateral filtra
   los enlaces. **Las dos listas están copiadas de los `@PreAuthorize` y tienen
   que seguir coincidiendo.**

Doce pruebas cubren exactamente esa cadena, incluidos los dos casos que más
duelen: el cargo que no mapea a ningún rol y la sesión vencida que no debe
restaurarse al recargar
([sesion.service.spec.ts](src/app/nucleo/sesion/sesion.service.spec.ts)).

---

## Idiomas

Español (por defecto), inglés y portugués. Se cambian desde **tres banderas
siempre a la vista**: en el pie del panel lateral, en la barra superior del
celular y, en la pantalla de entrar, flotando en una esquina. El cambio **no
recarga la página**: quien tiene media comanda escrita no la pierde.

La barra está siempre a la vista y no dentro de un menú a propósito: quien
necesita cambiar el idioma es justo quien no entiende lo que está leyendo, y a
esa persona no se le puede pedir que primero encuentre dónde se guarda el
ajuste.

### Cómo está montado

| Pieza | Qué es | Por qué así |
|---|---|---|
| [traducciones/es.ts](src/app/nucleo/i18n/traducciones/es.ts) | El diccionario español y la **fuente de las claves** | `ClaveI18n` sale de aquí. Los otros dos se declaran `Record<ClaveI18n, string>`, así que una traducción olvidada **no compila** |
| [traducciones/en.ts](src/app/nucleo/i18n/traducciones/en.ts) · [pt.ts](src/app/nucleo/i18n/traducciones/pt.ts) | Los otros dos idiomas | Se cargan con `import()` al elegirlos: son unos 50 kB cada uno que la pantalla de cocina no tiene por qué descargar |
| [i18n.service.ts](src/app/nucleo/i18n/i18n.service.ts) | `t`, `tp`, `tEnum` y el idioma como señal | El idioma vive en `localStorage`, como el tema y el panel plegado: es preferencia del dispositivo, no del turno |
| [titulo.strategy.ts](src/app/nucleo/i18n/titulo.strategy.ts) | El título de la pestaña | Las rutas declaran `title: 'panel.pos'`, la misma clave que nombra el destino en el panel y titula la pantalla. Un `effect` lo reescribe al cambiar de idioma: es lo único que vive fuera de una plantilla |
| [barra-idiomas.ts](src/app/disenio/barra-idiomas.ts) | El control | Tres banderas dibujadas a mano: los emoji de bandera no se pintan en Windows —salen las dos letras del país— y una librería costaría más de lo que ahorra. Va `integrada` en el panel y en la barra superior; sin sesión, flotante en `app.html` |
| [idioma.interceptor.ts](src/app/nucleo/http/idioma.interceptor.ts) | `Accept-Language` en cada petición | Hoy el backend no la mira. Va igual porque es el mecanismo estándar y esta es la única pieza que sabe el idioma |

### Las tres funciones

```ts
t('caja.cobrar')                                  // 'Cobrar' · 'Charge' · 'Cobrar'
t('pos.mesaZona', { numero: 4, zona: 'Terraza' }) // parámetros entre llaves
tp('comun.items', 3)                              // plural: '3 ítems' / '3 items' / '3 itens'
tEnum('estado', 'EN_PREPARACION')                 // enums del contrato: 'En preparación'
```

Se llaman **desde la plantilla**, no a través de un pipe
([i18n.service.ts:52](src/app/nucleo/i18n/i18n.service.ts#L52)). Un pipe puro
memoriza por sus argumentos, y como la clave no cambia al cambiar el idioma,
devolvería el texto anterior; uno impuro se ejecutaría en cada ciclo. Llamar a
la función deja que el consumidor reactivo de la plantilla vea la señal que hay
dentro, que es justo lo que hace falta. Hay una prueba que lo comprueba contra
el DOM en los tres idiomas
([panel-lateral.spec.ts:80](src/app/disenio/panel-lateral.spec.ts#L80)).

`tEnum` traduce los valores del contrato —estados de comanda, métodos de pago,
tipos de movimiento— y **devuelve el valor crudo si no lo conoce**. El día que
el backend añada un estado, la pantalla enseña `EN_TRANSITO` en vez de dejar el
hueco en blanco.

### Reglas al añadir texto

- Una frase entera por clave. Partirla en trozos para concatenarlos ata el orden
  de las palabras al del español, y en inglés no es el mismo.
- **Nada de HTML dentro de una traducción.** Cuando hace falta destacar un dato,
  la plantilla pone el `<strong>` alrededor del valor y la clave se queda con la
  etiqueta ([caja.page.html](src/app/paginas/caja/caja.page.html)).
- Los parámetros van entre llaves: `{nombre}`. Un hueco sin valor **se deja a la
  vista**: «S/ {total}» es un fallo que alguien reporta; «S/ » parece un importe
  que de verdad no existe.
- El español se escribe con ortografía completa, tildes y eñes incluidas. Es lo
  que lee una persona; el código y los comentarios son otra cosa.
- Los plurales van como pareja `.uno` / `.otros` y se leen con `tp`.

Cuatro pruebas guardan los diccionarios
([i18n.service.spec.ts](src/app/nucleo/i18n/i18n.service.spec.ts)): que ninguna
traducción esté en blanco, que **cada una lleve los mismos `{huecos}` que el
español** —un `{total}` perdido pinta una frase sin importe, que es peor que un
error porque parece correcta—, que todo plural tenga su pareja, y que los tres
idiomas del selector tengan diccionario.

### Lo que no se traduce, y por qué

- **Los mensajes del servidor.** Un 409 explica qué transiciones sí se permiten;
  un 422 dice qué insumo faltó. Eso lo sabe el backend y no la pantalla, así que
  se muestra tal cual ([errores.interceptor.ts:64](src/app/nucleo/http/errores.interceptor.ts#L64)).
  Lo que sí se traduce es el texto de respaldo, el que se escribe cuando la
  respuesta no trae ninguno.
- **Los números y las fechas.** `S/ 1,234.56` se escribe igual en las tres: los
  importes son soles peruanos y el formato es el del local, no el del idioma de
  quien mira. Un vuelto escrito «1.234,56» en un mostrador de Lima confunde al
  cajero.
- **`Chaquena`, `RUC`, `DNI`, `S/`.** La marca y lo que es peruano se quedan
  como están; traducirlos los haría irreconocibles en el mostrador.

---

## Tiempo real

El backend avisa por **Server-Sent Events** en `GET /api/v1/eventos/stream` y
cinco pantallas escuchan. El aviso no trae datos: solo el tema que cambió
(`COCINA`, `COMANDAS`, `MESAS`...), y la pantalla vuelve a pedir lo suyo por el
endpoint de siempre, que es el que aplica los permisos finos. El servidor filtra
además por cargo: la cocina no se entera del ritmo de la caja.

| Pantalla | Escucha | Si se cae la conexión, cada | Evidencia |
|---|---|---|---|
| `/kds` — la cola | `COCINA` | 15 s (el cronómetro va aparte, cada 1 s) | [kds.page.ts:123](src/app/paginas/kds/kds.page.ts#L123) |
| `/despacho` — comandas y conductores | `REPARTO` | 20 s | [despacho.page.ts:129](src/app/paginas/despacho/despacho.page.ts#L129) |
| `/tablero` — el día y los pendientes | `COMANDAS`, `CAJA`, `MESAS`, `STOCK` | 60 s | [tablero.page.ts:214](src/app/paginas/tablero/tablero.page.ts#L214) |
| `/ordenes` — la página abierta | `COMANDAS` | 30 s | [ordenes.page.ts:179](src/app/paginas/ordenes/ordenes.page.ts#L179) |
| `/mesas` — mesas y comandas abiertas; se pausa mientras se edita el plano, para no pisar las mesas a medio mover | `MESAS` | 30 s | [mesas.page.ts:322](src/app/paginas/mesas/mesas.page.ts#L322) |

**Por qué `fetch` y no `EventSource`.** `EventSource` no deja poner la cabecera
`Authorization`, y el token no puede ir en la URL: quedaría en el registro de
cada proxy por el que pase. `TiempoRealService` lee el cuerpo como stream y
separa los eventos a mano; son unas cuarenta líneas.

**Caerse no rompe nada.** Sin conexión, `cambios()` vuelve al refresco
periódico de antes, y el indicador junto al título pasa de «En vivo» a «Se
actualiza sola». Reintenta enseguida y luego a 1, 2, 4... hasta 30 s. Al volver
pide una recarga, por lo que haya cambiado en el hueco. Si en 60 s no llega ni
un byte —el servidor late cada 25 s— la da por muerta aunque el navegador no lo
sepa, que es lo que pasa en un celular que cambió de red.

**El servidor cierra cada conexión a los diez minutos** y el navegador la
reabre con el token vigente: una sesión cerrada deja de recibir avisos sin que
nadie tenga que acordarse de cortarla.

`nginx.conf` tiene `proxy_buffering off` y `proxy_read_timeout 1h` en `/api/`
([nginx.conf:47](nginx.conf#L47)): con buffering, los avisos se quedarían en
Nginx y la cocina no recibiría nada. El backend manda además
`X-Accel-Buffering: no`, por si hay otro proxy delante.

---

## Cómo verlo

Con las dependencias y el backend en pie, y datos de prueba sembrados:

```bash
docker compose up -d bd-logistica redis-logistica   # desde la raíz del repo
cd backend && ./sembrar-demo.sh                     # backend con datos de demo
cd ../frontend && pnpm install && pnpm start        # http://localhost:4200
```

El CORS del backend ya permite `http://localhost:4200`
(`app.cors.allowed-origins`), así que el dev server habla directo con el 8080.
Los usuarios sembrados comparten la clave `Chaquena2001`; `admin` recorre el
flujo entero por sí solo.

### En Docker

La pila completa vive en `compose.local.yml`, **no** en el `compose.yml` de la
raíz: ese publica solo lo vital (backend, base y Redis) y no levanta el
frontend. Es una decisión del repositorio, así que el arranque completo lleva la
bandera:

```bash
docker compose -f compose.local.yml up -d --build                       # http://localhost:81
docker compose -f compose.local.yml up -d --build frontend-logistica    # solo el front
```

La imagen es de dos etapas: Node compila con pnpm y **Nginx sirve los estáticos**,
sin Node ni `node_modules` en la imagen final ([Dockerfile](Dockerfile)). Ese
Nginx además:

- hace de **proxy** de `/api/` hacia `backend-logistica`, que es lo que permite
  el `apiBasePath` vacío —un solo origen, sin CORS—;
- expone `/v3/api-docs` y `/swagger-ui`, para poder correr `pnpm api:sync`
  contra el despliegue;
- resuelve el **enrutado del cliente** (`try_files ... /index.html`), así que
  recargar en `/kds` no da un 404;
- cachea los bundles con hash para siempre e `index.html` nunca, que es lo único
  que apunta a los bundles nuevos.

El puerto es el **81** y no el 80, para dejar ese libre al futuro
`frontend-facturacion` ([compose.local.yml](../compose.local.yml)).

---

## Qué produce la compilación

`pnpm build` (última ejecución verificada):

| Chunk | Raw | Transferido |
|---|---|---|
| **Inicial** (main + runtime + estilos + español) | 403,98 kB | 111,31 kB |
| cliente generado (compartido por las pantallas) | 163,77 kB | 7,67 kB |
| `menu-page` | 84,37 kB | 11,72 kB |
| `configuracion-page` | 80,47 kB | 9,92 kB |
| `personal-page` | 68,47 kB | 10,88 kB |
| `inventario-page` | 53,91 kB | 9,59 kB |
| `pt` (diccionario) | 51,40 kB | 14,19 kB |
| `en` (diccionario) | 49,59 kB | 13,53 kB |
| `login-page` | 47,14 kB | 11,47 kB |
| `mesas-page` | 46,62 kB | 10,52 kB |
| `pos-page` | 44,56 kB | 9,36 kB |
| `caja-page` | 30,27 kB | 6,83 kB |
| `ordenes-page` | 27,25 kB | 6,31 kB |
| `clientes-page` | 24,95 kB | 5,95 kB |
| `reportes-page` | 22,61 kB | 5,58 kB |
| `despacho-page` | 19,46 kB | 4,84 kB |
| `kds-page` | 18,68 kB | 4,86 kB |
| `perfil-page` | 16,02 kB | 3,67 kB |
| `tablero-page` | 14,57 kB | 4,33 kB |
| `sin-permiso-page` | 1,29 kB | 684 B |

Es la prueba de que el diferido funciona: **la pantalla de cocina descarga 19 kB
de código propio** más el cliente generado, que se baja una vez y comparten
todas, y **ningún dispositivo descarga un idioma que nadie ha elegido**: el
inglés y el portugués suman 101 kB y se piden solo al elegirlos. Lo que ganó el
inicial desde la fase 4.3 son servicios generados importados sueltos, nunca el
barril: `archivos.api` y `local.api` para el logo del panel (11 kB) y
`personal-asistencia.api` para el reloj de la barra del celular (8 kB). El
servicio de tiempo real no está ahí: lo traen las cinco pantallas que escuchan.

**Nada del arranque importa desde el barril `./api`.** El barril reexporta todos
los servicios generados, y `app.config.ts` importaba de ahí `Configuration`: eso
bastaba para meter todos los servicios en el bundle inicial, aunque cada
pantalla use dos o tres. Importándolo desde `./api/configuration`, el inicial
bajó de 511 kB a 375 kB y los servicios pasaron a un chunk compartido que llega
con la primera pantalla. Las páginas sí importan del barril, porque ya van en
diferido.

Las fuentes (84 kB en woff2) no cuentan en el bundle: se piden una vez y quedan
en caché. El diálogo de confirmación tampoco: `ConfirmacionService` lo descarga
con `import()` la primera vez que alguien pide confirmar. Con `@defer` el
runtime añadido pesaba más que lo que sacaba del inicial.

---

## Pruebas

```bash
pnpm test        # Vitest 4 + jsdom · 26 archivos, 92 pruebas, ~5 s
```

Las veintiséis suites cubren lo que falla en silencio, que es lo que no se ve al
mirar la pantalla:

| Suite | Qué guarda |
|---|---|
| [sesion.service.spec.ts](src/app/nucleo/sesion/sesion.service.spec.ts) | Que el rol salga del claim correcto: uno mal extraído abre o cierra pantallas enteras. Arma JWT con firma falsa a propósito, porque el frontend nunca la verifica |
| [panel-lateral.spec.ts](src/app/disenio/panel-lateral.spec.ts) | Que cada rol vea sus destinos y ninguno más, agrupados y sin grupos vacíos; que todo enlace tenga una pantalla detrás; que **cambiar de idioma repinte el panel**, y que en el cajón del celular avise al pulsar un destino |
| [configuracion.page.spec.ts](src/app/paginas/configuracion/configuracion.page.spec.ts) | Que la configuración ofrezca sus cinco secciones y monte solo la abierta: una pestaña que desaparece no da error, simplemente no está |
| [datos-local.seccion.spec.ts](src/app/paginas/configuracion/datos-local.seccion.spec.ts) | Que el horario pinte los siete días en su idioma y sin segundos, y que un día abierto con una sola hora se marque y no se envíe |
| [niveles.seccion.spec.ts](src/app/paginas/configuracion/niveles.seccion.spec.ts) | Que cada nivel diga su tramo de puntos, con el más alto sin techo, y que sin niveles se avise que nadie recibe descuento |
| [app.routes.spec.ts](src/app/app.routes.spec.ts) | Que cada rol aterrice en una pantalla y no en una redirección, y que `/trastienda` y `/kpis` lleven a su sitio nuevo |
| [i18n.service.spec.ts](src/app/nucleo/i18n/i18n.service.spec.ts) | Que los tres diccionarios estén completos y con los mismos huecos, y que el idioma se recuerde al recargar |
| [tema.service.spec.ts](src/app/nucleo/tema/tema.service.spec.ts) | Que el tema elegido se escriba en `data-tema`, se recuerde y se aplique al recargar: si falla, la interfaz sigue en claro sin ningún error |
| [confirmacion.service.spec.ts](src/app/nucleo/confirmacion/confirmacion.service.spec.ts) | Que la confirmación de peligro resuelva su promesa y que una nueva dé por rechazada la anterior: una promesa colgada es una acción que nunca ocurre |
| [formatos.spec.ts](src/app/nucleo/i18n/formatos.spec.ts) | Que los minutos se lean como se dicen: «8 d 4 h» y no «11803 min» |
| [tablero.page.spec.ts](src/app/paginas/tablero/tablero.page.spec.ts) | Que la caja vea enlazado lo que puede resolver y en cifra lo que no: un enlace a una pantalla sin permiso es un 403 en mitad del turno |
| [ordenes.page.spec.ts](src/app/paginas/ordenes/ordenes.page.spec.ts) | Que el día se pida con el desfase del local, que `?orden=` abra la comanda y que solo se ofrezcan los pasos que el servidor permite y no son de la caja |
| [mesas.page.spec.ts](src/app/paginas/mesas/mesas.page.spec.ts) | Que las mesas se agrupen por zona con la 2 antes que la 10, que la ocupada diga cuánto lleva y que solo ADMIN pueda dar de alta |
| [clientes.page.spec.ts](src/app/paginas/clientes/clientes.page.spec.ts) | Que la ficha cuente lo que falta para el cupón y que solo ADMIN pueda bloquear por fraude |
| [perfil.page.spec.ts](src/app/paginas/perfil/perfil.page.spec.ts) | Que quien no administra sepa a quién pedir la contraseña y cómo vincular Discord, y que al administrador no se le ofrezca vincularse |
| [reportes.page.spec.ts](src/app/paginas/reportes/reportes.page.spec.ts) | Que a la caja no se le pida la satisfacción —sería un 403 en cada carga— y que el administrador la vea del mismo rango |
| [menu.page.spec.ts](src/app/paginas/menu/menu.page.spec.ts) | Que el menú ofrezca sus tres pestañas y que el almacén vea las promociones sin poder crearlas |
| [tiempo-real.service.spec.ts](src/app/nucleo/tiempo-real/tiempo-real.service.spec.ts) | Que el stream se abra con el Bearer, que solo recargue por los temas que se escuchan —también con un evento partido en dos trozos—, que sin conexión vuelva al refresco periódico y que al volver de una caída pida una recarga. El stream es uno de verdad, escrito a mano por la prueba |
| [descarga.spec.ts](src/app/nucleo/http/descarga.spec.ts) | Que el nombre del archivo salga de `filename*` con sus tildes, luego de `filename`, y si no hay ninguno, el de respaldo |
| [inventario.seccion.spec.ts](src/app/paginas/inventario/inventario.seccion.spec.ts) | Que el filtro de lo que vence funcione y la fecha no se corra un día, y que una merma no arrastre el proveedor, costo y vencimiento escritos para una compra |
| [proveedores.seccion.spec.ts](src/app/paginas/inventario/proveedores.seccion.spec.ts) | Que los dados de baja sigan a la vista, porque sus lotes existen, y que un RUC sin 11 dígitos no se envíe |
| [carta.seccion.spec.ts](src/app/paginas/menu/carta.seccion.spec.ts) | Que el costo y el margen digan qué insumo falta comprar, que editar no pierda un alérgeno dado de baja que el plato ya tenía y que un SVG se rechace antes de subirlo |
| [alergenos.seccion.spec.ts](src/app/paginas/menu/alergenos.seccion.spec.ts) | Que los dados de baja sigan a la vista y que un nombre repetido, con otras mayúsculas, no se envíe |
| [turnos.seccion.spec.ts](src/app/paginas/personal/turnos.seccion.spec.ts) | Que la semana se pinte por persona y que un turno de noche se guarde terminando al día siguiente |
| [asistencia.seccion.spec.ts](src/app/paginas/personal/asistencia.seccion.spec.ts) | Que cada turno muestre su entrada, marque la tardanza y diga quién faltó |
| [barra-idiomas.spec.ts](src/app/disenio/barra-idiomas.spec.ts) | Que las tres banderas estén, que marquen cuál está en uso y que pulsarlas cambie el idioma: si la barra desaparece, los otros dos idiomas quedan inalcanzables sin que falle nada |

---

## Qué falta a propósito

- **La factura.** El registro de empresas por RUC existe y `vincularEmpresa` está
  en el contrato, pero no sale ningún comprobante de aquí: `backend-facturacion`
  no existe. Por eso el worker del outbox está apagado (`app.outbox.enabled=false`)
  y los eventos se quedan en `PENDIENTE`, que es lo que la bandeja explica en vez
  de fingir una avería.
- **Cambiar la contraseña propia.** `PATCH /trabajadores/{id}/password` es solo
  de ADMIN, así que Mi perfil explica a quién pedírsela en vez de ofrecer un
  formulario que terminaría en 403.
- **Los mensajes del servidor siguen en español.** El backend solo mira la
  cabecera `Accept-Language` que el frontend ya manda para los PDF y Excel, que
  salen en el idioma de la pantalla; un 409 llega con su texto en español aunque
  la interfaz esté en inglés. Se muestra igual porque explica cosas que la
  pantalla no sabe; traducirlo es trabajo del backend.
- **Los números y las fechas no cambian con el idioma.** Los importes son soles
  y se escriben como en el local: `1,234.56`. Formatear a la portuguesa
  (`1.234,56`) el cambio que se le devuelve a un comensal en Lima confundiría
  al cajero, no lo ayudaría.

---

## Convenciones que conviene respetar

- El token lo pone el **cliente generado** vía `credentials.bearerAuth`, no un
  interceptor: así solo viaja a los endpoints que declaran seguridad.
- Una pantalla **no inventa el texto de un fallo**. El mensaje del servidor ya
  explica, por ejemplo, qué transiciones de estado sí se permiten.
- Los roles de cada guarda están copiados de los `@PreAuthorize` del controlador
  correspondiente. Esconder un destino es comodidad, no seguridad.
- Todo componente nuevo nace `standalone`, `OnPush` y con señales. Si hace falta
  un formulario complejo, `ReactiveFormsModule`; para lo demás, señales y `(input)`.
- **Ningún texto visible se escribe en una plantilla.** Va al diccionario y se
  lee con `t(...)`; el compilador exige que los tres idiomas lo tengan. Eso
  incluye los `placeholder`, los `aria-label` y los `title`, que también los lee
  una persona.
- `.bloque`, `.chip`, `.tabla`, `.cabecera` y `.vacio` viven en `src/estilos/`
  porque varias superficies los pintan igual. La regla de color está explicada
  arriba de `_botones.scss`: una acción de peligro siempre pasa por
  `ConfirmacionService`, nunca a un solo toque.
- Un icono nuevo sale del mismo juego (Tabler, contorno) y va a `iconos.ts`.
  Nunca un emoji ni un carácter Unicode en su lugar.
