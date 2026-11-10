#!/usr/bin/env bash
# Creates and updates issues and milestones with every field that CONTRIBUTING.md →
# "Crear un issue" asks for. Validates everything before the first write, verifies the
# result after the last one and, if a write fails, says which steps were done and which
# are missing. Requires gh 2.94+ and jq. Run with --help for usage.
set -euo pipefail

READY="✅ ready"
TRIAGE="🔍 triage"
EPIC="🗺️ epic"
PREFIXES="feat|fix|docs|style|refactor|test|chore|ci|build|perf"

# Org-level issue fields. This is the only place these ids live.
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
  create-issue.sh create    [--dry-run] --state ready|triage|epic --title T --body-file F [fields]
  create-issue.sh create    [--dry-run] --batch FILE
  create-issue.sh update  N [--dry-run] [--state ready|triage] [--title T] [--body-file F] [fields]
  create-issue.sh promote N [--dry-run] [--title T] [--body-file F] [fields]
  create-issue.sh milestone [--dry-run] --title T [--due-on YYYY-MM-DD] [--description D]

Fields:
  --area KEY             an area label, by its last word or in full ("area: …")
  --priority P           Urgent | High | Medium | Low
  --effort E             Simple | Moderate | Complex
  --assignee LOGIN       a repo collaborator
  --milestone TITLE      an open milestone; "current" is the open one with the lowest number
  --epic N               the parent epic (an open issue labelled 🗺️ epic)
  --blocked-by N[,N]     issues that must close first (repeatable)
  --remove-blocked-by N  update only: drop a blocked-by link (repeatable)

States:
  ready   needs title, body with "## Acceptance criteria", area, priority, effort,
          assignee, milestone and epic. Labels: ✅ ready + the area.
  triage  needs title and body; every other field is optional. Label: 🔍 triage.
  epic    needs title, body and milestone; takes no area, priority, effort or epic.
          Labels: 🗺️ epic + ✅ ready.
The issue type comes from the title prefix: feat → Feature, fix → Bug, rest → Task.

--batch FILE is a JSON array of objects with the field names above (state, title,
area, priority, effort, assignee, milestone, epic, blocked_by) plus "body" (markdown)
or "body_file" (relative to FILE). "@k" as epic or in blocked_by points to the k-th
item of the same batch, which must come earlier (and be an epic, for epic). Every item
is validated before any is created.

update changes the given fields of an open issue and keeps the rest. An empty value
removes a field (--milestone "" sends an idea back to the inbox). The issue keeps its
state unless --state is given, and is validated against the state it ends in. Epics
stay epics. promote N is update N --state ready.

milestone creates an open milestone and fails if one with that title already exists.

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
      --batch | --title | --state | --area | --priority | --effort | --assignee | --milestone | --epic | \
        --body-file | --blocked-by | --remove-blocked-by | --due-on | --description)
        (($# >= 2)) || die "$1 needs a value"
        case $1 in
          --batch) BATCH=$2 ;;
          --blocked-by | --remove-blocked-by)
            local key=${1#--}
            spec=$(jq --arg k "${key//-/_}" --arg v "$2" '.[$k] += ($v | split(",") | map(select(. != "")))' <<<"$spec") ;;
          *)
            local key=${1#--}
            spec=$(jq --arg k "${key//-/_}" --arg v "$2" '.[$k] = $v' <<<"$spec") ;;
        esac
        shift 2 ;;
      *) die "unknown argument: $1 (see --help)" ;;
    esac
  done
  SPEC=$spec
}

