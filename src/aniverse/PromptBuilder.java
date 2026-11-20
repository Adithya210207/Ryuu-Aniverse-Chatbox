package aniverse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Builds what the brain sees (plan sections 5 and 6): the character system prompt from the template,
 * the romance rule, series lore snippets, thread notes, the history that fits, and retry reminders.
 * Message order: [system prompt] [notes] [history...] [lore] [user] [reminder]: the system prompt
 * stays first and unchanged so Ollama can reuse its cache.
 */
public final class PromptBuilder {
    public static final int CONTEXT = 8192;
    public static final int MARGIN = 440;
    public static final int LORE_CHARS = 1080;   // ~300 tokens
    /** Where a retry reminder goes: after (system after the user line), before, or inline (in the user line). */
    public static String REMINDER_MODE = System.getProperty("aniverse.reminder", "after");

    /** What the romance matrix allows in this chat (content_policy.md section 2). */
    public enum Romance { SWEET, FRIENDLY }

    private final Knowledge k;
    public PromptBuilder(Knowledge k) { this.k = k; }

    public static int tokens(String s) { return s == null ? 0 : (int) Math.ceil(s.length() / 3.6); }

    public static Romance romance(String ageBand, Boolean under18) {
        boolean minorUser = under18 == null || under18;   // unknown age = the careful rule
        String band = ageBand == null ? "" : ageBand.strip().toLowerCase(Locale.ROOT);
        return switch (band) {
            case "teen" -> minorUser ? Romance.SWEET : Romance.FRIENDLY;
            case "adult", "ageless" -> minorUser ? Romance.FRIENDLY : Romance.SWEET;
            default -> Romance.FRIENDLY;      // child (or unknown)
        };
    }

    public static String ageRule(String ageBand, Romance r, String userName) {
        String s = r == Romance.SWEET
                ? "You can flirt and be romantic with " + userName + " if they want it: compliments, blushing, dates, holding hands, hugs, confessing feelings. Keep it sweet and innocent: no kissing scenes, nothing suggestive, never anything sexual."
                : "You are friendly with " + userName + ", not romantic or flirty; you treat them like a friend.";
        if ("child".equals(ageBand)) s += " Keep topics simple and gentle.";
        return s;
    }

    // ------------------------------------------------------------ system prompt

    @SuppressWarnings("unchecked")
    public String system(Knowledge.Pack p, Persona persona, String scene, Boolean under18) {
        Map<String, Object> r = p.raw;
        Knowledge.Series ser = k.series(p.entry.seriesId());
        String user = persona.userName(p.entry.file());
        Map<String, Object> speech = p.speech();
        Map<String, Object> addr = App.map(speech, "address_user");
        List<String> tics = App.strings(speech, "tics");
        StringBuilder rel = new StringBuilder();
        for (Object o : App.list(r, "relationships")) {
            Map<String, Object> m = (Map<String, Object>) o;
            if (!rel.isEmpty()) rel.append('\n');
            rel.append("- ").append(App.str(m, "name")).append(": ").append(App.str(m, "line"));
        }
        List<String> secrets = App.strings(r, "secrets");
        List<String> tone = App.strings(p.toneLimits(), "rules");
        Map<String, String> v = new LinkedHashMap<>();
        v.put("name", p.name());
        v.put("series_title", ser == null ? p.entry.seriesTitle() : ser.title());
        v.put("snapshot", App.str(r, "snapshot"));
        v.put("persona", App.str(r, "persona"));
        v.put("personality", String.join(", ", App.strings(r, "personality")));
        v.put("speech_style", App.str(speech, "style"));
        v.put("address_user", App.str(addr, "default").replace("{user_name}", user));
        v.put("tics_line", tics.isEmpty() ? "" : " You sometimes say: " + String.join(", ", tics) + ".");
        v.put("abilities", App.str(r, "abilities"));
        v.put("facts_lines", lines(App.strings(r, "facts")));
        v.put("relationship_lines", rel.toString());
        v.put("likes", String.join(", ", App.strings(r, "likes")));
        v.put("dislikes", String.join(", ", App.strings(r, "dislikes")));
        v.put("boundary_lines", lines(App.strings(r, "boundaries")));
        v.put("secrets_block", secrets.isEmpty() ? "" : "\nWhat you keep secret (never reveal it; deflect in your own way):\n" + lines(secrets) + "\n");
        v.put("age_rule", ageRule(p.ageBand(), romance(p.ageBand(), under18), user));
        v.put("tone_lines", tone.isEmpty() ? "" : lines(tone) + "\n");
        v.put("persona_block", persona.block(p.entry.file()));
        v.put("scene_block", scene == null || scene.isBlank() ? p.defaultScene() : scene.strip());
        v.put("nexus_line", "");
        v.put("user_name", user);
        return fill(k.systemTemplate, v).strip();
    }

