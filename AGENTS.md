# urbanpulse-incident-manager

## Workflow

- Work is tracked in GitHub Issues. Every change starts from an issue.
- Never commit or push directly to `main`. All changes reach `main` through a reviewed pull request.
- Branch from an up-to-date `main` as `<type>/<short-kebab-description>` (e.g. `feat/incident-map`, `fix/login-timeout`). One branch per issue.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>: <description>`, imperative and lowercase, no trailing period. Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`. Keep commits small and focused.
- Issue and PR titles follow the same convention. Fill in the PR template and link the issue with `Closes #<n>`.
- Updating the docs is part of finishing an issue. Before opening the PR, find every doc that describes what the change touched (`README.md`, `docs/`, `GLOSSARY.md`, ADRs) and edit it in place so it describes the system as it is now: rewrite or delete what became stale, and add text only for something genuinely new. Docs state the current state, never the history of changes; git and PRs keep that.
- No AI attribution anywhere: no `Co-Authored-By` trailers or "Generated with" lines in commits, PRs, issues or comments.

## Quality attributes

Quality attributes (performance, scalability, reliability, availability, observability) drive the design: every feature must stay fluid with millions of incidents, and every improvement is justified by a measurement against a baseline. See the design guidelines in `docs/requirements.md` and the quality attributes and scenarios in `docs/architecture.md`.

When designing an issue, implementing it or reviewing a change, check for:

- List endpoints or screens without a limit, pagination or bounding area.
- Queries without an index that supports them.
- Collections or tables that grow without limit.
- New operations without metrics or logs.

If a concern is deferred, propose a follow-up issue (title and body) and create it only after the user confirms.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Generic labels: `triage`, `question`, `ready`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
