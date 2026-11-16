---
name: music-composition-arrangement
description: Compose songs, cues, loops, and scores as structured musical briefs with tempo, key, harmony, motifs, arrangement, MIDI/audio render plans, and mix notes.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: music-composition
  version: 1
---

# Skill: Music Composition Arrangement

## Goal
Turn a musical intent into a usable composition plan, lead sheet, MIDI/event specification, loop structure, or render-ready arrangement.

## Use This Skill When
- The user asks for music, songs, beats, loops, cues, soundtrack, motifs, chord progressions, melodies, or arrangement.
- The user wants a composition that can drive native synthesis, a DAW, OpenUtau vocals, animation timing, or game audio.
- The user wants variations: calmer, darker, faster, more danceable, more cinematic, etc.

## Do Not Use This Skill When
- The user wants to identify or transcribe copyrighted audio exactly.
- The user wants to clone a living artist's style; use general musical traits instead.
- The user only needs direct WAV generation and already provided a complete spec; use `native-music-synthesis`.

## Inputs
- Purpose: standalone song, underscore cue, loop, sting, trailer, menu music, vocal backing, etc.
- Mood, genre, BPM, key/mode, duration, instrumentation, complexity, and delivery format.
- Sync targets: lyrics, scene beats, animation frames, game states, or edit points.

## Workflow
1. **Define the musical brief**
   - State BPM, meter, key/mode, emotional arc, density curve, and reference traits.
   - Translate references into neutral traits: rhythm, timbre, harmony, arrangement, mix space.
2. **Write the core material**
   - Create motif, chord progression, bass movement, drum groove, and hook contour.
   - Keep motifs singable or memorable unless the brief asks for texture/noise.
3. **Arrange by sections**
   - Map intro, A/B, chorus/drop, bridge, breakdown, outro, loop seam, or cue markers.
   - Specify what enters/exits each section and why.
4. **Prepare implementation data**
   - For MIDI-like output: note, start, duration, velocity, channel/instrument.
   - For synthesis: oscillator/sample choices, envelopes, effects, gain, pan.
   - For vocals: syllable rhythm, melody range, breaths, held vowels, harmony stacks.
5. **Mix and production notes**
   - Provide frequency roles, stereo placement, reverb/delay choices, sidechain or ducking notes.
   - Reserve headroom and avoid masking vocals or dialogue.
6. **Iterate musically**
   - Change one axis at a time: tempo, harmony, motif, rhythm, instrumentation, density, or mix.

## Output
- A lead sheet, arrangement table, MIDI/event list, or generation spec.
- Optional handoff notes for `native-music-synthesis`, OpenUtau, `voice-tts` (`skill:voice-tts`), `autotune` (`skill:autotune`), Blender/animation timing, or ffmpeg assembly.
- A short rationale for key musical choices.

## Quality Checks
- The groove supports the emotional intent.
- The hook/motif is repeated with meaningful variation.
- The arrangement has contrast, not only accumulation.
- Loopable assets have clean starts, tails, and seam notes.
