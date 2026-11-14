#!/usr/bin/env bash
# Runs find-gaps.sh offline against every fixture.
# An analyze case is a folder with snapshot.json and expected.json: the run without
# --target projected to {target, components: [{name, folders, services, helpers}],
# cells: [{component, criterion, value}]}. Optional files:
#   evidence                        lines "component<TAB>criterion<TAB>text": the cell's
#                                   evidence must contain the text
#   target + expected-target.json   a run with --target "$(cat target)"
#   bad-target                      a run with that --target, which must exit 2
# The gather case is a folder with repo/ (a repo tree), gh/ (canned GraphQL answers for
# the fake gh in bin/) and expected.json, the whole snapshot.
# Prints "ok <case>" or a diff, and exits 1 if any case fails.
set -uo pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
GAPS="$HERE/../find-gaps.sh"
PROJECT='{target: (.target.title // null),
  components: ([.components[] | {name, folders, services, helpers}] | sort_by(.name)),
  cells: ([.cells[] | {component, criterion, value}] | sort_by(.component, .criterion))}'
SORT='{target, components: (.components | sort_by(.name)), cells: (.cells | sort_by(.component, .criterion))}'
FAILED=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL $1"; FAILED=1; }

compare() { # $1 case name, $2 expected file, $3 actual file
  if diff -u --label expected --label actual <(jq -S . "$2") <(jq -S . "$3") >"$TMP/diff"; then
    echo "ok $1"
  else
    fail "$1"
    sed 's/^/    /' "$TMP/diff"
  fi
}

# $1 case name, $2 fixture dir, $3 expected file, rest extra arguments for analyze.
run_analyze() {
  local name=$1 dir=$2 expected=$3
  shift 3
  if ! "$GAPS" analyze "$dir/snapshot.json" "$@" >"$TMP/out.json" 2>"$TMP/err"; then
    fail "$name: analyze exited with an error"
    sed 's/^/    /' "$TMP/err"
    return
  fi
  jq "$PROJECT" "$TMP/out.json" >"$TMP/actual"
  jq "$SORT" "$expected" >"$TMP/expected"
  compare "$name" "$TMP/expected" "$TMP/actual"
}

check_evidence() { # $1 case name, $2 evidence file
  local component criterion text found
  while IFS=$'\t' read -r component criterion text; do
    found=$(jq -r --arg c "$component" --arg k "$criterion" \
      '.cells[] | select(.component == $c and .criterion == $k) | .evidence' "$TMP/out.json")
    if [[ $found == *"$text"* ]]; then echo "ok $1 evidence $component $criterion"
    else fail "$1 evidence $component $criterion: \"$found\" lacks \"$text\""; fi
  done <"$2"
}

run_gather() { # $1 case name, $2 fixture dir
  local log="$TMP/gh.log" calls
  : >"$log"
  if ! PATH="$HERE/bin:$PATH" FAKE_GH_DIR="$2/gh" FAKE_GH_LOG="$log" \
    "$GAPS" gather --repo-root "$2/repo" >"$TMP/actual" 2>"$TMP/err"; then
    fail "$1: gather exited with an error"
    sed 's/^/    /' "$TMP/err"
    return
  fi
  compare "$1" "$2/expected.json" "$TMP/actual"
  # One call for the repo, one for milestones and one paginated call for every issue.
  calls=$(wc -l <"$log")
  if ((calls <= 3)); then echo "ok $1 gh calls $calls"; else fail "$1: $calls gh calls"; fi
}

for dir in "$HERE"/*/; do
  dir=${dir%/}
  name=$(basename "$dir")
  [[ $name != bin ]] || continue
  if [[ -d $dir/repo ]]; then
    run_gather "$name" "$dir"
    continue
  fi
  run_analyze "$name" "$dir" "$dir/expected.json"
  [[ ! -f $dir/evidence ]] || check_evidence "$name" "$dir/evidence"
  [[ ! -f $dir/target ]] || run_analyze "$name --target" "$dir" "$dir/expected-target.json" --target "$(cat "$dir/target")"
  if [[ -f $dir/bad-target ]]; then
    "$GAPS" analyze "$dir/snapshot.json" --target "$(cat "$dir/bad-target")" >/dev/null 2>"$TMP/err"
    rc=$?
    if ((rc == 2)) && grep -q '^error: no open milestone' "$TMP/err"; then echo "ok $name bad target"
    else fail "$name bad target: exit $rc"; fi
  fi
done

exit "$FAILED"
