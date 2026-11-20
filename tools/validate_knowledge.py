"""Checks everything in knowledge/ and eval/characters/ (plan section 10.1 step D, section 15).
Run from the project folder:  py tools/validate_knowledge.py        (add -v to list every warning)
Exit code 1 if there are errors. Warnings never fail the run."""
import json, re, sys, pathlib

sys.stdout.reconfigure(encoding="utf-8")
ROOT = pathlib.Path(__file__).resolve().parent.parent
K = ROOT / "knowledge"
VERBOSE = "-v" in sys.argv
errors, warnings = [], []
def err(where, msg): errors.append(f"{where}: {msg}")
def warn(where, msg): warnings.append(f"{where}: {msg}")

try:
    import jsonschema
except ImportError:
    print("jsonschema is missing: py -m pip install jsonschema"); sys.exit(2)

def load(p):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except Exception as e:
        err(p.relative_to(ROOT), f"not valid JSON ({e})"); return None

def schema(name):
    return jsonschema.Draft202012Validator(load(K / "schema" / f"{name}.schema.json"))

def check_schema(v, doc, where):
    for e in sorted(v.iter_errors(doc), key=lambda e: list(e.path)):
        path = "/".join(str(x) for x in e.path) or "(root)"
        err(where, f"{path}: {e.message[:160]}")

def lum(hexc):
    def ch(c):
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hexc[i:i + 2], 16) for i in (1, 3, 5))
    return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
INK = lum("#0b0b10")
def contrast(hexc):
    a, b = sorted([lum(hexc), INK], reverse=True)
    return (a + 0.05) / (b + 0.05)

# ------------------------------------------------------------------ global files
for f in ["system_prompt_template.txt", "narrator_prompt.txt", "content_policy.md", "image_style.md", "agent_brief.md"]:
    if not (K / "global" / f).exists(): err(f"global/{f}", "missing")
wr = load(K / "global" / "word_rules.json") or {"rules": []}
fill = {"{speaker_names}": "Alice|Bob", "{confusable}": "Sasuke", "{other_names}": "Sakura", "{user_name}": "Alex"}
for r in wr["rules"]:
    for key in ("pattern", "reply_ok", "reply_bad"):
        if key in r:
            p = r[key]
            for a, b in fill.items(): p = p.replace(a, b)
            try: re.compile(p)
            except re.error as e: err(f"word_rules/{r['code']}", f"{key} does not compile: {e}")
jp = load(K / "global" / "judge_prompts.json") or {"checks": []}
for c in jp["checks"]:
    if c["answers"] and len({a[:6] for a in c["answers"]}) != len(c["answers"]):
        err(f"judge_prompts/{c['id']}", "two answers share the same first 6 letters")

# ------------------------------------------------------------------ roster
roster = (load(K / "roster.json") or {}).get("characters", [])
if len(roster) != 100: err("roster.json", f"{len(roster)} characters, expected 100")
by_file = {r["file"]: r for r in roster}
glyphs = {}
for r in roster:
    if r["glyph"] in glyphs: err("roster.json", f"glyph {r['glyph']} used by #{glyphs[r['glyph']]} and #{r['number']}")
    glyphs[r["glyph"]] = r["number"]
    c = contrast(r["accent"])
    if c < 3.0: err("roster.json", f"#{r['number']} {r['name']} accent {r['accent']} contrast {c:.2f} < 3.0 on #0b0b10")
roster_series = sorted({r["series"] for r in roster})

# ------------------------------------------------------------------ series sheets
sv = schema("series")
sheets = {}
for p in sorted((K / "series").glob("*.json")):
    d = load(p)
    if d is None: continue
    where = f"series/{p.name}"
    check_schema(sv, d, where)
    sheets[p.stem] = d
    if d.get("id") != p.stem: err(where, f"id '{d.get('id')}' does not match the file name")
    want = sorted(r["file"] for r in roster if r["series"] == p.stem)
    have = sorted(d.get("characters_in_app", []))
    if have != want: err(where, f"characters_in_app {have} should be {want}")
    if any(s.get("checked") == "unchecked" for s in d.get("sources", [])): warn(where, "sources unchecked")
    if d.get("verify"): warn(where, f"{len(d['verify'])} verify item(s)")
    for ev in d.get("shared_canon_events", []):
        if ev != ev.lower(): err(where, f"shared_canon_events must be lower-case: '{ev}'")
