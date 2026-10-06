# Issue and Pull Request Templates Design

## Goal

Standardize GitHub issues and pull requests with English Markdown templates that collect the information needed to triage work, review changes, and link implementation to its issue.

## Files

- `.github/ISSUE_TEMPLATE/bug_report.md`
  - GitHub front matter: `Bug report`, `[Bug]: `, and `bug` label.
  - Sections: Description, Steps to reproduce, Expected result, Actual result, Evidence, and Environment.
- `.github/ISSUE_TEMPLATE/feature_request.md`
  - GitHub front matter: `Feature request`, `[Feature]: `, and `enhancement` label.
  - Sections: Description, Motivation, Proposal, and Alternatives considered.
- `.github/pull_request_template.md`
  - Sections: Description, Change type checkboxes, Testing, Checklist, and Related issue.
  - The checklist covers build success, tests, documentation, secrets, and compatibility.
  - The related issue section provides `Closes #...` for automatic issue closure when merged.

## Behavior

GitHub discovers the two issue templates from `.github/ISSUE_TEMPLATE` when a user creates an issue. GitHub inserts the pull request template into each new pull request. The existing empty pull request template is replaced.

## Validation

- Confirm all three paths exist and contain the agreed English headings and front matter.
- Confirm no credential-like content is introduced.
- Open a pull request from `chore/add-issue-and-pr-templates` that references and closes issue #2.
