---
status: accepted
---

# ADR-0009: Utilizaremos la API OpenWeatherMap para saber el tiempo de Málaga

## Contexto

En la aplicación aparecerán avisos derivados por las condiciones climáticas: un árbol caido por el viento o una calle inundada, por ejemplo. El operario necesita saber el clima para conocer su gravedad. De la misma forma, el analista necesita correlacionar incidencias con variables urbanas históricas, por lo que cada observación tiene que tener su fuente y su instante.

## Opciones consideradas

1. OpenWeatherMap
2. AEMET OpenData
3. Open-Meteo

## Decisión

Se elige por su API sencilla con consulta por coordenadas, documentación amplia y su plan gratuito que es suficiente para el volumen del proyecto. En caso de que existiese algún problema, se recurriría a alguna de las opciones consideradas.

## Consecuencias

+ Se obtiene el tiempo de cualquier punto de Málaga sin montar infraestructura propia.
+ Guardar fuente e instante de observación permite reproducir el contexto de una incidencia y correlacionarlo después.
+ Un fallo de la API no afecta al registro de incidencias.
− Requiere una clave de API que debe gestionarse como secreto en local y en AWS.
− El plan gratuito limita el número de llamadas, por lo que habrá que evitar consultas repetidas.
− Es un proveedor comercial, menos oficial que AEMET, y la precisión a escala de barrio puede variar.