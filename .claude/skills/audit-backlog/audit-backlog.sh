#!/usr/bin/env bash
# Audits the form of the open backlog. `gather` downloads a snapshot of every open issue,
# milestone, label and collaborator in a few paginated GraphQL queries; `analyze` reads a
# snapshot offline and prints the findings of checks (a), (c), (d) and (e) as JSON.
# Requires gh 2.94+ and jq. Run with --help for usage.
set -euo pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
CHECK="$HERE/../create-issue/create-issue.sh"
EPIC="🗺️ epic"
TRIAGE="🔍 triage"
WONTFIX="🚫 wontfix"

usage() {
  cat <<'EOF'
Usage:
  audit-backlog.sh gather > SNAPSHOT
  audit-backlog.sh analyze SNAPSHOT [--target "MILESTONE TITLE"]

gather   prints one JSON object with the repo, every milestone (open and closed), the
         labels, the assignable users and every open issue, with its labels, assignees,
         milestone, issue type, parent, sub-issues, blocked-by and issue fields. A
         truncated nested list adds a line to "warnings".

analyze  reads a snapshot, makes no GitHub calls and prints {target, warnings, findings}.
         The target delivery is the --target milestone, or the open milestone with the
         lowest number. Check (a) comes from `create-issue.sh check --input`; checks (c),
         (d) and (e) are computed here. Exit 0 when the analysis ran, 2 on errors.
EOF
}

die() { echo "error: $*" >&2; exit 2; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

# ---------------------------------------------------------------- gather

Q_META='query($owner: String!, $name: String!) { repository(owner: $owner, name: $name) {
  milestones(states: [OPEN, CLOSED], first: 100, orderBy: {field: NUMBER, direction: ASC}) { totalCount nodes { number title state } }
  labels(first: 100) { totalCount nodes { name } }
  assignableUsers(first: 100) { totalCount nodes { login } }
} }'

Q_ISSUES='query($owner: String!, $name: String!, $endCursor: String) { repository(owner: $owner, name: $name) {
  issues(states: OPEN, first: 100, after: $endCursor) {
    pageInfo { hasNextPage endCursor }
    nodes {
      number title body state url
      labels(first: 30) { nodes { name } }
      assignees(first: 10) { nodes { login } }
      milestone { number title state }
      issueType { name }
      parent { number state milestone { number } }
      subIssues(first: 100) { totalCount nodes { number state } }
      blockedBy(first: 50) { totalCount nodes { number state stateReason labels(first: 30) { nodes { name } } milestone { number } } }
      issueFieldValues(first: 10) { nodes { ... on IssueFieldSingleSelectValue { name field { ... on IssueFieldCommon { name } } } } }
    }
  }
} }'

