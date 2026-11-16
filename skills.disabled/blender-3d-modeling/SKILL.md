---
name: blender-3d-modeling
description: Create or modify Blender 3D assets with scriptable mesh/blockout workflows, materials, lighting, camera previews, exports, and geometry sanity checks.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: 3d-modeling
  version: 1
---

# Skill: Blender 3D Modeling

## Goal
Turn a 3D asset or scene request into a Blender-ready plan, Python script, `.blend` file, exported model, or render preview.

## Use This Skill When
- The user asks for 3D models, props, environments, characters, procedural meshes, materials, lighting, cameras, or Blender automation.
- The user needs GLB/GLTF, OBJ, FBX, STL, USD, or rendered previews.
- The user wants a scriptable/reproducible model rather than only visual advice.

## Do Not Use This Skill When
- The task is only 2D graphics; use `graphics-asset-production`.
- The task is only visual mood/design; use `visual-concept-art-direction` first.
- The task requires exact CAD/manufacturing tolerances beyond available information; ask for specs or mark assumptions.

## Inputs
- Asset/scene purpose, target platform, scale, units, poly budget, dimensions, and export format.
- Style, references as traits, materials, rigging/animation needs, and rendering requirements.
- Constraints: real-time, 3D print, game engine, web, AR/VR, cinematic, or concept render.

## Workflow
1. **Define the 3D contract**
   - State units, origin, orientation, scale, target format, and success criteria.
   - Decide blockout, low-poly, high-poly, procedural, sculpt-like, or kitbash workflow.
2. **Build in layers**
   - Blockout with primitives first.
   - Add silhouette-defining forms before detail.
   - Separate geometry, materials, lights, cameras, and annotations into named collections.
3. **Use scriptable Blender when possible**
   - Write Python that clears/sets the scene, creates meshes/curves/materials, names objects, sets origins, adds cameras/lights, and saves/exports.
   - Run with `blender --background --python <script.py>` when Blender is available.
4. **Apply materials and lighting**
   - Use simple physically-plausible materials unless the brief asks for stylization.
   - Add preview lighting and camera bookmarks that show the asset clearly.
5. **Validate geometry**
   - Check scale, transforms, normals, non-manifold edges for print assets, object names, and export compatibility.
   - For game/web assets, check triangle count, texture sizes, origin, and bounding box.
6. **Export and preview**
   - Save the `.blend` source and export required formats.
   - Render at least one preview image when possible.

## Output
- Blender source, script, exported model, and/or preview render paths.
- Notes on scale, materials, poly count, rigging readiness, and known limitations.

## Quality Checks
- The model has a readable silhouette from multiple angles.
- Object origins and transforms are sane for downstream tools.
- Materials are named and reusable.
- Exported files reopen without missing assets.
