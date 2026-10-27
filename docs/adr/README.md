# Decisiones de arquitectura (ADRs)

Cada ADR recoge una decisión relevante: su contexto, las opciones consideradas, la decisión y sus consecuencias.

## Cómo añadir un ADR

1. Copia [`0000-plantilla.md`](0000-plantilla.md) como `NNNN-titulo-en-kebab-case.md`, con el siguiente número libre.
2. Rellena las secciones y deja `status: proposed`.
3. Ábrelo en un PR. Cuando el equipo lo acepte, cambia a `status: accepted`.
4. Si una decisión sustituye a otra, marca la antigua como `status: superseded` y enlaza la nueva.

## Índice

| ADR | Decisión | Estado |
| --- | --- | --- |
| [0001](0001-postgresql-como-base-de-datos.md) | PostgreSQL como base de datos | accepted |
| [0002](0002-spring-boot-para-backend.md) | Spring Boot para el backend | proposed |
| [0003](0003-react-native-con-expo-para-frontend.md) | React Native con Expo para el frontend | proposed |
| [0004](0004-openstreetmap-como-fuente-de-mapas.md) | OpenStreetMap como fuente de mapas | proposed |
| [0005](0005-react-native-maps-como-renderizador.md) | react-native-maps como renderizador | proposed |
| [0006](0006-arquitectura-monolitica-inicial.md) | Arquitectura monolítica inicial | proposed |
| [0007](0007-postgis-para-datos-geoespaciales.md) | PostGIS para datos geoespaciales | proposed |
| [0008](0008-datos-abiertos-del-ayuntamiento-de-malaga.md) | Datos abiertos del Ayuntamiento de Málaga | proposed |
| [0009](0009-openweathermap-para-meteorologia.md) | OpenWeatherMap para meteorología | proposed |
| [0010](0010-gemini-y-chatgpt-como-ia-principal.md) | Gemini y ChatGPT como IA principal | proposed |
| [0011](0011-mapa-especifico-por-categoria.md) | Mapa específico por categoría | proposed |
| [0012](0012-docker-compose-para-desarrollo-local.md) | Docker Compose para el desarrollo local | proposed |

<!-- TODO: mantener la columna de estado al día. -->
