#!/usr/bin/env python3
"""Render the reviewed walkthrough draft with immutable screenshot URLs."""
import json
import sys
from pathlib import Path

sha = sys.argv[1]
if len(sha) != 40 or any(c not in "0123456789abcdef" for c in sha):
    raise SystemExit("expected full lowercase evidence commit SHA")
root = Path(__file__).resolve().parent
draft = json.loads((root / "walkthrough.json").read_text())
base = f"https://raw.githubusercontent.com/riatzukiza/.agents/{sha}/docs/walkthroughs/pr-11"
evidence = f"https://github.com/riatzukiza/.agents/blob/{sha}/docs/walkthroughs/pr-11"
draft["body"] = draft["body"].replace("{assets}/README.md", evidence + "/README.md").replace("{assets}", base)
for comment in draft["comments"]:
    comment["body"] = comment["body"].replace("{assets}", base)
print(json.dumps(draft, ensure_ascii=False))
