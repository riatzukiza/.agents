---
name: animation-production
description: Plan and produce animation with timing, storyboards, animatics, keyframes, rigs, motion graphics, renders, ffmpeg assembly, and review loops.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: animation-production
  version: 1
---

# Skill: Animation Production

## Goal
Turn motion intent into an executable animation plan, storyboard, animatic, Blender/2D implementation, rendered sequence, or video assembly.

## Use This Skill When
- The user asks for animation, motion graphics, storyboards, animatics, keyframes, camera moves, rigging plans, or video loops.
- The user needs to sync motion with music, vocals, dialogue, UI states, or scene beats.
- The deliverable may involve Blender, SVG/Canvas, CSS, frame sequences, or ffmpeg.

## Do Not Use This Skill When
- The task is only static 3D modeling; use `blender-3d-modeling`.
- The task is only static graphics; use `graphics-asset-production`.
- The user asks for deepfake or deceptive impersonation; refuse or redirect to non-deceptive animation.

## Inputs
- Duration, frame rate, aspect ratio, target platform, style, and delivery format.
- Assets available or needed: characters, props, backgrounds, logos, audio, subtitles.
- Timing constraints: beats, dialogue, lyrics, UI events, loops, cuts, or camera moves.

## Workflow
1. **Create the timing spine**
   - Define FPS, total frames, scene/shot list, beat markers, and loop seam if any.
   - Map emotional or informational beats to timestamps.
2. **Storyboard before polish**
   - Describe each shot: framing, action, camera, transition, text, and audio cue.
   - Build an animatic or timing table before full rendering.
3. **Choose production method**
   - Blender for 3D/camera/lighting/rigged motion.
   - SVG/Canvas/CSS for UI, vector, or procedural motion graphics.
   - ffmpeg for assembly, trims, overlays, captions, audio sync, and format conversion.
4. **Animate from broad to fine**
   - Block poses/camera first.
   - Add anticipation, action, follow-through, easing, holds, and secondary motion.
   - Keep curves editable and name key objects/tracks.
5. **Render and assemble**
   - Render test frames before full sequences.
   - Use deterministic output folders and filenames.
   - Assemble with audio, captions, and color management as needed.
6. **Review loop**
   - Check timing, readability, continuity, flicker, audio sync, compression, and loop seam.

## Output
- Storyboard/timing sheet, animation source, rendered frames/video, and assembly commands or scripts.
- Notes on FPS, resolution, codecs, assets, and known constraints.

## Quality Checks
- The motion reads in silhouette and at target size.
- Every move has purpose: story, emotion, usability, or rhythm.
- Audio and visual beats align within the intended tolerance.
- The exported video matches target platform requirements.
