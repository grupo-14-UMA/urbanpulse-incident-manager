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
