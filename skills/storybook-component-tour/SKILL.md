---
name: storybook-component-tour
description: "Walk through all components in a Storybook instance, capture screenshots with dark mode, and publish a comprehensive tour to Discord. Use when the user wants to showcase or document a component library."
compatibility: pi,opencode
metadata:
  domain: documentation
  workflow: storybook-tour
  version: 1
---

# Skill: Storybook Component Tour

## Goal
Generate a comprehensive visual tour of all components in a Storybook instance, capturing screenshots with proper dark mode theming and publishing organized posts to Discord.

## Use This Skill When
- User asks to "tour the components", "showcase the library", or "walk through Storybook"
- User wants to document or share a component library
- User says "tour all the stories" or similar
- After completing UI work that should be showcased

## Do Not Use This Skill When
- Storybook is not running or accessible
- User only wants to see one specific component
- Credentials for Discord are not configured

## Prerequisites
- Storybook running and accessible (default: `http://localhost:6006`)
- `agent-browser` skill available for browser automation
- Discord credentials configured (`OPENHAX_DISCORD_TOKEN`)

## Workflow

### 1. Discover Components

```bash
# Find all story files
find <storybook-path> -name "*.stories.tsx" | sort

# For each story file, discover exported story names
grep -h "export const" <story-file> | head -5
```

**Critical**: Story names don't follow a consistent pattern. Always grep for actual exported names:
- Some use `--all-variants`, others use `--default`, `--variants`, `--sizes`, etc.
- Don't assume naming conventions.

### 2. Capture Screenshots

For each component:

```bash
# Open story with dark mode
agent-browser open "http://localhost:6006/?path=/story/<category>-<component>--<story-name>"
sleep 1
agent-browser set media dark
agent-browser screenshot "/tmp/tour/<component>.png"
```

**Dark Mode**: Always run `set media dark` after `open` to ensure UI chrome theming.

### 3. Verify Screenshots

Before posting, verify each screenshot shows actual content:

```
analyze_image({
  source: "/tmp/tour/<component>.png",
  prompt: "Is this showing a proper <Component> component? Briefly describe."
})
```

**Error Detection**: Look for Storybook error pages like "Couldn't find story matching..."
- These indicate wrong story names
- Re-capture with correct story name from grep output

### 4. Organize Tour Structure

Group components by category:

```
Category 1: Primitives (8)
  - Badge
  - Button
  - Card
  ...

Category 2: AI IDE (5)
  - Chat
  - CommandPalette
  ...

Category 3: KMS IDE (4)
  ...

Category 4: Text Editors (2)
  ...
```

### 5. Post to Discord

Post in sequence:

1. **Tour announcement** - Overview of what's coming
2. **Category header** - "## 🧱 Primitives (8 components)"
3. **Component posts** - One image + description per component
4. **Summary post** - Table of all components with links

**Component Post Format**:
```
## 🧱 Primitives (1/8)

**Badge** - Status indicators and labels
• Semantic variants: Default, Success, Warning, Error
• With Dot for status indicators
• Multiple sizes and outline style
```

**Summary Post Format**:
```
## 🎬 Tour Complete!

**N components** across M categories:

| Category | Count | Components |
|----------|-------|------------|
| Primitives | 8 | Badge, Button, Card, ... |
| AI IDE | 5 | Chat, CommandPalette, ... |
...

**Running at:** http://localhost:6006
```

## Story URL Pattern

```
http://localhost:6006/?path=/story/<category>-<component>--<story>
```

Examples:
- `primitives-badge--all-variants`
- `ai-ide-chat--default`
- `kms-ide-codeblock--typescript`
- `text-editors-markdowneditor--default`

## Error Recovery

| Error | Cause | Fix |
|-------|-------|-----|
| "Couldn't find story" | Wrong story name | Grep for actual exported names |
| Blank/white screenshot | Page not loaded | Increase sleep time |
| Light mode UI chrome | Dark mode not set | Run `set media dark` after `open` |
| Component not visible | Story requires interaction | Use interaction-specific story |

## Tools Used

- `agent-browser` - Browser automation for navigation and screenshots
- `analyze_image` - Verify screenshots before posting
- `discord` - Post images and descriptions to channel

## Example Session

```
User: "Walk through all the stories for each component type in Storybook"

Agent:
1. Discovers 19 story files
2. Greps story names for each file
3. Captures screenshots with dark mode
4. Verifies each screenshot
5. Posts organized tour to Discord:
   - 4 category headers
   - 19 component posts
   - 1 summary table
```

## Output

- Complete visual documentation of component library
- Organized Discord posts for team reference
- Verified screenshots (no broken/error states)
- Summary table with component counts

## References
- `agent-browser` skill for browser automation details
- `discord-publish-tool` skill for Discord posting
- `analyze_image` tool for screenshot verification
