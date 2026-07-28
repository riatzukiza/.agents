# `~/.agents`

A canonical, versioned skill repository for agent work across local harnesses, connected tools, and project checkouts.

The repository is not a bag of prompts. It is an operating substrate:

- reusable skills with explicit activation gates,
- machine-readable contracts where automation needs them,
- scripts and references that travel with the skill,
- a global principle contract,
- append-only execution receipts,
- and a mycology loop that turns repeated friction into reviewed skills.

## Governing files

- [`PRINCIPLE.edn`](PRINCIPLE.edn) — the global intent contract: mission, directives, operators, uncertainty grammar, output shape, safety, licensing, and skill discovery.
- [`AGENTS.md`](AGENTS.md) — repository-specific instructions for maintaining this corpus.
- [`skills/`](skills/) — the canonical skill catalog.
- [`skills/.skill-lock.json`](skills/.skill-lock.json) — provenance and version metadata for imported skills.
- `.ημ/receipts.edn` — append-only evidence for substantive changes to this repository.
- `.ημ/session-mycology/` — project-local learning records and incubating skill spores.

## Core operating stack

| Skill | Purpose |
|---|---|
| [`receipt-river`](skills/receipt-river/SKILL.md) | Externalize execution state into an append-only ledger. |
| [`session-mycology`](skills/session-mycology/SKILL.md) | Turn recurring friction into reviewable skill spores. |
| [`fork-tax`](skills/fork-tax/SKILL.md) | Produce a deterministic commit, tag, push, and handoff snapshot when Π is invoked. |
| [`grok-intention`](skills/grok-intention/SKILL.md) | Recover compressed intent from prompts, notes, and repository context. |
| [`sing-the-songs-of-your-people`](skills/sing-the-songs-of-your-people/SKILL.md) | Produce truthful synthesis in the native voice of the corpus. |
| [`skill-authoring`](skills/skill-authoring/SKILL.md) | Create and revise scoped, reusable skills. |

These are substrate skills, not a mandatory bundle to load on every turn. Explicit invocation wins; otherwise the active agent should select the smallest relevant set.

## Skill shape

A native skill normally looks like this:

```text
skills/<name>/
├── SKILL.md       # operational instructions and activation gates
├── CONTRACT.edn   # optional machine-readable activation/governance contract
├── scripts/       # optional harness-neutral helpers
└── references/    # optional supporting material
```

`SKILL.md` is the human-operational source. `CONTRACT.edn` exists when activation, governance, scoring, or non-override behavior needs a machine-readable form.

Imported skills may have additional files. Preserve their provenance and do not silently present third-party material as native work.

## Discovery model

For substantive repository work, agents should resolve instructions in this order:

1. active harness and user instructions,
2. every applicable project `AGENTS.md`,
3. project-local skills,
4. the canonical global catalog at `~/.agents/skills`,
5. the selected skill's references and scripts.

Do not ingest the entire catalog. Search by task shape, explicit skill name, trigger phrase, referenced path, or repository guidance, then read only the relevant skills.

## Learning model

The repository learns through files, not implied memory:

```text
work
  -> append evidence with receipt-river
  -> score the turn with session-mycology
  -> incubate a project-local spore when the pattern generalizes
  -> review in a later session
  -> promote into skills/<name>/ with provenance
```

A spore is never promoted in the same session that created it. Recurrence and evidence earn promotion; enthusiasm alone does not.

## Local installation

Clone this repository at its canonical location:

```bash
git clone https://github.com/riatzukiza/.agents.git ~/.agents
```

Harnesses that already discover `~/.agents/skills` can read it directly. For other harnesses, link or configure **their documented skill directory** to `~/.agents/skills` while keeping this repository as the source of truth:

```bash
ln -sfn ~/.agents/skills <harness-skill-directory>
```

Do not duplicate and independently edit copied skill trees. Harness-specific adapters may specialize discovery or tool syntax, but should point back to this corpus.

## Working on this repository

Before changing a skill:

1. read [`PRINCIPLE.edn`](PRINCIPLE.edn) and [`AGENTS.md`](AGENTS.md),
2. inspect the relevant skill and contract,
3. tail `.ημ/receipts.edn` when present,
4. search for overlap before creating a new skill,
5. make the smallest compatible change,
6. verify the touched files using capabilities the active harness actually has,
7. append receipts and a mycology entry,
8. use a branch and pull request for governance-wide changes.

Π / Fork Tax is stronger than ordinary repository persistence. Do not create tags or full handoff manifests unless the user explicitly invokes it.

## Portability

The same corpus may be reached through a local checkout, symlink, coding-agent harness, GitHub connector, or another repository API. Tool boundaries must remain explicit:

- connector access is not a local shell,
- unavailable scripts were not run,
- repository API writes must preserve append-only records,
- host-specific assumptions belong in compatibility sections or adapters.

## License

Under the global contract:

- libraries are released under GNU LGPL v3 or later,
- services and standalone applications are released under GNU GPL v3 or later.

Individual imported skills may retain their upstream license metadata.
