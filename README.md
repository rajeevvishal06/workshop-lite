# Chainguard Images + Libraries — the 5-minute, no-magic version

A deliberately tiny demo you drive with **plain `docker build` + `grype`** — no Makefiles, no
scripts, no dashboard. The point: show, with nothing hidden, that adopting **Chainguard Images
and Chainguard Libraries is a drop-in change** — your application code and pinned dependency
versions never change.

> **Presenting it live?** [`RUNBOOK.md`](RUNBOOK.md) is the run-of-show — pre-build steps, exact
> commands, talk track, and what to point at, for every step.

Two self-contained tracks. Each is one small app + **three Dockerfiles that differ by one
meaningful line each**:

```
workshop-lite/
├── grype.yaml            # shared scanner config (optional safeguard — see below)
├── python/               # a ~15-line web app served by werkzeug (the hero library)
│   ├── app/  ·  Dockerfile.1-baseline  ·  Dockerfile.2-image  ·  Dockerfile.3-library
├── java/                 # a tiny program using H2 as a real database (the hero library)
│   └── app/  ·  Dockerfile.1-baseline  ·  Dockerfile.2-image  ·  Dockerfile.3-library
└── guardener/            # optional: AI migration at fleet scale (runs live OR replays a capture)
    ├── dockerfile/        #   use case 1 — migrate a legacy Dockerfile to Chainguard
    └── actions/           #   use case 2 — harden GitHub Actions (SHA-pinned chainguard-actions)
```

| Act | Base image | Library from | Result |
|-----|-----------|--------------|--------|
| `Dockerfile.1-baseline` | upstream (`python:3.14` / `temurin:17-jre`) | public registry | lots of CVEs |
| `Dockerfile.2-image` | Chainguard | public registry | base CVEs gone, library CVEs remain |
| `Dockerfile.3-library` | Chainguard | **Chainguard remediated** | library CVEs gone too |

---

## The headline: integrating Chainguard Libraries is easy

You do **not** change your code or your pinned versions. You only change **where your package
manager looks.**

Everything routes **through Chainguard** — both the remediated index *and* the standard index,
which serves Chainguard-built packages and safely proxies anything else (malware scanning +
cooldown). You never point at public PyPI / Maven Central directly.

**Python — 2 lines, zero manifest change:**
```bash
export PIP_INDEX_URL="https://user:$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)@libraries.cgr.dev/python-remediated/simple/"
export PIP_EXTRA_INDEX_URL="https://user:${CGR_TOKEN}@libraries.cgr.dev/python/simple/"   # Chainguard, NOT pypi.org
pip install -r requirements.txt        # werkzeug==3.0.2 now resolves to 3.0.2+cgr.1
```

**Java — a `settings.xml` that routes all of Maven through Chainguard:**
```xml
<!-- ~/.m2/settings.xml (inject the token from an env var; don't commit it) -->
<settings>
  <servers>
    <server><id>cgr-proxy</id><username>user</username><password>${env.CGR_TOKEN}</password></server>
    <server><id>cgr-remediated</id><username>user</username><password>${env.CGR_TOKEN}</password></server>
  </servers>
  <!-- send ALL of Maven (deps + plugins) through Chainguard's java index (proxies upstream,
       malware-scanned) — except the remediated repo, which is queried directly -->
  <mirrors>
    <mirror><id>cgr-proxy</id><mirrorOf>*,!cgr-remediated</mirrorOf><url>https://libraries.cgr.dev/java/</url></mirror>
  </mirrors>
  <profiles><profile><id>cgr</id><repositories>
    <repository><id>cgr-remediated</id><url>https://libraries.cgr.dev/java-remediated/</url></repository>
  </repositories></profile></profiles>
  <activeProfiles><activeProfile>cgr</activeProfile></activeProfiles>
</settings>
```
```xml
<!-- pom.xml: name the remediated build (Java resolves exact coordinates) -->
<dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><version>1.4.200-0.cgr.3</version></dependency>
```

That asymmetry is worth saying out loud: **Python is effortless (pip auto-picks the remediated
build); Java is one explicit coordinate.** Neither touches your application code. The rest of
this repo just proves it in a container and measures the CVE drop.

---

## Prerequisites

- **Docker** (BuildKit — default on modern Docker).
- **grype** — `brew install grype`. **cosign** + **syft** for the provenance/SBOM section —
  `brew install cosign syft`.
- **chainctl**, logged in to an org entitled to Chainguard Libraries:
  ```bash
  chainctl auth login
  chainctl auth configure-docker      # so cgr.dev images pull
  ```

Run everything below from the `workshop-lite/` root.

---

## Python track

