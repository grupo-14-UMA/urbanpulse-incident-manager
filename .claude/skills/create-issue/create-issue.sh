#!/usr/bin/env bash
# Creates and promotes issues with every field that CONTRIBUTING.md → "Crear un issue"
# asks for. Validates everything before the first write, verifies the result after the
# last one and, if a write fails, says which steps were done and which are missing.
# Requires gh 2.94+ and jq. Run with --help for usage.
set -euo pipefail

READY="✅ ready"
TRIAGE="🔍 triage"
EPIC="🗺️ epic"
PREFIXES="feat|fix|docs|style|refactor|test|chore|ci|build|perf"

# Org-level issue fields of grupo-14-UMA. This is the only place these ids live.
PRIORITY_FIELD="IFSS_kgDOAtzvIg"
EFFORT_FIELD="IFSS_kgDOAtzvJQ"
declare -A OPTION_ID=(
  [Urgent]=IFSSO_kgDOBQMSKg [High]=IFSSO_kgDOBQMSKw [Medium]=IFSSO_kgDOBQMSLA [Low]=IFSSO_kgDOBQMSLQ
  [Complex]=IFSSO_kgDOBQMSLg [Moderate]=IFSSO_kgDOBQMSLw [Simple]=IFSSO_kgDOBQMSMA
)
PRIORITIES="Urgent High Medium Low"
EFFORTS="Simple Moderate Complex"

usage() {
  cat <<'EOF'
Usage:
  create-issue.sh create [--dry-run] --title T --body-file F --state ready|triage [fields]
  create-issue.sh create [--dry-run] --batch FILE
  create-issue.sh promote <n> [--dry-run] [--title T] [--body-file F] [fields]

Fields:
  --area KEY          core | data | mobile | panel | repo (or the full "area: …" label)
  --priority P        Urgent | High | Medium | Low
  --effort E          Simple | Moderate | Complex
  --assignee LOGIN    a repo collaborator (the area owner)
  --milestone TITLE   an open milestone, e.g. "Entrega 2"
  --epic N            the delivery epic (an open issue labelled 🗺️ epic)
  --blocked-by N[,N]  issues that must close first (repeatable)

ready needs title, body with "## Acceptance criteria", area, priority, effort,
assignee, milestone and epic. triage needs title and body; other fields optional.
The issue type comes from the title prefix: feat → Feature, fix → Bug, rest → Task.

--batch FILE is a JSON array of objects with the keys above (state, title, area,
priority, effort, assignee, milestone, epic, blocked_by) plus "body" (markdown) or
"body_file" (relative to FILE). A blocked_by entry "@k" points to the k-th item of
the same batch, which must come earlier. Every item is validated before any is created.

promote sets the given fields on an existing 🔍 triage or ✅ ready issue, keeps the
current value of the rest, validates the result as ready and swaps 🔍 triage for ✅ ready.

--dry-run runs the read-only validation against GitHub and prints the writes it would
make, without making any.
EOF
}

die() { echo "error: $*" >&2; exit 1; }

DRY_RUN=0
SPEC='{}'
BATCH=""
REPO="" OWNER="" NAME=""
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------- arguments

parse_flags() {
  local spec='{}'
  while (($#)); do
    case $1 in
      --dry-run) DRY_RUN=1; shift ;;
      -h | --help) usage; exit 0 ;;
      --batch | --title | --state | --area | --priority | --effort | --assignee | --milestone | --epic | --body-file | --blocked-by)
        (($# >= 2)) || die "$1 needs a value"
        case $1 in
          --batch) BATCH=$2 ;;
          --body-file) spec=$(jq --arg v "$2" '.body_file = $v' <<<"$spec") ;;
          --blocked-by) spec=$(jq --arg v "$2" '.blocked_by += ($v | split(",") | map(select(. != "")))' <<<"$spec") ;;
          *) spec=$(jq --arg k "${1#--}" --arg v "$2" '.[$k] = $v' <<<"$spec") ;;
        esac
        shift 2 ;;
      *) die "unknown argument: $1 (see --help)" ;;
    esac
  done
  SPEC=$spec
}

