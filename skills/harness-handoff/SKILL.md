---
name: harness-handoff
description: "Continue work across OpenCode, Codex, and Claude Code and survive repo moves: append/read a repo-local .handoff.edn ledger and relocate harness state with handoff.bb."
---

# Skill: Harness Handoff

## Goal
Give a cold agent in any of the three harnesses (OpenCode, Codex CLI, Claude Code) a durable, path-independent record of what has been done on this repo, and migrate per-harness state when the repo directory moves.

## Use This Skill When
- Starting work in a repo where prior work may have happened in another harness.
- About to switch harness mid-task.
- After moving/renaming a repo directory.

## Do Not Use This Skill When
- Trivial one-shot tasks with no continuation value.

## Why This Exists
All three harnesses treat the absolute cwd as identity. Moving a repo orphans OpenCode's project rows (`opencode.db` `project.worktree`, `session.directory`, `session.path`), Codex's `~/.codex/config.toml` `[projects."<abs path>"]` trust entries (rollouts in `~/.codex/sessions/**` survive), and Claude Code's `~/.claude/projects/<cwd-with-dashes>/` dirs including the per-project `memory/` subdir. No harness can import another's transcript — the viable handoff is a repo-local ledger with per-harness session-id pointers.

## The Record Format

`.handoff.edn` at the repo root. Append-only, one EDN map per line:

```edn
{:handoff/v 1
 :ts "2026-08-13T12:00:00Z"
 :harness :opencode                 ; or :codex | :claude
 :session-id "ses_abc123"
 :repo {:origin "git@github.com:org/repo.git" :branch "main" :head "a1b2c3d"}
 :task "what is being done"
 :state :in-progress                ; or :blocked | :done
 :summary "2-4 sentences a cold agent can act on"
 :next "single next action"
 :artifacts ["src/foo.clj"]
 :notes "optional"}
```

Rules:
- Entries are keyed by git origin, never by absolute path. Paths rot; origins travel with clones.
- Entries are claims by the writing harness, not gospel — verify against the repo before trusting them.
- Never write secrets, tokens, or credentials into `.handoff.edn`. It is deliberately git-tracked so it travels with clones.

## The Workflow

1. **At task start**: run `bb ~/.agents/skills/harness-handoff/scripts/handoff.bb read` (or tail `.handoff.edn`) and ground yourself in the latest entries before planning. If there is no ledger but the sidecar index knows this origin at another path, `read` tells you the repo was moved.
2. **At meaningful checkpoints, and BEFORE switching away**, append:
   ```
   bb ~/.agents/skills/harness-handoff/scripts/handoff.bb record \
     --harness opencode --session-id ses_abc \
     --task "refactor auth middleware" --state :in-progress \
     --summary "Split auth.clj into token + session namespaces; tests green except refresh-token expiry." \
     --next "fix refresh-token expiry test in test/auth/token_test.clj"
   ```
3. **After moving a repo**: from the new path run `handoff.bb relocate --from /old/abs/path --to /new/abs/path` to migrate harness state (see below).
4. **Commit `.handoff.edn` with the work.** It belongs to the repo.

## Finding Your Session ID

- **opencode**: `sqlite3 ~/.local/share/opencode/opencode.db "select id from session order by time_created desc limit 1;"` (or the session slug from the TUI).
- **codex**: the UUID in the latest `~/.codex/sessions/YYYY/MM/DD/rollout-*.jsonl` filename.
- **claude**: the `.jsonl` filename (sans extension) under `~/.claude/projects/<cwd-encoded>/`.

## Relocate Semantics and Safety Rails

`relocate --from OLD --to NEW` performs, logging each step and treating each failure as non-fatal (report and continue):

1. **Claude**: `mv ~/.claude/projects/<OLD-encoded> ~/.claude/projects/<NEW-encoded>` (preserves `memory/`, makes `--resume` work at the new path). Skipped if the target already exists.
2. **Codex**: rewrite the `[projects."OLD"]` table header in `~/.codex/config.toml` to NEW; backs up to `config.toml.bak-<timestamp>` first. Reports if there is no entry.
3. **OpenCode**: REFUSES to touch the db if any opencode process is running (`pgrep -f opencode`). Otherwise copies `~/.local/share/opencode/opencode.db` to `opencode.db.bak-<timestamp>` and updates `project.worktree` plus `session.directory`/`session.path` for sessions of the moved project. Prints row counts.
4. Updates the sidecar index (`~/.local/share/handoff/<sha256-of-origin>.edn`) with the new path.
5. Prints a summary table of what moved and what still needs manual attention. Internal cwd fields inside old transcript/rollout files are historical records — do NOT rewrite them.

## Subcommands

| Command | Purpose |
|---|---|
| `record --harness H --session-id ID --task T --state S --summary S --next N [--artifacts a,b] [--notes x]` | Append an entry to `.handoff.edn` and the sidecar index |
| `read [--limit N]` | Print the last N entries for the repo containing cwd; detect moves via sidecar |
| `locate` | Print all known past paths and last sessions for this repo's origin |
| `relocate --from OLD --to NEW` | Migrate harness state after a repo move |

## Cross-References

- **receipt-river**: per-repo execution receipts. Complementary — handoff is cross-harness continuity; receipts are execution audit.
- **session-mycology**: end-of-turn retrospectives and skill spores.
