---
status: proposed
---

# ADR-0021: Generar datos sintéticos con SQL por lotes

## Contexto

Las consultas de incidencias deben seguir siendo útiles con millones de filas. El equipo necesita poder poblar la base local con datos reproducibles para explorar esas consultas y medir su comportamiento. La carga es solo una operación de desarrollo, debe ser explícita y no debe cambiar el esquema de producción.

## Opciones consideradas

1. Crear cada incidencia mediante la API o las entidades JPA.
2. Insertar el conjunto con SQL de PostgreSQL mediante operaciones por lotes.

## Decisión

Un comando local de Node.js ejecuta un script SQL con `generate_series`. La base de datos crea las incidencias en conjunto y mantiene las claves foráneas y los índices existentes. Los datos se distribuyen en barrios de Málaga, usan las categorías y los estados precargados, y reparten las incidencias entre cuentas ciudadanas sintéticas y técnicos de desarrollo.

El generador también escribe todas las transiciones necesarias en `StatusChange`: registro, validación, asignación, inicio, resolución o rechazo. Cada fila mantiene una secuencia válida del [ciclo de vida de la incidencia](0019-ciclo-de-vida-de-la-incidencia.md). No genera fotografías ni llama a la API. Cada ejecución añade incidencias; no borra ni reemplaza datos existentes. El tamaño se indica explícitamente y se limita a cinco millones por ejecución. La distribución y los campos derivados de la secuencia son deterministas; las fechas se calculan respecto a la hora de ejecución.

## Consecuencias

- \+ La carga masiva se ejecuta en PostgreSQL, sin construir millones de entidades en memoria en la API.
- \+ Para el mismo número y estado inicial, la distribución y los campos derivados de la secuencia son reproducibles; las fechas son relativas a la ejecución.
- \+ Las relaciones de usuario, categoría, estado e historial cumplen el esquema existente.
- \− Una ejecución que genera también el historial escribe varias filas por incidencia y requiere más almacenamiento y tiempo que poblar solo `Incident`.
- \− El comando depende de Docker Compose, de las migraciones aplicadas y de las cuentas de desarrollo.
- \− Ejecutarlo varias veces añade más filas; el usuario elige el volumen acumulado.
