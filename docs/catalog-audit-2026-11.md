# Skill catalog audit: November 2026

## Scope and measurement

Inventory against main at `6552c271ca7617d5178a81edc63e3847e532b08a`:
126 `skills/*/SKILL.md` files, 518,591 UTF-8 bytes of skill definitions,
and 25,012 UTF-8 bytes in their parsed names and descriptions. This is a
repository inventory, not a measurement of any running agent's prompt. After
moving the 13 candidates below, there are 113 definitions, 493,810 definition
bytes and 21,089 bytes of names/descriptions, before any other edits. Bytes are
not tokens. Other installed skill sources, permissions, versions and runtime
configuration affect actual context and discoverability.

[OpenCode skills](https://opencode.ai/docs/skills/) documents that permitted
skills are listed by name and description in the skill tool and the full body
is loaded when invoked. `~/.agents/skills` is one of several discovery roots.
[OpenCode rules](https://opencode.ai/docs/rules/) says project/global `AGENTS.md`
and configured instruction files enter context. The effective global
configuration, agent-specific prompt, other discovered roots and live token
usage were not available in this checkout, and must be measured on the user's
actual installation. The [v2 skills docs](https://opencode.ai/v2/docs/skills/)
describe additional source, permission and autoinvoke features; do not assume
v2 behavior on v1, or vice versa.

## Decision rule

Keep skills that have a clear reusable trigger, a concrete procedure and
verification, and evidence that they solve a recurring problem. Keep long or
conditional detail in references rather than the routinely advertised summary.
Use representative triggering and non-triggering tasks and observable outcomes
before declaring a skill high quality. These principles align with [Anthropic's
skill guidance](https://www.anthropic.com/research/equipping-agents-for-the-real-world-with-agent-skills)
and [agent evaluation guidance](https://www.anthropic.com/research/demystifying-evals-for-ai-agents).
Shorter text or recurrence metadata alone is not proof of quality.

## Moved out of active discovery

The 13 entries below all use the same generic promoted-spore template. Their
procedures mostly repeat the description, offer no concrete checks, and encode
one incident or an already-canonical rule. They are preserved byte-for-byte,
including contracts and metadata, in [archive/skills](../archive/skills/README.md).
This is a judgment about active catalog quality, not a claim that every lesson
is wrong or permanently useless.

- Project-specific incident: `cljs-policy-test-harness-before-route-order-deletion`, `fork-tax-inbox-docs`.
- One-incident technical notes: `docker-shared-network-alias-flap-triage`, `dt-dilated-player-constants`, `helix-plain-defn-vs-component-invocation-guard`, `shadow-cljs-esm-export-verification`, `submodule-monorepo-runtime-rehab`, `upstream-failure-first-triage`, `workspace-monorepo-rehome-with-pnpm-relink`, `opencode-batch-subagent-dispatch`.
- Redundant or potentially conflicting orchestration: `fork-tax-concurrent-agent-guardrails` (its core rule is already in `fork-tax`), `kanban-push-orchestration`, `kanban-ultra-wave-cadence` (both prescribe a review cadence rather than a general tested procedure).

Two similar templates remain pending dependency-aware review: `staging-promotion-gate-navigation` is referenced by `pr-flow`; `notes-to-specs-synthesis` is referenced by `repo-lore-archaeologist`. Keeping them is not a quality endorsement. This is a first cut, not a full behavioral evaluation of the 113 remaining skills.

## Compatibility repairs

`research-signal-scoring` had duplicated frontmatter and no `name`;
`cloudflare-tunnel-skills` had a name different from its directory. Both now
meet the portable name rule, and the Tailscale skill points to the corrected
Cloudflare skill ID. These are syntax and reference repairs, not behavioral
validation of either workflow.

The `skill-authoring` entry no longer requires a separate Pi location and
OpenCode symlink when the canonical `~/.agents/skills` directory is already
discovered. Confirm runtime configuration before adding another adapter.

## Follow-up priorities

- Check the running OpenCode version, global/project rule files, configured custom sources, agent prompts, skill permissions and duplicate IDs. Measure actual input tokens or serialized prompt before and after, on representative primary and subagent tasks.
- Audit overlapping OpenCode authoring skills. Several descriptions are only titles, and some refer to checkout-specific paths or wrappers. Consolidate only after checking users and testing actual current OpenCode configuration, agent, command, tool and plugin workflows.
- Reconcile the directly invokable PR settlement and merge skills with the selected PR profile. Native local work should not inherit hosted-provider round requirements; actual GitHub checks, review requirements, finding disposition and authorization remain binding. Do not equate a local review receipt with a GitHub approval.
- Preserve third-party provenance. Re-test representative positive/negative triggers and behavior across the models the user actually uses before further pruning or rewriting.
