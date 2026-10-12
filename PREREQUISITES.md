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

> **All workshop commands are written for a Linux/macOS shell (bash/zsh)** — they use
> `curl`, `export`, `$(...)`, `grep`, etc. **On Windows, do NOT use PowerShell/CMD** — install
> **WSL2 (Ubuntu)** and run everything inside the Ubuntu terminal, where every command works
> as-is.

| Tool | Why |
|------|-----|
| **Docker** (BuildKit is on by default) | build & run the app |
| **grype** ≥ 0.100.0 | scan images for CVEs |
| **chainctl** | Chainguard login + pull token |
| **cosign** | verify image signatures / provenance |
| **syft** | inspect the SBOM |
| **jq** + **python3** | parse command output (python3 ships with macOS/Ubuntu) |

Pick your OS:

**macOS** (Homebrew):
```bash
brew install grype cosign syft jq
# Docker Desktop:  https://docs.docker.com/desktop/install/mac-install/
# chainctl:        https://edu.chainguard.dev/chainguard/chainctl/how-to-install-chainctl/
```

**Windows** — use WSL2 (this is the smooth path; the commands are all Linux):
```powershell
# 1) In PowerShell (as admin), install WSL2 + Ubuntu, then reboot:
wsl --install -d Ubuntu
# 2) Install Docker Desktop for Windows and turn ON:
#    Settings -> Resources -> WSL Integration -> enable your Ubuntu distro
#    (docs: https://docs.docker.com/desktop/wsl/)
# 3) Open the "Ubuntu" terminal and do EVERYTHING there — including the Linux installs below.
```

**Linux, or inside WSL2 Ubuntu:**
```bash
sudo apt-get update && sudo apt-get install -y jq curl unzip
# grype + syft (Anchore installers):
curl -sSfL https://raw.githubusercontent.com/anchore/grype/main/install.sh | sudo sh -s -- -b /usr/local/bin
curl -sSfL https://raw.githubusercontent.com/anchore/syft/main/install.sh  | sudo sh -s -- -b /usr/local/bin
# cosign:
curl -sSfLo /tmp/cosign https://github.com/sigstore/cosign/releases/latest/download/cosign-linux-amd64 && sudo install /tmp/cosign /usr/local/bin/cosign
# chainctl:
curl -sSfLo /tmp/chainctl "https://dl.enforce.dev/chainctl/latest/chainctl_linux_amd64" && sudo install /tmp/chainctl /usr/local/bin/chainctl
# docker: use Docker Desktop's WSL integration (Windows), or install Docker Engine (native Linux).
# (For arm64 machines, swap the amd64 URLs for arm64 — see each project's releases page.)
```

**Give Docker room:** Docker Desktop → Settings → Resources → allow at least **~6 GB memory**
and **~20 GB disk** (we pull base images for both a Python and a Java track).

---

## 3. Get the workshop code

**Simplest — from the zip your organiser sent** (no GitHub account needed):
```bash
unzip workshop-lite.zip && cd workshop-lite
```

**Or, if the organiser added you as a collaborator on the GitHub repo:**
```bash
git clone https://github.com/rajeevvishal06/workshop-lite.git && cd workshop-lite
```
> Cloning a *private* repo requires you to be signed in to GitHub (a browser prompt, the `gh`
> CLI, or an SSH key). If that's unfamiliar, just use the zip — it's identical content.

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

## 6. Optional — Guardener (AI migration, two use cases)

The `guardener/` module is an **optional** side demo with two use cases, and both work
**without any extra setup** — each ships a committed, captured result you can show. Running
either **live** needs the Guardener **beta** on your org (entitlement-gated, per-org via the
beta waitlist — ask your account team). If you don't have the beta, skip it — nothing else
depends on it.

- **Dockerfile migration** (`guardener/dockerfile/`): a one-time `chainctl agent accept-terms`
  (needs the `guardener.admin` role) plus the `guardener.user` role to run.
- **Hardened GitHub Actions** (`guardener/actions/`): install the GitHub App
  (`github.com/apps/chainguard-guardener`) and link your org with `chainctl guardener github
  link`; on-demand migrations need the `guardener.actions.migrate` capability. Needs a GitHub
  repo you control — captured fallback needs none of this.

---

## 7. Pre-pull the base images (biggest time-saver)

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
