package aniverse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** App settings (settings.json). Defaults follow the plan (live typing off, action lines on, judge on). */
public final class Settings {
    public boolean liveTyping = false;
    public boolean actionLines = true;
    public boolean judgeOn = true;
    public boolean memoryNotes = true;
    /** short / normal / long */
    public String replyLength = "normal";

    private static Path file() { return App.DATA.resolve("settings.json"); }

    public static Settings load() {
        Settings s = new Settings();
        try {
            if (Files.exists(file())) {
                Map<String, Object> m = App.readJson(file());
                s.liveTyping = App.bool(m, "live_typing", s.liveTyping);
                s.actionLines = App.bool(m, "action_lines", s.actionLines);
                s.judgeOn = App.bool(m, "judge_on", s.judgeOn);
                s.memoryNotes = App.bool(m, "memory_notes", s.memoryNotes);
                String r = App.str(m, "reply_length");
                if (r.equals("short") || r.equals("normal") || r.equals("long")) s.replyLength = r;
            }
        } catch (Exception ignored) {}
        return s;
    }

    public void save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("live_typing", liveTyping);
        m.put("action_lines", actionLines);
        m.put("judge_on", judgeOn);
        m.put("memory_notes", memoryNotes);
        m.put("reply_length", replyLength);
        App.writeAtomic(file(), Json.pretty(m));
    }

    /** num_predict for a chat reply. */
    public int replyTokens() {
        return switch (replyLength) { case "short" -> 120; case "long" -> 320; default -> 220; };
    }
}
