# Chainguard workshop — the run sheet

Execute top to bottom. Four parts, one story, ~15 minutes:

> **Containers** remove the OS CVEs → **Libraries** remove the app-dependency CVE →
> **Guardener (images)** automates that at fleet scale → **Guardener (actions)** hardens the
> CI that builds it all. One app, nothing hidden, the CVE count falling at every step.

Each step shows the **command**, **👀 what you'll see**, and **💬 the point**. CVE totals
drift with grype's DB — the constants are **criticals → 0** and the **named CVE disappearing**.

---

## Part 0 — Setup (once, off-screen, ~3 min)

```bash
cd ~/rv-personal/workshop-lite
chainctl auth login
chainctl auth configure-docker                 # lets Docker pull cgr.dev images
chainctl config set default.group rajeevcorp   # lets Guardener find your org
grype db update                                 # current CVE data
export CGR_TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)
```
Have a terminal + a browser tab ready. (Token lasts ~1h — re-run the last line if it expires.)

---

## Part 1 — CONTAINER: swap the base image

```bash
docker build -q -f python/Dockerfile.1-baseline -t py:baseline python
grype py:baseline --config grype.yaml
```
👀 **Thousands of CVEs, ~120 CRITICAL.** This is where most teams start.

```bash
docker build -q -f python/Dockerfile.2-image -t py:image python
grype py:image --config grype.yaml
diff python/Dockerfile.1-baseline python/Dockerfile.2-image   # the ONLY change is the FROM line
```
👀 **Collapses to ~a dozen, 0 CRITICAL.** One `werkzeug` CVE remains (that's Part 2).

```bash
docker run --rm -d -p 8080:8080 --name pyc py:image && sleep 2
curl localhost:8080          # -> the SAME app runs, still served by werkzeug 3.0.2
docker rm -f pyc
```
👀 **The app still works** — identical response, just on a hardened base.

💬 **The point:** changing *one line* — the base image — to Chainguard erases the entire
OS-layer CVE problem, and the app runs exactly as before.

---

## Part 2 — LIBRARY: remediate your dependency

```bash
docker build -q -f python/Dockerfile.3-library --secret id=cgr_token,env=CGR_TOKEN -t py:library python
grype py:library --config grype.yaml
```
👀 **0 CRITICAL, and `CVE-2024-34069` (the werkzeug RCE) is GONE.**

```bash
docker run --rm -d -p 8080:8080 --name pyc py:library && sleep 2
curl localhost:8080          # -> served by werkzeug 3.0.2+cgr.1   (the remediated build)
docker rm -f pyc
```
💬 **The point:** `requirements.txt` still says `werkzeug==3.0.2` — pip pulled Chainguard's
remediated `3.0.2+cgr.1` automatically. **Zero code or version changes**, library CVE fixed.

---

## Part 3 — GUARDENER (image migration): automate Part 1 at fleet scale

```bash
cd guardener/dockerfile
chainctl agent dockerfile build -f Dockerfile.legacy -t py:guardener . --parent rajeevcorp
```
👀 Watch the AI loop: **analyze → build → hit a real failure → fix it → build → done.**

```bash
diff Dockerfile.legacy Dockerfile.legacy.migrated       # ubuntu+apt+root -> Chainguard, nonroot
docker build -q -f Dockerfile.legacy.migrated -t py:guardener .
grype py:guardener                                       # same collapse as Part 1 — 0 CRITICAL
cd ../..
```
> **No Guardener beta?** Skip the agent command and show the recorded result instead:
> `cd guardener/dockerfile && diff Dockerfile.legacy captured/Dockerfile.migrated.reference && cat captured/transcript.md && cd ../..`

💬 **The point:** Part 1 was one Dockerfile by hand. Guardener does the *same* migration —
**built and verified by an AI loop** — across the 400 Dockerfiles you actually have.

---

## Part 4 — GUARDENER (hardened actions): harden the CI itself

```bash
cat .github/workflows/ci.yml          # the BEFORE: actions on MUTABLE tags (actions/checkout@v4 ...)
chainctl guardener github migrate create rajeevvishal06/workshop-lite --parent rajeevcorp
```
👀 Prints a **PR URL** — open it: every `uses:` is rewritten to a **SHA-pinned
`chainguard-actions/*`** equivalent (same inputs/outputs, run steps untouched).

```bash
gh workflow run "Verify hardened-action provenance" --ref main && sleep 20 && gh run watch
```
👀 For each pinned action: the **upstream commit** it was hardened from, the **policy SHA**,
and a **signed SLSA attestation** — proof, not "trust us."

Optional — the threat, made real:
```bash
gh workflow run "CI (PINNED + hardened — immune to tag hijack)" --ref main
```
💬 **The point:** a mutable `@v4` tag can be hijacked — exactly how `tj-actions` dumped CI
secrets across ~23,000 repos. **SHA-pinned + hardened actions are immune, and provable.**

> Part 4 needs the repo **public** (your Guardener scope is PUBLIC). If it's private, either
> keep it public for the demo or ask Chainguard to set your scope to `ALL`.

---

## The whole story on one slide

| Step | Command spine | CVEs / outcome |
|------|---------------|----------------|
| 1 — Container | `Dockerfile.2-image` | thousands · ~120 crit → **~dozen · 0 crit** |
| 2 — Library | `Dockerfile.3-library` | werkzeug `CVE-2024-34069` **gone**, no code change |
| 3 — Guardener image | `chainctl agent dockerfile build` | Part 1 **automated + build-verified** at fleet scale |
| 4 — Guardener actions | `chainctl guardener github migrate create` | CI actions **SHA-pinned + hardened**, provable, hijack-immune |

## Reset between runs
```bash
docker rm -f pyc 2>/dev/null
docker image rm -f py:baseline py:image py:library py:guardener 2>/dev/null
rm -f guardener/dockerfile/Dockerfile.legacy.migrated*     # regenerated each live run
```
