# Guía de desarrollo

Cómo preparar el entorno, ejecutar el proyecto y probarlo en local. El flujo de trabajo con Git y los roles están en [CONTRIBUTING.md](../CONTRIBUTING.md). Cómo tratan los errores los clientes y la API está en [error-handling.md](error-handling.md).

## Requisitos

- Git
- Docker y Docker Compose
- Java 21 (solo para ejecutar el backend fuera de Docker; el proyecto incluye Maven Wrapper)
- Node.js 20.19+, 22.12+ o 24 LTS, y npm
- La aplicación Expo Go en un móvil

No hace falta instalar PostgreSQL: se ejecuta con Docker Compose.

## Preparar el entorno

```bash
git clone https://github.com/grupo-14-UMA/urbanpulse-incident-manager.git
cd urbanpulse-incident-manager
cp .env.example .env
```

Variables de `.env`:

| Variable | Descripción | Valor por defecto |
| --- | --- | --- |
| `DB_HOST` | Host de la base de datos para el backend | `localhost` |
| `DB_PORT` | Puerto de la base de datos para el backend | `5432` |
| `POSTGRES_DB` | Nombre de la base de datos (compartida con la imagen de Postgres) | `urbanpulse` |
| `POSTGRES_USER` | Usuario de la base de datos (compartido con la imagen de Postgres) | `urbanpulse` |
| `POSTGRES_PASSWORD` | Contraseña de la base de datos (compartida con la imagen de Postgres) | `urbanpulse` |
| `SERVER_PORT` | Puerto HTTP del backend | `8080` |
| `MANAGEMENT_PANEL_ORIGIN` | Origen web permitido por CORS para el panel | `http://localhost:5173` |
| `PHOTOS_DIR` | Carpeta local donde el backend guarda las fotografías cuando se ejecuta fuera de Docker | `./data/photos` |
| `JWT_SECRET` | Secreto que firma los tokens de acceso, de al menos 32 bytes. Obligatorio | Valor solo para desarrollo en `.env.example` y Docker Compose |

El fichero `.env` no se versiona. `./mvnw spring-boot:run` no lo lee: fuera de Docker, el backend usa estos valores por defecto o las variables de entorno de la shell. `JWT_SECRET` no tiene valor por defecto en la aplicación: sin él el backend no arranca. Un despliegue real usa un secreto propio y aleatorio, por ejemplo el resultado de `openssl rand -base64 48`.

## Base de datos y backend

```bash
docker compose up -d      # arranca la base de datos y el backend
docker compose ps         # estado de los servicios
docker compose logs -f    # logs
docker compose down       # detener (los datos se conservan en el volumen)
```

Para ejecutar el backend fuera de Docker con la base de datos en Docker:

```bash
docker compose up -d db
cd src/backend
export JWT_SECRET=dev-only-jwt-secret-change-me-in-any-deployment
export SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/dev-seed   # cuentas de desarrollo
./mvnw spring-boot:run
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Documento OpenAPI: `http://localhost:8080/v3/api-docs`

Flyway aplica al arrancar el backend las migraciones de `src/backend/src/main/resources/db/migration/`. La migración inicial activa PostGIS, crea las tablas `Incident` y `Category` y carga estas categorías: alumbrado, limpieza, mobiliario urbano, tráfico y semáforos, arbolado y otros. Las siguientes crean `User`, `Role` y `Attachment`, cargan los roles `CITIZEN`, `OPERATOR` y `TECHNICIAN` y añaden a `Incident` el ciudadano que la registró. La ubicación se almacena como `geometry(Point, 4326)`; `status_id = 1` representa el estado inicial `REPORTED` hasta que se añada la tabla `Status`. Las fechas `created_at` y `updated_at` son `timestamptz` con valor por defecto `now()`, así que también las filas insertadas directamente en la base de datos quedan en un instante inequívoco. Las fotografías subidas se guardan en el volumen `incident_photos` de Docker Compose (o en `PHOTOS_DIR` fuera de Docker) detrás de la interfaz `PhotoStorage`, mientras la tabla `Attachment` conserva sus metadatos y referencias.

