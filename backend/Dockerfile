# =============================================================================
# backend-logistica — Spring Boot 4.1 sobre Java 21
#
# Dos etapas: la primera compila con el JDK completo y Maven, la segunda solo
# lleva el JRE y el jar. La imagen final no contiene ni el codigo fuente ni el
# repositorio de Maven.
# =============================================================================

# --- Etapa 1: compilacion --------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS compilacion

WORKDIR /construccion

# El pom va solo y primero: mientras no cambien las dependencias, Docker
# reutiliza la capa con el repositorio ya descargado y la compilacion arranca
# en segundos en lugar de bajarse Spring entero otra vez.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# El nombre del jar lleva la version del pom. Se renombra a uno fijo para que
# la etapa siguiente no tenga que adivinarlo ni usar comodines.
RUN cp target/*.jar /construccion/aplicacion.jar

# --- Etapa 2: ejecucion ----------------------------------------------------
# Debian y no Alpine: la lectura de la carta llama al programa tesseract, que en
# Alpine solo esta en la rama de comunidad y compilado contra musl.
FROM eclipse-temurin:21-jre AS ejecucion

# curl lo usa el healthcheck del compose para saber cuando la aplicacion esta
# lista de verdad, no solo cuando el proceso arranco. tesseract-ocr trae el
# programa; el modelo del idioma se baja aparte.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates tesseract-ocr \
    && rm -rf /var/lib/apt/lists/*

# El modelo de espanol va fijado a un commit y comprobado por hash: es el mismo
# con el que se calibraron las reglas de lectura, y "latest" cambiaria lo que
# lee la carta sin tocar una linea de codigo.
ARG TESSDATA_COMMIT=c2b2e0df86272ce11be323f23f96cf656565ed41
ARG TESSDATA_SHA256=0b0fcbb4665189e01ab8019e591f014dd7260460de072543edd4b2cb4ede7c96
RUN mkdir -p /aplicacion/tessdata \
    && curl -fsSL -o /aplicacion/tessdata/spa.traineddata \
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata/${TESSDATA_COMMIT}/spa.traineddata" \
    && echo "${TESSDATA_SHA256}  /aplicacion/tessdata/spa.traineddata" | sha256sum -c -

# Usuario sin privilegios: si algo se cuela por un endpoint, no lo hace como root.
RUN groupadd -r chaquena && useradd -r -g chaquena chaquena

WORKDIR /aplicacion
COPY --from=compilacion --chown=chaquena:chaquena /construccion/aplicacion.jar ./aplicacion.jar

# El directorio de las fotos existe en la imagen y es del usuario de la
# aplicacion: un volumen nuevo hereda ese propietario al montarse, y sin esto
# la primera foto fallaria por permisos.
RUN mkdir -p /aplicacion/archivos && chown chaquena:chaquena /aplicacion/archivos

# Donde quedo el modelo de espanol de la etapa anterior.
ENV CARTA_TESSDATA=/aplicacion/tessdata

USER chaquena
EXPOSE 8080

# MaxRAMPercentage deja que la JVM respete el limite de memoria del contenedor
# en lugar de mirar la RAM de la maquina entera y reservar de mas.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar aplicacion.jar"]
