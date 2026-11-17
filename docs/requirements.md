# Requisitos

Requisitos iniciales de UrbanPulse tomados del [enunciado del proyecto](enunciado.pdf) ("UrbanPulse: Plataforma cloud para la gestión inteligente de incidencias urbanas", Universidad de Málaga).

## Pautas de diseño

- La incidencia es la entidad central del dominio y conserva un ciclo de vida auditable.
- Los datos externos enriquecen; nunca bloquean la creación del reporte si una fuente no está disponible.
- Los atributos de calidad (rendimiento, escalabilidad, fiabilidad, disponibilidad y observabilidad) guían el diseño. Cada funcionalidad debe seguir siendo fluida y útil con millones de incidencias: muestra lo relevante para el contexto (zona, filtros, página) y no todo el conjunto.
- Se adopta la solución más sencilla que cumpla esos atributos. Cada mejora (índices, caché, colas, eventos, separación en servicios) se justifica con una medición frente a una línea base, obtenida con datos sintéticos, pruebas de carga y observabilidad.
- La línea base se obtiene antes de transformar la arquitectura: primero se mide el sistema actual (SLIs) y se fijan los SLOs ([SLIs, SLOs y SLAs](course/slis-slos-slas.md)); después se separan servicios y se añaden las funcionalidades de la segunda fase, comparándolas con esas mediciones. Es una indicación del profesor.
- Cada funcionalidad de la segunda fase se diseña en un [documento de diseño](design-docs/README.md) que el equipo decide y el profesor valida antes de implementarla.
- El ciudadano y el técnico usan la aplicación móvil; el operador, el administrador y el analista usan el panel de gestión web ([ADR-0014](adr/0014-aplicacion-movil-y-panel-de-gestion-web.md)). Esta separación es una indicación del profesor y no aparece en el enunciado.
- Cada cambio relevante actualiza el modelo C4, un ADR y las pruebas asociadas.

## Actores

