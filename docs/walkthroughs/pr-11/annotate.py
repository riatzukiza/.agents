#!/usr/bin/env python3
"""Annotate copies; retain separate raw captures unchanged."""
from pathlib import Path
import hashlib
import json
import textwrap
from datetime import datetime, timezone
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
PALETTE = ["#64d8a1", "#7fc5ff", "#ffca75"]
RUNTIME_HASHES = {
    "test-espanso-regression": "0ac49e7eb6e85418613a115cd51a1c306ad769b197d42e5674f63cb4654fe6a3",
    "agent-entrypoint": "942f651e8cb2c745b64929c3f99e82bde2f48378f866e80d4b6ccf9545d063e8",
    "wait-emacs": "1851a5455f127b2a431fa06507292fac89905ce08572e9220fd26c60ff30d9fa",
}
SHOTS = [
    ("01-pr-merged", "01 / Merged implementation and its explicit fidelity boundary", [
        ((105, 205, 1250, 291), (1290, 250), "Merged PR #11: reviewed implementation is head 8a173d2; merge is 9812965f."),
        ((176, 463, 980, 561), (120, 503), "Design: extract a reusable isolated toolkit, add focus-checked tests and refusal-first installation."),
        ((172, 744, 983, 932), (120, 786), "Accepted debt: old core versions, incomplete optional Spacemacs bootstrap, and donor compatibility copy remain explicit."),
    ]),
    ("02-pr-checks", "02 / Hosted core-sandbox checks: evidence, not blanket parity", [
        ((381, 305, 750, 356), (1120, 330), "The exact-head sandbox CI job completed successfully; this capture shows GitHub's actual job summary."),
        ((385, 455, 1065, 574), (1120, 520), "Executed gates include installer/readiness checks, core desktop startup, and i3/Espanso regressions."),
        ((385, 579, 1350, 694), (320, 620), "Artifacts and teardown are covered. Green core CI does not qualify full Spacemacs, candidate mounts, or the live host."),
    ]),
    ("03-unicode-runtime", "03 / Fresh isolated Xvfb run: automatic Unicode versus forced keys", [
        ((7, 0, 88, 38), (130, 23), "Actual Emacs buffer after guarded :sandbox-unicode expansion: mu and circular arrow rendered successfully."),
        ((7, 386, 1252, 452), (1120, 490), "Actual fresh forced-key test log: missing vkey mapping for mu. Failure is a diagnostic, not a universal Unicode fix."),
        ((120, 355, 640, 384), (1000, 340), "This is core Emacs on container display :99, not the operator's Spacemacs desktop or proof of clipboard-timeout causality."),
    ]),
    ("04-installer-tests", "04 / Fresh installer refusal tests, displayed in sandbox Emacs", [
        ((6, 41, 950, 157), (1110, 92), "Unowned installs and symlink ancestors are refused; exact managed reinstallation is idempotent."),
        ((6, 161, 1140, 270), (1190, 210), "Intentional checksum mismatch proves local edits are refused. Source overlap is refused too."),
        ((6, 273, 1050, 357), (1110, 338), "Trailing-slash symlink, preserved extras, concurrent install and staging cleanup tests passed. Hostile ancestor replacement races remain outside the guarantee."),
    ]),
]

manifest = {
    "subject": "https://github.com/riatzukiza/.agents/pull/11",
    "reviewed_head": "8a173d2438b787675f1faf54ba78f7000beb098b",
    "merge": "9812965f071a07fe2e79c9b2c2656bcfacb8e8ce",
    "annotation_generated_at": datetime.now(timezone.utc).isoformat(),
    "capture_boundary": "Fresh headless public GitHub browser and non-root Xvfb container; no host desktop capture.",
    "capture_image": "sha256:a0b7c17de98f00fa97f90b7ade732ac29fd202aafa33c03e7d955ffabbf9bf70",
    "screenshots": [],
    "runtime_script_comparisons": [],
}
for name, observed in RUNTIME_HASHES.items():
    source = ROOT.parents[2] / "skills/agent-i3-sandbox/scripts" / name
    actual = hashlib.sha256(source.read_bytes()).hexdigest()
    assert actual == observed, f"runtime/source mismatch: {name}"
    manifest["runtime_script_comparisons"].append({
        "source_path": f"skills/agent-i3-sandbox/scripts/{name}",
        "container_path": f"/home/agent/bin/{name}",
        "source_sha256": actual, "observed_container_sha256": observed,
        "comparison_command": f"docker exec pr11-walkthrough-capture sha256sum /home/agent/bin/{name}",
    })
for stem, title, callouts in SHOTS:
    raw_path = ROOT / "raw" / f"{stem}.png"
    raw = Image.open(raw_path).convert("RGB")
    width, height = raw.size
    banner, footer = 62, 260
    image = Image.new("RGB", (width, height + banner + footer), "#111c2a")
    image.paste(raw, (0, banner))
    draw = ImageDraw.Draw(image)
    draw.text((22, 19), title, font=ImageFont.truetype(FONT, 23), fill="#f4f7fc")
    font = ImageFont.truetype(FONT, 19)
    cursor = height + banner + 18
    for number, (box, badge, explanation) in enumerate(callouts, 1):
        color = PALETTE[number - 1]
        x1, y1, x2, y2 = box
        draw.rounded_rectangle((x1, y1 + banner, x2, y2 + banner), 6, outline=color, width=3)
        bx, by = badge[0], badge[1] + banner
        draw.line((bx, by, x2 if bx > x2 else x1, (y1 + y2) / 2 + banner), fill=color, width=2)
        draw.ellipse((bx - 17, by - 17, bx + 17, by + 17), fill=color)
        draw.text((bx - 7, by - 12), str(number), font=font, fill="#111c2a")
        wrapped = textwrap.wrap(explanation, width=max(70, (width - 80) // 10))
        draw.text((23, cursor), f"{number}.", font=font, fill=color)
        for line in wrapped:
            draw.text((55, cursor), line, font=font, fill="#e8edf5")
            cursor += 26
        cursor += 9
    draw.text((23, height + banner + footer - 29), "Raw capture files preserved separately; these copies add explanatory overlays.", font=ImageFont.truetype(FONT, 15), fill="#a6b8cb")
    output = ROOT / "annotated" / f"{stem}.png"
    image.save(output, optimize=True)
    manifest["screenshots"].append({
        "raw": f"raw/{stem}.png", "raw_sha256": hashlib.sha256(raw_path.read_bytes()).hexdigest(),
        "annotated": f"annotated/{stem}.png", "annotated_sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
        "raw_dimensions": list(raw.size), "annotations": [x[2] for x in callouts],
    })
(ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
