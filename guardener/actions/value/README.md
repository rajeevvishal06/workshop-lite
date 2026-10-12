# The value of Chainguard Hardened Actions — the full story

Swapping a `uses:` line is the *mechanic*. This module shows the *value* — why SHA-pinned,
hardened actions matter — across four levers that build on each other. Everything here is
grounded in **real data** from the actions this repo actually pins (see PR that Guardener
opened, and the live provenance below).

| # | Lever | Shows | Where |
|---|-------|-------|-------|
| 1 | **Tag-hijack immunity** | the real `tj-actions` attack, and why a moved tag can't touch a pinned+hardened action | [`tag-hijack/`](tag-hijack/README.md) |
| 2 | **Provenance you can prove** | real `source.json` + `HARDENING.md` + SLSA attestation for every pinned action | [`provenance/`](provenance/README.md) |
| 3 | **Green drop-in** | the same pipeline, hardened + SHA-pinned, runs green — zero behavior change | [`workflows/hardened-ci.yml`](workflows/hardened-ci.yml) |
| 4 | **Threat model** | the full attack class, and exactly what hardening removes | [`threat-model.md`](threat-model.md) |

## The 60-second pitch

> A GitHub Action runs **inside your job with your secrets**, and `@v4` is a **mutable
> pointer** anyone who compromises the upstream can repoint — that's how `tj-actions`
> dumped secrets across ~23,000 repos (CVE-2025-30066). Chainguard Hardened Actions fix
> this two ways at once: you **pin an immutable SHA** (the reference can't be rewritten),
> and the action is **rebuilt-from-source, policy-audited, and SLSA-attested** (you can
> prove what's in the commit you pinned). Same inputs and outputs — so it's a one-line-
> per-step change and your pipeline still goes green.

## How it connects to the rest of the workshop
- The **"before"** (mutable tags) is [`../workflows-before/ci.yml`](../workflows-before/ci.yml).
- **Guardener** did the swap automatically — the real migration PR and its "Not migrated"
  table are captured in [`../captured/`](../captured/).
- This `value/` module is the **"so what"**: run the demos to feel the difference, not just
  read the diff.

## Suggested run order (≈10 min)
1. **Read** [`threat-model.md`](threat-model.md) — frame the risk (1 min).
2. **Prove it** — `./provenance/verify-hardening.sh` — real provenance for every pin (2 min).
3. **Feel the threat** — [`tag-hijack/`](tag-hijack/README.md) — mechanism + the `tj-actions`
   story (3 min).
4. **Show it's free** — promote [`workflows/hardened-ci.yml`](workflows/hardened-ci.yml) and
   watch the hardened pipeline go green (live).

## Live vs. local
`provenance/verify-hardening.sh` runs **anywhere** with `gh`+`jq` (no beta needed — it reads
public metadata). The `workflows/*.yml` and `tag-hijack/*.yml` run **live** once copied into
`.github/workflows/` (keep the `tag-hijack/poisoned-action/` path intact).
