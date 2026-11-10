#!/usr/bin/env bash
# PreToolUse hook on Bash: blocks creating issues outside create-issue.sh
# (gh issue create, and gh api POSTs to the issues collection or a createIssue mutation).
# Editing, commenting on and closing existing issues stays allowed.
# Matches gh in command position only and skips heredoc bodies, so text that merely
# mentions these commands (docs, commit messages) passes.
set -euo pipefail

command=$(jq -r '.tool_input.command // ""')

blocked() {
  cat >&2 <<'EOF'
Blocked: issues are created only through the create-issue skill
(.claude/skills/create-issue/SKILL.md), whose script validates and fills every field.
Use `create-issue.sh create` or `create-issue.sh promote <n>`. Editing, commenting on
and closing existing issues stays allowed.
EOF
  exit 2
}

ws='[[:space:]]'
heredoc="<<-?$ws*[\"']?([A-Za-z_][A-Za-z0-9_]*)"
prefix="^$ws*(([A-Za-z_][A-Za-z0-9_]*=[^[:space:]]*|sudo|env|command|exec|time|nohup|xargs)$ws+)*"
gh_issue_create="${prefix}gh$ws+issue$ws+(create|new)($ws|\$)"
gh_api="${prefix}gh$ws+api($ws|\$)"
issues_collection="repos/[^[:space:]\"']+/issues/?([[:space:]\"'?]|\$)"
method_get="(-X|--method)[[:space:]=]*[\"']?(GET|get)"
method_post="(-X|--method)[[:space:]=]*[\"']?(POST|post)"
field_flag="$ws(-f|-F|--field|--raw-field|--input)($ws|=)"

# Drop heredoc bodies, then put each simple command on its own line.
code=$(awk -v re="$heredoc" '
  delim != "" { if ($0 ~ "^[[:space:]]*" delim "[[:space:]]*$") delim = ""; next }
  { print; if (match($0, re)) { d = substr($0, RSTART, RLENGTH); sub(/^<<-?[[:space:]]*["\x27]?/, "", d); delim = d } }
' <<<"$command" | sed -E 's/(&&|\|\||[;&|`]|\$\(|\()/\n/g')

while IFS= read -r segment; do
  [[ $segment =~ $gh_issue_create ]] && blocked
  [[ $segment =~ $gh_api ]] || continue
  [[ $segment == *createIssue* ]] && blocked
  [[ $segment =~ $issues_collection ]] || continue
  [[ $segment =~ $method_get ]] && continue
  if [[ $segment =~ $method_post || $segment =~ $field_flag ]]; then blocked; fi
done <<<"$code"
exit 0
