# Guía de desarrollo

Cómo preparar el entorno, ejecutar el proyecto y probarlo en local. El flujo de trabajo con Git y los roles están en [CONTRIBUTING.md](../CONTRIBUTING.md).

## Requisitos

- Git
- Docker y Docker Compose
- Java 21 (solo para ejecutar el backend fuera de Docker; el proyecto incluye Maven Wrapper)
- Node.js <!-- TODO: versión --> y npm
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
| <!-- TODO --> | <!-- TODO --> | <!-- TODO --> |

El fichero `.env` no se versiona.

## Base de datos y backend

<!-- TODO: comprobar cuando existan docker-compose.yml (#11) y el backend (#9). -->

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

- API: `http://localhost:<!-- TODO: puerto -->/api`
- Swagger UI: `http://localhost:<!-- TODO: puerto -->/swagger-ui.html`

El esquema se crea automáticamente con Flyway al arrancar el backend. Las migraciones están en `src/backend/src/main/resources/db/migration/`.

## Aplicación móvil

<!-- TODO: comprobar cuando exista la app (#10). -->

```bash
cd src/frontend
npm install
npx expo start
```

Escanea el código QR con Expo Go. El móvil y el portátil deben estar en la misma red.

El móvil no puede acceder al `localhost` del portátil, así que `EXPO_PUBLIC_API_URL` debe apuntar a la IP del portátil en la red local, por ejemplo `http://192.168.1.20:8080`.

<!-- TODO: cómo obtener la IP en Linux/macOS/Windows y dónde se define la variable. -->

La versión web de Expo no funciona porque `react-native-maps` no la soporta.

## Pruebas

```bash
cd src/backend
./mvnw test      # pruebas
./mvnw verify    # compilación, pruebas y comprobaciones configuradas
```

<!-- TODO: explicar las pruebas de integración con Testcontainers (necesitan Docker en marcha) y cómo se prueba la app manualmente. -->

## Problemas frecuentes

<!-- TODO: ir añadiendo los problemas que surjan (puerto ocupado, la app no conecta con el backend, etc.). -->
