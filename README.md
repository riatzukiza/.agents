# `~/.agents`

A canonical, versioned operating substrate for agent work across local harnesses, cloud sandboxes, connected tools, and project checkouts.

This is not a bag of prompts. It contains:

- a global intent contract,
- a process constitution,
- a construction kernel,
- harness-specific projections,
- reusable skills with explicit activation gates,
- machine-readable contracts,
- scripts and references,
- append-only execution receipts,
- and a mycology loop that turns recurring friction into reviewed skills.

## Start here

| File | Purpose |
|---|---|
| [`PRINCIPLE.edn`](PRINCIPLE.edn) | Mission, directives, operators, uncertainty grammar, output contract, safety, licensing, and skill-system invariants |
| [`AGENTS.md`](AGENTS.md) | Short universal map for agents working in this repository |
| [`PROCESS.md`](PROCESS.md) | Claims, evidence, authority, acceptance, process data, capability adaptation, and bootstrap |
| [`STYLE.md`](STYLE.md) | Intent-to-system construction kernel, source/interpreter/projection boundaries, and verification |
| [`docs/WEAVE.md`](docs/WEAVE.md) | Cross-repository synthesis and source-role map |

Harness adapters:

- [`CHATGPT.md`](CHATGPT.md)
- [`CLAUDE.md`](CLAUDE.md)
- [`CODEX.md`](CODEX.md)
- [`OPENCODE.md`](OPENCODE.md)
- [`PERPLEXITY.md`](PERPLEXITY.md)

Each adapter starts from observed capabilities rather than product-name assumptions.

## Core operating stack

| Skill | Purpose |
|---|---|
| [`environment-classifier`](skills/environment-classifier/SKILL.md) | Determine what the current harness can inspect, mutate, execute, persist, and verify |
| [`process-bootstrap`](skills/process-bootstrap/SKILL.md) | Add the smallest useful process layer without requiring eta-mu or any harness |
| [`receipt-river`](skills/receipt-river/SKILL.md) | Externalize execution truth into an append-only ledger |
| [`session-mycology`](skills/session-mycology/SKILL.md) | Turn recurring friction into reviewable skill spores |
| [`eta-mu-kanban`](skills/eta-mu-kanban/SKILL.md) | Operate Rheos/eta-mu boards lawfully and migrate legacy ledgers toward `.ημ/` |
| [`fork-tax`](skills/fork-tax/SKILL.md) | Produce a deterministic commit/tag/push handoff when Π is explicitly invoked |
| [`grok-intention`](skills/grok-intention/SKILL.md) | Recover compressed intent from prompts, notes, history, and repository context |
| [`sing-the-songs-of-your-people`](skills/sing-the-songs-of-your-people/SKILL.md) | Produce truthful synthesis in the native voice of the corpus |
| [`skill-authoring`](skills/skill-authoring/SKILL.md) | Create and revise scoped reusable skills |

These are substrate skills, not a mandatory prompt bundle. Explicit invocation wins; otherwise load the smallest relevant set.

## Skill shape

```text
skills/<name>/
├── SKILL.md       # operational instructions and activation gates
├── CONTRACT.edn   # optional machine-readable activation/governance contract
├── scripts/       # optional harness-neutral helpers
└── references/    # optional supporting material
```

`SKILL.md` is the human-operational source. `CONTRACT.edn` exists when activation, governance, scoring, effects, or non-override behavior needs a machine-readable form.

Imported skills may have additional files. Preserve their provenance and upstream license metadata.

## Discovery

For substantive repository work:

1. active safety, harness, and user instructions,
2. applicable `AGENTS.md` / override files,
3. project constitution, decisions, process, and style,
4. project-local skills,
5. the global catalog at `~/.agents/skills`,
6. the selected skill's references and scripts.

Do not ingest the entire catalog. Search by explicit skill, task shape, trigger, referenced path, or repository guidance.

OpenCode currently discovers global and project `.agents/skills` directly. Other harnesses may use their own skill locations or adapters; this repository remains canonical.

## Process data

The preferred project-local topology is:

```text
<project>/
├── AGENTS.md
├── project source, decisions, designs, and tasks
├── .agents/skills/            # project-local skills when needed
└── .ημ/
    ├── receipts.edn
    ├── environment/
    ├── ledgers/
    ├── runs/
    ├── session-mycology/
    ├── config/
    ├── plugins/
    └── projections/
```

This is a semantic target, not a demand to create empty directories.

Legacy `.eta-mu/`, root `receipts.edn`, `kanban/.events/ledger.edn`, task comments, and tool-specific state are preserved evidence. Migrations must inventory, copy/replay, compare, cut over, and retain rollback.

## No-tool baseline

A repository can respect the process with only plain files and Git:

- short project instructions,
- explicit source/projection authority,
- append-only receipts,
- bounded tasks or plans,
- relevant verification records,
- and mycology observations.

Rheos, eta-mu, Muse, Katamorph, event-ledger, hooks, daemons, databases, MCP, and connectors are optional strengthening layers.

Use `process-bootstrap` to select:

```text
inventory
  -> file-only substrate
  -> helper scripts
  -> Rheos/eta-mu
  -> Muse harness adapters
  -> runtime services
```

Each layer remains intelligible without the next.

## Learning

```text
work
  -> Receipt River evidence
  -> Session Mycology score
  -> optional project-local spore
  -> later review
  -> promoted or revised skill
```

A spore is never automatically promoted in the same session that created it. Direct user requests to author a skill are explicit skill work and retain their own provenance.

## Local installation

```bash
git clone https://github.com/riatzukiza/.agents.git ~/.agents
```

Harnesses that discover `~/.agents/skills` can read it directly. For others, configure their documented skill location to reference this catalog rather than maintaining divergent copies.

Example:

```bash
ln -sfn ~/.agents/skills <harness-skill-directory>
```

Do not independently edit copied trees. Host-specific files are projections of this corpus.

## Working on this repository

1. read `PRINCIPLE.edn`, `PROCESS.md`, `STYLE.md`, and `AGENTS.md`,
2. classify the environment,
3. inspect receipts and relevant skills,
4. search for overlap and historical context,
5. make the smallest compatible change,
6. verify with capabilities actually present,
7. append receipts and mycology state,
8. use a branch and pull request for governance-wide changes.

Π / Fork Tax is stronger than ordinary persistence. Do not create tags or full handoff manifests unless explicitly invoked.

## License

Under the global contract:

- libraries are GNU LGPL v3 or later,
- services and standalone applications are GNU GPL v3 or later.

Imported skills may retain their upstream license.
