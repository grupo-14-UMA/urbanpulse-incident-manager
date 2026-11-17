# Documentos de diseño

Un documento de diseño (*Software Design Document*, *design doc* o DD) describe cómo se propone resolver un problema, qué decisiones técnicas se toman y por qué se eligen frente a otras alternativas. Sirve para que el equipo analice, discuta y valide el diseño **antes** de invertir esfuerzo en implementarlo. Este documento resume las indicaciones del profesor ("Documentos de Diseño de Software", Desarrollo Software en Plataformas en la Nube, UMA) y el artículo en el que se basan: Michael Lynch, [*How to Write an Effective Software Design Document*](https://refactoringenglish.com/excerpts/write-an-effective-design-doc/).

## Regla del proyecto

Cada funcionalidad nueva de [docs/requirements.md → Funcionalidades de la segunda fase](../requirements.md#funcionalidades-de-la-segunda-fase), y cada cambio arquitectónico significativo (separar servicios, comunicación asíncrona, cambiar el almacenamiento o la autenticación), se diseña primero en un documento de diseño:

1. **No se programa** ninguna parte de la solución hasta que su documento está `Aceptado`.
2. Los issues de implementación se crean, o pasan a `✅ ready`, a partir del documento aceptado y lo enlazan. Antes solo existe el issue de escribir el documento.
3. **Las decisiones las toma el equipo.** Quien redacta (una persona o un agente) investiga, propone alternativas con sus ventajas, inconvenientes y evidencia, y recomienda una; el equipo discute y decide, y el profesor (el cliente) valida el resultado. Una propuesta nunca se presenta como decisión tomada.
4. El razonamiento es lo más importante del documento: por qué se elige una opción, qué se descarta y qué se gana y se pierde. La plantilla no tiene que rellenarse al pie de la letra; las secciones que no aplican se quitan.

## Cuándo escribir uno

Conviene escribirlo si alguna de estas preguntas tiene un "sí", y es necesario si lo tienen dos o más:

| Criterio | Pregunta |
| --- | --- |
| Coordinación | ¿Colaboran varias personas en la implementación? |
| Duración | ¿Exige más de tres meses de trabajo a tiempo completo? |
| Vida en producción | ¿Funcionará durante años? |
| Integración | ¿Se integra con servicios de otros equipos o externos? |
| Ambigüedad | ¿Los objetivos o requisitos son difusos? |
| Riesgos graves | ¿Hay riesgos de seguridad, privacidad o cumplimiento legal? |

El esfuerzo es proporcional al riesgo: desde una página para un cambio moderado hasta un documento largo para un cambio de arquitectura. En un cambio trivial, cero.

## Qué incluir: el coste de equivocarse

Un documento que detalla todo es ya la implementación. Para cada decisión, la pregunta es: **¿cuánto cuesta equivocarse?**

- **Alto coste (permanentes):** lenguaje, paradigma de base de datos, modelo de consistencia, arquitectura de almacenamiento, contratos entre servicios, división en servicios. Se justifican en detalle.
- **Bajo coste (triviales):** estilo de paginación en la interfaz, nombres internos, detalles visuales. Se corrigen en horas y no consumen revisión.

## Estructura

La [plantilla](plantilla.md) es la de la asignatura. Sus secciones cubren los componentes habituales de un documento de diseño:

| Bloque | Componentes | Sección de la plantilla |
| --- | --- | --- |
| Identificación y alineación | Título corto y distintivo; metadatos (autoría, fecha, estado, quién aprobó y cuándo); objetivo en una frase; contexto: por qué ahora, qué problema, qué se intentó antes; documentos relacionados | Metadatos, 1, 2 |
| Alcance y experiencia | Objetivos expresados como impacto, no como implementación ("minimizar caídas al desplegar", no "añadir Kubernetes"); no objetivos que un lector podría suponer incluidos; escenarios de uso narrados paso a paso | 3, 4 |
| Arquitectura y restricciones | Diagramas editables (Mermaid o C4 en `docs/workspace.dsl`); glosario; restricciones (presupuesto, plazo, infraestructura); interfaces (API, eventos, formatos); dependencias e infraestructura, sobre todo las difíciles de cambiar | 5, 7.1-7.3, 7.6 |
| Operación y calidad | SLOs (ver [SLIs, SLOs y SLAs](../course/slis-slos-slas.md)); monitorización y alertas; seguridad (amenazas, superficie de ataque, fronteras de confianza); privacidad (datos sensibles, retención, acceso); legal (licencias, condiciones de servicios externos); logs (qué eventos, niveles, retención, qué no se registra nunca) | 4, 7.4-7.6, 10 |
| Planificación y conocimiento | Hitos que entregan algo demostrable; problemas abiertos (problema, opciones, siguiente paso) y resueltos (decisión y discusión conservada); alternativas consideradas, en pocas líneas cada una | 6, 8, 9, 11, 12 |

En los requisitos de calidad se describen escenario, condiciones, métrica, umbral y método de validación. Los umbrales salen del enunciado, de la negociación con el cliente o de una medición; nunca se inventan como si fueran hechos.

## Ciclo de vida y revisión

| Estado | Significado |
| --- | --- |
| Borrador | Se está redactando; puede tener problemas abiertos. |
| En revisión | El equipo lo revisa en un PR; después, el profesor. |
| Aceptado | El equipo lo aprobó y el profesor lo validó. Se puede implementar. |
| Sustituido | Otro documento lo reemplaza; se enlaza. |
| Descartado | No se hará; se conserva el motivo. |

Pautas de revisión:

1. **Autocontenido.** Las primeras páginas (objetivo, contexto y escenarios) bastan para entenderlo sin una explicación verbal.
2. **Comentarios clasificados.** Se distingue entre aclaraciones, objeciones de fondo sobre decisiones de alto coste y desacuerdos menores.
3. **Debates cerrados por escrito.** Un desacuerdo se resume en *Problemas abiertos*, se decide según el coste de equivocarse y pasa a *Problemas resueltos* con la decisión y la discusión.

Un documento aceptado queda como registro histórico de la decisión; la documentación operativa (`docs/architecture.md`, `README.md`, guías) describe el sistema tal como queda implementado. Si la implementación se aparta del diseño, se anota en la sección 12 del documento.

## Relación con los ADR y los issues

- Cada documento tiene una sección de decisiones con enlaces a los [ADR](../adr/) que derivan de él, y cada uno de esos ADR enlaza el documento que lo originó.
- La redacción del documento es un issue `docs: …`. Los issues de implementación salen del plan del documento aceptado (sección 9).

## Cómo añadir un documento

1. Copia [`plantilla.md`](plantilla.md) como `DD-NNN-titulo-en-kebab-case.md`, con el siguiente número libre.
2. Rellénalo con estado `Borrador` y ábrelo en un PR para revisarlo como `En revisión`.
3. Cuando el equipo lo apruebe y el profesor lo valide, cambia a `Aceptado` con la fecha y quién lo aprobó.

## Índice

| DD | Título | Estado |
| --- | --- | --- |
| — | Todavía no hay documentos de diseño. | — |
