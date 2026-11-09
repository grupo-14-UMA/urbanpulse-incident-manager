# Cómo contribuir

Este documento describe cómo trabaja el equipo en el repositorio. Para preparar el entorno local, ver [docs/development.md](docs/development.md).

## Roles

Todos los miembros desarrollan. Cada uno tiene una responsabilidad en el equipo y es dueño de un área de desarrollo:

| Rol | Miembro | Responsabilidades |
| --- | --- | --- |
| Team Leader y Product Owner | Alfonso (@alraro) | Redacta y prioriza los issues, organiza las entregas. |
| Reviewer | Andrea (@apr2004) | Revisa los pull requests, sobre todo el código. |
| Tester | María (@mmartinmaria) | Revisa los pull requests, sobre todo que cumplen los criterios de aceptación del issue. |
| Maintainer | Daniel (@daniiroblees) | Configuración del repositorio, etiquetas, CI y releases. |

### Áreas de desarrollo

| Área | Miembro | Etiqueta | Carpeta | Se encarga de |
| --- | --- | --- | --- | --- |
| Core & Security Engineer | Alfonso (@alraro) | `area: ⚙️ core` | `src/backend/` | Cuentas, roles y autenticación; ciclo de vida de la incidencia; logging del backend. |
| Data & Performance Engineer | Andrea (@apr2004) | `area: 🗄️ data` | `src/backend/` | Consultas acotadas (área visible, paginación), índices, dataset sintético y fotos, también en la app móvil. |
| Mobile Engineer | María (@mmartinmaria) | `area: 📱 mobile` | `src/frontend/` | App móvil del Ciudadano y del Técnico, sus tests y su gestión de errores. |
| Ops Panel Engineer | Daniel (@daniiroblees) | `area: 🖥️ panel` | `src/management-panel/` | Panel de gestión del Operador. |

El dueño de un área desarrolla los issues con su etiqueta, revisa los PR que tocan su carpeta y vela por que su parte cumpla los atributos de calidad (consultas acotadas, índices, logs). Cuando su área está bloqueada o al día, ayuda en las demás. Los issues de configuración del repositorio llevan `area: 🔧 repo` y son del Maintainer.

## Flujo de trabajo

