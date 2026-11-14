#!/usr/bin/env bash
# Finds work the system lacks. `gather` downloads every milestone and every issue (open and
# closed) in a few paginated GraphQL queries and scans the repo for its components;
# `analyze` reads a snapshot offline and prints the components and the mechanical cells
# (local, ci, tests) as JSON. Requires gh 2.94+, jq and python3 with PyYAML.
# Run with --help for usage.
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  find-gaps.sh gather [--repo-root DIR] > SNAPSHOT
  find-gaps.sh analyze SNAPSHOT [--target "MILESTONE TITLE"]

gather   prints one JSON object {repo, milestones, issues, evidence, warnings}:
           milestones  every milestone, open and closed, with its description;
           issues      every issue, open and closed, with body, state, stateReason,
                       labels, milestone, issue type, parent and sub-issues;
           evidence    what the repo under DIR (default: the git top level) holds:
             orchestration  each compose file and its services (build folder and its
                            source file count, image, depends_on with condition,
                            restart);
             manifests      each folder with a manifest (package.json, pom.xml, …),
                            skipping dependency, build and hidden folders, with its
                            test, lint and build commands and its test files;
             workflows      each .github/workflows file: events, the folders it works
                            on and its run steps;
             docs           README.md, AGENTS.md, CONTRIBUTING.md and docs/**;
             starts         in the docs' code blocks, each `cd FOLDER` and the commands
                            after it.
         A truncated list adds a line to "warnings".

analyze  reads a snapshot, makes no network calls and prints
         {target, warnings, components, cells}:
           target      the --target milestone, or the open milestone with the lowest
                       number, or null: {number, title, description, epics};
           components  [{name, folders, services, helpers, manifests, workflows, image}]:
                       services, manifest folders and workflow folders merged. A service
                       with no build that shares an image with a built service, or that
                       another service waits for to complete, is a helper of that one;
           cells       [{component, criterion, value, evidence}] for local, ci and tests,
                       value ok | gap | na | unknown (the criterion's docs are missing).
         Exit 0 when the analysis ran, 2 on errors (an unknown or closed --target too).
EOF
}

die() { echo "error: $*" >&2; exit 2; }

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

need_python() {
  command -v python3 >/dev/null || die "python3 is required"
  python3 -c 'import yaml' 2>/dev/null || die "PyYAML is required (pip install pyyaml, or the python3-yaml package)"
}

# ---------------------------------------------------------------- gather: tracker

Q_META='query($owner: String!, $name: String!) { repository(owner: $owner, name: $name) {
  milestones(states: [OPEN, CLOSED], first: 100, orderBy: {field: NUMBER, direction: ASC}) {
    totalCount nodes { number title state description } }
} }'

Q_ISSUES='query($owner: String!, $name: String!, $endCursor: String) { repository(owner: $owner, name: $name) {
  issues(states: [OPEN, CLOSED], first: 100, after: $endCursor) {
    pageInfo { hasNextPage endCursor }
    nodes {
      number title body state stateReason url
      labels(first: 30) { totalCount nodes { name } }
      milestone { number title state }
      issueType { name }
      parent { number }
      subIssues(first: 100) { totalCount nodes { number state } }
    }
  }
} }'

# ---------------------------------------------------------------- gather: repo

PY_SCAN=$(cat <<'PY'
import json, os, re, sys
import yaml

root = os.path.abspath(sys.argv[1])
SKIP = {"node_modules", "target", "dist", "build", "out", "coverage", "vendor", "venv", "__pycache__"}
MANIFESTS = [  # file name, ecosystem; the first one found names the folder's ecosystem
    ("package.json", "npm"), ("pom.xml", "maven"), ("build.gradle", "gradle"),
    ("build.gradle.kts", "gradle"), ("pyproject.toml", "python"), ("setup.py", "python"),
    ("go.mod", "go"), ("Cargo.toml", "cargo"), ("composer.json", "php"), ("Gemfile", "ruby"),
]
COMPOSE = re.compile(r"^(docker-)?compose(\.[\w-]+)?\.ya?ml$")
CODE = re.compile(r"\.(js|jsx|ts|tsx|mjs|cjs|py|java|kt|go|rs|rb|php|cs|swift|dart|scala)$")
TEST_DIRS = {"test", "tests", "__tests__", "spec", "specs"}
TEST_NAME = re.compile(r"(\.(test|spec)\.\w+$)|(^test_.*\.py$)|(_test\.(py|go)$)|((Test|Tests|IT)\.(java|kt)$)")
warnings = []

def rel(path):
    r = os.path.relpath(path, root).replace(os.sep, "/")
    return "." if r == "" else r

def norm(folder, base="."):
    """A folder relative to the repo root, from a path relative to base."""
    p = os.path.normpath(os.path.join(root, base, folder))
    return rel(p)

def read(path):
    with open(path, encoding="utf-8", errors="replace") as f:
        return f.read()

def load_yaml(path):
    try:
        return yaml.safe_load(read(path)) or {}
    except yaml.YAMLError as e:
        warnings.append(f"{rel(path)}: invalid YAML ({str(e).splitlines()[0]})")
        return {}

# Walk the repo once: manifests, compose files and code files.
manifest_dirs, compose_files, code_files = {}, [], []
for d, dirs, files in os.walk(root):
    dirs[:] = sorted(x for x in dirs if x not in SKIP and not x.startswith("."))
    found = [(f, e) for f, e in MANIFESTS if f in files]
    if found:
        manifest_dirs[rel(d)] = found
    for f in sorted(files):
        if COMPOSE.match(f):
            compose_files.append(os.path.join(d, f))
        if CODE.search(f):
            code_files.append(rel(os.path.join(d, f)))

def inside(path, folder):
    return folder == "." or path == folder or path.startswith(folder + "/")

def owner(path):
    """The innermost manifest folder holding path."""
    best = None
    for m in manifest_dirs:
        if inside(path, m) and (best is None or len(m) > len(best)):
            best = m
    return best

tests = {m: [] for m in manifest_dirs}
for f in code_files:
    m = owner(f)
    if m is None:
        continue
    parts = f[len(m) + 1:].split("/") if m != "." else f.split("/")
    if TEST_NAME.search(parts[-1]) or any(p in TEST_DIRS for p in parts[:-1]):
        tests[m].append(f)

def commands(folder, found):
    eco = found[0][1]
    path = os.path.join(root, folder)
    text = "\n".join(read(os.path.join(path, f)) for f, _ in found)
    cmd = {"test": None, "lint": None, "build": None}
    scripts = {}
    if eco == "npm":
        try:
            scripts = json.loads(read(os.path.join(path, "package.json"))).get("scripts") or {}
        except json.JSONDecodeError:
            warnings.append(f"{folder}/package.json: invalid JSON")
        t = scripts.get("test", "")
        if t and "no test specified" not in t:
            cmd["test"] = "npm test"
        if "lint" in scripts:
            cmd["lint"] = "npm run lint"
        if "build" in scripts:
            cmd["build"] = "npm run build"
    elif eco == "maven":
        w = "./mvnw" if os.path.exists(os.path.join(path, "mvnw")) else "mvn"
        cmd["test"], cmd["build"] = f"{w} test", f"{w} package"
        lint = re.search(r"checkstyle|spotless|pmd|spotbugs|errorprone", text)
        if lint:
            cmd["lint"] = f"{w} verify ({lint.group(0)})"
    elif eco == "gradle":
        w = "./gradlew" if os.path.exists(os.path.join(path, "gradlew")) else "gradle"
        cmd["test"], cmd["build"] = f"{w} test", f"{w} build"
        lint = re.search(r"checkstyle|spotless|ktlint|detekt|pmd", text)
        if lint:
            cmd["lint"] = f"{w} check ({lint.group(0)})"
    elif eco == "python":
        if "pytest" in text:
            cmd["test"] = "pytest"
        lint = re.search(r"ruff|flake8|pylint", text)
        if lint:
            cmd["lint"] = lint.group(0)
    elif eco == "go":
        cmd["test"], cmd["build"] = "go test ./...", "go build ./..."
    elif eco == "cargo":
        cmd["test"], cmd["build"] = "cargo test", "cargo build"
    return eco, scripts, cmd

manifests = []
for folder in sorted(manifest_dirs):
    found = manifest_dirs[folder]
    eco, scripts, cmd = commands(folder, found)
    t = sorted(tests[folder])
    manifests.append({
        "folder": folder, "files": [norm(f, folder) for f, _ in found], "ecosystem": eco,
        "scripts": scripts, "commands": cmd, "test_files": t[:20], "test_file_count": len(t),
    })

# Compose files and their services.
orchestration = []
for path in compose_files:
    data = load_yaml(path)
    base = rel(os.path.dirname(path))
    services = []
    for name, s in sorted((data.get("services") or {}).items()):
        s = s or {}
        b = s.get("build")
        build = None
        if isinstance(b, str):
            build = norm(b, base)
        elif isinstance(b, dict):
            build = norm(b.get("context", "."), base)
        dep = s.get("depends_on") or []
        if isinstance(dep, dict):
            deps = [{"service": k, "condition": (v or {}).get("condition", "service_started")} for k, v in dep.items()]
        else:
            deps = [{"service": k, "condition": "service_started"} for k in dep]
        restart = s.get("restart")
        services.append({"name": name, "build": build, "image": s.get("image"),
                         "code_files": None if build is None else sum(inside(f, build) for f in code_files),
                         "depends_on": deps, "restart": None if restart is None else str(restart)})
    orchestration.append({"file": rel(path), "services": services})

# GitHub Actions workflows.
CD = re.compile(r"(?:^|&&|;|\|\|)\s*cd\s+([^\s;&|]+)", re.M)
PREFIX = re.compile(r"--prefix[ =]([^\s;&|]+)")
def glob_folder(glob):
    """The folder a path filter or dependency path names: its part before any wildcard,
    or the parent folder when the whole pattern is a file path."""
    out, parts = [], glob.split("/")
    for part in parts:
        if re.search(r"[*?\[{]", part):
            return "/".join(out) or "."
        out.append(part)
    return os.path.dirname(glob) or "."

def folders_of(run, wd):
    base = wd or "."
    found = [norm(m, base) for m in CD.findall(run) + PREFIX.findall(run) if "${{" not in m]
    return found or ([norm(wd)] if wd else [])

workflows = []
wf_dir = os.path.join(root, ".github", "workflows")
for f in sorted(os.listdir(wf_dir)) if os.path.isdir(wf_dir) else []:
    if not re.search(r"\.ya?ml$", f):
        continue
    data = load_yaml(os.path.join(wf_dir, f))
    on = data.get("on", data.get(True))  # YAML 1.1 reads a bare `on` as true
    if isinstance(on, str):
        on = {on: None}
    elif isinstance(on, list):
        on = {e: None for e in on}
    on = on or {}
    folders = set()
    for ev in on.values():
        if isinstance(ev, dict):
            for p in ev.get("paths") or []:
                if not str(p).startswith("!"):
                    folders.add(norm(glob_folder(str(p))))
    wf_wd = ((data.get("defaults") or {}).get("run") or {}).get("working-directory")
    steps = []
    for job_id, job in (data.get("jobs") or {}).items():
        job = job or {}
        job_wd = ((job.get("defaults") or {}).get("run") or {}).get("working-directory") or wf_wd
        for i, st in enumerate(job.get("steps") or []):
            st = st or {}
            for dep in str((st.get("with") or {}).get("cache-dependency-path") or "").split():
                if "${{" not in dep:
                    folders.add(norm(glob_folder(dep)))
            if "run" not in st:
                continue
            wd = st.get("working-directory") or job_wd
            wd = None if wd and "${{" in str(wd) else wd
            sf = folders_of(str(st["run"]), wd)
            folders.update(sf)
            steps.append({"job": str(job_id), "name": st.get("name") or f"step {i + 1}",
                          "folders": sorted(set(sf)), "run": str(st["run"]).strip()})
    workflows.append({"file": f".github/workflows/{f}", "name": data.get("name") or f,
                      "events": sorted(str(e) for e in on), "folders": sorted(folders - {"."}) if folders != {"."} else ["."],
                      "steps": steps})

# Docs and the `cd FOLDER` starts in their code blocks.
docs = [d for d in ("README.md", "AGENTS.md", "CONTRIBUTING.md") if os.path.isfile(os.path.join(root, d))]
for d, dirs, files in os.walk(os.path.join(root, "docs")):
    dirs.sort()
    docs += sorted(rel(os.path.join(d, f)) for f in files)
starts = []
for doc in docs:
    if not doc.endswith(".md"):
        continue
    fence, current = False, None
    for line in read(os.path.join(root, doc)).splitlines():
        s = line.strip()
        if s.startswith("```"):
            fence, current = not fence, None
            continue
        if not fence:
            continue
        cmd = s.split(" #")[0].strip() if not s.startswith("#") else ""
        m = re.match(r"cd\s+([^\s;&|]+)\s*(?:&&\s*(.*))?$", cmd)
        if m:
            current = {"doc": doc, "folder": norm(m.group(1), current["folder"] if current else "."), "commands": []}
            starts.append(current)
            cmd = (m.group(2) or "").strip()
        if current and cmd:
            current["commands"].append(cmd)

json.dump({"evidence": {"orchestration": orchestration, "manifests": manifests, "workflows": workflows,
                        "docs": docs, "starts": starts},
           "warnings": warnings}, sys.stdout)
PY
)

cmd_gather() {
  local root=""
  while (($#)); do
    case $1 in
      --repo-root) (($# >= 2)) || die "--repo-root needs a value"; root=$2; shift 2 ;;
      *) die "unknown argument: $1 (see --help)" ;;
    esac
  done
  command -v jq >/dev/null || die "jq is required"
  need_python
  [[ -n $root ]] || root=$(git rev-parse --show-toplevel 2>/dev/null) || root=$PWD
  [[ -d $root ]] || die "repo root not found: $root"
  python3 -c "$PY_SCAN" "$root" >"$TMP/repo.json" || die "cannot scan the repo at $root"

  local repo
  repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner) || die "cannot read the repository"
  gh api graphql -f owner="${repo%/*}" -f name="${repo#*/}" -f query="$Q_META" >"$TMP/meta.json" ||
    die "cannot read the milestones"
  gh api graphql --paginate --slurp -f owner="${repo%/*}" -f name="${repo#*/}" -f query="$Q_ISSUES" >"$TMP/pages.json" ||
    die "cannot read the issues"
  jq -n --arg repo "$repo" --slurpfile meta "$TMP/meta.json" --slurpfile pages "$TMP/pages.json" \
    --slurpfile scan "$TMP/repo.json" '
    $meta[0].data.repository as $r
    | [$pages[0][].data.repository.issues.nodes[]] as $issues
    | def cut(what; c): if c.totalCount > (c.nodes | length) then ["only the first \(c.nodes | length) of \(c.totalCount) \(what) were read"] else [] end;
    {
      repo: $repo,
      milestones: ($r.milestones.nodes | sort_by(.number)),
      issues: ($issues | sort_by(.number)),
      evidence: $scan[0].evidence,
      warnings: (
        cut("milestones"; $r.milestones)
        + [$issues[] | . as $i | (cut("labels"; .labels) + cut("sub-issues"; .subIssues))[] | "#\($i.number): \(.)"]
        + $scan[0].warnings
      )
    }'
}

