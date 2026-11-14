---
name: audit-backlog
description: Audits the form of the open backlog in the GitHub tracker and offers fixes. Use when auditing or reviewing the backlog, checking whether issues are well formed, or getting ready for a planning meeting.
---

# audit-backlog

Answers "is the backlog in good shape?": whether each open issue has what its state requires and whether the issues fit together. `audit-backlog.sh`, next to this file, gathers a snapshot and runs the deterministic checks; you judge check (b), write the report and drive the fixes. `audit-backlog.sh --help` and `create-issue.sh --help` describe the scripts.

**Speak Spanish with the user**: the report, the questions and the summary.

The audit is **read-only** until the user approves a fix, and every write goes through `../create-issue/create-issue.sh update`. That script is the only writing command you run.

## Steps

### 1. Read the rules

Read on every run, since they live only there:

- `CONTRIBUTING.md` → "Crear un issue" (states, definition of ready, ready checklist, current milestone), "Áreas de desarrollo" (area → owner), "Prioridad y esfuerzo".
- `AGENTS.md` → Quality attributes.

### 2. Gather and analyze

```bash
A=.claude/skills/audit-backlog/audit-backlog.sh
$A gather > <scratchpad>/backlog.json
$A analyze <scratchpad>/backlog.json [--target "<milestone title>"]
```

Pass `--target` only when the user named a milestone, exactly as they gave it; without it the target is the current milestone. On exit 2, show the error in Spanish and stop.

`analyze` prints `{target, warnings, findings}`. Each finding has `check` (a, c, d, e), `severity` (red, yellow), `issue`, `url`, `title`, `milestone`, `field`, an English `message`, `related` issues and a `fix` `{kind: derivable|judgement|none, flag, value}`. `issue`, `url`, `title` and `field` are null for milestone-level findings.

### 3. Judge check (b)

For every open `✅ ready` issue of the snapshot that is not a `🗺️ epic`, read the body and add one ⚪ finding per issue, listing every problem found:

- **Acceptance criteria not observable**: a criterion names a file, function, class or table to create instead of something visible or measurable from outside, or the section has no filled item (only `- [ ]` or template comments).
- **Quality attributes miss what applies**: the body outside `## Proposal` introduces one of these cases, and `## Quality attributes` neither covers it nor says `None apply: <reason>`:
  - a list endpoint or screen → limit, pagination or bounding area;
  - a new query → supporting index;
  - a table or collection that grows → limit or retention;
  - a new operation → logs or metrics.
- **Quality attributes empty**: the section exists but holds only template comments, with no `None apply: <reason>`.

The `## Proposal` section is optional and non-binding: its presence and content are always fine. `fix:` issues have no quality attributes section, so judge only their acceptance criteria. A missing section is already a check (a) finding.

Done when every ready non-epic issue has been read.

### 4. Report

Print in the terminal, in Spanish, exactly this layout:

```
# Auditoría del backlog · objetivo: <target title>
🔴 <n> · 🟡 <n> · ⚪ <n>

## 🔴 Rompe <target title>
### (a) Campos por estado
- [#87](url) <title>: <what is wrong, in Spanish>. Arreglo: <proposed fix, or "solo aviso">.
### (c) Dependencias rotas
…
## 🟡 Desorden que aún no bloquea
…
## ⚪ Redacción
### (b) Issues ready flojas
…
```

- Check headings: (a) Campos por estado, (b) Issues ready flojas, (c) Dependencias rotas, (d) Huérfanas y epics, (e) Preguntas abiertas en la entrega.
- Order: 🔴 (red), 🟡 (yellow), ⚪ (check b); inside each level, by check; leave out empty levels and checks.
- Milestone-level findings show the milestone title instead of an issue link.
- With no target (no open milestone), the title says `objetivo: ninguno` and there is no 🔴 level.
- Warnings from the snapshot go last, under `## Avisos`.
- With no findings at all, say so in one line.

### 5. Offer fixes

Only when some finding has `fix.kind` `derivable` or `judgement`.

1. Group the fixes by issue.
2. **Skip an issue** whose check (a) findings also include a fix-`none` one with field `template`, `body`, `area` (several areas) or `blocked_by`, or a `state` finding for `🚫 wontfix` or `❓ question`: `update` rejects it until a person fixes that. Tell the user which issues are skipped and why.
3. **Derivable fixes**: one list with issue, flag, value and reason ("área core → su dueño", "único epic de Entrega 2", "el prefijo feat → Feature"). Fill `--assignee` with the owner of the issue's area from "Áreas de desarrollo". A `type` fix has no flag: any `update` sets the type from the prefix. Ask once: apply all, all except some, or none.
4. **Judgement fixes**: one issue at a time, propose a value with a one-line reason from `CONTRIBUTING.md`:
   - priority: the Priority table and what depends on the issue (the `blockedBy` of other issues);
   - effort: the Effort table;
   - area: the folders the issue mentions;
   - milestone: the target, unless the issue says it is for later;
   - state: `triage` unless every ready field is present;
   - title: the fixed title.

   When an issue misses both a value and what follows from it (an area and its assignee, a milestone and its epic), propose them together. The user confirms, changes or skips each one.
5. **Apply**, per issue with at least one approved fix: one `create-issue.sh update <n>` with every approved flag of that issue (a type fix alone is `update <n>` with no flags). Run it with `--dry-run` first; if it fails validation, report the error and move on without retrying. Otherwise run it for real. Done when `verify` shows only ✓.
6. **End** with the issues changed (with URLs) and the ones that failed, with the reason.

Findings with fix `none` (bodies, acceptance criteria, dependencies, closing an epic, promoting a triage issue) are reported only: the user rewrites or closes them, through `create-issue` when it is a checklist field.
