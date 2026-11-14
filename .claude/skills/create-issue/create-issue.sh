#!/usr/bin/env bash
# Creates and updates issues and milestones with every field that CONTRIBUTING.md →
# "Crear un issue" asks for. Validates everything before the first write, verifies the
# result after the last one and, if a write fails, says which steps were done and which
# are missing. Requires gh 2.94+ and jq. Run with --help for usage.
set -euo pipefail

READY="✅ ready"
TRIAGE="🔍 triage"
EPIC="🗺️ epic"
WONTFIX="🚫 wontfix"
QUESTION="❓ question"
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
  create-issue.sh check   N [N…] [--json]
  create-issue.sh check   --input SNAPSHOT [--json]

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
  ready   needs title, body with "## Description", "## Acceptance criteria" and,
          unless the prefix is fix, "## Quality attributes", area, priority, effort,
          assignee, milestone and epic. Labels: ✅ ready + the area.
  triage  needs title and a body with "## Description"; every other field is
          optional. Label: 🔍 triage.
  epic    needs title, body and milestone; takes no area, priority, effort or epic.
          Labels: 🗺️ epic + ✅ ready.
Every issue has at most one area label. The issue type comes from the title prefix:
feat → Feature, fix → Bug, rest → Task.

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

check is read-only: it validates open issues against the state they are in (🗺️ epic →
epic, else ✅ ready → ready, else 🔍 triage → triage; with neither, an error, and the
issue is validated as triage). Besides the rules above it checks that the issue type
matches the prefix and that the state labels don't contradict each other (both 🔍 triage
and ✅ ready, neither, 🚫 wontfix on an open issue, ❓ question on a ready one).
With numbers it reads those issues from GitHub. With --input it reads a snapshot from
audit-backlog.sh gather and makes no GitHub calls.
Output: "error: #N: message" per error, then "#N ok" per issue without errors (only the
errors with --input). --json prints [{number, state, errors: [{field, message}]}], with
state ready|triage|epic|none and field one of title, type, template, state, body, area,
priority, effort, assignee, milestone, epic, blocked_by.
Exit 0 when no issue has errors, 1 when one has, 2 on usage or network errors.

--dry-run runs the read-only validation against GitHub and prints the writes it would
make, without making any.
EOF
}

DIE_CODE=1 # check exits 2 on usage or network errors
die() { echo "error: $*" >&2; exit "$DIE_CODE"; }

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

# The lookups below are filled from GitHub (load_repo_data) or, with OFFLINE=1, entirely
# from a snapshot (load_snapshot); validate() reads them the same way either way.
LABELS="" MILESTONES="" CURRENT_MILESTONE=""
OFFLINE=0
declare -A COLLAB=() ISSUE=()

load_repo_data() {
  command -v jq >/dev/null || die "jq is required"
  [[ $(gh issue edit --help 2>/dev/null) == *--add-blocked-by* ]] || die "gh 2.94+ is required (gh issue edit --add-blocked-by)"
  REPO=$(gh repo view --json owner,name --jq '.owner.login + "/" + .name') || die "cannot read the repository"
  OWNER=${REPO%/*} NAME=${REPO#*/}
  LABELS=$(gh label list --limit 200 --json name --jq '.[].name') || die "cannot read the labels"
  MILESTONES=$(gh api "repos/$REPO/milestones?state=open&per_page=100" --jq 'sort_by(.number) | .[].title') || die "cannot read the milestones"
  CURRENT_MILESTONE=$(head -1 <<<"$MILESTONES")
}

