# Arquitectura

Este documento describe la arquitectura de UrbanPulse siguiendo el [modelo C4](https://c4model.com/). Cada cambio relevante actualiza este documento y, si corresponde, un [ADR](adr/).

## Visión general

<!-- TODO: resumen en un párrafo: monolito Spring Boot (ADR-0006) + app móvil Expo + PostgreSQL, ejecutado en local con Docker Compose y pensado para desplegarse en AWS. -->

## Nivel 1: Contexto del sistema

<!-- TODO: diagrama de contexto (imagen en docs/diagrams/ o bloque Mermaid). -->

Actores:

- **Ciudadano**: registra incidencias y consulta su estado.
- **Operador**, **Técnico**, **Administrador**, **Analista**: <!-- TODO: incluir cuando entren en el alcance. -->

Sistemas externos:

| Sistema | Uso | Decisión |
| --- | --- | --- |
| OpenStreetMap | Teselas del mapa | [ADR-0004](adr/0004-openstreetmap-como-fuente-de-mapas.md) |
| Datos abiertos del Ayuntamiento de Málaga | Contexto urbano | [ADR-0008](adr/0008-datos-abiertos-del-ayuntamiento-de-malaga.md) |
| OpenWeatherMap | Meteorología | [ADR-0009](adr/0009-openweathermap-para-meteorologia.md) |
| Gemini / ChatGPT | Asistencia inteligente | [ADR-0010](adr/0010-gemini-y-chatgpt-como-ia-principal.md) |
<!-- TODO: indicar en qué entrega se integra cada uno. -->

## Nivel 2: Contenedores

<!-- TODO: diagrama de contenedores. -->

| Contenedor | Tecnología | Responsabilidad |
| --- | --- | --- |
| Aplicación móvil | React Native + Expo, react-native-maps | <!-- TODO --> |
| Backend (API REST) | Java 21, Spring Boot 3 | <!-- TODO --> |
| Base de datos | PostgreSQL (+ PostGIS más adelante) | <!-- TODO --> |

<!-- TODO: cómo se comunican (HTTP/JSON, puerto, CORS) y por qué la app usa EXPO_PUBLIC_API_URL. -->

## Nivel 3: Componentes del backend

<!-- TODO: paquetes/capas del backend (controladores, servicios, repositorios, entidades) cuando exista el código. -->

## API

<!-- TODO: comprobar contra la implementación y enlazar Swagger UI. -->

| Método | Ruta | Descripción |
| --- | --- | --- |
| `POST` | `/api/incidents` | Crea una incidencia |
| `GET` | `/api/incidents` | Lista las incidencias |
| `GET` | `/api/incidents/{id}` | Detalle de una incidencia |
| `GET` | `/api/categories` | Lista las categorías |

## Modelo de datos

<!-- TODO: enlazar el modelo completo de dbdiagram (issue #8) y explicar qué subconjunto se aplica en cada entrega. -->

## Atributos de calidad

<!-- TODO: atributos que guían el diseño (disponibilidad, escalabilidad, seguridad, degradación controlada si una fuente externa falla...) y cómo se abordan. -->

## Decisiones de arquitectura

Las decisiones están en [docs/adr/](adr/). Ver el [índice](adr/README.md).
