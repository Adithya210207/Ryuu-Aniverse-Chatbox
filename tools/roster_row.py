"""Prints one roster row (what a Character Agent needs) so agents don't have to read the whole roster.
Run:  py -I tools/roster_row.py 37"""
import json, sys, pathlib
sys.stdout.reconfigure(encoding="utf-8")
root = pathlib.Path(__file__).resolve().parent.parent
rows = json.loads((root / "knowledge" / "roster.json").read_text(encoding="utf-8"))["characters"]
n = int(sys.argv[1])
r = next(c for c in rows if c["number"] == n)
r = dict(r)
r["note"] = ("ONGOING series: snapshot = latest published arc as of today" if r["ongoing"] else "finished: snapshot = end of the story") + \
            (" | CONFIRM the age band at the snapshot" if r["age_band_confirm"] else "")
print(json.dumps(r, ensure_ascii=False, indent=1))
