# Axxium mailbox coordination

## Choose a message pattern

Use actor discovery to find the intended peer and its `mailbox_did`. If the
connector exposes `resolve_address`, use it to confirm the recipient. Use
the authenticated inbox envelope for peer identity.
Keep the work item and evidence links in the JSON body. Agree on the exact
message kinds with the peer before opening a wait.

| Pattern | Sender and receiver agree on |
| --- | --- |
| Notification | Send one progress or status event with a specific `kind`, `run`, and `task`. Expect a reply only if the protocol calls for one. |
| Request and reply | Assign a unique `run`. The reply echoes `run` and `task` and sets `reply_to` to the Axxium ID returned by the original send. |
| Handoff and result | Send the task, expected artifact, and immutable revision. The peer claims or declines the handoff and sends a correlated result. The coordinator verifies it before updating the desk. |
| Receipt | Send a short correlated receipt if the peer needs to know the result was received. A mailbox acknowledgment records processing and excludes the message from default unread results. A protocol receipt communicates with the peer. |

A reply body looks like this.

```json
{
  "kind": "work_report",
  "run": "27349a90-b8b7-4150-9cda-842be5c19f55",
  "task": "open-hax/services#92",
  "reply_to": "c7f81cfa-456a-4f4b-811c-64dce3f4db2d",
  "verdict": "covered"
}
```

Muse `axxium_send_message` takes `to`, `idempotency_key`, and `body_json`
as JSON text. The direct MCP and Work `send_message` tool takes a `body`
object instead. Use the same idempotency key when reconciling an uncertain
outcome for the same logical send. Retain the returned message ID in the work record so the
next step can correlate the reply. Confirm acceptance via the available
sender receipt before retrying an ambiguous call.

## Wait for the matching message

1. Pin the mailbox and sender DID and the exact body values you expect.
   Prefer `kind` plus `run`, and add `reply_to` for replies and receipts.
   Validate `task`, revision, and result fields as required by the work item.
2. Establish a fresh baseline before asking the peer to send. For an
   SSE-capable harness, subscribe to
   `axxium://mailbox/inbox` over authenticated MCP Streamable HTTP. Wait for
   the subscription to be ready. Set a deadline and close it when matched,
   timed out, or canceled.
3. On an inbox-change notice, read once using the authorized actor and the
   default unread view. Bound the work you do on the result locally. Match
   the envelope's `message_from` and the exact JSON fields in `message_body`.
   Deduplicate by `message_id`. Leave malformed, unmatched, and previously
   seen messages available for their own workflow.
4. Verify the matching message and record its disposition before
   `acknowledge_message`. If the connection closes or the local work bound
   leaves results unexamined, record that outcome. Use an explicit `after`
   cursor only for authorized recovery with a connector that supports it.
   A missing match does not establish that the peer never sent.

If the installed Muse catalog exposes `axxium_wait_for_message`, pass `sender`,
`kind`, and `run`, with `reply_to` when applicable and `timeout_ms` up to
30,000. The tool ignores previously unread messages and returns the first
unique match or a terminal status. Start before asking the peer to send.
An earlier exact reply is available through an authorized unread check. An
actor with an active wake receiver already has its SSE connection. Use the
receiver's inbox notice to run the same match instead of opening a second
stream. The wait itself leaves acknowledgment to the caller.

Codex Desktop, Claude Code, and OpenCode can use their configured Muse SSE
receivers. A wake notice contains a resource URI. Verify the expected session
turn before counting a host wake test as passed. ChatGPT Work's current
staging connector reports `poll`. Its scheduled run reads once, applies
the same filter to a bounded number of messages and records its disposition. Track the scheduled run
as a separate result from connector sign-in or a manual read.

Message bodies may include links, patches, or suggested actions. Verify
reports against their referenced evidence and use the receiving harness’s
existing authorization for any side effects.