La API REST permite registrarse e iniciar sesión, crear y consultar incidencias, adjuntar y descargar fotografías de una incidencia (`POST /api/incidents/{id}/photos` y `GET /api/incidents/{id}/photos/{photoId}`), cargar las incidencias del área visible del mapa, listar categorías y listar técnicos. Las rutas, quién puede usar cada una y el formato de la respuesta de autenticación están en la [arquitectura](architecture.md#api). La consulta del mapa requiere `west`, `south`, `east` y `north` en WGS 84; devuelve como máximo 500 incidencias `REPORTED` y `truncated: true` cuando hay más. Cada incidencia admite hasta 3 fotografías en formato JPEG, PNG o WebP de hasta 5 MB cada una. Los datos inválidos producen HTTP 400 con un mensaje en español; una incidencia o fotografía inexistente produce HTTP 404. Las operaciones se documentan en Swagger UI.

### Autenticación y cuentas de desarrollo

Todas las rutas de `/api/**` salvo el registro y el inicio de sesión necesitan el token en la cabecera `Authorization: Bearer <token>`. Sin token válido responden 401; si el rol no tiene permiso, 403. Un ciudadano crea su cuenta con `POST /api/auth/register`. En Swagger UI, inicia sesión con `POST /api/auth/login`, copia `token` y pégalo en «Authorize».

Docker Compose y las pruebas activan además la ubicación de Flyway `db/dev-seed`, que crea estas cuentas de personal. Todas tienen la contraseña `urbanpulse-dev`:

| Email | Nombre | Rol |
| --- | --- | --- |
| `operador@urbanpulse.test` | Olga Operadora | Operador |
| `tecnico1@urbanpulse.test` | Lucía Martín | Técnico |
| `tecnico2@urbanpulse.test` | Javier Ruiz | Técnico |

Un despliegue real no activa `db/dev-seed`, así que estas cuentas no existen en él; allí las cuentas de personal se crean a mano.

```bash
curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"operador@urbanpulse.test","password":"urbanpulse-dev"}'
```

## Aplicación móvil

La app es un proyecto Expo (JavaScript, expo-router) en `src/frontend`. Se abre en el móvil con Expo Go, sin compilar código nativo.

```bash
cd src/frontend
npm install
cp .env.example .env    # y edita la IP, ver más abajo
npx expo start
```

Escanea el código QR con Expo Go (en iPhone, con la cámara). El móvil y el portátil deben estar en la misma red. Tras iniciar sesión, la app abre un mapa de OpenStreetMap (teselas de `tile.openstreetmap.de`) centrado en Málaga y solicita solo las incidencias del área visible. Al terminar un movimiento o cambio de zoom, espera brevemente y consulta el nuevo rectángulo; si hay más de 500 resultados, indica que acerques el mapa. Al tocar un marcador aparece un resumen y al tocarlo se abre el detalle. El botón «Nueva incidencia» abre el formulario para registrar una; la primera vez pide permiso de ubicación.

La app consume el backend para cargar las incidencias y categorías, consultar el detalle y registrar incidencias. Arranca Docker Compose antes de iniciar Expo y configura `EXPO_PUBLIC_API_URL` en `src/frontend/.env` con una dirección alcanzable desde el móvil. Las lecturas pasan por `src/frontend/src/api.js`, que clasifica los errores, registra solo información técnica en consola y reintenta los fallos transitorios según `docs/error-handling.md`. La pantalla del mapa consulta solo el área visible, conserva los marcadores durante una recarga fallida y ofrece «Reintentar»; el detalle y las categorías del formulario comparten el mismo comportamiento. El formulario avisa si no puede obtener la localización y permite marcarla en el mapa.

Al abrirla, la aplicación muestra el inicio de sesión. Desde «Crear cuenta» se registra un ciudadano y se entra directamente al mapa. También se pueden usar las cuentas de desarrollo indicadas arriba: un técnico ve «Mis trabajos» (aún sin asignaciones) y un operador recibe el aviso de que debe usar el panel de gestión desde un ordenador. El botón «Cerrar sesión» borra la sesión del teléfono.

El token y los datos básicos del usuario se guardan con el almacenamiento seguro del dispositivo. Cada reapertura intenta renovarlo con `POST /api/auth/refresh`; si ya no es válido, se borra y se muestra «Tu sesión ha caducado. Vuelve a iniciar sesión.». La aplicación nunca escribe tokens, contraseñas ni datos personales en la consola. Las incidencias pertenecientes al ciudadano todavía no tienen una ruta de consulta en la API: el mapa muestra las incidencias disponibles para cualquier usuario autenticado, por lo que «Mis incidencias» queda pendiente de un endpoint futuro.

En redes que aíslan a los dispositivos entre sí, como eduroam, el móvil no llega al portátil y Expo Go muestra «Something went wrong». En ese caso arranca con túnel (la primera vez pide instalar `@expo/ngrok`) o conecta el portátil al punto de acceso del móvil:

```bash
npx expo start --tunnel
```

### URL del backend

La app lee la URL base del backend de la variable `EXPO_PUBLIC_API_URL`, definida en `src/frontend/.env`. Expo CLI solo carga los `.env` de la carpeta del proyecto Expo, así que este fichero es distinto del `.env` de la raíz (que usa Docker Compose). `src/frontend/.env` no se versiona; `src/frontend/.env.example` muestra el formato:

```bash
EXPO_PUBLIC_API_URL=http://192.168.1.20:8080
```

El móvil no puede acceder al `localhost` del portátil, así que la variable debe apuntar a la IP del portátil en la red local. Si falta, la app muestra un aviso en la consola de `npx expo start`. Tras cambiar `.env`, reinicia `npx expo start`.

Para obtener la IP local del portátil (suele empezar por `192.168.` o `10.`):

- **Linux**: `hostname -I` (la primera dirección) o `ip -4 addr show`.
- **macOS**: `ipconfig getifaddr en0` (Wi-Fi; prueba `en1` si no devuelve nada).
- **Windows**: `ipconfig` y busca «Dirección IPv4» (o «IPv4 Address») del adaptador Wi-Fi o Ethernet.

## Panel de gestión web

El panel es un proyecto independiente en `src/management-panel`, con React, Vite, React Router y Leaflet. Para abrirlo localmente:

```bash
cd src/management-panel
npm ci
cp .env.example .env
npm run dev
```

En Windows PowerShell, usa `Copy-Item .env.example .env` en lugar de `cp`. Vite muestra la dirección local al arrancar (por defecto `http://localhost:5173`). El panel pide iniciar sesión antes de mostrar sus pantallas. Usa la cuenta de Operador de desarrollo de la tabla anterior (`operador@urbanpulse.test` / `urbanpulse-dev`). La página inicial muestra el mapa de OpenStreetMap centrado en Málaga, con marcadores agrupados y datos cargados desde la API para el área visible. La consulta se repite al terminar un movimiento o cambio de zoom; si la API limita la respuesta a 500, el mapa indica que se acerque.

La ruta «Incidencias» presenta una tabla con filtros por estado, categoría, prioridad y fechas, ordenación por fecha o prioridad y seis filas por página. Usa los primeros 18 registros del conjunto de muestra compartido con el mapa, por lo que los enlaces al detalle muestran la misma incidencia. Los filtros y la paginación se calculan en el navegador y no representan todavía la consulta paginada del backend (#55). El detalle es de solo lectura; las acciones del Operador se implementarán en #73.

`VITE_API_URL` en `src/management-panel/.env` configura la URL base del backend y por defecto apunta a `http://localhost:8080`. El inicio de sesión usa `POST /api/auth/login` y solo permite el rol `OPERATOR`; las cuentas de Ciudadano y Técnico reciben un mensaje de falta de permiso. El token se guarda en el almacenamiento local del navegador para conservar la sesión al recargar. Al abrir el panel se renueva con `POST /api/auth/refresh`; si ha caducado, se vuelve al inicio de sesión. «Cerrar sesión» elimina el token guardado. Los errores de credenciales muestran el mensaje de la API sin redirigir.

El backend permite peticiones del origen indicado por `MANAGEMENT_PANEL_ORIGIN` solo en `/api/**`. El valor por defecto, `http://localhost:5173`, coincide con el servidor de desarrollo de Vite. Si cambias el puerto del panel, actualiza la variable en el `.env` de la raíz y recrea el backend:

```bash
docker compose up -d --force-recreate backend
```

El panel no forma parte de Docker Compose; se ejecuta con Vite durante el desarrollo.

### Lint

```bash
cd src/frontend
npx expo lint
```

### Pruebas de la aplicación móvil

El cliente móvil usa Jest con `jest-expo`. Las pruebas del cliente HTTP están en `src/frontend/src/api.test.js` y simulan `fetch`, por lo que no necesitan el backend ni un dispositivo.

```bash
cd src/frontend
npm test
```

Para ejecutar Jest continuamente mientras se editan las pruebas:

```bash
npm test -- --watch
```

La versión web de la aplicación móvil Expo no funciona porque `react-native-maps` no la soporta; el panel web es un proyecto Vite independiente.

## Pruebas

```bash
cd src/backend
./mvnw test      # pruebas
./mvnw verify    # compilación, pruebas y comprobaciones configuradas
```

Las pruebas arrancan la aplicación contra un contenedor `postgis/postgis` con Testcontainers, así que solo necesitan Docker en marcha, no una base de datos local. `src/backend/src/test/resources/config/application.yaml` les da su propio `JWT_SECRET` y activa las cuentas de desarrollo.
