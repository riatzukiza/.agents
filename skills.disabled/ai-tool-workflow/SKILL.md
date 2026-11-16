---
description: "Guide AI mode selection and workflow optimization across chat, agent, research, and canvas modes. Use when the user asks about agent vs research mode, tool selection, or optimizing work across AI interaction modes."
---

---
description: "Guide AI mode selection and workflow optimization across chat, agent, research, and canvas modes. Use when the user asks about agent vs research mode, tool selection, or optimizing work across AI interaction modes."
---

# AI Tool Workflow

## Purpose

When the user asks about AI mode selection, workflow optimization, or tool selection, provide guidance on which AI mode to use for which task.

## Trigger

- "which AI mode should I use"
- "agent vs research mode"
- "how to use AI for code review"
- "AI workflow optimization"

---

## Mode Selection

| Mode | Best For |
|------|----------|
| **Normal chat (extended thinking)** | Designing approaches, exploring tradeoffs, writing specs, whiteboard thinking |
| **Agent mode** | Repo/PR review, multi-step tasks, systematic passes, "go do N things" |
| **Research mode** | Citations, "what do sources say", writing for humans, blog posts |
| **Canvas** | Long-form documents, single source of truth drafts, collaboration |

---

## When Agent Mode is Better

- You can provide a **bounded corpus**: PR diff, changed files, repo snapshot
- You want a **systematic pass**: architecture issues, test gaps, security footguns
- You want output in a repeatable format: checklist + severity + file/line pointers
- You want to "go do N things and come back with a report"

## When Extended Thinking is Better

- You're **exploring** without knowing the endpoint
- You want to **iterate quickly** without tool overhead
- You need to **design** first, then execute
- The corpus is **too large** to paste

---

## Three-Lane Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    DISCOVERY LANE                             │
│  research/chat → capture notes → identify intent             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      SPEC LANE                                │
│  chat/agent → write spec/specs → define constraints          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     EXECUTE LANE                              │
│  agent → implement → verify → report                         │
└─────────────────────────────────────────────────────────────┘
```

### Lane Guidelines

**Discovery Lane**:
- Use research mode for citations
- Use extended thinking for exploration
- Capture notes in timestamped files
- Don't jump to implementation

**Spec Lane**:
- Use extended thinking for spec design
- Use agent mode if you need systematic passes
- Define constraints, not just implementation
- Write tests first

**Execute Lane**:
- Use agent mode for multi-step tasks
- Use extended thinking for tricky implementations
- Verify each step
- Generate report at the end

---

## PR/Repo Review Protocol

### Agent Mode Review Checklist

```
1. Architecture Issues
   - Circular dependencies
   - Layering violations
   - Missing abstractions

2. Test Gaps
   - Missing edge cases
   - Uncovered paths
   - Integration test needs

3. Security Footguns
   - Input validation
   - Auth/authz issues
   - Data exposure

4. Style Consistency
   - Naming conventions
   - Error handling
   - Documentation

5. Production Readiness
   - Error handling
   - Observability
   - Graceful degradation
```

### Extended Thinking Review

Better when:
- You're exploring the codebase
- You don't have a bounded diff
- You want architectural advice
- You need design guidance

---

## Anti-Patterns to Avoid

- **Premature agent**: Starting agent mode before understanding the problem
- **Research without scope**: Using research mode without a clear question
- **Canvas for iteration**: Multiple small iterations in Canvas instead of chat
- **Agent without context**: Agent mode without providing repo/files

---

## Example Usage

```
User: "I need to review this PR"

Agent:
1. Ask for PR diff or changed files
2. Recommend agent mode if bounded corpus
3. Suggest extended thinking if architectural

User: "I need to design a new system"

Agent:
1. Recommend extended thinking for exploration
2. Use spec lane: write spec, define constraints
3. Then switch to agent for implementation

User: "I need citations for a blog post"

Agent:
1. Recommend research mode
2. Provide sources with citations
3. Help structure the argument
```