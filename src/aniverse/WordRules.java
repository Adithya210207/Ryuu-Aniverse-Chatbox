package aniverse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The word rules from knowledge/global/word_rules.json, compiled for one character (plan section 8.2).
 * Placeholders in patterns ({speaker_names}, {confusable}, {other_names}, {user_name}) are filled from
 * the pack and the persona. The judge calls themselves are made by TurnPipeline.
 */
public final class WordRules {
    private static final String NEVER = "(?!x)x";
    /** Everyday words never used alone as "real story" keywords (they'd hide made-up memories). */
    private static final Set<String> COMMON = Set.of("death", "dead", "dying", "meeting", "training", "leaving", "losing", "lost",
            "village", "right", "left", "great", "fourth", "third", "second", "sixth", "seventh", "fifth", "last", "first",
            "team", "exam", "exams", "ninja", "land", "mount", "battle", "fight", "home", "friend", "friends", "school",
            "attack", "assault", "return", "trip", "night", "arm", "eye", "eyes", "house", "room", "city", "town", "world",
            "year", "years", "summer", "winter", "spring", "beach", "park", "party", "dinner", "lunch", "food", "game",
            "games", "class", "club", "festival", "with", "from", "into", "after", "before", "when", "story", "final",
            "war", "wars", "time", "days", "life", "love", "kiss", "date", "gift", "money", "debt", "letter", "promise");

    private final Map<String, Map<String, Object>> rules = new java.util.LinkedHashMap<>();
    private final Map<String, Pattern> pat = new java.util.HashMap<>();
    private final Map<String, Pattern> ok = new java.util.HashMap<>();
    private final Map<String, Pattern> bad = new java.util.HashMap<>();
    private final Pattern listLines;
    private final int listMin;
    private final Pattern canon;
    private final Set<String> stop;
    private final String appearance;
    private final List<Map<String, Object>> factChecks;

    @SuppressWarnings("unchecked")
    public WordRules(Knowledge k, Knowledge.Pack p, String userName) {
        List<String> rel = p.relationshipNames();
        List<String> speakers = new ArrayList<>(rel);
        if (userName != null && !userName.isBlank() && !userName.startsWith("the person")) speakers.add(userName);
        List<String> confusable = App.strings(p.nameRules(), "never_claims_to_be");
        Pattern listP = null; int min = 3;
        for (Map<String, Object> r : k.wordRules) {
            String code = App.str(r, "code");
            rules.put(code, r);
            if (r.containsKey("pattern")) pat.put(code, compile(fill(App.str(r, "pattern"), speakers, confusable, rel, userName)));
            if (r.containsKey("reply_ok")) ok.put(code, compile(App.str(r, "reply_ok")));
            if (r.containsKey("reply_bad")) bad.put(code, compile(App.str(r, "reply_bad")));
            if (r.get("also") instanceof Map<?, ?> also && also.get("list_lines") instanceof Map<?, ?> ll) {
                listP = compile(String.valueOf(ll.get("pattern")));
                if (ll.get("min_count") instanceof Number n) min = n.intValue();
            }
        }
        listLines = listP;
        listMin = min;
        // real story words: never treat them as made-up memories
        Set<String> words = new LinkedHashSet<>();
        words.addAll(p.canonEvents());
        Knowledge.Series ser = k.series(p.entry.seriesId());
        if (ser != null) words.addAll(ser.canon());
        for (String n : rel) { words.add(n.toLowerCase(Locale.ROOT)); for (String part : n.split("[\\s()]+")) if (part.length() >= 3) words.add(part.toLowerCase(Locale.ROOT)); }
        words.addAll(App.strings(p.nameRules(), "also_known_as").stream().map(s -> s.toLowerCase(Locale.ROOT)).toList());
        // key names inside phrases too ("pain's assault" -> pain, "haku and zabuza" -> haku, zabuza)
        Set<String> parts = new LinkedHashSet<>();
        for (String w : words) {
            for (String part : w.toLowerCase(Locale.ROOT).replace("'s", "").split("[^a-z0-9\\-]+")) {
                if (part.length() >= 4 && !COMMON.contains(part) && !k.stopWords.contains(part)) parts.add(part);
            }
        }
        words.addAll(parts);
        StringBuilder alt = new StringBuilder();
        for (String w : words) { if (w.isBlank()) continue; if (!alt.isEmpty()) alt.append('|'); alt.append(Pattern.quote(w.strip())); }
        canon = alt.isEmpty() ? compile(NEVER) : Pattern.compile("(?<![\\w])(?:" + alt + ")(?:s|es|ed|ing)?(?![\\w])", Pattern.CASE_INSENSITIVE);
        stop = new java.util.HashSet<>(k.stopWords);
        appearance = p.appearance().toLowerCase(Locale.ROOT);
        factChecks = p.factChecks();
    }

    private static Pattern compile(String regex) { return Pattern.compile(regex); }