missing = [s for s in roster_series if s not in sheets]
extra = [s for s in sheets if s not in roster_series]
if missing: warn("series/", f"{len(missing)} of {len(roster_series)} sheets not written yet: {', '.join(missing)}")
if extra: err("series/", f"sheets with no roster characters: {', '.join(extra)}")

# ------------------------------------------------------------------ character packs, tests, image prompts
cv, tv = schema("character"), schema("tests")
PROMPT_KEYS = ["persona", "abilities"]
packs = 0
for p in sorted((K / "characters").glob("*.json")):
    d = load(p)
    if d is None: continue
    packs += 1
    where = f"characters/{p.name}"
    check_schema(cv, d, where)
    r = by_file.get(p.stem)
    if not r: err(where, "file name is not in roster.json"); continue
    for key in ("number", "id", "series", "accent", "glyph"):
        if d.get(key) != r[key]: err(where, f"{key} '{d.get(key)}' != roster '{r[key]}'")
    if d.get("age_band") != r["age_band"]:
        (warn if r["age_band_confirm"] else err)(where, f"age_band '{d.get('age_band')}' != roster '{r['age_band']}' (update the plan table if the agent is right)")
    sp = d.get("speech", {})
    prompt_part = " ".join([d.get("persona", ""), d.get("abilities", ""), sp.get("style", ""),
                            " ".join(d.get("personality", [])), " ".join(d.get("facts", [])),
                            " ".join(x.get("name", "") + ": " + x.get("line", "") for x in d.get("relationships", [])),
                            " ".join(d.get("likes", [])), " ".join(d.get("dislikes", [])),
                            " ".join(d.get("boundaries", [])), " ".join(d.get("secrets", []))])
    n = len(prompt_part)
    if n > 3600: err(where, f"prompt part {n} chars (limit ~3,200)")
    elif n > 3200: warn(where, f"prompt part {n} chars (limit ~3,200)")
    for ev in d.get("canon_events", []):
        if ev != ev.lower(): err(where, f"canon_events must be lower-case: '{ev}'")
    if any(s.get("checked") == "unchecked" for s in d.get("sources", [])): warn(where, "sources unchecked")
    if d.get("verify"): warn(where, f"{len(d['verify'])} verify item(s)")
    if not (K / "characters" / f"{p.stem}.md").exists(): err(where, "cheat sheet .md missing")
    if not (K / "image_prompts" / f"{p.stem}.md").exists(): err(where, "image prompt .md missing")
    t = ROOT / "eval" / "characters" / f"{p.stem}.tests.json"
    if not t.exists(): err(where, "tests file missing")
    else:
        td = load(t)
        if td is not None:
            check_schema(tv, td, f"eval/{t.name}")
            if td.get("id") != p.stem: err(f"eval/{t.name}", "id does not match the file name")

# ------------------------------------------------------------------ report
if "--only" in sys.argv:
    # --only 022_monkey_d_luffy,023_roronoa_zoro : show just those characters' lines (saves agent tokens)
    names = sys.argv[sys.argv.index("--only") + 1].split(",")
    errors = [e for e in errors if any(n in e for n in names)]
    warnings = [w for w in warnings if any(n in w for n in names)]
    for n in names:
        if n not in by_file: errors.append(f"--only: '{n}' is not a roster file name")
    print(f"checked only: {', '.join(names)}")
else:
    print(f"roster {len(roster)} · series sheets {len(sheets)}/{len(roster_series)} · character packs {packs}/100")
print(f"errors: {len(errors)} · warnings: {len(warnings)}")
for e in errors: print("  ERROR  " + e)
shown = warnings if VERBOSE else [w for w in warnings if "verify item" not in w and "unchecked" not in w]
for w in shown: print("  warn   " + w)
hidden = len(warnings) - len(shown)
if hidden: print(f"  (+{hidden} verify/unchecked warnings; run with -v to list them)")
sys.exit(1 if errors else 0)
