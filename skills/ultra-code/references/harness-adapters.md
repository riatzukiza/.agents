# Harness adapters

Verified documentation on 2026-11-03. These are selection notes, not a promise about an installed version. Inspect the current session's tools, agent definitions, permission behavior and capacity before dispatch.

| Host | Native route | Isolation and lifecycle |
| --- | --- | --- |
| OpenCode v2 | Use the documented `subagent` tool with a configured agent. `background: true` returns a handle and completion notification; `sessionID` continues the child. | Subagents start with fresh context. Worktree creation and session movement are separate operations. Use read-only agent permissions where appropriate; do not copy v1 frontmatter into v2 without checking migration rules. |
| OpenCode v1 or a fork | Use the exposed Task/subagent/session surface and observed version-specific background capability. | A fork or old release may differ from v2. If no background surface exists, foreground native agents remain valid. Do not assume a plugin or experimental flag is installed. |
| Claude Code | Use native subagents with fresh context; use supported background execution and completion handling. | A fork inherits the conversation and is unsuitable for independent review. `isolation: worktree` is explicit; verify the resulting source revision because a worktree may start from the default branch. Check task limits, tool restrictions and team behavior in the installed version. |
| Codex / ChatGPT Work | Use the current host's native spawn, wait/result and steering surfaces. Use a fresh context rather than a full-history fork for review. | Children inherit parent sandbox/approval settings. A read-only role instruction is not a separate sandbox. In a shared tree, assign disjoint write scopes or provision isolated worktrees. |
| Other harness | Map observed spawn/result/wait/cancel/context/capacity capabilities to the same EDN plan. | If unavailable, report the actual missing operation. Preserve evidence for another host; do not invent an API. |

## Current-session adapter record

Record host/version or unknown, observed tool names, whether fresh-context spawn is available, whether calls can return before completion, task/context identifiers, notification or polling mechanism, cancellation support, capacity, inherited permissions, worktree support, and selected models. An operation observed here need not exist in another harness.

Only the coordinator publishes aggregate receipts and advances dependent work. Workers may return evidence through their native completion result or an assigned immutable artifact. Inspect every result; completion notifications are not themselves a clean review. Keep unrelated ready jobs running after a failed branch of the graph.

## Sources

- [OpenCode v2 tools](https://dev.opencode.ai/v2/docs/tools/)
- [OpenCode v2 agents](https://dev.opencode.ai/v2/docs/agents/)
- [OpenCode stable agents](https://opencode.ai/docs/agents/)
- [Claude Code subagents](https://code.claude.com/docs/en/sub-agents)
- [Codex subagents](https://learn.chatgpt.com/docs/agent-configuration/subagents)

The native checker is portable NBB over a pure .cljc core. It does not implement these host APIs. The active assistant executes the plan with its actual available tools and records the results.

