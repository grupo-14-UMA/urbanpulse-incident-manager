---
name: find-gaps
description: Finds work the system lacks that no issue covers, crossing every component with the team's criteria and the requirements, and proposes issues for the gaps. Use when looking for gaps or missing work, checking which requirements have no code or issue, or getting ready to plan a delivery.
---

# find-gaps

Answers "what does the system lack that no issue covers?". `find-gaps.sh`, next to this file, gathers a snapshot of the tracker and the repo, discovers the components and fills the mechanical cells; you settle the components, fill the other cells, match every gap with the tracker, classify the gaps, write the report and drive the creation. `find-gaps.sh --help` and `../create-issue/create-issue.sh --help` describe the scripts.

**Speak Spanish with the user**: the report, the questions and the summary. Write issue titles and bodies in English, with the Spanish domain terms of `GLOSSARY.md`.

Usage: `/find-gaps [<milestone title>] [--report-only]`. With `--report-only` (how `plan-milestone` calls it) run steps 1-8 and stop after the report. Otherwise the run is **read-only** until the user approves a gap, and every write goes through `../create-issue/`.

## What exists and what should exist

- **What exists** is the repo: the code, the orchestration file, the CI workflows, the tests. The architecture docs (`docs/architecture.md`, `README.md`, `docs/development.md`, `docs/deployment.md`) describe what exists; you check that they match the repo and never read them as a list of what should exist.
- **What should exist** comes only from:
  1. the requirements: `docs/requirements.md` and `docs/enunciado.pdf`;
  2. the quality targets: the quality attributes and scenarios, wherever they live (`docs/architecture.md` or a roadmap doc);
  3. the team rules: `AGENTS.md` → Quality attributes, `docs/error-handling.md`, `CONTRIBUTING.md`;
  4. a roadmap doc, when the repo has one.
- What a delivery promises is the **goal of its epic**, the milestone description and the epic's sub-issues. The enunciado does not split work by delivery.

## Steps

### 1. Read the sources

Read on every run the sources of what should exist (above) and the defining doc of every criterion (step 4). A criterion whose defining docs are all missing shows ❔ in its column and adds a line under `## Avisos`; its rule comes only from that doc. Done when you can state, for every criterion, its rule as its defining doc writes it, and what the target delivery promises.

### 2. Gather and analyze

```bash
G=.claude/skills/find-gaps/find-gaps.sh
$G gather > <scratchpad>/gaps.json
$G analyze <scratchpad>/gaps.json [--target "<milestone title>"]
```

Pass `--target` only when the user named a milestone, exactly as given. On exit 2, show the error in Spanish and stop. `analyze` prints `{target, warnings, components, cells}`: `target` is `null` when no milestone is open, and then the report says `objetivo: ninguno`. Done when `analyze` exits 0.

### 3. Settle the components

Start from `components`. Add a component the script can't detect, such as a folder of our own code with no manifest, service or workflow, and fill its cells yourself. Every component comes from the repo: a component a doc describes and the repo lacks is a `docs` gap of that doc ("el doc describe algo que no existe"), never a row. External systems (map tiles, third-party APIs) are not components. Name each component the way the docs name it. Done when every folder with code of our own belongs to exactly one component.

### 4. Fill the matrix

Each criterion is a rule, the doc that defines it and what counts as evidence. Keep the script's cells for `local`, `ci` and `tests` (`ok` ✅, `gap` 🔴, `na` ⚫, `unknown` ❔) and fill the rest from the code.

| Code | Column | Rule | Defining doc | Evidence |
| --- | --- | --- | --- | --- |
| `local` | Local | Every component starts in the local environment as the development guide says. | `docs/development.md`, `docs/deployment.md` | A service in the orchestration file, or a guide section that starts it. |
| `ci` | CI | Every component with code has a workflow that builds it, lints it and runs its tests on each PR. | `CONTRIBUTING.md` (required checks), `docs/deployment.md` → Integración continua | A workflow working on its folder, and the steps it runs. |
| `tests` | Tests | Every component with code has tests and a command to run them. | `docs/development.md` → Pruebas | Test files and a test command. |
| `logs` | Logs | Its operations log, and have metrics once observability exists. | `AGENTS.md` → Quality attributes, the quality attributes | Logger use in the code, a metrics endpoint or library. |
| `errors` | Errores | It follows the error handling policy. | `docs/error-handling.md` | The API error contract, screen states, retries… |
| `docs` | Docs | The docs describe the component as it is, no more and no less. | `README.md`, `docs/architecture.md`, `docs/development.md` | Its row or section exists and matches the repo. |
| `quality` | Calidad | Bounded lists (limit, pagination or bounding area), queries with a supporting index, no table that grows without limit. | `AGENTS.md` → Quality attributes, the quality scenarios | List endpoints and screens with a limit, indexes in the migrations. A static review, not a measurement. |

| Value | Meaning |
| --- | --- |
| ✅ | ok, with evidence in the repo. |
| 🔴 | gap. Partial coverage is 🔴 too, and the evidence says what is missing. |
| 🔗 #n | covered by an open issue (step 5). |
| 🚫 #n | discarded in a closed issue (step 5); never proposed again. |
| ⚫ | n/a, **only** with a cited doc or a checkable fact ("no code of our own in the repo"). Without either the cell is 🔴, and its proposal may be "document why it does not apply". |
| ❔ | the criterion's defining docs are missing. |

