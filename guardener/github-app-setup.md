# Guardener GitHub App — exact setup & test steps (as run on `workshop-lite`)

A record of exactly what we did to stand up and demonstrate two Guardener GitHub-App
features on `rajeevvishal06/workshop-lite`: **Hardened Actions** and **Commit Verification**.
Every config file is read from the repo's **default branch (`main`)**, so each one is added
via a PR that you **merge** before testing.

---

## 0. One-time prerequisites

- **Install** the Guardener GitHub App and grant it the repo: https://github.com/apps/chainguard-guardener (Contents: RW + Workflows: RW).
- **Link** your GitHub org/account to your Chainguard group (opens a browser):
  ```bash
  chainctl guardener github link --github-org rajeevvishal06 --parent rajeevcorp
  chainctl guardener github status --parent rajeevcorp      # confirm the link
  ```
- **Default group** so you don't repeat `--parent`:
  ```bash
  chainctl config set default.group rajeevcorp
  ```
- **Role:** you need `owner` (or `guardener.*` capabilities) on the group. Check:
  `chainctl iam role-bindings list --parent rajeevcorp`.
- **Repository visibility must match the Guardener scope.** The scope is **PUBLIC** by
  default (`chainctl guardener entitlement get --parent rajeevcorp`), so the repo must be
  **public**. Private repos need scope `ALL`, which **only Chainguard can set** — contact them.

---

## 1. Hardened Actions  (`.chainguard/actions.yaml`)

### 1a. Enable (merge to `main`)
```yaml
# .chainguard/actions.yaml
enabled: true
migrate:
  enabled: true
  period: "168h"     # NOTE: clamped to a 1-day minimum; shorter values have no effect
```
Add via a PR and merge it. (`enabled` alone = recommendations; the `migrate` block = automated migration PRs.)

### 1b. Feature — PR recommendations (automatic, per-PR, event-driven)
Open a PR that **adds/changes a workflow** using a **mutable** action, e.g. `actions/checkout@v4`.
Within ~30s the `chainguard[bot]` posts a **review comment with a one-click suggestion**:
```
Recommendation: replace actions/checkout@v4 with
  chainguard-actions/actions-checkout@<sha> # v4      (Apply suggestion button)
```
Check run: `actions-recommender` (non-blocking). Only sees lines **in that PR's diff**.

### 1c. Feature — migration PR (bulk, whole-repo, on demand or scheduled)
```bash
chainctl guardener github migrate create rajeevvishal06/workshop-lite --parent rajeevcorp
```
Opens/updates **one** PR (author `app/chainguard`) that rewrites **every** mutable action on
`main` to a SHA-pinned `chainguard-actions/*`. Also runs automatically on the `migrate.period`
cadence (≥ 1 day). Sweeps files **nobody edited** — the complement to 1b.
- Conservative versioning: `actions/setup-python@v5` was **skipped** (`MISSING_VERSION`, no
  bare `v5` hardened tag). Set `migrate.version-strategy: smallest-major-bump` to allow it.
- Apply the `skip:guardener` label on a migration PR to take manual control.

---

## 2. Commit Verification  (`.chainguard/source.yaml`)

Verifies **every commit in a PR** is signed by an authorized signer. Accepted if it satisfies
**any** authority. Check run: **`Enforce - Commit Signing`**.

### 2a. Enable (merge to `main`)
```yaml
# .chainguard/source.yaml
spec:
  authorities:
    # authority #1 — keyless Sigstore (gitsign): a chainguard.dev identity via Google
    - keyless:
        url: https://fulcio.sigstore.dev
        identities:
          - subjectRegExp: '.+@chainguard\.dev$'
            issuer: https://accounts.google.com
      ctlog:
        url: https://rekor.sigstore.dev
    # authority #2 — GitHub's web-flow GPG key (commits made in the GitHub web UI)
    - key:
        kms: https://github.com/web-flow.gpg
```
Add via a PR and merge it. (Once on `main`, every PR gets the check — unsigned commits on
existing open PRs will start failing it. That's expected.)

### 2b. FAIL — unsigned local commit
```bash
git checkout -b cv-fail origin/main
echo x > cv.txt && git add cv.txt
git commit -m "unsigned"          # plain git, no signing
git push -u origin cv-fail && gh pr create --fill
```
Result: `Enforce - Commit Signing` = **action_required**, *"Failed to verify commit signature"*,
`❌ Found Git signature`.

### 2c. PASS — web-flow (GitHub web UI, authority #2)
In the browser: **Add file → Create new file** → commit to a **new branch** → open the PR.
GitHub signs that commit with its `web-flow` key → check **passes**.
> Note: a commit made via `gh`/the REST API with an OAuth token is **not** web-flow signed
> (it comes back `verified=false, unsigned`). Use the actual web UI.

### 2d. PASS — gitsign (your real identity, authority #1)

**Set up gitsign in this repo (local only):**
```bash
cd ~/rv-personal/workshop-lite
brew install sigstore/tap/gitsign          # one-time install
git config --local gpg.format x509
git config --local gpg.x509.program gitsign
git config --local commit.gpgsign true
```
**Make a signed commit and push:**
```bash
git checkout -b cv-pass-gitsign origin/main
echo "signed with gitsign" > commit-verification-demo/gitsign.txt
git add commit-verification-demo/gitsign.txt
git commit -m "gitsign-signed commit"      # a browser opens — log in as
                                            # rajeev.vishal@chainguard.dev via Google
git log -1 --show-signature                 # confirm signer email + issuer before pushing
git push -u origin cv-pass-gitsign
gh pr create --fill
```
Result: check **passes** with all four claims green:
`✅ Found Git signature · ✅ Validated Git signature · ✅ Validated Rekor entry · ✅ Allowed by policy`.
> GitHub's *native* badge shows `bad_cert` for gitsign commits — expected (GitHub doesn't trust
> the Sigstore CA). Guardener does the real Sigstore verification. If the browser login used a
> different OIDC issuer, authority #1's `issuer` wouldn't match — adjust it and re-check.

### 2e. UNSET gitsign  (so normal commits don't pop a browser every time)
```bash
cd ~/rv-personal/workshop-lite
git config --local --unset commit.gpgsign
git config --local --unset gpg.format
git config --local --unset gpg.x509.program
git config --local --get commit.gpgsign      # prints nothing = off
```

---

## Gotchas we hit (so you don't)
- **Config is read from the base branch**, not the PR — a PR that *adds* `actions.yaml`/
  `source.yaml` doesn't get the feature itself; merge it first.
- **`migrate.period` is clamped to ≥ 1 day** — `5m` has no effect; the auto migration PR is
  at most daily. Use `migrate create` to see it immediately.
- **`migrate create` also attempts image migration** — you'll see
  *"Images: migration is not enabled"* until a `.chainguard/images.yaml` with a `migrate` block exists.
- **Private repo + PUBLIC scope = silent no-op** (no comment, no check). Make the repo public
  or get scope `ALL` from Chainguard.