# Reads body_file into body and normalises the formats of the keys present. $1 spec, $2 base dir.
normalize() {
  local s=$1 dir=$2 file
  file=$(jq -r '.body_file // empty' <<<"$s")
  if [[ -n $file ]]; then
    [[ $file == /* ]] || file="$dir/$file"
    [[ -f $file ]] || die "body file not found: $file"
    s=$(jq --rawfile b "$file" '.body = $b | del(.body_file)' <<<"$s")
  fi
  jq --arg current "$CURRENT_MILESTONE" '
    def cap: if type == "string" and length > 0 then (.[:1] | ascii_upcase) + (.[1:] | ascii_downcase) else (. // "") end;
    def num: tostring | ltrimstr("#");
    def fix(k; f): if has(k) then .[k] |= f else . end;
    fix("state"; (. // "") | ascii_downcase)
    | fix("priority"; cap) | fix("effort"; cap)
    | fix("epic"; if . == null then "" else num end)
    | fix("milestone"; if . == "current" then $current else (. // "") end)
    | fix("area"; . // "") | fix("assignee"; . // "")
    | fix("blocked_by"; map(num) | unique)
    | fix("remove_blocked_by"; map(num) | unique)
  ' <<<"$s"
}

# ---------------------------------------------------------------- lookups

LABELS="" MILESTONES="" CURRENT_MILESTONE=""
declare -A COLLAB=() ISSUE=()

load_repo_data() {
  command -v jq >/dev/null || die "jq is required"
  [[ $(gh issue edit --help 2>/dev/null) == *--add-blocked-by* ]] || die "gh 2.94+ is required (gh issue edit --add-blocked-by)"
  REPO=$(gh repo view --json owner,name --jq '.owner.login + "/" + .name')
  OWNER=${REPO%/*} NAME=${REPO#*/}
  LABELS=$(gh label list --limit 200 --json name --jq '.[].name')
  MILESTONES=$(gh api "repos/$REPO/milestones?state=open&per_page=100" --jq 'sort_by(.number) | .[].title')
  CURRENT_MILESTONE=$(head -1 <<<"$MILESTONES")
}

get() { jq -r --arg k "$1" '.[$k] // "" | tostring' <<<"$2"; }

# Prints the full area label for its last word or a full label; fails if none exists.
area_label() {
  local v=$1
  [[ -n $v ]] || return 1
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

# "@k" → "batch item k", N → "#N".
ref() { if [[ $1 == @* ]]; then echo "batch item ${1#@}"; else echo "#$1"; fi; }

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
BATCH_STATES=()

# $1 normalised spec, $2 label for messages, $3 batch position (1-based; 0 outside a batch).
validate() {
  local s=$1 who=$2 pos=$3
  local title state body area priority effort assignee milestone epic b
  title=$(get title "$s") state=$(get state "$s") body=$(get body "$s")
  area=$(get area "$s") priority=$(get priority "$s") effort=$(get effort "$s")
  assignee=$(get assignee "$s") milestone=$(get milestone "$s") epic=$(get epic "$s")

  [[ $state == ready || $state == triage || $state == epic ]] || err "$who: state must be ready, triage or epic"
  if [[ ! $title =~ ^($PREFIXES):\ [^[:space:]] || $title == *. ]]; then
    err "$who: title must be '<type>: <description>' without a trailing period, type one of ${PREFIXES//|/, }"
  fi
  [[ -n ${body//[[:space:]]/} ]] || err "$who: body is empty"

  local required=() forbidden=()
  case $state in
    ready)
      [[ -n $area ]] || required+=(area)
      [[ -n $priority ]] || required+=(priority)
      [[ -n $effort ]] || required+=(effort)
      [[ -n $assignee ]] || required+=(assignee)
      [[ -n $milestone ]] || required+=(milestone)
      [[ -n $epic ]] || required+=(epic)
      grep -qE '^##[[:space:]]+Acceptance criteria[[:space:]]*$' <<<"$body" || err "$who: a ready issue needs a '## Acceptance criteria' section in the body"
      ;;
    epic)
      [[ -n $milestone ]] || required+=(milestone)
      [[ -z $area ]] || forbidden+=(area)
      [[ -z $priority ]] || forbidden+=(priority)
      [[ -z $effort ]] || forbidden+=(effort)
      [[ -z $epic ]] || forbidden+=(epic)
      ((${#forbidden[@]} == 0)) || err "$who: an epic takes no $(IFS=,; echo "${forbidden[*]}" | sed 's/,/, /g')"
      ;;
  esac
  ((${#required[@]} == 0)) || err "$who: $([[ $state == epic ]] && echo "an epic" || echo "a $state issue") needs $(IFS=,; echo "${required[*]}" | sed 's/,/, /g')"

  [[ -z $area ]] || area_label "$area" >/dev/null || err "$who: no label for area '$area'"
  [[ -z $priority || " $PRIORITIES " == *" $priority "* ]] || err "$who: priority must be one of $PRIORITIES"
  [[ -z $effort || " $EFFORTS " == *" $effort "* ]] || err "$who: effort must be one of $EFFORTS"
  [[ -z $assignee ]] || is_collaborator "$assignee" || err "$who: $assignee is not a collaborator"
  [[ -z $milestone ]] || grep -qxF -- "$milestone" <<<"$MILESTONES" || err "$who: no open milestone '$milestone'"

  if [[ $epic == @* ]]; then
    local k=${epic#@}
    if ((pos == 0)); then
      err "$who: epic $epic only works inside a batch"
    elif ! [[ $k =~ ^[0-9]+$ ]] || ((k < 1 || k >= pos)); then
      err "$who: epic $epic must point to an earlier item of the batch"
    elif [[ ${BATCH_STATES[k - 1]} != epic ]]; then
      err "$who: epic $epic points to a batch item that is not an epic"
    fi
  elif [[ -n $epic ]]; then
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
  done < <(jq -r '(.blocked_by // [])[]' <<<"$s")
}

fail_validation() {
  printf '%s\n' "${ERRORS[@]/#/error: }" >&2
  echo "Nothing was $1." >&2
  exit 1
}

describe() {
  local s=$1
  jq -r --arg type "$(type_for "$(get title "$s")")" --arg area "$(area_label "$(get area "$s")" 2>/dev/null || true)" '
    def ref: if startswith("@") then "batch item \(.[1:])" else "#\(.)" end;
    def v(k): if (.[k] // "") == "" then "—" else .[k] end;
    "  \(.title)",
    "    state: \({ready: "✅ ready", triage: "🔍 triage", epic: "🗺️ epic + ✅ ready"}[.state]) · type: \($type)"
      + (if $area != "" then " · \($area)" else "" end),
    "    assignee: \(v("assignee")) · milestone: \(v("milestone")) · epic: \(if (.epic // "") != "" then (.epic | ref) else "—" end)",
    "    priority: \(v("priority")) · effort: \(v("effort")) · blocked by: \(if (.blocked_by | length) > 0 then (.blocked_by | map(ref) | join(", ")) else "—" end)"
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
    echo "Fix the cause and finish it with: create-issue.sh update <n> and the missing values."
  } >&2
}

# $1 number, $2 / $3 Priority / Effort: a value to set, "-" to clear, "" to leave alone.
set_fields() {
  local node fields="" f v
  node=$(gh api "repos/$REPO/issues/$1" --jq .node_id)
  for f in PRIORITY EFFORT; do
    if [[ $f == PRIORITY ]]; then v=$2; else v=$3; fi
    local id_var="${f}_FIELD"
    case $v in
      "") ;;
      -) fields+="{ fieldId: \"${!id_var}\", delete: true } " ;;
      *) fields+="{ fieldId: \"${!id_var}\", singleSelectOptionId: \"${OPTION_ID[$v]}\" } " ;;
    esac
  done
  gh api graphql -f id="$node" -f query="mutation(\$id: ID!) { setIssueFieldValue(input: { issueId: \$id, issueFields: [ $fields] }) { clientMutationId } }"
}

fields_desc() { # same arguments as set_fields, without the number
  local d=()
  case $1 in "") ;; -) d+=("clear Priority") ;; *) d+=("set Priority=$1") ;; esac
  case $2 in "") ;; -) d+=("clear Effort") ;; *) d+=("set Effort=$2") ;; esac
  printf '%s, ' "${d[@]}" | sed 's/, $//'
}

# Checks the issue against the expected values. $1 number, $2 expected spec (resolved).
verify() {
  local n=$1 e=$2 actual report
  ((DRY_RUN)) && return 0
  actual=$(fetch_issue "$n")
  LAST_URL=$(jq -r .url <<<"$actual")
  report=$(jq -r -n --argjson a "$actual" --argjson e "$e" --arg ready "$READY" --arg triage "$TRIAGE" --arg epic "$EPIC" '
    ($a.labels.nodes | map(.name)) as $labels
    | ([$a.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {}) as $f
    | (if $e.state == "triage" then [$triage] elif $e.state == "epic" then [$epic, $ready] else [$ready] end) as $want
    | ([$triage, $ready, $epic] - $want) as $unwanted
    | [
        {c: "state", ok: (($want - $labels | length) == 0 and ($unwanted - ($unwanted - $labels) | length) == 0)},
        {c: "type", ok: ($a.issueType.name == $e.type)},
        {c: "area", ok: ([$labels[] | select(startswith("area: "))] == (if $e.area_label != "" then [$e.area_label] else [] end))},
        {c: "assignee", ok: (if ($e.assignee // "") != "" then ([$a.assignees.nodes[].login] | index($e.assignee)) != null
                             else ($a.assignees.nodes | length) == 0 end)},
        {c: "milestone", ok: (($a.milestone.title // "") == ($e.milestone // ""))},
        {c: "parent", ok: ((($a.parent.number // "") | tostring) == ($e.epic // ""))},
        {c: "priority", ok: (($f.Priority // "") == ($e.priority // ""))},
        {c: "effort", ok: (($f.Effort // "") == ($e.effort // ""))},
        {c: "blocked by", ok: (($e.blocked_by | map(tonumber) | sort) == ([$a.blockedBy.nodes[].number] | sort))}
      ]
    | map("\(if .ok then "✓" else "✗" end) \(.c)") | join("  ")
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
    def res: if startswith("@") and ($created[(.[1:] | tonumber) - 1] // 0) > 0
             then ($created[(.[1:] | tonumber) - 1] | tostring) else . end;
    .type = $type | .area_label = $area
    | .epic = ((.epic // "") | res)
    | .blocked_by = ((.blocked_by // []) | map(res))
  ' <<<"$1"
}

state_labels() { # $1 state
  case $1 in
    ready) echo "$READY" ;;
    triage) echo "$TRIAGE" ;;
    epic) printf '%s\n%s\n' "$EPIC" "$READY" ;;
  esac
}

# ---------------------------------------------------------------- create

create_one() { # $1 spec (expected), sets CREATED_N
  local s=$1 title state area assignee milestone epic priority effort b l
  title=$(get title "$s") state=$(get state "$s") area=$(get area_label "$s")
  assignee=$(get assignee "$s") milestone=$(get milestone "$s") epic=$(get epic "$s")
  priority=$(get priority "$s") effort=$(get effort "$s")
  local blockers
  mapfile -t blockers < <(jq -r '.blocked_by[]' <<<"$s")

  PLANNED=("create the issue with labels, assignee, milestone and type") STEP_I=0
  [[ -z $epic ]] || PLANNED+=("make it a sub-issue of $(ref "$epic")")
  for b in "${blockers[@]}"; do PLANNED+=("mark it blocked by $(ref "$b")"); done
  [[ -z $priority && -z $effort ]] || PLANNED+=("$(fields_desc "$priority" "$effort")")

  local args=(issue create --title "$title" --body-file "$TMP/body" --type "$(get type "$s")")
  while IFS= read -r l; do args+=(--label "$l"); done < <(state_labels "$state")
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
  jq -s -e 'all(has("remove_blocked_by") | not)' <<<"$specs" >/dev/null || die "--remove-blocked-by only works with update"

  load_repo_data
  local -a items=()
  local line pos
  while IFS= read -r line; do
    items+=("$(normalize "$(jq -c '.blocked_by //= []' <<<"$line")" "$dir")")
    BATCH_STATES+=("$(get state "${items[-1]}")")
  done <<<"$specs"
  for ((pos = 1; pos <= ${#items[@]}; pos++)); do
    validate "${items[pos - 1]}" "item $pos ($(get title "${items[pos - 1]}"))" "$pos"
  done
  ((${#ERRORS[@]} == 0)) || fail_validation created

  local created='[]' k e
  for ((k = 1; k <= ${#items[@]}; k++)); do
    echo "[$k/${#items[@]}]"
    describe "${items[k - 1]}"
    e=$(expected "${items[k - 1]}" "$created")
    if ! create_one "$e"; then
      ((k == 1)) || echo "Created earlier in this batch: $(jq -r 'map("#\(.)") | join(", ")' <<<"$created"). Items $k-${#items[@]} were not finished." >&2
      exit 1
    fi
    created=$(jq --argjson n "${CREATED_N:-0}" '. + [$n]' <<<"$created")
  done
  ((DRY_RUN)) && echo "Dry run: nothing was created."
  return 0
}

# ---------------------------------------------------------------- update

cmd_update() { # $1 forced state ("" for none), $2 issue number, rest flags
  local forced=$1 n=${2:-}
  [[ $n =~ ^#?[0-9]+$ ]] || die "usage: create-issue.sh update <n> [fields]"
  n=${n#\#}
  shift 2
  parse_flags "$@"
  [[ -z $BATCH ]] || die "update takes no --batch"

  load_repo_data
  local cur labels cur_state target flags
  cur=$(fetch_issue "$n" 2>/dev/null) && [[ $cur != null ]] || die "issue #$n not found"
  [[ $(jq -r .state <<<"$cur") == OPEN ]] || die "#$n is closed"
  labels=$(jq -r '.labels.nodes[].name' <<<"$cur")
  if grep -qxF -- "$EPIC" <<<"$labels"; then cur_state=epic
  elif grep -qxF -- "$READY" <<<"$labels"; then cur_state=ready
  elif grep -qxF -- "$TRIAGE" <<<"$labels"; then cur_state=triage
  else cur_state=""; fi

  flags=$(normalize "$SPEC" ".")
  target=$(get state "$flags")
  if [[ -n $forced ]]; then
    [[ -z $target || $target == "$forced" ]] || die "promote always ends in $forced; drop --state"
    target=$forced
  fi
  if [[ $cur_state == epic ]]; then
    [[ -z $target || $target == epic ]] || die "#$n is an epic; epics stay epics"
    target=epic
  else
    [[ -z $target || $target == ready || $target == triage ]] || die "--state must be ready or triage"
    target=${target:-$cur_state}
    [[ -n $target ]] || die "#$n has neither $TRIAGE nor $READY; pass --state"
  fi

  # Current values, overlaid by the given flags; an empty flag value removes the field.
  local areas
  areas=$(grep '^area: ' <<<"$labels" || true)
  if [[ $(grep -c . <<<"$areas") -gt 1 ]] && ! jq -e 'has("area")' <<<"$flags" >/dev/null; then
    err "#$n has several area labels; pass --area"
  fi
  local s
  s=$(jq -n --argjson c "$cur" --argjson f "$flags" --arg area "$(head -1 <<<"$areas")" --arg state "$target" '
    ([$c.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {}) as $fv
    | {
        title: $c.title, body: $c.body, area: $area,
        priority: ($fv.Priority // ""), effort: ($fv.Effort // ""),
        assignee: ($c.assignees.nodes[0].login // ""),
        milestone: ($c.milestone.title // ""),
        epic: (($c.parent.number // "") | tostring)
      }
    + ($f | del(.state, .blocked_by, .remove_blocked_by))
    | .state = $state
    | .blocked_by = (([$c.blockedBy.nodes[].number | tostring] + ($f.blocked_by // []) | unique) - ($f.remove_blocked_by // []))
  ')
  validate "$s" "#$n" 0
  while read -r b; do
    [[ -z $b ]] || jq -e --arg b "$b" '[.blockedBy.nodes[].number | tostring] | index($b)' <<<"$cur" >/dev/null ||
      err "#$n is not blocked by #$b"
  done < <(jq -r '(.remove_blocked_by // [])[]' <<<"$flags")
  ((${#ERRORS[@]} == 0)) || fail_validation changed
  s=$(expected "$s" '[]')

  echo "#$n →"
  describe "$s"

  # Only the differences become writes.
  local edit=() edit_desc=() line l area cur_assignees assignee milestone
  area=$(get area_label "$s") assignee=$(get assignee "$s") milestone=$(get milestone "$s")
  [[ $(get title "$s") == $(jq -r .title <<<"$cur") ]] || { edit+=(--title "$(get title "$s")"); edit_desc+=("title"); }
  if jq -e 'has("body")' <<<"$flags" >/dev/null; then
    get body "$s" >"$TMP/body"
    edit+=(--body-file "$TMP/body")
    edit_desc+=("body")
  fi
  local want
  want=$(state_labels "$target")
  for l in "$READY" "$TRIAGE" "$EPIC"; do
    if grep -qxF -- "$l" <<<"$want"; then
      grep -qxF -- "$l" <<<"$labels" || { edit+=(--add-label "$l"); edit_desc+=("+$l"); }
    else
      ! grep -qxF -- "$l" <<<"$labels" || { edit+=(--remove-label "$l"); edit_desc+=("-$l"); }
    fi
  done
  [[ -z $area ]] || grep -qxF -- "$area" <<<"$labels" || { edit+=(--add-label "$area"); edit_desc+=("+$area"); }
  while IFS= read -r line; do
    [[ -z $line || $line == "$area" ]] || { edit+=(--remove-label "$line"); edit_desc+=("-$line"); }
  done <<<"$areas"
  cur_assignees=$(jq -r '.assignees.nodes[].login' <<<"$cur")
  if jq -e 'has("assignee")' <<<"$flags" >/dev/null; then
    [[ -z $assignee ]] || grep -qxF -- "$assignee" <<<"$cur_assignees" || { edit+=(--add-assignee "$assignee"); edit_desc+=("+@$assignee"); }
    while IFS= read -r line; do
      [[ -z $line || $line == "$assignee" ]] || { edit+=(--remove-assignee "$line"); edit_desc+=("-@$line"); }
    done <<<"$cur_assignees"
  fi
  if [[ $milestone != "$(jq -r '.milestone.title // ""' <<<"$cur")" ]]; then
    if [[ -n $milestone ]]; then edit+=(--milestone "$milestone"); edit_desc+=("milestone $milestone")
    else edit+=(--remove-milestone); edit_desc+=("no milestone"); fi
  fi

  local type cur_type epic cur_epic priority effort cur_p cur_e p_act="" e_act="" add_blockers=() del_blockers=()
  type=$(get type "$s") cur_type=$(jq -r '.issueType.name // ""' <<<"$cur")
  epic=$(get epic "$s") cur_epic=$(jq -r '(.parent.number // "") | tostring' <<<"$cur")
  priority=$(get priority "$s") effort=$(get effort "$s")
  cur_p=$(jq -r '[.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {} | .Priority // ""' <<<"$cur")
  cur_e=$(jq -r '[.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {} | .Effort // ""' <<<"$cur")
  [[ $priority == "$cur_p" ]] || p_act=${priority:--}
  [[ $effort == "$cur_e" ]] || e_act=${effort:--}
  mapfile -t add_blockers < <(jq -r --argjson c "$cur" '.blocked_by - [$c.blockedBy.nodes[].number | tostring] | .[]' <<<"$s")
  mapfile -t del_blockers < <(jq -r --argjson s "$s" '[.blockedBy.nodes[].number | tostring] - $s.blocked_by | .[]' <<<"$cur")

  PLANNED=() STEP_I=0
  ((${#edit[@]} == 0)) || PLANNED+=("edit $(printf '%s, ' "${edit_desc[@]}" | sed 's/, $//')")
  [[ $type == "$cur_type" ]] || PLANNED+=("set type $type")
  if [[ $epic != "$cur_epic" ]]; then
    if [[ -n $epic ]]; then PLANNED+=("make it a sub-issue of #$epic"); else PLANNED+=("remove its parent #$cur_epic"); fi
  fi
  for b in "${add_blockers[@]}"; do PLANNED+=("mark it blocked by #$b"); done
  for b in "${del_blockers[@]}"; do PLANNED+=("drop blocked by #$b"); done
  [[ -z $p_act && -z $e_act ]] || PLANNED+=("$(fields_desc "$p_act" "$e_act")")

  if ((${#PLANNED[@]} == 0)); then
    echo "  #$n already has every value; nothing to change."
  else
    if ((${#edit[@]})); then step "${PLANNED[STEP_I]}" gh issue edit "$n" "${edit[@]}" || { partial_report "#$n"; exit 1; }; fi
    if [[ $type != "$cur_type" ]]; then step "${PLANNED[STEP_I]}" gh issue edit "$n" --type "$type" || { partial_report "#$n"; exit 1; }; fi
    if [[ $epic != "$cur_epic" ]]; then
      if [[ -n $epic ]]; then step "${PLANNED[STEP_I]}" gh issue edit "$n" --parent "$epic" || { partial_report "#$n"; exit 1; }
      else step "${PLANNED[STEP_I]}" gh issue edit "$n" --remove-parent || { partial_report "#$n"; exit 1; }; fi
    fi
    for b in "${add_blockers[@]}"; do
      step "${PLANNED[STEP_I]}" gh issue edit "$n" --add-blocked-by "$b" || { partial_report "#$n"; exit 1; }
    done
    for b in "${del_blockers[@]}"; do
      step "${PLANNED[STEP_I]}" gh issue edit "$n" --remove-blocked-by "$b" || { partial_report "#$n"; exit 1; }
    done
    if [[ -n $p_act || -n $e_act ]]; then
      step "${PLANNED[STEP_I]}" set_fields "$n" "$p_act" "$e_act" || { partial_report "#$n"; exit 1; }
    fi
  fi
  verify "$n" "$s" || exit 1
  ((DRY_RUN)) && echo "Dry run: nothing was changed."
  return 0
}

# ---------------------------------------------------------------- milestone

find_milestone() { # $1 title; prints the milestone (open or closed) as JSON, or nothing
  gh api "repos/$REPO/milestones?state=all&per_page=100" --paginate | jq -s -c --arg t "$1" 'add | .[] | select(.title == $t)'
}

cmd_milestone() {
  parse_flags "$@"
  local title due desc
  title=$(get title "$SPEC") due=$(get due_on "$SPEC") desc=$(get description "$SPEC")
  [[ $(jq -r 'keys - ["title", "due_on", "description"] | join(", ")' <<<"$SPEC") == "" ]] || die "milestone takes only --title, --due-on and --description"
  [[ -n ${title//[[:space:]]/} ]] || die "milestone needs --title"
  [[ -z $due || $due =~ ^[0-9]{4}-[0-9]{2}-[0-9]{2}$ ]] || die "--due-on must be YYYY-MM-DD"

  load_repo_data
  if [[ -n $(find_milestone "$title") ]]; then
    die "a milestone titled '$title' already exists. Nothing was created."
  fi
  echo "  milestone: $title · due on: ${due:-—} · description: ${desc:-—}"
  local args=(api "repos/$REPO/milestones" -f title="$title" -f state=open)
  [[ -z $due ]] || args+=(-f due_on="${due}T12:00:00Z")
  [[ -z $desc ]] || args+=(-f description="$desc")
  PLANNED=("create the milestone") STEP_I=0
  step "${PLANNED[0]}" gh "${args[@]}" || { partial_report ""; exit 1; }
  if ((DRY_RUN)); then
    echo "Dry run: nothing was created."
    return 0
  fi
  local m ok
  m=$(find_milestone "$title")
  ok=$(jq -r --arg due "$due" --arg desc "$desc" '
    [ "\(if .state == "open" then "✓" else "✗" end) open",
      "\(if ((.due_on // "")[:10]) == $due then "✓" else "✗" end) due on",
      "\(if (.description // "") == $desc then "✓" else "✗" end) description" ] | join("  ")' <<<"$m")
  echo "  verify milestone #$(jq -r .number <<<"$m"): $ok"
  [[ $ok != *✗* ]] || die "the milestone does not match what was asked: see the ✗ checks above."
  jq -r .html_url <<<"$m"
}

# ---------------------------------------------------------------- main

case ${1:-} in
  create) shift; cmd_create "$@" ;;
  update) shift; cmd_update "" "$@" ;;
  promote) shift; cmd_update ready "$@" ;;
  milestone) shift; cmd_milestone "$@" ;;
  -h | --help | "") usage ;;
  *) die "unknown command: $1 (see --help)" ;;
esac
