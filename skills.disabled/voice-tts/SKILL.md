---
name: voice-tts
description: "Convert text to speech via the OpenHax Voxx gateway, strongly preferring Kokoro."
license: GPL-3.0-or-later
compatibility: opencode, pi
metadata:
  audience: agents
  workflow: tts-synthesis
  version: 4
---

# Skill: voice-tts

## Architecture

The **Voice Gateway (Voxx)** is the canonical TTS boundary for agents. Agents should not talk to voice providers directly; send text to Voxx and let the service choose the backend.

Canonical source/runtime homes:

- `~/devel/orgs/open-hax/voxx/` — source package
- `~/devel/services/voxx/` — local compose/runtime home

## Strong Default

Use Voxx through **Kokoro** by default. If a remote/free provider is useful (for example Xiaomi MiMo), put it in the Voxx backend order rather than editing prompts, and keep Kokoro after it. Keep eSpeak opt-in only; do not allow silent robotic fallback in normal agent speech.

Recommended backend orders:

```bash
# stable local default
VOICE_GATEWAY_TTS_BACKEND_ORDER=kokoro
VOICE_GATEWAY_TTS_DEFAULT_VOICE=af_jessica
VOICE_GATEWAY_TTS_DEFAULT_SPEED=1.15

# remote/free provider first, local fallback on quota/status errors
VOICE_GATEWAY_TTS_BACKEND_ORDER=xiaomi_mimo,kokoro
VOICE_GATEWAY_TTS_DEFAULT_VOICE=af_jessica
VOICE_GATEWAY_TTS_DEFAULT_SPEED=1.15
```

| Backend | Characteristics | Best Use Case |
|---|---|---|
| **Kokoro** | High quality, fast, local/OpenAI-compatible | Default agent speech path |
| **MeloTTS** | Local fallback with multiple-language support | Narrative or language-specific fallback |
| **Xiaomi MiMo** | Optional remote/free-plan backend through Voxx | Use only through Voxx, with local fallbacks after it |
| **eSpeak** | Small local fallback | Explicit diagnostic only; do not include in normal fallback order |

## Environment Variables

| Var | Required | Notes |
|---|---|---|
| `VOXX_URL` | preferred | The endpoint for the Voxx gateway (default: `http://127.0.0.1:8787/v1/audio/speech`) |
| `VOICE_GATEWAY_API_KEY` | preferred | API key for the gateway (default: `dev-token`) |
| `VOICE_GATEWAY_TTS_BACKEND_ORDER` | recommended | Prefer `kokoro`; if remote is desired use e.g. `xiaomi_mimo,kokoro` so quota/status errors fall back locally without silent eSpeak |
| `VOICE_GATEWAY_TTS_DEFAULT_VOICE` | recommended | Default Kokoro voice; current workspace default is `af_jessica` for brighter, more energetic speech |
| `VOICE_GATEWAY_TTS_DEFAULT_SPEED` | recommended | Default speed for callers that omit speed; current workspace default is `1.15` |
| `TTS_QUEUE_MAX_CONCURRENT` | recommended | Voxx processing queue concurrency; default `1` protects local GPU/CPU |
| `TTS_QUEUE_MAX_PENDING` | recommended | Pending TTS queue depth before Voxx rejects bursts; default `32` |
| `TTS_QUEUE_TIMEOUT_SECONDS` | recommended | Max wait for queued TTS; default `120` |
| `TTS_POSTPROCESS_PROFILE` | optional | Service default final mastering profile; callers can override per request |
| `TTS_PROMPT_AWARE_DEFAULT` | optional | Service default for prompt-aware tag instructions; normally keep `0` and opt in per request |

## Postprocess and Prompt-Aware API

Voxx exposes final mastering and prompt-aware performance as request-level options on `POST /v1/audio/speech`, provider-style TTS routes, and realtime TTS query strings.

Postprocess profiles:

| Profile | Aliases | Use |
|---|---|---|
| `sports-commentator-v1` | `sports`, `commentator` | high-energy broadcast / sports announcing |
| `broadcast-warm-v1` | `broadcast`, `warm` | warm conversational broadcast polish |
| `narrator-polish-v1` | `narrator`, `polish` | audiobook-style leveling and presence |
| `crisp-radio-v1` | `radio`, `crisp` | tight radio/dispatch intelligibility |
| `soft-studio-v1` | `soft`, `studio` | gentle studio cleanup |

