---
name: plan-milestone
description: Plans a delivery from the gaps of the system, the inbox of ideas, the open triage issues and what is left of the current delivery, and leaves its milestone, epic, issues and release issue in the GitHub tracker. Use when planning or refining the next delivery (Entrega), deciding the scope of a milestone, or turning the inbox into a plan.
---

# plan-milestone

Answers "what does the next delivery build, and why?", and leaves the answer in the tracker: a milestone whose description is the goal, an epic that states the goal and what is in and out, `✅ ready` issues, and a release issue blocked by all of them. You argue the scope and drive the conversation; `find-gaps`, `audit-backlog.sh` and `create-issue.sh` gather, check and write.

**Speak Spanish with the user**: the proposal, the questions and the summary. Write titles and bodies in English, with the Spanish domain terms of `GLOSSARY.md`.

Usage: `/plan-milestone [<milestone title>]`. The run is **read-only** until the user approves: nothing is created, moved or closed before that. Every write goes through `../create-issue/create-issue.sh`, except closing an idea (`gh issue close`) and editing the description of an existing milestone (`gh api --method PATCH repos/{owner}/{repo}/milestones/<n> -f description=…`).

## The rules live elsewhere

Read on every run, and never copy them here:

- `CONTRIBUTING.md` → "Crear un issue" (states, definition of ready, ready checklist, current milestone), "Planificar una entrega", "Áreas de desarrollo" (area → owner), "Prioridad y esfuerzo".
- `docs/deployment.md` → "Versionado": the version of each delivery and the release procedure.
- `AGENTS.md` → Quality attributes.
- The `create-issue` skill: its modes, the batch format (`create-issue.sh --help`) and the release-link rule.

Never read `docs/enunciado.pdf`: `find-gaps` already turns it into gaps.

## Steps

### 1. Pick the delivery

The **current** delivery is the current milestone of `CONTRIBUTING.md`. The **target**:

- With an argument: the milestone with that title, open, exactly as given. If none exists, it will be created with that title.
- Without one: the open milestone right after the current one. With none, a new `Entrega N`, N one more than the highest `Entrega` of all milestones, open or closed.

If the target is the current delivery, say that it is already in progress and ask before going on. The target is **existing** when its milestone has an open `🗺️ epic`; then this is a refinement (step 3). Done when you can name the target, whether it exists, and its version (from "Versionado").

### 2. Gather

```bash
A=.claude/skills/audit-backlog/audit-backlog.sh
$A gather > <scratchpad>/backlog.json
```

Then call the Skill tool for `find-gaps` with `--report-only`, plus the target title when its milestone exists (otherwise no title: its *Pendiente para otra entrega* and *Mejora futura* classes are the pool). From the snapshot (on exit 2, show the error in Spanish and stop):

```bash
jq -r --arg cur "<current>" --arg tgt "<target>" '
  def names: [.labels.nodes[].name];
  def row: "#\(.number) [\(.milestone.title // "inbox")] \(names | map(select(startswith("area: ") or . == "🔍 triage" or . == "✅ ready")) | join(" ")) \(.title)";
  .issues[] | select(names | index("🗺️ epic") | not)
  | select((names | index("🔍 triage")) or .milestone.title == $cur or .milestone.title == $tgt)
  | row' <scratchpad>/backlog.json
```

Triage issues with no milestone are the inbox; the rest carry their milestone.

Read in full only the bodies you need to argue about. Done when you have four lists: the gaps, the inbox, the open triage issues (with their milestone), and the open issues of the current delivery.

### 3. Start from what exists (refinement only)

Read the epic of the target: its goal, what is in and what stays out, with reasons. The goal is the starting point; propose only what is new since the epic was written (gaps, ideas, triage issues, issues of the current delivery). Never propose again what the epic leaves out, unless there is a new reason, and say which. Do not create the milestone or the epic again.

### 4. Propose the scope

Before any write, show in the terminal:

