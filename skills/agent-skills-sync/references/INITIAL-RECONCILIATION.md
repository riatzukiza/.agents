# Initial Stealth/Yoga reconciliation — 2026-10-26

The user authorized the same active skill catalog on both machines while
preserving parked copies, conflicts, machine configuration and device branches.

Before this change, distribution consisted of independent Git device branches
and scoped manual copies. Yoga's fork-tax coordinator is a separate repository
persistence job; it did not establish an automatic shared skill catalog.

Five common files had different contents. Original versions were archived on
both hosts under `~/.local/state/agents-sync/initial-<host>-<UTC>.tar`.
The initial selections were explicit:

- `model-routing/SKILL.md`: Stealth's current bindings and no automatic provider
  substitution; Yoga's dated 2026-08-13 availability claims were preserved in backup.
- `opencode-models-providers/SKILL.md`: Stealth's recovered anonymous MiMo reference;
  the supporting `docs/notes` file is now shared as well.
- `session-mycology/SKILL.md`: Stealth's existing current working-file version,
  preserved as a synchronization choice, not a new qualification of its runtime.
- `receipt-river/{SKILL.md,CONTRACT.edn}`: Yoga's existing 0.2.2 active guidance.
  Its recorded source is `docs/segmented-receipt-ledger` at `7f4dfb8`, from
  Yoga `.ημ/skill-sync/receipt-river-20261024.json`. Copying it does not migrate
  either repository ledger or establish acceptance of an upstream proposal.

Unique active skills were propagated in both directions. Yoga's parked catalog
was copied as parked content; it was not enabled. Root machine instructions,
ledger state, Git history, runtime and credentials remain separate. Concurrent
local root/handoff edits were preserved; file synchronization is not code review.

Verification used the actual installed Unison profile and systemd service:
Stealth addition -> Yoga; Yoga edit -> Stealth; distinct simultaneous edits
retained with service failure; remote and local deletion each blocked; probe
removed on both hosts; successful final service run. A hash/link/executable-bit
comparison matched the shared catalog (142 active, 124 parked skills).

The timer runs only on Stealth, every two minutes plus up to ten seconds of
jitter. Both hosts must be reachable. No automatic commit, push, text-conflict
resolution, token copying or automatic deletion is configured.
