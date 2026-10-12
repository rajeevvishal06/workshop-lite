# Verifying the Guardener modules

Exact steps to confirm both use cases work. Each has a **captured mode** (no beta needed —
runs anywhere) and a **live mode** (needs the Guardener beta on your org). Captured mode is
the real test of the committed artifacts; live mode exercises the actual agent / GitHub App.

> **Last verified (captured mode, all ✅):** UC1 image built, ran and served `werkzeug 3.0.2`,
> `User=65532` (nonroot), distroless, `grype` → **0 Critical** (1 High = the werkzeug *library*
> CVE, which the Libraries act clears — not the base-image migration). UC2 all three YAMLs
> parse; 4/4 `uses:` → `chainguard-actions/*` 40-char SHA pins + version comment; config has
> recommend + migrate both enabled (`period 168h`).

Tooling used: Docker, `grype`, and macOS's built-in `ruby` (for YAML). `chainctl` is only
needed for live mode.

---

## Use case 1 — Dockerfile migration (`dockerfile/`)

### A. Captured mode (no access — always works)
```bash
cd guardener/dockerfile

# 1. the before -> after the AI produced
diff Dockerfile.legacy captured/Dockerfile.migrated.reference
cat captured/transcript.md

# 2. PROVE the "after" actually works — build, run, scan (the real test)
docker build -f captured/Dockerfile.migrated.reference -t py:guardener-captured .
docker run --rm -d -p 8082:8080 --name gd py:guardener-captured && sleep 2
curl localhost:8082                                                   # -> served by werkzeug 3.0.2
docker inspect py:guardener-captured --format 'User={{.Config.User}}' # -> 65532 (nonroot)
docker rm -f gd
grype py:guardener-captured                                           # -> 0 Critical

# cleanup
docker image rm -f py:guardener-captured
```
**Pass criteria:** image builds; `curl` returns `werkzeug 3.0.2`; `User=65532`; grype shows
**0 Critical**. The lone **High** is `werkzeug 3.0.2` (`GHSA-2g68-c3qc-8985`) — the *library*
CVE the **Libraries** act removes, not something a base-image migration touches. This is the
same outcome as `../../python/Dockerfile.2-image`, which is exactly the point.

### B. Live mode (needs the Guardener beta)
```bash
cd guardener/dockerfile
chainctl auth login
chainctl agent accept-terms                           # one-time per org (needs guardener.admin)

chainctl agent dockerfile build -f Dockerfile.legacy -t py:guardener .   # AI build+fix, a few min
diff Dockerfile.legacy Dockerfile.legacy.migrated     # inspect what it wrote
docker run --rm -d -p 8082:8080 --name gd py:guardener && sleep 2
curl localhost:8082 ; docker rm -f gd
grype py:guardener
```
**Pass criteria:** `Dockerfile.legacy.migrated` appears as a multi-stage Chainguard /
nonroot / distroless build; the image runs and serves; grype shows 0 Critical. No beta →
the command errors on entitlement/terms; fall back to A.

---

## Use case 2 — Hardened GitHub Actions (`actions/`)

No local "run" (it's a GitHub App), so verification = artifacts valid + mapping correct
(captured), and the App opens a real PR (live).

### A. Captured mode (no access — always works)
```bash
cd guardener/actions

# 1. the before -> after the App would produce
diff workflows-before/ci.yml captured/ci.hardened.yml
cat captured/pr-summary.md

# 2. validate YAML (macOS ruby — no installs)
for f in workflows-before/ci.yml captured/ci.hardened.yml .chainguard/actions.yaml; do
  ruby -ryaml -e "YAML.load_file('$f')" && echo "OK  $f" || echo "BAD $f"
done

# 3. confirm every action is hardened + 40-char SHA-pinned
ruby -ryaml -e '
a = YAML.load_file("captured/ci.hardened.yml")["jobs"]["build"]["steps"].select{|s|s["uses"]}.map{|s|s["uses"]}
a.each{|u| sha=u.split("@")[1].to_s.split(" ")[0].length==40; h=u.start_with?("chainguard-actions/");
  puts "#{h&&sha ? "OK " : "BAD"} #{u}"}'
```
**Pass criteria:** all three files parse; every `uses:` in the after is
`chainguard-actions/<org>-<name>@<40-char-sha> # vN`.
*(Optional schema lint: `brew install actionlint && actionlint captured/ci.hardened.yml`.)*

### B. Live mode (needs the beta + a GitHub repo you control)
```bash
# 1. install the App:  https://github.com/apps/chainguard-guardener  (Contents: RW + Workflows: RW)
# 2. link your GitHub org to your Chainguard group:
chainctl guardener github link <github-org> --group <chainguard-group>
# 3. commit guardener/actions/.chainguard/actions.yaml to the repo root as .chainguard/actions.yaml
# 4. trigger an on-demand migration PR:
chainctl guardener github migrate create <owner/repo>      # waits + prints the PR by default
chainctl guardener github status                           # connection sanity
```
**Pass criteria:** the command prints a PR URL; the PR rewrites `uses:` lines to SHA-pinned
`chainguard-actions/*` and summarizes the changes in its body. For recommendation mode, open a
PR that edits a workflow and confirm Guardener posts a non-blocking suggestion comment.

---

## Fidelity notes
- **UC1 is proven end-to-end** by the build/run/scan above. The numbers in
  `captured/transcript.md` (image sizes, the `python3`→`python` fix) are *representative* of a
  real run — refresh them from a live run if you want them exact.
- **UC2's SHAs are placeholders** — syntactically valid 40-char pins, but not the real current
  SHAs of each hardened action (those need a live App run). After a real `migrate create`, copy
  the actual SHAs from the PR into `captured/ci.hardened.yml` so the diff is 100% faithful.
