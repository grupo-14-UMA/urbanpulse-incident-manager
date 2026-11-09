---
status: accepted
---

# ADR-0016: Autenticar con JWT emitidos por el propio backend

## Contexto

La segunda entrega cierra el ciclo de vida de una incidencia entre tres roles: ciudadano, operador y técnico ([RF01](../requirements.md), HU01). El backend necesita saber qué usuario hace cada petición y qué rol tiene para decidir qué puede hacer. Los clientes son una aplicación móvil y un panel web que consumen la misma API REST ([ADR-0014](0014-aplicacion-movil-y-panel-de-gestion-web.md)), y el backend debe poder escalar en horizontal sin compartir estado de sesión entre instancias.

La sesión debe comportarse como la de una aplicación habitual: el usuario solo vuelve a iniciar sesión si pasa varios días sin abrir el cliente.

## Opciones consideradas

1. JWT firmados por el propio backend con Spring Security y su soporte de servidor de recursos OAuth2 (Nimbus).
2. Sesiones en el servidor con cookie.
3. Un proveedor de identidad externo (Keycloak, Auth0, Firebase Authentication).
4. Una biblioteca JWT de terceros (jjwt, java-jwt).
5. Token de acceso corto más tokens de refresco guardados en el servidor, rotados y con detección de reutilización.

## Decisión

El backend emite y valida sus propios JWT con Spring Security y el soporte de servidor de recursos OAuth2, que usa Nimbus. No hay proveedor de identidad externo ni biblioteca JWT adicional.

- **Firma**: HS256 con el secreto de la variable de entorno `JWT_SECRET`. Es obligatoria: el backend no arranca si falta o tiene menos de 32 bytes, y la configuración de la aplicación no tiene valor por defecto. Docker Compose y `.env.example` llevan un valor solo para desarrollo, marcado como tal.
- **Contenido**: el identificador del usuario como `sub` y el rol en el claim `role`. La autorización lee el rol del token.
- **Sin estado**: no hay sesiones en el servidor. Cerrar sesión consiste en que el cliente descarte el token.
- **Duración y renovación deslizante**: el token dura 7 días. `POST /api/auth/refresh` cambia un token todavía válido por otro nuevo de 7 días, con el rol actual del usuario. Los clientes lo renuevan al arrancar, así que el usuario solo vuelve a iniciar sesión tras 7 días sin usar el cliente.
- **Contraseñas**: BCrypt, con una sal aleatoria por contraseña. Se normalizan a Unicode NFC y nunca se recortan espacios. Al crearlas deben tener al menos 8 caracteres y no pueden estar formadas solo por espacios. No hay reglas de composición, según NIST SP 800-63B. El backend rechaza contraseñas que exceden el límite interno de BCrypt con un mensaje para elegir una más corta; así evita que BCrypt ignore parte de una contraseña. El inicio de sesión no aplica las reglas de registro: solo compara, así que cambiarlas nunca bloquea una cuenta existente.
- **Email**: se le quitan los espacios del principio y del final, también los Unicode como el de no separación, y se guarda en minúsculas, así que se compara sin distinguir mayúsculas y minúsculas.
- **Logs**: las contraseñas, los hashes y los tokens nunca aparecen en respuestas ni en logs.

## Consecuencias

- \+ Cada petición se valida sin consultar la base de datos ni compartir estado, así que el backend puede tener varias instancias sin sesiones pegajosas.
- \+ Un único mecanismo sirve a la aplicación móvil y al panel web, que envían el token en la cabecera `Authorization`.
- \+ Spring Security y Nimbus son dependencias mantenidas del propio ecosistema de Spring Boot; no se añade ninguna biblioteca ni servicio.
- − Un token robado no se puede revocar y se puede renovar indefinidamente mientras siga siendo válido. Se acepta por ahora. La opción 5 lo resuelve, pero necesita una tabla nueva, la rotación de tokens y la purga de los caducados. Es el camino cuando haya que revocar sesiones (cierre de sesión global, cuentas bloqueadas por un administrador).
- − Un cambio de rol no se aplica hasta que el cliente renueva el token.
- − Todas las instancias comparten el mismo secreto simétrico. Cambiarlo invalida todos los tokens emitidos.
- − Las sesiones en el servidor (opción 2) se descartan porque obligan a compartir estado entre instancias y encajan peor con la aplicación móvil. Un proveedor externo (opción 3) añade un servicio que desplegar u otra dependencia de terceros sin que hoy haga falta federación ni inicio de sesión social.
