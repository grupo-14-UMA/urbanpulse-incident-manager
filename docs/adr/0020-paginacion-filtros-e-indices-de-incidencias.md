---
status: proposed
---

# ADR-0020: Paginar y filtrar las incidencias en el servidor

## Contexto

El panel necesita buscar incidencias por estado, categoría, prioridad y fecha, ordenarlas y recorrer el resultado. Filtrar en el navegador requería descargar hasta 500 incidencias abiertas del mapa, omitía las rechazadas y resueltas, y no podía ofrecer resultados correctos ni acotados cuando el conjunto crece. La lista y el mapa también deben respetar el alcance de datos de cada rol.

## Opciones consideradas

1. Mantener la carga espacial del mapa y filtrar y paginar en el navegador.
2. Añadir un endpoint específico de búsqueda y conservar por separado el endpoint del mapa.
3. Reutilizar `GET /api/incidents`, seleccionando listado paginado sin límites geográficos y consulta espacial cuando se proporcionan los cuatro límites.

## Decisión

La misma ruta sirve dos modos acotados: sin límites devuelve una página con tamaño predeterminado de 20 y máximo de 100; con los cuatro límites WGS 84 devuelve hasta 500 incidencias abiertas del área visible. Ambos modos admiten los filtros compatibles. El listado permite ordenar por fecha o prioridad y aplica el ámbito del usuario antes de los filtros: operadores ven todas las incidencias, ciudadanos las propias y técnicos las asignadas. El mapa aplica el mismo ámbito por rol en la consulta SQL.

Se usan índices compuestos que comienzan por los campos de filtro o alcance y continúan con fecha e identificador para ordenar resultados estables. La prioridad se ordena con una expresión indexada derivada de su enum; no se almacena una columna calculada redundante. La prueba de rendimiento inserta un millón de filas, captura el SQL de listado producido por Hibernate al llamar al endpoint y compara su plan y tiempo de ejecución con los índices desactivados y activos. Las consultas de estado, categoría, autor, técnico y prioridad también verifican sus índices con `EXPLAIN (ANALYZE, BUFFERS)`.

## Consecuencias

- \+ La API devuelve solo una página y el listado puede buscar en todos los estados sin depender del área del mapa.
- \+ Los límites espaciales y la consulta de lista conservan respuestas acotadas; la autorización por rol se aplica en el servidor.
- \+ Los índices respaldan el orden y los filtros comunes y se comprueban sobre un conjunto sintético de un millón de incidencias.
- − Los índices ocupan espacio y añaden coste a las escrituras; deben revisarse si los patrones de consulta cambian.
- − La paginación usa offset; páginas muy profundas pueden requerir una estrategia distinta si las mediciones de producción lo justifican.
- − La prueba de rendimiento tarda más que las pruebas funcionales porque crea un millón de filas.
