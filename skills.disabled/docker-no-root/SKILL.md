---
name: docker-no-root
description: "Never run Docker containers as root. Enforce USER directive in Dockerfiles and user: in compose files. Database images are the only exception."
triggers:
  - "docker"
  - "compose"
  - "container"
  - "Dockerfile"
  - "deploy"
---

# Docker No-Root Policy

## Invariant
No application container in any service stack may run as PID 1 root (`user: 0:0`).

## Rules

### Dockerfiles
- If a Dockerfile installs global tools (corepack, pnpm, etc.) that need root, perform those `RUN` steps BEFORE the `USER` directive.
- Always add `USER 1000:1000` (or `USER node` if using official Node images) after all `RUN` steps that need elevated permissions.
- The final `CMD`/`ENTRYPOINT` must execute as the non-root user.

### docker-compose.yml
- Application services using pre-built images (`node:*`, `python:*`, `nginx:*`, etc.) MUST have `user: "${DOCKER_USER:-1000:1000}"`.
- Services with custom Dockerfiles that contain `USER` directives do not need a compose-level `user:` (Dockerfile wins).
- Database services (postgres, mongodb, redis, mysql, chromadb) manage their own users — skip them.
- Init containers that create files for other services MAY use `user: "0:0"` with a comment `# init container`.
- MongoDB search (`mongot`) placeholder containers MAY use root with a comment.

### Shared Volume Mounts
- When a bind mount is shared between containers with different users, root-owned files created by one container will block writes from non-root containers.
- If you encounter `EACCES` errors in a non-root container, check for root-owned files in the shared mount from sibling containers.
- Fix by either: (a) ensuring all containers sharing the mount use the same UID, or (b) running `pnpm install` / `npm install` in the container that owns the mount, not from the host.

## Anti-Patterns (DO NOT)
- Setting `user: "0:0"` on application services to work around permission issues.
- Using `npm install -g` as non-root without a `--prefix` flag.
- Running `corepack enable` as non-root on images where `/usr/local/bin` is root-owned.

## Correct Pattern
```dockerfile
FROM node:22-bookworm-slim
RUN corepack enable && corepack prepare pnpm@latest --activate
USER 1000:1000
```
