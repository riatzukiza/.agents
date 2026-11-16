---
name: autotune
description: "Pitch correction and audio processing pipeline using ffmpeg + rubberband/sox. Pairs with voice-tts for full TTS-to-processed-audio workflows."
license: GPL-3.0-or-later
compatibility: opencode, pi
metadata:
  audience: agents
  workflow: audio-processing
  version: 1
---

# Skill: autotune

## Goal
Apply pitch correction ("autotune") and optional effects to audio files.
Typical pipeline: raw TTS output → pitch snap → reverb/chorus → final MP3/WAV.

## Tools Required

| Tool | Purpose | Install |
|---|---|---|
| `ffmpeg` | decode/encode any format | `apt install ffmpeg` |
| `rubberband-cli` | high-quality pitch shift / time stretch | `apt install rubberband-cli` |
| `sox` | pitch snap, reverb, chorus effects | `apt install sox` |

## Canonical Workflow

### 1. TTS → raw WAV
```bash
~/.pi/agent/skills/voice-tts/tts.sh --text "words" --postprocess off --output dry.mp3
ffmpeg -y -i dry.mp3 dry.wav
```

### 2. Pitch snap with rubberband (semitone grid)
```bash
# snap to nearest semitone (T-Pain style)
rubberband --pitch 0 --crisp 6 dry.wav tuned.wav
```

### 3. Pitch snap with sox (simpler, lower quality)
```bash
sox dry.wav tuned.wav pitch 0       # 0 cents = detect + snap
```

### 4. Add reverb
```bash
sox tuned.wav wet.wav reverb 50 50 100
```

### 5. Final encode
```bash
ffmpeg -y -i wet.wav -b:a 192k final.mp3
```

## Combined One-liner
```bash
~/.pi/agent/skills/voice-tts/tts.sh --text "hello" --postprocess off --output /tmp/dry.mp3 && \
  ffmpeg -y -i /tmp/dry.mp3 /tmp/dry.wav 2>/dev/null && \
  rubberband --pitch 0 --crisp 6 /tmp/dry.wav /tmp/tuned.wav && \
  sox /tmp/tuned.wav /tmp/wet.wav reverb 50 50 100 && \
  ffmpeg -y -i /tmp/wet.wav -b:a 192k /tmp/final.mp3 2>/dev/null && \
  echo "Done: /tmp/final.mp3"
```

## Notes
- rubberband `--pitch 0` means "no shift" but still re-pitches to enforce clean tuning when combined with `--crisp`
- sox `pitch` takes cents (100 cents = 1 semitone); 0 = identity
- For hard T-Pain effect, pitch to +200 or -200 cents and add heavy reverb
- Always work in WAV internally; MP3 encode as the final step only
- For autotune source capture, prefer `--postprocess off` to avoid compressing/limiting before pitch work; add Voxx postprocess profiles such as `radio`, `narrator`, or `soft` after tuning if desired.
- Current dry spoken Voxx default is Kokoro `af_jessica` at speed `1.15`; override voice/speed only when the tuning plan needs a different performance.
- If a remote Voxx-backed source provider returns quota/rate-limit/auth/status-code errors, retry through the same Voxx endpoint with local fallbacks configured (`kokoro,espeak` after the remote provider); do not bypass Voxx or fan out parallel retries.
