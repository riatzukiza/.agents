---
name: creative-director-orchestrator
description: Coordinate complex creative visions by decomposing them into a sequenced pipeline of narrative, sonic, visual, and kinetic sub-skills.
license: GPL-3.0-or-later
compatibility: opencode
metadata:
  audience: agents
  workflow: creative-direction
  version: 1
---

# Skill: Creative Director Orchestrator

## Goal
Act as the high-level strategic lead for multi-medium creative projects, transforming a vague vision into a concrete, sequenced production pipeline across lore, audio, visuals, and motion.

## Use This Skill When
- The user asks for a "project," "world," "series," or a a multi-asset production (e.g., "a short film," "a cohesive music album with art," "a game concept").
- The user provides a complex mood board or a dense narrative seed and wants a plan for manifesting it across different mediums.
- A project requires synchronization between audio and visual assets (e.g., "the visuals should pulse to the bass").

## Do Not Use This Skill When
- The user only wants a single discrete asset (e.g., "write one poem," "draw one character"); use the specific pillar skill instead.
- The user is asking for a technical fix (e.g., "fix this bug in the Blender script").

## Inputs
- High-level vision: The core "vibe," goal, and emotional target.
- Target deliverables: What constitutes "finished" (e.g., 3 videos, 1 album, 1 PDF world bible).
- Constraints: Time, technical limits, platform requirements, or intended audience.
- Existing seeds: Any mood boards, songs, or sketches already present.

## Workflow
1. **Vision Deconstruction**
   - Break the request into its fundamental creative pillars:
     - **Conceptual/Narrative**: Themes, rules, story, identity.
     - **Sonic**: Palette, tempo, timbre, vocal style. → `skill:voice-tts` for spoken TTS synthesis; `skill:autotune` for pitch processing.
     - **Visual**: Aesthetics, color, shape, lighting.
     - **Kinetic**: Pacing, camera movement, timing, VFX.
2. **Dependency Mapping**
   - Identify the "Critical Path." Determine what must be locked before others can start.
     - *Example: Narrative $\rightarrow$ Visual $\rightarrow$ Kinetic $\rightarrow$ Sonic Polish.*
3. **Sequence Generation**
   - Map the project to a chain of specific skills.
     - *Example: creative-storycraft → visual-concept-art-direction → music-composition-arrangement → voice-tts → autotune → animation-production.*
4. **Hand-off Definition**
   - Define the "Interface" between pillars. What precisely must be delivered from one skill to the next?
     - *Example: The World Bible must provide the "Brutalist-Neon" color palette before the Concept Art begins.*
5. **Execution Oversight**
   - Act as the "Consistency Guard." As assets are produced, verify they align with the original vision.
   - Trigger revisions if a sonic element contradicts the visual mood.
6. **Pipeline Finalization**
   - Orchestrate the final assembly via `multimedia-creative-pipeline`.

## Output
- **Master Production Plan (MPP)**: A sequenced list of tasks and skills.
- **Inter-Pillar Contract**: Specifications for hand-offs (e.g., "The audio must be 120bpm for the visualizer sync").
- **Vision Ledger**: A living record of core aesthetic decisions to prevent drift.

## Quality Checks
- Does the plan cover all requested deliverables?
- Is the sequence logical (no "painting before the sketch")?
- Are the hand-offs explicit and usable for the next agent/skill?
- Is there a mechanism for consistency checks across the different media?
