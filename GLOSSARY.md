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
Fase del ciclo de vida en la que se encuentra una incidencia. Toda incidencia empieza en **Registrada** (`REPORTED`), el estado que tiene en cuanto el ciudadano la registra.
_Evitar_: situación, fase

**Fecha de registro** (`createdAt`):
Instante en que el ciudadano registra una incidencia, el mismo en el que pasa a **Registrada**. Se refiere al mismo momento para cualquier cliente, esté donde esté.
_Evitar_: fecha de creación, fecha de alta

**Categoría** (`Category`):
Tipo de problema al que pertenece una incidencia. Es opcional cuando el ciudadano la registra.
_Evitar_: tipo, clase

### Actores

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
