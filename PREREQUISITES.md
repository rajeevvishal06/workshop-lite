# Workshop Prerequisites — please complete BEFORE the session

Thanks for joining the **Chainguard Images + Libraries** hands-on workshop. It's a short,
run-it-yourself exercise: you build a tiny app three ways with plain `docker` and scan each with
`grype`, watching CVEs drop and then proving the supply chain. To keep the session moving,
please do the setup below **ahead of time** — ~15–20 min plus some image-download time.

> ⏱️ **Do this at least a day early.** The one item with real lead time is **Chainguard
> access** (step 1) — if you don't already have it, request it *now* so it's granted before we
> start. Everything else is quick.

---

## 1. Chainguard access (the long-pole — sort this first)

You need a **Chainguard NFR organization** with:
- **Images** access (to pull `cgr.dev/chainguard/*` base images), and
- **Libraries** entitlements for **Python and Java**, with the **upstream/proxy policy enabled**
  (so the Chainguard index can also serve proxied upstream packages).

Not sure if you have this? **Reply to the workshop organiser now** so it can be provisioned in
time. You'll confirm it works in step 4.

---

## 2. Install the tools

| Tool | Why | Install |
|------|-----|---------|
| **Docker** (with BuildKit — default) | build & run the app | https://docs.docker.com/get-docker/ |
| **grype** ≥ 0.100.0 | scan images for CVEs | `brew install grype` · https://github.com/anchore/grype |
| **chainctl** | Chainguard login + token | https://edu.chainguard.dev/chainguard/chainctl/ |
| **cosign** | verify image signatures/provenance | `brew install cosign` |
| **syft** | inspect the SBOM | `brew install syft` |
| **jq**, **python3** | parse command output (python3 is usually preinstalled) | `brew install jq` |

macOS (Homebrew), one line for most of it:
```bash
brew install grype cosign syft jq      # + Docker Desktop + chainctl from the links above
```
**Windows:** use **WSL2** with Docker Desktop and run everything inside the Linux shell.
**Linux:** Docker Engine + the tools above from each project's releases.

**Give Docker room:** Docker Desktop → Settings → Resources → allow at least **~6 GB memory**
and **~20 GB disk** (we pull base images for both a Python and a Java track).

---

## 3. Get the workshop code

```bash
git clone <REPO_URL>          # ← the organiser will share the exact URL (or a zip)
cd workshop-lite
```
> Organiser note: fill in `<REPO_URL>` before sending, or attach the folder as a zip.

---

## 4. Log in to Chainguard and confirm entitlements

```bash
chainctl auth login                    # opens a browser
chainctl auth configure-docker         # lets Docker pull cgr.dev images
chainctl libraries entitlements list   # expect Python + Java = enabled
```
If the last command doesn't show **Python and Java** enabled, tell the organiser before the
session.

---

## 5. Confirm your machine can reach everything (2-minute smoke test)

```bash
# a) a Chainguard Libraries token mints (proves your NFR entitlement):
chainctl auth token --audience libraries.cgr.dev >/dev/null 2>&1 && echo "token OK ✓" || echo "login/entitlement needed ✗"

# b) you can pull a Chainguard image:
docker pull cgr.dev/chainguard/python:latest-dev && echo "cgr.dev pull OK ✓"

# c) grype runs:
grype version >/dev/null && echo "grype OK ✓"
```
Three ✓ and you're set. Any ✗ → follow the hint / ping the organiser.

---

## 6. Pre-pull the base images (biggest time-saver)

This caches every base image locally so the live builds are fast. Do it on good wifi the day
before (needs step 4 done first):

```bash
docker pull python:3.14
docker pull eclipse-temurin:17-jre
docker pull maven:3.9-eclipse-temurin-17
docker pull cgr.dev/chainguard/python:latest-dev
docker pull cgr.dev/chainguard/jre:latest
docker pull cgr.dev/chainguard/maven:latest-dev
```

**Optional but recommended — one dry run** so app layers are cached too:
```bash
docker build -f python/Dockerfile.1-baseline -t py:baseline python
grype py:baseline --config grype.yaml        # expect a big CVE count — that's the "before"
```

---

## ✅ You're ready when

- [ ] `chainctl libraries entitlements list` shows **Python + Java**
- [ ] the step-5 smoke test prints **three ✓**
- [ ] the six `docker pull`s in step 6 completed with no errors
- [ ] you have the `workshop-lite` folder (cloned or unzipped)

Bring a terminal and a browser. If anything above fails, ping the organiser **before** the
workshop so we can fix it without eating into the live time.