### Act 1 — baseline (upstream image + PyPI)
```bash
docker build -f python/Dockerfile.1-baseline -t py:baseline python
docker run --rm -d -p 8080:8080 --name pyc py:baseline && sleep 2
curl localhost:8080                    # -> served by werkzeug 3.0.2
docker rm -f pyc
grype py:baseline --config grype.yaml
```
Expect **thousands of CVEs (~2,000+), ~120 critical** — almost all of it the base OS image.

### Act 2 — Chainguard image (swap the base)
```bash
docker build -f python/Dockerfile.2-image -t py:image python
grype py:image --config grype.yaml
```
Expect the total to **collapse to a dozen-ish, 0 critical**. Among the remaining items is the
vulnerable `werkzeug` (`CVE-2024-34069`, the debugger RCE) — image hardening didn't touch your library.
`diff python/Dockerfile.1-baseline python/Dockerfile.2-image` → the `FROM` line.

### Act 3 — Chainguard Libraries (remediate)
```bash
export CGR_TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)
docker build -f python/Dockerfile.3-library --secret id=cgr_token,env=CGR_TOKEN -t py:library python
docker run --rm -d -p 8080:8080 --name pyc py:library && sleep 2
curl localhost:8080                    # -> served by werkzeug 3.0.2+cgr.1
docker rm -f pyc
grype py:library --config grype.yaml
```
Expect **0 critical, and `CVE-2024-34069` gone** — the werkzeug debugger RCE is remediated,
`requirements.txt` unchanged. (A few newer low/medium advisories may still show — that's DB
drift, not the point.) Confirm the token never entered the image (history shows the *mount*, not the value):
```bash
docker history --no-trunc py:library | grep -c 'eyJ'    # -> 0 (no JWT in any layer)
```

---

## Java track

