# knowledge/

Everything the brain (Stheno), the judge (phi4-mini) and the word rules know. The app reads this folder at start-up; edit + restart is enough (no rebuild).

## Folders

| Path | What | Made in |
|---|---|---|
| `global/system_prompt_template.txt` | The character system prompt with `{placeholders}` (plan section 6) | Phase 1 |
| `global/narrator_prompt.txt` | The action-line (narrator) prompt (plan 8.5) | Phase 1 |
| `global/judge_prompts.json` | Judge checks J1–J7 (J5a/J5b), retry reminders, retry policy, crisis note (plan 8.3–8.4) | Phase 1 |
| `global/word_rules.json` | Word rules: patterns, actions, hints (plan 8.2) | Phase 1 |
| `global/content_policy.md` | Age bands, romance matrix (final), tone-down rules, app-wide rules (plan 9) | Phase 1 |
| `global/persona_template.json` | Shape of the user's `persona.json` | Phase 1 |
| `global/image_style.md` | Shared ChatGPT portrait rules + per-character file layout (plan 7.7) | Phase 1 |
| `global/agent_brief.md` | Instructions for the 100 Character Agents and 10 reviewers (plan 10) | Phase 1 |
| `schema/*.schema.json` | Shapes of character packs, series sheets and tests | Phase 1 |
| `series/<id>.json` | 41 series sheets | Phase 2 |
| `characters/NNN_<id>.json` / `.md` | 100 knowledge packs + cheat sheets | Phase 4 |
| `image_prompts/NNN_<id>.md` | 100 ChatGPT portrait prompts | Phase 4 |

Tests per character live in `../eval/characters/NNN_<id>.tests.json`.

## How the system prompt is filled (`system_prompt_template.txt`)

| Placeholder | Filled with |
|---|---|
| `{name}` | pack `name` |
| `{series_title}` | series sheet `title` |
| `{snapshot}` | pack `snapshot` |
| `{persona}` | pack `persona` |
| `{personality}` | pack `personality`, joined with ", " |
| `{speech_style}` | pack `speech.style` |
| `{address_user}` | pack `speech.address_user.default` (with `{user_name}` filled in) |
| `{tics_line}` | `" You sometimes say: " + tics joined with ", " + "."`, or empty |
| `{abilities}` | pack `abilities` |
| `{facts_lines}` | pack `facts`, one `- ` line each |
| `{relationship_lines}` | pack `relationships`, `- Name: line` each |
| `{likes}`, `{dislikes}` | joined with ", " |
| `{boundary_lines}` | pack `boundaries`, one `- ` line each |
| `{secrets_block}` | `"\nWhat you keep secret (never reveal it, deflect in your own way):\n" + "- " lines`, or empty |
| `{age_rule}` | romance matrix line (`content_policy.md` section 2) |
| `{tone_lines}` | pack `tone_limits.rules` as `- ` lines, or empty |
| `{persona_block}` | from `persona.json` (`persona_template.json` notes) |
| `{scene_block}` | the chosen scene, default pack `default_scene` |
| `{nexus_line}` | empty; Nexus mode (Phase 9) adds a line |
| `{user_name}` | persona name, or "the person you're talking with" |

The order is fixed (plan 6.1): the parts that never change come first, so Ollama can reuse its cache; the persona and scene come last.

## Placeholders in judge prompts and hints

`{name}`, `{series_title}`, `{user_name}`, `{user_line}`, `{reply}`, `{facts_lines}`, `{fact}`, `{appearance}`, `{cue}`, `{notes}`, `{turns}`. Quotes inside user text, replies and cues are replaced with `'` before filling.

## Rules for editing

- `judge_prompts.json` and `word_rules.json` are **generated**: edit `tools\build_global_json.py`, then run `py tools\build_global_json.py knowledge\global` (it compile-checks every pattern and runs 15 behaviour checks). Patterns use Java regex syntax with flags like `(?i)` at the start.
- After changing judge wording, run `py tools\judge_smoke.py .` (29 quick cases on phi4-mini; starts and stops the private server).
- Run `py tools\validate_knowledge.py` after editing packs or sheets (made in Phase 2).
- Don't put real story text (subtitles, wiki prose) in any file; only original wording and short catchphrases.
