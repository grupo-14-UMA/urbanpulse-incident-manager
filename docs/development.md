# Guía de desarrollo

Cómo preparar el entorno, ejecutar el proyecto y probarlo en local. El flujo de trabajo con Git y los roles están en [CONTRIBUTING.md](../CONTRIBUTING.md). Cómo tratan los errores los clientes y la API está en [error-handling.md](error-handling.md).

## Requisitos

- Git
- Docker y Docker Compose
- Java 21 (solo para ejecutar el backend fuera de Docker; el proyecto incluye Maven Wrapper)
- Node.js 20.19+, 22.12+ o 24 LTS, y npm
- La aplicación Expo Go en un móvil
- Para las skills de agente de `.claude/skills/`: GitHub CLI (`gh`) 2.94+ y `jq`; `find-gaps` necesita además Python 3 con PyYAML (`pip install pyyaml` o el paquete `python3-yaml`)

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
| `PHOTOS_DIR` | Carpeta local donde el backend guarda las fotografías cuando se ejecuta fuera de Docker | `./data/photos` |
| `MANAGEMENT_PANEL_ORIGIN` | Origen permitido por CORS; en Compose sigue el puerto del panel, y para Vite se establece a `http://localhost:5173` | Derivado de `MANAGEMENT_PANEL_PORT` |
| `MANAGEMENT_PANEL_PORT` | Puerto publicado para el panel en Docker Compose | `8081` |
| `JWT_SECRET` | Secreto que firma los tokens de acceso, de al menos 32 bytes. Obligatorio | Valor solo para desarrollo en `.env.example` y Docker Compose |

El fichero `.env` no se versiona. `./mvnw spring-boot:run` no lo lee: fuera de Docker, el backend usa estos valores por defecto o las variables de entorno de la shell. `JWT_SECRET` no tiene valor por defecto en la aplicación: sin él el backend no arranca. Un despliegue real usa un secreto propio y aleatorio, por ejemplo el resultado de `openssl rand -base64 48`.

## Base de datos y backend

```bash
docker compose up -d --build # arranca la base de datos, el backend y el panel web
docker compose ps         # estado de los servicios
docker compose logs -f    # logs
docker compose down       # detener (los datos se conservan en el volumen)
```

El panel queda disponible en `http://localhost:8081` (o en el puerto indicado por `MANAGEMENT_PANEL_PORT`). Nginx sirve la aplicación y reenvía las peticiones `/api/` al backend dentro de Compose. Los logs de acceso se consultan con `docker compose logs -f panel`.

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

Flyway aplica al arrancar el backend las migraciones de `src/backend/src/main/resources/db/migration/`. La migración inicial activa PostGIS, crea las tablas `Incident` y `Category` y carga estas categorías: alumbrado, limpieza, mobiliario urbano, tráfico y semáforos, arbolado y otros. Las siguientes crean `User` y `Role`, cargan los roles `CITIZEN`, `OPERATOR` y `TECHNICIAN` y añaden a `Incident` el ciudadano que la registró; pasan las fechas a `timestamptz`; crean `Attachment` para los metadatos de las fotografías; y crean la tabla `Status` con los seis estados del ciclo de vida y la tabla `StatusChange` con el historial (con su entrada inicial para las incidencias que ya existían), además de añadir a `Incident` la prioridad, su justificación, el técnico asignado y `version` para el bloqueo optimista; `status_id` referencia `Status`. Las migraciones posteriores añaden los índices que respaldan las consultas por fecha, estado, categoría, reporter, assignee y prioridad. `priority_rank` y `priority_rank_desc` son columnas generadas y almacenadas para ordenar prioridades con y sin prioridad asignada, y mantener las no asignadas al final en ambos sentidos. La migración que añade estas columnas reescribe `Incident` y está separada de la creación de índices; consulta la ventana de mantenimiento en [deployment.md](deployment.md). La ubicación se almacena como `geometry(Point, 4326)`. Las fechas `created_at` y `updated_at` son `timestamptz` con valor por defecto `now()`, así que también las filas insertadas directamente en la base de datos quedan en un instante inequívoco. Las fotografías subidas se guardan en el volumen `incident_photos` de Docker Compose (o en `PHOTOS_DIR` fuera de Docker) detrás de la interfaz `PhotoStorage`, mientras la tabla `Attachment` conserva sus metadatos y referencias.

