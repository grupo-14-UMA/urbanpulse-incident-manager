---
status: accepted
---

# ADR-0018: Almacenamiento local persistente de fotografías

## Contexto

Las fotografías de una incidencia deben poder descargarse después de reiniciar o recrear el backend. Guardar los bytes únicamente en el sistema de ficheros del contenedor los pierde al reemplazarlo; guardar los bytes en PostgreSQL acoplaría el almacenamiento de ficheros a la base de datos.

## Opciones consideradas

1. Guardar los bytes en el sistema de ficheros del contenedor sin volumen.
2. Guardar los bytes en un volumen persistente de Docker y sus metadatos en PostgreSQL.
3. Guardar los bytes como objetos en un almacenamiento externo.

## Decisión

En el entorno local, guardar los ficheros en `PHOTOS_DIR` y sus metadatos en la tabla `Attachment`. Docker Compose configura `PHOTOS_DIR` en `/var/lib/urbanpulse/photos` y monta ahí el volumen nombrado `incident_photos`. Un servicio inicializador prepara sus permisos para el usuario sin privilegios del backend antes de arrancarlo.

La implementación depende de la interfaz `PhotoStorage`, de modo que el almacenamiento externo se pueda incorporar con su propia decisión cuando se defina el despliegue de producción.

## Consecuencias

- `docker compose restart` y `docker compose down` seguido de `docker compose up` conservan los ficheros.
- `docker compose down -v` elimina el volumen de fotos junto con el de PostgreSQL.
- La copia de seguridad debe conservar tanto los metadatos de `Attachment` como el contenido de `incident_photos`.
- Un despliegue con varias instancias del backend necesitará un almacenamiento compartido, en lugar de un volumen local por contenedor.
