---
name: ssh-tunnel-docker-routing
description: "Pattern for setting up SSH reverse tunnels that route through Docker container networks: use 0.0.0.0 binding with GatewayPorts clientspecified, use Docker bridge gateway IP (not localhost) in reverse_proxy configs, and create systemd user services with autossh for persistence."
license: GPL-3.0
metadata:
  origin: session-mycology-promotion
  promoted-from-spore: ssh-tunnel-docker-routing
  recurrence: 1
---

# Skill: ssh-tunnel-docker-routing

## Goal
Pattern for setting up SSH reverse tunnels that route through Docker container networks: use 0.0.0.0 binding with GatewayPorts clientspecified, use Docker bridge gateway IP (not localhost) in reverse_proxy configs, and create systemd user services with autossh for persistence.

## Use This Skill When
- The same pattern or failure mode has recurred enough to deserve a named protocol.
- The current task clearly matches the lesson captured by this promoted spore.

## Do Not Use This Skill When
- The situation is obviously unrelated to ssh-tunnel-docker-routing.
- You only have a one-off glitch with no evidence that the recurring pattern applies.

## Inputs
- The current task context.
- The relevant files, logs, or artifacts that exhibit the pattern.

## Steps
1. Verify the current task really matches the recurring pattern.
2. Apply the core lesson from the originating spore: Pattern for setting up SSH reverse tunnels that route through Docker container networks: use 0.0.0.0 binding with GatewayPorts clientspecified, use Docker bridge gateway IP (not localhost) in reverse_proxy configs, and create systemd user services with autossh for persistence.
3. Prefer concrete evidence over narrative momentum.
4. If the pattern no longer fits reality, update or retire this skill instead of forcing it.

## Output
- A truthful, concrete application of the pattern to the current task.
