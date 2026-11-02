---
status: accepted
---

# ADR-0006: Arquitectura monolítica para el desarrollo inicial

## Contexto

El sistema debe cubrir el registro de incidencias, su ciclo de vida auditable, la gestión de usuarios y roles, la integración con fuentes externas y la asistencia inteligente. El equipo es reducido, el plazo es limitado y el volumen de usuarios inicial es bajo. Además, se aplica la pauta de adoptar la solución más sencilla que satisfaga los atributos de calidad medidos, y se debe tener en cuenta su posterior despliegue en Amazon Web Services (AWS).

## Opciones consideradas

1. Monolito
2. Microservicios
3. Funciones serverless (AWS Lambda)

## Decisión

Desarrollar un único backend Spring Boot (ver [ADR-0002](0002-spring-boot-para-backend.md)) desplegado como un solo artefacto en contenedor, junto a la aplicación móvil y PostgreSQL. Internamente se organizará en paquetes por funcionalidad (incidencias, categorías, usuarios, integraciones externas), con dependencias claras entre ellos, para facilitar una posible separación posterior.

## Consecuencias

- \+ Desarrollo, pruebas y despliegue más sencillos, con un único artefacto y un único repositorio.
- \+ Transacciones locales que facilitan mantener la coherencia del ciclo de vida y la auditoría de las incidencias.
- \+ Menor coste y complejidad operativa al ejecutarse con Docker Compose (ver [ADR-0012](0012-docker-compose-para-desarrollo-local.md)) y en AWS.
- − Todo el sistema escala y se despliega a la vez, sin poder escalar de forma independiente un módulo concreto.
- − Riesgo de acoplamiento entre módulos si no se respetan los límites de los paquetes.
- − Si la asistencia inteligente o la ingesta de datos externos crecen, podría ser necesario extraerlas a servicios separados.