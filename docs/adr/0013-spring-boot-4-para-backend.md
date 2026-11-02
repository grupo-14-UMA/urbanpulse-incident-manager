---
status: accepted
---

# ADR-0013: Usar Spring Boot 4 para el backend

Sustituye a [ADR-0002](0002-spring-boot-para-backend.md).

## Contexto

El [ADR-0002](0002-spring-boot-para-backend.md) eligió Java 21 y Spring Boot 3 cuando aún no existía código de backend. Al crear el esqueleto (#9) hay que fijar la versión que usará todo el equipo, y el soporte de código abierto de Spring Boot 3.5 ya ha terminado. Empezar en esa línea obligaría a migrar más adelante un proyecto que todavía se puede crear en una versión con soporte.

## Opciones consideradas

1. Spring Boot 4.0.x
2. Spring Boot 3.5

## Decisión

Utilizar Java 21 con Spring Boot 4.0.x y Maven, mediante Maven Wrapper. El backend se organiza en capas (controladores, servicios, repositorios y entidades) y como monolito, igual que en el ADR-0002.

El esquema de la base de datos lo gestiona únicamente Flyway, sin un ADR aparte, e Hibernate solo lo valida al arrancar. La documentación de la API se genera con springdoc-openapi, y las pruebas se ejecutan contra PostgreSQL con PostGIS mediante Testcontainers.

## Consecuencias

- \+ El proyecto empieza en una línea de versiones con soporte.
- \+ Se mantienen las ventajas del ADR-0002: ecosistema maduro para seguridad, validación, persistencia y pruebas, integración con PostgreSQL mediante Spring Data JPA y despliegue como un único artefacto en contenedor.
- − springdoc-openapi debe mantenerse en una línea compatible con Spring Boot 4 (3.x).
- − Parte de la documentación y los ejemplos disponibles todavía se refieren a Spring Boot 3.
- − Mayor consumo de memoria y tiempo de arranque que otras opciones más ligeras.
