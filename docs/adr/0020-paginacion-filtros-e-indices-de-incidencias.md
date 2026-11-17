---
status: proposed
---

# ADR-0020: Paginar y filtrar las incidencias en el servidor

## Contexto

El panel necesita buscar incidencias por estado, categoría, prioridad y fecha, ordenarlas y recorrer el resultado. Filtrar en el navegador requería descargar hasta 500 incidencias abiertas del mapa, omitía las rechazadas y resueltas, y no podía ofrecer resultados correctos ni acotados cuando el conjunto crece. La lista y el mapa también deben respetar el alcance de datos de cada rol.

## Opciones consideradas

1. Mantener la carga espacial del mapa y filtrar y paginar en el navegador.
2. Usar una ruta para el listado paginado y otra para la consulta espacial del mapa.
3. Reutilizar una sola ruta y seleccionar el modo según se proporcionen los cuatro límites del mapa.

## Decisión

Se elige la opción 2 para mantener un contrato estable por ruta: `GET /api/incidents` devuelve el listado filtrado y `GET /api/incidents/map` consulta el área visible. El listado devuelve hasta 100 resultados (20 por defecto), ordena por fecha o prioridad y avanza mediante un cursor de clave, sin `OFFSET` ni recuento total. El cursor incluye las claves de orden y está ligado al campo y dirección de ordenación. Ambos endpoints aplican el ámbito del usuario antes de los filtros: operadores ven todas las incidencias, ciudadanos las propias y técnicos las asignadas.

Se usan índices compuestos que comienzan por los campos de filtro o alcance y continúan con fecha e identificador para ordenar resultados estables. Los cursores usan comparaciones nativas de filas sobre las mismas columnas del índice. `priority_rank` y `priority_rank_desc` son columnas generadas y almacenadas que dejan al final las incidencias sin prioridad en ambos sentidos. Las migraciones separan la reescritura de la tabla de la creación de índices; el despliegue requiere una ventana de mantenimiento mientras se aplican ambas. La comprobación de rendimiento es optativa: inserta un millón de filas, captura el SQL del endpoint y compara los planes antes y después de crear los índices; también verifica con `EXPLAIN (ANALYZE, BUFFERS)` los filtros y cursores. Informa los tiempos como referencia, pero no hace depender la verificación normal de una comparación de relojes.

## Consecuencias

- \+ La API devuelve solo una página y el listado puede buscar en todos los estados sin depender del área del mapa.
- \+ Los límites espaciales y la consulta de lista conservan respuestas acotadas; la autorización por rol se aplica en el servidor.
- \+ Los índices respaldan el orden y los filtros comunes y se pueden comprobar sobre un conjunto sintético de un millón de incidencias.
- − Los índices ocupan espacio y añaden coste a las escrituras; deben revisarse si los patrones de consulta cambian.
- − La navegación por cursor solo permite avanzar y volver por cursores que conserve el cliente; no ofrece salto directo a una página arbitraria ni un recuento total.
- − La comprobación de rendimiento optativa tarda más que las pruebas funcionales porque crea un millón de filas.
