---
status: accepted
---

# ADR-0003: Usar como framework React Native como principal tecnología frontend con Expo

## Contexto

El ciudadano necesita registrar incidencias con su posición, adjuntar fotografías y consultar su estado. Son tareas que se realizan desde la calle, por lo que se requiere acceso a GPS, cámara y galería. El equipo es reducido y quiere cubrir Android e iOS con una única base de código, sin configurar entornos nativos complejos.

## Opciones consideradas

1. React Native con Expo
2. Flutter
3. Aplicación web / PWA

## Decisión

Utilizar React Native con Expo (TypeScript) y `react-native-maps` para el mapa. La aplicación se comunicará con el backend por HTTP/JSON y la URL de la API se configurará con la variable `EXPO_PUBLIC_API_URL`, para cambiar de entorno sin modificar el código.

## Consecuencias

- \+ Una única base de código para Android e iOS.
- \+ Desarrollo rápido con Expo Go y acceso sencillo a GPS, cámara y galería.
- \+ Reutiliza el conocimiento de JavaScript/TypeScript y React del equipo.
- − Dependencia de Expo y de su ciclo de versiones.
- − Las variables `EXPO_PUBLIC_*` quedan incluidas en la app, por lo que no pueden contener secretos.
- − Menor rendimiento que el nativo en mapas con muchos marcadores.