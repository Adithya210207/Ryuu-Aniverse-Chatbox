package aniverse;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The action line above each reply (plan section 8.5): the brain with the narrator prompt writes one
 * sentence; word rules + judge J4 check it; up to 2 re-narrations, then a plain safe gesture from the pack.
 */
public final class Narrator {
    private final Knowledge k;
    private final Judge judge;
    private final Random rng = new Random();

    public Narrator(Knowledge k, Judge judge) { this.k = k; this.judge = judge; }

    public record Cue(String text, int tries, boolean plain) {}

    public Cue narrate(Knowledge.Pack p, WordRules rules, String userName, String userLine, String reply) {
        for (int t = 1; t <= 3; t++) {
            String cue = once(p, userName, userLine, reply, 0.7 + 0.1 * (t - 1));
            if (cue.isBlank()) continue;
            if (cue.endsWith("…") || cue.split("\\s+").length > 28) continue;   // cut off or far too long
            if (!rules.cueProblems(cue).isEmpty()) continue;
            Map<String, String> v = new LinkedHashMap<>();
            v.put("appearance", p.appearance());
            v.put("cue", cue);
            String a = judge.ask("J4", v);
            if (judge.flags("J4", a)) continue;
            return new Cue(cue, t, false);
        }
        return new Cue(plain(p, userName), 3, true);
    }

    private String once(Knowledge.Pack p, String userName, String userLine, String reply, double temperature) {
        try {
            Knowledge.Series ser = k.series(p.entry.seriesId());
            Map<String, String> v = new LinkedHashMap<>();
            v.put("series_title", ser == null ? p.entry.seriesTitle() : ser.title());
            v.put("name", p.name());
            v.put("user_name", userName);
            v.put("appearance", p.appearance());
            v.put("pronoun_subject", switch (p.pronoun()) { case "he" -> "he"; case "she" -> "she"; case "it" -> "it"; default -> "they"; });
            String sys = PromptBuilder.fill(k.narratorTemplate, v).strip();
            String said = userLine == null || userLine.isBlank() ? "" : userName + " says: \"" + userLine.strip() + "\"\n";
            String user = said + p.name() + " replies: \"" + reply.strip() + "\"\n\nNarrate what " + p.name() + " does as they reply.";
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("temperature", temperature);
            opt.put("num_predict", 48);
            String raw = Ollama.chatOnce(Ollama.BRAIN, List.of(PromptBuilder.msg("system", sys), PromptBuilder.msg("user", user)), opt,
                    Duration.ofMinutes(2)).text();
            return clean(raw);
        } catch (Exception e) {
            return "";
        }
    }

    /** One clean sentence: no asterisks/quotes, cut after the first sentence end (TQQ cleanAction). */
    public static String clean(String s) {
        String t = s.replace("*", "").replaceAll("[\"“”]", "").strip();
        int nl = t.indexOf('\n');
        if (nl > 0) t = t.substring(0, nl).strip();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c == '.' || c == '!' || c == '?') && i > 10) { t = t.substring(0, i + 1); break; }
        }
        if (t.length() > 160) t = t.substring(0, 160).strip() + "…";
        if (!t.isEmpty() && !Ollama.endsSentence(t)) t = t.replaceAll("[\\s,;:\\-]+$", "") + "…";
        return t;
    }

    /** A plain, safe gesture from the pack (used only when every narrated line was odd). */
    public String plain(Knowledge.Pack p, String userName) {
        List<String> cues = p.plainCues();
        String c = cues.isEmpty() ? p.name() + " looks at {user_name} for a moment." : cues.get(rng.nextInt(cues.size()));
        return c.replace("{user_name}", userName == null || userName.startsWith("the person") ? "you" : userName);
    }
}
