# Cómo contribuir

Este documento describe cómo trabaja el equipo en el repositorio. Para preparar el entorno local, ver [docs/development.md](docs/development.md).

## Roles

Todos los miembros desarrollan. Además, cada uno tiene una responsabilidad:

| Rol | Miembro | Responsabilidades |
| --- | --- | --- |
| Team Leader y Product Owner | Alfonso (@alraro) | Redacta y prioriza los issues, organiza las entregas. |
| Reviewer | Andrea (@apr2004) | Revisa los pull requests. |
| Tester | María (@mmartinmaria) | Comprueba los criterios de aceptación antes de cerrar un issue. |
| Maintainer | Daniel (@daniiroblees) | Configuración del repositorio, etiquetas, CI y releases. |

<!-- TODO: detallar responsabilidades si cambian o se reparten de otra forma. -->

## Flujo de trabajo

1. **Issue**: todo cambio empieza en un issue de GitHub con descripción y criterios de aceptación. Usa las plantillas de `.github/ISSUE_TEMPLATE/`.
2. **Rama**: se crea desde `main` actualizado, con el nombre `<tipo>/<descripcion-corta>` (por ejemplo `feat/incident-map`, `fix/login-timeout`). Una rama por issue.
3. **Commits**: siguen [Conventional Commits](https://www.conventionalcommits.org/) (ver abajo).
4. **Pull request**: se abre contra `main` rellenando la plantilla y enlazando el issue con `Closes #<n>`.
5. **Revisión**: hace falta 1 aprobación. Nunca se hace push directo a `main`.
6. **Merge**: lo hace el autor del PR una vez aprobado.
7. **Verificación**: el Tester comprueba los criterios de aceptación del issue.

<!-- TODO: indicar el tipo de merge (squash, merge commit...) y si se borra la rama al terminar. -->

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

Los títulos de los PR siguen la misma convención.

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
