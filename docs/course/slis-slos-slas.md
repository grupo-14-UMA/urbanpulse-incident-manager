# SLIs, SLOs y SLAs

Resumen de "Definición y medición de atributos de calidad: SLIs, SLOs y SLAs" (Desarrollo Software en Plataformas en la Nube, UMA). Requisitos como "el sistema debe ser rápido" son ambiguos; estos tres conceptos los convierten en algo medible.

## Definiciones

| Concepto | Qué es | Pregunta que responde | Ejemplo |
| --- | --- | --- | --- |
| **SLI** (*Service Level Indicator*) | Métrica cuantitativa real del servicio en un momento dado. | ¿Cuál es el estado medido del servicio ahora? | La latencia p95 del endpoint es de 145 ms. |
| **SLO** (*Service Level Objective*) | Objetivo o rango asignado a un SLI: el nivel que el equipo considera aceptable sin encarecer la infraestructura sin necesidad. | ¿Qué nivel nos comprometemos a mantener internamente? | El 95 % de las peticiones responde en ≤ 200 ms. |
| **SLA** (*Service Level Agreement*) | Contrato con clientes externos: un SLO más penalizaciones o créditos si se incumple. | ¿Qué pasa si no cumplimos ante el cliente? | 99,9 % de disponibilidad o reembolso del 10 %. |

Dentro de una organización, o entre equipos internos, no se definen SLAs: se trabaja con SLOs. En un documento de diseño van los SLOs.

## Qué se mide

Las dimensiones básicas son disponibilidad, latencia, *throughput* (rendimiento) y tasa de error.

- **Latencia por percentiles, nunca por media.** Una media de 100 ms puede ocultar que el 10 % de los usuarios espera 800 ms. P50 (mediana) refleja al usuario típico; P95 y P99 (*tail latency*) el extremo. En microservicios importan especialmente: una petición del usuario puede desencadenar muchas llamadas internas, y un P99 alto en una de ellas afecta a la respuesta global.
- **Disponibilidad en "nueves".** 99,9 % permite unas 8 h 45 min de caída al año (unos 43 min al mes); 99,99 %, unos 52 min al año (4,3 min al mes). Cada nueve más es un salto exponencial en complejidad.

## Presupuesto de error

`Error budget = 100 % − SLO`. Con un SLO de disponibilidad del 99,9 % en 30 días, el presupuesto es del 0,1 %. No es un fallo, es un margen para desplegar e innovar. Si se agota, se congelan las funcionalidades no críticas y se prioriza la estabilidad.

## SLOs en los documentos de diseño

Los SLOs se fijan en el [documento de diseño](../design-docs/README.md) **antes de escribir código**, junto con la monitorización y las alertas:

1. **Métricas claras**: SLOs de latencia, disponibilidad y escala (p. ej., P50 de las peticiones HTTP de usuario ≤ 200 ms; P50 de las consultas a PostgreSQL ≤ 80 ms).
2. **Monitorización y alertas**: un SLO solo sirve si se mide automáticamente y avisa cuando se rompe o está a punto de romperse.
3. **Umbrales de alerta** sobre lo que nota el usuario, no sobre métricas secundarias aisladas: p. ej., P95 > 3 s durante 2 minutos seguidos dispara una alerta de prioridad alta.

Ejemplo de especificación del material (servicio de caché):

| Tipo | Contenido |
| --- | --- |
| SLOs | Disponibilidad 99,95 % mensual; P95 de lectura en caché ≤ 15 ms; P50 HTTP ≤ 200 ms y P95 ≤ 800 ms. |
| SLIs | `sli_http_latency_p95`, `sli_cache_hit_ratio`, `sli_db_query_latency_p50`. |
| Alertas | Latencia HTTP P95: aviso > 500 ms durante 5 min, crítica ≥ 3 s durante 2 min. CPU de PostgreSQL: aviso ≥ 75 % de media en 10 min, crítica ≥ 90 % en ventana móvil de 2 min. Caché: aviso con tasa de acierto < 80 %, crítica si cae del todo. |

## Qué implica para UrbanPulse

- Antes de transformar la arquitectura (microservicios y demás funcionalidades nuevas) se mide el sistema actual: SLIs con k6 y Micrometer (ver [Práctica de k6 y observabilidad](practica-k6-observabilidad.md)) y, a partir de esa línea base, los SLOs. Así cada cambio posterior se compara con datos, no con impresiones.
- Los SLOs completan las medidas pendientes de los escenarios de calidad (EC01-EC04) de [docs/architecture.md](../architecture.md#escenarios-de-calidad).
- Un umbral solo se fija desde el enunciado, la negociación con el profesor o una medición.

Referencias del material: Michael Lynch, *How to Write an Effective Software Design Document*; Donne Martin, *The System Design Primer*; Alex Xu, *System Design Interview*.