La API REST permite registrarse e iniciar sesión, crear y consultar incidencias, listar los trabajos abiertos asignados al técnico autenticado, validarlas, rechazarlas, asignarlas, priorizarlas, empezarlas y resolverlas, leer su historial de estados, adjuntar y descargar fotografías de una incidencia (`POST /api/incidents/{id}/photos` y `GET /api/incidents/{id}/photos/{photoId}`), consultar el mapa del área visible (`GET /api/incidents/map`), listar categorías y listar técnicos. Las rutas, quién puede usar cada una y el formato de la respuesta de autenticación están en la [arquitectura](architecture.md#api). `GET /api/incidents` devuelve páginas de hasta 100 incidencias (20 por defecto), filtradas y ordenadas en el servidor, con `hasMore` y `nextCursor` para continuar mediante comparaciones por tuplas, sin `OFFSET`. Admite filtros de estado, categoría (también `uncategorized=true`), prioridad y rango de fechas, y orden por fecha o prioridad; las incidencias sin prioridad aparecen al final en ambos sentidos. Las fechas son instantes ISO-8601; `createdAfter` incluye el límite y `createdBefore` lo excluye. Los parámetros `west`, `south`, `east` y `north` solo son válidos en `/api/incidents/map`; enviados a la lista producen HTTP 400. El mapa requiere los cuatro límites WGS 84; devuelve como máximo 500 incidencias abiertas (`REPORTED`, `VALIDATED`, `ASSIGNED` o `IN_PROGRESS`) y marca `truncated: true` cuando hay más. Ambos endpoints limitan los resultados por rol: operadores ven todas las incidencias, los ciudadanos solo las propias (incluidas las rechazadas en el listado) y los técnicos solo las asignadas. El endpoint de mapa conserva `ST_Intersects` y el índice GiST. Cada incidencia admite hasta 3 fotografías en formato JPEG, PNG o WebP de hasta 5 MB cada una. Los datos inválidos producen HTTP 400 con un mensaje en español; una incidencia inexistente produce HTTP 404; una acción de otro rol o sobre una incidencia ajena, HTTP 403; una acción que el estado no admite o un cambio simultáneo, HTTP 409. Cada cambio de estado y de prioridad se registra en el log con la incidencia, la transición y el rol y el identificador de quien la hizo. Las operaciones se documentan en Swagger UI.

### Cargar incidencias sintéticas

Para explorar las consultas del backend y el panel con muchos datos, primero arranca Docker Compose para que PostgreSQL tenga aplicadas las migraciones y las cuentas locales de desarrollo:

```bash
docker compose up -d db backend
node scripts/generate-synthetic-incidents.mjs 1000000
```

El número se puede cambiar entre 1 y 5.000.000 por ejecución. El comando muestra cuánto tardó la carga. Inserta incidencias en barrios de Málaga, categorías opcionales, fechas, prioridades, cuentas ciudadanas sintéticas, técnicos asignados y el historial coherente de transiciones. No genera fotografías. Puedes iniciar sesión como el primer ciudadano sintético con `ciudadano.sintetico.000001@urbanpulse.test` y la contraseña `urbanpulse-dev` para consultar sus incidencias desde la aplicación móvil. Las cuentas sintéticas usan el mismo hash de contraseña que las cuentas locales de desarrollo.

La operación es explícita y acumulativa: no se ejecuta al arrancar la aplicación ni borra datos existentes; repetirla añade otro lote. La generación corre en PostgreSQL con SQL por lotes para evitar crear entidades JPA una por una ([ADR-0021](adr/0021-carga-sintetica-por-lotes-con-postgresql.md)).

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

Escanea el código QR con Expo Go (en iPhone, con la cámara). El móvil y el portátil deben estar en la misma red. Tras iniciar sesión, la app abre un mapa de OpenStreetMap (teselas de `tile.openstreetmap.de`) centrado en Málaga y solicita solo tus incidencias del área visible. Al terminar un movimiento o cambio de zoom, espera brevemente y consulta el nuevo rectángulo; si hay más de 500 resultados, indica que acerques el mapa. Al tocar un marcador aparece un resumen y al tocarlo se abre el detalle, que descarga cada fotografía con la sesión autenticada, la guarda temporalmente en la caché del teléfono y la muestra con el componente `Image` nativo de React Native; si falla la descarga o la visualización, ofrece reintentar y elimina el archivo temporal al cerrar el detalle. Desde el detalle también puedes añadir fotos a una incidencia existente. El botón «Nueva incidencia» abre el formulario para registrar una con ubicación y hasta 3 fotografías desde la cámara o la galería. La app pide los permisos necesarios, muestra una vista previa, reduce las fotos a menos de 5 MB y permite quitarlas antes de enviar; también se puede registrar sin fotos.

La app consume el backend para cargar las incidencias propias y categorías, consultar detalles y registrar incidencias. Arranca Docker Compose antes de iniciar Expo y configura `EXPO_PUBLIC_API_URL` en `src/frontend/.env` con una dirección alcanzable desde el móvil. Las lecturas pasan por `src/frontend/src/api.js`, que clasifica los errores, registra solo información técnica en consola y reintenta los fallos transitorios según `docs/error-handling.md`. La app reduce y convierte las fotos con `expo-image-manipulator`, comprueba que no superen 5 MB y las envía como archivos multipart con `File` de Expo FileSystem y `fetch` de `expo/fetch`; el cliente descarga las fotografías guardadas con la sesión autenticada, las copia temporalmente a la caché del teléfono y el componente `Image` nativo de React Native las muestra desde ese archivo local. Si una subida falla, conserva la selección para reintentar. La pantalla del mapa consulta solo el área visible, conserva los marcadores durante una recarga fallida y ofrece «Reintentar»; el detalle y las categorías del formulario comparten el mismo comportamiento. El formulario avisa si no puede obtener la localización y permite marcarla en el mapa.

Al abrirla, la aplicación muestra el inicio de sesión. Desde «Crear cuenta» se registra un ciudadano y se entra directamente al mapa. También se pueden usar las cuentas de desarrollo indicadas arriba: un técnico ve «Mis trabajos» paginados, abre sus incidencias abiertas asignadas, puede empezar el trabajo y resolverlo con una nota y fotografías opcionales; un operador recibe el aviso de que debe usar el panel de gestión desde un ordenador. El botón «Cerrar sesión» borra la sesión del teléfono.

El token y los datos básicos del usuario se guardan con el almacenamiento seguro del dispositivo. Cada reapertura intenta renovarlo con `POST /api/auth/refresh`; si ya no es válido, se borra y se muestra «Tu sesión ha caducado. Vuelve a iniciar sesión.». La aplicación nunca escribe tokens, contraseñas ni datos personales en la consola. Para ciudadanos, el backend limita el mapa, el detalle y la descarga de fotos a las incidencias que hayan registrado. El mapa del técnico muestra únicamente las incidencias abiertas que tiene asignadas; el detalle y la descarga de fotos siguen las reglas de acceso correspondientes.

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

Para usar el panel ya compilado en Docker, arranca el entorno con `docker compose up -d --build` y abre `http://localhost:8081`. En este modo el navegador llama a `/api` en el mismo origen; Nginx reenvía esas solicitudes al backend.

Para desarrollar el panel con Vite, ejecuta:

```bash
cd src/management-panel
npm ci
cp .env.example .env
npm run dev
```

En Windows PowerShell, usa `Copy-Item .env.example .env` en lugar de `cp`. Vite muestra la dirección local al arrancar (por defecto `http://localhost:5173`). El panel pide iniciar sesión antes de mostrar sus pantallas. Usa la cuenta de Operador de desarrollo de la tabla anterior (`operador@urbanpulse.test` / `urbanpulse-dev`). La página inicial muestra el mapa de OpenStreetMap centrado en Málaga, con marcadores agrupados y datos cargados desde la API para el área visible. La consulta se repite al terminar un movimiento o cambio de zoom; si la API limita la respuesta a 500, el mapa indica que se acerque.

La ruta «Incidencias» consulta el listado del backend y solicita seis filas por cursor. Los filtros de estado, categoría (incluidas las incidencias sin categoría), prioridad y fechas y la ordenación por fecha o prioridad se calculan en el servidor; el panel muestra si hay una página siguiente sin solicitar el recuento completo de la tabla. También aparecen incidencias rechazadas y resueltas. El mapa permite filtrar por categoría y conserva la consulta acotada al área visible. El detalle muestra los datos del backend, la ubicación, hasta tres fotos adjuntas y los cinco cambios de estado más recientes. Según el estado, el operador puede validar o rechazar incidencias registradas, asignar incidencias validadas a un técnico y cambiar la prioridad de incidencias en gestión. El motivo del rechazo es obligatorio; ante un HTTP 409, el panel muestra el mensaje de la API cuando está disponible y recomienda recargar la incidencia.

En Docker Compose, el navegador llama a `/api` en el mismo origen y Nginx reenvía esas solicitudes al backend. Con Vite, `VITE_API_URL` en `src/management-panel/.env` configura la URL base del backend y apunta por defecto a `http://localhost:8080`. El backend permite peticiones del origen indicado por `MANAGEMENT_PANEL_ORIGIN` solo en `/api/**`: para Vite, establece `MANAGEMENT_PANEL_ORIGIN=http://localhost:5173` en el `.env` de la raíz y recrea el backend con `docker compose up -d --force-recreate backend`; para Docker Compose, el origen predeterminado es `http://localhost:8081`.

### Lint

```bash
cd src/frontend
npx expo lint
```

### Pruebas de la aplicación móvil

El cliente móvil usa Jest con `jest-expo`. Las pruebas del cliente HTTP y del flujo de selección/subida de fotos están en `src/frontend/src/api.test.js` y `src/frontend/src/incidentPhotos.test.js`; simulan `fetch` y el selector de imágenes, por lo que no necesitan el backend ni un dispositivo.

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

La comprobación de planes del listado es optativa porque inserta un millón de incidencias sintéticas y crea índices; `./mvnw verify` la omite por defecto. Desde `src/backend`, ejecútala con:

```bash
./mvnw -Durbanpulse.performance-tests=true -Dtest=IncidentListQueryPlanTests test
```

La prueba informa de los tiempos del SELECT del endpoint con índices ausentes y presentes, valida el plan en lugar de depender de una comparación de relojes y ejecuta `EXPLAIN (ANALYZE, BUFFERS)` sobre cursores por fecha y prioridad y filtros por estado, categoría, ciudadano y técnico. La prueba normal del backend cubre permisos por rol, filtros, cursores, orden y filtros del endpoint de mapa. La decisión está documentada en el [ADR-0020](adr/0020-paginacion-filtros-e-indices-de-incidencias.md).
