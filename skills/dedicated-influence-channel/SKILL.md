---
name: dedicated-influence-channel
description: Add a new conserved-quantity flow to an ECS without violating single-writer or barrier invariants.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: ecs-influence-extension
  project: gates-of-truth
  discoverable-by:
    - opencode
    - eta-mu
    - claude
  version: 1
---

# Skill: Dedicated Influence Channel

## Goal
Safely extend an ECS influence system with a new mass, energy, or quantity flow by giving it a dedicated component, a single-writer system, and a registered influence channel.

## Use This Skill When
- You need to add a new source or sink for a conserved quantity inside the ECS tick pipeline.
- The existing influence channels do not cleanly model the new flow.
- You want to avoid single-writer violations or post-fold mutation.

## Do Not Use This Skill When
- The flow can reuse an existing channel without changing its semantics.
- The change is a one-off script, debug probe, or lifecycle effect outside the tick pipeline.
- The ECS has no influence registry or accumulator mechanism to fold contributions.

## Steps
1. Define a new component `c/<flux-name>` in the components namespace.
2. Create one system that owns writes to that component; declare every `:read` and `:write` in the system registry.
3. Register the component in the integrator `influence-registry` under the appropriate accumulate (e.g., `:mass`).
4. Emit the influence only from its owner system.
5. Materialize transient effects via `spawn-request.*` or equivalent lifecycle markers when needed.
6. Add a test that exercises the new channel end-to-end and checks conservation.

## Anti-patterns
- Reusing an existing mass-flux channel for a different physical meaning.
- Mutating the quantity after the integrator fold.
- Letting two systems write the same component.
- Adding a new channel without a test that asserts conservation.

## Output
- A new component, system, and registry entry.
- A passing test that shows the new flow is integrated and conserved.
