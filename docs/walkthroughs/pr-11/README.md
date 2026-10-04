# PR #11 author walkthrough evidence

Subject: https://github.com/riatzukiza/.agents/pull/11

Reviewed code: `8a173d2438b787675f1faf54ba78f7000beb098b`.
Merge: `9812965f071a07fe2e79c9b2c2656bcfacb8e8ce`.

Screenshots are fresh post-merge evidence, not captures of the original CI run.
Raw capture files are preserved alongside annotated copies. Overlays
intentionally modify the copies only; the separate raw files remain unchanged.

The evidence branch is separate from the merged implementation. No live host
desktop, host clipboard, or user Emacs session is used to produce runtime images.

## Annotated screenshots

1. [Merged PR and fidelity boundaries](annotated/01-pr-merged.png)
2. [Actual hosted core CI job](annotated/02-pr-checks.png)
3. [Fresh sandbox Unicode result and forced-key diagnostic](annotated/03-unicode-runtime.png)
4. [Fresh installer refusal test output, displayed in sandbox Emacs](annotated/04-installer-tests.png)

The public GitHub pages were captured by a fresh headless Chrome session, not
the operator's browser profile. Runtime captures use a non-root container with
no mounts and its own `DISPLAY=:99`. `capture.Dockerfile` adds ImageMagick only
for capture, so this is a capture-enabled derivative, not an unchanged stock
image. The three key runtime scripts' SHA-256 hashes were compared with the
merged source and matched. `manifest.json` records capture-image identity,
reviewed revisions, image hashes, dimensions and annotation text.

The Unicode screenshot shows a fresh automatic expansion in the actual editor
buffer above the forced-key failure log retained from the immediately preceding
regression run in that same container. It does not claim both modes ran
simultaneously, nor identify the causal selection owner of the original host
freeze. The installer screenshot displays the actual complete stdout/stderr of
a freshly executed temporary-directory test; it is not a fabricated console.

## Capture procedure

```bash
# From this evidence worktree, first build the subject's core image.
SANDBOX_PROJECT=pr11-evidence bash skills/agent-i3-sandbox/scripts/agentctl up core
docker build --build-arg BASE_IMAGE=pr11-evidence-core:latest \
  -f docs/walkthroughs/pr-11/capture.Dockerfile \
  -t pr11-walkthrough-capture:latest docs/walkthroughs/pr-11
docker run --detach --name pr11-walkthrough-capture pr11-walkthrough-capture:latest
docker exec pr11-walkthrough-capture /home/agent/bin/smoke-i3
docker exec pr11-walkthrough-capture /home/agent/bin/test-i3-regression
docker exec pr11-walkthrough-capture /home/agent/bin/test-espanso-regression
bash skills/agent-i3-sandbox/tests/test-installer.sh > docs/walkthroughs/pr-11/installer-tests.log 2>&1
docker cp docs/walkthroughs/pr-11/installer-tests.log pr11-walkthrough-capture:/workspace/installer-tests.log
docker cp docs/walkthroughs/pr-11/capture-runtime.sh pr11-walkthrough-capture:/workspace/capture-runtime.sh
docker exec pr11-walkthrough-capture bash /workspace/capture-runtime.sh
docker cp pr11-walkthrough-capture:/workspace/.artifacts/03-unicode-runtime.png docs/walkthroughs/pr-11/raw/
docker cp pr11-walkthrough-capture:/workspace/.artifacts/04-installer-tests.png docs/walkthroughs/pr-11/raw/
python3 docs/walkthroughs/pr-11/annotate.py
docker stop pr11-walkthrough-capture
docker rm pr11-walkthrough-capture
SANDBOX_PROJECT=pr11-evidence bash skills/agent-i3-sandbox/scripts/agentctl down core
```

All screenshots were visually checked before publication. No privileged
runtime, host X11 socket, live clipboard, host Emacs socket or user config mount
was used. The runtime image/container details were inspected: user `agent:agent`,
mounts `[]`.

## Self-review findings and boundaries

- Accepted before merge: mutable Ubuntu/APT packages; old i3/Emacs versions chosen
  for the pinned Espanso reproduction; Linux/amd64-only binary; recorded digest
  rather than independently signed provenance; unqualified optional full
  Spacemacs bootstrap; original donor compatibility copy retained; no in-place
  managed install upgrade mechanism. Full parity/extraction follow-up: [#12](https://github.com/riatzukiza/.agents/issues/12).
- Residual limitations surfaced by this walkthrough: installer ancestor checks
  are not atomic against a hostile actor replacing directories; read-only bind
  inputs can still change on the host during the copy; exact output ordering and
  fixed sleeps are version/timing-sensitive. These are limitations, not claimed
  reproductions of an exploit or of the original clipboard freeze.
- Newly reproduced: installed `tests/test-static.sh` requires the surrounding
  catalog's workflow path and therefore is not fully standalone. The installer
  and runtime remain usable, but installed static validation fails. Tracked in
  [#13](https://github.com/riatzukiza/.agents/issues/13); not retrospectively
  described as knowingly accepted pre-merge debt.

Software/process documentation: GPL-3.0-or-later. Captured upstream interfaces
retain their original ownership; annotations are authored for this walkthrough.