```
# Plan de <target> (<version>) · <nuevo | refinamiento>

## Objetivo
<3-6 lines: what the delivery builds or polishes and why now, from the gaps, the requirements and what the current delivery leaves>

## Entra
1. <#n | hueco k | nuevo> <title>: <reason>
## Se queda fuera
- <#n | hueco k> <title>: <reason>
## Mover desde <current>        (only when it makes sense)
- #n <title>: <why it fits better here>
```

Every pending idea of the four lists that does not enter goes under "Se queda fuera" with its reason. Propose moving an issue of the current delivery only when that delivery cannot finish it or it belongs to the new goal; usually there is none.

The user corrects the scope item by item: add, drop, move, merge, reword. Then ask for the due date (`YYYY-MM-DD`, or none).

An inbox idea that stays out stays in the inbox. Close it as not planned (`gh issue close <n> --reason "not planned" --comment "<reason>"`) only when the user says it will never be done. Age alone never closes nor flags an idea. Done when the user approves the scope and the due date.

### 5. Settle the issues

One grilling for the whole delivery: call the Skill tool for `grilling` and `domain-modeling`, keeping it at the level of the definition of ready. Settle, for every item of the scope:

- how it splits into issues, each a concrete piece of work (an idea or a gap may become several issues, or several one);
- its order, as *blocked by*;
- area, assignee (normally the owner of the area), priority and effort, from `CONTRIBUTING.md`;
- what `create-issue` needs for a ready body: what and why, observable acceptance criteria, quality attributes, out of scope.

Dependencies may point to issues of the current delivery, never the other way round: no issue of the current delivery ends blocked by one of the target. Count the issues per assignee and point it out when one person gets far more than the rest; leave the decision to the user and do not rebalance. A question the user cannot settle now ends as a `🔍 triage` issue with the target milestone, with the open question in its body, never as an invented detail. Done when every issue has every ready field or is left as triage.

### 6. Write the plan

Draft every body to a file in the scratchpad, following the `create-issue` skill. The **epic** (new delivery only) has a Conventional Commits title and a body with the goal, `## In scope` (the issues, or what they deliver) and `## Out of scope` (each with its reason). The **milestone** description is the goal. The **release issue** is `chore: release vX.Y.Z`, a ready task of the Maintainer's area whose acceptance criteria follow the release procedure in `docs/deployment.md`.

Run, in order:

1. New delivery: `create-issue.sh milestone --title <target> [--due-on …] --description <goal>`. Existing delivery: the epic body update with `create-issue.sh update <epic> --body-file …`, and the milestone description only if the goal changed.
2. One batch for everything new: the epic first (new delivery), the new issues with `"epic": "@1"` (or the epic number) and `"@k"` in `blocked_by`, and the release issue last (new delivery, or no release yet) with `blocked_by` every other item. In an existing delivery with a release, every new issue has `"blocks": ["<release>"]`.
3. One `update` (or `promote`) per existing issue that enters: an inbox idea or triage issue gets the milestone, the epic, `--blocks <release>` and, when it becomes ready, the new body; a moved issue also gets `--remove-blocks <old release>`. When the batch created the release of an existing delivery, every issue already in the milestone also gets `--blocks <release>`. Never create a new issue for something that already has one.
4. The ideas the user said will never be done: `gh issue close`.

Every `create-issue.sh` command runs with `--dry-run` first. Show the user the dry-run summary of the whole plan at once (the batch, the updates, the closings) and wait for explicit approval. Before the milestone exists, the batch dry-run can only report that the milestone is missing: say so, and run it again after creating the milestone. Then run each command for real, in order; after the batch, put the new release number into the `--blocks` of step 3. On a failed write, follow the `create-issue` skill. Done when every `verify` shows only ✓.

### 7. Audit and close

Call the Skill tool for `audit-backlog` with the target title, and let it drive its own fixes. End with the milestone, epic, issue and release URLs, the issues per assignee, and what stayed out. Done when the audit shows no 🔴 finding for the target, or the user has decided on each one.
