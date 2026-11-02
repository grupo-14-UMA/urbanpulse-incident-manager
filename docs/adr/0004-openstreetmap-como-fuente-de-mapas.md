---
status: accepted
---

# ADR-0004: Usar OpenStreetMap como API de datos sobre mapas

## Contexto

La localización es central en el sistema: el ciudadano marca la posición de la incidencia y el personal la visualiza en un mapa. Hace falta una fuente de cartografía base para la aplicación móvil con el menor coste posible, al tratarse de un proyecto académico. Además, los datos externos no deben bloquear la creación del reporte: si el mapa no carga, el ciudadano debe poder registrar la incidencia con las coordenadas del dispositivo.

## Opciones consideradas

1. OpenStreetMap
2. Google Maps
3. Mapbox

## Decisión

Utilizar OpenStreetMap como fuente de cartografía base dándole los créditos necesarios. Las cuadrículas se mostrarán en la aplicación mediante el renderizador. Las coordenadas se obtendrán del GPS del dispositivo o de la selección en el mapa, sin depender de ningún servicio externo para guardarse.

## Consecuencias

- \+ Sin coste de licencia ni claves de API para la cartografía base.
- \+ Datos abiertos y bien actualizados para el contexto urbano de Málaga.
- \+ Compatible con el uso posterior de PostGIS.
- − Los servidores públicos no garantizan disponibilidad ni rendimiento para tráfico alto.
- − Obliga a mostrar la atribución en la aplicación para cumplir con el copyright.
- − Estilo visual menos pulido que el de proveedores comerciales.