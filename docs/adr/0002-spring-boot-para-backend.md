---
status: accepted
---

# ADR-0002: Usar Spring Boot como tecnología para backend

## Contexto

El sistema necesita un backend que exponga una API REST para registrar y consultar incidencias, gestionar usuarios y roles, y mantener un ciclo de vida auditable. Además, debe integrar fuentes externas sin que su indisponibilidad impida crear un reporte, y persistir los datos en PostgreSQL. Se debe tener en cuenta su posterior despliegue en Amazon Web Services (AWS) y que el equipo tiene experiencia previa en Java.

## Opciones consideradas

1. Spring Boot (Java 21)
2. Node.js (NestJS / Express)
3. Python (FastAPI / Django)

## Decisión

Utilizar Java 21 con Spring Boot 3, organizado en capas (controladores, servicios, repositorios y entidades) y como monolito.

## Consecuencias

- \+ Ecosistema maduro con soluciones estándar para seguridad, validación, persistencia y pruebas.
- \+ Integración directa con PostgreSQL mediante Spring Data JPA.
- \+ Se despliega como un único artefacto en contenedor, compatible con AWS.
- − Mayor consumo de memoria y tiempo de arranque que otras opciones más ligeras.
- − Más código repetitivo que en frameworks como FastAPI o Express.