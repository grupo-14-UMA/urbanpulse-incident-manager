---
status: accepted
---

# ADR-0011: Cuando un usuario selecciona una categoría de incidencia, se le muestra un mapa concreto para esa categoría

## Contexto

El ciudadano marca en el mapa la posición de la incidencia y puede elegir una categoría, que es opcional. Muchos avisos se refieren a un activo urbano concreto, como un semáforo, un contenedor o una fuente, y el sistema debe poder asociar la incidencia al activo más probable. El portal de datos abiertos de Málaga ofrece capas de esos activos y el sistema debe mostrar capas urbanas relevantes en un mapa (RF08).

Mostrar todas las capas a la vez llenaría el mapa de marcadores, y `react-native-maps` no agrupa marcadores ni rinde bien con muchos. Además, esas capas proceden de una fuente externa que puede no estar disponible.

## Opciones consideradas

1. Un único mapa genérico con todas las capas activables a mano
2. Un mapa específico por categoría, que muestra solo la capa relacionada con ella
3. Un mapa sin capas, solo con la posición del usuario

## Decisión

Cuando el usuario selecciona una categoría, la aplicación muestra un mapa específico que carga solo la capa de activos relacionada con esa categoría (por ejemplo, semáforos para una incidencia de semáforos). Si todavía no ha elegido categoría, o la categoría no tiene capa asociada, se muestra el mapa genérico.

## Consecuencias

- \+ El mapa solo muestra lo relevante para la categoría.
- \+ Facilita elegir el activo afectado y asociarlo a la incidencia.
- \+ Cargar solo una capa y solo la zona visible reduce los datos transferidos y el número de marcadores.
- \+ La indisponibilidad de una capa no impide crear el reporte.
- − Hay que mantener la correspondencia entre categorías y capas.
- − La utilidad depende de la calidad y actualización de los datos abiertos, que varían entre conjuntos.