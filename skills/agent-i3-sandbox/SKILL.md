---
name: agent-i3-sandbox
description: "Test i3, Emacs, and espanso configurations in a disposable non-root Xvfb Docker sandbox without accessing the host desktop."
license: GPL-3.0-or-later
compatibility: "Linux host with Docker Compose; core image is linux/amd64 because Espanso 2.1.4-beta X11 is pinned for that architecture."
metadata:
  provenance: "Adapted from riatzukiza/spacemacs.d at ff76ccd6a8e1afca372a8e523bcdb5914fa7de4b: .agents/skills/agent-i3-sandbox and layers/agent-sandbox."
---

# Skill: agent-i3-sandbox

## Goal

Run isolated i3, graphical Emacs, and Espanso checks inside an Xvfb
display. The sandbox never mounts host X11, DBus, desktop sockets, or live
configuration paths by default.

## Use This Skill When

- validating an i3 configuration or a keybinding without reloading the host WM;
- reproducing an Emacs/X11 focus or text-injection behavior;
- testing an Espanso match, especially an ASCII-versus-Unicode expansion;
- collecting i3, Emacs, Espanso, selection, and timing logs from a disposable container.

## Do Not Use This Skill When

- the task requires a host desktop action, host daemon reload, or a live Espanso change;
- the host is not Linux/amd64; the pinned Espanso X11 package deliberately fails on another architecture;
- a result from the container is being presented as proof about a host-specific input method or clipboard manager.

## Quick start

```bash
cd skills/agent-i3-sandbox
./scripts/agentctl up core
./scripts/agentctl smoke core
./scripts/agentctl i3-regression core
./scripts/agentctl espanso-regression core
./scripts/agentctl snapshot core
./scripts/agentctl artifacts core /tmp/desktop-evidence-new
./scripts/agentctl logs core
./scripts/agentctl down core
```

`agentctl` scopes Compose resources through `SANDBOX_PROJECT` (default:
`agent-i3-sandbox`), so it does not claim global container names or images.
Artifacts are written inside the container at `/workspace/.artifacts` and can
be exported with `agentctl artifacts` to a new directory before teardown.

## Optional configuration inputs

The core image uses bundled minimal i3, Emacs, and Espanso fixtures. To test a
candidate file, copy it into a disposable checkout or use the explicit,
read-only override file:

```bash
SANDBOX_I3_CONFIG=/absolute/path/to/candidate-i3.conf \
  docker compose -f docker-compose.yml -f docker-compose.optional.yml run --rm core
```

`SANDBOX_I3_CONFIG` is required by the optional file and is mounted read-only.
For an Espanso config-directory snapshot (containing `config/` and `match/`):

```bash
SANDBOX_ESPANSO_CONFIG=/absolute/path/to/candidate-espanso \
  docker compose -f docker-compose.yml -f docker-compose.espanso.yml up --build --wait core
```

The read-only input is copied into the container's writable HOME before daemon
startup. Use a separate Compose project for each experiment. Bundled regression
commands require bundled triggers; for candidate matches, adapt a disposable
fixture and assert its exact output rather than expecting fixture triggers.

Do not point it at a live configuration and do not expect this mount to reload
anything on the host. The optional `spacemacs` image contains a pinned upstream
checkout and the bundled minimal private layer; it has no host-layer mount.
Full Spacemacs package initialization currently requires online ELPA bootstrap
and is not verified. Core tests prove graphical Emacs, not a fully bootstrapped
Spacemacs environment. Do not report its core daemon smoke as Spacemacs parity.

## Espanso fidelity note

The core image pins Espanso X11 `2.1.4-beta` from its upstream Debian artifact,
verifies the recorded SHA-256, and invokes only commands observed from
`espanso --help` (including its documented `espanso daemon` subcommand) during
image build and smoke testing. The daemon runs in the container's isolated DBus
session; it does not register or use a host service. No fictional
`espanso check` or `espanso compile` command is used.

Its Unicode regression is intentionally diagnostic. On normal Linux keyboard
layouts, forced key-injection expansion of `μ⟲` errors with missing virtual-key
mappings; this is tested as an expected failure. The automatic match test also
records clipboard/selection timing because this Espanso version may choose its
clipboard path for non-ASCII text. OS/APT packages are not digest-pinned, so
this is not a bit-reproducible distribution. It does **not** claim that timing
proves a causal owner of a host freeze.

## Installation

Installation is opt-in and never changes services, package caches, or desktop
configuration:

```bash
./scripts/install.sh --dest /tmp/agent-i3-sandbox-copy       # dry run
./scripts/install.sh --apply --dest "$HOME/.agents/skills/agent-i3-sandbox"
```

The installer rejects an existing unowned directory or any symlink. A directory
previously installed by this script contains `.agent-i3-sandbox-managed` and
is verified against its manifest. An identical reinstall is idempotent; locally
modified or different-version installs are refused. Choose a new destination
for a new version. Extra user files are preserved. This installs the skill only;
it does not install Docker, Espanso, Emacs, or any live desktop configuration.

## Provenance

This extraction preserves the intent of the Spacemacs-local source while
removing its device paths, fixed Compose identities, missing-init dependency,
and host configuration mounts. The source was inspected read-only.
See [NOTICE.md](NOTICE.md) for source revisions, adaptation scope, and dependency
licenses. The original Spacemacs-local checkout remains a compatibility copy;
this catalog bundle is the reusable implementation.
