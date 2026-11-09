---
status: accepted
---

# ADR-0017: Guardar y exponer los instantes en UTC con zona explícita

## Contexto

La fecha de registro de una incidencia se guardaba como `timestamp` sin zona y la API la devolvía como `LocalDateTime` (`2026-11-07T10:15:30.123456`). El valor era UTC, pero nada lo indicaba: cada cliente tenía que suponerlo, y la aplicación móvil añadía `Z` y recortaba los microsegundos para poder interpretarlo. Además, el valor por defecto `CURRENT_TIMESTAMP` de una columna `timestamp` depende de la zona de la sesión, así que una fila insertada fuera del backend, como las de una carga masiva de datos sintéticos, podía quedar en otra zona sin que se notara. El modelo de datos prevé más instantes (historial de estados, asignaciones, observaciones de fuentes externas) que tendrían el mismo problema.

## Opciones consideradas

1. Mantener `timestamp` en la base de datos y exponer `Instant` en Java
2. Usar `timestamptz` en la base de datos y mantener `LocalDateTime` en Java
3. Usar `timestamptz` en la base de datos e `Instant` en Java

## Decisión

Todos los instantes del sistema se representan con la opción 3:

- Las columnas son `timestamptz`.
- Las entidades JPA usan `Instant`, truncado a milisegundos al generarse.
- La API los serializa en ISO-8601 en UTC con milisegundos y sufijo `Z`, por ejemplo `2026-11-07T10:15:30.123Z`.

Los clientes los interpretan directamente y deciden la zona en la que los muestran.

## Consecuencias

- \+ El instante es el mismo en la base de datos, el backend y los clientes, sin depender de la zona de la JVM ni de la sesión de PostgreSQL.
- \+ Los clientes no necesitan reparar el formato antes de interpretarlo.
- \+ La API y la base de datos devuelven el mismo valor, porque no se pierde precisión entre una y otra.
- − Las columnas existentes requieren una migración que reescribe la tabla, barata ahora y cara cuando haya millones de filas.
- − Se pierde la precisión por debajo del milisegundo, que ningún caso de uso necesita.
