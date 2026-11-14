#!/usr/bin/env bash
# Runs audit-backlog.sh analyze (and create-issue.sh check) offline against every fixture.
# Each folder holds snapshot.json and expected.json, the findings of a run without
# --target as an array of {check, issue, severity, field}. Optional files:
#   target + expected-target.json   a run with --target "$(cat target)"
#   bad-target                      a run with that --target, which must exit 2
# Prints "ok <case>" or a diff, and exits 1 if any case fails.
set -uo pipefail

HERE=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
AUDIT="$HERE/../audit-backlog.sh"
CHECK="$HERE/../../create-issue/create-issue.sh"
PROJECT='map({check, issue, severity, field}) | sort_by(.check, (.issue // 0), .severity, (.field // ""))'
FAILED=0
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

fail() { echo "FAIL $1"; FAILED=1; }

# $1 case name, $2 fixture dir, $3 expected file, rest extra arguments for analyze.
run_case() {
  local name=$1 dir=$2 expected=$3
  shift 3
  if ! "$AUDIT" analyze "$dir/snapshot.json" "$@" >"$TMP/out.json" 2>"$TMP/err"; then
    fail "$name: analyze exited with an error"
    sed 's/^/    /' "$TMP/err"
    return
  fi
  jq -S ".findings | $PROJECT" "$TMP/out.json" >"$TMP/actual"
  jq -S "$PROJECT" "$expected" >"$TMP/expected"
  if diff -u --label expected --label actual "$TMP/expected" "$TMP/actual" >"$TMP/diff"; then
    echo "ok $name"
  else
    fail "$name"
    sed 's/^/    /' "$TMP/diff"
  fi
}

for dir in "$HERE"/*/; do
  dir=${dir%/}
  name=$(basename "$dir")
  run_case "$name" "$dir" "$dir/expected.json"
  [[ ! -f $dir/target ]] || run_case "$name --target" "$dir" "$dir/expected-target.json" --target "$(cat "$dir/target")"
  if [[ -f $dir/bad-target ]]; then
    "$AUDIT" analyze "$dir/snapshot.json" --target "$(cat "$dir/bad-target")" >/dev/null 2>"$TMP/err"
    rc=$?
    if ((rc == 2)) && grep -q "^error: no open milestone" "$TMP/err"; then echo "ok $name bad target"; else fail "$name bad target: exit $rc"; fi
  fi

  # create-issue.sh check exits 1 exactly when check (a) has findings.
  "$CHECK" check --input "$dir/snapshot.json" >/dev/null 2>&1
  rc=$?
  want=$(jq 'if any(.[]; .check == "a") then 1 else 0 end' "$dir/expected.json")
  if ((rc == want)); then echo "ok $name check exit $rc"; else fail "$name check: exit $rc, expected $want"; fi
done

exit "$FAILED"
