# =============================================================================
# frontend-logistica — Angular 22 servido por Nginx
#
# Dos etapas: Node compila el bundle, Nginx sirve los estaticos. La imagen
# final no lleva Node ni node_modules: son unos pocos megas de HTML, JS y CSS.
# =============================================================================

# --- Etapa 1: compilacion --------------------------------------------------
FROM node:24-alpine AS compilacion

WORKDIR /construccion

# Corepack trae el pnpm exacto que fija "packageManager" en package.json, en
# lugar del que toque globalmente: la imagen y el equipo usan la misma version.
# La activacion no va aqui, sino despues de copiar los manifiestos, porque
# corepack necesita leer package.json para saber que version preparar.
RUN corepack enable
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0

# Los manifiestos primero, para que la capa de node_modules se reutilice
# mientras no cambien las dependencias. pnpm-workspace.yaml entra aqui porque
# lleva onlyBuiltDependencies: sin el, la instalacion omite los scripts de
# esbuild y compania y la compilacion falla.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml .npmrc ./

# --frozen-lockfile falla si el lock no cuadra con package.json, en vez de
# resolver por su cuenta: la imagen instala exactamente lo mismo que el equipo.
RUN pnpm install --frozen-lockfile

COPY . .

# Configuracion de produccion: environment.ts deja apiBasePath vacio, asi que
# las peticiones salen al mismo origen y las resuelve el proxy de Nginx de
# abajo. El navegador nunca cruza origen y CORS no entra en juego.
RUN pnpm run build

# --- Etapa 2: servicio -----------------------------------------------------
FROM nginx:1.27-alpine AS servicio

RUN apk add --no-cache curl

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=compilacion /construccion/dist/frontend-logistica/browser /usr/share/nginx/html

EXPOSE 80

# La imagen de Nginx ya arranca como root para abrir el puerto 80 y baja a
# nginx para atender peticiones, que es el comportamiento que queremos.
CMD ["nginx", "-g", "daemon off;"]