cmd_gather() {
  (($# == 0)) || die "gather takes no arguments"
  command -v jq >/dev/null || die "jq is required"
  local repo
  repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner) || die "cannot read the repository"
  gh api graphql -f owner="${repo%/*}" -f name="${repo#*/}" -f query="$Q_META" >"$TMP/meta.json" ||
    die "cannot read milestones, labels and collaborators"
  gh api graphql --paginate --slurp -f owner="${repo%/*}" -f name="${repo#*/}" -f query="$Q_ISSUES" >"$TMP/pages.json" ||
    die "cannot read the open issues"
  jq -n --arg repo "$repo" --slurpfile meta "$TMP/meta.json" --slurpfile pages "$TMP/pages.json" '
    $meta[0].data.repository as $r
    | [$pages[0][].data.repository.issues.nodes[]] as $issues
    | def cut(what; c): if c.totalCount > (c.nodes | length) then ["only the first \(c.nodes | length) of \(c.totalCount) \(what) were read"] else [] end;
    {
      repo: $repo,
      milestones: ($r.milestones.nodes | sort_by(.number)),
      labels: [$r.labels.nodes[].name],
      collaborators: [$r.assignableUsers.nodes[].login],
      issues: ($issues | sort_by(.number)),
      warnings: (
        cut("milestones"; $r.milestones) + cut("labels"; $r.labels) + cut("collaborators"; $r.assignableUsers)
        + [$issues[] | . as $i
            | (cut("sub-issues"; .subIssues) + cut("blocked-by issues"; .blockedBy))[]
            | "#\($i.number): \(.)"]
      )
    }'
}

# ---------------------------------------------------------------- analyze

# jq program. Inputs: $s the snapshot, $c the output of `check --input --json`,
# $target {number, title} or null, $epic / $triage / $wontfix the label names.
JQ_ANALYZE='
def names: [(.labels.nodes // [])[].name];
def is_epic: names | index($epic) != null;
def sev(m): if $target != null and m == $target.number then "red" else "yellow" end;
def finding(chk; i; field; msg; related; fix):
  {check: chk, severity: sev(i.milestone.number), issue: i.number, url: i.url, title: i.title,
   milestone: i.milestone.title, field: field, message: msg, related: related, fix: fix};
def none: {kind: "none", flag: null, value: null};
def fix(kind; flag; value): {kind: kind, flag: flag, value: value};
# Reachability closure of a graph {"n": [m, …]}: each node maps to every node it reaches.
def closure:
  def go: (. as $m | with_entries(.value = ((.value + [.value[] as $j | ($m[$j | tostring] // [])[]]) | unique))) as $n
          | if $n == . then . else $n | go end;
  go;

[$s.issues[] | select(.state == "OPEN")] as $issues
| ($issues | map({key: (.number | tostring), value: .}) | from_entries) as $by
| ($c | map({key: (.number | tostring), value: .errors}) | from_entries) as $errs
| [$s.milestones[] | select(.state == "OPEN")] as $open
| ($open | map({key: (.number | tostring),
                value: [. as $m | $issues[] | select(is_epic and .milestone.number == $m.number) | .number]})
  | from_entries) as $E

# (a) fields per state, from create-issue.sh check
| [ $c[] | . as $r | $by[$r.number | tostring] as $i | $r.errors[]
    | . as $e
    | ([$i.labels.nodes[].name | select(startswith("area: "))]) as $areas
    | (if $e.field == "type" then fix("derivable"; null; null)
       elif $e.field == "assignee" and ($e.message | test("needs")) and ($areas | length) == 1
         then fix("derivable"; "--assignee"; null)
       elif $e.field == "epic" and ($e.message | test("needs")) and $i.milestone != null
            and (($E[$i.milestone.number | tostring] // []) | length) == 1
         then fix("derivable"; "--epic"; ($E[$i.milestone.number | tostring][0] | tostring))
       elif ($e.field | IN("priority", "effort", "area")) and ($e.message | test("needs"))
         then fix("judgement"; "--\($e.field)"; null)
       elif $e.field == "milestone" then fix("judgement"; "--milestone"; null)
       elif $e.field == "state" and ($e.message | test("neither|both")) then fix("judgement"; "--state"; null)
       elif $e.field == "title" then fix("judgement"; "--title"; null)
       else none end) as $fix
    | finding("a"; $i; $e.field; $e.message; (if $fix.flag == "--epic" then [$fix.value | tonumber] else [] end); $fix)
  ] as $a

# (c) broken dependencies
| [ $issues[] | . as $i | $i.blockedBy.nodes[] | . as $b
    | if ($b.state == "CLOSED" and $b.stateReason == "NOT_PLANNED") or (names | index($wontfix))
        then finding("c"; $i; "blocked_by"; "blocked by discarded #\($b.number)"; [$b.number]; none)
      elif $b.state == "OPEN" and $i.milestone != null and $b.milestone == null
        then finding("c"; $i; "blocked_by"; "blocked by #\($b.number), which has no milestone"; [$b.number]; none)
      elif $b.state == "OPEN" and $i.milestone != null and $b.milestone.number > $i.milestone.number
        then finding("c"; $i; "blocked_by"; "blocked by #\($b.number), which is in a later milestone"; [$b.number]; none)
      else empty end
  ] as $c_deps
| ($issues | map({key: (.number | tostring),
                  value: [.blockedBy.nodes[] | select(.state == "OPEN" and $by[.number | tostring] != null) | .number]})
  | from_entries | closure) as $reach
| [ $issues[] | .number as $n
    | [$n] + [$reach[$n | tostring][] | select(. != $n and ($reach[tostring] | index($n)) != null)]
    | select(length > 1 or ($reach[$n | tostring] | index($n)) != null)
    | sort | select(.[0] == $n)
    | . as $members
    | {check: "c",
       severity: (if any($members[]; $by[tostring].milestone.number == $target.number and $target != null) then "red" else "yellow" end),
       issue: $n, url: $by[$n | tostring].url, title: $by[$n | tostring].title,
       milestone: $by[$n | tostring].milestone.title, field: "blocked_by",
       message: "dependency cycle: \($members | map("#\(.)") | join(", "))", related: $members, fix: none}
  ] as $c_cycles

# (d) orphans and epics
| [ $open[] | . as $m | $E[$m.number | tostring] as $es
    | if ($es | length) == 0 then "milestone has no open epic"
      elif ($es | length) > 1 then "milestone has several open epics"
      else empty end
    | {check: "d", severity: sev($m.number), issue: null, url: null, title: null, milestone: $m.title,
       field: null, message: ., related: $es, fix: none}
  ] as $d_ms
| [ $issues[] | select(is_epic | not) | . as $i | select($i.milestone != null)
    | ($E[$i.milestone.number | tostring] // []) as $es
    | select(($es | length) == 1 and $i.parent.number != $es[0])
    | select(any(($errs[$i.number | tostring] // [])[]; .field == "epic") | not)
    | finding("d"; $i; "epic"; "not a sub-issue of #\($es[0]), the epic of its milestone"; [$es[0]];
              if $i.parent == null then fix("derivable"; "--epic"; ($es[0] | tostring)) else none end)
  ] as $d_out
| [ $issues[] | . as $i | select($i.parent != null)
    | select($i.milestone.state != "CLOSED") # a closed milestone is already an (a) finding
    | select($i.milestone == null or $i.parent.milestone.number != $i.milestone.number)
    | finding("d"; $i; "epic"; "its epic #\($i.parent.number) is in a different milestone"; [$i.parent.number]; none)
  ] as $d_parent
| [ $issues[] | select(is_epic) | . as $i
    | select($i.subIssues.totalCount >= 1 and $i.subIssues.totalCount == ($i.subIssues.nodes | length)
             and all($i.subIssues.nodes[]; .state == "CLOSED"))
    | finding("d"; $i; "state"; "every sub-issue is closed; the epic can be closed"; [$i.subIssues.nodes[].number]; none)
    | .severity = "yellow"
  ] as $d_done

# (e) open questions in the target
| [ $issues[] | . as $i | select($target != null and $i.milestone.number == $target.number and (names | index($triage)))
    | finding("e"; $i; "state"; "triage issue inside the target delivery; promote it before work starts"; []; none)
  ] as $e

| {
    target: $target,
    warnings: ($s.warnings // []),
    findings: ($a + $c_deps + $c_cycles + $d_ms + $d_out + $d_parent + $d_done + $e
               | sort_by((if .severity == "red" then 0 else 1 end), .check, (.issue // 0)))
  }
'

cmd_analyze() {
  local snap=${1:-} target_title="" has_target=false
  [[ -n $snap && $snap != -* ]] || die "usage: audit-backlog.sh analyze SNAPSHOT [--target \"MILESTONE TITLE\"]"
  shift
  while (($#)); do
    case $1 in
      --target) (($# >= 2)) || die "--target needs a value"; target_title=$2; has_target=true; shift 2 ;;
      *) die "unknown argument: $1 (see --help)" ;;
    esac
  done
  command -v jq >/dev/null || die "jq is required"
  [[ -f $snap ]] || die "snapshot not found: $snap"
  jq -e 'type == "object" and (.issues | type == "array") and (.milestones | type == "array")' "$snap" >/dev/null 2>&1 ||
    die "invalid snapshot: $snap"

  local target
  target=$(jq -c --arg t "$target_title" --argjson has "$has_target" '
    [.milestones[] | select(.state == "OPEN")] | sort_by(.number) as $o
    | if $has then (($o | map(select(.title == $t)) | .[0]) // {missing: ($o | map(.title) | join(", "))})
      else ($o[0] // null) end
    | if type == "object" and has("number") then {number, title} else . end' "$snap")
  if [[ $target == '{"missing":'* ]]; then
    die "no open milestone '$target_title'; open: $(jq -r .missing <<<"$target")"
  fi

  local rc=0
  "$CHECK" check --input "$snap" --json >"$TMP/check.json" 2>"$TMP/check.err" || rc=$?
  ((rc <= 1)) || die "create-issue.sh check failed: $(cat "$TMP/check.err")"

  jq -n --slurpfile s "$snap" --slurpfile c "$TMP/check.json" --argjson target "$target" \
    --arg epic "$EPIC" --arg triage "$TRIAGE" --arg wontfix "$WONTFIX" \
    '$s[0] as $s | $c[0] as $c | '"$JQ_ANALYZE"
}

# ---------------------------------------------------------------- main

case ${1:-} in
  gather) shift; cmd_gather "$@" ;;
  analyze) shift; cmd_analyze "$@" ;;
  -h | --help | "") usage ;;
  *) die "unknown command: $1 (see --help)" ;;
esac
