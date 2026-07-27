---
name: multimedia-creative-pipeline
description: Orchestrate story, music, visuals, graphics, 3D, animation, and OpenUtau vocals into a coherent multimedia production plan with asset manifests and handoffs.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: multimedia-production
  version: 1
---

# Skill: Multimedia Creative Pipeline

## Goal
Coordinate creative work across narrative, sound, visuals, graphics, 3D, animation, and synthetic vocals without losing intent, continuity, or deliverable discipline.

## Use This Skill When
- The user asks for a complete creative package, music video, trailer, game intro, story world, animated short, album/visualizer, or cross-media asset suite.
- Multiple creative skills need to work together.
- The user wants a production plan, asset manifest, phase breakdown, or handoff map.

## Do Not Use This Skill When
- The task is a single isolated artifact; use the most specific skill instead.
- The user only wants brainstorming with no production outputs.
- The project requires external collaborators or paid assets not available; first mark dependencies and permissions.

## Inputs
- Project goal, audience, runtime/scope, platform, deadline, budget/tool constraints, and desired outputs.
- Existing story, music, art, 3D, animation, vocal, brand, or technical assets.
- Quality bar: sketch, prototype, demo, portfolio, release, or production-ready.

## Workflow
1. **Define the north star**
   - Write one sentence for the audience experience.
   - List deliverables, constraints, and non-goals.
2. **Split into tracks**
   - Story: premise, beats, script, continuity.
   - Music: tempo map, arrangement, stems, mix.
   - Vocals: lyrics, melody, OpenUtau/voicebank plan, renders.
   - Visuals: art direction, palettes, style frames.
   - Graphics: titles, logos, posters, UI, captions.
   - 3D: assets, scenes, materials, camera.
   - Animation: storyboard, timing, renders, assembly.
3. **Create an asset manifest**
   - For each asset: id, owner/skill, source path, export path, format, status, license, dependencies, and validation.
   - Use deterministic filenames and version suffixes.
4. **Plan phases and gates**
   - Phase 1: concept lock.
   - Phase 2: rough assets and animatic/audio sketch.
   - Phase 3: production assets.
   - Phase 4: integration/render/mix.
   - Phase 5: review/export/archive.
5. **Manage continuity across modalities**
   - Keep motifs synchronized: story theme, musical motif, color palette, shapes, motion language, vocal persona.
   - Record what is canon versus exploratory.
6. **Integrate and verify**
   - Check audio sync, frame rate, codec, color, captions, credits, licenses, and platform delivery specs.

## Output
- Production plan, asset manifest, phase checklist, and per-skill handoff prompts.
- Optional folder structure and naming convention for the project.

## Quality Checks
- Every asset traces back to the north star or a platform requirement.
- Dependencies are explicit before production starts.
- The rough integrated prototype appears before polish.
- Final exports are reproducible from saved sources.

## Related Skills
- `creative-storycraft`
- `music-composition-arrangement`
- `openutau-synthetic-vocals`
- `visual-concept-art-direction`
- `graphics-asset-production`
- `blender-3d-modeling`
- `animation-production`
- `native-music-synthesis`
- `voice-tts` (`skill:voice-tts`) — Voxx + Kokoro TTS for spoken vocal synthesis
- `autotune` (`skill:autotune`) — pitch correction and audio effects pipeline
