package aniverse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Everything in knowledge/: roster, series sheets, character packs and the global files
 * (system prompt template, narrator prompt, judge prompts, word rules). Read once at start-up;
 * packs are read when first needed. Plan sections 7 and 13.
 */
public final class Knowledge {

    /** One roster row (knowledge/roster.json). */
    public record Entry(int number, String file, String id, String name, String seriesId, String seriesTitle,
                        String snapshot, boolean ongoing, String ageBand, String accent, String glyph, int wave) {
        public String num() { return String.format("%03d", number); }
    }

    /** One series sheet. */
    public record Series(String id, String title, String nativeTitle, Map<String, Object> raw) {
        public List<Object> terms() { return App.list(raw, "terms"); }
        public List<String> canon() { return App.strings(raw, "shared_canon_events"); }
    }

    /** One character's knowledge pack (knowledge/characters/NNN_id.json). */
    public static final class Pack {
        public final Entry entry;
        public final Map<String, Object> raw;
        Pack(Entry e, Map<String, Object> raw) { this.entry = e; this.raw = raw; }

        public String name() { return App.str(raw, "name"); }
        public String nativeName() { return App.str(raw, "native_name"); }
        public String pronoun() { return App.str(raw, "pronoun"); }
        public String ageBand() { String b = App.str(raw, "age_band"); return b.isEmpty() ? entry.ageBand() : b; }
        public String appearance() { return App.str(raw, "appearance"); }
        public String tagline() { return App.str(raw, "tagline"); }
        public String greeting() { return App.str(raw, "greeting"); }
        public String defaultScene() { return App.str(raw, "default_scene"); }
        public List<String> starters() { return App.strings(raw, "starters"); }
        public List<String> facts() { return App.strings(raw, "facts"); }
        public List<String> canonEvents() { return App.strings(raw, "canon_events"); }
        public List<String> plainCues() { return App.strings(raw, "plain_cues"); }
        public Map<String, Object> speech() { return App.map(raw, "speech"); }
        public Map<String, Object> nameRules() { return App.map(raw, "name_rules"); }
        public Map<String, Object> fallbacks() { return App.map(raw, "fallbacks"); }
        public Map<String, Object> toneLimits() { return App.map(raw, "tone_limits"); }

        @SuppressWarnings("unchecked")
        public List<String> relationshipNames() {
            List<String> out = new ArrayList<>();
            for (Object o : App.list(raw, "relationships")) if (o instanceof Map<?, ?> m) out.add(App.str((Map<String, Object>) m, "name"));
            return out;
        }

        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> factChecks() {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : App.list(raw, "fact_checks")) if (o instanceof Map<?, ?> m) out.add((Map<String, Object>) m);
            return out;
        }
    }

    private final List<Entry> roster = new ArrayList<>();
    private final Map<String, Entry> byFile = new LinkedHashMap<>();
    private final Map<String, Series> series = new LinkedHashMap<>();
    private final Map<String, Pack> packs = new ConcurrentHashMap<>();

    public final String systemTemplate;
    public final String narratorTemplate;
    public final Map<String, Map<String, Object>> judgeChecks = new LinkedHashMap<>();
    public final Map<String, Object> reminders;
    public final Map<String, Object> retryPolicy;
    public final Map<String, Object> crisisNote;
    public final List<Map<String, Object>> wordRules = new ArrayList<>();
    public final List<String> stopWords;

    @SuppressWarnings("unchecked")
    public Knowledge() {
        Path k = App.knowledge();
        for (Object o : App.list(App.readJson(k.resolve("roster.json")), "characters")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Entry e = new Entry(((Number) m.get("number")).intValue(), App.str(m, "file"), App.str(m, "id"), App.str(m, "name"),
                    App.str(m, "series"), App.str(m, "series_title"), App.str(m, "snapshot"), App.bool(m, "ongoing", false),
                    App.str(m, "age_band"), App.str(m, "accent"), App.str(m, "glyph"), ((Number) m.get("wave")).intValue());
            roster.add(e);
            byFile.put(e.file(), e);
        }
        try (var files = Files.list(k.resolve("series"))) {
            for (Path p : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                Map<String, Object> m = App.readJson(p);
                series.put(App.str(m, "id"), new Series(App.str(m, "id"), App.str(m, "title"), App.str(m, "native_title"), m));
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read knowledge/series: " + e.getMessage(), e);
        }
        Path g = k.resolve("global");
        systemTemplate = App.read(g.resolve("system_prompt_template.txt"));
        narratorTemplate = App.read(g.resolve("narrator_prompt.txt"));
        Map<String, Object> jp = App.readJson(g.resolve("judge_prompts.json"));
        for (Object o : App.list(jp, "checks")) { Map<String, Object> c = (Map<String, Object>) o; judgeChecks.put(App.str(c, "id"), c); }
        reminders = App.map(jp, "reminders");
        retryPolicy = App.map(jp, "retry_policy");
        crisisNote = App.map(jp, "crisis_note");
        Map<String, Object> wr = App.readJson(g.resolve("word_rules.json"));
        for (Object o : App.list(wr, "rules")) wordRules.add((Map<String, Object>) o);
        stopWords = App.strings(wr, "stop_words");
    }

    public List<Entry> roster() { return roster; }
    public Entry entry(String file) { return byFile.get(file); }
    public Series series(String id) { return series.get(id); }

    public boolean hasPack(String file) { return Files.exists(App.knowledge().resolve("characters").resolve(file + ".json")); }

    /** The pack for a roster file name (NNN_id), or null if it isn't written yet. */
    public Pack pack(String file) {
        Entry e = byFile.get(file);
        if (e == null || !hasPack(file)) return null;
        return packs.computeIfAbsent(file, f -> new Pack(e, App.readJson(App.knowledge().resolve("characters").resolve(f + ".json"))));
    }

    /** Finds a roster entry by file name, id, number or (part of) the name. */
    public Entry find(String q) {
        String s = q.strip().toLowerCase();
        for (Entry e : roster) if (e.file().equals(s) || e.id().equals(s) || e.num().equals(s) || String.valueOf(e.number()).equals(s)) return e;
        for (Entry e : roster) if (e.name().toLowerCase().contains(s)) return e;
        return null;
    }
}