1. **Issue**: todo cambio empieza en un issue de GitHub con descripción y criterios de aceptación, creado según [Crear un issue](#crear-un-issue).
2. **Rama**: se crea desde `main` actualizado, con el nombre `<tipo>/<descripcion-corta>` (por ejemplo `feat/incident-map`, `fix/login-timeout`). Una rama por issue.
3. **Commits**: siguen [Conventional Commits](https://www.conventionalcommits.org/) (ver abajo).
4. **Pull request**: se abre contra `main` rellenando la plantilla y enlazando el issue con `Closes #<n>`.
5. **Revisión**: hace falta 1 aprobación. GitHub pide la revisión automáticamente según [`.github/CODEOWNERS`](.github/CODEOWNERS): siempre al Reviewer y al Tester y, si el PR toca su carpeta, también al dueño del área de desarrollo, al Maintainer o al Product Owner. El Reviewer y el Tester revisan los dos el código y los criterios de aceptación del issue, cada uno con más atención a su parte; en un PR sin cambios de comportamiento (documentación, configuración) basta un vistazo. Como siempre se pide a los dos, un PR de uno de ellos lo aprueba el otro. Nunca se hace push directo a `main`.
6. **Merge**: lo hace el autor del PR una vez aprobado.

Los PR se fusionan con *Squash and merge* y la rama se borra después del merge.

## Tablero del proyecto

El tablero del equipo es [Urban Pulse tasks](https://github.com/orgs/grupo-14-UMA/projects/2). Solo contiene issues; las PR enlazadas aparecen dentro de su issue.

| Estado | Cómo llega una issue |
| --- | --- |
| `Todo` | Se añade automáticamente al tablero. |
| `In progress` | La persona que empieza el trabajo mueve la issue aquí. Es el único cambio manual de estado. |
| `In review` | GitHub la mueve al abrir una PR enlazada con `Closes #<n>`. |
| `Done` | GitHub la mueve al cerrar la issue o fusionar la PR enlazada. |

La vista **Mi trabajo** es un tablero por estado, filtrado por `assignee:@me -is:blocked`: muestra las issues asignadas a quien la consulta y que ya se pueden empezar. Incluye las etiquetas, la prioridad, el esfuerzo y las PR enlazadas. La vista **Pendiente** es una tabla global de issues abiertas, agrupadas por milestone, con responsable, estado, prioridad, esfuerzo, etiquetas, milestone e issue padre. Las dos vistas se ordenan por prioridad (de `Urgent` a `Low`) y, a igual prioridad, por esfuerzo (de `Low` a `High`): arriba queda lo más urgente y más sencillo.

Al reabrir una issue, vuelve automáticamente a `Todo`. Las issues cerradas hace más de 14 días se archivan automáticamente; archivar las oculta del tablero, pero no las elimina. Las sub-issues se añaden al tablero automáticamente.

## Convención de commits

Formato `<tipo>: <descripción>`, en imperativo, en minúsculas y sin punto final.

| Tipo | Uso |
| --- | --- |
| `feat` | Nueva funcionalidad |
| `fix` | Corrección de un error |
| `docs` | Documentación |
| `style` | Formato, sin cambios de lógica |
| `refactor` | Reorganización del código sin cambiar su comportamiento |
| `test` | Pruebas |
| `chore` | Mantenimiento |
| `ci` | Integración continua |
| `build` | Sistema de construcción o dependencias |
| `perf` | Rendimiento |

Ejemplos:

```text
feat: add incidents rest api
fix: reject coordinates out of range
docs: update development guide
```

Los títulos de los issues y de los PR siguen la misma convención.

## Crear un issue

Todo issue, lo cree una persona o un agente, sale con todo esto:

- [ ] **Título** con el prefijo de [Conventional Commits](#convención-de-commits) (`feat: …`, `fix: …`, `docs: …`).
- [ ] **Plantilla** de `.github/ISSUE_TEMPLATE/`: `feature_request.md` para `feat:`, `bug_report.md` para `fix:` y `task.md` para el resto.
- [ ] **Tipo** (*issue type*) según el prefijo: `fix:` → `Bug`, `feat:` → `Feature`, cualquier otro → `Task`. La plantilla lo pone sola.
- [ ] **Estado**: `✅ ready` si está especificado con criterios de aceptación; `🔍 triage` si hay que evaluarlo en equipo.
- [ ] **Prioridad**: el campo `Priority` (ver [Prioridad y esfuerzo](#prioridad-y-esfuerzo)).
- [ ] **Esfuerzo**: el campo `Effort`.
- [ ] **Área**: una etiqueta `area: …`, la del área responsable del issue.
- [ ] **Assignee**: el dueño de esa área (ver [Áreas de desarrollo](#áreas-de-desarrollo)).
- [ ] **Milestone** de la entrega actual.
- [ ] **Sub-issue** del epic de la entrega.
- [ ] **Dependencias**: los issues que tienen que cerrarse antes, como *blocked by*.

Los epic llevan `🗺️ epic` y no llevan área, prioridad ni esfuerzo. Los comandos `gh` de cada paso están en [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md).

## Etiquetas

| Etiqueta | Significado |
| --- | --- |
| `🔍 triage` | Hay que evaluarlo en equipo |
| `✅ ready` | Especificado y listo para empezar |
| `❓ question` | Falta información |
| `🚫 wontfix` | No se va a hacer |
| `area: ⚙️ core` | Área Core & Security |
| `area: 🗄️ data` | Área Data & Performance |
| `area: 📱 mobile` | Área App móvil |
| `area: 🖥️ panel` | Área Panel de gestión |
| `area: 🔧 repo` | Configuración del repositorio, CI y releases |
| `🗺️ epic` | Issue de una entrega que agrupa sub-issues |

Cada familia tiene su color, y una etiqueta nueva sigue el de la suya: áreas en tonos pastel distintos entre sí, estados en grises y `🗺️ epic` en negro. El tipo de issue no es una etiqueta: es el *issue type*. La prioridad y el esfuerzo tampoco: son campos del issue.

Cada issue lleva una sola etiqueta `area: …`, la del área dueña, aunque toque otras carpetas. Los epic no llevan etiqueta de área.

Los issues de cada entrega se agrupan en un milestone (por ejemplo "Entrega 1"). El orden entre issues se marca con las dependencias nativas de GitHub (*blocked by*).

## Prioridad y esfuerzo

La prioridad y el esfuerzo son los campos de issue `Priority` y `Effort` de la organización, no etiquetas. Se ponen en la barra lateral del issue; desde la terminal, con los comandos de [docs/agents/issue-tracker.md](docs/agents/issue-tracker.md).

| `Priority` | Significado |
| --- | --- |
| `Urgent` | Bloquea la entrega actual: `main` roto, un plazo de días o algo que frena a otros |
| `High` | Necesario para la entrega actual, va primero |
| `Medium` | Necesario para la entrega actual, no bloquea |
| `Low` | Deseable |

El esfuerzo mide la complejidad del issue, no el tiempo que lleva:

| `Effort` | Significado |
| --- | --- |
| `Low` | Cambio acotado en un solo sitio, sin decisiones de diseño |
| `Medium` | Varias piezas o capas, o una decisión de diseño acotada |
| `High` | Cruza áreas o tiene incertidumbre (migraciones, rendimiento, integraciones nuevas); conviene partirlo o discutirlo antes de empezar |

Los campos `Start date` y `Target date` no se usan: las fechas las marcan los milestones.

## Antes de pedir revisión

- [ ] El proyecto compila y las pruebas pasan.
- [ ] La documentación está actualizada si el cambio lo requiere (README, guías, ADR).
- [ ] No hay credenciales ni datos sensibles.
- [ ] El PR solo contiene cambios del issue enlazado.

## Decisiones de arquitectura

Las decisiones relevantes se recogen como ADR en [docs/adr/](docs/adr/). Ver el [índice y la plantilla](docs/adr/README.md).

## Glosario

Issues, código y conversaciones usan los términos de [GLOSSARY.md](GLOSSARY.md). En el código se usa el término en inglés que aparece entre paréntesis.
