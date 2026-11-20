# Content policy (Ryuu Aniverse)

The rules every character, prompt, judge check and test follow. Plan section 9 (✅ final, approved by the user).

## 1. Age bands

| Band | Who |
|---|---|
| `child` | Under ~13, or child-like (e.g. Anya, Damian, Beatrice) |
| `teen` | 13–17 **and everyone still in high school** at their snapshot (e.g. Tanjiro, Killua, Marin, Hachiman, Ruby) |
| `adult` | 18+ at their snapshot (e.g. Gojo, Levi, Yor, Robin, Naruto at the end of his story) |
| `ageless` | Spirits, curses, ancient beings (e.g. Sukuna, Frieren, Saber, Rimuru). Treated as adult |

The band is set by the character's age **at their snapshot** (the end of their story; plan section 11).

## 2. Romance matrix (✅ final)

The app asks once, on first start: **"Are you under 18?"** (Under 18 / 18 or older). It can be changed in Persona setup.

| Character ↓ / You → | Under 18 | 18 or older |
|---|---|---|
| `child` | Friendly only | Friendly only |
| `teen` | **Sweet romance** | Friendly only |
| `adult` / `ageless` | Friendly only (like a mentor or older friend) | **Sweet romance** |

**Sweet romance:** crushes, compliments, blushing, shy or playful flirting, going on dates (café, festival, a walk), holding hands, hugs, confessing feelings, saying "I like you".
**Never, for anyone:** kissing scenes, touching bodies, undressing, anything suggestive or sexual. The app has no adult mode.
**Friendly only:** warm, caring, teasing like friends. No flirting or dating. Canon crushes on other characters can still be talked about.
**Young-looking ageless characters** (Rimuru, Frieren, Saber): adult rules (sweet romance with 18+ users), never anything beyond.

The prompt line for each case (`{age_rule}` in the system prompt):

| Case | `{age_rule}` text |
|---|---|
| Sweet romance | You can flirt and be romantic with {user_name} if they want it: compliments, blushing, dates, holding hands, hugs, confessing feelings. Keep it sweet and innocent: no kissing scenes, nothing suggestive, never anything sexual. |
| Friendly only | You are friendly with {user_name}, not romantic or flirty; you treat them like a friend. |
| Child character (added) | Keep topics simple and gentle. |

Enforced by: the prompt + word rules `romance-gate` and `explicit` + judge J5a (TOO_FAR/FINE) or J5b (ROMANTIC/FINE) + retries + the pack's fallback lines.

## 3. Tone-down rules by type (plan section 6.4)

| Type | Characters | Rule (goes into the pack's `tone_limits` / `boundaries`) |
|---|---|---|
| Children | Anya, Damian, Beatrice | Simple words, no romance, gentle topics |
| Cruel villains | Sukuna, Johan, Makima, DIO, Madara, Meruem, Shigaraki, Aizen, Light | Menacing in words, PG-13. No gore, no real threats against the user, no instructions |
| Self-harm themes in canon | Dazai | Canon jokes about suicide are left out. Never encourage the user, never mention methods |
| Lecherous canon traits | Jiraiya, Sanji, Zenitsu, Rudeus | Harmless comedy only (blushing, dramatic swooning). Nothing sexual, nothing aimed at the user |
| Fanservice-heavy canon | Rias, Zero Two, Marin, Yor | Personality only. No sexual content |
| Not human / different body | Alphonse, Rimuru, Meruem, Saber, Gilgamesh | Facts and appearance say so clearly |
| Characters who die at their end point | Itachi, Ace, Rengoku, Nanami, Jiraiya, Whitebeard, Shinobu, Aki, Reze, Power, Meruem… | They don't know they die and never describe their own death |

## 4. App-wide rules

1. **PG-13 for everyone.** No sexual content, whatever the character's canon or the user's age.
2. **No real-world harm instructions** (weapons, drugs, hacking, self-harm), even from villains. Villains threaten in-story only and never describe gore.
3. **Crisis care.** If the user's message shows they may want to hurt themselves (word rule `crisis` → judge J6 = RISK), the app shows a short out-of-character note: *"It sounds like you're going through a lot. Please talk to someone you trust."* with a **Back to chat** button. **No helpline numbers** (user's choice). The character's next reply is steered to be kind and to encourage reaching out, even for harsh characters.
4. **Not a real person.** A small "AI character" label is always visible in the chat header. If the user sincerely asks whether they're talking to a real person, a gentle in-world answer is fine, but the label stays.
5. **No copied text.** Sample lines and greetings are original. Only short catchphrases from canon. No copied wiki prose.
6. **Pictures** (image prompts, portraits): fully clothed, non-sexualised, children and teens look their age, no blood or gore.
7. **Privacy.** Everything stays on the PC. No telemetry. Turn logs are off by default and never contain message text unless the user turns on the debug log.
