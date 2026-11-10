# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual label strings used in this repo's issue tracker.

| Canonical role    | Label in our tracker | Meaning                                       |
| ----------------- | -------------------- | --------------------------------------------- |
| `needs-triage`    | `🔍 triage`          | An idea, or an issue with open questions      |
| `needs-info`      | `❓ question`        | Waiting on the reporter for more information  |
| `ready-for-agent` | `✅ ready`           | Fully specified and ready to be worked on     |
| `ready-for-human` | `✅ ready`           | Fully specified and ready to be worked on     |
| `wontfix`         | `🚫 wontfix`         | Will not be worked on                         |

When a skill mentions a role, use the corresponding label string from this table, copied exactly (emoji included). Both "ready" roles share the `✅ ready` label: this repo does not distinguish who implements an issue.

The `area: …` labels and `🗺️ epic` are not triage labels; they are listed in `CONTRIBUTING.md`. What each state needs is in `CONTRIBUTING.md` → Crear un issue; create and promote issues with the `create-issue` skill.
