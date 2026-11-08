# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Canonical role    | Label in our tracker | Meaning                                       |
| ----------------- | -------------------- | --------------------------------------------- |
| `needs-triage`    | `🔍 triage`          | Needs to be evaluated by the team             |
| `needs-info`      | `❓ question`        | Waiting on the reporter for more information  |
| `ready-for-agent` | `✅ ready`           | Fully specified and ready to be worked on     |
| `ready-for-human` | `✅ ready`           | Fully specified and ready to be worked on     |
| `wontfix`         | `🚫 wontfix`         | Will not be worked on                         |

When a skill mentions a role, use the corresponding label string from this table, copied exactly (emoji included). Both "ready" roles share the `✅ ready` label: this repo does not distinguish who implements an issue.

The `priority: …` and `area: …` labels and `🗺️ epic` are not triage labels; they are listed in `CONTRIBUTING.md`. Every new issue also needs them: follow the checklist in `docs/agents/issue-tracker.md`.