| Actor | Responsabilidades | Necesidades | Cliente |
| --- | --- | --- | --- |
| Ciudadano | Registra incidencias, aporta ubicación y evidencias, consulta el estado. | Interfaz sencilla, confirmación, privacidad y seguimiento. | Aplicación móvil |
| Operador municipal | Valida, rechaza, clasifica, prioriza y asigna. | Contexto fiable, historial, búsqueda y trazabilidad. | Panel de gestión |
| Técnico | Acepta trabajos, actualiza el progreso y documenta la resolución. | Cola de trabajo y seguimiento del progreso. | Aplicación móvil |
| Administrador | Gestiona usuarios, roles, categorías, departamentos y fuentes. | Configuración segura, auditoría y control de acceso. | Panel de gestión |
| Analista (opcional) | Explora indicadores, patrones espaciales y resultados de modelos. | Datos consistentes, exportación, métricas y explicabilidad. | Panel de gestión |
| Auditor | Analiza el registro de auditoría: estados de cada incidencia, cambios y quién los hizo ([RF31](#funcionalidades-de-la-segunda-fase)). | Registro completo e inalterable, filtros y análisis. | Panel de gestión |
| Sistema externo | Proporciona datos urbanos, meteorológicos, territoriales o documentales. | | |

El enunciado marca como opcionales el actor Analista, sus casos de uso de entrenamiento de modelos y de consulta mediante RAG, y la entidad `KnowledgeDocument`.

## Requisitos funcionales

| ID | Requisito | Definición |
| --- | --- | --- |
| RF01 | Identidad y acceso | El sistema permitirá registro o acceso de usuarios y autenticación de personal autorizado. |
| RF02 | Roles y permisos | El sistema aplicará permisos diferenciados para ciudadanía, operadores, técnicos, administración y analistas. |
| RF03 | Registrar incidencia | El ciudadano podrá registrar una incidencia con título, descripción, posición y categoría opcional. |
| RF04 | Localización | La incidencia conservará coordenadas, dirección normalizada y precisión de la localización. |
| RF05 | Evidencias | Se podrán adjuntar fotografías u otros ficheros con validación de tipo y tamaño. |
| RF06 | Consulta y seguimiento | El usuario podrá consultar detalle, estado, fechas e historial visible de sus incidencias. |
| RF07 | Búsqueda | El personal podrá buscar y filtrar por estado, categoría, fecha, prioridad, distrito y departamento. |
| RF08 | Mapa | El sistema mostrará incidencias y capas urbanas relevantes en un mapa. |
| RF09 | Validación | Un operador podrá validar, solicitar información o rechazar un reporte dejando motivo. |
| RF10 | Prioridad | El sistema permitirá establecer prioridad manual o asistida y conservar su justificación. |
| RF11 | Asignación | Una incidencia validada podrá asignarse a un departamento, equipo o técnico. |
| RF12 | Ciclo de vida | Los cambios de estado respetarán transiciones permitidas y quedarán auditados. |
| RF13 | Colaboración | Operadores y técnicos podrán añadir comentarios, evidencias y datos de resolución. |
| RF14 | Notificaciones | El sistema notificará cambios relevantes mediante canales configurables. |
| RF15 | Estadísticas | Se calcularán indicadores por categoría, territorio, estado y tiempo de resolución. |
| RF16 | Duplicados | El sistema podrá sugerir incidencias similares por proximidad, tiempo y semántica. |
| RF17 | Análisis zonal | Se podrán consultar incidencias y contexto agregado para una zona y periodo. |
| RF18 | Auditoría | Se registrarán acciones administrativas, decisiones asistidas y cambios relevantes. |
| RF19 | Informes | El sistema generará informes operativos y territoriales exportables. |
| RF20 | Asistencia inteligente | La plataforma podrá clasificar, priorizar, resumir y recomendar con indicación de confianza. |
| RF21 | Barrio y distrito | El sistema identificará barrio y distrito a partir de la localización. |
| RF22 | Activo urbano | El sistema podrá asociar una incidencia a un activo urbano próximo. |
| RF23 | Contexto externo | El sistema podrá obtener información contextual de fuentes externas. |
| RF24 | Vista contextual | El operador podrá visualizar el contexto urbano asociado. |
| RF25 | Correlación histórica | El sistema podrá correlacionar incidencias con variables urbanas históricas. |
| RF26 | Degradación controlada | La falta de disponibilidad de una fuente externa no impedirá crear la incidencia. |

## Funcionalidades de la segunda fase

El profesor añadió estos requisitos de finalización después de la Entrega 1; no están en el enunciado. Cada funcionalidad se plasma primero en un [documento de diseño](design-docs/README.md), que el profesor (el cliente) tiene que validar, y no se implementa hasta entonces.

| ID | Funcionalidad | Definición | Relación |
| --- | --- | --- | --- |
| RF27 | Dashboard operativo | Los operadores municipales y los técnicos disponen de un dashboard con, al menos, un mapa de las incidencias, indicadores generales con datos y filtros de incidencias. | RF07, RF08, RF15 |
| RF28 | Imágenes en un servicio externo | Las imágenes de las incidencias se suben a un servicio externo; el profesor pone Cloudinary como ejemplo. | RF05, [ADR-0018](adr/0018-almacenamiento-de-fotos-en-volumen-detras-de-interfaz.md) |
| RF29 | Incidencia normalizada | Los reportes de una misma incidencia enviados por distintos ciudadanos se unifican en una única incidencia normalizada, con trazabilidad entre cada reporte y su incidencia. | RF16 |
| RF30 | Microservicio de contexto | Un microservicio con base de datos propia extrae, normaliza y gestiona datos externos que dan contexto a las incidencias: los conjuntos de datos abiertos que se seleccionen y la información meteorológica. La extracción se actualiza periódicamente. El profesor nombró AEMET como fuente meteorológica y aceptó mantener OpenWeatherMap ([ADR-0009](adr/0009-openweathermap-para-meteorologia.md)), que da datos para la coordenada exacta de la incidencia. | RF21-RF25, [ADR-0008](adr/0008-datos-abiertos-del-ayuntamiento-de-malaga.md), [ADR-0009](adr/0009-openweathermap-para-meteorologia.md) |
| RF31 | Microservicio de auditoría | Un microservicio con base de datos propia registra todos los estados por los que pasa una incidencia, los cambios realizados y qué usuario los hizo, e incluye un dashboard para analizar esa información. Crea el rol Auditor, que tiene acceso a ella junto con el Administrador. | RF12, RF18 |

Requisitos adicionales, que el profesor irá completando a lo largo de la asignatura:

| ID | Requisito | Definición |
| --- | --- | --- |
| RA01 | Paginación | Todos los servicios REST de lectura masiva tienen paginación. |
| RA02 | UUID de la incidencia | La incidencia tiene un UUID que permite seguirla entre los distintos microservicios. |
| RA03 | Fiabilidad y resiliencia | Se diseñan estrategias para cuando un componente (servicio externo, microservicio…) no está disponible durante la ejecución. |
| RA04 | Observabilidad | Un entorno de observabilidad que combina k6, Micrometer, logs estructurados, Prometheus, Grafana, etc. permite evaluar los atributos de calidad. Los experimentos que pide la práctica están en [Práctica de k6 y observabilidad](course/practica-k6-observabilidad.md). |
| RA05 | Documento de diseño | Las propuestas de diseño de estas soluciones se plasman en un documento de diseño validado por el cliente. |

El [ADR-0006](adr/0006-arquitectura-monolitica-inicial.md) (monolito) choca con RF30 y RF31: el documento de diseño que los aborde tiene que revisarlo.

## Historias de usuario

El enunciado no trae historias de usuario redactadas. Estas se derivan de sus casos de uso y de los actores, y cada una indica los requisitos que cubre.

### Ciudadano

| ID | Historia | RF |
| --- | --- | --- |
| HU01 | Como ciudadano, quiero registrarme e iniciar sesión para poder reportar incidencias y seguirlas. | RF01 |
| HU02 | Como ciudadano, quiero registrar una incidencia con título, descripción, mi posición y, si la sé, una categoría, para avisar al Ayuntamiento de un problema. | RF03, RF04 |
| HU03 | Como ciudadano, quiero adjuntar fotografías o vídeos a la incidencia para que se entienda mejor el problema. | RF05 |
| HU04 | Como ciudadano, quiero ver si ya existen avisos similares cerca para aportar información a uno existente en lugar de duplicarlo. | RF16 |
| HU05 | Como ciudadano, quiero poder enviar mi incidencia aunque falle una fuente de datos externa, para no perder el aviso. | RF26 |
| HU06 | Como ciudadano, quiero consultar el estado, las fechas y el historial de mis incidencias para saber cómo avanzan. | RF06 |
| HU07 | Como ciudadano, quiero recibir una notificación cuando mi incidencia cambie de estado para no tener que consultarla constantemente. | RF14 |

### Operador municipal

| ID | Historia | RF |
| --- | --- | --- |
| HU08 | Como operador, quiero buscar y filtrar incidencias por estado, categoría, fecha, prioridad, distrito y departamento para encontrar las que debo atender. | RF07 |
| HU09 | Como operador, quiero ver las incidencias y las capas urbanas en un mapa para entender su distribución en la ciudad. | RF08 |
| HU10 | Como operador, quiero validar, pedir más información o rechazar un reporte indicando el motivo, para que solo avancen los avisos válidos. | RF09, RF12 |
| HU11 | Como operador, quiero establecer la prioridad de forma manual o a partir de una sugerencia, dejando su justificación, para atender primero lo urgente. | RF10, RF20 |
| HU12 | Como operador, quiero asignar una incidencia validada a un departamento, equipo o técnico para que alguien se encargue de resolverla. | RF11 |
| HU13 | Como operador, quiero ver el contexto urbano de la incidencia (dirección, barrio, distrito, activo afectado, tiempo, tráfico) para decidir con información fiable. | RF21, RF22, RF23, RF24 |

### Técnico

| ID | Historia | RF |
| --- | --- | --- |
| HU14 | Como técnico, quiero ver mi cola de trabajos asignados y aceptarlos para organizar mi jornada. | RF11, RF12 |
| HU15 | Como técnico, quiero actualizar el progreso y añadir comentarios, evidencias y datos de resolución para documentar la actuación. | RF12, RF13 |

### Administrador

| ID | Historia | RF |
| --- | --- | --- |
| HU16 | Como administrador, quiero gestionar usuarios y roles para controlar quién puede hacer qué. | RF02 |
| HU17 | Como administrador, quiero gestionar categorías, departamentos y fuentes externas para adaptar la plataforma a la organización municipal. | RF02, RF23 |
| HU18 | Como administrador, quiero consultar el registro de auditoría para revisar las acciones administrativas y las decisiones asistidas. | RF18 |

### Analista

Opcional según el enunciado.

| ID | Historia | RF |
| --- | --- | --- |
| HU19 | Como analista, quiero consultar indicadores por categoría, territorio, estado y tiempo de resolución para evaluar el servicio. | RF15 |
| HU20 | Como analista, quiero consultar las incidencias y el contexto agregado de una zona y un periodo para detectar patrones espaciales. | RF17 |
| HU21 | Como analista, quiero exportar informes operativos y territoriales para compartirlos. | RF19 |
| HU22 | Como analista, quiero correlacionar incidencias con variables urbanas históricas para entender sus causas. | RF25 |
| HU23 | Como analista, quiero entrenar y evaluar modelos de clasificación, priorización y detección de hotspots para mejorar la asistencia inteligente. | RF20 |
| HU24 | Como analista, quiero consultar procedimientos y normativa mediante RAG y recibir recomendaciones con citas a la evidencia recuperada. | RF20 |
