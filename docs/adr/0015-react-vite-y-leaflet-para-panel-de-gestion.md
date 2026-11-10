---
status: accepted
---

# ADR-0015: Usar React con Vite y Leaflet para el panel de gestión

## Contexto

El panel de gestión es el cliente web del operador, el administrador y el analista (ver [ADR-0014](0014-aplicacion-movil-y-panel-de-gestion-web.md)). Su vista principal es un mapa con muchas incidencias y capas urbanas, sobre el que se filtra, valida y asigna. La aplicación móvil usa `react-native-maps` (ver [ADR-0005](0005-react-native-maps-como-renderizador.md)), que no funciona en web, así que el panel necesita otra librería de mapas. La cartografía debe seguir siendo la de OpenStreetMap (ver [ADR-0004](0004-openstreetmap-como-fuente-de-mapas.md)), y el equipo ya conoce React y JavaScript por la aplicación móvil.

## Opciones consideradas

1. React con Vite y Leaflet (`react-leaflet`)
2. Expo web en el mismo proyecto que la aplicación móvil, con otra librería de mapas solo para web
3. React con MapLibre GL

## Decisión

Desarrollar el panel de gestión como un proyecto web independiente con React y Vite, en JavaScript como la aplicación móvil, y usar `react-leaflet` para el mapa, con las teselas raster de OpenStreetMap.

Para que el mapa siga siendo rápido con muchas incidencias, se cargarán solo las del área visible. La API limita cada respuesta a 500 incidencias y avisa cuando hay más; la agrupación de marcadores (`leaflet.markercluster`) sigue pendiente.

## Consecuencias

- \+ Leaflet es la librería de mapas web más extendida, con mucha documentación y una API sencilla para marcadores, capas y eventos.
- \+ Usa directamente las mismas teselas raster de OpenStreetMap que la aplicación móvil.
- \+ El panel se compila a ficheros estáticos, que pueden servirse aparte del backend.
- \+ La aplicación móvil no carga con las pantallas ni las dependencias del panel.
- − No se comparte código de interfaz con la aplicación móvil; solo se comparte la API.
- − Leaflet dibuja los marcadores en el DOM; si el área visible contiene muchos puntos, la falta de agrupación puede degradar el mapa. MapLibre GL rinde mejor con muchos elementos, pero necesita teselas o estilos vectoriales y es más complejo.
- − Expo web habría permitido un solo proyecto, pero con dos renderizadores de mapa y con las rutas de gestión dentro de la aplicación móvil.