The app is a tiny web app (the JDK's built-in HTTP server — **no web framework**) that queries
H2 as a real in-memory database on each request. No Spring Boot, so the only meaningful CVEs
are the base image and the H2 hero library. You curl it just like the Python one (on `:8081`
here, so it can run alongside the Python app).

### Act 1 — baseline (upstream image + Maven Central)
```bash
docker build -f java/Dockerfile.1-baseline -t jv:baseline java
docker run --rm -d -p 8081:8080 --name jc jv:baseline && sleep 2
curl localhost:8081                    # -> backed by h2-1.4.200.jar, query result: Chainguard
docker rm -f jc
grype jv:baseline --config grype.yaml
```
Expect a few hundred total with **2 critical** — H2 1.4.200 carries the 2 H2 Console RCE
**criticals** (`CVE-2021-42392`, `CVE-2022-23221`).

### Act 2 — Chainguard image (swap the base)
```bash
docker build -f java/Dockerfile.2-image -t jv:image java
grype jv:image --config grype.yaml
```
Expect the total to **collapse to a dozen-ish, but the 2 H2 criticals persist** — they're in
the library, not the OS.
`diff java/Dockerfile.1-baseline java/Dockerfile.2-image` → the runtime `FROM` line.

### Act 3 — Chainguard Libraries (remediate)
```bash
export CGR_TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)
docker build -f java/Dockerfile.3-library --secret id=cgr_token,env=CGR_TOKEN -t jv:library java
docker run --rm -d -p 8081:8080 --name jc jv:library && sleep 2
curl localhost:8081                    # -> backed by h2-1.4.200-0.cgr.3.jar  (remediated!)
docker rm -f jc
grype jv:library --config grype.yaml
```
Expect **0 critical** — H2 is now `1.4.200-0.cgr.3` and both console-RCE criticals are gone.
`diff java/Dockerfile.2-image java/Dockerfile.3-library` → the Maven step (remediated
coordinate + a `settings.xml` built from the token secret).
> Heads-up for the demo: the UI shows the H2 **jar name** (`h2-1.4.200-0.cgr.3.jar` at this
> act) — that's the remediated Maven coordinate, read from the classpath. The separate line
> `h2 driver reports: 1.4.200` is H2's internal code line, which remediation doesn't change —
> mention it if asked so no one thinks the jar name is off.

---

## Checking the running app

Both tracks are web apps — run the image, then hit it with `curl` or a browser. They use
different ports so they can run at the same time:

| Track | Run it | Check it |
|-------|--------|----------|
| Python | `docker run --rm -d -p 8080:8080 --name pyc py:library && sleep 2` | `curl localhost:8080` · or open http://localhost:8080 |
| Java | `docker run --rm -d -p 8081:8080 --name jc jv:library && sleep 2` | `curl localhost:8081` · or open http://localhost:8081 |

- Swap the tag (`py:baseline` / `py:image` / `py:library`, same for `jv:`) to check any act.
- The response tells you which act you're on: Python shows the **werkzeug version**
  (`3.0.2` → `3.0.2+cgr.1`); Java shows the **h2 jar name** (`h2-1.4.200.jar` →
  `h2-1.4.200-0.cgr.3.jar`).
- Follow logs: `docker logs -f c` (or `jc`). See what's running: `docker ps`.
- Stop when done: `docker rm -f pyc jc`.
- **Curl returns a big HTML dashboard instead of plain text?** That's the *other* project on
  port 8080 — stop it (`cd ~/rv-personal/partner-workshop && make down`) or use a free port
  (e.g. `-p 8090:8080`, then curl 8090).

---

## Provenance & SBOM — and what they're actually FOR

Chainguard artifacts carry signed **SLSA provenance** and an **SBOM** by default; public ones
don't. But the value isn't that they *exist* — it's what you can **do** with them. Three quick
proofs:

### 1. Prove authenticity — and gate on it (`cosign verify`)
```bash
# Chainguard image: VERIFIED — and it tells you exactly which builder signed it
cosign verify \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  --certificate-identity=https://github.com/chainguard-images/images/.github/workflows/release.yaml@refs/heads/main \
  cgr.dev/chainguard/python:latest-dev >/dev/null && echo "VERIFIED ✓"

# Same policy on the public image:
cosign verify --certificate-identity-regexp='.*' --certificate-oidc-issuer-regexp='.*' python:3.14
#   Error: no signatures found
```
**Why it matters:** you can cryptographically prove the image is genuine and untampered — and
make your CI or Kubernetes admission policy **reject anything not signed by Chainguard**. The
public image can't be verified at all; you're trusting the registry and hoping.

### 2. Read the provenance — a verifiable build record, not a mystery binary
```bash
TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)
curl -s -u "user:$TOKEN" \
  "https://libraries.cgr.dev/python-remediated/integrity/werkzeug/3.0.2%2Bcgr.1/werkzeug-3.0.2%2Bcgr.1-py3-none-any.whl/provenance" \
  | jq -r '.attestation_bundles[0].attestations[0].envelope | (.payload // .statement) | @base64d | fromjson | "buildType: \(.predicate.buildDefinition.buildType)\nbuilder  : \(.predicate.runDetails.builder.id)\nsubject  : \(.subject[0].name)"'
# buildType: .../ecosystems-wheel-rebuilder/PythonBuild@v1
# builder  : .../ecosystems-wheel-rebuilder/workflows/python-build-versions.yaml@<sha>
# subject  : werkzeug-3.0.2+cgr.1-py3-none-any.whl
```
**Why it matters:** you can see exactly **how and by whom** each package was built, traced to
source and a specific build workflow. That's how you'd catch a SolarWinds-style tampered build —
the artifact wouldn't match its provenance.

### 3. Use the SBOM to answer "am I affected?" in seconds
```bash
# The next openssl / Log4Shell drops. Which version do you actually ship?
syft -q cgr.dev/chainguard/python:latest-dev | grep -iE 'openssl|libcrypto'
#   libcrypto3         3.6.4-r4    apk   <- your exact OpenSSL version, in seconds
#   libcurl-openssl4   8.22.0-r2   apk
```
**Why it matters:** the SBOM is your instant inventory. Instead of scrambling when a CVE lands,
you query it and know in seconds. Chainguard ships the SBOM **signed, with the image**, so you
*trust* it rather than regenerate one yourself and hope it's complete.

### The one-glance side-by-side
```bash
cosign tree python:3.14                                       # public   -> "No ... Artifacts found"
cosign tree cgr.dev/chainguard/python:latest-dev 2>/dev/null  # Chainguard -> Attestations + Signatures
```
> And your scanners get the remediation for free: Chainguard publishes an **OpenVEX** feed
> (`https://libraries.cgr.dev/openvex/v1`) that grype / trivy / etc. consume, so fixed status
> shows up in the tools you already run — no new tool to buy.

---

## How it works (the five things worth knowing)

1. **Knob 1 — the base image** (`FROM`). Upstream → Chainguard removes the OS-layer CVEs.
2. **Knob 2 — the library source.** PyPI → `libraries.cgr.dev/python-remediated`; Maven Central
   → `libraries.cgr.dev/java-remediated`.
3. **Python needs no manifest change** — pip auto-selects `3.0.2+cgr.1` (a PEP 440 local version
   that outranks plain `3.0.2`). **Java names the exact coordinate** `1.4.200-0.cgr.3`.
4. **The token is a build secret** (`--secret id=cgr_token,env=CGR_TOKEN`, mounted `mode=0444`
   so the non-root Chainguard user can read it), so it never lands in an image layer.
5. **`grype.yaml` (`using-cpes: false`) is an optional safeguard** — it stops grype re-reporting
   a remediated CVE via fuzzy CPE matching. With the current grype + DB it's a **no-op** (the
   `+cgr`/`-0.cgr` builds are recognized as fixed with or without it); it's kept as insurance.

