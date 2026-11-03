# UrbanPulse

Plataforma cloud para comunicar, gestionar y analizar incidencias urbanas en Málaga, enriquecidas con datos de contexto de la ciudad.

## Características principales

- [ ] Mapa de Málaga con las incidencias registradas.
- [ ] Detalle de una incidencia: título, descripción, categoría, estado y fecha.
- [ ] Registro de incidencias con ubicación (GPS o seleccionada en el mapa) y categoría opcional.
- [ ] Documentación interactiva de la API (Swagger UI).

## Tecnologías

| Tecnología | Uso | Decisión |
| --- | --- | --- |
| Java 21 + Spring Boot 4 | Backend y API REST | [ADR-0013](docs/adr/0013-spring-boot-4-para-backend.md) |
| PostgreSQL | Base de datos | [ADR-0001](docs/adr/0001-postgresql-como-base-de-datos.md) |
| Flyway | Migraciones del esquema de base de datos | [ADR-0013](docs/adr/0013-spring-boot-4-para-backend.md) |
| springdoc-openapi | Documentación de la API y Swagger UI | [#9](https://github.com/grupo-14-UMA/urbanpulse-incident-manager/issues/9) |
| Testcontainers | Pruebas del backend contra PostgreSQL con PostGIS | [#9](https://github.com/grupo-14-UMA/urbanpulse-incident-manager/issues/9) |
| React Native + Expo | Aplicación móvil | [ADR-0003](docs/adr/0003-react-native-con-expo-para-frontend.md) |
| react-native-maps + OpenStreetMap | Mapa de incidencias | [ADR-0004](docs/adr/0004-openstreetmap-como-fuente-de-mapas.md), [ADR-0005](docs/adr/0005-react-native-maps-como-renderizador.md) |
| Docker Compose | Entorno local | [ADR-0012](docs/adr/0012-docker-compose-para-desarrollo-local.md) |

## Puesta en marcha rápida

```bash
cp .env.example .env
docker compose up -d          # base de datos + backend
cd src/frontend && npx expo start
```

La guía completa está en [docs/development.md](docs/development.md).

## Estructura del repositorio

```text
src/backend/    Backend Spring Boot (sus pruebas están en src/backend/src/test/)
src/frontend/   Aplicación React Native (Expo)
docs/           Documentación: arquitectura, desarrollo, despliegue, ADRs, requisitos
```

## Documentación

- [Arquitectura](docs/architecture.md)
- [Guía de desarrollo](docs/development.md)
- [Guía de despliegue](docs/deployment.md)
- [Decisiones de arquitectura (ADRs)](docs/adr/)
- [Requisitos](docs/requirements.md)
- [Glosario](GLOSSARY.md)
- [Cómo contribuir](CONTRIBUTING.md)

## Equipo

| Miembro | Rol |
| --- | --- |
| Alfonso ([@alraro](https://github.com/alraro)) | Team Leader y Product Owner |
| Andrea ([@apr2004](https://github.com/apr2004)) | Reviewer |
| María ([@mmartinmaria](https://github.com/mmartinmaria)) | Tester |
| Daniel ([@daniiroblees](https://github.com/daniiroblees)) | Maintainer |

Proyecto de la asignatura: Desarrollo Software en Plataforma en la Nube, Universidad de Málaga.
