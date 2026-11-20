# Image style rules (ChatGPT portraits)

Shared by all 100 image prompt files (`knowledge/image_prompts/NNN_<id>.md`, plan section 7.7). Each character agent copies the **Style setup** block into its file word for word, so every file works on its own.

## Style setup (send first in a new ChatGPT chat)

```
I'm making a set of matching anime portraits for a private desktop chat app. Each
picture is ONE character from an anime. All pictures must look like one set: same
art style, same framing, same lighting. If I ask for more than one in this chat,
keep reusing the style of the first picture.

Rules for EVERY picture:
- Tall portrait image (1024 x 1536).
- High-quality modern anime illustration, clean line art, soft cel shading with gentle
  painterly light, like a TV anime key visual. Keep the character's canon look so fans
  recognise them instantly (hair, eyes, outfit, signature items).
- Waist-up portrait of one character, centred, facing the viewer.
- The face is in the upper third of the image (eyes about 25% down from the top), with
  only a little space above the head.
- Soft rim light in the character's accent colour. Background: dreamy soft-focus bokeh
  in the accent colour, fading to near-black at the bottom.
- The bottom 40% stays simple and darker (my app puts text there). Nothing important
  near the left and right edges (my app crops the sides).
- Fully clothed, tasteful and non-sexualised. Children and teens look their age.
- No blood, no gore, no weapons pointed at the viewer.
- No text, no logo, no watermark, no frame, no other people.
```

## Layout of each character's file

````markdown
# NNN · Character Name → `NNN_<id>.png`

| | |
|---|---|
| Series | ... |
| Snapshot | ... (the end of their story) |
| Accent | `#RRGGBB` (colour name) |
| Save as | `assets/portraits/NNN_<id>.png` |

## How to use
1. Open a new ChatGPT chat and send the **Style setup** below.
2. Send the **Character prompt**. If you don't like the result, try the **Alternative**.
3. Download the picture, rename it to `NNN_<id>.png`, put it in `assets\portraits\`, restart the app.

## Style setup
```
(copied word for word from knowledge/global/image_style.md)
```

## Character prompt
```
Picture: NAME from SERIES (at the end of their story). Accent colour: name (#RRGGBB).
Look: hair, eyes, face marks, outfit, signature item, as at the snapshot.
Pose: a pose that fits their personality.
Mood: lighting and background feel, in the accent colour.
```

## Alternative
```
(same format, a different pose and mood)
```

## Fixing a picture
- `Same picture, but move them higher so the face is in the top third.`
- `Same picture, but make the background darker at the bottom.`
- `Same picture, but closer to their canon look: <what's wrong>.`

## ⚠️ Check against the anime
| Detail | Why unsure |
|---|---|
| ... | ... |
````

## Writing rules for the character prompt

- **Look at the snapshot.** Describe them as they look at the end of their story (e.g. adult Naruto with the Hokage cloak, Levi with his scars, Miku as an adult). Name the signature items fans expect.
- **Pose and mood** fit the personality. One clear idea, not a scene.
- **No weapons pointed at the viewer**; a sheathed or resting weapon is fine.
- **Canon marks are fine** (scars, Sukuna's markings, Dazai's bandages), described plainly, never as wounds or blood.
- **Never sexualised.** Modest versions of fanservice outfits. Children and teens look their age.
- Put any look detail you're not sure about in the ⚠️ table instead of guessing silently.

## Why the rules (how the app uses the picture)

| Where | What the app does |
|---|---|
| Home Spotlight panels | Crops a tall, narrow slice from the **centre** |
| Gallery cards | Crops 3:4 from the centre-top |
| Bottom 40% | Darkened and covered by the name |
| Rail and chat avatars | Cuts a circle from the **top**, so the face must be high |
| Chat stage (right side) | Full-height portrait with a dark fade on the left and bottom |
| Chat background | Heavily blurred, so the accent colour matters more than detail |

If ChatGPT declines a character, that character keeps the app's generated avatar (accent colour + glyph) until a picture is added.
