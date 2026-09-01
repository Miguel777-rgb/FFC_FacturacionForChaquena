/**
 * Produccion. La base vacia hace que las peticiones salgan al mismo origen que
 * sirve la app: Nginx en el puerto 81 hace de proxy hacia el backend, asi que
 * la imagen de Docker no necesita reconstruirse por entorno.
 */
export const environment = {
  produccion: true,
  apiBasePath: '',

/**
 * Client id de la credencial OAuth de Google Cloud (APIs & Services >
 * Credentials). Vacio esconde el boton de "Entrar con Google": mas vale no
 * ofrecerlo que ofrecer uno que falla.
 *
 * El backend valida que el token venga emitido para este mismo client id, asi
 * que los dos valores tienen que coincidir con GOOGLE_OAUTH_CLIENT_ID de
 * backend/.env. No es un secreto: viaja en la URL del flujo de OAuth.
 */
  googleClientId: '751792078626-uqlfbhmihqfjgmlcm536dqjab3b4tc3a.apps.googleusercontent.com',
} as const; 
