# Práctica de k6 y observabilidad

Resumen de la "Práctica sobre atributos de calidad y observabilidad" y del "Tutorial práctico de Grafana k6" (Desarrollo Software en Plataformas en la Nube, UMA). La práctica pide medir el sistema con pruebas de carga y observabilidad para evaluar sus atributos de calidad. Los [SLIs y SLOs](slis-slos-slas.md) se fijan a partir de estas mediciones.

## Experimentos que hay que hacer

| Nº | Experimento | Qué se hace | Objetivo |
| --- | --- | --- | --- |
| 1 | Caracterización del rendimiento de la API | Medir la latencia y el *throughput* de distintos endpoints con una carga pequeña y estable. Condiciones propuestas: 10 VUs, 2 minutos por operación, base de datos precargada y la misma infraestructura en todas las ejecuciones. | Línea base. |
| 2 | Escalabilidad y saturación | Aumentar la carga progresivamente (por ejemplo 10, 25, 50 y 100 VUs, ajustados al hardware) hasta que el sistema deja de cumplir sus objetivos. Añadir un experimento con `constant-arrival-rate` para estimar la capacidad en peticiones por segundo y explorar `ramping-arrival-rate`. | Identificar los límites de capacidad y estudiar la degradación progresiva; distinguir concurrencia, capacidad y escalabilidad. |
| 3 | Pico de incidencias urbanas | Simular un evento extraordinario en Málaga, como lluvias intensas: muchos ciudadanos notifican a la vez alcantarillas obstruidas, inundaciones y problemas de tráfico. | Evaluar la tolerancia a picos: si aparecen errores, cuánto sube la latencia y si el servicio se recupera al terminar el pico. |
| 4 | Influencia del volumen de datos | Repetir las mismas consultas con distintos tamaños de base de datos (por ejemplo 1.000, 10.000, 100.000 y 1.000.000 de incidencias). Estudiar consultas filtradas, paginación, ordenación por fecha y estadísticas agregadas. Añadir un índice sobre los campos más filtrados y repetir. | Relacionar la calidad arquitectónica con el diseño de datos. |

Preguntas que hay que responder:

- **Experimento 1**: rellenar por operación una tabla `Operación/endpoint · p50 · p95 · p99 · peticiones/s · errores`. ¿Qué endpoint tiene mayor latencia? ¿La operación más lenta es también la de menor *throughput*? ¿Hay diferencias importantes entre p50 y p99? ¿Qué operaciones merecen una optimización prioritaria?
- **Experimento 2**: ¿en qué punto empieza a degradarse la API y cuándo incumple sus requisitos?
- **Experimento 3** (pregunta arquitectónica): ¿qué ocurre con la creación de incidencias si a la vez muchos usuarios consultan el mapa? Sirve para estudiar la competencia por conexiones, CPU y acceso a PostgreSQL.
- **Experimento 4**: ¿mejorar el esquema de PostgreSQL permite aumentar la capacidad sin cambiar el número de instancias de Spring Boot?

## Método

1. Datos representativos y entorno estabilizado antes de medir.
2. Cada configuración se repite **al menos tres veces**.
3. Se representa la variabilidad, no un único resultado.
4. Se analiza la métrica `dropped_iterations` en cada experimento y se explica qué indica.

## k6

k6 ejecuta scripts JavaScript con **usuarios virtuales** (VUs). Cada VU repite una **iteración** (la función `default`), que puede hacer varias peticiones. Un VU no es una persona con navegador, sino una unidad de ejecución que simula un comportamiento programado.

### Tipos de prueba

| Tipo | Objetivo | Carga | Duración | Atributo | Cuándo |
| --- | --- | --- | --- | --- | --- |
| Smoke | Comprobar que responde | Muy baja | Segundos | Fiabilidad | Tras desplegar o antes de una batería de pruebas |
| Load | Comportamiento con la carga prevista | Normal | Minutos | Rendimiento | Verificar los requisitos no funcionales |
| Stress | Encontrar los límites | Creciente, por encima de lo previsto | Minutos | Rendimiento, fiabilidad | Antes de producción o tras cambios arquitectónicos |
| Spike | Respuesta a picos bruscos | Picos repentinos | Minutos | Resiliencia | Ráfagas de tráfico esperadas |
| Soak | Problemas que no salen en pruebas cortas (memoria, agotamiento de conexiones) | Sostenida | Prolongada | Fiabilidad | — |

### Métricas de k6

`http_req_duration` (duración HTTP: envío, espera y recepción), `http_req_failed` (tasa de peticiones fallidas), `http_reqs` (número y tasa de peticiones), `iteration_duration`, `iterations`, `vus`, `vus_max`, `checks` (proporción de comprobaciones superadas) y `dropped_iterations`.

### Cómo se configura la carga