# Reads body_file into body and normalises the field formats. $1 spec, $2 base dir.
normalize() {
  local s=$1 dir=$2 file
  file=$(jq -r '.body_file // empty' <<<"$s")
  if [[ -n $file ]]; then
    [[ $file == /* ]] || file="$dir/$file"
    [[ -f $file ]] || die "body file not found: $file"
    s=$(jq --rawfile b "$file" '.body = $b | del(.body_file)' <<<"$s")
  fi
  jq '
    def cap: if type == "string" and length > 0 then (.[:1] | ascii_upcase) + (.[1:] | ascii_downcase) else . end;
    def num: tostring | ltrimstr("#");
    .state = ((.state // "") | ascii_downcase)
    | .priority = (.priority | cap)
    | .effort = (.effort | cap)
    | (if .epic != null then .epic = (.epic | num) else . end)
    | .blocked_by = ((.blocked_by // []) | map(num) | unique)
  ' <<<"$s"
}

# ---------------------------------------------------------------- lookups

LABELS="" MILESTONES=""
declare -A COLLAB=() ISSUE=()

load_repo_data() {
  command -v jq >/dev/null || die "jq is required"
  [[ $(gh issue edit --help 2>/dev/null) == *--add-blocked-by* ]] || die "gh 2.94+ is required (gh issue edit --add-blocked-by)"
  REPO=$(gh repo view --json owner,name --jq '.owner.login + "/" + .name')
  OWNER=${REPO%/*} NAME=${REPO#*/}
  LABELS=$(gh label list --limit 200 --json name --jq '.[].name')
  MILESTONES=$(gh api "repos/$REPO/milestones?state=open&per_page=100" --jq '.[].title')
}

get() { jq -r --arg k "$1" '.[$k] // "" | tostring' <<<"$2"; }

# Prints the full area label for a key ("mobile") or a full label; fails if none exists.
area_label() {
  local v=$1
  if [[ $v == area:* ]]; then
    grep -qxF -- "$v" <<<"$LABELS" && echo "$v"
    return
  fi
  local l
  l=$(awk -v s=" $v" 'index($0, "area: ") == 1 && length($0) >= length(s) && substr($0, length($0) - length(s) + 1) == s' <<<"$LABELS" | head -1)
  [[ -n $l ]] && echo "$l"
}

is_collaborator() {
  local u=$1
  if [[ -z ${COLLAB[$u]+x} ]]; then
    if gh api "repos/$REPO/collaborators/$u" --silent 2>/dev/null; then COLLAB[$u]=1; else COLLAB[$u]=0; fi
  fi
  [[ ${COLLAB[$u]} == 1 ]]
}

# Caches {state, pr, labels} of issue $1; fails if it does not exist.
issue_info() {
  local n=$1
  if [[ -z ${ISSUE[$n]+x} ]]; then
    ISSUE[$n]=$(gh api "repos/$REPO/issues/$n" --jq '{state, pr: (.pull_request != null), labels: [.labels[].name]}' 2>/dev/null) || ISSUE[$n]=""
  fi
  [[ -n ${ISSUE[$n]} ]]
}

type_for() {
  case ${1%%:*} in
    feat) echo Feature ;;
    fix) echo Bug ;;
    *) echo Task ;;
  esac
}

Q_ISSUE='query($owner: String!, $name: String!, $n: Int!) { repository(owner: $owner, name: $name) { issue(number: $n) {
  number title body state url
  labels(first: 30) { nodes { name } }
  assignees(first: 10) { nodes { login } }
  milestone { title }
  issueType { name }
  parent { number }
  issueFieldValues(first: 10) { nodes { ... on IssueFieldSingleSelectValue { name field { ... on IssueFieldCommon { name } } } } }
  blockedBy(first: 50) { nodes { number } }
} } }'

fetch_issue() {
  gh api graphql -f query="$Q_ISSUE" -f owner="$OWNER" -f name="$NAME" -F n="$1" --jq '.data.repository.issue'
}

# ---------------------------------------------------------------- validation

ERRORS=()
err() { ERRORS+=("$1"); }

