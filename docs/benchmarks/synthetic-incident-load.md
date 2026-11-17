# Línea base de carga sintética

Medición de la carga de un millón de incidencias en una base nueva del entorno local:

| Fecha | Incidencias | Transiciones de estado | Tiempo total | Entorno |
| --- | ---: | ---: | ---: | --- |
| 2026-11-17 | 1.000.000 | 3.550.000 | 238,0 s | Windows host, Docker Engine 29.9.3, PostgreSQL 17.5 (`postgis/postgis:17-3.5`) |

Se ejecutó `node scripts/generate-synthetic-incidents.mjs 1000000` contra un volumen Docker Compose aislado y vacío. El tiempo medido por el comando incluye la ejecución de PostgreSQL, los `ANALYZE` de ambas tablas y la comunicación con el contenedor. Docker informó el mismo número de versión para cliente y motor: 29.9.3. La salida del generador informa la duración de cada ejecución.

Las ubicaciones sintéticas se distribuyen alrededor de 13 centros de Málaga con un desplazamiento de ±0,005° en latitud y longitud.

Para repetir la medición, arranca el backend con Docker Compose y ejecuta el mismo comando desde la raíz del repositorio. El generador es acumulativo; usa una base desechable o vacía cuando quieras comparar mediciones.
