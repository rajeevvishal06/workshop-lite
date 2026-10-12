# Guardener use case 1 — AI Dockerfile migration, at fleet scale

**~5 min · optional.** The Python and Java tracks swapped the base image **by hand** — one
`FROM` line, easy for one service. Guardener is the answer to *"we have 400 Dockerfiles"*: it
**reads a legacy Dockerfile, rewrites it to Chainguard, then builds the image, watches it
fail, and fixes it** in a loop until it runs — automated and verified.

> The agent runs **server-side**, but it never touches your machine directly: every file read
> and Docker build is *requested* by the agent and **mediated by `chainctl` locally**, which
> only performs the ones it deems acceptable. Your source and builds stay on your machine.

Same no-magic spirit as the rest of workshop-lite: plain commands, nothing hidden, and a
**captured** result committed in [`captured/`](captured/) so the story lands even without
beta access.

```
guardener/dockerfile/
├── Dockerfile.legacy          # the BEFORE: ubuntu:22.04, root, apt, build toolchain (~1 GB)
├── app/                       # the same werkzeug app from the Python track (so it builds & runs)
└── captured/
    ├── Dockerfile.migrated.reference   # a representative Guardener output (the AFTER)
    └── transcript.md                   # what the agent did, step by step + talk track
```

> **Access note.** Guardener is in **beta and entitlement-gated** — an NFR org does **not**
> automatically have it. Running a migration needs the **`guardener.user`** role; a one-time
> `chainctl agent accept-terms` first needs the **`guardener.admin`** role. Nothing in the
> Python/Java/provenance tracks depends on this module.

---

## Option A — run it live (if Guardener is enabled on your org)

The result is written next to the input as `Dockerfile.legacy.migrated`. Run from this folder:

```bash
cd guardener/dockerfile
chainctl auth login                 # if not already
chainctl agent accept-terms         # one-time per org (needs guardener.admin)

# AI iterative build+fix — burns Chainguard tokens, takes a few minutes:
chainctl agent dockerfile build -f Dockerfile.legacy -t py:guardener .
```
> `chainctl agent dockerfile` also offers `optimize`, `upgrade`, and `validate` for an
> already-migrated Dockerfile. Pass build args with `--build-arg` if your image needs them.

Then show the before/after and prove it runs:
```bash
diff Dockerfile.legacy Dockerfile.legacy.migrated     # what the agent rewrote
docker run --rm -d -p 8082:8080 --name gd py:guardener && sleep 2
curl localhost:8082                                   # served by werkzeug 3.0.2
docker rm -f gd
grype py:guardener                                    # same OS-CVE collapse as Dockerfile.2-image
```
> Port `8082` so it can run alongside the Python (`8080`) and Java (`8081`) tracks. Every
> migration also prints **post-migration insights** — image-size, CVE, and filesystem deltas.

---

## Option B — no access? show the captured result (always works)

```bash
cd guardener/dockerfile
diff Dockerfile.legacy captured/Dockerfile.migrated.reference   # the before -> after
cat captured/transcript.md                                       # the analyze->build->fix->done loop
```

The transcript is the narration: Guardener didn't find-and-replace, it **built, hit a real
failure (`python3` vs `python` on distroless), and fixed it** — then verified the container
serves on `:8080`. That's the afternoon-per-service it saves.

---

## Where it fits the story

| | How you get to Chainguard Images |
|---|---|
| **Python / Java tracks** | base-image swap **by hand** — one `FROM` line, fine for one service |
| **Guardener (this)** | the **same** swap, **automated + built-and-verified**, for a fleet of Dockerfiles |

Guardener is the on-ramp at scale; **Libraries** (Act 3 of each track) and the
**provenance/SBOM** section are what you get once you're on Chainguard. The migrated image is
the same minimal, nonroot, distroless result as `../../python/Dockerfile.2-image` — scan it
with `grype` and you see the identical OS-CVE collapse, produced for you instead of by hand.
