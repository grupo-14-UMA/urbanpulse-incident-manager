---
name: create-issue
description: Puts issues, epics and milestones into the GitHub tracker through a validating script. Use when creating an issue, epic or milestone, turning loose ideas or notes into triage issues, promoting a 🔍 triage issue to ✅ ready, or changing an issue's checklist fields.
---

# create-issue

Every issue reaches the tracker through `create-issue.sh`, next to this file; a hook blocks every other way to create one. You make the judgement calls (wording, area, priority, effort, dependencies); the script validates them, runs every `gh` call and verifies the result. `create-issue.sh --help` lists the subcommands, flags and batch format.

**Speak Spanish with the user** in every mode, the grilling included. Write issue titles and bodies in English, with the Spanish domain terms of `GLOSSARY.md`.

## Reference

Read these from `CONTRIBUTING.md` on every run; they live only there:

- The states (triage, ready, epic), the **definition of ready**, the ready checklist and the **current milestone** → "Crear un issue".
- The area of an issue and its owner, normally the assignee → "Áreas de desarrollo".
- The `Priority` and `Effort` scales → "Prioridad y esfuerzo".

Read labels, milestones and epics from the tracker. The body follows the template in `.github/ISSUE_TEMPLATE/` that matches the title prefix. `--milestone current` resolves to the current milestone. For *blocked by*, search the open issues whose work the new one needs first.

Write every body to a file in the scratchpad and pass it with `--body-file`. Checklist fields change only through the script: `update <n>` for any of them, in any state. `gh issue edit` stays for one-off actions (a comment, closing, an extra label).

## Modes

Pick the mode from the request:

- **Create**: a concrete piece of work, with enough to state what and how to check it → one ✅ ready issue.
- **Idea**: loose notes, possibly several topics, not for the current milestone yet → 🔍 triage issues with no milestone.
- **Promote**: an existing 🔍 triage issue → ✅ ready.
- **Update**: change fields of an existing issue and keep its state (give an idea a milestone, send an issue back to the inbox with `--milestone ""`, fix a missing field).
- **Delivery**: a new milestone and its epic, optionally with their issues in one batch.
- **Check**: validate existing issues without changing them.

A request for an issue with open questions that the user can't settle now becomes a triage issue, with the milestone when it belongs to a delivery.

Every mode ends the same way: run the command with `--dry-run` until it reports no error, show the user the dry-run summary (and the body, when it changes), wait for their explicit approval, then run it without `--dry-run`. Done when every `verify` check shows ✓; give the user the URLs.

### Create

1. Draft the title and body to the definition of ready: what and why, observable acceptance criteria, the quality attributes that apply (or `None apply: <reason>`), out of scope when it helps, and technical constraints only when already decided. A first idea of how to do it goes in the optional, non-binding Proposal section; the issue never requires a way of implementing it. Ask the user, briefly, for whatever you can't infer.
2. Choose area, assignee, priority, effort, milestone, epic and *blocked by*.
3. Run `create-issue.sh create --state ready` with every value.

### Idea

1. Split the notes into one issue per distinct topic. For each: a title, the area only when clear, and 2-4 lines of context (what and why) that a reader who never saw the notes can follow. The context fills the template's description and motivation; the other headings stay empty for the promote.
2. Propose the list to the user: title, area and context of each. Done when the user approves the list, after merging, dropping or rewording what they ask.
3. Run a batch file with one `"state": "triage"` item per issue and no milestone. They show up in the "No Milestone" group of the **Pendiente** view.

### Promote

1. Read the issue and its comments.
2. Grill the user, as `/grill-with-docs` does: call the Skill tool for `grilling` and `domain-modeling`. Keep it **short** and at the level of the definition of ready: settle what, why and how to check it (acceptance criteria, quality attributes, out of scope) and the missing fields. Record any idea of how to implement it as a non-binding proposal and leave the decision to the implementer. Done when every item of the definition of ready and every ready field has an answer.
3. Rewrite the body into the template to the definition of ready, keeping what the triage issue said that still holds.
4. Run `create-issue.sh promote <n>` with the new body and only the values that are new or change; the script keeps the rest.

### Update

Run `create-issue.sh update <n>` with only the fields that change. An empty value removes a field; `--state ready|triage` changes the state, and the script validates the issue against the state it ends in.

### Delivery

1. Run `create-issue.sh milestone --title …`, with `--due-on` and `--description` when the user gives them.
2. Create the epic with `--state epic` and that milestone: title, body (the delivery's goal) and milestone, and no area, priority, effort or parent epic.
3. To create the epic with its issues, put the epic first in a batch and point the issues at it with `"epic": "@1"`; `"@k"` in `blocked_by` orders issues of the same batch.

### Check

Run `create-issue.sh check <n> [<n>…]`. It is read-only and validates each issue against the state it is in, with the same rules as `create` and `update` plus the issue type and contradictory state labels. Show the errors to the user in Spanish and offer the **Update** or **Promote** that fixes them; a whole-backlog review is the `audit-backlog` skill.

## When a write fails

The script prints which steps were done and which are missing. Tell the user exactly that, fix the cause, and finish the issue with `create-issue.sh update <n>` and the missing values. Report the issue as created only once `verify` passes.
