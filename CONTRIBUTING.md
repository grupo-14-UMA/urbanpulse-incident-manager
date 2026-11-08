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
| Core & Security Engineer | Alfonso (@alraro) | `area: core` | `src/backend/` | Cuentas, roles y autenticación; ciclo de vida de la incidencia; logging del backend. |
| Data & Performance Engineer | Andrea (@apr2004) | `area: data` | `src/backend/` | Consultas acotadas (área visible, paginación), índices, dataset sintético y fotos, también en la app móvil. |
| Mobile Engineer | María (@mmartinmaria) | `area: mobile` | `src/frontend/` | App móvil del Ciudadano y del Técnico, sus tests y su gestión de errores. |
| Ops Panel Engineer | Daniel (@daniiroblees) | `area: panel` | `src/management-panel/` | Panel de gestión del Operador. |

El dueño de un área desarrolla los issues con su etiqueta, revisa los PR que tocan su carpeta y vela por que su parte cumpla los atributos de calidad (consultas acotadas, índices, logs). Cuando su área está bloqueada o al día, ayuda en las demás. Los issues de configuración del repositorio llevan `area: repo` y son del Maintainer.

## Flujo de trabajo

1. **Issue**: todo cambio empieza en un issue de GitHub con descripción y criterios de aceptación. Usa las plantillas de `.github/ISSUE_TEMPLATE/`.
2. **Rama**: se crea desde `main` actualizado, con el nombre `<tipo>/<descripcion-corta>` (por ejemplo `feat/incident-map`, `fix/login-timeout`). Una rama por issue.
3. **Commits**: siguen [Conventional Commits](https://www.conventionalcommits.org/) (ver abajo).
4. **Pull request**: se abre contra `main` rellenando la plantilla y enlazando el issue con `Closes #<n>`.
5. **Revisión**: hace falta 1 aprobación. GitHub pide la revisión automáticamente según [`.github/CODEOWNERS`](.github/CODEOWNERS): siempre al Reviewer y al Tester y, si el PR toca su carpeta, también al dueño del área de desarrollo, al Maintainer o al Product Owner. El Reviewer y el Tester revisan los dos el código y los criterios de aceptación del issue, cada uno con más atención a su parte; en un PR sin cambios de comportamiento (documentación, configuración) basta un vistazo. Como siempre se pide a los dos, un PR de uno de ellos lo aprueba el otro. Nunca se hace push directo a `main`.
6. **Merge**: lo hace el autor del PR una vez aprobado.

Los PR se fusionan con *Squash and merge* y la rama se borra después del merge.

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

## Etiquetas

| Etiqueta | Significado |
| --- | --- |
| `triage` | Hay que evaluarlo en equipo |
| `ready` | Especificado y listo para empezar |
| `question` | Falta información |
| `wontfix` | No se va a hacer |
| `priority: high` | Necesario para la entrega actual, va primero |
| `priority: medium` | Necesario para la entrega actual, no bloquea |
| `priority: low` | Deseable |
| `area: core` | Área Core & Security |
| `area: data` | Área Data & Performance |
| `area: mobile` | Área App móvil |
| `area: panel` | Área Panel de gestión |
| `area: repo` | Configuración del repositorio, CI y releases |
| `epic` | Issue de una entrega que agrupa sub-issues |

Cada issue lleva una sola etiqueta `area: …`, la del área dueña, aunque toque otras carpetas. Los epic no llevan etiqueta de área.

Los issues de cada entrega se agrupan en un milestone (por ejemplo "Entrega 1"). El orden entre issues se marca con las dependencias nativas de GitHub (*blocked by*).

## Antes de pedir revisión

- [ ] El proyecto compila y las pruebas pasan.
- [ ] La documentación está actualizada si el cambio lo requiere (README, guías, ADR).
- [ ] No hay credenciales ni datos sensibles.
- [ ] El PR solo contiene cambios del issue enlazado.

## Decisiones de arquitectura

Las decisiones relevantes se recogen como ADR en [docs/adr/](docs/adr/). Ver el [índice y la plantilla](docs/adr/README.md).

## Glosario

Issues, código y conversaciones usan los términos de [GLOSSARY.md](GLOSSARY.md). En el código se usa el término en inglés que aparece entre paréntesis.
