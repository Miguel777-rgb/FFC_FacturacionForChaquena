# frontend-logistica

La cara web del `backend-logistica`. **Angular 22** sin zonas
(`provideZonelessChangeDetection`), señales, control de flujo `@if`/`@for`, y un
cliente HTTP **generado** desde el contrato OpenAPI del backend.

Siete superficies cubren la operación del local. Cuatro son los puestos por los
que pasa una comanda: `/pos` la toma, `/kds` la cocina, `/caja` la cobra y la
cierra, y `/despacho` la entrega al conductor de la empresa de reparto. Las
otras tres administran el local: `/trastienda` (inventario, carta, parámetros,
bots y eventos), `/personal` (quién trabaja y qué abre su cargo) y `/kpis`. Ese
recorrido es el mismo que documenta
[`backend/FLUJO_PRINCIPAL.md`](../backend/FLUJO_PRINCIPAL.md).

La interfaz habla **español, inglés y portugués**, y se cambia sin recargar:
ver [Idiomas](#idiomas).

---

## Once cosas que hay que saber antes de tocar nada

| # | Lo que hay que saber | Evidencia |
|---|---|---|
| 1 | **No hay zone.js.** La detección de cambios se dispara por señales, y *todos* los componentes son `OnPush`. Un valor que no sea señal no repinta la pantalla. | [app.config.ts:23](src/app/app.config.ts#L23) · `ChangeDetectionStrategy.OnPush` en los 19 componentes |
| 2 | **El cliente HTTP no se escribe: se genera.** 23 servicios y 110 modelos salen de 99 rutas / 129 operaciones del contrato. Editar a mano `src/app/api` se pierde en el siguiente `pnpm api:sync`. | [api/generator-config.json](api/generator-config.json) · [src/app/api/.openapi-generator/FILES](src/app/api/.openapi-generator/FILES) |
| 3 | **El token lo pone el cliente generado, no un interceptor.** Viaja solo a los endpoints que declaran `bearerAuth` en el contrato, no a toda petición saliente. | [app.config.ts:50](src/app/app.config.ts#L50) |
| 4 | **El JWT se decodifica, nunca se verifica.** El backend firma con HMAC simétrico; el secreto no está —ni puede estar— en el navegador. Sirve para decidir qué pintar, no qué permitir. | [sesion.service.ts:134](src/app/nucleo/sesion/sesion.service.ts#L134) |
| 5 | **El rol sale del claim `roles`, no del `cargo`.** El cargo se llama `ADMINISTRADOR`; el `@PreAuthorize` pide `ADMIN`. El puente es la tabla `cargo_roles`. | [sesion.service.ts:125](src/app/nucleo/sesion/sesion.service.ts#L125) · [sesion.service.spec.ts:44](src/app/nucleo/sesion/sesion.service.spec.ts#L44) |
| 6 | **Guardas y menús son comodidad, no seguridad.** Los roles están copiados de los `@PreAuthorize`; quien de verdad protege los datos es el servidor con su 403. | [guardas.ts:30](src/app/nucleo/sesion/guardas.ts#L30) · [panel-lateral.ts:29](src/app/disenio/panel-lateral.ts#L29) · [trastienda.page.ts:26](src/app/paginas/trastienda/trastienda.page.ts#L26) |
| 7 | **Los errores tienen un solo camino.** El interceptor traduce cada código a un aviso legible; una pantalla no inventa el texto de un fallo. | [errores.interceptor.ts:64](src/app/nucleo/http/errores.interceptor.ts#L64) |
| 8 | **Ningún estado de dominio se calcula aquí.** Qué gestos se ofrecen lo dice `transicionesPermitidas`; si una comanda quedó `PAGADO` lo decide el servidor y se relee. | [pos.page.ts:673](src/app/paginas/pos/pos.page.ts#L673) · [caja.page.ts:337](src/app/paginas/caja/caja.page.ts#L337) |
| 9 | **Cada superficie es un chunk aparte.** La tablet del mozo no descarga el arqueo de caja: las ocho rutas con componente son `loadComponent`. | [app.routes.ts](src/app/app.routes.ts) · tabla de compilación más abajo |
| 10 | **La interfaz habla tres idiomas y cambia sin recargar**, desde una barra de banderas fija en la esquina. Las plantillas llaman a `t('clave')`; el español viaja en el bundle, el inglés y el portugués se descargan al elegirlos. | [i18n.service.ts](src/app/nucleo/i18n/i18n.service.ts) · [Idiomas](#idiomas) |
| 11 | **Formularios: solo el login usa `ReactiveFormsModule`.** El resto son señales y manejadores `(input)`. No hay `ngModel`, ni `CommonModule`, ni `*ngIf`/`*ngFor` en todo el proyecto. | [login.page.ts:35](src/app/paginas/login/login.page.ts#L35) |

---

## Scripts

Todos se corren desde `frontend/`. El proyecto usa **pnpm**, no npm.

| Script | Qué hace | Detalle que importa | Evidencia |
|---|---|---|---|
| `pnpm start` | `ng serve` en `http://localhost:4200` | Usa la configuración *development*, que reemplaza `environment.ts` por `environment.development.ts` y apunta a `localhost:8080` | [angular.json](angular.json) · [environment.development.ts:7](src/environments/environment.development.ts#L7) |
| `pnpm build` | Compila a `dist/frontend-logistica/browser` | Configuración *production* por defecto: `outputHashing: all` y presupuestos de 500 kB (aviso) / 1 MB (error) para el bundle inicial | [angular.json](angular.json) |
| `pnpm watch` | `ng build --watch` en desarrollo | Sin optimizar y con *source maps*; útil cuando se sirve desde otro servidor | [package.json](package.json) |
| `pnpm test` | `ng test` con el builder `@angular/build:unit-test` | Corre sobre **Vitest 4 + jsdom**. Hoy: 1 archivo, 12 pruebas, ~1 s | [angular.json](angular.json) · [tsconfig.spec.json](tsconfig.spec.json) |
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
├── styles.scss           tokens, regla de color y las piezas compartidas
├── environments/         apiBasePath y googleClientId, por entorno
└── app/
    ├── app.config.ts     providers raíz: zoneless, router, http, Configuration
    ├── app.ts / .html    cascarón: panel lateral + <router-outlet> + avisos
    ├── app.routes.ts     ocho rutas en diferido + dos redirecciones
    ├── api/              GENERADO — no se edita a mano
    ├── nucleo/           sesión, roles, guardas, errores, avisos, logo, idiomas
    │   ├── sesion/       sesion.service · rol · guardas · google.service
    │   ├── http/         errores.interceptor · avisos.service · idioma.interceptor
    │   ├── i18n/         i18n.service · idioma · titulo.strategy
    │   │   └── traducciones/   es (fuente de las claves) · en · pt
    │   └── marca/        logo.service
    ├── disenio/          panel-lateral · pila-avisos · icono · barra-idiomas
    │                     secciones.scss (trastienda, personal y KPIs)
    └── paginas/          login · inicio · sin-permiso · pos · kds · caja
                          despacho · trastienda (+5 secciones) · personal · kpis
```

---

## Componentes, uno por uno

### Arranque y cascarón

| Archivo | Qué es | Lo que hay que saber | Evidencia |
|---|---|---|---|
| [main.ts](src/main.ts) | Punto de entrada | `bootstrapApplication` standalone; no hay `AppModule` en el proyecto | 5 líneas |
| [app.config.ts](src/app/app.config.ts) | Los cuatro *providers* raíz | Zoneless, `withComponentInputBinding`, el interceptor de errores y la `Configuration` del cliente generado con el `bearerAuth` conectado a la sesión | [:23](src/app/app.config.ts#L23), [:34](src/app/app.config.ts#L34), [:50](src/app/app.config.ts#L50) |
| [app.ts](src/app/app.ts) + [app.html](src/app/app.html) | El cascarón | Con sesión pinta panel lateral + `<router-outlet>`; sin sesión, solo el outlet. La pila de avisos está **fuera** del `@if`: un error de login también se ve | [app.html](src/app/app.html) |
| [app.routes.ts](src/app/app.routes.ts) | Ocho rutas + dos redirecciones | Todas `loadComponent`. Toda ruta salvo el login lleva `sesionAbierta`, y las cinco superficies además `exigeRol(...)` copiado del controlador | [app.routes.ts](src/app/app.routes.ts) |
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
| [logo.service.ts](src/app/nucleo/marca/logo.service.ts) | Logo del local | Vive en `localStorage` —al revés que la sesión— porque es preferencia del dispositivo, no del turno. Máximo 512 KB. **No viaja al servidor**: `ConfiguracionLocal` no tiene campo para él | [:10](src/app/nucleo/marca/logo.service.ts#L10), [:17](src/app/nucleo/marca/logo.service.ts#L17) |

### Diseño — las cuatro piezas compartidas

| Archivo | Qué es | Lo que hay que saber | Evidencia |
|---|---|---|---|
| [panel-lateral.ts](src/app/disenio/panel-lateral.ts) | Navegación entre superficies | Solo lista destinos que el rol permite. Plegado deja una regleta de iconos en vez de desaparecer, y el estado se recuerda **por dispositivo**: la pantalla de cocina quiere estar plegada siempre. En móvil se pliega solo | [:29](src/app/disenio/panel-lateral.ts#L29), [:48](src/app/disenio/panel-lateral.ts#L48) |
| [pila-avisos.ts](src/app/disenio/pila-avisos.ts) | Los avisos en pantalla | `role="status"` + `aria-live="polite"`: el lector de pantalla los anuncia sin interrumpir. Se apilan **por encima** de la barra de idiomas, que ocupa la misma esquina |
| [barra-idiomas.ts](src/app/disenio/barra-idiomas.ts) | Las tres banderas | Fija sobre todas las pantallas, también sobre la de entrar. Cada botón lleva el nombre del idioma en `aria-label`: una bandera no es un idioma | [:15](src/app/disenio/pila-avisos.ts#L15) |
| [icono.ts](src/app/disenio/icono.ts) + [iconos.ts](src/app/disenio/iconos.ts) | Nueve trazos SVG | Sin librería de iconos: son nueve `path` en `currentColor`. `aria-hidden` va fijo, porque un icono nunca es la única forma de nombrar algo | [icono.ts:21](src/app/disenio/icono.ts#L21) |
| [styles.scss](src/styles.scss) | Tokens y piezas comunes | `.bloque`, `.chip`, `.cabecera`, `.vacio` y `.cifra` viven aquí porque tres superficies los pintaban idénticos. Tema oscuro por `prefers-color-scheme` | [:150](src/styles.scss#L150), [:256](src/styles.scss#L256) |

**La regla de color no es negociable** ([styles.scss:10](src/styles.scss#L10)).
La marca es roja (`#8F0808`) y el estado crítico también, y se separan por dos
vías a la vez: luminosidad (marca oscura, crítico claro) y **forma**, que es la
que de verdad protege. La marca es lo único **relleno**; lo destructivo va
siempre **contorneado** ([styles.scss:225](src/styles.scss#L225)). Un botón rojo
relleno significa «acción principal», nunca «borrar»: si un `Cancelar comanda`
se pinta relleno, el mozo con prisa lo confunde con `Enviar`, y eso es un error
de servicio, no de estilo. El mínimo táctil (`--toque: 44px`,
[:78](src/styles.scss#L78)) rige inputs y botones globalmente.

### Las superficies

| Ruta / archivo | Roles | Qué resuelve | Detalle clave | APIs |
|---|---|---|---|---|
| [login.page.ts](src/app/paginas/login/login.page.ts) | — | Dos vías de entrada: usuario/contraseña y Google | Muestra **el texto del servidor**, no uno deducido del código HTTP: cuando el backend empezó a distinguir «cuenta dada de baja» de «contraseña incorrecta», el mapeo anterior quedó al revés ([:122](src/app/paginas/login/login.page.ts#L122)) | `Autenticacion` |
| [inicio.page.ts](src/app/paginas/inicio/inicio.page.ts) | cualquiera | Reparte a cada rol a su superficie | Sin plantilla: existe para que `/` sea válida sin repetir el destino en cada redirección | — |
| [sin-permiso.page.ts](src/app/paginas/sin-permiso/sin-permiso.page.ts) | cualquiera | El 403 explicado | Dice con qué roles entró la persona, para que sepa qué pedirle al administrador | — |
| [pos.page.ts](src/app/paginas/pos/pos.page.ts) (794 líneas) | MOZO, ADMIN | Nace la comanda y se entrega en la mesa | Ver abajo | `SalonMesas`, `CatalogoPlatillos`, `CatalogoComplementos`, `CatalogoPromociones`, `Clientes`, `FeedbackYFidelizacion`, `Comandas` |
| [kds.page.ts](src/app/paginas/kds/kds.page.ts) | COCINA, ADMIN | La cola de cocina en tres columnas | Ver abajo | `CocinaKDS`, `InventarioInsumos` |
| [caja.page.ts](src/app/paginas/caja/caja.page.ts) | CAJA, ADMIN | Cobrar, acreditar, calificar y cerrar | Ver abajo | `Comandas`, `CajaPagos`, `CajaArqueoYFraude`, `Reportes`, `FeedbackYFidelizacion` |
| [despacho.page.ts](src/app/paginas/despacho/despacho.page.ts) | DELIVERY, MOZO, ADMIN | El tramo del reparto que ocurre dentro del local | Ver abajo | `Comandas`, `DespachoReparto`, `DespachoTransportistas` |
| [trastienda.page.ts](src/app/paginas/trastienda/trastienda.page.ts) | ALMACEN, ADMIN | Cascarón de cinco pestañas | Ver abajo | — (cada sección pide lo suyo) |
| [personal.page.ts](src/app/paginas/personal/personal.page.ts) | ADMIN | Trabajadores, cargos y qué abre cada cargo | Los roles se **leen**, no se crean: un rol es la palabra dentro de un `@PreAuthorize`, así que inventar «SUPERVISOR» no abriría ninguna puerta. Quitar un rol **no expulsa a quien ya inició sesión**: los roles viajan en el token | `Trabajadores`, `Cargos`, `Roles` |
| [kpis.page.ts](src/app/paginas/kpis/kpis.page.ts) | ADMIN, CAJA | Venta del rango, la serie por horas o días y lo que pide atención ahora | Manda las fechas en ISO **con desfase local**, no en `Z`: con UTC, la cena del sábado en Lima aparece repartida entre sábado y domingo ([:225](src/app/paginas/kpis/kpis.page.ts#L225)) | `Reportes` |

Cada superficie grande es un trío `.ts` + `.html` + `.scss` con el mismo
nombre; solo `inicio` y `sin-permiso` llevan la plantilla en línea, porque son
27 y 43 líneas. Las secciones de la trastienda, el personal y los KPIs comparten
una sola hoja, [secciones.scss](src/app/disenio/secciones.scss), por la misma
razón por la que `.bloque` y `.chip` viven en `styles.scss`: varias copias del
mismo formulario se desincronizan.

`/personal` y `/kpis` fueron pestañas de la trastienda y ya no lo son:
administrar permisos no es trabajo de almacén, y una cifra que se mira al llegar
no puede costar dos clics dentro de otra pantalla
([app.routes.ts:66](src/app/app.routes.ts#L66)).

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
- Un platillo agotado se pinta en gris y el botón no responde: pedirlo terminaría
  en un 422 de stock insuficiente ([:374](src/app/paginas/pos/pos.page.ts#L374)).

#### `/kds` — la cocina

- **Dos relojes distintos.** La cola se refresca cada 15 s
  ([:32](src/app/paginas/kds/kds.page.ts#L32)) y el cronómetro late cada segundo
  ([:111](src/app/paginas/kds/kds.page.ts#L111)): los minutos que manda el
  servidor ya están viejos en una tarjeta que lleva catorce segundos en pantalla,
  así que se re-derivan de `recibida`.
- **El refresco automático es «silencioso»**: no vacía la pantalla, que en cocina
  se leería como que la cola desapareció ([:125](src/app/paginas/kds/kds.page.ts#L125)).
- Las tres columnas se derivan del estado más `flagCierrePlatillo`
  ([:88](src/app/paginas/kds/kds.page.ts#L88)).
- **«Tarde» se mide contra la promesa de la propia cocina**; solo si aún no hay
  promesa se respeta el veredicto del servidor ([:165](src/app/paginas/kds/kds.page.ts#L165)).
- El aviso de insumo faltante **no cambia el estado** de la comanda: va al mozo,
  que es quien puede hablar con el comensal ([:270](src/app/paginas/kds/kds.page.ts#L270)).

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

#### `/trastienda` — cinco secciones

Cada sección pide sus propios datos al montarse, y el `@switch` las monta y
desmonta: entrar a corregir un precio no descarga el kardex entero
([trastienda.page.html:27](src/app/paginas/trastienda/trastienda.page.html#L27)).

| Sección | Roles | Qué hace | Detalle clave |
|---|---|---|---|
| [inventario](src/app/paginas/trastienda/inventario.seccion.ts) | ALMACEN, ADMIN | Insumos, kardex, movimiento suelto y conteo físico | La cantidad va siempre en positivo: **el signo lo pone el motivo** ([:199](src/app/paginas/trastienda/inventario.seccion.ts#L199)). El conteo solo envía las líneas escritas —un insumo no contado no es un insumo en cero— y deja los descuadres en pantalla después de aplicarlos ([:314](src/app/paginas/trastienda/inventario.seccion.ts#L314)) |
| [carta](src/app/paginas/trastienda/carta.seccion.ts) | ALMACEN, ADMIN | Categorías, platillos y recetas | `PUT /platillos/{id}/receta` **reemplaza**, no parchea ([:283](src/app/paginas/trastienda/carta.seccion.ts#L283)). Un platillo sin receta se vende pero no descuenta nada, y el inventario se separa de la realidad sin que nadie lo note ([:25](src/app/paginas/trastienda/carta.seccion.ts#L25)) |
| [local](src/app/paginas/trastienda/local.seccion.ts) | ADMIN | Cuatro parámetros, satisfacción y registro de empresas | No son preferencias de pantalla: el umbral decide cuándo la caja emite cupón y el objetivo de cocina es la vara del KDS. Cambiar uno cambia lo que hacen tres superficies ([:30](src/app/paginas/trastienda/local.seccion.ts#L30)). El `PUT` reemplaza, así que los cuatro campos viajan siempre ([:145](src/app/paginas/trastienda/local.seccion.ts#L145)) |
| [bots](src/app/paginas/trastienda/bots.seccion.ts) | ADMIN | Salud de los dos bots de Discord y vinculaciones | Separa **tres capas que fallan por separado** y en el orden en que hay que descartarlas: proveedor configurado, conexión de cada bot, y quién está vinculado. Un bot perfectamente conectado tampoco atiende si nadie corrió `/vincular` ([:22](src/app/paginas/trastienda/bots.seccion.ts#L22)) |
| [eventos](src/app/paginas/trastienda/outbox.seccion.ts) | ADMIN | Bandeja del outbox | Un evento aquí **no es un error**: es una venta que existe y una factura que aún no. Con el worker apagado todo se queda en `PENDIENTE`, y la pantalla lo dice en vez de fingir una avería ([:30](src/app/paginas/trastienda/outbox.seccion.ts#L30)) |

`ALMACEN` solo ve dos de las cinco pestañas. El resto son de `ADMIN` porque sus
endpoints piden ese rol, y enseñarle la pestaña a un almacenero sería regalarle
un 403 en mitad del turno
([trastienda.page.ts:21](src/app/paginas/trastienda/trastienda.page.ts#L21)).

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
4. `INICIO_POR_ROL` decide dónde aterriza. `ADMIN` va a `/pos` y no a la
   trastienda, porque es quien recorre el flujo principal entero y ese recorrido
   empieza tomando una comanda.
5. `sesionAbierta` + `exigeRol(...)` filtran cada ruta; el panel lateral filtra
   los enlaces; la trastienda filtra las pestañas. **Las tres listas están
   copiadas de los `@PreAuthorize` y tienen que seguir coincidiendo.**

Doce pruebas cubren exactamente esa cadena, incluidos los dos casos que más
duelen: el cargo que no mapea a ningún rol y la sesión vencida que no debe
restaurarse al recargar
([sesion.service.spec.ts](src/app/nucleo/sesion/sesion.service.spec.ts)).

---

## Idiomas

Español (por defecto), inglés y portugués. Se cambian desde una **barra de tres
banderas fija en la esquina inferior derecha**, superpuesta a todas las
pantallas y también a la de entrar, y el cambio **no recarga la página**: quien
tiene media comanda escrita no la pierde por cambiar de idioma.

La barra está siempre a la vista y no dentro de un menú a propósito: quien
necesita cambiar el idioma es justo quien no entiende lo que está leyendo, y a
esa persona no se le puede pedir que primero encuentre dónde se guarda el
ajuste.

### Cómo está montado

| Pieza | Qué es | Por qué así |
|---|---|---|
| [traducciones/es.ts](src/app/nucleo/i18n/traducciones/es.ts) | El diccionario español y la **fuente de las claves** | `ClaveI18n` sale de aquí. Los otros dos se declaran `Record<ClaveI18n, string>`, así que una traducción olvidada **no compila** |
| [traducciones/en.ts](src/app/nucleo/i18n/traducciones/en.ts) · [pt.ts](src/app/nucleo/i18n/traducciones/pt.ts) | Los otros dos idiomas | Se cargan con `import()` al elegirlos: son 28 kB cada uno que la pantalla de cocina no tiene por qué descargar |
| [i18n.service.ts](src/app/nucleo/i18n/i18n.service.ts) | `t`, `tp`, `tEnum` y el idioma como señal | El idioma vive en `localStorage`, como el logo y el panel plegado: es preferencia del dispositivo, no del turno |
| [titulo.strategy.ts](src/app/nucleo/i18n/titulo.strategy.ts) | El título de la pestaña | Las rutas declaran `title: 'titulo.pos'`, una clave. Un `effect` lo reescribe al cambiar de idioma: es lo único que vive fuera de una plantilla |
| [barra-idiomas.ts](src/app/disenio/barra-idiomas.ts) | El control | Tres banderas fijas, dibujadas a mano como los iconos: los emoji de bandera no se pintan en Windows —salen las dos letras del país— y una librería costaría más de lo que ahorra. Se monta en `app.html`, fuera del enrutado |
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

## Refresco: no hay WebSocket

El backend no expone SSE ni WebSocket, así que dos pantallas se refrescan por
*polling*, aislado en un `interval` con `takeUntilDestroyed` para poder
cambiarlo sin tocar los componentes:

| Pantalla | Cada | Evidencia |
|---|---|---|
| `/kds` — la cola | 15 s (+ cronómetro cada 1 s) | [kds.page.ts:32](src/app/paginas/kds/kds.page.ts#L32) |
| `/despacho` — comandas y conductores | 20 s | [despacho.page.ts:30](src/app/paginas/despacho/despacho.page.ts#L30) |

El `nginx.conf` ya está preparado para el cambio: `proxy_buffering off` y
`proxy_read_timeout 1h` en `/api/`, porque con buffering activado un flujo SSE
se quedaría entero en Nginx y la cocina no recibiría nada
([nginx.conf:47](nginx.conf#L47)).

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
| **Inicial** (main + runtime + estilos + español) | 478,83 kB | 104,49 kB |
| `trastienda-page` | 97,21 kB | 12,13 kB |
| `login-page` | 48,53 kB | 11,91 kB |
| `pos-page` | 40,67 kB | 8,61 kB |
| `personal-page` | 30,42 kB | 6,12 kB |
| `caja-page` | 28,30 kB | 6,43 kB |
| `pt` (diccionario) | 28,29 kB | 8,51 kB |
| `en` (diccionario) | 27,28 kB | 8,06 kB |
| `kpis-page` | 20,24 kB | 4,97 kB |
| `despacho-page` | 18,90 kB | 4,63 kB |
| `kds-page` | 16,01 kB | 4,10 kB |
| `sin-permiso-page` / `inicio-page` | 1,27 kB / 457 B | — |

Es la prueba de que el diferido funciona: **la pantalla de cocina descarga 16 kB
de código propio**, no los 330 kB de todas las superficies juntas, y **ningún
dispositivo descarga un idioma que nadie ha elegido**. El inicial queda por
debajo del presupuesto de aviso (500 kB); meter los tres diccionarios dentro lo
pasaba por 33 kB, y ese fue el motivo de cargarlos aparte.

---

## Pruebas

```bash
pnpm test        # Vitest 4 + jsdom · 5 archivos, 37 pruebas, ~1,5 s
```

Las cinco suites cubren lo que falla en silencio, que es lo que no se ve al
mirar la pantalla:

| Suite | Qué guarda |
|---|---|
| [sesion.service.spec.ts](src/app/nucleo/sesion/sesion.service.spec.ts) | Que el rol salga del claim correcto: uno mal extraído abre o cierra pantallas enteras. Arma JWT con firma falsa a propósito, porque el frontend nunca la verifica |
| [panel-lateral.spec.ts](src/app/disenio/panel-lateral.spec.ts) | Que cada rol vea sus destinos y ninguno más, que todo enlace tenga ruta detrás, y que **cambiar de idioma repinte el panel** |
| [trastienda.page.spec.ts](src/app/paginas/trastienda/trastienda.page.spec.ts) | Que las pestañas aparezcan según el rol: una que desaparece no da error, simplemente no está |
| [i18n.service.spec.ts](src/app/nucleo/i18n/i18n.service.spec.ts) | Que los tres diccionarios estén completos y con los mismos huecos, y que el idioma se recuerde al recargar |
| [barra-idiomas.spec.ts](src/app/disenio/barra-idiomas.spec.ts) | Que las tres banderas estén, que marquen cuál está en uso y que pulsarlas cambie el idioma: si la barra desaparece, los otros dos idiomas quedan inalcanzables sin que falle nada |

---

## Qué falta a propósito

- **La factura.** El registro de empresas por RUC existe y `vincularEmpresa` está
  en el contrato, pero no sale ningún comprobante de aquí: `backend-facturacion`
  no existe. Por eso el worker del outbox está apagado (`app.outbox.enabled=false`)
  y los eventos se quedan en `PENDIENTE`, que es lo que la bandeja explica en vez
  de fingir una avería.
- **Refresco en vivo.** Sin WebSocket ni SSE en el backend, KDS y despacho van
  por *polling*.
- **El logo no viaja al servidor.** Cada dispositivo lleva el suyo en
  `localStorage` porque `ConfiguracionLocal` no tiene campo para él. El día que
  lo tenga, `LogoService` es el único punto a cambiar.
- **El tema oscuro no tiene interruptor.** `styles.scss` define los tokens para
  `[data-tema='oscuro']`, pero hoy nadie escribe ese atributo: el tema lo decide
  el sistema operativo vía `prefers-color-scheme`.
- **Los mensajes del servidor siguen en español.** El backend no mira la
  cabecera `Accept-Language` que el frontend ya manda, así que un 409 llega con
  su texto en español aunque la interfaz esté en inglés. Se muestra igual porque
  explica cosas que la pantalla no sabe; traducirlo es trabajo del backend.
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
- `.bloque`, `.chip`, `.cabecera` y `.vacio` viven en `src/styles.scss` porque
  varias superficies los pintan igual. La regla de color está explicada arriba
  del todo de ese archivo y no es negociable.
