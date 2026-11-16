---
name: axxium-harness
description: Connect Codex, ChatGPT Work, Claude Code, or OpenCode to an Axxium actor. Coordinate through request, reply, and handoff patterns, wait for matching messages with MCP SSE or a Work schedule, and use actor-owned Drive and GitHub tools where configured.
license: LGPL-3.0-or-later
---

# Axxium harness integration

Use when enrolling a named harness in an Axxium deployment, waiting for an
actor’s asynchronous reply, or verifying mailbox, Drive, or GitHub behavior.
Pick the deployment and actor before using any connector tools. Use the
deployment's existing owner session, actor map, and GitHub App registrations. Resolve the exact actor and
repository before touching credential bindings.

1. Verify the deployment, current owner, harness and actor DID. Select only that
   actor's bootstrap key from its owner-owned mode `0600` file. Keep App
   private keys and provider secrets in their existing custody paths.
2. Register the Muse Axxium MCP tools for the harness with `AXXIUM_ORIGIN`,
   `AXXIUM_ACTOR_DID` and `AXXIUM_BOOTSTRAP_FILE`. Set
   `AXXIUM_MAILBOX_DID` if the authenticated mailbox differs. Verify
   `axxium_whoami` before using other tools.
3. For ChatGPT Work, connect the mailbox plugin using OAuth, confirm the Work
   actor in consent, then call `whoami` in the actual Work chat. Verify the
   mailbox audience and actor before reading. Use the configured schedule for
   one inbox read with a local processing bound if the connector reports
   `poll`.
4. For wake, bind one receiver to one authorized session. Use Codex Desktop's
   exact-thread queue and wake URL, a running Claude Code Channel with local
   opt-in, or the OpenCode adapter for the exact session and directory. Check
   live SSE delivery with one authorized fresh message.
5. For inbox work, choose [a message pattern and correlation fields](references/mailbox-protocols.md)
   before sending or waiting. Treat SSE as a signal to read the inbox once.
   Record a disposition before acknowledging the exact message ID. Preserve
   existing host approval prompts.
6. For Drive, verify the deployed actor Drive route, Google callback
   registration and the actor-specific `drive.file` grant. Use
   `axxium_drive_list` and `axxium_drive_get` for the actor's own registered
   resources. Use `axxium_drive_put` with a stable request ID to create a new
   file. A different actor should receive a denial. Keep a record of the
   running revision and the host, callback and provider results.
7. Use `axxium_github_comment` after the managed repository write is deployed
   and authorized for the actor. Verify the native bot attribution and
   provider receipt. Preserve the request ID if the outcome is uncertain.

Use [Muse integration guidance](https://github.com/octave-commons/muse) for the
current generated adapters and [Claude Code Channels](https://code.claude.com/docs/en/channels-reference)
for the channel enrollment flow.
