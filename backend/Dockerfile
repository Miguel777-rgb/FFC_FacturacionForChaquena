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
FROM eclipse-temurin:21-jre-alpine AS ejecucion

# curl lo usa el healthcheck del compose para saber cuando la aplicacion esta
# lista de verdad, no solo cuando el proceso arranco.
RUN apk add --no-cache curl

# Usuario sin privilegios: si algo se cuela por un endpoint, no lo hace como root.
RUN addgroup -S chaquena && adduser -S chaquena -G chaquena

WORKDIR /aplicacion
COPY --from=compilacion --chown=chaquena:chaquena /construccion/aplicacion.jar ./aplicacion.jar

USER chaquena
EXPOSE 8080

# MaxRAMPercentage deja que la JVM respete el limite de memoria del contenedor
# en lugar de mirar la RAM de la maquina entera y reservar de mas.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar aplicacion.jar"]
