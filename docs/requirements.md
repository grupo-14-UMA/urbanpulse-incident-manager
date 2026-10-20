# Requisitos

Requisitos iniciales de UrbanPulse tomados del enunciado del proyecto ("UrbanPulse: Plataforma cloud para la gestión inteligente de incidencias urbanas", Universidad de Málaga).

## Pautas de diseño

- La incidencia es la entidad central del dominio y conserva un ciclo de vida auditable.
- Los datos externos enriquecen; nunca bloquean la creación del reporte si una fuente no está disponible.
- Se adopta la solución más sencilla que satisfaga los atributos de calidad medidos.
- Cada cambio relevante actualiza el modelo C4, un ADR y las pruebas asociadas.

## Actores

| Actor | Responsabilidades | Necesidades |
| --- | --- | --- |
| Ciudadano | Registra incidencias, aporta ubicación y evidencias, consulta el estado. | Interfaz sencilla, confirmación, privacidad y seguimiento. |
| Operador municipal | Valida, rechaza, clasifica, prioriza y asigna. | Contexto fiable, historial, búsqueda y trazabilidad. |
| Técnico | Acepta trabajos, actualiza el progreso y documenta la resolución. | Cola de trabajo y seguimiento del progreso. |
| Administrador | Gestiona usuarios, roles, categorías, departamentos y fuentes. | Configuración segura, auditoría y control de acceso. |
| Analista | Explora indicadores, patrones espaciales y resultados de modelos. | Datos consistentes, exportación, métricas y explicabilidad. |
| Sistema externo | Proporciona datos urbanos, meteorológicos, territoriales o documentales. | |

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

| ID | Historia | RF |
| --- | --- | --- |
| HU19 | Como analista, quiero consultar indicadores por categoría, territorio, estado y tiempo de resolución para evaluar el servicio. | RF15 |
| HU20 | Como analista, quiero consultar las incidencias y el contexto agregado de una zona y un periodo para detectar patrones espaciales. | RF17 |
| HU21 | Como analista, quiero exportar informes operativos y territoriales para compartirlos. | RF19 |
| HU22 | Como analista, quiero correlacionar incidencias con variables urbanas históricas para entender sus causas. | RF25 |
| HU23 | Como analista, quiero entrenar y evaluar modelos de clasificación, priorización y detección de hotspots para mejorar la asistencia inteligente. | RF20 |
| HU24 | Como analista, quiero consultar procedimientos y normativa mediante RAG y recibir recomendaciones con citas a la evidencia recuperada. | RF20 |
