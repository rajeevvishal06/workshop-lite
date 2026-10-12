# Lever 4 — the threat model: mutable upstream actions vs. hardened + pinned

A side-by-side of what each attack in the GitHub Actions supply-chain class can do to a
normal pipeline, and exactly what Chainguard's Hardened Actions remove. This is the
"why it matters" narrative to pair with the live demos.

## The two pipelines

```
  ❌ upstream, mutable tags                     ✅ chainguard-actions, SHA-pinned + hardened
  ────────────────────────────                 ────────────────────────────────────────────
  uses: actions/checkout@v4                     uses: chainguard-actions/actions-checkout@59b7218… # v4
  uses: tj-actions/changed-files@v35            uses: chainguard-actions/tj-actions-changed-files@<sha> # vN
         │                                               │
         ▼                                               ▼
  tag is a MUTABLE pointer:                      SHA is IMMUTABLE: the reference can't be
  upstream (or an attacker) can repoint          repointed. AND the exact commit was rebuilt
  it to new code that runs in YOUR job           from source, policy-audited, and attested —
  with YOUR secrets, no change on your side.     you can prove what's in it (see provenance/).
```

## The attack class, and what hardening removes

| Attack | What it does to a mutable-tag pipeline | SHA pin? | Hardened action? |
|--------|----------------------------------------|----------|------------------|
| **Tag hijack** (e.g. `tj-actions`, CVE-2025-30066) | Upstream tag is moved to malicious code that dumps your secrets; runs on your next build with zero change on your side | ✅ frozen — moved tag can't touch your pinned SHA | ✅ exact commit is audited + attested, so you also trust *what's in it* |
| **Transitive / nested action compromise** | A hardened-looking action internally `uses:` another action by a mutable tag; the compromise hides one level down | ⚠️ pins the top level only | ✅ Chainguard pins the action's **own** internal `uses:` and image refs to digests, and attests the build |
| **Typosquat / lookalike** | `actions/chekout`, a fork with a familiar name, etc. | — | ✅ you pull from the curated `chainguard-actions/*` catalog, not arbitrary authors |
| **Silent upstream drift** | `@v4` quietly becomes new behavior/deps you never reviewed | ✅ you only move when you bump the SHA | ✅ each version is re-hardened and re-attested on upstream release |
| **"Can't prove what ran"** | After an incident you can't show what code/version executed | ✅ the SHA is in your git history | ✅ `HARDENING.md` + `source.json` + SLSA attestation give a verifiable record |

## The one-sentence version

> Pinning a SHA stops the reference from being **rewritten**; using a Chainguard-hardened
> action lets you **prove what's in** the thing you pinned — and Chainguard keeps it current
> by re-hardening and re-attesting on every upstream release. You get both from a one-line-
> per-step change, with the same inputs and outputs.

## Prove each claim, don't take it on faith
- **Immutability / hijack immunity:** [`tag-hijack/`](tag-hijack/README.md)
- **"Prove what's in it":** [`provenance/`](provenance/README.md) — real `source.json`,
  `HARDENING.md`, and SLSA attestation for every action this repo pins
- **Drop-in, zero behavior change:** [`workflows/hardened-ci.yml`](workflows/hardened-ci.yml)
  runs the same pipeline green
