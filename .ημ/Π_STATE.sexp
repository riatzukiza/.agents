(π-state
  (ts "2026-08-13T15:31:09Z")
  (branch "main")
  (base "00a05c8")
  (owner "opencode/local")
  (stageable
    (paths
      "skills/harness-handoff/"
      "skills/i3-devops/"
      "skills/model-routing/"
      "skills/tailscale-nfs-mesh/"))
  (blocked
    (path "PRINCIPLE.edn")
    (reason "Modified by chatgpt/github-connector session; scope changed from :global to :chatgpt with significant content removal. Needs review before commit."))
  (concurrent-dirt
    (path "skills.disabled/")
    (reason "Benched skills directory, untracked. Not owned by this session.")
    (path "skills.backup-20260319T002831Z/")
    (reason "Backup directory from 2026-03-19. Not owned.")
    (path "pi.skills")
    (reason "Broken symlink to /home/err/.pi/agent/skills (non-existent). Not owned."))
  (excluded
    (path "skills/webhook-fullstack/.clj-kondo/")
    (reason "Build artifact")
    (path "skills/webhook-fullstack/.cpcache/")
    (reason "Build artifact")
    (path "skills/webhook-fullstack/.lsp/")
    (reason "Build artifact")
    (path "skills/webhook-fullstack/.shadow-cljs/")
    (reason "Build artifact")))
