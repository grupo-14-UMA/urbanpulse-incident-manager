# Issue tracker: GitHub

Issues and specs for this repo live as GitHub issues. Use the `gh` CLI for all operations.

## Creating an issue

Every issue follows the checklist in [`CONTRIBUTING.md` → Crear un issue](../../CONTRIBUTING.md#crear-un-issue): Conventional Commits title, template, issue type, `✅ ready` or `🔍 triage`, `area: …`, the `Priority` and `Effort` issue fields, assignee (the area owner), milestone, sub-issue of the delivery epic and *blocked by* dependencies. Copy label names exactly from `gh label list` (they include emojis). Look up the area owner in `CONTRIBUTING.md` → Áreas de desarrollo, and the current milestone with `gh api repos/{owner}/{repo}/milestones --jq '.[] | select(.state=="open") | .title'`.

```bash
# 1. Create it with labels, assignee and milestone
gh issue create --title "feat: …" --body-file body.md \
  --label "✅ ready" --label "area: 📱 mobile" \
  --assignee <owner> --milestone "Entrega 2"

# 2. Issue type: Bug, Feature or Task
gh api --method PATCH repos/{owner}/{repo}/issues/<n> -f type=Feature

# 3. Sub-issue of the delivery epic (database id, not #number)
gh api --method POST repos/{owner}/{repo}/issues/<epic>/sub_issues \
  -F sub_issue_id=$(gh api repos/{owner}/{repo}/issues/<n> --jq .id)

# 4. Blocked by: one call per blocker
gh api --method POST repos/{owner}/{repo}/issues/<n>/dependencies/blocked_by \
  -F issue_id=$(gh api repos/{owner}/{repo}/issues/<blocker> --jq .id)

# 5. Priority and Effort: gh has no flags for issue fields, use GraphQL
gh api graphql -f query='mutation($id: ID!) { setIssueFieldValue(input: {
  issueId: $id, issueFields: [
    { fieldId: "IFSS_kgDOAtzvIg", singleSelectOptionId: "IFSSO_kgDOBQMSLA" },
    { fieldId: "IFSS_kgDOAtzvJQ", singleSelectOptionId: "IFSSO_kgDOBQMSLw" }
  ] }) { clientMutationId } }' \
  -f id=$(gh api repos/{owner}/{repo}/issues/<n> --jq .node_id)
```

Issue field and option ids (org-level, the same for every repo in `grupo-14-UMA`). Pick values with the scales in `CONTRIBUTING.md` → Prioridad y esfuerzo; epics get neither field.

| Field | Field id | Option ids |
| --- | --- | --- |
| `Priority` | `IFSS_kgDOAtzvIg` | Urgent `IFSSO_kgDOBQMSKg`, High `IFSSO_kgDOBQMSKw`, Medium `IFSSO_kgDOBQMSLA`, Low `IFSSO_kgDOBQMSLQ` |
| `Effort` | `IFSS_kgDOAtzvJQ` | Complex `IFSSO_kgDOBQMSLg`, Moderate `IFSSO_kgDOBQMSLw`, Simple `IFSSO_kgDOBQMSMA` |

Check the result with `gh issue view <n> --json labels,assignees,milestone`, `gh api repos/{owner}/{repo}/issues/<n> --jq '.type.name'` and, for the fields, `gh api graphql -f query='{ repository(owner: "grupo-14-UMA", name: "urbanpulse-incident-manager") { issue(number: <n>) { issueFieldValues(first: 5) { nodes { ... on IssueFieldSingleSelectValue { name field { ... on IssueFieldCommon { name } } } } } } } }'`.

## Conventions

- **Create an issue**: `gh issue create --title "..." --body "..."`. Use a heredoc for multi-line bodies.
- **Read an issue**: `gh issue view <number> --json number,title,body,labels,comments`.
- **List issues**: `gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'` with appropriate `--label` and `--state` filters.
- **Make an issue a sub-issue of a parent**: `gh issue create --parent <parent> ...`, or `gh issue edit <parent> --add-sub-issue <child>` afterwards (`gh` 2.94+). Older `gh`: `gh api --method POST repos/<owner>/<repo>/issues/<parent>/sub_issues -F sub_issue_id=<child-db-id>` (database id, as in **Blocking** below). Without sub-issues, put `Part of #<parent>` at the top of the child body.
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

Create a GitHub issue.

## When a skill says "fetch the relevant ticket"

Read it as in **Read an issue** above.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a single issue with **child** issues as tickets.

- **Map**: a single issue labelled `wayfinder:map`, holding the Notes / Decisions-so-far / Fog body. `gh issue create --label wayfinder:map`.
- **Child ticket**: an issue linked to the map as a GitHub sub-issue (see **Make an issue a sub-issue of a parent**). Where sub-issues aren't enabled, add the child to a task list in the map body and put `Part of #<map>` at the top of the child body. Labels: `wayfinder:<type>` (`research`/`prototype`/`grilling`/`task`). Once claimed, the ticket is assigned to the driving dev.
- **Blocking**: GitHub's **native issue dependencies**, the canonical, UI-visible representation. Add an edge with `gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`, where `<blocker-db-id>` is the blocker's numeric **database id** (`gh api repos/<owner>/<repo>/issues/<n> --jq .id`, _not_ the `#number` or `node_id`). GitHub reports `issue_dependencies_summary.blocked_by` (open blockers only, the live gate). Where dependencies aren't available, fall back to a `Blocked by: #<n>, #<n>` line at the top of the child body. A ticket is unblocked when every blocker is closed.
- **Frontier query**: list the map's open children (`gh issue list --state open`, scoped to the map's sub-issues / task list), drop any with an open blocker (`issue_dependencies_summary.blocked_by > 0`, or an open issue in the `Blocked by` line) or an assignee; first in map order wins.
- **Claim**: `gh issue edit <n> --add-assignee @me`, the session's first write.
- **Resolve**: `gh issue comment <n> --body "<answer>"`, then `gh issue close <n>`, then append a context pointer (gist + link) to the map's Decisions-so-far.
