# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Creating an issue

Create issues, epics and milestones, and change the checklist fields of an issue, with the `create-issue` skill (`.claude/skills/create-issue/SKILL.md`). It drives `.claude/skills/create-issue/create-issue.sh`, the only way to create issues: a `PreToolUse` hook (`block-raw-create.sh`, same folder) blocks `gh issue create` and `gh api` calls that create issues.

```bash
S=.claude/skills/create-issue/create-issue.sh
$S create --dry-run --state ready --title "feat: …" --body-file body.md \
  --area <area> --priority High --effort Simple --assignee <owner> \
  --milestone current --epic <epic> --blocked-by <n>,<n>
$S create --state epic --title "feat: …" --body-file goal.md --milestone <milestone>
$S create --batch issues.json             # several issues (and their epic), all validated first
$S update <n> --milestone ""              # change fields, keep the state; "" removes a field
$S update <n> --state triage              # validated against the state it ends in
$S promote <n> --body-file body.md        # update <n> --state ready
$S milestone --title <title> [--due-on YYYY-MM-DD] [--description …]
$S check <n> [<n>…] [--json]              # read-only: validate issues against their state
$S --help                                 # every flag and the batch format
```

The script validates every value against `CONTRIBUTING.md` → Crear un issue before the first write, sets the labels, assignee, milestone, issue type, parent epic, *blocked by* and the `Priority` / `Effort` issue fields, and then verifies each one. `--milestone current` is the open milestone with the lowest number. When a write fails it prints which steps were done and which are missing; finish the issue with `update <n>`. `--dry-run` runs only the read-only checks and prints the writes.

Comments, closing and one-off labels go through `gh` directly (see below); checklist fields go through `update`.

## Auditing the backlog

The `audit-backlog` skill (`.claude/skills/audit-backlog/SKILL.md`) reviews the form of every open issue and offers fixes, which it applies only after approval and only through `create-issue.sh update`. Its helper `.claude/skills/audit-backlog/audit-backlog.sh` has `gather` (a snapshot of the open backlog in a few paginated GraphQL queries) and `analyze` (offline findings, reusing `create-issue.sh check --input`). `tests/run.sh` in the same folder runs the analysis against offline fixtures.

## Conventions

- **Create an issue**: the `create-issue` skill (see **Creating an issue** above).
- **Read an issue**: `gh issue view <number> --json number,title,body,labels,comments`.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Make an issue a sub-issue of a parent**: `gh issue edit <child> --parent <parent>` or `gh issue edit <parent> --add-sub-issue <child>` (`gh` 2.94+).
- **Comment on an issue**: `gh issue comment <number> --body "..."`
- **Apply / remove labels**: `gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **Close**: `gh issue close <number> --comment "..."`

Infer the repo from `git remote -v`; `gh` does this automatically when run inside a clone.

## Pull requests as a triage surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature requests; `/triage` reads this flag.)_

When set to `yes`, PRs run through the same labels and states as issues, using the `gh pr` equivalents:

- **Read a PR**: `gh pr view <number> --comments` and `gh pr diff <number>` for the diff.
- **List external PRs for triage**: `gh api --paginate 'repos/{owner}/{repo}/pulls?state=open' --jq '.[] | select(.author_association | IN("OWNER","MEMBER","COLLABORATOR") | not) | {number, title, author: .user.login, author_association, labels: [.labels[].name]}'`.
- **Comment / label / close**: `gh pr comment`, `gh pr edit --add-label`/`--remove-label`, `gh pr close`.

GitHub shares one number space across issues and PRs, so a bare `#42` may be either: resolve with `gh pr view 42` and fall back to `gh issue view 42`.

## When a skill says "publish to the issue tracker"

Create the issue with the `create-issue` skill.

## When a skill says "fetch the relevant ticket"

Read it as in **Read an issue** above.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. Create it with the `create-issue` skill, then `gh issue edit <n> --add-label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (see **Make an issue a sub-issue of a parent**). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies**, the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only, the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me`, the session's first write.
- **Resolve**: `gh issue comment <n> --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
