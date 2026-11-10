---
name: create-issue
description: Puts issues into the GitHub tracker through a validating script. Use when creating an issue, turning loose ideas or notes into triage issues, or promoting a 🔍 triage issue to ✅ ready.
---

# create-issue

Every issue reaches the tracker through `create-issue.sh`, next to this file; a hook blocks every other way. You make the judgement calls (wording, area, priority, effort, dependencies); the script validates them, runs every `gh` call and verifies the result. `create-issue.sh --help` lists the flags and the batch format.

**Speak Spanish with the user** in every mode, the grilling included. Write issue titles and bodies in English, with the Spanish domain terms of `GLOSSARY.md`.

## Reference

Read these from `CONTRIBUTING.md` on every run; they live only there:

- The two states, the **definition of ready** and the ready checklist → "Crear un issue".
- The area of an issue and its owner, who is the assignee → "Áreas de desarrollo".
- The `Priority` and `Effort` scales → "Prioridad y esfuerzo".

The body follows the template in `.github/ISSUE_TEMPLATE/` that matches the title prefix. The current milestone is the open one (`gh api repos/{owner}/{repo}/milestones --jq '.[].title'`), and its epic is the open `🗺️ epic` issue in that milestone. For *blocked by*, search the open issues whose work the new one needs first.

Write every body to a file in the scratchpad and pass it with `--body-file`.

## Modes

Pick the mode from the request:

- **Create**: a concrete piece of work, with enough to state what and how to check it → one ✅ ready issue.
- **Idea**: loose notes, possibly several topics, not for the current milestone yet → 🔍 triage issues with no milestone.
- **Promote**: an existing 🔍 triage issue → ✅ ready.

A request for an issue with open questions that the user can't settle now becomes a triage issue, with the milestone when it belongs to a delivery.

### Create

1. Draft the title and body to the definition of ready: what and why, observable acceptance criteria, the quality attributes that apply, out of scope when it helps, and technical constraints only when already decided. The how belongs to the implementer. Ask the user, briefly, for whatever you can't infer.
2. Choose area, assignee, priority, effort, milestone, epic and *blocked by*.
3. Run `create-issue.sh create --dry-run` with every value. Done when the dry run reports no error.
4. Show the user the dry-run summary and the body, and wait for their explicit approval. Apply any change and repeat step 3.
5. Run the same command without `--dry-run`. Done when every `verify` check shows ✓; give the user the URL.

### Idea

1. Split the notes into one issue per distinct topic. For each: a title, the area only when clear, and 2-4 lines of context (what and why) that a reader who never saw the notes can follow. The context fills the template's description and motivation; the other headings stay empty for the promote.
2. Propose the list to the user: title, area and context of each. Done when the user approves the list, after merging, dropping or rewording what they ask.
3. Write a batch file with one `"state": "triage"` item per issue and no milestone, run it with `--dry-run`, then without. Done when every item verifies; give the user the URLs. They show up in the "No Milestone" group of the **Pendiente** view.

### Promote

1. Read the issue and its comments.
2. Grill the user, as `/grill-with-docs` does: call the Skill tool for `grilling` and `domain-modeling`. Keep it **short** and at the level of the definition of ready: settle what, why and how to check it (acceptance criteria, quality attributes, out of scope) and the missing fields. Park any question about how to implement as the implementer's call. Done when every item of the definition of ready and every ready field has an answer.
3. Rewrite the body into the template to the definition of ready, keeping what the triage issue said that still holds.
4. Run `create-issue.sh promote <n> --dry-run` with the new body and only the values that are new or change; the script keeps the rest. Done when the dry run reports no error.
5. Show the user the dry-run summary and the body, and wait for their explicit approval.
6. Run it without `--dry-run`. Done when every `verify` check shows ✓; give the user the URL.

## When a write fails

The script prints which steps were done and which are missing. Tell the user exactly that, fix the cause, and finish the issue with `create-issue.sh promote <n>` (a triage issue: with `gh issue edit`). Report the issue as created only once `verify` passes.

Epics and milestones are created by hand in the GitHub web UI; say so when asked for one.
