/**
 * Descarga el contrato OpenAPI del backend-logistica y lo guarda en
 * api/openapi.json, que es lo que consume el generador del cliente.
 *
 *   node api/fetch-spec.mjs [url]
 *
 * El backend tiene que estar corriendo. Por defecto apunta a localhost:8080;
 * se puede sobreescribir con la variable de entorno API_DOCS_URL o con un
 * argumento.
 *
 * El JSON se guarda con las claves ordenadas y sin el bloque `servers`, para
 * que dos descargas seguidas den un diff vacio y solo se vean los cambios
 * reales del contrato. La URL base del backend la decide el frontend en
 * src/environments, no el contrato.
 */
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const AQUI = dirname(fileURLToPath(import.meta.url));
const DESTINO = join(AQUI, 'openapi.json');
const URL_DOCS =
  process.argv[2] ?? process.env.API_DOCS_URL ?? 'http://localhost:8080/v3/api-docs';

/** Ordena las claves de forma estable para que el diff sea legible. */
function ordenar(valor) {
  if (Array.isArray(valor)) return valor.map(ordenar);
  if (valor && typeof valor === 'object') {
    return Object.fromEntries(
      Object.keys(valor)
        .sort()
        .map((k) => [k, ordenar(valor[k])]),
    );
  }
  return valor;
}

let respuesta;
try {
  respuesta = await fetch(URL_DOCS, { signal: AbortSignal.timeout(15_000) });
} catch (causa) {
  console.error(`No se pudo leer el contrato en ${URL_DOCS}.`);
  console.error('Arranca el backend primero:  cd backend && ./mvnw spring-boot:run');
  console.error(`Detalle: ${causa.message}`);
  process.exit(1);
}

if (!respuesta.ok) {
  console.error(`${URL_DOCS} respondio ${respuesta.status} ${respuesta.statusText}.`);
  process.exit(1);
}

const spec = await respuesta.json();

if (!spec.openapi?.startsWith('3.0')) {
  console.error(
    `El contrato vino en OpenAPI ${spec.openapi}. El generador typescript-angular ` +
      'necesita 3.0: revisa springdoc.api-docs.version en application.properties.',
  );
  process.exit(1);
}

delete spec.servers;

const rutas = Object.keys(spec.paths ?? {}).length;
const operaciones = Object.values(spec.paths ?? {}).reduce(
  (total, ruta) =>
    total +
    Object.keys(ruta).filter((m) => ['get', 'post', 'put', 'patch', 'delete'].includes(m)).length,
  0,
);
const modelos = Object.keys(spec.components?.schemas ?? {}).length;

writeFileSync(DESTINO, JSON.stringify(ordenar(spec), null, 2) + '\n');

console.log(`Contrato guardado en api/openapi.json`);
console.log(`  OpenAPI ${spec.openapi} · ${rutas} rutas · ${operaciones} operaciones · ${modelos} modelos`);