Request options can be query params or JSON fields:

- `postprocess_profile=radio` or JSON `"postprocess_profile": "radio"`
- `postprocess=off` to bypass final mastering for one request
- `prompt_aware=1` or JSON `"prompt_aware": true` to ask prompt-capable backends to treat `[excited]`, `[whisper]`, `[pause]`, etc. as performance tags
- `prompt_aware_style=...` for custom tag instructions

List profiles with:

```bash
curl -H "Authorization: Bearer ${VOICE_GATEWAY_API_KEY:-dev-token}" \
  http://127.0.0.1:8787/v1/audio/postprocess-profiles
```

## CLI Script

`~/.pi/agent/skills/voice-tts/tts.sh`

```bash
~/.pi/agent/skills/voice-tts/tts.sh --text "Hello world" --output out.mp3
# optional overrides:
#   --voice <voice_id>   Voxx/Kokoro voice ID, default af_jessica; examples: af_jessica, af_bella, af_nicole, alloy, nova
#   --model <model_id>   e.g., "kokoro", "tts-1", "gpt-4o-mini-tts"
#   --format <format>    default: mp3
#   --speed <float>      playback speed multiplier
#   --postprocess-profile <profile|alias>  e.g. sports, radio, narrator, soft
#   --postprocess <on|off|profile>         e.g. off or broadcast
#   --prompt-aware                         enable tag-aware performance prompting
#   --prompt-aware-style <text>            custom tag interpretation instruction
```

Examples:

```bash
~/.pi/agent/skills/voice-tts/tts.sh \
  --text "[excited] Local Voxx is alive!" \
  --postprocess-profile radio \
  --prompt-aware \
  --output out.mp3

~/.pi/agent/skills/voice-tts/tts.sh \
  --text "Clean raw upstream voice" \
  --postprocess off \
  --output raw.mp3
```

## Backend Selection

Voxx supports both a service default backend order and per-request provider selection:

- Service default/fallback order: `VOICE_GATEWAY_TTS_BACKEND_ORDER=kokoro` or `xiaomi_mimo,kokoro` in the Voxx runtime; current default voice/speed is `af_jessica` at `1.15`. Keep eSpeak opt-in only for explicit tiny-fallback diagnostics.
- Per-request provider preference: set the OpenAI-compatible JSON `model` field, or pass `--model` to `tts.sh`.

Provider selection examples:

```bash
# Force Melo for this request if available.
~/.pi/agent/skills/voice-tts/tts.sh \
  --model melo \
  --text "Melo local fallback check." \
  --output /tmp/voxx-melo.mp3

# Force eSpeak for a last-resort local intelligibility check.
~/.pi/agent/skills/voice-tts/tts.sh \
  --model espeak \
  --text "eSpeak direct provider check." \
  --output /tmp/voxx-espeak.mp3

# Force an optional remote provider while still going through Voxx.
~/.pi/agent/skills/voice-tts/tts.sh \
  --model xiaomi_mimo \
  --text "Remote provider through Voxx check." \
  --output /tmp/voxx-xiaomi.mp3
```

Always confirm the provider actually used from the response header `x-openhax-tts-backend` when debugging. A `200` only means Voxx produced audio; the backend header identifies whether the requested provider or a fallback rendered it.

Keep Kokoro first for stable local agent work when no specific provider is required. If a remote provider such as Xiaomi MiMo is first and returns quota/rate-limit/auth/status-code errors (for example 402, 403, 429, or 5xx), Voxx should continue to Kokoro. Agents should not rewrite prompts or bypass Voxx; ensure the service order includes `kokoro` and excludes `espeak` unless the user explicitly asks for a tiny diagnostic fallback.

Prompt-aware tags are provider-dependent. Xiaomi MiMo receives prompt-aware instructions through its chat style prompt, and OpenAI-compatible remote providers receive `instructions`; local Kokoro/Melo/eSpeak may speak tags literally, so use prompt-aware mode only when the selected backend can honor it or when that risk is acceptable.

Voxx has a bounded TTS processing queue. If calls return queue-full or queue-timeout 503s, back off and retry later; do not launch parallel retries.

## Do Not

- Do not bypass Voxx for TTS.
- Do not add direct proprietary voice-provider SDK usage to skills or agent code.
- Do not send raw markdown to the TTS engine; strip it to plain text first.
