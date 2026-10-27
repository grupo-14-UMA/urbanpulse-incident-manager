---
status: accepted
---

# ADR-0008: Utilizaremos la API proporcionada por el ayuntamiento de Málaga (<https://datosabiertos.malaga.eu/>)

## Contexto

UrbanPulse necesita información externa de la ciudad para mostrar el contexto urbano de las incidencias y ayudar a los operadores a tomar decisiones. Esta información se utilizará principalmente para los requisitos RF21 (Barrio y distrito), RF22 (Activo urbano), RF23 (Contexto externo) y RF24 (Vista contextual).Se ha decidido estudiar los conjuntos de datos del portal de Datos Abiertos del Ayuntamiento de Málaga. Aunque no se integrarán en la primera entrega, se seleccionan los siguientes para futuras versiones:

- Cortes de tráfico: información sobre calles cortadas. Formatos como CSV y GeoJSON, con información geográfica. 
- Semáforos: ubicación de semáforos y semáforos acústicos. Disponibles en formatos geográficos como GeoJSON. 
- Contenedores: contenedores de aceite, pilas, ropa, residuos industriales, vidrio, envases y plástico, papel y cartón y residuos sólidos urbanos. Formatos y coordenadas según la categoría.
- Fuentes de agua potable: ubicación de fuentes públicas. 
- Aparcamientos: bicicletas, motos, residentes, rotación, movilidad reducida y aparcamientos municipales. 
- Transporte público: paradas de taxi, líneas y paradas de autobuses EMT.
- Parques caninos: localización de parques para perros, con información geográfica.
- Playas: información y localización de las playas de Málaga.
- Desfibriladores: información sobre desfibriladores y su localización cuando esté disponible.
- Instalaciones deportivas: centros deportivos, piscinas, pistas de tenis y pádel, fútbol, baloncesto, atletismo, senderismo y otras instalaciones. 
- Distritos y barrios: delimitaciones territoriales para identificar el barrio y distrito de cada incidencia. 

Los formatos, las coordenadas y la frecuencia de actualización se comprobarán en cada recurso antes de integrarlo. Se priorizará GeoJSON por facilitar la representación de los datos en mapas.

## Opciones consideradas

1. datosabiertos.malaga.eu
2. juntadeandalucia.es/datosabiertos/portal
3. datos.gob.es

## Decisión

Se utilizará el portal de Datos Abiertos del Ayuntamiento de Málaga como fuente principal de información urbana. Se elige esta opción porque contiene información específica de Málaga, incluyendo tráfico, transporte público, equipamientos y otros activos municipales. Las otras fuentes podrán utilizarse como complemento si falta información necesaria.La integración se realizará en futuras versiones, priorizando los conjuntos de datos que permitan cumplir los requisitos RF21, RF22, RF23 y RF24.

## Consecuencias

\+ Se dispone de información específica de la ciudad de Málaga.
\+ Los formatos geográficos permiten representar activos y delimitar zonas en el mapa.
\+ Se facilita identificar activos cercanos y obtener información del barrio y distrito.
− Algunos datos pueden estar desactualizados o incompletos.
− Los formatos y la frecuencia de actualización varían entre conjuntos.
− La disponibilidad del portal no está garantizada, por lo que la creación de incidencias no dependerá de él.