# ---------------------------------------------------------------- analyze

PY_ANALYZE=$(cat <<'PY'
import json, re, sys

with open(sys.argv[1], encoding="utf-8") as f:
    snap = json.load(f)
target = json.loads(sys.argv[2])
ev = snap.get("evidence") or {}
docs = set(ev.get("docs") or [])
warnings = list(snap.get("warnings") or [])
EPIC = "🗺️ epic"

# Each mechanical criterion and the docs that define it.
DEFINING = {
    "local": ["docs/development.md", "docs/deployment.md"],
    "ci": ["CONTRIBUTING.md", "docs/deployment.md"],
    "tests": ["docs/development.md"],
}
for crit, need in DEFINING.items():
    missing = [d for d in need if d not in docs]
    if missing:
        warnings.append(f"criterion {crit}: defining doc missing: {', '.join(missing)}")

def inside(path, folder):
    return folder == "." or path == folder or path.startswith(folder + "/")

# --- components
services = [dict(s, file=o["file"]) for o in ev.get("orchestration") or [] for s in o["services"]]
by_service = {s["name"]: s for s in services}
manifests = {m["folder"]: m for m in ev.get("manifests") or []}
comps = []  # {name, folders, services, helpers, manifests, workflows, image}

def comp_for_folder(folder):
    for c in comps:
        if folder in c["folders"]:
            return c
    return None

def new_comp(name, folder=None):
    c = {"name": name, "folders": [folder] if folder else [], "services": [], "helpers": [],
         "manifests": [], "workflows": [], "image": None}
    comps.append(c)
    return c

built = [s for s in services if s["build"]]
for s in built:
    c = comp_for_folder(s["build"]) or new_comp(s["name"], s["build"])
    c["services"].append(s["name"])
for folder in sorted(manifests):
    c = comp_for_folder(folder) or new_comp(folder.rsplit("/", 1)[-1] if folder != "." else "root", folder)
    c["manifests"].append(manifests[folder]["files"][0])

def comp_of_service(name):
    for c in comps:
        if name in c["services"] or name in c["helpers"]:
            return c
    return None

# Services with no build: helpers of the one they share an image with or the one that waits
# for them to complete; otherwise components of their own (an image with no code of ours).
pending = [s for s in services if not s["build"]]
while pending:
    progress, rest = False, []
    for s in pending:
        host = None
        same = [b for b in built if s["image"] and b["image"] == s["image"]]
        if same:
            host = comp_of_service(same[0]["name"])
        else:
            waiting = [o["name"] for o in services
                       if any(d["service"] == s["name"] and d["condition"] == "service_completed_successfully"
                              for d in o["depends_on"])]
            if waiting:
                host = comp_of_service(waiting[0])
                if host is None and any(w in [p["name"] for p in pending] for w in waiting):
                    rest.append(s)
                    continue
        if host:
            host["helpers"].append(s["name"])
        else:
            new_comp(s["name"])["services"].append(s["name"])
            comps[-1]["image"] = s["image"]
        progress = True
    if not progress:
        for s in rest:
            new_comp(s["name"])["services"].append(s["name"])
            comps[-1]["image"] = s["image"]
        break
    pending = rest

def owner(folder):
    best = None
    for c in comps:
        for f in c["folders"]:
            if inside(folder, f) and (best is None or len(f) > len(best[1])):
                best = (c, f)
    return best[0] if best else None

# Workflows attach to the component holding the folders they work on; a step folder that no
# component holds becomes a component of its own.
workflows = ev.get("workflows") or []
for wf in workflows:
    for st in wf["steps"]:
        for f in st["folders"]:
            if f != "." and owner(f) is None:
                new_comp(f.rsplit("/", 1)[-1], f)
attached = {}  # workflow file -> components
for wf in workflows:
    cs = []
    for f in wf["folders"]:
        c = owner(f) if f != "." else None
        if c and c not in cs:
            cs.append(c)
    attached[wf["file"]] = cs
    for c in cs:
        c["workflows"].append(wf["file"])

names = {}
for c in comps:  # unique names
    n = names.get(c["name"], 0)
    names[c["name"]] = n + 1
    if n:
        c["name"] = f"{c['name']}-{n + 1}"

# --- cells
def cell(c, crit, value, evidence):
    if value != "na" and all(d not in docs for d in DEFINING[crit]):
        value, evidence = "unknown", f"defining docs missing ({', '.join(DEFINING[crit])}); {evidence}"
    return {"component": c["name"], "criterion": crit, "value": value, "evidence": evidence}

def has_code(c):
    """A manifest, a workflow folder, or a build folder holding source files."""
    if c["manifests"] or (c["folders"] and not c["services"]):
        return True
    return any((by_service[s].get("code_files") or 0) > 0 for s in c["services"] if by_service[s]["build"])

def no_code(c):
    if c["image"]:
        return f"no code of our own: runs image `{c['image']}`, with no build and no manifest"
    return f"no code of our own: {' or '.join(c['folders'])} has no manifest and no source files"

STARTS = re.compile(r"(?<![\w-])(start|serve|dev|up)(?![\w-])|:run\b")
def local(c):
    if c["services"]:
        s = by_service[c["services"][0]]
        return cell(c, "local", "ok", f"service `{s['name']}` in `{s['file']}`")
    for st in ev.get("starts") or []:
        if st["doc"] in DEFINING["local"] and st["folder"] in c["folders"]:
            run = [x for x in st["commands"] if STARTS.search(x)]
            if run:
                return cell(c, "local", "ok",
                            f"no service in the orchestration file; `{st['doc']}` starts it with `cd {st['folder']}` and `{run[0]}`")
    return cell(c, "local", "gap", "no service in the orchestration file and no guide "
                f"({', '.join(DEFINING['local'])}) that starts {' or '.join(c['folders']) or 'it'}")

TOOLS = {
    "test": re.compile(r"\b(jest|vitest|mocha|ava|playwright|cypress|pytest|tox|nox|node --test)\b|\b(go|cargo|dotnet) test\b"),
    "lint": re.compile(r"\b(eslint|expo lint|biome|stylelint|ruff|flake8|pylint|mypy|golangci-lint|clippy|checkstyle|spotless|pmd|ktlint|detekt)\b|prettier --check"),
    "build": re.compile(r"\b(vite build|tsc|webpack|next build|rollup|esbuild|expo export|docker build)\b|\b(go|cargo|dotnet) build\b|python -m build"),
}
NPM = re.compile(r"^(?:npm|yarn|pnpm)(?:\s+--prefix[ =]\S+)?\s+(?:run\s+|run-script\s+)?([\w:.-]+)")
MAVEN = re.compile(r"^(?:\./)?(?:mvnw|mvn)\b(.*)")
GRADLE = re.compile(r"^(?:\./)?(?:gradlew|gradle)\b(.*)")

def kinds(segment, manifest):
    """What a shell command does to a component: a subset of {build, lint, test}."""
    out = set()
    m = NPM.match(segment)
    if m:
        name = m.group(1)
        if name in ("ci", "install", "i"):
            return out
        name = "test" if name == "t" else name
        body = (manifest or {}).get("scripts", {}).get(name, "")
        for k in ("test", "lint", "build"):
            if name == k or name.startswith(k + ":") or TOOLS[k].search(body):
                out.add(k)
        return out
    m = MAVEN.match(segment)
    if m:
        goals = m.group(1).split()
        if set(goals) & {"compile", "test", "package", "verify", "install", "deploy"}:
            out.add("build")
        skip = any(g.startswith(("-DskipTests", "-Dmaven.test.skip")) for g in goals)
        if set(goals) & {"test", "package", "verify", "install", "deploy"} and not skip:
            out.add("test")
        if set(goals) & {"verify", "install", "deploy"} and ((manifest or {}).get("commands") or {}).get("lint"):
            out.add("lint")
    m = GRADLE.match(segment)
    if m:
        goals = m.group(1).split()
        if set(goals) & {"build", "assemble", "check", "test"}:
            out.add("build")
        if set(goals) & {"build", "check", "test"}:
            out.add("test")
        if set(goals) & {"build", "check"} and ((manifest or {}).get("commands") or {}).get("lint"):
            out.add("lint")
    for k, rx in TOOLS.items():
        if rx.search(segment):
            out.add(k)
    return out

def merged_manifest(c):
    """The scripts and commands of every manifest of a component, the first one winning."""
    ms = [m for m in manifests.values() if m["files"][0] in c["manifests"]]
    if not ms:
        return None
    scripts, commands = {}, {}
    for m in reversed(ms):
        scripts.update(m.get("scripts") or {})
        commands.update({k: v for k, v in (m.get("commands") or {}).items() if v})
    return {"scripts": scripts, "commands": commands}

def test_command(m):
    """The manifest's test command, or an npm script that runs a test tool."""
    if m["commands"].get("test"):
        return m["commands"]["test"]
    for name, body in (m.get("scripts") or {}).items():
        if TOOLS["test"].search(body):
            return f"npm run {name}"
    return None

def ci(c):
    if not has_code(c):
        return cell(c, "ci", "na", no_code(c))
    where = " or ".join(c["folders"]) or c["name"]
    wfs = [w for w in workflows if w["file"] in c["workflows"]]
    if not wfs:
        return cell(c, "ci", "gap", f"no workflow works on {where}")
    manifest = merged_manifest(c)
    done, notes = {}, []
    for w in wfs:
        if "pull_request" not in w["events"] and "pull_request_target" not in w["events"]:
            notes.append(f"`{w['file']}` does not run on pull requests")
            continue
        alone = attached[w["file"]] == [c]
        for st in w["steps"]:
            if st["folders"] and not any(owner(f) is c for f in st["folders"]):
                continue
            if not st["folders"] and not alone:
                continue
            for seg in re.split(r"\n|&&|;|\|\|", st["run"]):
                seg = re.sub(r"^\s*cd\s+\S+\s*", "", seg).strip()
                for k in kinds(seg, manifest):
                    done.setdefault(k, (w["file"], seg))
    need = ["test", "lint"] + (["build"] if (manifest or {}).get("commands", {}).get("build") else [])
    verb = {"build": "builds", "lint": "lints", "test": "tests"}
    parts, by_step = [], {}
    for k in ("build", "lint", "test"):
        if k in done:
            by_step.setdefault(done[k], []).append(verb[k])
    for (file, seg), vs in by_step.items():
        parts.append(f"{' and '.join(vs)} with `{seg}` (`{file}`)")
    missing = [k for k in need if k not in done]
    parts += [f"does not {k}" for k in missing]
    value = "gap" if missing or (notes and not done) else "ok"
    return cell(c, "ci", value, "; ".join(notes + parts))

def tests(c):
    if not has_code(c):
        return cell(c, "tests", "na", no_code(c))
    ms = [m for m in manifests.values() if m["files"][0] in c["manifests"]]
    if not ms:
        return cell(c, "tests", "gap", f"no manifest in {' or '.join(c['folders'])}, so no test command")
    count = sum(m["test_file_count"] for m in ms)
    cmds = [test_command(m) for m in ms if test_command(m)]
    files = [f for m in ms for f in m["test_files"]][:3]
    have = f"{count} test files (e.g. {', '.join(f'`{f}`' for f in files)})" if count else "no test files"
    run = f"test command `{cmds[0]}`" if cmds else f"no test command in `{ms[0]['files'][0]}`"
    return cell(c, "tests", "ok" if count and cmds else "gap", f"{have}; {run}")

cells = [f(c) for c in comps for f in (local, ci, tests)]

if target is not None:
    target["epics"] = sorted(i["number"] for i in snap.get("issues") or []
                             if i["state"] == "OPEN" and (i.get("milestone") or {}).get("number") == target["number"]
                             and EPIC in [l["name"] for l in (i.get("labels") or {}).get("nodes") or []])
json.dump({"target": target, "warnings": warnings, "components": comps, "cells": cells},
          sys.stdout, ensure_ascii=False, indent=2)
print()
PY
)

