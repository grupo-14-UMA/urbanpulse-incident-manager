---
status: accepted
---

# ADR-0007: Almacenar datos geoespaciales usando PostGIS para PostgreSQL

## Contexto

La localización es central en el sistema: cada incidencia tiene una posición, obtenida del GPS del dispositivo o de la selección en el mapa ([ADR-0004](0004-openstreetmap-como-fuente-de-mapas.md)). Varios requisitos necesitan consultas espaciales sobre ella: sugerir incidencias similares por proximidad (RF16), identificar el barrio y el distrito a partir de la localización (RF21) y asociar una incidencia a un activo urbano próximo (RF22). Además, las capas urbanas del portal de datos abiertos de Málaga se publicarán preferentemente en GeoJSON ([ADR-0008](0008-datos-abiertos-del-ayuntamiento-de-malaga.md)).

## Opciones consideradas

1. PostGIS sobre PostgreSQL
2. Columnas de latitud y longitud, con los cálculos espaciales en el backend

## Decisión

Utilizar la extensión PostGIS sobre PostgreSQL ([ADR-0001](0001-postgresql-como-base-de-datos.md)) desde la primera migración, en lugar de empezar con latitud y longitud y migrar después.

- La localización de una incidencia se guarda como `geometry(Point, 4326)`, el sistema de referencia WGS 84 que usan las coordenadas GPS.
- La migración de Flyway activa la extensión `postgis`.
- Las pruebas y el entorno local usan la imagen `postgis/postgis`, para trabajar siempre con el mismo motor.
- La API sigue exponiendo la localización como `latitude` y `longitude`; la conversión a geometría es responsabilidad del backend.

## Consecuencias

- \+ Las consultas de proximidad y de pertenencia a un barrio o distrito se resuelven en la base de datos, con funciones e índices espaciales, sin implementarlas a mano.
- \+ Se evita migrar más adelante los datos de localización ya almacenados.
- \+ La aplicación móvil no depende de cómo se almacena la localización.
- − Hace falta `hibernate-spatial` para mapear las geometrías en las entidades JPA.
- − La extensión también debe estar disponible en la base de datos del despliegue en AWS.
- − Añade complejidad en la primera entrega, que todavía no hace consultas espaciales.
