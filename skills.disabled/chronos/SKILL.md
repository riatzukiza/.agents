---
name: chronos
description: "Time tracking for contracting work. Start/stop sessions, track billable hours, and manage projects."
license: GPL-3.0-or-later
compatibility: pi,opencode
metadata:
  audience: agents
  workflow: time-tracking
  version: 1
extensions:
  - chronos
---

# Skill: Chronos Time Tracker

## Goal
Enable agents to track time on behalf of the user, manage projects, and observe session status.

## Use This Skill When
- User mentions tracking time, billing, hours, or clients
- User asks "what am I working on?" or "how long have I been working on X?"
- User wants to start/stop a work session
- User wants to see time reports or status
- User mentions rates, invoicing, or project management

## Do Not Use This Skill When
- The user has an unrelated request
- No time tracking is needed

## Actions

### status (default)
Check active sessions and recent activity.

```
chronos({ action: 'status' })
```

Returns:
- Currently active sessions with elapsed time
- Recent sessions
- List of projects

### start
Start a new time session.

```
chronos({ action: 'start', project: 'ProjectName', task: 'Description' })
```

Parameters:
- `project` (required): Project name (created if doesn't exist)
- `task` (optional): Task description
- `tags` (optional): Array of tags for categorization
- `client` (optional): Client name
- `hourly_rate` (optional): Hourly rate for billing

### stop
Stop the currently active session(s).

```
chronos({ action: 'stop' })
```

Returns total duration logged.

### list
List recent sessions.

```
chronos({ action: 'list', limit: 10 })
```

### project_create
Create a new project.

```
chronos({ action: 'project_create', project: 'NewProject', client: 'ClientName', hourly_rate: 150 })
```

### project_list
List all projects.

```
chronos({ action: 'project_list' })
```

## Web UI
The Chronos web interface is available at http://localhost:5199

## Starting the Server
If the server is not running:

```bash
cd ~/devel/packages/chronos && bun start
```

## Environment Variables
- `CHRONOS_URL`: Override default URL (default: http://localhost:5199)
- `CHRONOS_PORT`: Server port (default: 5199)

## Examples

### Starting a work session
```
User: "Start tracking time for client work"
Agent: chronos({ action: 'status' })  // First check for existing sessions
Agent: chronos({ action: 'start', project: 'Client Contract Work', task: 'Feature development' })
Agent: "Started session on 'Client Contract Work'. View at http://localhost:5199"
```

### Checking status
```
User: "What am I working on?"
Agent: chronos({ action: 'status' })
Agent: "You have an active session on 'Pi Development': Building Chronos time tracker (2h 15m)"
```

### Stopping work
```
User: "Stop tracking"
Agent: chronos({ action: 'stop' })
Agent: "Stopped session. Total: 2h 45m on 'Pi Development'"
```