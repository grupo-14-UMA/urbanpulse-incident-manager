---
status: proposed
---

# ADR-0014: Separar la aplicación móvil del panel de gestión web

## Contexto

UrbanPulse tiene cinco actores con tareas muy distintas. El ciudadano registra incidencias y sigue su estado en la calle, donde ve el problema y donde tiene a mano el GPS y la cámara del móvil. El técnico también trabaja en la calle: atiende los trabajos asignados y documenta la resolución con fotografías. El operador, el administrador y el analista trabajan desde un puesto de trabajo: filtran y validan incidencias sobre un mapa con capas, configuran la plataforma o estudian indicadores, tareas que piden una pantalla grande.

El enunciado no dice qué cliente usa cada actor, pero el profesor ha indicado que la gestión debe hacerse desde una interfaz web, además de la aplicación móvil.

## Opciones consideradas

1. Aplicación móvil para el ciudadano y el técnico, y panel de gestión web para el operador, el administrador y el analista
2. Aplicación móvil para todos los actores
3. Portal web para el ciudadano y aplicación para todo el personal
4. Aplicación móvil y portal web para el ciudadano, y panel de gestión web para el personal

## Decisión

Se adopta la opción 1. UrbanPulse tiene dos clientes:

- **Aplicación móvil** (React Native con Expo, ver [ADR-0003](0003-react-native-con-expo-para-frontend.md)): la usan el ciudadano y el técnico.
- **Panel de gestión** (web, ver [ADR-0015](0015-react-vite-y-leaflet-para-panel-de-gestion.md)): lo usan el operador, el administrador y el analista.

El ciudadano no tiene versión web: registra y sigue sus incidencias solo desde la aplicación móvil.

Los dos clientes consumen la misma API REST del backend monolítico (ver [ADR-0006](0006-arquitectura-monolitica-inicial.md)). Lo que cada uno puede hacer lo deciden los permisos de su rol (RF02), no endpoints distintos para cada cliente.

## Consecuencias

- \+ Cada actor usa el dispositivo que encaja con su trabajo: el móvil en la calle y el ordenador en el puesto de trabajo.
- \+ La aplicación móvil se mantiene sencilla y no incluye las pantallas de gestión.
- \+ Una sola API y un solo backend que desplegar, escalar y observar.
- − Hay dos clientes que desarrollar y mantener, con tecnologías de mapas distintas.
- − El backend debe permitir CORS para el origen del panel de gestión, algo que la aplicación móvil no necesita.
- − Los registros y métricas del backend deben distinguir de qué cliente o rol viene cada petición, porque los dos comparten API.