    public static String fill(String template, Map<String, String> v) {
        String out = template;
        for (Map.Entry<String, String> e : v.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        return out;
    }

    private static String lines(List<String> items) {
        StringBuilder b = new StringBuilder();
        for (String s : items) { if (!b.isEmpty()) b.append('\n'); b.append("- ").append(s); }
        return b.toString();
    }

    // ------------------------------------------------------------ lore

    /** Series glossary lines whose term or alias appears in the text (max ~300 tokens). */
    @SuppressWarnings("unchecked")
    public String lore(Knowledge.Pack p, String text) {
        Knowledge.Series ser = k.series(p.entry.seriesId());
        if (ser == null || text == null || text.isBlank()) return "";
        String t = text.toLowerCase(Locale.ROOT);
        StringBuilder b = new StringBuilder();
        for (Object o : ser.terms()) {
            Map<String, Object> m = (Map<String, Object>) o;
            List<String> names = new ArrayList<>();
            names.add(App.str(m, "term"));
            names.addAll(App.strings(m, "aliases"));
            boolean hit = false;
            for (String n : names) {
                if (n.isBlank()) continue;
                if (Pattern.compile("(?<![\\w])" + Pattern.quote(n.toLowerCase(Locale.ROOT)) + "(?![\\w])").matcher(t).find()) { hit = true; break; }
            }
            if (!hit) continue;
            String line = "- " + App.str(m, "term") + ": " + App.str(m, "line");
            if (b.length() + line.length() > LORE_CHARS) break;
            b.append(line).append('\n');
        }
        return b.isEmpty() ? "" : "World notes for this reply (true in your story):\n" + b.toString().strip();
    }

    // ------------------------------------------------------------ messages

    public static Map<String, Object> msg(String role, String content) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("role", role);
        m.put("content", content);
        return m;
    }

    /**
     * Full message list for one reply.
     * @param history earlier turns of the thread (oldest first), roles "user" / "character"
     */
    public List<Map<String, Object>> messages(String system, String notes, List<Map<String, Object>> history,
                                              String lore, String userLine, String reminder, int replyTokens) {
        List<Map<String, Object>> head = new ArrayList<>();
        head.add(msg("system", system));
        if (notes != null && !notes.isBlank()) head.add(msg("system", "What you remember about earlier in this chat:\n" + notes.strip()));
        List<Map<String, Object>> tail = new ArrayList<>();
        if (lore != null && !lore.isBlank()) tail.add(msg("system", lore));
        boolean hasRem = reminder != null && !reminder.isBlank();
        String rem = hasRem ? "Reminder: " + reminder.strip() : "";
        if (hasRem && REMINDER_MODE.equals("before")) tail.add(msg("system", rem));
        if (userLine != null) {
            tail.add(msg("user", hasRem && REMINDER_MODE.equals("inline") ? userLine + "\n\n(" + rem + ")" : userLine));
        }
        if (hasRem && REMINDER_MODE.equals("after")) tail.add(msg("system", rem));
        int used = MARGIN + replyTokens;
        for (Map<String, Object> m : head) used += tokens(String.valueOf(m.get("content"))) + 4;
        for (Map<String, Object> m : tail) used += tokens(String.valueOf(m.get("content"))) + 4;
        List<Map<String, Object>> kept = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, Object> h = history.get(i);
            String text = App.str(h, "text");
            if (text.isBlank()) continue;   // a blank/missing text in an old history file: don't send "null" to the model
            int cost = tokens(text) + 4;
            if (used + cost > CONTEXT) break;
            used += cost;
            kept.add(msg("user".equals(h.get("role")) ? "user" : "assistant", text));
        }
        Collections.reverse(kept);
        List<Map<String, Object>> out = new ArrayList<>(head);
        out.addAll(kept);
        out.addAll(tail);
        return out;
    }
}
