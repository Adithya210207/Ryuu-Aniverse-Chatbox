"""Builds one self-contained, paste-ready ChatGPT prompt per character (style rules + that character's look) from the
image_prompts/NNN_id.md files. Output: knowledge/image_prompts/standalone/NNN_id.md (no need to send a style setup first).
Run:  py -I tools/build_standalone_prompts.py"""
import re, pathlib, sys
sys.stdout.reconfigure(encoding="utf-8")
root = pathlib.Path(__file__).resolve().parent.parent / "knowledge" / "image_prompts"
out = root / "standalone"
out.mkdir(exist_ok=True)

def block(text, heading):
    m = re.search(r"^## " + re.escape(heading) + r"\s*\n```\n(.*?)\n```", text, re.S | re.M)
    return m.group(1).strip() if m else None

n = 0
for f in sorted(root.glob("[0-9][0-9][0-9]_*.md")):
    t = f.read_text(encoding="utf-8")
    style, char = block(t, "Style setup"), block(t, "Character prompt")
    if not style or not char:
        print("SKIP (missing block):", f.name); continue
    style = re.sub(r"If I ask for more than one in this chat,\s*keep reusing the style of the first picture\.", "", style)
    style = re.sub(r"\s+", " ", style.split("Rules for EVERY picture:")[0]).strip()
    rules = re.search(r"Rules for EVERY picture:\n(.*)", block(t, "Style setup"), re.S).group(1).strip()
    text = (f"{style}\n\nCreate ONE image now, following these rules:\n\n{rules}\n\nThe character:\n{char}\n\n"
            "Make the picture and nothing else: no text reply needed beyond the image.\n")
    (out / (f.stem + ".md")).write_text(text, encoding="utf-8")
    n += 1
print(n, "prompts written to", out)
