# Workshop-lite — live run-of-show

Your single sheet for delivering it. Each step has the **command**, the **say this**, and the
**look for**. Run Part 0 off-screen; do Parts 1–5 live. Ports: Python `8080`, Java `8081`.

> Numbers drift a little with grype's DB — quote ranges ("~2,200 → ~10 → 9"), not exact totals.

---

## Part 0 — Before you present (off-screen, ~5 min)

```bash
cd ~/rv-personal/workshop-lite
chainctl auth login && chainctl auth configure-docker
grype db update

# free port 8080 if the big project is still up
( cd ~/rv-personal/partner-workshop && make down ) 2>/dev/null

# token (valid ~1h — re-run this export if your session runs long)
export CGR_TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)

# pre-build all 6 images so nothing waits on screen
docker build -q -f python/Dockerfile.1-baseline -t py:baseline python
docker build -q -f python/Dockerfile.2-image    -t py:image    python
docker build -q -f python/Dockerfile.3-library  --secret id=cgr_token,env=CGR_TOKEN -t py:library python
docker build -q -f java/Dockerfile.1-baseline   -t jv:baseline java
docker build -q -f java/Dockerfile.2-image      -t jv:image    java
docker build -q -f java/Dockerfile.3-library    --secret id=cgr_token,env=CGR_TOKEN -t jv:library java
```
Have open: a terminal + a browser tab on `localhost:8080`.

---

## Part 1 — Open (30 sec)

> *"Two kinds of CVEs hide in every container: the base image, and the libraries your app pulls
> in. This is one tiny app. I'll change only the base image, then only where the library comes
> from — never the app code — and watch both drop to zero. Then I'll show you the part people
> forget: proving where it all came from."*

---

## Part 2 — Python track

**Act 1 — baseline (🔴).**
```bash
docker run --rm -d -p 8080:8080 --name c py:baseline && sleep 2
curl localhost:8080          # served by werkzeug 3.0.2
docker rm -f c
grype py:baseline --config grype.yaml
```
**Say:** *"It runs — but ~2,200 CVEs, 122 critical. This is where most teams start."*

**Act 2 — swap the base image (🟠).**
```bash
diff python/Dockerfile.1-baseline python/Dockerfile.2-image   # one FROM line
grype py:image --config grype.yaml
```
**Say:** *"One line — the base image. 2,200 down to ten, zero critical. But that 1 high is
`werkzeug` — it's in the **library**, not the OS. Hardening the image didn't touch it."*

**Act 3 — Chainguard Libraries (🟢).**
```bash
docker run --rm -d -p 8080:8080 --name c py:library && sleep 2
curl localhost:8080          # served by werkzeug 3.0.2+cgr.1
docker rm -f c
grype py:library --config grype.yaml
```
**Say:** *"Same app, same pinned `werkzeug==3.0.2` — now `3.0.2+cgr.1`, CVE gone. No code
change, no version bump. And everything came through Chainguard — never PyPI directly."*
**Look for:** UI flips `3.0.2` → `3.0.2+cgr.1`; grype `9 / 0 / 0`.

---

## Part 3 — Java track (same arc, port 8081)

**Act 1 — baseline.**
```bash
docker run --rm -d -p 8081:8080 --name jc jv:baseline && sleep 2
curl localhost:8081          # backed by h2-1.4.200.jar
docker rm -f jc
grype jv:baseline --config grype.yaml
```
**Say:** *"Java app backed by H2 1.4.200 — two criticals, the H2 Console RCEs from right after
Log4Shell."*

**Act 2 — swap the base image.**
```bash
diff java/Dockerfile.1-baseline java/Dockerfile.2-image   # one runtime FROM line
grype jv:image --config grype.yaml
```
**Say:** *"269 down to ~5 — but the two criticals **persist**. They're in H2, not the OS. Watch."*

**Act 3 — Chainguard Libraries.**
```bash
docker run --rm -d -p 8081:8080 --name jc jv:library && sleep 2
curl localhost:8081          # backed by h2-1.4.200-0.cgr.3.jar
docker rm -f jc
grype jv:library --config grype.yaml
```
**Say:** *"H2 is now `1.4.200-0.cgr.3`, both criticals gone, app unchanged. For a team stuck on
H2 1.4.200 because 2.x is a breaking change — this fixes it today."*
**Look for:** UI shows `h2-1.4.200-0.cgr.3.jar`; grype `~2 / 0 / 1`.
> The `h2 driver reports: 1.4.200` line is H2's internal code line (unchanged by remediation) —
> the **jar name** is the remediated coordinate. Mention it if asked.

---

## Part 4 — Provenance & SBOM (the must-call-out)

