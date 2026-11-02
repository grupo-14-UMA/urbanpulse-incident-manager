---
status: proposed
---

# ADR-0013: Usar Spring Boot 4 para el backend

Sustituye a [ADR-0002](0002-spring-boot-para-backend.md).

## Contexto

El [ADR-0002](0002-spring-boot-para-backend.md) eligió Spring Boot 3 cuando aún no existía código de backend. Al crear el esqueleto (#9) hay que fijar la versión que usará todo el equipo, y el soporte de código abierto de Spring Boot 3.5 ya ha terminado.

## Opciones consideradas

1. Spring Boot 4.0.x
2. Spring Boot 3.5

## Decisión

Utilizar Spring Boot 4.0.x. Se mantiene el resto de lo decidido en el ADR-0002: Java 21, backend organizado en capas (controladores, servicios, repositorios y entidades) y monolito.

## Consecuencias

- \+ El proyecto empieza en una línea de versiones con soporte.
- \+ Las migraciones de la base de datos se gestionan con el starter de Flyway de Spring Boot.
- − springdoc-openapi debe mantenerse en la línea 3.x, la que admite Spring Boot 4.
- − Parte de la documentación y los ejemplos disponibles todavía se refieren a Spring Boot 3.
