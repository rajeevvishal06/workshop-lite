# Captured migration PR (REAL — from a live run on this repo)

This is the actual pull request the Guardener GitHub App (`app/chainguard`) opened against
`rajeevvishal06/workshop-lite` when we ran `chainctl guardener github migrate create`. It is
not illustrative — the SHAs, counts, and the "Not migrated" row are copied from the live PR.

---

**Title:** `Migrate GitHub Actions to Chainguard equivalents`
**Author:** `app/chainguard` · **Branch:** `guardener/_dot_github/workflows` · **+3 / −3**

**Body (verbatim shape):**

> This pull request migrates GitHub Actions used in this repository's workflows to their
> Chainguard-maintained equivalents. It was opened automatically; pinned references remain
> pinned to immutable digests. Major-version changes are permitted only when the repository
> explicitly opts in to closest-supported-major migrations.
>
> | Action | Current version | Chainguard equivalent | Chainguard version | Occurrences |
> | --- | --- | --- | --- | --- |
> | `actions/checkout` | `v4` | `chainguard-actions/actions-checkout` | `v4` | 1 |
> | `docker/build-push-action` | `v6` | `chainguard-actions/docker-build-push-action` | `v6` | 1 |
> | `docker/setup-buildx-action` | `v3` | `chainguard-actions/docker-setup-buildx-action` | `v3` | 1 |
>
> ### Not migrated
> | Action | Version | Occurrences | Reason |
> | --- | --- | --- | --- |
> | `actions/setup-python` | `v5` | 1 | Chainguard equivalent exists, but no published release matches the pinned version |
>
> **Note:** apply the `skip:guardener` label to take manual control (automation stops
> updating/closing the PR or deleting the branch).

The real pins (verified to resolve to real commits + tags):
- `chainguard-actions/actions-checkout@59b7218bfc2eaabc464f1254f6e1dac5bda91d94 # v4`
- `chainguard-actions/docker-setup-buildx-action@6d415f71ab3ef9a7738ade671313ffac4ba9f06c # v3`
- `chainguard-actions/docker-build-push-action@90e59df237ac4b0e77911df31d377167fc308506 # v6`

## Talk track

- *"Every migrated `uses:` went from a **mutable tag** to an **immutable commit SHA** of a
  Chainguard-hardened action — the version kept in a trailing comment so it's still
  readable. A moved tag can't hijack your pipeline anymore."*
- *"Watch what it **didn't** do: `actions/setup-python@v5` was left alone because there's no
  hardened release matching that exact pin. It **refuses to loosen or guess a version** —
  conservative by design. You fix it by pinning a published version like `v5.6.0`."*
- *"Same inputs and outputs — migrating is a one-line-per-step change, nothing else moves."*
- Then go prove it: `value/provenance/verify-hardening.sh` shows the upstream commit,
  policy SHA, and signed attestation behind each pin. See [`../value/`](../value/README.md).

## What the gates taught us (all three required for the App to act)
1. Org linked + App granted access to the repo.
2. Repo **visibility matches the org's Guardener scope** (scope was PUBLIC; a private repo
   needs Chainguard to set scope = ALL, or make the repo public).
3. **`.chainguard/actions.yaml` present on the default branch** to *enable* the feature —
   installing the App alone does nothing.
