---
status: accepted
---

# ADR-0010: Gemini y ChatGPT como IA principal

## Contexto

El equipo es reducido, el plazo es limitado y el proyecto abarca varias tecnologías (Spring Boot, PostgreSQL con PostGIS, React Native con Expo, Docker y AWS), algunas de las cuales no todos los miembros dominan. Un asistente de IA puede acelerar la escritura de código, pruebas y documentación, y ayudar a aprender estas tecnologías.

Este ADR trata de las herramientas de IA que usa el equipo para desarrollar UrbanPulse, no de funcionalidades de IA del producto. Conviene fijar cuáles se usan y bajo qué reglas, para que todos trabajen igual, para no comprometer secretos ni datos personales y para que el código generado se revise igual que cualquier otro. El repositorio ya prevé que las incidencias del tracker puedan implementarse con o sin ayuda de agentes (`docs/agents/`).

## Opciones consideradas

1. Gemini (Google) y ChatGPT (OpenAI)
2. GitHub Copilot u otro asistente integrado en el IDE
3. No usar asistentes de IA

## Decisión

Utilizar Gemini y ChatGPT como herramientas de IA principales durante el desarrollo, para generar y explicar código, proponer pruebas, redactar documentación y resolver dudas técnicas. Se aplican estas reglas:

- Todo lo generado con IA pasa por la revisión y las pruebas habituales antes de integrarse; la persona que lo aporta es responsable de su contenido, como si lo hubiera escrito ella.
- No se introducen en los prompts credenciales, ficheros `.env`, claves de API ni datos personales de usuarios.
- Las propuestas deben respetar los ADR vigentes; si alguna los contradice, se plantea explícitamente en lugar de aplicarla en silencio.
- Cada miembro puede usar además otras herramientas en su IDE, pero Gemini y ChatGPT son las de referencia del equipo.

## Consecuencias

- \+ Se acelera la escritura de código, pruebas y documentación, y se reduce el tiempo de aprendizaje de las tecnologías nuevas para el equipo.
- \+ Dos herramientas permiten contrastar respuestas y seguir trabajando si una falla o agota su cuota.
- \+ Con reglas comunes, el código generado se trata igual que el resto en revisión y pruebas.
- − Las herramientas pueden generar código incorrecto o desactualizado.
- − Existe riesgo de filtrar secretos o datos personales a terceros si no se respetan las reglas sobre prompts.
- − Dependencia de servicios externos, con límites de uso en los planes gratuitos.
- − Puede haber diferencias de estilo y calidad entre aportaciones.