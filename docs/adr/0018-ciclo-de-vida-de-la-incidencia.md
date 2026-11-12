---
status: accepted
---

# ADR-0018: Ciclo de vida de la incidencia

## Contexto

Hasta ahora toda incidencia se quedaba en `REPORTED` para siempre: el ciudadano no sabía si alguien la había mirado, el operador no podía aceptarla, descartarla ni priorizarla, y el técnico no podía indicar que había empezado o terminado el trabajo. Nadie podía saber quién cambió qué y cuándo. Los requisitos RF09 a RF12 piden un ciclo de vida con estados, prioridad, asignación a un técnico y trazabilidad de los cambios. Es la regla central del dominio y es difícil de cambiar después.

## Opciones consideradas

1. Un endpoint por acción (`validate`, `reject`, `assign`, `start`, `resolve`) frente a un único `PATCH` genérico del estado.
2. Estado como tabla de consulta (`Status`) con clave foránea frente a una cadena libre.

## Decisión

Un endpoint por acción y estado en una tabla `Status` referenciada por clave foránea. Cada acción tiene un rol, un estado de origen y uno de destino, y la regla vive en `Incident`, no en el controlador:

```text
REPORTED ──validate──▶ VALIDATED ──assign──▶ ASSIGNED ──start──▶ IN_PROGRESS ──resolve──▶ RESOLVED
    └─────reject─────▶ REJECTED
```

- **Operador**: valida, rechaza (con motivo obligatorio), asigna una incidencia validada a un técnico y cambia la prioridad.
- **Técnico**: empieza y resuelve (con nota de resolución obligatoria) solo las incidencias que tiene asignadas.
- `REJECTED` y `RESOLVED` son estados finales. No hay reasignación: solo se asigna desde `VALIDATED`.
- La prioridad (`LOW`, `MEDIUM`, `HIGH`, `URGENT`) es manual y opcional. Se puede fijar al validar y cambiar mientras la incidencia está `VALIDATED`, `ASSIGNED` o `IN_PROGRESS`; la justificación vigente sustituye a la anterior.
- Una acción que el estado actual no permite devuelve 409; una acción de otro rol, 403. Se comprueba primero el rol, después el cuerpo, la existencia, la pertenencia (técnico asignado o ciudadano que la registró) y por último el estado.
- Cada cambio de estado se guarda en `StatusChange` (estado anterior y nuevo, quién, cuándo y motivo o nota), solo para añadir: nunca se modifica ni se borra. El cambio de prioridad no se audita. La primera entrada es el registro por el ciudadano.
- El historial lo leen los operadores, el técnico asignado y el ciudadano que la registró. Muestra el rol de quien cambió el estado, nunca su nombre ni su identificador.
- Dos cambios simultáneos sobre una misma incidencia se resuelven con bloqueo optimista (`version`): el segundo recibe 409.
- El mapa muestra las incidencias abiertas (`REPORTED`, `VALIDATED`, `ASSIGNED`, `IN_PROGRESS`); las rechazadas y resueltas salen de él.

## Consecuencias

- \+ El ciclo de vida no se puede saltar: ningún código modifica el estado sin pasar por las reglas de `Incident`, y la base de datos rechaza estados desconocidos.
- \+ Cada acción tiene un contrato, un permiso y una documentación OpenAPI propios.
- \+ Hay trazabilidad completa de los cambios de estado sin exponer al personal municipal.
- \− Añadir un estado o una transición exige una migración y cambios en el código.
- \− Los cambios de prioridad y sus justificaciones anteriores no quedan en el historial; se podría añadir en el futuro.
