# urbanpulse-incident-manager

## Workflow

- Work is tracked in GitHub Issues. Every change starts from an issue.
- Never commit or push directly to `main`. All changes reach `main` through a reviewed pull request.
- Branch from an up-to-date `main` as `<type>/<short-kebab-description>` (e.g. `feat/incident-map`, `fix/login-timeout`). One branch per issue.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/): `<type>: <description>`, imperative and lowercase, no trailing period. Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`. Keep commits small and focused.
- PR titles follow the same convention. Fill in the PR template and link the issue with `Closes #<n>`.
- No AI attribution anywhere: no `Co-Authored-By` trailers or "Generated with" lines in commits, PRs, issues or comments.

## Agent skills

### Issue tracker

Issues live in GitHub Issues (`gh` CLI). See `docs/agents/issue-tracker.md`.

### Triage labels

Generic labels: `triage`, `question`, `ready`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `GLOSSARY.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
