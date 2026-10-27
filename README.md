# UrbanPulse

Plataforma cloud para comunicar, gestionar y analizar incidencias urbanas en Málaga, enriquecidas con datos de contexto de la ciudad.

<!-- TODO: 2-3 frases más: qué problema resuelve, para quién (ciudadanía, operadores, técnicos...) y qué aporta frente a un simple formulario de avisos. -->

## Características principales

<!-- TODO: marcar con [x] lo que funcione en cada entrega. -->

- [ ] Mapa de Málaga con las incidencias registradas.
- [ ] Detalle de una incidencia: título, descripción, categoría, estado y fecha.
- [ ] Registro de incidencias con ubicación (GPS o seleccionada en el mapa) y categoría opcional.
- [ ] Documentación interactiva de la API (Swagger UI).
<!-- TODO: añadir características de próximas entregas (autenticación, validación por operadores, datos abiertos, IA...). -->

## Tecnologías

| Tecnología | Uso | Decisión |
| --- | --- | --- |
| Java 21 + Spring Boot 3 | Backend y API REST | [ADR-0002](docs/adr/0002-spring-boot-para-backend.md) |
| PostgreSQL | Base de datos | [ADR-0001](docs/adr/0001-postgresql-como-base-de-datos.md) |
| Flyway | Migraciones del esquema de base de datos | [ADR-0002](docs/adr/0002-spring-boot-para-backend.md) |
| React Native + Expo | Aplicación móvil | [ADR-0003](docs/adr/0003-react-native-con-expo-para-frontend.md) |
| react-native-maps + OpenStreetMap | Mapa de incidencias | [ADR-0004](docs/adr/0004-openstreetmap-como-fuente-de-mapas.md), [ADR-0005](docs/adr/0005-react-native-maps-como-renderizador.md) |
| Docker Compose | Entorno local | [ADR-0012](docs/adr/0012-docker-compose-para-desarrollo-local.md) |
<!-- TODO: añadir o quitar filas según lo que se use realmente (springdoc, Testcontainers, GitHub Actions, AWS...). -->

## Puesta en marcha rápida

<!-- TODO: comprobar estos comandos cuando existan docker-compose.yml y la app. -->

```bash
cp .env.example .env
docker compose up -d          # base de datos + backend
cd src/frontend && npx expo start
```

La guía completa está en [docs/development.md](docs/development.md).

## Estructura del repositorio

```text
src/backend/    Backend Spring Boot
src/frontend/   Aplicación React Native (Expo)
docs/           Documentación: arquitectura, desarrollo, despliegue, ADRs, requisitos
tests/          Pruebas
```
<!-- TODO: actualizar si cambia la estructura. -->

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

Proyecto de la asignatura <!-- TODO: nombre de la asignatura -->, Universidad de Málaga.
