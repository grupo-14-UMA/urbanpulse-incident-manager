---
status: accepted
---

# ADR-0018: Almacenamiento de fotografías en un volumen detrás de una interfaz

## Contexto

Una incidencia puede incluir hasta 3 fotografías aportadas como evidencia visual (`image/jpeg`, `image/png` o `image/webp`, hasta 5 MB por fichero). Con millones de incidencias, guardar los binarios dentro de PostgreSQL encarecería las copias de seguridad, el registro de transacciones (WAL) y la memoria caché de la base de datos. Al mismo tiempo, en el entorno local basado en Docker Compose las fotografías deben sobrevivir al reinicio o recreación del contenedor del backend sin depender todavía de una cuenta externa en la nube, pero el diseño debe permitir migrar a un almacenamiento de objetos compatible con S3 en despliegues posteriores sin modificar la lógica de negocio ni el contrato de la API.

## Opciones consideradas

1. Guardar los binarios de las fotografías como columnas `BYTEA` en PostgreSQL.
2. Escribir los ficheros directamente en el sistema de archivos local desde el servicio de incidencias.
3. Guardar en PostgreSQL únicamente los metadatos (`Attachment`: clave de almacenamiento, tipo MIME, tamaño y fecha) y delegar la lectura, escritura y borrado de los bytes en una interfaz `PhotoStorage`, cuya implementación inicial (`FileSystemPhotoStorage`) persiste en un volumen de Docker (`incident_photos`).

## Decisión

Se elige la opción 3:

- La tabla `Attachment` almacena solo los metadatos de cada fotografía (`incident_id`, `storage_key`, `file_type`, `file_size`, `created_at`) con el índice compuesto `(incident_id, id)`.
- El límite de 3 fotografías por incidencia se protege en transacción bloqueando la fila de `Incident` (`SELECT … FOR UPDATE`) antes de contar e insertar los adjuntos.
- El backend valida que cada fichero no esté vacío, no supere 5 MB y tenga un tipo MIME declarado (`image/jpeg`, `image/png` o `image/webp`) coherente con su firma binaria (*magic bytes*).
- El dominio depende únicamente de la interfaz `PhotoStorage` (`store`, `read`, `delete`). En local y Docker Compose, `FileSystemPhotoStorage` escribe bajo el directorio configurado por `PHOTOS_DIR` (montado en el volumen `incident_photos`) y verifica que ninguna clave resuelva fuera de dicho directorio.
- Las fotografías se sirven a usuarios autenticados a través de `GET /api/incidents/{id}/photos/{photoId}` con la cabecera `X-Content-Type-Options: nosniff`.

## Consecuencias

- \+ PostgreSQL mantiene tablas ligeras e indexadas; el tamaño de los binarios no degrada las consultas relacionales ni espaciales.
- \+ Las fotografías sobreviven a reinicios del contenedor del backend gracias al volumen `incident_photos` de Docker Compose.
- \+ Pasar a Amazon S3 o un servicio compatible en entregas posteriores solo requiere añadir otra implementación de `PhotoStorage`, sin cambiar controladores, entidades ni clientes.
- − El respaldo de datos comprende ahora tanto la base de datos como el volumen de fotografías, y el servicio debe limpiar los ficheros escritos si la transacción de base de datos falla.
