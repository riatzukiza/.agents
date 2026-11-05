# Provenance and runtime fidelity

Source: [riatzukiza/spacemacs.d](https://github.com/riatzukiza/spacemacs.d),
revision `ff76ccd6a8e1afca372a8e523bcdb5914fa7de4b`, paths
`.agents/skills/agent-i3-sandbox/` and `layers/agent-sandbox/`.
The portable implementation adapts the source architecture and private-layer
helpers; it repairs device-relative paths and shared Compose names, replaces
host-specific shell/Helm probes with self-contained i3 and Espanso tests, and
adds a managed installer. Original device-specific development targets remain
in the source repository. This executable toolkit is GPL-3.0-or-later under
the catalog's global license contract.

Runtime dependencies retain their own upstream licenses:

- [Espanso 2.1.4-beta](https://github.com/espanso/espanso/tree/v2.1.4-beta):
  GPL-3.0; upstream Linux/amd64 X11 Debian artifact. SHA-256 recorded from the
  downloaded artifact: `55b1977ccd77d2cc46870af181c41a3650169058daff7538601572acf7befdb2`.
  This digest is an integrity pin, not an independently signed upstream attestation.
- [Spacemacs](https://github.com/syl20bnr/spacemacs), optional image:
  GPL-3.0-or-later; revision pinned in `Dockerfile.spacemacs`.
- GNU Emacs: GPL-3.0-or-later; i3: BSD-3-Clause; Xvfb/Xorg and xdotool:
  upstream X11/MIT-family licenses. Installed package license texts reside
  under `/usr/share/doc` in the container.

The Ubuntu/APT layer is mutable and currently supplies i3 4.17.1 and Emacs 26.3.
These versions differ from the operator's live desktop. Container results prove
the bundled fixture behavior, not host-specific Spacemacs, XKB, Chromium, or
clipboard-manager behavior. A future separately packaged distribution should
add a supported version matrix and immutable image/package inputs.
