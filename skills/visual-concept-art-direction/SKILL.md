---
name: visual-concept-art-direction
description: Develop visual concepts, art direction, palettes, composition plans, prompt packs, and critique loops for illustrations, worlds, characters, props, and scenes.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: visual-concept-art
  version: 1
---

# Skill: Visual Concept Art Direction

## Goal
Translate narrative or product intent into coherent visual direction that can guide illustration, image generation, modeling, graphics, or animation.

## Use This Skill When
- The user asks for concept art, visual design, moodboards, image prompts, character looks, environments, props, style frames, or art critique.
- The user needs consistent visual language across story, music, 3D, animation, or graphic assets.
- The user provides a vague aesthetic and wants it sharpened into executable art direction.

## Do Not Use This Skill When
- The user only needs final vector/raster production; use `graphics-asset-production`.
- The user only needs Blender mesh implementation; use `blender-3d-modeling`.
- The request depends on an exact living-artist style clone; convert to non-identifying traits.

## Inputs
- Subject, world, mood, audience, medium, aspect ratio, and intended use.
- Required motifs, brand/story constraints, accessibility constraints, and forbidden elements.
- Optional references expressed as traits: palette, lighting, composition, line quality, material, era.

## Workflow
1. **Extract the visual thesis**
   - State the intended emotional read in one sentence.
   - Choose 3-5 governing traits: silhouette, palette, texture, lighting, composition, symbolism.
2. **Build an art-direction board in text**
   - Palette with hex colors or color roles.
   - Shape language: circles, triangles, monoliths, filigree, broken geometry, etc.
   - Material language: skin, cloth, chrome, paper, stone, glass, fur, smoke, light.
   - Camera and composition: lens feel, framing, depth, focal hierarchy.
3. **Create design variants**
   - Produce at least three distinct directions when exploring.
   - For each variant list strengths, risks, and best use case.
4. **Write production prompts or briefs**
   - Include subject, action, environment, lighting, palette, composition, medium, constraints, negative space, and exclusions.
   - Keep prompts modular so details can be swapped without losing the core direction.
5. **Critique and iterate**
   - Evaluate clarity, silhouette, value grouping, color harmony, symbolism, and continuity with the story bible.
   - Identify what to preserve before changing anything.

## Output
- Art-direction brief, prompt pack, variant matrix, or critique report.
- Optional handoff notes for graphics, Blender, animation, or image-generation tools.

## Quality Checks
- The first-read silhouette is understandable.
- Palette and lighting serve the emotional thesis.
- Details reinforce hierarchy instead of creating noise.
- The direction is specific enough for another agent or artist to execute.