The `docs` cell looks both ways: the component is described as it is, and nothing the docs describe as present is missing from the repo. Docs that state plans as facts are a `docs` gap. Done when every cell of every component has a value and one line of evidence citing a file, a workflow step, a doc or an issue.

### 5. Match the gaps with the tracker

For every 🔴 cell, search the snapshot for issues that cover it, by title, body and area label:

```bash
jq -r --arg q '<regex>' '.issues[]
  | select("\(.title)\n\(.body // "")" | test($q; "i"))
  | "#\(.number) \(.state) \(.stateReason // "-") \(.milestone.title // "inbox") \(.title)"' <scratchpad>/gaps.json
```

Read in full only the candidates the search returns, never every body. Matching is a judgement, and the evidence cites the issue it relied on:

- **Open issue, any state** (triage in the inbox too) → 🔗 #n. Outside the target milestone, the evidence says where it is (another milestone, or the inbox).
- **Closed as not planned, or labelled `🚫 wontfix`** → 🚫 #n.
- **Closed as completed, and the repo still lacks the work** → 🔴 with "#n se cerró pero falta X".

Done when every 🔴 cell has been searched, and the remaining 🔴 cells have no open or discarded issue.

### 6. Requirements without work

For every requirement of `docs/requirements.md` and every deliverable of the enunciado: cite the code that implements it (endpoint, screen or module), or the issue that covers it (`#n`, matched as in step 5), or neither. Only a requirement with no code and no open issue becomes a gap. Mark the requirements the enunciado calls optional *(opcional)*. Done when every requirement has its line of evidence.

### 7. Classify the gaps

One proposed issue per gap: the 🔴 cells and the requirements without work, plus an open issue outside the target that covers a blocking gap (step 9 moves it). Classify each one against the target, reading the epic's goal (`target.epics`), the milestone description and the epic's sub-issues:

- **Bloquea <entrega>**: the epic's goal needs it, or without it what the delivery promises does not work (it does not start, a delivered component has no CI…). Docs-out-of-sync gaps are **always** here.
- **Pendiente para otra entrega**: the requirements, the rules or the roadmap ask for it, and the target epic does not. Optional requirements go here unless the epic asks for them. A gap the epic does not clearly cover goes here with "dudoso: el epic no lo menciona": a gap reaches Bloquea only through what the epic says.
- **Mejora futura**: justified only by a measurement against a baseline (observability, load tests, caches, separating services), as `docs/requirements.md` → Pautas de diseño says.

With no target there is no Bloquea class. Give each gap a Conventional Commits title in English and 2-4 lines of context in Spanish, and number the gaps across all classes. Done when every gap has a class, a number, a title and its context.

### 8. Report

Print in the terminal, in Spanish, exactly this layout:

```
# Huecos del sistema · objetivo: <target title | ninguno>
🔴 <n> huecos · 🔗 <n> cubiertos · 🚫 <n> descartados

## Matriz
| Componente | Local | CI | Tests | Logs | Errores | Docs | Calidad |
| <component> | ✅ | 🔴 | 🔗 #n | … |
Leyenda: ✅ ok · 🔴 hueco · 🔗 cubierta por #n · 🚫 descartada en #n · ⚫ n/a · ❔ sin regla

## Evidencia
### <component>
- <Column> <value>: <evidence, in Spanish, citing files, steps, docs or issues>.

## Requisitos sin trabajo
- <RF id> <name> [(opcional)]: <evidence>.

## Huecos propuestos
### Bloquea <target title>
1. `<type>: <english title>`: <2-4 lines of context in Spanish>. [Relacionada: #n]
### Pendiente para otra entrega
2. …
### Mejora futura
3. …

## Avisos
- <snapshot and analyze warnings, missing defining docs>
```

- Evidence goes in the list under the matrix, so the table fits a terminal.
- Leave out empty classes and sections. With no gaps at all, say so in one line.
- The numbered list is the contract with `plan-milestone`: class, title, context and related issue of each gap, in this form on every run.

With `--report-only`, stop here.

### 9. Create the approved gaps

1. Ask the user which gaps to create, by number ("crea 1, 3 y 5"). Nothing is written before that.
2. **Bloquea**: for each picked gap, recommend "ready ahora" or "triage con el milestone objetivo" (triage when it has open questions), and the user decides. Ready runs the `create-issue` **Create** mode, its short round of questions included, with the target milestone and its epic. Triage runs `create-issue.sh create --state triage` with the target milestone.
3. **Pendiente para otra entrega** and **Mejora futura**: one `create-issue` batch with one `"state": "triage"` item per gap and no milestone, as in its **Idea** mode.
4. **A blocking gap covered by an open issue outside the target**: offer `create-issue.sh update <n> --milestone "<target>" --epic <target epic>` in place of a new issue.
5. **Closed as completed but missing**: a new issue whose context says "#n was closed but X is missing". The closed issue stays closed.
6. Every command runs with `--dry-run` first and runs for real after the user approves its summary, as the `create-issue` skill says. Done when every approved gap is created or updated and `verify` shows only ✓.
7. End with the URLs created or updated, and the failures with their reason.
