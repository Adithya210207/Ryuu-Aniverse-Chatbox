package aniverse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Who the user is (persona.json; shape in knowledge/global/persona_template.json): a global profile plus
 * per-character overrides, and "under 18" which sets the romance matrix (content_policy.md section 2).
 */
public final class Persona {
    public record Profile(String name, String bio, String howMet) {}

    private final Path file;
    private Profile global = new Profile("", "", "");
    private final Map<String, Profile> overrides = new LinkedHashMap<>();
    /** null = not asked yet. */
    public Boolean under18;

    private Persona(Path file) { this.file = file; }

    @SuppressWarnings("unchecked")
    public static Persona load() {
        Persona p = new Persona(App.DATA.resolve("persona.json"));
        try {
            if (Files.exists(p.file)) {
                Map<String, Object> root = App.readJson(p.file);
                Map<String, Object> g = App.map(root, "global");
                p.global = new Profile(App.str(g, "name"), App.str(g, "bio"), App.str(g, "how_met"));
                Object u = g.get("under_18");
                p.under18 = u instanceof Boolean b ? b : null;
                App.map(root, "overrides").forEach((k, v) -> {
                    if (!(v instanceof Map<?, ?>)) return;   // skip a damaged entry instead of losing every profile
                    Map<String, Object> m = (Map<String, Object>) v;
                    p.overrides.put(k, new Profile(App.str(m, "name"), App.str(m, "bio"), App.str(m, "how_met")));
                });
            }
        } catch (Exception bad) {
            // unreadable: keep a copy instead of overwriting it with an empty profile on the next save (like the chat history)
            try {
                String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Files.copy(p.file, p.file.resolveSibling("persona.unreadable-" + stamp + ".json"));
            } catch (Exception ignored) {}
            Log.error("persona.json could not be read", bad);
        }
        return p;
    }

    public void save() {
        Map<String, Object> g = new LinkedHashMap<>();
        g.put("name", global.name()); g.put("bio", global.bio()); g.put("how_met", global.howMet()); g.put("under_18", under18);
        Map<String, Object> ov = new LinkedHashMap<>();
        overrides.forEach((k, v) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", v.name()); m.put("bio", v.bio()); m.put("how_met", v.howMet());
            ov.put(k, m);
        });
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", 1); root.put("global", g); root.put("overrides", ov);
        App.writeAtomic(file, Json.pretty(root));
    }

    public Profile global() { return global; }
    public void setGlobal(Profile p) { global = clip(p); }
    public void setOverride(String characterFile, Profile p) {
        if (p == null) overrides.remove(characterFile); else overrides.put(characterFile, clip(p));
    }
    public Profile override(String characterFile) { return overrides.get(characterFile); }

    /** The profile for one character: its override's filled fields, else the global ones. */
    public Profile forCharacter(String characterFile) {
        Profile o = overrides.get(characterFile);
        if (o == null) return global;
        return new Profile(o.name().isBlank() ? global.name() : o.name(), o.bio().isBlank() ? global.bio() : o.bio(),
                o.howMet().isBlank() ? global.howMet() : o.howMet());
    }

    /** {user_name}: the persona name or "the person you're talking with". */
    public String userName(String characterFile) {
        String n = forCharacter(characterFile).name().strip();
        return n.isEmpty() ? "the person you're talking with" : n;
    }

    /** Short name for transcripts and the narrator. */
    public String displayName(String characterFile) {
        String n = forCharacter(characterFile).name().strip();
        return n.isEmpty() ? "You" : n;
    }

    /** {persona_block} for the system prompt. */
    public String block(String characterFile) {
        Profile p = forCharacter(characterFile);
        if (p.name().isBlank() && p.bio().isBlank() && p.howMet().isBlank())
            return "Someone you met recently; you don't know much about them yet.";
        StringBuilder b = new StringBuilder();
        if (!p.name().isBlank()) b.append(p.name().strip()).append('.');
        if (!p.bio().isBlank()) b.append(' ').append(sentence(p.bio()));
        if (!p.howMet().isBlank()) b.append(" How you met: ").append(sentence(p.howMet()));
        return b.toString().strip();
    }

    private static String sentence(String s) {
        String t = s.strip().replaceAll("\\s+", " ");
        return Ollama.endsSentence(t) ? t : t + ".";
    }

    private static Profile clip(Profile p) {
        return new Profile(cut(p.name(), 40), cut(p.bio(), 300), cut(p.howMet(), 400));
    }
    private static String cut(String s, int n) { String t = s == null ? "" : s.strip(); return t.length() > n ? t.substring(0, n) : t; }
}
