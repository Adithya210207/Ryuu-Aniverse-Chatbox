RYUU ANIVERSE  -  v2
One Universe. Infinite Characters. Endless Conversations.

A desktop app where you chat with 100 anime characters. Everything runs on your own computer:
no accounts, no cloud, nothing is sent anywhere.

WHAT YOU NEED
- Windows 10 or 11 (64-bit)
- Ollama (free): https://ollama.com/download  - install it once
- About 12 GB free disk space for the two AI models
- A computer that can run an 8B model: 16 GB RAM, ideally a graphics chip with 8+ GB. Replies take
  about 10-30 seconds; the laptop will run warm while you chat.

FIRST START (once)
1. Install Ollama and start it.
2. Double-click  "Setup models.bat"  and wait. It installs the two AI models (about 11 GB of downloads).
   Already have the Stheno GGUF file? Put it in the "models" folder as L3-8B-Stheno-v3.2-Q8_0.gguf first.
3. Double-click  "Ryuu Aniverse v2.exe".
4. Open a character. The first time, you tell them who you are and whether you are under 18
   (this only decides the romance rules: see below). Models load in about 15 seconds.

USING IT
- Home: hover a panel to meet a character, click to talk. Below: all 100 characters with search, series filter, favourites.
- Chat: threads on the left (New chat, Rename, Export, Delete). Click the scene line under the title to change where you are.
  "Your profile" is a separate profile for each character. "Actions" turns the little scene lines on or off.
  "Typing effect" shows replies letter by letter.
- Your chats and settings are saved in  %LOCALAPPDATA%\Ryuu Aniverse  (not in the program folder).
  Deleting that folder resets the app. Portraits live in  content\assets\portraits  (replace any picture with your own PNG of the same name).

THE RULES (built in, cannot be turned off)
- Characters are AI-written role-play, not real people or the real series. Facts are checked but can be wrong.
- Sweet romance only, never explicit. Children: friendly only. Teen characters: sweet romance only with users under 18.
  Adult characters: sweet romance only with users 18+. Everyone else: friendly.
- If you write something that sounds like you are in real danger, the character answers with a short caring note.
  This app is not a replacement for real help.

IF SOMETHING GOES WRONG
- "AI models not installed": run  Setup models.bat  again, then press Try again in the chat.
- "Ollama not running": start the Ollama app, press Try again.
- A reply that never arrives ends in an error after 3 minutes. The log is  %LOCALAPPDATA%\Ryuu Aniverse\app.log  (send it when asking for help).
- The app uses its own private Ollama on port 11436 (your normal Ollama is not touched) and stops it when you close the app.

This is an unofficial fan project, not affiliated with any anime, publisher or studio. See NOTICE.txt for credits and licences.
