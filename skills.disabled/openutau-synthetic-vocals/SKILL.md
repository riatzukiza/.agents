---
name: openutau-synthetic-vocals
description: "Create synthetic singing workflows for OpenUtau: lyrics, melody, syllabification, phonemizers, voicebank checks, USTX project drafting, rendering, and mix handoff."
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: synthetic-vocals
  version: 1
---

# Skill: OpenUtau Synthetic Vocals

## Goal
Turn lyrics and melody into an OpenUtau-ready vocal part, USTX project draft, render plan, or troubleshooting checklist.

## Use This Skill When
- The user asks for synthetic vocals, UTAU/OpenUtau singing, vocal melody, lyric-to-notes, harmonies, voicebank setup, phonemizers, or USTX files.
- The user wants vocals integrated with a song, animation, game cue, or multimedia project.
- The user needs a reproducible handoff for a human or automated OpenUtau session.

## Do Not Use This Skill When
- The user asks to clone a real person's voice without consent.
- The user wants hidden impersonation or deceptive audio.
- The user expects guaranteed headless OpenUtau rendering without first verifying the installed version and available render path.

## Inputs
- Lyrics, language, pronunciation needs, melody, BPM, meter, key, range, and expression goals.
- Voicebank name/path, singer type, phonemizer, resampler/wavtool, and license constraints.
- Desired output: USTX, WAV render, harmony stack, phoneme timing sheet, or mix-ready stems.

## Workflow
1. **Verify the vocal contract**
   - Confirm consent/licensing for the voicebank and allowed usage.
   - State language, singer/voicebank, phonemizer, tempo, key, range, and output format.
2. **Prepare singable lyrics**
   - Split text into syllables aligned to notes.
   - Mark breaths, rests, melisma, held vowels, consonant pickups, and pronunciation hints.
   - Keep vowels on sustained notes; place consonants before beats when natural.
3. **Map melody and timing**
   - Create a note table with measure/beat or tick position, duration, MIDI tone, lyric, and expression note.
   - Keep range comfortable for the chosen voicebank unless a stylized strain is intended.
4. **Build or edit USTX**
   - USTX is UTF-8 YAML. Include project name, tempos, time signatures, tracks, voice parts, and notes.
   - Track fields should include singer and phonemizer when known.
   - Note fields should include position, duration, tone, and lyric; add pitch/vibrato/expression data only when intentional.
5. **Render pragmatically**
   - Prefer verified local OpenUtau behavior. If GUI export is the only reliable route, produce the USTX and explicit GUI steps.
   - If a local CLI/headless renderer exists, run `--help` or equivalent first and record the exact command before batch rendering.
   - Do not claim a WAV was rendered unless an output file exists and is playable.
6. **Mix handoff**
   - Export dry vocal and optional harmony/double tracks.
   - Provide tuning notes, timing offsets, de-essing/EQ/compression suggestions, and reverb/delay send ideas.

## Troubleshooting
- If phonemes are missing, check phonemizer choice, dictionary support, lyric syntax, aliases, and voicebank encoding.
- If notes are silent, check singer path, oto/config files, resampler/wavtool, cache, note range, and invalid lyrics.
- If timing sounds late, move consonant-heavy syllables earlier or use phoneme overrides.
- If headless rendering fails, fall back to USTX handoff plus GUI render instructions.

## Output
- USTX draft, note/lyric timing table, render command or GUI export steps, and mix notes.
- Explicit list of assumptions, missing assets, and unverified render steps.

## Voice TTS Handoff
When the goal is **spoken** (not sung) synthesis from assistant message content:
- Use `skill:voice-tts` instead of OpenUtau
- Script: `~/.pi/agent/skills/voice-tts/tts.sh --text "..." --output out.mp3`
- For performed spoken delivery, add Voxx request options such as `--postprocess-profile narrator` or `--postprocess-profile radio --prompt-aware` when using a backend that can honor tags like `[whisper]`, `[excited]`, or `[pause]`.
- If a remote Voxx-backed provider hits quota/rate-limit/auth/status errors, keep using Voxx and fall back through the configured local providers (`kokoro,melo,espeak`); do not change prompts or call providers directly.
- For pitch-processed speech, chain with `skill:autotune` (rubberband + sox)

## References
- OpenUtau USTX format: https://github.com/stakira/OpenUtau/wiki/USTX-file-format
- voice-tts skill: `~/.pi/agent/skills/voice-tts/SKILL.md`
- autotune skill: `~/.pi/agent/skills/autotune/SKILL.md`
- OpenUtau phonemizers: https://github.com/stakira/OpenUtau/wiki/Phonemizers
