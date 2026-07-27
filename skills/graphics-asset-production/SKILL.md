---
name: graphics-asset-production
description: Produce logos, icons, posters, covers, UI graphics, SVGs, sprite sheets, and export packs with format, accessibility, sizing, and versioning discipline.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: graphics-production
  version: 1
---

# Skill: Graphics Asset Production

## Goal
Create production-ready 2D graphic assets with clear constraints, editable sources, exports, and verification.

## Use This Skill When
- The user asks for logos, icons, banners, posters, album covers, thumbnails, UI graphics, diagrams, sprites, or social cards.
- The user needs SVG, PNG, WebP, PDF, sprite sheet, favicon, or responsive asset variants.
- The task requires layout, typography, color, accessibility, or export discipline.

## Do Not Use This Skill When
- The user only wants conceptual art direction; use `visual-concept-art-direction` first.
- The user wants complex scene animation; use `animation-production`.
- The user wants photoreal 3D assets; use `blender-3d-modeling`.

## Inputs
- Asset type, purpose, audience, dimensions, platform requirements, and deadline.
- Brand/story constraints, copy text, palette, typography constraints, and accessibility needs.
- Required source format and export formats.

## Workflow
1. **Lock the production brief**
   - Define canvas sizes, safe areas, bleed, transparent/background requirements, and target platforms.
   - Decide vector-first, raster-first, or hybrid.
2. **Design the hierarchy**
   - Establish primary read, secondary read, action/copy, and decorative elements.
   - Choose grid, margins, type scale, contrast, and color roles.
3. **Create editable source**
   - Prefer SVG for icons, logos, diagrams, flat graphics, and scalable UI assets.
   - Use HTML/CSS/SVG or scriptable generation when reproducibility matters.
   - Keep text editable until final export unless platform requires outlines.
4. **Export variants**
   - Produce named sizes and formats with deterministic filenames.
   - Include light/dark, monochrome, transparent, and high-DPI variants when relevant.
5. **Verify**
   - Check dimensions, file sizes, transparency, color contrast, text legibility, and edge clipping.
   - Open at thumbnail size and full size.
6. **Document handoff**
   - Record source path, export paths, palette, fonts, and any license constraints.

## Output
- Editable source asset plus export pack.
- Brief notes on usage, colors, typography, and accessibility checks.

## Quality Checks
- The asset reads correctly at its smallest target size.
- Exports match platform dimensions exactly.
- Contrast is sufficient for text and functional marks.
- Source files are organized so future edits are cheap.
