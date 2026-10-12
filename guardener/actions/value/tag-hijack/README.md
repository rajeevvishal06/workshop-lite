# Lever 1 — the tag-hijack threat (and why hardened + pinned is immune)

The single most important thing to understand about GitHub Actions: **every action you
`uses:` runs inside your job, with your `GITHUB_TOKEN` and any secrets you pass it.** So
the action's code *is* your supply chain — and a `@v4` / `@main` reference is a **mutable
pointer** the upstream (or whoever compromises it) can repoint to new code at any moment.

## This isn't hypothetical — `tj-actions/changed-files`, March 2025

**CVE-2025-30066.** An attacker gained write access to the popular `tj-actions/changed-files`
action and **moved its existing version tags** to a malicious commit. That commit dumped the
CI runner's memory — i.e. **secrets** — into the public build log. Any workflow pinned to a
mutable tag like `@v35` started running the malicious code on its next run, **with no change
on the victim's side**. It hit an estimated **~23,000 repositories**.

Two independent things would each have stopped it cold:
1. **Pinning to a commit SHA** instead of a tag — the moved tag can't touch a pinned SHA.
2. **Using a hardened, rebuilt-from-source action** whose exact commit is policy-audited and
   attested — so you also know *what's in* the code you pinned.

Chainguard's Hardened Actions give you **both at once** (and even publish a hardened
`chainguard-actions/tj-actions-changed-files`).

## See the mechanism yourself (safe simulation)

[`poisoned-action/`](poisoned-action/action.yml) is a local stand-in for a third-party
dependency. It does **nothing malicious** — it just prints what *any* action step can already
see (`GITHUB_TOKEN`, a `DEMO_SECRET`) and marks the spot where a hijacked action would
exfiltrate. The point is to make tangible that a dependency executes with your credentials.

- [`unpinned.yml`](unpinned.yml) — the ❌ vulnerable pattern: deps on **mutable tags**.
- [`pinned.yml`](pinned.yml) — the ✅ immune pattern: **SHA-pinned chainguard-actions**.

To run them live, copy both into `.github/workflows/` (and keep the `poisoned-action/`
path), optionally set a `DEMO_SECRET` repo secret, and trigger each from the Actions tab.
Both print the same marker — the difference is **what an attacker can change afterward**:
with `unpinned.yml`, a moved tag silently swaps the code; with `pinned.yml`, the SHA is
frozen and the hardening is attested.

> Why not a real cross-repo tag-move here? That needs a second repo you control plus tag
> force-pushes — noisy and risky in a workshop. The real `tj-actions` incident above *is*
> the live example; this local demo shows the **mechanism** (deps run with your secrets)
> safely. The fix is identical either way: pin the SHA, prefer the hardened action.

Related: [`../threat-model.md`](../threat-model.md) maps the full attack class;
[`../provenance/`](../provenance/) proves the hardening behind the pins.
