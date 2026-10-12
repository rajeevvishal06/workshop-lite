# Guardener — the intelligent migration expert (optional modules)

Guardener is Chainguard's AI migration tool: a growing suite of capabilities you opt into
independently. This workshop covers the **two** headline use cases, each self-contained with a
committed **captured** result so it works even without beta access.

| # | Use case | Hardens | How you run it | Folder |
|---|----------|---------|----------------|--------|
| 1 | **Dockerfile migration** | your **images** | **local** `chainctl agent dockerfile build` (AI build+fix loop) | [`dockerfile/`](dockerfile/) |
| 2 | **Hardened Actions** | your **CI/CD** | **GitHub App** + `.chainguard/actions.yaml` / `chainctl guardener github migrate` | [`actions/`](actions/) |

The through-line with the Python/Java tracks: those swap the base image **by hand**, one
service at a time. Guardener does the **same migrations automatically, at fleet scale** — use
case 1 for your Dockerfiles, use case 2 for the CI that builds them.

> **Beta & entitlement-gated.** An NFR org doesn't automatically have Guardener — access is
> granted per-org (talk to your account team / PM). Dockerfile migration needs the
> `guardener.user` role (and a one-time `chainctl agent accept-terms`, which needs
> `guardener.admin`); the Actions App needs the org linked with `chainctl guardener github link`
> and the `guardener.actions.migrate` capability for on-demand runs. **Nothing in the
> Python/Java/provenance tracks depends on either module** — and both ship a captured fallback.

> **Checking it works?** [`VERIFY.md`](VERIFY.md) has exact build/run/scan and YAML-validation
> steps for both use cases (captured mode needs no beta).

Start with whichever surface matters to your audience:
- **[`dockerfile/`](dockerfile/README.md)** — AI rewrites a legacy `ubuntu:22.04` Dockerfile to
  a minimal, nonroot, distroless Chainguard build, building and fixing until it runs.
- **[`actions/`](actions/README.md)** — the GitHub App swaps every `uses:` in your workflows for
  a SHA-pinned Chainguard-hardened equivalent, via recommendation comments or an auto-maintained
  migration PR.
