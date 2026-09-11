#!/usr/bin/env bash
set -euo pipefail

command -v jq >/dev/null 2>&1 || { echo "Missing: jq" >&2; exit 2; }

# Use the Voxx gateway for unified TTS access.
VOXX_URL="${VOXX_URL:-${VOICE_GATEWAY_SPEECH_URL:-http://127.0.0.1:8787/v1/audio/speech}}"
API_KEY="${VOICE_GATEWAY_API_KEY:-dev-token}"

TEXT=""
OUTPUT="out.mp3"
VOICE="${KNOXX_VOXX_VOICE_ID:-${VOICE_GATEWAY_TTS_DEFAULT_VOICE:-af_jessica}}"
MODEL="${KNOXX_VOXX_MODEL_ID:-kokoro}"
FORMAT="mp3"
SPEED="${KNOXX_VOXX_DEFAULT_SPEED:-${VOICE_GATEWAY_TTS_DEFAULT_SPEED:-1.15}}"
POSTPROCESS=""
POSTPROCESS_PROFILE=""
PROMPT_AWARE=""
PROMPT_AWARE_STYLE=""

urlencode() {
  jq -nr --arg value "$1" '$value|@uri'
}

add_query_param() {
  local key="$1"
  local value="$2"
  [[ -n "$value" ]] || return 0
  local sep="?"
  [[ "$VOXX_URL" == *\?* ]] && sep="&"
  VOXX_URL+="${sep}${key}=$(urlencode "$value")"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --text)   TEXT="$2";   shift 2 ;;
    --voice)  VOICE="$2";  shift 2 ;;
    --model)  MODEL="$2";  shift 2 ;;
    --output) OUTPUT="$2"; shift 2 ;;
    --format) FORMAT="$2"; shift 2 ;;
    --speed)  SPEED="$2";  shift 2 ;;
    --postprocess) POSTPROCESS="$2"; shift 2 ;;
    --postprocess-profile) POSTPROCESS_PROFILE="$2"; shift 2 ;;
    --prompt-aware) PROMPT_AWARE="1"; shift ;;
    --no-prompt-aware) PROMPT_AWARE="0"; shift ;;
    --prompt-aware-style) PROMPT_AWARE_STYLE="$2"; shift 2 ;;
    --url|--voxx-url) VOXX_URL="$2"; shift 2 ;;
    --api-key) API_KEY="$2"; shift 2 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

[[ -n "$TEXT" ]] || { echo "--text is required" >&2; exit 2; }

payload=$(jq -n \
  --arg input "$TEXT" \
  --arg voice "$VOICE" \
  --arg model "$MODEL" \
  --arg response_format "$FORMAT" \
  --argjson speed "$SPEED" \
  '{input:$input, voice:$voice, model:$model, response_format:$response_format, speed:$speed}')

add_query_param "postprocess" "$POSTPROCESS"
add_query_param "postprocess_profile" "$POSTPROCESS_PROFILE"
add_query_param "prompt_aware" "$PROMPT_AWARE"
add_query_param "prompt_aware_style" "$PROMPT_AWARE_STYLE"

curl -fsSL \
  -X POST "$VOXX_URL" \
  -H "Authorization: Bearer $API_KEY" \
  -H "Content-Type: application/json" \
  --data "$payload" \
  --output "$OUTPUT"

echo "Wrote $OUTPUT via Voxx Gateway"
