# UrbanPulse

Plataforma para comunicar, gestionar y analizar incidencias urbanas en Málaga, enriquecidas con datos de contexto de la ciudad.

## Lenguaje

En el código se usa el término en inglés que aparece entre paréntesis.

### Incidencias

**Incidencia** (`Incident`):
Aviso de un problema en la vía o el espacio público, con descripción, localización y estado. Es la entidad central del dominio.
_Evitar_: reporte, aviso, ticket

**Localización** (`Location`):
Punto del mapa donde está el problema de una incidencia, expresado en coordenadas. Es distinta de la posición del ciudadano, que como mucho sirve para proponerla.
_Evitar_: posición, ubicación, punto

**Estado** (`Status`):
Fase del ciclo de vida en la que se encuentra una incidencia ([ADR-0019](docs/adr/0019-ciclo-de-vida-de-la-incidencia.md)). Toda incidencia empieza en **Registrada** (`REPORTED`), el estado que tiene en cuanto el ciudadano la registra. Después puede ser **Validada** (`VALIDATED`, un operador comprobó que es un problema real), **Rechazada** (`REJECTED`, un operador la descartó con un motivo; es final), **Asignada** (`ASSIGNED`, un operador la encargó a un técnico), **En curso** (`IN_PROGRESS`, el técnico asignado empezó el trabajo) o **Resuelta** (`RESOLVED`, el técnico asignado la terminó con una nota; es final).
_Evitar_: situación, fase

**Prioridad** (`Priority`):
Urgencia con la que hay que atender una incidencia: Baja (`LOW`), Media (`MEDIUM`), Alta (`HIGH`) o Urgente (`URGENT`). La fija el operador, de forma manual y opcional, junto con una **Justificación** también opcional que explica el motivo. Una incidencia sin prioridad está «sin prioridad».
_Evitar_: urgencia, importancia

**Asignación** (`Assignment`):
Técnico responsable de una incidencia, elegido por un operador. Una incidencia asignada no se reasigna.
_Evitar_: encargo, derivación

**Historial de estados** (`StatusChange`):
Registro de cada cambio de estado de una incidencia: de qué estado a cuál, quién (por su rol, nunca por su nombre ante el ciudadano), cuándo y con qué motivo o nota. La primera entrada es el registro de la incidencia por el ciudadano.
_Evitar_: log, auditoría

**Motivo de rechazo**:
Explicación obligatoria que escribe el operador al rechazar una incidencia, para que el ciudadano sepa por qué se descartó.

**Nota de resolución**:
Explicación obligatoria que escribe el técnico al resolver una incidencia y que documenta el trabajo hecho.

**Fecha de registro** (`createdAt`):
Instante en que el ciudadano registra una incidencia, el mismo en el que pasa a **Registrada**. Se refiere al mismo momento para cualquier cliente, esté donde esté.
_Evitar_: fecha de creación, fecha de alta

**Categoría** (`Category`):
Tipo de problema al que pertenece una incidencia. Es opcional cuando el ciudadano la registra.
_Evitar_: tipo, clase

**Fotografía adjunta** (`Attachment`):
Imagen asociada a una incidencia. La API valida el fichero, guarda su contenido en el almacenamiento de fotos y conserva sus metadatos y referencia para mostrarla después.
_Evitar_: archivo (sin más), imagen (sin contexto)

### Actores

**Usuario** (`User`):
Persona con cuenta en UrbanPulse. Tiene un único rol, que decide qué puede hacer.
_Evitar_: cuenta, perfil

**Rol** (`Role`):
Papel que desempeña un usuario en UrbanPulse: ciudadano, operador, técnico, administrador o analista.
_Evitar_: perfil, tipo de usuario

**Ciudadano** (`Citizen`):
Persona que registra incidencias y sigue su estado.
_Evitar_: vecino, cliente, usuario (sin más)

**Operador** (`Operator`):
Personal municipal que valida, rechaza, prioriza y asigna incidencias.
_Evitar_: gestor, moderador

**Técnico** (`Technician`):
Personal que ejecuta la actuación sobre una incidencia asignada y documenta su resolución.
_Evitar_: operario, trabajador

**Administrador** (`Administrator`):
Personal municipal que gestiona usuarios, roles, categorías, departamentos y fuentes externas.
_Evitar_: admin, superusuario

**Analista** (`Analyst`):
Personal municipal que estudia indicadores y patrones de las incidencias en la ciudad.
_Evitar_: analizador

### Clientes

**Aplicación móvil** (`mobile`):
Cliente que usan el ciudadano y el técnico, pensado para usarse en la calle.
_Evitar_: frontend, app (sin más)

**Panel de gestión** (`web`):
Cliente web que usan el operador, el administrador y el analista.
_Evitar_: dashboard, backoffice, portal, frontend
