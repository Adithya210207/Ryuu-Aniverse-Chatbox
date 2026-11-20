package aniverse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The phi4-mini judge (plan section 8.3; wording in knowledge/global/judge_prompts.json).
 * Each check returns the matched answer word, or null when the judge is off, missing or unsure:
 * the caller then relies on the word rules. An answer counts if the reply starts with the first
 * 6 letters of an answer word.
 */
public final class Judge {
    private final Knowledge k;
    /** Set by the app after Ollama.loadJudge(): false = word rules only ("Judge off" badge). */
    public volatile boolean ready;

    public Judge(Knowledge k) { this.k = k; }

    /** Runs one check (J1..J6). vars fill {placeholders}; quotes in free text become '. */
    @SuppressWarnings("unchecked")
    public String ask(String id, Map<String, String> vars) {
        if (!ready) return null;
        Map<String, Object> c = k.judgeChecks.get(id);
        if (c == null) return null;
        String raw = call(c, vars);
        if (raw == null) return null;
        // "TOO FAR" / "TOO-FAR" must still count as TOO_FAR
        String a = raw.strip().toUpperCase(java.util.Locale.ROOT).replaceAll("^[^A-Z_]+", "").replaceFirst("^TOO[\\s-]+FAR", "TOO_FAR");
        for (Object o : App.list(c, "answers")) {
            String w = String.valueOf(o);
            String pre = w.length() > 6 ? w.substring(0, 6) : w;
            if (a.startsWith(pre)) return w;
        }
        return null;
    }

    /** J7 thread notes: free text (bullets), or null. */
    public String notes(Map<String, String> vars) {
        if (!ready) return null;
        Map<String, Object> c = k.judgeChecks.get("J7");
        String raw = c == null ? null : call(c, vars);
        return raw == null || raw.isBlank() ? null : raw.strip();
    }

    /** True if the answer equals the check's flag word. */
    public boolean flags(String id, String answer) {
        Map<String, Object> c = k.judgeChecks.get(id);
        return answer != null && c != null && answer.equals(App.str(c, "flag_on"));
    }

    @SuppressWarnings("unchecked")
    private String call(Map<String, Object> c, Map<String, String> vars) {
        try {
            Map<String, String> v = new LinkedHashMap<>();
            vars.forEach((key, val) -> v.put(key, val == null ? "" : (key.equals("reply") || key.equals("user_line") || key.equals("cue"))
                    ? val.strip().replace("\"", "'") : val));
            List<Map<String, Object>> msgs = new ArrayList<>();
            Object sys = c.get("system");
            if (sys != null) msgs.add(PromptBuilder.msg("system", PromptBuilder.fill(String.valueOf(sys), v)));
            msgs.add(PromptBuilder.msg("user", PromptBuilder.fill(App.str(c, "user"), v)));
            Map<String, Object> options = new LinkedHashMap<>(App.map(c, "options"));
            return Ollama.chatOnce(Ollama.JUDGE, msgs, options, Duration.ofSeconds(60)).text();
        } catch (Exception e) {
            return null;
        }
    }
}
