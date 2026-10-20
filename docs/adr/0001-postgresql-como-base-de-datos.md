---
status: accepted
---

# ADR-0001: Usar PostgreSQL como base de datos relacional

## Contexto

El sistema requiere una base de datos capaz de gestionar las incidencias y su ciclo de vida. Para ello es necesario que soporte los datos de APIs externas, como las coordenadas o los dispositivos inteligentes de la ciudad. Además, debemos tener en cuenta su posterior despliegue en Amazon Web Services (AWS).

## Opciones consideradas

1. PostgreSQL
2. MySQL
3. Supabase

## Decisión

Utilizar PostgreSQL, levantado mediante Docker Compose en desarrollo local (ver [ADR-0012](0012-docker-compose-para-desarrollo-local.md)).

## Consecuencias

- \+ Cálculo sencillo de distancias y proximidad mediante PostGIS (ver [ADR-0007](0007-postgis-para-datos-geoespaciales.md)).
- \+ Compatibilidad directa con servicios cloud gestionados en AWS.
- − Pérdida de flexibilidad a la hora de almacenar datos.
- − Mayor consumo de recursos que otras opciones más ligeras.
