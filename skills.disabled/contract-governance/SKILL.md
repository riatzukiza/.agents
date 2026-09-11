---
name: contract-governance
description: Meta-governance for contract amendments, versioning, rollback, and constitutional layer management.
---

# contract-governance

Meta-governance for contract amendments and evolution.

## Purpose

Defines how the contract itself evolves:
- Constitutional layers (immutable, semi-stable, mutable)
- Amendment proposals and workflow
- Contract versioning
- Rollback mechanism

## Activation

Priority 30, not autoloaded. Only invoked when:
- User requests amendment
- Contract change is proposed
- Governance questions arise

## Protocol

1. Proposals go to `specs/amendments/`
2. Agent can propose, but cannot auto-apply
3. User must approve semi-stable **contract amendments** (changes to this AI's operating rules)
4. Immutable sections cannot be touched

**Note**: User approval applies ONLY to modifying the AI behavioral contract itself. This is NOT about code review, PR approval, or development workflow. Solo developers have no external reviewer requirement — use Kanban `in_review` state for self-review only.