- `vus` + `duration`: carga fija (`sleep(1)` simula el tiempo de espera del usuario).
- `ramping-vus` con `stages`: la carga sube o baja por etapas dentro de una misma ejecución (`{ duration: '30s', target: 10 }` lleva a 10 VUs en 30 s).
- `constant-arrival-rate`: inicia un número fijo de iteraciones por unidad de tiempo, independientemente de lo que tarden (`rate`, `timeUnit`, `duration`, `preAllocatedVUs`, `maxVUs`). Responde a "¿aguanta la API 100 consultas por segundo?". Si no hay VUs libres para mantener la tasa, las iteraciones que no se pueden iniciar se cuentan en `dropped_iterations`. Una iteración puede tener varias peticiones, así que 100 iteraciones/s no equivalen siempre a 100 peticiones/s.
- `ramping-arrival-rate`: como la anterior, pero con una tasa que cambia por etapas.
- Para que los datos varíen entre peticiones se usan `__VU` (identificador del VU) y `__ITER` (iteración de ese VU); si todos los VUs crean la misma incidencia, las reglas de negocio pueden rechazar peticiones.

### Requisitos de calidad como *thresholds*

Un requisito como "la consulta de incidencias responde en menos de 500 ms para el 95 % de las peticiones, con menos de un 1 % de errores, con 50 VUs" tiene operación, condición, métrica y criterio. Los *thresholds* lo convierten en éxito o fracaso de la prueba, y si uno se incumple k6 termina con un código de error:

```js
thresholds: {
  http_req_duration: ['p(95)<500'],
  http_req_failed: ['rate<0.01'],
  checks: ['rate>0.99'],
  dropped_iterations: ['count==0'], // con ejecutores de tasa
}
```

### Visualizar resultados

- Dashboard web integrado: `K6_WEB_DASHBOARD=true k6 run script.js` y abrir `http://localhost:5665`. `K6_WEB_DASHBOARD_EXPORT=report.html` guarda un informe HTML al terminar.
- Para guardarlas junto a las de la aplicación, k6 puede enviar sus métricas a Prometheus con la salida *Prometheus Remote Write*, que necesita un receptor configurado.

## Observabilidad de la aplicación

La idea es combinar dos fuentes en Grafana:

- **Desde k6** (vista del cliente): peticiones por segundo, latencia, errores y usuarios virtuales.
- **Desde la aplicación y la infraestructura** (qué pasa dentro): CPU y memoria, hilos activos, conexiones a la base de datos, métricas de la JVM, consultas y operaciones del backend.

### Spring Boot Actuator y Micrometer

- **Actuator** es el módulo de Spring Boot que expone endpoints operativos del backend: `health`, `info`, `metrics` y, con el registro de Prometheus, `prometheus`.
- **Micrometer** es la biblioteca de instrumentación de Java que usa Spring Boot: recoge métricas de la aplicación y las publica en el formato de cada sistema de monitorización, como Prometheus. Es a las métricas lo que SLF4J es a los logs. Sus instrumentos básicos son el **Counter** (cuántas veces ocurre algo: incidencias creadas, errores de negocio), el **Gauge** (un valor que sube y baja: conexiones activas, tareas pendientes) y el **Timer** (duración y frecuencia de una operación). También da métricas de distribución y, con Micrometer Observation y Micrometer Tracing, trazas.
- Pasos del tutorial: dependencias `spring-boot-starter-actuator` y `micrometer-registry-prometheus`; `management.endpoints.web.exposure.include=health,info,metrics,prometheus` y `management.endpoint.health.show-details=never`; Prometheus lee `/actuator/prometheus`, con series como `jvm_memory_used_bytes`, `process_cpu_usage`, `hikaricp_connections_active` y `hikaricp_connections_pending`; métricas de negocio propias con `Counter.builder("incidents.created")`, que se incrementa solo después de guardar la incidencia.
- Equivalentes en otros lenguajes: `prom-client` u OpenTelemetry JS en Node.js; `prometheus-client` u OpenTelemetry Python; `System.Diagnostics.Metrics` en .NET.

### Diagnóstico combinando k6 y Micrometer

| Fuente | Métrica | Inicio | Bajo carga |
| --- | --- | --- | --- |
| k6 | Latencia p95 | 120 ms | 1.200 ms |
| k6 | Peticiones fallidas | 0 % | 4 % |
| Micrometer | Uso de CPU | 25 % | 42 % |
| Micrometer | Conexiones activas a PostgreSQL | 3/10 | 10/10 |
| Micrometer | Conexiones pendientes | 0 | 18 |

k6 dice que la aplicación se ralentiza y falla; Micrometer, que la CPU no está saturada pero el pool de conexiones está lleno y hay peticiones esperando. Hipótesis: el cuello de botella está en el acceso a PostgreSQL (consultas lentas o conexiones retenidas demasiado tiempo).

## Referencias

- k6: [primeros pasos](https://grafana.com/docs/k6/latest/examples/get-started-with-k6/), [escenarios](https://grafana.com/docs/k6/latest/using-k6/scenarios/), [thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/), [asignación de VUs con tasa de llegada](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/arrival-rate-vu-allocation/), [dashboard web](https://grafana.com/docs/k6/latest/results-output/web-dashboard/), [Prometheus Remote Write](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/).
- [Micrometer](https://docs.micrometer.io/micrometer/reference/concepts.html), [Spring Boot Actuator: métricas](https://docs.spring.io/spring-boot/reference/actuator/metrics.html), [Prometheus](https://prometheus.io/docs/introduction/overview/), [Grafana](https://grafana.com/docs/grafana/latest/), [OpenTelemetry](https://opentelemetry.io/docs/).
