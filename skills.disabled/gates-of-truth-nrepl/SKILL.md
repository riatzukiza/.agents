---
name: gates-of-truth-nrepl
description: Connect to the Gates of Truth nREPL, inspect the live Phase 0 stellar simulation, and interact with the ECS world through the Clojure runtime.
---

# Skill: Gates of Truth nREPL Interaction

## Goal
Enable any agent to connect to the running Gates of Truth dev service, query the live simulation state, mutate the world through the ECS protocol, and understand the architecture invariants that govern the single-substrate world model.

## Prerequisites
- The dev service must be running under pm2 as `gates-of-truth-dev`.
- Port 7888 must be open on 127.0.0.1.
- The `clj` CLI must be available with the `:repl` alias.

## Connection Protocol

### Step 1: Verify the service is running
```bash
pm2 list | grep gates-of-truth-dev
# Expected: online status, pid present
```

### Step 2: Check service logs (without blocking)
```bash
pm2 logs gates-of-truth-dev --lines 30 --nostream
```
Omit `--nostream` only when you intend to tail forever; otherwise the command blocks.

### Step 3: Connect via nREPL
```bash
clj -M:repl -c -h 127.0.0.1 -p 7888
```

For non-interactive evaluation (pipe commands):
```bash
cat <<'CLJEOF' | clj -M:repl -c -h 127.0.0.1 -p 7888 2>/dev/null | tail -5
(require '[infra.dev.window :as w])
(pr-str (:phase0/phase @(:world @w/service-state)))
CLJEOF
```

## Live Simulation State Queries

### Get the world snapshot
```clojure
(require '[infra.dev.window :as w])
(require '[domain.ecs.core :as ecs])
(require '[domain.ecs.components :as c])
(require '[domain.phase0 :as p])

(let [ws @(:world @w/service-state)]
  {:phase     (:phase0/phase ws)
   :tick      (:tick ws)
   :bodies    (count (:alive ws))
   :sim-time  (:phase0/sim-time ws)
   :stats     (:phase0/stats ws)})
```

### Get the observer (player spark)
```clojure
(let [ws  @(:world @w/service-state)
      eid (first (ecs/entities-with ws c/observer))
      obs (when eid (ecs/get-component ws eid c/observer))]
  {:coherence      (:coherence obs)
   :focus-intensity (:focus-intensity obs)
   :focus-radius   (:focus-radius obs)
   :decoherence    (cond (> (:coherence obs) 0.8) :highly-coherent
                         (> (:coherence obs) 0.5) :coherent
                         (> (:coherence obs) 0.2) :wavering
                         :else :fading)})
```

### Get regime distribution
```clojure
(let [ws @(:world @w/service-state)]
  (frequencies (keep #(ecs/get-component ws % c/regime) (ecs/all-entities ws))))
```

### Get field report (one-line HUD)
```clojure
(p/field-report @(:world @w/service-state))
```

### Get resolved bodies (stars, planets, protostars)
```clojure
(let [ws @(:world @w/service-state)]
  (->> (ecs/entities-with ws c/matter-state c/mass)
       (map (fn [eid]
              (let [r (stellar/entity->region ws eid)]
                (select-keys r [:matter-state :mass :temperature]))))
       (filter #(not= :nebula (:matter-state %)))
       vec))
```

## World Mutation Protocol

### Reset to a fresh nebula
```clojure
(reset! (:world @w/service-state) (p/create-world))
```

### Reset with custom parameters
```clojure
(reset! (:world @w/service-state)
  (p/create-world {:nebula-mass 8e30
                   :nebula-radius 2e16
                   :gas-count 2000
                   :spin 0.7
                   :turb 0.2}))
```

### Modify the observer
```clojure
(require '[domain.player :as player])
(swap! (:world @w/service-state)
  #(player/update-observer % (fn [obs] (player/narrow-focus obs 5.0))))
```

### Pause/resume (disable adaptive pacing)
```clojure
;; Pause: freeze dt
(swap! (:world @w/service-state) assoc :phase0/adaptive-pacing? false)

;; Resume: re-enable
(swap! (:world @w/service-state) assoc :phase0/adaptive-pacing? true)
```

### Step one tick manually
```clojure
(swap! (:world @w/service-state) p/tick-world)
```

### Change camera
```clojure
(swap! (:camera @w/service-state) assoc :distance 400.0)
(swap! (:camera @w/service-state) assoc :mode :fit-all)
```

## Architecture Invariants (MUST FOLLOW)

1. **Single substrate**: There is exactly ONE world model — `domain.ecs.core`. Never create a parallel world type.
2. **Namespace law**: `domain/` is pure, `infra/` handles IO, `shape/` is math, `law/` is schemas. Never import `infra/` from `domain/`.
3. **Single renderer**: `infra.render` is the only renderer. Do not fork a second renderer namespace.
4. **New physics = new ECS system + components**: Attach as components on existing entities, run as ordered systems.
5. **No utils/ or helpers/**: Every function has a named home.

## Simulation Phases

| Phase | Description | Key Events |
|-------|-------------|------------|
| `:phase-0/initializing` | World bootstrapping | — |
| `:phase-0/nebula-collapse` | Gas cloud collapsing under self-gravity | Density increasing |
| `:phase-0/protostar` | Resolved core forming | Temperature rising |
| `:phase-0/ignition` | Fusion begins | `:event/stellar-ignition` |
| `:phase-0/accretion` | Disk forming, bodies merging | `:event/collision` |
| `:phase-0/planets-formed` | Stable system with planets | `:event/planet-formation` |
| `:phase-0/dispersed` | Cloud dispersed, no stars formed | Terminal state |

## Component Vocabulary

| Component | Key | Type | Description |
|-----------|-----|------|-------------|
| Position | `:component/position` | `[x y z]` | World-space coordinates |
| Velocity | `:component/velocity` | `[vx vy vz]` | m/s |
| Mass | `:component/mass` | double | kg |
| Temperature | `:component/temperature` | double | Kelvin |
| Matter State | `:component/matter-state` | keyword | `:nebula :protostar :star :planet :debris` |
| Regime | `:component/regime` | keyword | Dominant physics tag |
| B-Field | `:component/b-field` | `[bx by bz]` | Tesla (SI) |
| Observer | `:component/observer` | map | Player spark state |

## Use This Skill When

- You need to inspect the live Gates of Truth simulation.
- You want to modify the running world through the nREPL.
- You need to understand what phase the simulation is in.
- You want to spawn a fresh nebula with different parameters.
- You need to debug the ECS state or check component values.

## Runtime Notes

- The simulation runs at ~60 Hz with adaptive dt dilation.
- The observer's coherence is sustained by witnessing threshold events.
- Check recent logs without blocking: `pm2 logs gates-of-truth-dev --lines 30 --nostream`.
- `pm2 restart gates-of-truth-dev` restarts from a fresh nebula.
- Hot-reload via nREPL keeps the live world; `pm2 restart` resets it.
- The dev window shows regime-tinted fog, magnetic field lines, and shaded bodies.
