# Fork Tax — 2026-08-13T15:31:09Z

## Base
- Branch: `main`
- Commit: `00a05c8`

## Staged (owned)
- `skills/harness-handoff/` — new skill (SKILL.md + scripts/)
- `skills/i3-devops/` — new skill (SKILL.md)
- `skills/model-routing/` — new skill (SKILL.md)
- `skills/tailscale-nfs-mesh/` — new skill (SKILL.md + CONTRACT.edn + scripts/)

## Blocked (needs review)
- `PRINCIPLE.edn` — Modified by chatgpt/github-connector session. Scope changed `:global` → `:chatgpt`. Significant content stripped (skill-repository grammar, uncertainty operators, harness-boundary rules). **Do not commit without review.**

## Concurrent dirt (not absorbed)
- `skills.disabled/` — benched skills directory, untracked
- `skills.backup-20260319T002831Z/` — old backup
- `pi.skills` — broken symlink to `/home/err/.pi/agent/skills`

## Excluded (build artifacts)
- `skills/webhook-fullstack/.clj-kondo/`
- `skills/webhook-fullstack/.cpcache/`
- `skills/webhook-fullstack/.lsp/`
- `skills/webhook-fullstack/.shadow-cljs/`

## Verification
- `git status` inspected: pass
- New skill directories contain SKILL.md: pass
- No secrets detected in staged paths: pass