Set the token if it expired: `export CGR_TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)`

**4a. Prove authenticity — and gate on it.**
```bash
cosign verify \
  --certificate-oidc-issuer=https://token.actions.githubusercontent.com \
  --certificate-identity=https://github.com/chainguard-images/images/.github/workflows/release.yaml@refs/heads/main \
  cgr.dev/chainguard/python:latest-dev >/dev/null && echo "VERIFIED ✓"

cosign verify --certificate-identity-regexp='.*' --certificate-oidc-issuer-regexp='.*' python:3.14
#   Error: no signatures found
```
**Say:** *"I can cryptographically prove this image was built by Chainguard's release workflow
and hasn't been tampered with — and make my cluster reject anything that isn't. The public
image? No signature to verify at all."*

**4b. Read the provenance — a verifiable build record.**
```bash
curl -s -u "user:$CGR_TOKEN" \
  "https://libraries.cgr.dev/python-remediated/integrity/werkzeug/3.0.2%2Bcgr.1/werkzeug-3.0.2%2Bcgr.1-py3-none-any.whl/provenance" \
  | python3 -c 'import sys,json,base64; e=json.load(sys.stdin)["attestation_bundles"][0]["attestations"][0]["envelope"]; s=json.loads(base64.b64decode(e.get("statement") or e.get("payload"))); print("buildType:", s["predicate"]["buildDefinition"]["buildType"]); print("builder  :", s["predicate"]["runDetails"]["builder"]["id"]); print("subject  :", s["subject"][0]["name"])'
```
**Say:** *"The remediated library itself carries signed SLSA provenance — you can see exactly how
and by whom it was built, traced to source. That's how you'd catch a SolarWinds-style tampered
build: the artifact wouldn't match its provenance."*

**4c. Use the SBOM to answer "am I affected?"**
```bash
syft -q cgr.dev/chainguard/python:latest-dev | grep -iE 'openssl|libcrypto'
#   libcrypto3   3.6.4-r4   <- your exact OpenSSL version, in seconds
```
**Say:** *"When the next openssl or Log4Shell drops, the SBOM is your instant inventory — you
query it and know. Chainguard ships it signed, with the image, so you trust it instead of
regenerating one and hoping it's complete."*

**Quick side-by-side (optional):**
```bash
cosign tree python:3.14                                       # public -> nothing
cosign tree cgr.dev/chainguard/python:latest-dev 2>/dev/null  # Chainguard -> attestations + signatures
```

---

## Part 5 — Close: integration is easy

```bash
# Python — everything through Chainguard, no code/requirements change:
export PIP_INDEX_URL="https://user:$CGR_TOKEN@libraries.cgr.dev/python-remediated/simple/"
export PIP_EXTRA_INDEX_URL="https://user:$CGR_TOKEN@libraries.cgr.dev/python/simple/"
# pip install -r requirements.txt   ->  werkzeug 3.0.2 becomes 3.0.2+cgr.1 automatically
```
**Say:** *"That's the whole integration — point your package manager at Chainguard. Java is a
`settings.xml` block. Even packages Chainguard hasn't built come through its proxy, malware
scanned — you stop pulling from PyPI and Maven Central entirely. Fewer CVEs, remediated
libraries, and a supply chain you can prove — your developers change nothing."*

---

## Part 6 — Reset / cleanup
```bash
docker rm -f c jc 2>/dev/null
docker image rm -f py:baseline py:image py:library jv:baseline jv:image jv:library 2>/dev/null
```

---

## Validated end-state (reference)
| | baseline | image | library |
|---|---|---|---|
| **Python** (werkzeug) | ~2200 / 122 / ~499 | ~10 / 0 / 1 | 9 / 0 / **0**  ·  `3.0.2+cgr.1` |
| **Java** (h2) | ~269 / 2 / 6 | ~5 / **2** / 2 | ~2 / **0** / 1  ·  `1.4.200-0.cgr.3` |

Provenance/SBOM: Chainguard image `cosign verify` → **VERIFIED**; public → **no signatures**;
library provenance → SLSA v1 build record; SBOM query → exact package versions on demand.

## If something breaks
- **Curl returns a big HTML dashboard** → the main project is on :8080. `( cd ~/rv-personal/partner-workshop && make down )`, or use `-p 8090:8080`.
- **401 / token errors** → `export CGR_TOKEN=$(chainctl auth token --audience libraries.cgr.dev 2>/dev/null)` (tokens last ~1h).
- **Library build resolves nothing / fails** → confirm the org's Libraries entitlement **and upstream proxy policy** are enabled.
- **Act 3 numbers don't drop** → make sure every scan passes `--config grype.yaml`.