cmd_analyze() {
  local snap=${1:-} target_title="" has_target=false
  [[ -n $snap && $snap != -* ]] || die "usage: find-gaps.sh analyze SNAPSHOT [--target \"MILESTONE TITLE\"]"
  shift
  while (($#)); do
    case $1 in
      --target) (($# >= 2)) || die "--target needs a value"; target_title=$2; has_target=true; shift 2 ;;
      *) die "unknown argument: $1 (see --help)" ;;
    esac
  done
  command -v jq >/dev/null || die "jq is required"
  command -v python3 >/dev/null || die "python3 is required"
  [[ -f $snap ]] || die "snapshot not found: $snap"
  jq -e 'type == "object" and (.issues | type == "array") and (.milestones | type == "array")
         and (.evidence | type == "object")' "$snap" >/dev/null 2>&1 || die "invalid snapshot: $snap"

  local target
  target=$(jq -c --arg t "$target_title" --argjson has "$has_target" '
    [.milestones[] | select(.state == "OPEN")] | sort_by(.number) as $o
    | if $has then (($o | map(select(.title == $t)) | .[0]) // {missing: ($o | map(.title) | join(", "))})
      else ($o[0] // null) end
    | if type == "object" and has("number") then {number, title, description: (.description // "")} else . end' "$snap")
  if [[ $target == '{"missing":'* ]]; then
    die "no open milestone \"$target_title\"; open: $(jq -r '.missing | if . == "" then "none" else . end' <<<"$target")"
  fi
  python3 -c "$PY_ANALYZE" "$snap" "$target" || die "analysis failed"
}

# ---------------------------------------------------------------- main

case ${1:-} in
  gather) shift; cmd_gather "$@" ;;
  analyze) shift; cmd_analyze "$@" ;;
  -h | --help | "") usage ;;
  *) die "unknown command: $1 (see --help)" ;;
esac
