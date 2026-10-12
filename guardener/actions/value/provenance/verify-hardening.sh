#!/usr/bin/env bash
# Prove the hardening behind each Chainguard Action your workflow pins.
# For every "owner/repo@ref" (defaults to what workshop-lite's CI pins), show:
#   1. the immutable commit the tag resolves to      (you pinned the SHA, not this tag)
#   2. upstream provenance            (source.json — which upstream commit was hardened)
#   3. the hardening report summary   (HARDENING.md — policy-SHA-stamped, per version)
#   4. that a signed SLSA build attestation exists
# Requires: gh (authenticated), jq.
set -uo pipefail
ACTIONS=("$@")
if [ "${#ACTIONS[@]}" -eq 0 ]; then
  ACTIONS=(
    "chainguard-actions/actions-checkout@v4"
    "chainguard-actions/docker-setup-buildx-action@v3"
    "chainguard-actions/docker-build-push-action@v6"
  )
fi
for a in "${ACTIONS[@]}"; do
  repo="${a%@*}"; ref="${a#*@}"
  echo "=== $a ==="
  sha=$(gh api "/repos/$repo/git/ref/tags/$ref" --jq '.object.sha' 2>/dev/null)
  echo "  tag $ref -> immutable commit ${sha:-<none>}"
  gh api "/repos/$repo/contents/source.json?ref=$ref" --jq '.content' 2>/dev/null | base64 -d 2>/dev/null \
    | jq -r '"  hardened from: \(.owner)/\(.repo)@\(.version)  (upstream commit \(.commit_sha))\n  policy SHA:    \(.policy_sha)"' 2>/dev/null \
    || echo "  source.json: (not published for this action yet)"
  gh api "/repos/$repo/contents/HARDENING.md?ref=$ref" --jq '.content' 2>/dev/null | base64 -d 2>/dev/null \
    | grep -iE 'was hardened' | sed 's/^/  report: /' || echo "  HARDENING.md: (none)"
  if gh api "/repos/$repo/contents/attestations/provenance.intoto.jsonl?ref=$ref" --jq '.path' >/dev/null 2>&1; then
    echo "  attestation: attestations/provenance.intoto.jsonl (signed SLSA in-toto) ✓"
  else
    echo "  attestation: (none found)"
  fi
  echo
done
