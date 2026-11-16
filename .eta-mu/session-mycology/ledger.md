## 2026-10-26 agent-ssh-identity mesh turn

- efficiency: medium; recon-first recovered the knoxx pattern cleanly, but two slips: scp clobbered yoga's ssh_config (reconstructed from local backup), and stray if-False fragments leaked into three generated scripts before cleanup.
- friction: Tailscale SSH silently replaces OpenSSH sessions on 100.x addresses and PAM never sets loginuid (4294967295) — cross-device agent SSH must use LAN addresses; attribution must be asserted per hop.
- skill-candidate: p≈0.55 — the hazard is already captured verbatim in agent-ssh-identity SKILL.md/VERIFICATION.md where it is used; a separate spore would be redundant.
