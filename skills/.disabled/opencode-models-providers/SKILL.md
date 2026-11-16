---
name: opencode-models-providers
description: "Skill: OpenCode Models & Providers"
---

# Skill: OpenCode Models & Providers

## Goal
Select, validate, or update model/provider configurations with authoritative provider documentation.

## Use This Skill When
- You change provider integrations or model selection.
- You update auth, environment variables, or model compatibility.

## Do Not Use This Skill When
- The change is unrelated to model/provider behavior.

## Inputs
- Target provider(s) and model(s).
- Current provider configuration behavior in OpenCode.

For OpenCode's anonymous free MiMo route, recover [the recorded no-key/tool-registration guidance](../../docs/notes/opencode-anonymous-mimo-routing.md) before investigating credentials. Discover the current eligible model from the installed client's refreshed catalog; do not treat a dated slug or upstream error as authority to change providers.

## Steps
1. Confirm supported providers/models in OpenCode docs.
2. Validate provider-specific constraints with upstream docs.
3. Update any OpenCode config defaults or validation rules.

## Output
- Accurate provider/model behavior and updated documentation.

## References
- OpenCode models docs: https://opencode.ai/docs/models/
- OpenCode providers docs: https://opencode.ai/docs/providers/
- OpenAI API docs: https://platform.openai.com/docs
- Anthropic API docs: https://docs.anthropic.com/
- Azure OpenAI docs: https://learn.microsoft.com/azure/ai-services/openai/
- Google Vertex AI generative AI docs: https://cloud.google.com/vertex-ai/generative-ai/docs
- Ollama API docs: https://github.com/ollama/ollama/blob/main/docs/api.md
- Ollama model library: https://ollama.com/library
