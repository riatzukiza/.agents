# claude context

Execution scope: Claude Code sessions and Claude Code-managed workers; Claude models inside OpenCode use opencode.

Read ../SKILL.md and ../identities.tsv. Your remote user is `claude`; your source-device alias is `knoxx-agent-claude`. Connect with `ssh knoxx-agent-claude` from stealth. Your private key path is `/home/err/.ssh/agent-identities/knoxx/claude`. Never read or print its contents.

Mesh (2026-10-26): the same account exists on stealth and yoga; use `ssh stealth-agent-claude` locally on stealth and `ssh yoga-agent-claude` on yoga; from any device `ssh <device>-agent-claude` (that device's own key, identities-stealth.tsv / identities-yoga.tsv). Cross-device and knoxx aliases use LAN addresses, never Tailscale 100.x (Tailscale SSH strips the loginuid).

Before work run `id; cat /proc/self/loginuid; sudo -n id` over that direct SSH login. Username must be claude; loginuid must equal the UID in the manifest, including in sudo descendants. Keep task and session IDs in receipts. All workers launched by this harness use claude; a separate harness must open its own authenticated SSH login. Do not borrow another identity when tools or credentials are unavailable. Existing local processes are not automatically migrated by this document.
