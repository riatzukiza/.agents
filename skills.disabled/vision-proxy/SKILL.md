---
name: vision-proxy
description: "Give vision capabilities to any model by analyzing images through vision-capable models (GPT-4o, Claude 3.5, Kimi K2.5) via the Open Hax proxy. DEPRECATED: Use analyze_image tool instead - it supports prompt parameter for specific inquiries."
license: GPL-3.0-or-later
compatibility: pi,opencode
metadata:
  audience: agents
  workflow: image-analysis
  version: 2
  deprecated: true
  successor: analyze_image
extensions:
  - vision-proxy
---

# Skill: Vision Proxy

> **DEPRECATED**: Use the `analyze_image` tool instead. It now supports a `prompt` parameter for specific inquiries and provides both structured analysis and direct Q&A modes.

## Goal
Enable any model to "see" and analyze images by routing vision requests through vision-capable models via the Open Hax proxy infrastructure.

## Recommended Tool

### analyze_image (preferred)
The unified vision tool that handles both structured analysis and specific inquiries:

```
analyze_image({
  source: "/path/to/image.png",
  prompt: "What color is the button?"  // Optional: for specific questions
})
```

When `prompt` is provided, it answers the specific question directly.
When `prompt` is omitted, it returns structured JSON with classification, regions, elements, and actions.

## Legacy Tools (still available)

### vision
Analyze an image using a vision-capable model.

```
vision({
  source: "/path/to/image.png",
  prompt: "Describe this image",   // optional, default is comprehensive
  model: "gpt-4o",                 // optional, default is gpt-4o
  detail: "auto"                   // low, high, or auto
})
```

### ocr
Extract text from an image (convenience wrapper).

```
ocr({
  source: "/path/to/screenshot.png",
  language: "en"  // optional, helps with accuracy
})
```

## Migration Guide

| Old Pattern | New Pattern |
|-------------|-------------|
| `vision({ source, prompt: "What is X?" })` | `analyze_image({ source, prompt: "What is X?" })` |
| `vision({ source })` | `analyze_image({ source })` |
| `ocr({ source })` | `analyze_image({ source, taskHint: "ocr" })` |

## Configuration

### Environment Variables
- `OPEN_HAX_OPENAI_PROXY_URL` - Proxy URL (default: http://localhost:8789)
- `OPEN_HAX_OPENAI_PROXY_AUTH_TOKEN` - Auth token (required)
- `VISION_MODEL` - Default model override