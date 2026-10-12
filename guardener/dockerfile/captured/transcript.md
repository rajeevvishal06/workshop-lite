# Captured Guardener run (reference)

A representative transcript of `chainctl agent dockerfile build` migrating
`Dockerfile.legacy` (ubuntu:22.04) to Chainguard. Use this to narrate the module when
the Guardener beta is not enabled on your demo org. Real runs are non-deterministic;
the shape is always the same: **analyze → convert → build → observe → fix → repeat → done.**

```
$ chainctl agent dockerfile build -f Dockerfile.legacy -t py:guardener .

▸ analyzing build context (Dockerfile.legacy, app/requirements.txt, app/app.py)
▸ base image  ubuntu:22.04  ->  candidate: cgr.dev/chainguard/python
▸ detected: apt-installed python3 + pip; root user; build-essential retained in final image
▸ drafting Chainguard multi-stage Dockerfile (deps on -dev, runtime on distroless)
▸ build attempt 1 … OK (deps resolved into --user site-packages)
▸ runtime attempt 1 … FAILED: `python3` not on PATH in distroless runtime
    fix: entrypoint uses `python` (Chainguard images provide `python`); copy --user site-packages
▸ build attempt 2 … OK
▸ smoke test: container starts, binds :8080, HTTP 200 on /            … PASS
▸ image size 1.01 GB -> 118 MB   |   runs as nonroot (65532)   |   0 apt packages
✔ wrote Dockerfile.legacy.migrated
✔ tagged py:guardener
```

## Talk track

- *"This wasn't a search-and-replace. Guardener **built the image, watched it fail, and
  fixed it** — the `python3` vs `python` entrypoint is exactly the kind of thing that eats
  an afternoon of manual migration."*
- *"It burned Chainguard's tokens iterating, not your engineers' time. What normally takes a
  team days per service, it does in minutes — and hands you a Dockerfile you can review."*
- Point at the migrated file: nonroot, multi-stage, distroless runtime, no apt — **the same
  hardening the Python track did by hand, produced automatically.** Then scan it:
  `grype py:guardener` shows the same OS-CVE collapse as `Dockerfile.2-image`.