# $1 a snapshot from audit-backlog.sh gather. Fills every lookup; no GitHub calls follow.
load_snapshot() {
  local f=$1 n v
  command -v jq >/dev/null || die "jq is required"
  [[ -f $f ]] || die "snapshot not found: $f"
  jq -e 'type == "object" and (.issues | type == "array")' "$f" >/dev/null 2>&1 || die "invalid snapshot: $f"
  OFFLINE=1
  REPO=$(jq -r '.repo // ""' "$f")
  OWNER=${REPO%/*} NAME=${REPO#*/}
  LABELS=$(jq -r '.labels[]' "$f")
  MILESTONES=$(jq -r '.milestones | map(select(.state == "OPEN")) | sort_by(.number) | .[].title' "$f")
  CURRENT_MILESTONE=$(head -1 <<<"$MILESTONES")
  while IFS= read -r n; do [[ -z $n ]] || COLLAB[$n]=1; done < <(jq -r '.collaborators[]' "$f")
  # Open issues, then the parents and blockers they point to (open or closed).
  while IFS=$'\t' read -r n v; do
    [[ -n ${ISSUE[$n]+x} ]] || ISSUE[$n]=$v
  done < <(jq -r '
    def info: {state: (.state | ascii_downcase), pr: false, labels: [(.labels.nodes // [])[].name]};
    (.issues[] | [.number, (info | tojson)]),
    (.issues[] | (.parent // empty), (.blockedBy.nodes // [])[] | [.number, (info | tojson)])
    | @tsv' "$f")
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
    if ((OFFLINE)); then COLLAB[$u]=0
    elif gh api "repos/$REPO/collaborators/$u" --silent 2>/dev/null; then COLLAB[$u]=1; else COLLAB[$u]=0; fi
  fi
  [[ ${COLLAB[$u]} == 1 ]]
}

# Caches {state, pr, labels} of issue $1; fails if it does not exist.
issue_info() {
  local n=$1
  if [[ -z ${ISSUE[$n]+x} ]]; then
    if ((OFFLINE)); then ISSUE[$n]=""
    else ISSUE[$n]=$(gh api "repos/$REPO/issues/$n" --jq '{state, pr: (.pull_request != null), labels: [.labels[].name]}' 2>/dev/null) || ISSUE[$n]=""; fi
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

# jq: an issue as fetched (Q_ISSUE or the snapshot) → the spec validate() reads.
JQ_SPEC='def issue_spec:
  ([.issueFieldValues.nodes[] | select(.field) | {(.field.name): .name}] | add // {}) as $fv
  | [.labels.nodes[].name | select(startswith("area: "))] as $areas
  | {
      title, body: (.body // ""), area: ($areas[0] // ""), area_labels: $areas,
      priority: ($fv.Priority // ""), effort: ($fv.Effort // ""),
      assignee: (.assignees.nodes[0].login // ""),
      milestone: (.milestone.title // ""),
      epic: ((.parent.number // "") | tostring),
      blocked_by: [.blockedBy.nodes[].number | tostring]
    };'

# ---------------------------------------------------------------- validation

# ERRORS holds the text lines; ERR_FIELDS / ERR_MSGS one entry per field, for check --json.
ERRORS=() ERR_FIELDS=() ERR_MSGS=()
# $1 field, $2 message. Inside validate() the text line starts with "$who: ".
err() { ERRORS+=("${who:+$who: }$2"); ERR_FIELDS+=("$1"); ERR_MSGS+=("$2"); }
# One text line "$1 f1, f2", one entry per field "$1 f". $1 message start, rest fields.
err_fields() {
  local m=$1 f
  shift
  ERRORS+=("${who:+$who: }$m $(IFS=,; echo "$*" | sed 's/,/, /g')")
  for f; do ERR_FIELDS+=("$f"); ERR_MSGS+=("$m $f"); done
}
BATCH_STATES=()

has_heading() { grep -qE "^##[[:space:]]+$1[[:space:]]*\$" <<<"$2"; }

# $1 normalised spec, $2 label for messages, $3 batch position (1-based; 0 outside a batch).
# Only check puts issue_type (the current issue type) and labels (every current label)
# in the spec; area_labels holds the current area labels when they are kept.
validate() {
  local s=$1 who=$2 pos=$3
  local title state body area priority effort assignee milestone epic b
  local has_type itype n_areas has_labels labels blockers
  IFS=$'\x1f' read -r title state area priority effort assignee milestone epic has_type itype n_areas has_labels < <(
    jq -r '[.title, .state, .area, .priority, .effort, .assignee, .milestone, .epic,
             has("issue_type"), .issue_type, ((.area_labels // []) | length), has("labels")]
           | map(if . == null then "" else tostring end) | join("\u001f")' <<<"$s")
  body=$(get body "$s")
  labels=$(jq -r '(.labels // [])[]' <<<"$s")
  blockers=$(jq -r '(.blocked_by // [])[]' <<<"$s")

  [[ $state == ready || $state == triage || $state == epic ]] || err state "state must be ready, triage or epic"
  if [[ ! $title =~ ^($PREFIXES):\ [^[:space:]] || $title == *. ]]; then
    err title "title must be '<type>: <description>' without a trailing period, type one of ${PREFIXES//|/, }"
  fi
  if [[ $has_type == true && $itype != "$(type_for "$title")" ]]; then
    err type "issue type is ${itype:-not set}; the prefix '${title%%:*}' needs $(type_for "$title")"
  fi
  [[ -n ${body//[[:space:]]/} ]] || err body "body is empty"

  if [[ $state != epic ]]; then
    has_heading Description "$body" || err template "the body needs a '## Description' section"
  fi
  if [[ $state == ready ]]; then
    has_heading "Acceptance criteria" "$body" || err template "a ready issue needs a '## Acceptance criteria' section in the body"
    [[ ${title%%:*} == fix ]] || has_heading "Quality attributes" "$body" ||
      err template "a ready issue needs a '## Quality attributes' section in the body"
  fi
  ((n_areas <= 1)) || err area "has several area labels; pass --area"

  if [[ $has_labels == true ]]; then
    local tri=0 rdy=0
    grep -qxF -- "$TRIAGE" <<<"$labels" && tri=1
    grep -qxF -- "$READY" <<<"$labels" && rdy=1
    if [[ $state != epic ]]; then
      ((!tri || !rdy)) || err state "has both $TRIAGE and $READY"
      ((tri || rdy)) || err state "has neither $TRIAGE nor $READY"
    fi
    ! grep -qxF -- "$WONTFIX" <<<"$labels" || err state "is open but labelled $WONTFIX; close it"
    [[ $state != ready ]] || ! grep -qxF -- "$QUESTION" <<<"$labels" || err state "is ready but labelled $QUESTION"
  fi

  local required=() forbidden=()
  case $state in
    ready)
      [[ -n $area ]] || required+=(area)
      [[ -n $priority ]] || required+=(priority)
      [[ -n $effort ]] || required+=(effort)
      [[ -n $assignee ]] || required+=(assignee)
      [[ -n $milestone ]] || required+=(milestone)
      [[ -n $epic ]] || required+=(epic)
      ;;
    epic)
      [[ -n $milestone ]] || required+=(milestone)
      [[ -z $area ]] || forbidden+=(area)
      [[ -z $priority ]] || forbidden+=(priority)
      [[ -z $effort ]] || forbidden+=(effort)
      [[ -z $epic ]] || forbidden+=(epic)
      ((${#forbidden[@]} == 0)) || err_fields "an epic takes no" "${forbidden[@]}"
      ;;
  esac
  ((${#required[@]} == 0)) || err_fields "$([[ $state == epic ]] && echo "an epic" || echo "a $state issue") needs" "${required[@]}"

  [[ -z $area ]] || area_label "$area" >/dev/null || err area "no label for area '$area'"
  [[ -z $priority || " $PRIORITIES " == *" $priority "* ]] || err priority "priority must be one of $PRIORITIES"
  [[ -z $effort || " $EFFORTS " == *" $effort "* ]] || err effort "effort must be one of $EFFORTS"
  [[ -z $assignee ]] || is_collaborator "$assignee" || err assignee "$assignee is not a collaborator"
  [[ -z $milestone ]] || grep -qxF -- "$milestone" <<<"$MILESTONES" || err milestone "no open milestone '$milestone'"

  if [[ $epic == @* ]]; then
    local k=${epic#@}
    if ((pos == 0)); then
      err epic "epic $epic only works inside a batch"
    elif ! [[ $k =~ ^[0-9]+$ ]] || ((k < 1 || k >= pos)); then
      err epic "epic $epic must point to an earlier item of the batch"
    elif [[ ${BATCH_STATES[k - 1]} != epic ]]; then
      err epic "epic $epic points to a batch item that is not an epic"
    fi
  elif [[ -n $epic ]]; then
    if ! [[ $epic =~ ^[0-9]+$ ]] || ! issue_info "$epic"; then
      err epic "epic #$epic does not exist"
    elif ! jq -e --arg l "$EPIC" '.state == "open" and (.labels | index($l))' <<<"${ISSUE[$epic]}" >/dev/null; then
      err epic "#$epic is not an open issue labelled $EPIC"
    fi
  fi

  while read -r b; do
    [[ -n $b ]] || continue
    if [[ $b == @* ]]; then
      local k=${b#@}
      if ((pos == 0)); then
        err blocked_by "blocked by $b only works inside a batch"
      elif ! [[ $k =~ ^[0-9]+$ ]] || ((k < 1 || k >= pos)); then
        err blocked_by "blocked by $b must point to an earlier item of the batch"
      fi
    elif ! [[ $b =~ ^[0-9]+$ ]] || ! issue_info "$b" || [[ $(jq -r .pr <<<"${ISSUE[$b]}") == true ]]; then
      err blocked_by "blocked by #$b is not an existing issue"
    fi
  done <<<"$blockers"
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
  # --area replaces every current area label, so they only count when it is not given.
  local areas s
  areas=$(grep '^area: ' <<<"$labels" || true)
  s=$(jq -n --argjson c "$cur" --argjson f "$flags" --arg state "$target" "$JQ_SPEC"'
    ($c | issue_spec) as $b
    | $b + ($f | del(.state, .blocked_by, .remove_blocked_by))
    | .state = $state
    | .blocked_by = (($b.blocked_by + ($f.blocked_by // []) | unique) - ($f.remove_blocked_by // []))
    | if $f | has("area") then del(.area_labels) else . end
  ')
  validate "$s" "#$n" 0
  while read -r b; do
    [[ -z $b ]] || jq -e --arg b "$b" '[.blockedBy.nodes[].number | tostring] | index($b)' <<<"$cur" >/dev/null ||
      err blocked_by "#$n is not blocked by #$b"
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

# ---------------------------------------------------------------- check

# jq: an issue → its spec plus number, check_state (ready|triage|epic|none), issue_type
# and labels. With no state label it is validated as triage.
JQ_CHECK_SPEC="$JQ_SPEC"'
  def check_spec($ready; $triage; $epic):
    [.labels.nodes[].name] as $l
    | (if $l | index($epic) then "epic" elif $l | index($ready) then "ready"
       elif $l | index($triage) then "triage" else "none" end) as $st
    | issue_spec + {number, check_state: $st, state: (if $st == "none" then "triage" else $st end),
                    issue_type: .issueType.name, labels: $l};'

cmd_check() {
  DIE_CODE=2
  local input="" json=0 nums=()
  while (($#)); do
    case $1 in
      --input) (($# >= 2)) || die "--input needs a value"; input=$2; shift 2 ;;
      --json) json=1; shift ;;
      -h | --help) usage; exit 0 ;;
      *) [[ $1 =~ ^#?[0-9]+$ ]] || die "usage: create-issue.sh check N [N…] [--json] | check --input SNAPSHOT [--json]"
         nums+=("${1#\#}"); shift ;;
    esac
  done
  if [[ -n $input && ${#nums[@]} -gt 0 ]] || [[ -z $input && ${#nums[@]} -eq 0 ]]; then
    die "check takes issue numbers or --input, not both or neither"
  fi

  local specs n issue
  if [[ -n $input ]]; then
    load_snapshot "$input"
    specs=$(jq -c --arg r "$READY" --arg t "$TRIAGE" --arg e "$EPIC" "$JQ_CHECK_SPEC"'
      .issues[] | select(.state == "OPEN") | check_spec($r; $t; $e)' "$input")
  else
    load_repo_data
    specs=""
    for n in "${nums[@]}"; do
      issue=$(fetch_issue "$n" 2>/dev/null) && [[ $issue != null ]] || die "issue #$n not found"
      [[ $(jq -r .state <<<"$issue") == OPEN ]] || die "#$n is closed"
      specs+=$(jq -c --arg r "$READY" --arg t "$TRIAGE" --arg e "$EPIC" "$JQ_CHECK_SPEC"'check_spec($r; $t; $e)' <<<"$issue")$'\n'
    done
  fi

  local line st results=() oks=() failed=0 i pairs
  while IFS= read -r line; do
    [[ -n $line ]] || continue
    n=$(jq -r .number <<<"$line") st=$(jq -r .check_state <<<"$line")
    ERRORS=() ERR_FIELDS=() ERR_MSGS=()
    validate "$line" "#$n" 0
    ((${#ERRORS[@]} == 0)) || failed=1
    if ((json)); then
      pairs=()
      for ((i = 0; i < ${#ERR_FIELDS[@]}; i++)); do pairs+=("${ERR_FIELDS[i]}" "${ERR_MSGS[i]}"); done
      results+=("$(jq -cn --argjson n "$n" --arg st "$st" '$ARGS.positional as $p
        | {number: $n, state: $st, errors: [range(0; $p | length; 2) as $i | {field: $p[$i], message: $p[$i + 1]}]}' --args "${pairs[@]}")")
    else
      for ((i = 0; i < ${#ERR_MSGS[@]}; i++)); do echo "error: #$n: ${ERR_MSGS[i]}"; done
      ((${#ERRORS[@]})) || oks+=("#$n ok")
    fi
  done <<<"$specs"

  if ((json)); then
    printf '%s\n' "${results[@]}" | jq -s .
  elif [[ -z $input ]]; then
    ((${#oks[@]} == 0)) || printf '%s\n' "${oks[@]}"
  fi
  return "$failed"
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
  check) shift; cmd_check "$@" ;;
  -h | --help | "") usage ;;
  *) die "unknown command: $1 (see --help)" ;;
esac