# $1 normalised spec, $2 label for messages, $3 batch position (1-based; 0 for promote).
validate() {
  local s=$1 who=$2 pos=$3
  local title state body area priority effort assignee milestone epic b
  title=$(get title "$s") state=$(get state "$s") body=$(get body "$s")
  area=$(get area "$s") priority=$(get priority "$s") effort=$(get effort "$s")
  assignee=$(get assignee "$s") milestone=$(get milestone "$s") epic=$(get epic "$s")

  [[ $state == ready || $state == triage ]] || err "$who: state must be ready or triage"
  if [[ ! $title =~ ^($PREFIXES):\ [^[:space:]] || $title == *. ]]; then
    err "$who: title must be '<type>: <description>' without a trailing period, type one of ${PREFIXES//|/, }"
  fi
  [[ -n ${body//[[:space:]]/} ]] || err "$who: body is empty"

  local required=()
  if [[ $state == ready ]]; then
    [[ -n $area ]] || required+=(area)
    [[ -n $priority ]] || required+=(priority)
    [[ -n $effort ]] || required+=(effort)
    [[ -n $assignee ]] || required+=(assignee)
    [[ -n $milestone ]] || required+=(milestone)
    [[ -n $epic ]] || required+=(epic)
    ((${#required[@]} == 0)) || err "$who: a ready issue needs $(IFS=,; echo "${required[*]}" | sed 's/,/, /g')"
    grep -qE '^##[[:space:]]+Acceptance criteria[[:space:]]*$' <<<"$body" || err "$who: a ready issue needs a '## Acceptance criteria' section in the body"
  fi

  [[ -z $area ]] || area_label "$area" >/dev/null || err "$who: no label for area '$area'"
  [[ -z $priority || " $PRIORITIES " == *" $priority "* ]] || err "$who: priority must be one of $PRIORITIES"
  [[ -z $effort || " $EFFORTS " == *" $effort "* ]] || err "$who: effort must be one of $EFFORTS"
  [[ -z $assignee ]] || is_collaborator "$assignee" || err "$who: $assignee is not a collaborator"
  [[ -z $milestone ]] || grep -qxF -- "$milestone" <<<"$MILESTONES" || err "$who: no open milestone '$milestone'"
  if [[ -n $epic ]]; then
    if ! [[ $epic =~ ^[0-9]+$ ]] || ! issue_info "$epic"; then
      err "$who: epic #$epic does not exist"
    elif ! jq -e --arg l "$EPIC" '.state == "open" and (.labels | index($l))' <<<"${ISSUE[$epic]}" >/dev/null; then
      err "$who: #$epic is not an open issue labelled $EPIC"
    fi
  fi

  while read -r b; do
    [[ -n $b ]] || continue
    if [[ $b == @* ]]; then
      local k=${b#@}
      if ((pos == 0)); then
        err "$who: blocked by $b only works inside a batch"
      elif ! [[ $k =~ ^[0-9]+$ ]] || ((k < 1 || k >= pos)); then
        err "$who: blocked by $b must point to an earlier item of the batch"
      fi
    elif ! [[ $b =~ ^[0-9]+$ ]] || ! issue_info "$b" || [[ $(jq -r .pr <<<"${ISSUE[$b]}") == true ]]; then
      err "$who: blocked by #$b is not an existing issue"
    fi
  done < <(jq -r '.blocked_by[]' <<<"$s")
}

fail_validation() {
  printf '%s\n' "${ERRORS[@]/#/error: }" >&2
  echo "Nothing was $1." >&2
  exit 1
}

describe() {
  local s=$1
  jq -r --arg type "$(type_for "$(get title "$s")")" --arg area "$(area_label "$(get area "$s")" 2>/dev/null || true)" '
    "  \(.title)",
    "    state: \(if .state == "ready" then "✅ ready" else "🔍 triage" end) · type: \($type)"
      + (if $area != "" then " · \($area)" else "" end),
    "    assignee: \(.assignee // "—") · milestone: \(.milestone // "—") · epic: \(if .epic then "#\(.epic)" else "—" end)",
    "    priority: \(.priority // "—") · effort: \(.effort // "—") · blocked by: \(if (.blocked_by | length) > 0 then (.blocked_by | map(if startswith("@") then "batch item \(.[1:])" else "#\(.)" end) | join(", ")) else "—" end)"
  ' <<<"$s"
}

# ---------------------------------------------------------------- writes

PLANNED=() STEP_I=0
LAST_URL=""

# Runs one planned write. $1 description (must equal PLANNED[STEP_I]), rest the command.
step() {
  local desc=$1
  shift
  if ((DRY_RUN)); then
    echo "  [dry-run] would $desc"
    STEP_I=$((STEP_I + 1))
    return 0
  fi
  if "$@" >"$TMP/out" 2>"$TMP/err"; then
    echo "  ✓ $desc"
    STEP_I=$((STEP_I + 1))
    return 0
  fi
  echo "  ✗ $desc" >&2
  sed 's/^/      /' "$TMP/err" >&2
  return 1
}

partial_report() {
  local i
  {
    echo "error: a step failed${1:+; $1 is incomplete}"
    echo "Done:"
    if ((STEP_I == 0)); then echo "  (nothing)"; fi
    for ((i = 0; i < STEP_I; i++)); do echo "  - ${PLANNED[i]}"; done
    echo "Missing:"
    for ((i = STEP_I; i < ${#PLANNED[@]}; i++)); do echo "  - ${PLANNED[i]}"; done
    echo "Fix the cause and finish it with: create-issue.sh promote <n> (or by hand if it is triage)."
  } >&2
}

set_fields() { # $1 number, $2 priority, $3 effort
  local node fields=""
  node=$(gh api "repos/$REPO/issues/$1" --jq .node_id)
  [[ -z $2 ]] || fields+="{ fieldId: \"$PRIORITY_FIELD\", singleSelectOptionId: \"${OPTION_ID[$2]}\" } "
  [[ -z $3 ]] || fields+="{ fieldId: \"$EFFORT_FIELD\", singleSelectOptionId: \"${OPTION_ID[$3]}\" } "
  gh api graphql -f id="$node" -f query="mutation(\$id: ID!) { setIssueFieldValue(input: { issueId: \$id, issueFields: [ $fields] }) { clientMutationId } }"
}

fields_desc() { # $1 priority, $2 effort
  local d=()
  [[ -z $1 ]] || d+=("Priority=$1")
  [[ -z $2 ]] || d+=("Effort=$2")
  echo "set ${d[*]}"
}

# Checks the issue against the expected values. $1 number, $2 expected spec (resolved).
verify() {
  local n=$1 e=$2 actual report
  ((DRY_RUN)) && return 0
  actual=$(fetch_issue "$n")
  LAST_URL=$(jq -r .url <<<"$actual")
  report=$(jq -r -n --argjson a "$actual" --argjson e "$e" --arg ready "$READY" --arg triage "$TRIAGE" '
    ($a.labels.nodes | map(.name)) as $labels
    | ([$a.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {}) as $f
    | ($e.state == "ready") as $isReady
    | [
        {c: "state", ok: (($labels | index(if $isReady then $ready else $triage end)) != null
                          and ($labels | index(if $isReady then $triage else $ready end)) == null)},
        {c: "type", ok: ($a.issueType.name == $e.type)},
        {c: "area", ok: (if $e.area_label != "" then ([$labels[] | select(startswith("area: "))] == [$e.area_label]) else null end)},
        {c: "assignee", ok: (if ($e.assignee // "") != "" then ([$a.assignees.nodes[].login] | index($e.assignee)) != null else null end)},
        {c: "milestone", ok: (($a.milestone.title // "") == ($e.milestone // ""))},
        {c: "parent", ok: (if ($e.epic // "") != "" then (($a.parent.number // 0) | tostring) == $e.epic else null end)},
        {c: "priority", ok: (if ($e.priority // "") != "" then $f.Priority == $e.priority else null end)},
        {c: "effort", ok: (if ($e.effort // "") != "" then $f.Effort == $e.effort else null end)},
        {c: "blocked by", ok: (if ($e.blocked_by | length) > 0 then (($e.blocked_by | map(tonumber)) - [$a.blockedBy.nodes[].number] | length == 0) else null end)}
      ]
    | map(select(.ok != null) | "\(if .ok then "✓" else "✗" end) \(.c)") | join("  ")
  ')
  echo "  verify #$n: $report"
  if [[ $report == *✗* ]]; then
    echo "error: #$n ($LAST_URL) does not match what was asked: see the ✗ checks above." >&2
    return 1
  fi
  echo "  $LAST_URL"
}

# Adds what verify needs and replaces "@k" with the numbers created in this batch.
expected() { # $1 spec, $2 JSON array of created numbers
  jq --argjson created "$2" --arg type "$(type_for "$(get title "$1")")" \
    --arg area "$(area_label "$(get area "$1")" 2>/dev/null || true)" '
    .type = $type | .area_label = $area
    | .blocked_by = (.blocked_by | map(if startswith("@") and ($created[(.[1:] | tonumber) - 1] // 0) > 0
                                       then ($created[(.[1:] | tonumber) - 1] | tostring) else . end))
  ' <<<"$1"
}

# ---------------------------------------------------------------- create

create_one() { # $1 spec (expected), sets CREATED_N
  local s=$1 title state area assignee milestone epic priority effort b
  title=$(get title "$s") state=$(get state "$s") area=$(get area_label "$s")
  assignee=$(get assignee "$s") milestone=$(get milestone "$s") epic=$(get epic "$s")
  priority=$(get priority "$s") effort=$(get effort "$s")
  local blockers
  mapfile -t blockers < <(jq -r '.blocked_by[]' <<<"$s")

  PLANNED=("create the issue with labels, assignee, milestone and type") STEP_I=0
  [[ -z $epic ]] || PLANNED+=("make it a sub-issue of #$epic")
  for b in "${blockers[@]}"; do
    if [[ $b == @* ]]; then PLANNED+=("mark it blocked by batch item ${b#@}"); else PLANNED+=("mark it blocked by #$b"); fi
  done
  [[ -z $priority && -z $effort ]] || PLANNED+=("$(fields_desc "$priority" "$effort")")

  local args=(issue create --title "$title" --body-file "$TMP/body" --type "$(get type "$s")"
    --label "$([[ $state == ready ]] && echo "$READY" || echo "$TRIAGE")")
  [[ -z $area ]] || args+=(--label "$area")
  [[ -z $assignee ]] || args+=(--assignee "$assignee")
  [[ -z $milestone ]] || args+=(--milestone "$milestone")
  get body "$s" >"$TMP/body"

  if ! step "${PLANNED[0]}" gh "${args[@]}"; then
    local url
    url=$(grep -oE 'https://github\.com/[^ ]+/issues/[0-9]+' "$TMP/out" | tail -1 || true)
    partial_report "${url:+the issue was created anyway at $url and}"
    return 1
  fi
  if ((DRY_RUN)); then
    CREATED_N=0
  else
    CREATED_N=$(grep -oE '/issues/[0-9]+' "$TMP/out" | tail -1 | grep -oE '[0-9]+')
    echo "  #$CREATED_N created"
  fi
  local n=$CREATED_N
  if [[ -n $epic ]]; then step "${PLANNED[STEP_I]}" gh issue edit "$n" --parent "$epic" || { partial_report "#$n"; return 1; }; fi
  for b in "${blockers[@]}"; do
    step "${PLANNED[STEP_I]}" gh issue edit "$n" --add-blocked-by "$b" || { partial_report "#$n"; return 1; }
  done
  if [[ -n $priority || -n $effort ]]; then
    step "${PLANNED[STEP_I]}" set_fields "$n" "$priority" "$effort" || { partial_report "#$n"; return 1; }
  fi
  verify "$n" "$s"
}

cmd_create() {
  parse_flags "$@"
  local specs dir="."
  if [[ -n $BATCH ]]; then
    [[ $SPEC == '{}' ]] || die "--batch cannot be combined with other fields"
    [[ -f $BATCH ]] || die "batch file not found: $BATCH"
    specs=$(jq -c 'if type == "array" and length > 0 then .[] else error("the batch must be a non-empty JSON array") end' "$BATCH") || die "invalid batch file: $BATCH"
    dir=$(dirname "$BATCH")
  else
    specs=$(jq -c . <<<"$SPEC")
  fi

  load_repo_data
  local -a items=()
  local line pos=0
  while IFS= read -r line; do
    pos=$((pos + 1))
    items+=("$(normalize "$line" "$dir")")
  done <<<"$specs"
  for ((pos = 1; pos <= ${#items[@]}; pos++)); do
    validate "${items[pos - 1]}" "item $pos ($(get title "${items[pos - 1]}"))" "$pos"
  done
  ((${#ERRORS[@]} == 0)) || fail_validation created

  local created='[]' k
  for ((k = 1; k <= ${#items[@]}; k++)); do
    echo "[$k/${#items[@]}]"
    describe "${items[k - 1]}"
    local e
    e=$(expected "${items[k - 1]}" "$created")
    if ! create_one "$e"; then
      local done_list
      done_list=$(jq -r 'map("#\(.)") | join(", ")' <<<"$created")
      ((k == 1)) || echo "Created earlier in this batch: $done_list. Items $k-${#items[@]} were not finished." >&2
      exit 1
    fi
    created=$(jq --argjson n "${CREATED_N:-0}" '. + [$n]' <<<"$created")
  done
  ((DRY_RUN)) && echo "Dry run: nothing was created."
  return 0
}

# ---------------------------------------------------------------- promote

cmd_promote() {
  local n=${1:-}
  [[ $n =~ ^#?[0-9]+$ ]] || die "usage: create-issue.sh promote <n> [fields]"
  n=${n#\#}
  shift
  parse_flags "$@"
  [[ -z $BATCH ]] || die "promote takes no --batch"
  [[ $(jq -r '.state // ""' <<<"$SPEC") == "" ]] || die "promote always leaves the issue ready; drop --state"

  load_repo_data
  local cur
  cur=$(fetch_issue "$n" 2>/dev/null) && [[ $cur != null ]] || die "issue #$n not found"
  local labels
  labels=$(jq -r '.labels.nodes[].name' <<<"$cur")
  [[ $(jq -r .state <<<"$cur") == OPEN ]] || die "#$n is closed"
  ! grep -qxF -- "$EPIC" <<<"$labels" || die "#$n is an epic; epics are not promoted"
  grep -qxF -- "$TRIAGE" <<<"$labels" || grep -qxF -- "$READY" <<<"$labels" || die "#$n has neither $TRIAGE nor $READY"

  # Current values, overlaid by the given flags.
  local areas
  areas=$(grep '^area: ' <<<"$labels" || true)
  if [[ $(grep -c . <<<"$areas") -gt 1 && $(jq -r '.area // ""' <<<"$SPEC") == "" ]]; then
    err "#$n has several area labels; pass --area"
  fi
  local s
  s=$(jq -n --argjson c "$cur" --argjson f "$SPEC" --arg area "$(head -1 <<<"$areas")" '
    ([$c.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {}) as $fv
    | {
        title: $c.title, body: $c.body, state: "ready",
        area: (if $area != "" then $area else null end),
        priority: $fv.Priority, effort: $fv.Effort,
        assignee: ($c.assignees.nodes[0].login // null),
        milestone: ($c.milestone.title // null),
        epic: ($c.parent.number // null)
      }
    + ($f | del(.blocked_by))
    | .blocked_by = ([$c.blockedBy.nodes[].number | tostring] + ($f.blocked_by // []))
  ')
  s=$(normalize "$s" ".")
  validate "$s" "#$n" 0
  ((${#ERRORS[@]} == 0)) || fail_validation changed
  s=$(expected "$s" '[]')

  echo "#$n →"
  describe "$s"

  # Only the differences become writes.
  local edit=() area cur_assignees
  area=$(get area_label "$s")
  local edit_desc=() line
  [[ $(get title "$s") == $(jq -r .title <<<"$cur") ]] || { edit+=(--title "$(get title "$s")"); edit_desc+=("title"); }
  if [[ $(jq -r '.body_file // ""' <<<"$SPEC") != "" ]]; then
    get body "$s" >"$TMP/body"
    edit+=(--body-file "$TMP/body")
    edit_desc+=("body")
  fi
  grep -qxF -- "$READY" <<<"$labels" || { edit+=(--add-label "$READY"); edit_desc+=("+$READY"); }
  ! grep -qxF -- "$TRIAGE" <<<"$labels" || { edit+=(--remove-label "$TRIAGE"); edit_desc+=("-$TRIAGE"); }
  grep -qxF -- "$area" <<<"$labels" || { edit+=(--add-label "$area"); edit_desc+=("+$area"); }
  while IFS= read -r line; do
    [[ -z $line || $line == "$area" ]] || { edit+=(--remove-label "$line"); edit_desc+=("-$line"); }
  done <<<"$areas"
  cur_assignees=$(jq -r '.assignees.nodes[].login' <<<"$cur")
  if [[ $(jq -r '.assignee // ""' <<<"$SPEC") != "" ]]; then
    grep -qxF -- "$(get assignee "$s")" <<<"$cur_assignees" || { edit+=(--add-assignee "$(get assignee "$s")"); edit_desc+=("+@$(get assignee "$s")"); }
    while IFS= read -r line; do
      [[ -z $line || $line == "$(get assignee "$s")" ]] || { edit+=(--remove-assignee "$line"); edit_desc+=("-@$line"); }
    done <<<"$cur_assignees"
  fi
  [[ $(get milestone "$s") == $(jq -r '.milestone.title // ""' <<<"$cur") ]] || { edit+=(--milestone "$(get milestone "$s")"); edit_desc+=("milestone $(get milestone "$s")"); }

  local type cur_type epic cur_epic priority effort cur_fields new_blockers=()
  type=$(get type "$s") cur_type=$(jq -r '.issueType.name // ""' <<<"$cur")
  epic=$(get epic "$s") cur_epic=$(jq -r '.parent.number // "" | tostring' <<<"$cur")
  priority=$(get priority "$s") effort=$(get effort "$s")
  cur_fields=$(jq -r '[.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {} | "\(.Priority // "")|\(.Effort // "")"' <<<"$cur")
  mapfile -t new_blockers < <(jq -r --argjson c "$cur" '.blocked_by - [$c.blockedBy.nodes[].number | tostring] | .[]' <<<"$s")

  PLANNED=() STEP_I=0
  ((${#edit[@]} == 0)) || PLANNED+=("edit $(printf '%s, ' "${edit_desc[@]}" | sed 's/, $//')")
  [[ $type == "$cur_type" ]] || PLANNED+=("set type $type")
  [[ $epic == "$cur_epic" ]] || PLANNED+=("make it a sub-issue of #$epic")
  for b in "${new_blockers[@]}"; do PLANNED+=("mark it blocked by #$b"); done
  [[ "$priority|$effort" == "$cur_fields" ]] || PLANNED+=("$(fields_desc "$priority" "$effort")")

  if ((${#PLANNED[@]} == 0)); then
    echo "  #$n already has every value; nothing to change."
  else
    if ((${#edit[@]})); then step "${PLANNED[STEP_I]}" gh issue edit "$n" "${edit[@]}" || { partial_report "#$n"; exit 1; }; fi
    if [[ $type != "$cur_type" ]]; then step "${PLANNED[STEP_I]}" gh issue edit "$n" --type "$type" || { partial_report "#$n"; exit 1; }; fi
    if [[ $epic != "$cur_epic" ]]; then step "${PLANNED[STEP_I]}" gh issue edit "$n" --parent "$epic" || { partial_report "#$n"; exit 1; }; fi
    for b in "${new_blockers[@]}"; do
      step "${PLANNED[STEP_I]}" gh issue edit "$n" --add-blocked-by "$b" || { partial_report "#$n"; exit 1; }
    done
    if [[ "$priority|$effort" != "$cur_fields" ]]; then
      step "${PLANNED[STEP_I]}" set_fields "$n" "$priority" "$effort" || { partial_report "#$n"; exit 1; }
    fi
  fi
  verify "$n" "$s" || exit 1
  ((DRY_RUN)) && echo "Dry run: nothing was changed."
  return 0
}

# ---------------------------------------------------------------- main

case ${1:-} in
  create) shift; cmd_create "$@" ;;
  promote) shift; cmd_promote "$@" ;;
  -h | --help | "") usage ;;
  *) die "unknown command: $1 (see --help)" ;;
esac
