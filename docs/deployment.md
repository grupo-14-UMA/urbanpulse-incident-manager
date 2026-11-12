# Guía de despliegue

Cómo construir y desplegar UrbanPulse. El entorno de desarrollo está en [development.md](development.md).

Por ahora UrbanPulse solo se despliega en local con Docker Compose. Esta guía se ampliará cuando se implementen el despliegue en la nube y la distribución de la aplicación móvil.

## Requisitos

- Docker y Docker Compose

## Versionado

Cada entrega se publica con una etiqueta y una release de GitHub siguiendo [SemVer](https://semver.org/lang/es/): `v0.1.0` para la Entrega 1, `v0.2.0` para la siguiente, etc.

La release la crea el Maintainer cuando todos los PR de la entrega están fusionados en `main`:

```bash
gh release create v0.1.0 --target main --title "v0.1.0 - Entrega 1" --generate-notes
```

El comando crea la etiqueta sobre `main` y genera las notas a partir de los PR fusionados.

## Construir el backend

```bash
git switch main
git pull
cd src/backend
./mvnw verify
```

## Despliegue local con Docker Compose

```bash
cp .env.example .env
docker compose up -d --build
docker compose ps
```

Compose construye la imagen del backend y la del panel, y arranca PostgreSQL con PostGIS (`postgis/postgis`). El backend espera a que la base de datos esté lista antes de arrancar. El panel se compila en una etapa Node y se sirve con Nginx; el navegador lo abre en `http://localhost:8081`. Nginx sirve las rutas de React y reenvía `/api/` al backend dentro de la red de Compose.

El panel también puede ejecutarse en modo desarrollo con Vite en `http://localhost:5173`. Para ese modo, establece `MANAGEMENT_PANEL_ORIGIN=http://localhost:5173` en `.env` y recrea el backend; el valor predeterminado autoriza el origen del panel servido por Compose (`http://localhost:8081`).

Para detenerlo:

```bash
docker compose down       # los datos se conservan en el volumen
docker compose down -v    # borra también los datos
```

## Configuración

Compose lee las variables del fichero `.env` de la raíz:

| Variable | Descripción | Valor por defecto |
| --- | --- | --- |
| `POSTGRES_DB` | Nombre de la base de datos | `urbanpulse` |
| `POSTGRES_USER` | Usuario de la base de datos | `urbanpulse` |
| `POSTGRES_PASSWORD` | Contraseña de la base de datos | `urbanpulse` |
| `DB_PORT` | Puerto del equipo en el que se publica PostgreSQL | `5432` |
| `SERVER_PORT` | Puerto del equipo en el que se publica el backend | `8080` |
| `MANAGEMENT_PANEL_PORT` | Puerto del equipo en el que se publica el panel | `8081` |
| `MANAGEMENT_PANEL_ORIGIN` | Origen permitido por CORS para el panel; por defecto sigue `MANAGEMENT_PANEL_PORT` | `http://localhost:8081` |

Dentro de Compose el backend siempre se conecta a `db:5432` y escucha en el puerto `8080`; `DB_HOST` solo se usa al ejecutar el backend fuera de Docker.

El fichero `.env` no se versiona. Los valores por defecto solo sirven para el entorno local.

## Comprobaciones tras el despliegue

- [ ] `docker compose ps` muestra `db` como `healthy`, `backend` y `panel` en marcha.
- [ ] El panel carga en `http://localhost:8081` y puede iniciar sesión y consultar la API.
- [ ] Swagger UI carga en `http://localhost:8080/swagger-ui.html`.
- [ ] `docker compose logs panel` muestra los logs de acceso de Nginx.
- [ ] No hay errores en `docker compose logs backend`.

## Integración continua

Los cuatro workflows de GitHub Actions se ejecutan en cada pull request dirigido a `main`:

- `.github/workflows/backend-ci.yml` prepara Java 21 y lanza `./mvnw verify` en `src/backend`, que compila el backend y ejecuta sus pruebas contra PostgreSQL con PostGIS mediante Testcontainers.
- `.github/workflows/management-panel-ci.yml` instala las dependencias del panel con `npm ci` y ejecuta `npm run build`.
- `.github/workflows/mobile-app-ci.yml` instala las dependencias de la aplicación móvil con `npm ci` y ejecuta `npm run lint` y `npm test -- --runInBand`.
- `.github/workflows/review-policy.yml` comprueba que el pull request tenga las aprobaciones requeridas según su autor y publica el estado `review-policy`.

El despliegue no está automatizado.
