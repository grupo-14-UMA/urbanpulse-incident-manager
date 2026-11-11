# Gestión de errores

Política común de UrbanPulse ante una petición o un recurso que falla: qué ve el usuario, qué se registra, cuándo se reintenta y qué estados tiene una pantalla. La siguen todos los clientes (la aplicación móvil y el panel de gestión) y la API en la forma de sus respuestas de error.

## Reglas

1. **Ningún fallo es silencioso.** O se muestra el error, o se muestra un aviso con una alternativa. Por ejemplo, si no se puede obtener la posición GPS, se avisa y se ofrece tocar el mapa para marcar la localización.
2. **El usuario nunca ve detalles técnicos**: códigos HTTP, rutas, nombres de campos o de parámetros, nombres de variables de entorno, trazas ni respuestas crudas del backend. Lo que necesita un desarrollador para diagnosticar el fallo va al log.
3. **Los mensajes están en español, en lenguaje llano y dicen qué puede hacer el usuario**: reintentar, corregir un dato, esperar o volver a iniciar sesión.

## Tipos de error

| Tipo | Ejemplo | Qué ve el usuario | Reintento automático | Botón «Reintentar» |
| --- | --- | --- | --- | --- |
| Sin conexión o timeout | Red caída, backend parado o sin respuesta dentro del plazo de la petición (10 s; 60 s para subir una fotografía) | «No se pudo conectar con UrbanPulse. Comprueba tu conexión e inténtalo de nuevo.» | Sí, solo en lecturas | Sí |
| Error del servidor | HTTP 500, 502, 503, 504 | «Algo ha fallado en UrbanPulse. Inténtalo de nuevo en unos minutos.» | Solo 502, 503 y 504, y solo en lecturas | Sí |
| Datos no válidos | HTTP 400 | El `message` del backend y los mensajes de `errors`. Si no hay, «Revisa los datos e inténtalo de nuevo.» | No | No: el usuario corrige y vuelve a enviar |
| No encontrado | HTTP 404 | Un texto propio de la pantalla, por ejemplo «Esta incidencia no existe». | No | No |
| Sesión no válida | HTTP 401 | «Tu sesión ha caducado. Vuelve a iniciar sesión.» y se lleva al inicio de sesión. | No | No |
| Sin permiso | HTTP 403 | «No tienes permiso para hacer esto.» | No | No |
| Otro error del cliente | Otro código 4xx | Igual que los datos no válidos. | No | No |
| Respuesta inesperada | Cuerpo que no es JSON, campo obligatorio ausente o con un formato no válido | El mismo texto que un error del servidor. | No | Sí |
| Fallo al cargar fotografía | La descarga o decodificación de la foto falla | «No se pudo cargar esta foto.» | No | Sí |
| Recurso del dispositivo | Permiso de ubicación, cámara o galería denegado, GPS sin señal | Un aviso que no bloquea la pantalla y ofrece la alternativa, como marcar la localización en el mapa o enviar sin fotos. | No | No |

El cliente clasifica el error en un único sitio, su capa de acceso a la API, y las pantallas solo reciben el tipo y el texto para el usuario. Una pantalla puede sustituir el texto de un tipo por otro más concreto (como en el 404), pero no mostrar el diagnóstico técnico.

La pantalla de inicio de sesión es la excepción del 401: allí significa que el email o la contraseña no son correctos, no que la sesión haya caducado. Muestra el `message` del backend («El email o la contraseña no son correctos.»), conserva lo que escribió el usuario y no redirige.

## Contrato de errores de la API

- Toda respuesta de error usa `ApiErrorResponse`: `message`, un texto general, y `errors`, un mapa de campo a mensaje que puede estar vacío.
- Los mensajes de las respuestas 4xx están escritos para el usuario final, en español y sin nombres de campos, rutas ni detalles de implementación. El cliente muestra los valores de `errors`, nunca sus claves.
- Las respuestas 5xx llevan un `message` genérico, sin el mensaje de la excepción, trazas ni nombres de clases. El detalle queda en el log del backend.

## Reintentos

Los reintentos automáticos absorben cortes breves de conexión o del backend sin enseñar un error al usuario:

- **Solo en lecturas** (`GET`), porque se pueden repetir sin efectos. Una escritura nunca se reintenta automáticamente: si la petición llegó al backend pero se perdió la respuesta, repetirla registraría la incidencia dos veces.
- **Solo en errores transitorios**: sin conexión, timeout, 502, 503 y 504. Un 4xx no se reintenta, porque repetirlo no lo arregla.
- **Tres intentos en total**: el primero inmediato, el segundo tras unos 1 s y el tercero tras unos 3 s más.
- **Con jitter**: cada espera se multiplica por un factor aleatorio entre 0,5 y 1,5. Así los clientes no reintentan todos a la vez cuando el backend vuelve tras una caída.
- **Con un presupuesto de 15 s** desde el primer intento: pasado ese tiempo no se lanza un intento nuevo. Las lecturas y envíos normales tienen un timeout de 10 s; subir una fotografía puede tardar hasta 60 s.

Mientras dura la secuencia, la pantalla sigue en estado de carga. Si todos los intentos fallan, muestra el error con el botón «Reintentar», que vuelve a lanzar la secuencia completa.

## Envíos

Al enviar datos (registrar una incidencia, adjuntar fotografías o cambiar su estado):

- El botón de envío queda bloqueado mientras se envía, para evitar el doble envío.
- No se puede salir de la pantalla a mitad del envío, porque se perdería el resultado.
- Si el envío falla, se conserva lo que escribió el usuario y se muestra el error junto al botón. Para reintentar, el usuario vuelve a pulsarlo.
- La app móvil registra primero la incidencia y después sube las fotos una a una. Si una foto falla, mantiene la incidencia registrada, permite reintentar las fotos pendientes y deja continuar sin añadir más fotos.

## Estados de una pantalla

Toda pantalla que carga datos distingue estos estados y nunca se queda en blanco:

| Estado | Qué se muestra |
| --- | --- |
| Cargando | Un indicador de carga, también durante los reintentos automáticos. |
| Con contenido | Los datos. |
| Vacío | Un texto que lo dice, por ejemplo «No hay categorías disponibles.». |
| Error | El mensaje del tipo de error y, si corresponde, «Reintentar». |
| No encontrado | Solo al cargar un elemento por su identificador: el texto propio de la pantalla. |

Si falla una recarga y ya había datos, se conservan los anteriores y se muestra un aviso con «Reintentar». Pueden estar desactualizados, pero siguen siendo útiles.

## Qué se registra en el cliente

Cada petición fallida deja una sola línea de log, al nivel de aviso, con:

- método y ruta;
- código HTTP, o «sin respuesta»;
- duración;
- tipo de error;
- número de intento;
- la pista técnica para el desarrollador, por ejemplo revisar `EXPO_PUBLIC_API_URL`.

Nunca se registran los cuerpos de las peticiones ni de las respuestas, el título ni la descripción de una incidencia, la posición del ciudadano, tokens ni cabeceras de autenticación.

Los logs del cliente solo se ven en la consola de desarrollo (la de `npx expo start` o la del navegador) y no se envían a ningún servidor.

Si falla la descarga de una foto en la app móvil, se registra un aviso con el error nativo resumido y la URL eliminada; el detalle ofrece volver a intentarlo.