> **CVE totals drift** — grype's DB updates daily, so exact counts change over time. The story
> is constant: **criticals cleared** and the **named CVE removed** (werkzeug `CVE-2024-34069`,
> the H2 console RCEs) at the library act. Lead with those, not the raw total.

> Two Chainguard indexes, no public one: `.../python-remediated` (`.../java-remediated`) serves
> the CVE-backported builds; `.../python` (`.../java`) serves everything else — Chainguard's own
> built-from-source packages **plus a protected proxy of upstream** (malware scanning + cooldown)
> for whatever Chainguard hasn't built. The library act uses the remediated index first and the
> standard index as fallback, so **no dependency ever comes straight from PyPI / Maven Central** —
> proxied packages are still scanned on the way through.

---

## Integrating into YOUR setup (pick what matches you)

- **Local dev / existing project:** the 2-line (Python) or `settings.xml` (Java) shown at the
  top. For a durable credential instead of a session token:
  `chainctl auth pull-token create --repository=python|java --ttl=720h`.
- **Committed config** (`pip.conf`, `~/.m2/settings.xml`, `build.gradle`): keep the token out
  of git — inject it from an env var / CI secret at runtime.
- **CI / container builds:** the `Dockerfile.3-library` pattern here — token as a BuildKit
  `--secret`, never a `--build-arg` (build-args show up in image history).
- **Production at scale:** point your artifact manager (JFrog / Nexus / Cloudsmith) at
  `libraries.cgr.dev/...` (remediated + standard) and let Chainguard's protected proxy handle
  upstream — so the artifact manager never reaches out to public PyPI / Maven Central itself.
  Developers change nothing at all.
- **No token in the URL:** Chainguard publishes a pip keyring backend
  (`chainguard-dev/keyrings-chainguard-libraries`) that authenticates via `chainctl`
  automatically — see the Chainguard Libraries access docs.

---

## Optional: Guardener — automate the migration at fleet scale

The tracks above swap the base image **by hand** — one `FROM` line, easy for one service.
**Guardener** is the answer to *"we have 400 of these"*: Chainguard's AI migration tool, in two
use cases. Same no-magic spirit — plain commands, each with a **captured** result committed so
it works even without access. Full walkthroughs in [`guardener/README.md`](guardener/README.md).

**Use case 1 — Dockerfile migration** ([`guardener/dockerfile/`](guardener/dockerfile/)). An AI
agent (local, via `chainctl`) reads a legacy Dockerfile, rewrites it to Chainguard, then
**builds the image, watches it fail, and fixes it** until it runs.
```bash
cd guardener/dockerfile
chainctl agent accept-terms                                   # one-time (needs guardener.admin)
chainctl agent dockerfile build -f Dockerfile.legacy -t py:guardener .
diff Dockerfile.legacy Dockerfile.legacy.migrated
# no access? the committed capture tells the same story:
diff Dockerfile.legacy captured/Dockerfile.migrated.reference && cat captured/transcript.md
```
The result is the same minimal, nonroot, distroless image as `python/Dockerfile.2-image` —
`grype py:guardener` shows the identical OS-CVE collapse, produced **for** you.

**Use case 2 — Hardened GitHub Actions** ([`guardener/actions/`](guardener/actions/)). A GitHub
**App** swaps every `uses:` in your workflows for a **SHA-pinned** Chainguard-hardened
equivalent — same inputs/outputs — killing tag-hijack supply-chain attacks the image scan never
sees. Configured by a committed `.chainguard/actions.yaml`; runs as recommendation comments or
an auto-maintained migration PR.
```bash
# live: install github.com/apps/chainguard-guardener, then
chainctl guardener github link <github-org> --group <chainguard-group>
chainctl guardener github migrate create <owner/repo>
# no access? the committed capture tells the same story:
cd guardener/actions && diff workflows-before/ci.yml captured/ci.hardened.yml && cat captured/pr-summary.md
```

> **Beta, entitlement-gated.** An NFR org doesn't automatically have Guardener — access is
> granted per-org via the beta waitlist. Nothing in the tracks above depends on either module.

---

## Cleanup
```bash
docker rm -f pyc jc gd 2>/dev/null
docker image rm -f py:baseline py:image py:library jv:baseline jv:image jv:library py:guardener 2>/dev/null
```
