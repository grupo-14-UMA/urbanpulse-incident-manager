---
status: accepted
---

# ADR-0005: Usar React-Native-Maps como renderizador de mapas en la aplicación

## Contexto

La aplicación móvil debe mostrar un mapa en el que el ciudadano seleccione la posición de la incidencia y en el que el personal visualice incidencias como marcadores. La aplicación está desarrollada con React Native y Expo (ver [ADR-0003](0003-react-native-con-expo-para-frontend.md)) y los datos del mapa proceden de OpenStreetMap (ver [ADR-0004](0004-openstreetmap-como-fuente-de-mapas.md)), por lo que se necesita un componente que renderice el mapa y permita superponer teselas propias, marcadores y capas.

## Opciones consideradas

1. react-native-maps
2. MapLibre React Native
3. Leaflet dentro de una WebView

## Decisión

Utilizar `react-native-maps`, con el componente `UrlTile` para mostrar las teselas de OpenStreetMap, y `Marker` para representar las incidencias. La posición del usuario se obtendrá con el módulo de ubicación de Expo.

## Consecuencias

- \+ Es la librería de mapas más extendida en React Native, con amplia documentación y comunidad.
- \+ Compatible con Expo y con una API sencilla para marcadores, regiones y eventos de selección.
- \+ Rendimiento nativo, superior al de una WebView.
- − El proveedor de mapa por defecto depende de la plataforma (Apple Maps en iOS, Google Maps en Android), por lo que habrá que configurar `UrlTile` para que la cartografía visible sea la de OpenStreetMap.
- − No incluye agrupación de marcadores (*clustering*); habrá que añadirla con una librería adicional si crece el volumen de incidencias.
- − Menor control del estilo del mapa que con MapLibre.