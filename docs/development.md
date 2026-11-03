# Guía de desarrollo

Cómo preparar el entorno, ejecutar el proyecto y probarlo en local. El flujo de trabajo con Git y los roles están en [CONTRIBUTING.md](../CONTRIBUTING.md).

## Requisitos

- Git
- Docker y Docker Compose
- Java 21 (solo para ejecutar el backend fuera de Docker; el proyecto incluye Maven Wrapper)
- Node.js 20 LTS o posterior, y npm
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

El fichero `.env` no se versiona. `./mvnw spring-boot:run` no lo lee: fuera de Docker, el backend usa estos valores por defecto o las variables de entorno de la shell.

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
./mvnw spring-boot:run
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Documento OpenAPI: `http://localhost:8080/v3/api-docs`

Flyway aplica al arrancar el backend las migraciones de `src/backend/src/main/resources/db/migration/`.

## Aplicación móvil

La app es un proyecto Expo (JavaScript, expo-router) en `src/frontend`. Se abre en el móvil con Expo Go, sin compilar código nativo.

```bash
cd src/frontend
npm install
cp .env.example .env    # y edita la IP, ver más abajo
npx expo start
```

Escanea el código QR con Expo Go (en iPhone, con la cámara). El móvil y el portátil deben estar en la misma red. La app abre un mapa de OpenStreetMap (teselas de `tile.openstreetmap.de`) centrado en Málaga.

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

### Lint

```bash
cd src/frontend
npx expo lint
```

La versión web de Expo no funciona porque `react-native-maps` no la soporta.

## Pruebas

```bash
cd src/backend
./mvnw test      # pruebas
./mvnw verify    # compilación, pruebas y comprobaciones configuradas
```

Las pruebas arrancan la aplicación contra un contenedor `postgis/postgis` con Testcontainers, así que solo necesitan Docker en marcha, no una base de datos local.
