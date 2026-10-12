# Guardener use case 2 — Hardened GitHub Actions

**~5 min · optional.** Where use case 1 hardens your **images**, this hardens your **CI
itself**. Guardener's **Hardened Actions** replaces the upstream GitHub Actions in your
workflows with Chainguard's **hardened, SHA-pinned** equivalents — same inputs and outputs,
only the `uses:` line changes. Pinning to a commit SHA (not a mutable `@v4` tag) is GitHub's
own best practice: it stops **tag hijacking**, where an upstream tag is moved to point at
malicious code and silently reruns in your pipeline.

> **Chainguard Actions** is a catalog of 800+ continuously-hardened drop-in replacements. The
> repo name carries the upstream org as a prefix: `actions/checkout` →
> `chainguard-actions/actions-checkout`, `tj-actions/changed-files` →
> `chainguard-actions/tj-actions-changed-files`.

Unlike use case 1 (a **local** `chainctl agent` run), this is a **GitHub App** capability —
it's configured by a file committed to your repo and it opens pull requests. So in a live room
it needs a GitHub repo + the App installed; the committed `captured/` result lets you tell the
same story without that.

```
guardener/actions/
├── workflows-before/ci.yml        # the BEFORE: a CI workflow pinned to mutable @vN tags
├── .chainguard/actions.yaml       # the config you commit (recommend + auto-migrate PRs)
├── captured/                      # the REAL migration result from a live run on this repo
│   ├── ci.hardened.yml            #   the AFTER: chainguard-actions, SHA-pinned (3/4 migrated)
│   └── pr-summary.md              #   the actual PR body (incl. the "Not migrated" row) + talk track
└── value/                         # ⭐ WHY it matters — the value module (run the demos)
    ├── threat-model.md            #   mutable-tag vs hardened+pinned, the full attack class
    ├── tag-hijack/                #   the real tj-actions attack + a safe local simulation
    ├── provenance/                #   PROVE the hardening: source.json + HARDENING.md + attestation
    └── workflows/                 #   green drop-in hardened CI + a provenance-proof CI job
```

> **Start with [`value/`](value/README.md)** if your goal is to show *why* hardened actions
> matter, not just that a `uses:` line changed. It's grounded in this repo's real migration.

---

## The two modes (both set in `.chainguard/actions.yaml`)

1. **PR recommendations** (`enabled: true`) — when a PR touches `.github/workflows/`, Guardener
   posts **non-blocking** review comments with one-click suggestion blocks for the hardened,
   SHA-pinned equivalent. Nothing is forced.
2. **Automated migration PRs** (`migrate.enabled: true`) — Guardener opens and **keeps updated**
   a single migration PR across all workflows, on the `period` cadence you set, respecting your
   `ignore` lists. See [`.chainguard/actions.yaml`](.chainguard/actions.yaml).

---

## Option A — run it live (GitHub App + chainctl)

```bash
# 1. Install the Guardener GitHub App on the org/repo:
#    https://github.com/apps/chainguard-guardener
#    (requests Contents: RW + Workflows: RW — to read workflows and push migration PR branches)

# 2. Link your GitHub org to your Chainguard group (unlocks private repos + group-scoped ops):
chainctl guardener github link <github-org> --group <chainguard-group>

# 3. Commit .chainguard/actions.yaml to the repo root (see this folder's copy).

# 4a. Inventory what you'd migrate (optional but handy):
chainctl actions discover
#     If your org restricts which actions can run, allow  chainguard-actions/*  first.

# 4b. Or trigger an on-demand, org-wide migration PR right now:
chainctl guardener github migrate create <owner/repo>
#     --parent <group>   the Chainguard group that owns the App installation
#     --wait=false       return immediately with an operation name (default waits + prints the PR)
#     --timeout 10m      how long to wait (default 10m)

# 5. Check a migration you didn't wait on:
chainctl guardener github migrate get <operation-name>
```
> `migrate create` needs the **`guardener.actions.migrate`** capability on the group that owns
> the repo's App installation. Keep SHAs fresh afterward with **Renovate** or **Dependabot**.

---

## Option B — no access? show the captured result (always works)

```bash
cd guardener/actions
diff workflows-before/ci.yml captured/ci.hardened.yml   # mutable @vN tags -> SHA-pinned chainguard-actions
cat captured/pr-summary.md                               # the PR Guardener would open + talk track
```

The diff is the whole story: every `uses:` flips from a mutable tag to a **SHA-pinned**
`chainguard-actions/*` with the version preserved in a trailing comment — your run/test steps
untouched.

---

## Where it fits the story

| Use case | Surface | How you run it |
|----------|---------|----------------|
| 1 — Dockerfile migration | your **images** | local `chainctl agent dockerfile build` |
| 2 — Hardened Actions (this) | your **CI/CD** | GitHub App + `.chainguard/actions.yaml` / `chainctl guardener github migrate` |

Same Guardener, two surfaces. Hardening the base image stops OS-layer CVEs; hardening the
actions stops a **supply-chain** class the image scan never sees — a hijacked tag running in
CI with your secrets. For a single-repo pilot where installing an org-wide App isn't an
option, the **`cg-actions`** Claude Code skill does the same swap for one repository.
