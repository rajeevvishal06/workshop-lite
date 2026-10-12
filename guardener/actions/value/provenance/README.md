# Lever 2 — prove the hardening (not "trust us")

Pinning a SHA stops the reference from moving. This step answers the next question:
**what's actually in the commit you pinned, and who built it?** Every Chainguard Action
carries its own transparency record, on each version branch of its repo:

| File | What it proves |
|------|----------------|
| `source.json` | the **exact upstream commit** this action was hardened from, plus the **policy SHA** (the ruleset applied) |
| `HARDENING.md` | the per-version report — what was checked and fixed, stamped with the policy SHA; regenerated on every hardening run |
| `attestations/provenance.intoto.jsonl` | a **signed SLSA build attestation** — cryptographic proof of how/by whom it was built |

These live in the action's repo (e.g. `chainguard-actions/actions-checkout`) on the **version
branch** (`v4`), not `main` — read the report for the version you actually pin.

## Run it

[`verify-hardening.sh`](verify-hardening.sh) walks every action you pin and prints all three.
Needs only `gh` (authenticated) + `jq`:

```bash
# defaults to the actions workshop-lite pins; or pass your own owner/repo@ref list:
./verify-hardening.sh \
  chainguard-actions/actions-checkout@v4 \
  chainguard-actions/docker-build-push-action@v6
```

## Real output (from this repo's pins — verified, not illustrative)

```
=== chainguard-actions/actions-checkout@v4 ===
  tag v4 -> immutable commit 59b7218bfc2eaabc464f1254f6e1dac5bda91d94
  hardened from: actions/checkout@v4  (upstream commit 11d5960a326750d5838078e36cf38b85af677262)
  policy SHA:    d636be7e43ef829af6e853da6b3c7566db9f72fe
  report: Action **actions--checkout/v4** was hardened automatically. 0 finding(s) ...
  attestation: attestations/provenance.intoto.jsonl (signed SLSA in-toto) ✓

=== chainguard-actions/docker-build-push-action@v6 ===
  tag v6 -> immutable commit 90e59df237ac4b0e77911df31d377167fc308506
  hardened from: docker/build-push-action@v6  (upstream commit 10e90e3645eae34f1e60eeb005ba3a3d33f178e8)
  policy SHA:    d636be7e43ef829af6e853da6b3c7566db9f72fe
  report: Action **docker--build-push-action/v6** was hardened automatically. 0 finding(s) ...
  attestation: attestations/provenance.intoto.jsonl (signed SLSA in-toto) ✓
```

> **"0 findings" is the honest result for these actions — and it's still valuable.** The
> point isn't that every action was full of holes; it's that each is **rebuilt from source,
> audited against a pinned policy, attested, and re-hardened on every upstream release** —
> and you can *prove* all of that per version. When a finding *does* exist, `HARDENING.md`
> lists what was fixed.

## In CI
[`../workflows/verify-hardening.yml`](../workflows/verify-hardening.yml) runs this as a
scheduled job, so the provenance of what you pin is re-proven automatically — and drift
(e.g. a pin that no longer resolves) gets caught.
