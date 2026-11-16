---
name: task-router
description: Evaluate a task/spec file, normalize metadata, compute valid next Kanban states, and recommend the best transition/work skill.
---

# Skill: Task Router

## Goal
Given a task file, determine its current state, normalize metadata, and recommend the best next transition.

Use the canonical FSM exposed by `packages/kanban` through `bin/eta-mu-board`, not legacy Promethean-only status lore.

## Use This Skill When
- You need to move a task through the Kanban FSM.
- You want a deterministic next-step recommendation.
- You need to interpret task status in terms of the canonical `packages/kanban` states.

## Do Not Use This Skill When
- There is no task file/spec context.

## Output
- current_state
- valid_next_states
- blockers/warnings
- recommended_transition
- recommended `bin/eta-mu-board` command when useful