    private static String alternation(List<String> names) {
        StringBuilder b = new StringBuilder();
        for (String n : names) { if (n == null || n.isBlank()) continue; if (!b.isEmpty()) b.append('|'); b.append(Pattern.quote(n.strip())); }
        return b.isEmpty() ? NEVER : b.toString();
    }

    private static String fill(String p, List<String> speakers, List<String> confusable, List<String> others, String userName) {
        String u = userName == null || userName.isBlank() || userName.startsWith("the person") ? NEVER : Pattern.quote(userName.strip());
        return p.replace("{speaker_names}", alternation(speakers)).replace("{confusable}", alternation(confusable))
                .replace("{other_names}", alternation(others)).replace("{user_name}", u);
    }

    public boolean has(String code, String text) {
        Pattern p = pat.get(code);
        return p != null && text != null && p.matcher(text).find();
    }

    public String hint(String code) { return App.str(rules.get(code), "hint"); }

    // ------------------------------------------------------------ reply fixes

    /** Silent fixes: leftover tokens and *actions*. */
    public String autofix(String reply) {
        String r = reply == null ? "" : reply;
        r = r.replaceAll("<\\|[^|>]{0,40}\\|>", "");
        r = r.replaceAll("\\*[^*\\n]{0,200}\\*", " ").replace("*", "");
        r = r.replaceAll("[ \\t]{2,}", " ").replaceAll("\\s+([,.!?])", "$1").strip();
        // the whole reply wrapped in quotes ("...") -> drop the outer quotes (inner quotes stay)
        if (r.length() > 2 && "\"“".indexOf(r.charAt(0)) >= 0 && "\"”".indexOf(r.charAt(r.length() - 1)) >= 0
                && r.substring(1, r.length() - 1).chars().noneMatch(c -> c == '"' || c == '“' || c == '”')) {
            r = r.substring(1, r.length() - 1).strip();
        }
        return r;
    }

    public boolean looksLikeList(String reply) {
        if (listLines == null || reply == null) return false;
        Matcher m = listLines.matcher(reply);
        int n = 0;
        while (m.find()) n++;
        return n >= listMin;
    }

    // ------------------------------------------------------------ user message

    /** A past event/thing that is not real story canon and not said earlier in the chat (InventionCheck). */
    public boolean premise(String userLine, String earlier) {
        if (!has("premise", userLine)) return false;
        if (canon.matcher(userLine).find()) return false;
        return !mentionedBefore(userLine, earlier);
    }

    public boolean cantKnow(String userLine) { return has("cant-know", userLine); }

    /** Did the reply deny/refuse the premise? */
    public boolean denies(String reply) { Pattern p = ok.get("premise"); return p != null && reply != null && p.matcher(reply).find(); }

    /** Did the reply say it doesn't know (and not invent an answer anyway)? */
    public boolean saysNotKnowing(String reply) {
        Pattern o = ok.get("cant-know"), b = bad.get("cant-know");
        return reply != null && o != null && o.matcher(reply).find() && (b == null || !b.matcher(reply).find());
    }

    /** The fact lines whose topic words are in the user's message (max 4). */
    public List<String> factsFor(String userLine) {
        List<String> out = new ArrayList<>();
        if (userLine == null) return out;
        String u = userLine.toLowerCase(Locale.ROOT);
        for (Map<String, Object> fc : factChecks) {
            for (String w : App.strings(fc, "topic_words")) {
                if (Pattern.compile("(?<![\\w])" + Pattern.quote(w.toLowerCase(Locale.ROOT)) + "(?![\\w])").matcher(u).find()) {
                    out.add(App.str(fc, "fact"));
                    break;
                }
            }
            if (out.size() >= 4) break;
        }
        return out;
    }

    /** True if a telling word of the message (not a stop word, 3+ letters) was already in the chat (TQQ). */
    public boolean mentionedBefore(String userLine, String earlier) {
        if (earlier == null || earlier.isBlank()) return false;
        String e = " " + earlier.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9']+", " ") + " ";
        for (String w : userLine.toLowerCase(Locale.ROOT).split("[^a-z0-9']+")) {
            if (w.length() < 3 || stop.contains(w)) continue;
            String stem = w.endsWith("s") ? w.substring(0, w.length() - 1) : w;
            if (e.contains(" " + stem)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ action line (cue)

    /** Problems in the narrator's action line, word rules only. Empty = fine. */
    public List<String> cueProblems(String cue) {
        List<String> out = new ArrayList<>();
        if (cue == null || cue.isBlank()) return out;
        if (has("cue-people", cue)) out.add("cue-people");
        Pattern s = pat.get("cue-strange");
        if (s != null) {
            Matcher m = s.matcher(cue);
            while (m.find()) {
                if (!appearance.contains(m.group().toLowerCase(Locale.ROOT))) { out.add("cue-strange"); break; }
            }
        }
        if (has("cue-glasses", cue) && !appearance.contains("glasses") && !appearance.contains("goggles")) out.add("cue-glasses");
        return out;
    }
}
