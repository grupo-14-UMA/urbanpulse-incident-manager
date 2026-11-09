# UrbanPulse

Plataforma cloud para comunicar, gestionar y analizar incidencias urbanas en Málaga, enriquecidas con datos de contexto de la ciudad. El ciudadano y el técnico usan una aplicación móvil; el operador, el administrador y el analista, un panel de gestión web.

## Características principales

- [x] Panel web inicial con navegación y mapa de OpenStreetMap centrado en Málaga, con incidencias de muestra.
- [x] Mapa de Málaga con las incidencias registradas.
- [x] Detalle de una incidencia: título, descripción, categoría, estado y fecha.
- [x] Registro de incidencias con ubicación (GPS o seleccionada en el mapa) y categoría opcional.
- [ ] Documentación interactiva de la API (Swagger UI).

## Tecnologías

| Tecnología | Uso | Decisión |
| --- | --- | --- |
| Java 21 + Spring Boot 4 | Backend y API REST | [ADR-0013](docs/adr/0013-spring-boot-4-para-backend.md) |
| PostgreSQL + PostGIS | Persistencia relacional y geoespacial | [ADR-0001](docs/adr/0001-postgresql-como-base-de-datos.md), [ADR-0007](docs/adr/0007-postgis-para-datos-geoespaciales.md) |
| Hibernate Spatial | Mapeo JPA de geometrías PostGIS | [ADR-0007](docs/adr/0007-postgis-para-datos-geoespaciales.md) |
| Flyway | Migraciones del esquema de base de datos | [ADR-0013](docs/adr/0013-spring-boot-4-para-backend.md) |
| springdoc-openapi | Documentación de la API y Swagger UI | [#9](https://github.com/grupo-14-UMA/urbanpulse-incident-manager/issues/9) |
| Testcontainers | Pruebas del backend contra PostgreSQL con PostGIS | [#9](https://github.com/grupo-14-UMA/urbanpulse-incident-manager/issues/9) |
| React Native + Expo | Aplicación móvil | [ADR-0003](docs/adr/0003-react-native-con-expo-para-frontend.md) |
| React + Vite + Leaflet | Panel de gestión web | [ADR-0014](docs/adr/0014-aplicacion-movil-y-panel-de-gestion-web.md), [ADR-0015](docs/adr/0015-react-vite-y-leaflet-para-panel-de-gestion.md) |
| react-native-maps + OpenStreetMap | Mapa de incidencias en la aplicación móvil | [ADR-0004](docs/adr/0004-openstreetmap-como-fuente-de-mapas.md), [ADR-0005](docs/adr/0005-react-native-maps-como-renderizador.md) |
| Docker Compose | Entorno local | [ADR-0012](docs/adr/0012-docker-compose-para-desarrollo-local.md) |

## Puesta en marcha rápida

```bash
cp .env.example .env
docker compose up -d          # base de datos + backend
cd src/frontend && npx expo start
```

La guía completa está en [docs/development.md](docs/development.md).
El panel web se ejecuta aparte desde `src/management-panel` con `npm ci` y `npm run dev`.

## Estructura del repositorio

```text
src/backend/    Backend Spring Boot (sus pruebas están en src/backend/src/test/)
src/frontend/   Aplicación móvil React Native (Expo)
src/management-panel/ Panel web React (Vite + Leaflet)
docs/           Documentación: arquitectura, desarrollo, despliegue, ADRs, requisitos
```

## Documentación

- [Arquitectura](docs/architecture.md)
- [Guía de desarrollo](docs/development.md)
- [Gestión de errores](docs/error-handling.md)
- [Guía de despliegue](docs/deployment.md)
- [Decisiones de arquitectura (ADRs)](docs/adr/)
- [Requisitos](docs/requirements.md)
- [Glosario](GLOSSARY.md)
- [Cómo contribuir](CONTRIBUTING.md)

## Equipo

| Miembro | Rol | Área de desarrollo |
| --- | --- | --- |
| Alfonso ([@alraro](https://github.com/alraro)) | Team Leader y Product Owner | Core & Security Engineer |
| Andrea ([@apr2004](https://github.com/apr2004)) | Reviewer | Data & Performance Engineer |
| María ([@mmartinmaria](https://github.com/mmartinmaria)) | Tester | Mobile Engineer |
| Daniel ([@daniiroblees](https://github.com/daniiroblees)) | Maintainer | Ops Panel Engineer |

Proyecto de la asignatura: Desarrollo Software en Plataforma en la Nube, Universidad de Málaga.
