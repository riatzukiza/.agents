---
name: note-plan-gen
description: Generate symbolic note plans (time, midi note) for vocal tuning or synthesis.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: audio-production
  version: 1
---

# Skill: note-plan-gen

## Purpose
Convert a structured lyrics file into a `notes.txt` pitch plan for `autotune` (`skill:autotune`).
Assigns MIDI note numbers and timing to each syllable given key, scale, BPM, and stress.

## Dependencies
- `python3` (stdlib only)

## Usage
```bash
.agent/skills/note-plan-gen/agent-note-plan-gen.py \
  --lyrics lyrics.txt \
  --key C \
  --scale major \
  --bpm 90 \
  --octave 4 \
  --output notes.txt
```

## Inputs
| Param | Description |
|---|---|
| `--lyrics FILE` | Lyrics file (see format below) |
| `--key NOTE` | Root note: C D E F G A B (with optional # or b) |
| `--scale NAME` | major, minor, pentatonic, blues, dorian, mixolydian |
| `--bpm N` | Tempo in beats per minute |
| `--octave N` | Base octave (default 4, middle C = C4 = MIDI 60) |
| `--output FILE` | Output notes.txt path |
| `--swing FLOAT` | Swing ratio 0.5 (straight) to 0.75 (hard swing), default 0.5 |
| `--contour NAME` | arch, descend, ascend, flat, wave |

## Lyrics format
Tab-separated: `syllable<TAB>stress<TAB>beats`
- stress: 1=primary, 2=secondary, 0=unstressed
- beats: duration in beats (fractions ok)

```text
stay	1	1.0
with	0	0.5
me	2	0.5
to	0	0.5
night	1	1.5
```

## Skill chain
<- follows: `lyrics-gen` or hand-authored lyrics.txt
-> precedes: `autotune` (`skill:autotune`) — uses `~/.pi/agent/skills/autotune/tts.sh` + rubberband/sox pipeline
