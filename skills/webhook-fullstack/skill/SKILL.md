---
name: webhook-fullstack
version: 1
description: >
  Full-stack webhook skill for shadow-cljs + Fastify plugins.
  Data-oriented, schema-driven, event-sourced with a Merkle DAG
  immutable event ledger via eta-mu. Work flows strictly through
  six layers: shape → extern → law → domain → infra → client.
  All knowledge accumulates as EDN in the skill directory.
  Agents MUST append reason events to reason.edn and decision.edn
  at every pivot or risk decision during plugin construction.
agents: [main_agent, general_purpose, clojure]
tags: [clojurescript, shadow-cljs, fastify, webhook, event-sourcing, malli, merkle-dag, eta-mu]
layer-order: [shape, extern, law, domain, infra, client]
ledger-files: [reason.edn, decision.edn]
---

# Webhook Fullstack Skill

This skill defines a full-stack webhook architecture using the ordered flow:

shape → extern → law → domain → infra → client

## Core rules

- shape.* defines pure data shapes first.
- extern.* isolates JavaScript and npm boundaries.
- law.* validates and coerces data with Malli.
- domain.* stays pure and free of I/O.
- infra.* owns Fastify plugins, endpoint registration, and ledger writes.
- client.* is the last boundary for browser or remote consumers.

## Fastify plugin reuse

Shared plugins must be wrapped with fastify-plugin so decorations and hooks are
available across sibling route plugins. Register shared plugins before route
plugins.

## Ledger rules

Agents using this skill must append:
- one reason event to reason.edn for each work session.
- one decision event to decision.edn whenever they pivot or take a significant risk.

## Canonical directories

- src/shape
- src/extern
- src/law
- src/domain
- src/infra
- src/client

## Example accumulation files

The skill directory contains append-only EDN files for shapes, reasoning, and
pivot decisions.
