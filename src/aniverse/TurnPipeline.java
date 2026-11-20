package aniverse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

/**
 * One chat turn, plan section 5:
 *  1 pre-check the user's message (crisis, dangerous request)  2 build the prompt
 *  3 brain writes the reply  4 word rules + gated judge checks  5 retry (max 2) with reminders,
 *  then fallback line  6 action line  7 save; thread notes every 12 turns.
 */
public final class TurnPipeline {

    /** One problem with a reply. */
    public record Issue(String code, String reminderKey, String hint, boolean sure, String fallbackKind) {}

    /** What one turn produced. */
    public record Result(String reply, String action, boolean actionPlain, List<String> fixed, boolean crisis,
                         boolean fallback, int tries, double seconds, String judgeState, List<String> log) {}

    private final Knowledge k;
    private final Settings settings;
    private final Persona persona;
    public final Judge judge;
    /** The character's people list for this turn when the user named a family/team role (checked by J8); null otherwise. */
    private String rolePeople;
    private final PromptBuilder prompts;
    private final Narrator narrator;
    private final Random rng = new Random();

    public TurnPipeline(Knowledge k, Settings settings, Persona persona) {
        this.k = k;
        this.settings = settings;
        this.persona = persona;
        this.judge = new Judge(k);
        this.prompts = new PromptBuilder(k);
        this.narrator = new Narrator(k, judge);
    }

    public PromptBuilder prompts() { return prompts; }

    /** The system prompt for this chat. */
    public String system(Knowledge.Pack p, Threads.ChatThread t) {
        return prompts.system(p, persona, t.scene, persona.under18);
    }

    /** Pre-reads the chat so the first reply is fast. */
    public void prime(Knowledge.Pack p, Threads.ChatThread t) {
        Ollama.prime(prompts.messages(system(p, t), t.notes, t.messages, "", null, null, settings.replyTokens()));
    }

    private boolean judgeOn() { return settings.judgeOn && judge.ready; }

    /**
     * Runs one turn and adds both messages to the thread (the caller saves).
     * @param onToken gets streamed text of the FIRST try (only used for live typing); may be null
     */
    public Result run(Knowledge.Pack p, Threads threads, Threads.ChatThread t, String userLine,
                      Consumer<String> onToken, Ollama.Handle handle) throws Exception {
        long t0 = System.nanoTime();
        if (userLine != null && userLine.length() > 2000) userLine = userLine.substring(0, 2000);   // safety cap (the app already stops at 1,500)
        List<String> log = new ArrayList<>();
        String file = p.entry.file();
        String user = persona.userName(file);
        WordRules rules = new WordRules(k, p, persona.forCharacter(file).name());
        PromptBuilder.Romance romance = PromptBuilder.romance(p.ageBand(), persona.under18);
        String earlier = t.allText() + "\n" + persona.forCharacter(file).bio() + "\n" + persona.forCharacter(file).howMet();
        String prevReply = t.lastReply();
        List<Map<String, Object>> history = new ArrayList<>(t.messages);

        // ---- 1. pre-check the user's message
        List<String> pre = new ArrayList<>();
        boolean crisis = false;
        if (rules.has("crisis", userLine)) {
            String a = judgeOn() ? judge.ask("J6", vars(p, user, userLine, null)) : null;
            crisis = a == null || judge.flags("J6", a);
            log.add("crisis rule -> J6 " + a + (crisis ? " (crisis note)" : ""));
        }
        if (crisis) pre.add(reminder("crisis", p, user, userLine, null));
        // the user names someone in a family/team role (wife, sister, student...): show who is really close to the character
        rolePeople = null;
        if (ROLE.matcher(userLine).find()) {
            String people = peopleLine(p);
            if (!people.isEmpty()) {
                rolePeople = people;
                pre.add("The only people close to you: " + people + " If " + user + " calls someone your wife, husband, sister, brother, son, daughter, "
                        + "student, teacher or similar and that does not match this list, gently correct them in your own voice and say who that person really is to you.");
                log.add("role claim -> people list");
                // "your sister Nezuko": a name that is not among their people at all gets a direct correction
                java.util.regex.Matcher m = NAMED_ROLE.matcher(userLine);
                String low = people.toLowerCase(java.util.Locale.ROOT);
                while (m.find()) {
                    String role = m.group(1), name = m.group(2);
                    if (low.contains(name.toLowerCase(java.util.Locale.ROOT))) continue;
                    pre.add(name + " is NOT your " + role.toLowerCase(java.util.Locale.ROOT) + ". Say so in your own voice, and say who "
                            + name + " really is to you, or that you hardly know them.");
                    log.add("named role '" + role + " " + name + "' not in their people");
                }
            }
        }
        boolean danger = rules.has("danger", userLine);
        if (danger) { pre.add(reminder("danger", p, user, userLine, null)); log.add("danger rule -> pre-reminder"); }
        boolean premise = false;   // user decision: playing along with invented shared memories is fine in a roleplay chat (rules.premise(...) kept for reference)
        boolean cantKnow = rules.cantKnow(userLine);
        List<String> facts = rules.factsFor(userLine);
        if (premise) log.add("premise rule");
        if (cantKnow) log.add("cant-know rule");
        if (!facts.isEmpty()) log.add("fact topic: " + facts.size() + " fact(s)");

        // ---- 2. prompt parts
        String system = system(p, t);
        String lore = prompts.lore(p, userLine);
        int max = 1 + Math.max(0, k.retryPolicy.get("max_retries") instanceof Number n ? n.intValue() : 2);

        // ---- 3-5. reply, check, retry
        String best = null;
        List<Issue> bestIssues = null;
        List<Issue> last = List.of();
        int tries = 0;
        for (int attempt = 0; attempt < max; attempt++) {
            tries++;
            boolean lastTry = attempt == max - 1;
            LinkedHashSet<String> rem = new LinkedHashSet<>(pre);
            for (Issue i : last) {
                boolean every = App.bool(App.map(k.reminders, i.reminderKey()), "every_retry", false);
                if (every || lastTry) rem.add(i.hint());
            }
            String reminder = String.join(" ", rem);
            List<Map<String, Object>> msgs = prompts.messages(system, t.notes, history, lore, userLine, reminder, settings.replyTokens());
            Map<String, Object> opt = new LinkedHashMap<>();
            opt.put("num_predict", settings.replyTokens());
            Ollama.Handle h = handle == null ? new Ollama.Handle() : handle;
            String raw = Ollama.chat(Ollama.BRAIN, msgs, opt, attempt == 0 ? onToken : null, h);
            if (h.isCancelled()) return new Result(raw, "", false, List.of(), crisis, false, tries, secs(t0), state(), log);
            String reply = rules.autofix(raw);
            if (h.truncated) { reply = Ollama.trimToSentence(reply); h.truncated = false; }
            last = check(p, rules, romance, user, userLine, reply, prevReply, premise, cantKnow, facts, danger, log, attempt);
            if (bestIssues == null || last.size() < bestIssues.size()) { best = reply; bestIssues = last; }
            if (last.isEmpty()) break;
            log.add("try " + tries + " issues: " + codes(last));
        }

        String reply = best;
        // every try came back empty (and no fallback applies): report an error instead of saving a blank reply
        if ((reply == null || reply.isBlank()) && (bestIssues == null || bestIssues.stream().noneMatch(i -> i.sure() && i.fallbackKind() != null)))
            throw new java.io.IOException("The model gave an empty reply. Please try again.");
        boolean fallback = false;
        List<String> fixed = new ArrayList<>();
        if (tries > 1) for (Issue i : bestIssues == null ? List.<Issue>of() : bestIssues) fixed.add(i.code());
        if (bestIssues != null && !bestIssues.isEmpty()) {
            for (Issue i : bestIssues) {
                if (i.sure() && i.fallbackKind() != null) {
                    reply = fallbackLine(p, i.fallbackKind(), user);
                    fallback = true;
                    log.add("fallback (" + i.fallbackKind() + ") after " + codes(bestIssues));
                    break;
                }
            }
        }
        if (tries > 1 && !fallback && (bestIssues == null || bestIssues.isEmpty())) fixed.add("retried");

        // ---- 6. action line
        String action = "";
        boolean plain = false;
        if (settings.actionLines && reply != null && !reply.isBlank()) {
            Narrator.Cue c = narrator.narrate(p, rules, persona.displayName(file), userLine, reply);
            action = c.text();
            plain = c.plain();
            if (c.tries() > 1) log.add("action line: " + c.tries() + " tries" + (c.plain() ? " -> plain" : ""));
        }

        // stopped while the checks or the action line ran: the UI shows "Stopped", so don't save a reply it never shows
        if (handle != null && handle.isCancelled()) return new Result(reply, "", false, List.of(), crisis, fallback, tries, secs(t0), state(), log);

        // ---- 7. save
        threads.add(t, Threads.USER, userLine);
        Map<String, Object> m = threads.add(t, Threads.CHARACTER, reply);
        if (!action.isBlank()) m.put("action", action);
        if (!fixed.isEmpty()) m.put("fixed", fixed);
        return new Result(reply, action, plain, fixed, crisis, fallback, tries, secs(t0), state(), log);
    }

    /** Checks one reply. Empty = OK to show. */
    private List<Issue> check(Knowledge.Pack p, WordRules rules, PromptBuilder.Romance romance, String user, String userLine,
                              String reply, String prevReply, boolean premise, boolean cantKnow, List<String> facts,
                              boolean danger, List<String> log, int attempt) {
        List<Issue> out = new ArrayList<>();
        if (reply == null || reply.isBlank()) { out.add(new Issue("empty", "in_character", reminder("in_character", p, user, userLine, null), false, null)); return out; }
        String norm = norm(reply);

        // staying in character
        boolean ai = rules.has("ai", reply), asst = rules.has("assistant", reply) || rules.looksLikeList(reply);
        if (ai || asst) {
            String a = judgeOn() ? judge.ask("J3", vars(p, user, userLine, reply)) : null;
            out.add(new Issue(ai ? "ai" : "assistant", "in_character", reminder("in_character", p, user, userLine, null),
                    a == null || judge.flags("J3", a), danger ? "refuse" : null));
            log.add("ai/assistant rule -> J3 " + a);
        }
        if (rules.has("script", reply)) out.add(new Issue("script", "", rules.hint("script"), false, null));
        if (prevReply != null && norm.length() > 3 && norm.equals(norm(prevReply))) out.add(new Issue("repeat", "", rules.hint("repeat"), false, null));
        if (norm.length() > 6 && norm.equals(norm(userLine))) out.add(new Issue("echo", "", rules.hint("echo"), false, null));
        if (rules.has("self-name", reply)) out.add(new Issue("self-name", "", rules.hint("self-name").replace("{name}", p.name()), false, null));

        // content limits
        if (rules.has("explicit", reply)) out.add(new Issue("explicit", "too_far", reminder("too_far", p, user, userLine, null), true, "refuse"));
        else if (rules.has("romance-gate", reply)) {
            String id = romance == PromptBuilder.Romance.SWEET ? "J5a" : "J5b";
            String a = judgeOn() ? judge.ask(id, vars(p, user, userLine, reply)) : null;
            // friendly-only chat and no judge answer (judge off/unsure): the careful rule, the word rule alone counts when the
            // user was being romantic too (before, judge off meant the friendly-only limit was never enforced)
            boolean flagged = judge.flags(id, a) || (a == null && id.equals("J5b") && rules.has("romance-gate", userLine));
            if (flagged) {
                String key = id.equals("J5a") ? "too_far" : "friendly_only";
                // friendly-only: replace with a refusal only if the USER was being romantic; a plain question about the
                // character's own canon partner ("who is your partner?") may keep its (retried) answer
                boolean userRomantic = id.equals("J5a") || rules.has("romance-gate", userLine);
                out.add(new Issue(key, key, reminder(key, p, user, userLine, null), userRomantic, "refuse"));
            }
            log.add("romance rule -> " + id + " " + a);
        }
        if (danger && rules.looksLikeList(reply)) out.add(new Issue("danger", "danger", reminder("danger", p, user, userLine, null), true, "refuse"));

        // made-up memories / things they can't know (InventionCheck)
        if (premise) {
            boolean ruleAlong = !rules.denies(reply);
            String a = judgeOn() ? judge.ask("J1", vars(p, user, userLine, reply)) : null;
            boolean along = ruleAlong || judge.flags("J1", a);
            // sure: the judge says ALONG (TQQ-tested 69/70), or no judge answer and the rule found no denial
            boolean sure = judge.flags("J1", a) || (a == null && ruleAlong);
            if (along) out.add(new Issue("premise", "premise", reminder("premise", p, user, userLine, null), sure, "not_remember"));
            log.add("premise -> rule " + (ruleAlong ? "ALONG" : "denies") + ", J1 " + a);
        }
        if (cantKnow && !rules.saysNotKnowing(reply)) out.add(new Issue("cant-know", "cant_know", reminder("cant_know", p, user, userLine, null), true, "cant_know"));

        // a wrong role claim ("your sister X") must be corrected, not accepted (J8)
        if (rolePeople != null && judgeOn()) {
            Map<String, String> v8 = vars(p, user, userLine, reply);
            v8.put("people", rolePeople);
            String a8 = judge.ask("J8", v8);
            if (judge.flags("J8", a8)) out.add(new Issue("role", "role", reminder("role", p, user, userLine, null), false, null));
            log.add("role claim -> J8 " + a8);
        }

        // facts
        if (!facts.isEmpty() && judgeOn()) {
            Map<String, String> v = vars(p, user, userLine, reply);
            StringBuilder fl = new StringBuilder();
            for (String f : facts) fl.append("- ").append(f).append('\n');
            v.put("facts_lines", fl.toString().strip());
            String a = judge.ask("J2", v);
            if (judge.flags("J2", a)) out.add(new Issue("facts", "facts", reminder("facts", p, user, userLine, String.join(" ", facts)), false, null));
            log.add("facts -> J2 " + a);
        }
        return out;
    }

    // ------------------------------------------------------------ helpers

    private static final java.util.regex.Pattern ROLE = java.util.regex.Pattern.compile(
            "\\b(wife|husband|spouse|girlfriend|boyfriend|fianc[eé]e?|sister|brother|son|daughter|father|mother|dad|mom|student|teacher|master|apprentice|lieutenant|captain|partner|child|kid|sibling)\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** "your sister Nezuko" */
    private static final java.util.regex.Pattern NAMED_ROLE = java.util.regex.Pattern.compile(
            "\\b[Yy]our (?:younger |elder |older |little |big )?(wife|husband|spouse|girlfriend|boyfriend|sister|brother|son|daughter|father|mother|student|teacher|master|apprentice|lieutenant|partner)\\s+(?:the |Hashira |Captain |Lieutenant )?([A-Z][\\w'-]+)");

    /** "Name (how they relate); Name (...)": the pack's relationships, kept short. */
    private static String peopleLine(Knowledge.Pack p) {
        StringBuilder b = new StringBuilder();
        for (Object o : App.list(p.raw, "relationships")) {
            if (!(o instanceof Map<?, ?> m)) continue;
            String n = String.valueOf(m.get("name")), l = String.valueOf(m.get("line"));
            if (l.length() > 90) l = l.substring(0, 90);
            String piece = n + " (" + l + ")";
            if (b.length() + piece.length() > 900) break;
            if (b.length() > 0) b.append("; ");
            b.append(piece);
        }
        return b.toString();
    }

    private Map<String, String> vars(Knowledge.Pack p, String user, String userLine, String reply) {
        Map<String, String> v = new LinkedHashMap<>();
        Knowledge.Series ser = k.series(p.entry.seriesId());
        v.put("name", p.name());
        v.put("series_title", ser == null ? p.entry.seriesTitle() : ser.title());
        v.put("user_name", user);
        v.put("user_line", userLine == null ? "" : userLine);
        if (reply != null) v.put("reply", reply);
        return v;
    }

    private String reminder(String key, Knowledge.Pack p, String user, String userLine, String fact) {
        String text = App.str(App.map(k.reminders, key), "text");
        return text.replace("{name}", p.name()).replace("{user_name}", user)
                .replace("{user_line}", userLine == null ? "" : userLine.strip().replace("\"", "'"))
                .replace("{fact}", fact == null ? "" : fact);
    }

    private String fallbackLine(Knowledge.Pack p, String kind, String user) {
        List<String> lines = App.strings(p.fallbacks(), kind);
        if (lines.isEmpty()) return kind.equals("refuse") ? "...Let's talk about something else." : "...I don't remember that.";
        return lines.get(rng.nextInt(lines.size())).replace("{user_name}", user.startsWith("the person") ? "" : user);
    }

    private static String norm(String s) { return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9]+", " ").strip(); }
    private static double secs(long t0) { return (System.nanoTime() - t0) / 1e9; }
    private static String codes(List<Issue> l) { return String.join(",", l.stream().map(Issue::code).toList()); }
    private String state() { return judgeOn() ? "judge on" : (settings.judgeOn ? "judge unavailable" : "judge off"); }

    // ------------------------------------------------------------ thread notes (J7)

    /** Updates the thread's memory notes every 12 user turns. Returns true if updated. */
    public boolean updateNotesIfDue(Knowledge.Pack p, Threads.ChatThread t) {
        if (!settings.memoryNotes || !judgeOn()) return false;
        // snapshot: this runs on the turn thread after the reply, while the next turn may already add messages
        List<Map<String, Object>> msgs = new ArrayList<>(t.messages);
        int turns = 0;
        for (Map<String, Object> m : msgs) if (Threads.USER.equals(m.get("role"))) turns++;
        if (turns - t.notesTurns < 12) return false;
        StringBuilder lines = new StringBuilder();
        int from = Math.max(0, msgs.size() - 24);
        String user = persona.displayName(p.entry.file());
        for (int i = from; i < msgs.size(); i++) {
            Map<String, Object> m = msgs.get(i);
            if (m == null) continue;
            lines.append(Threads.USER.equals(m.get("role")) ? user : p.name()).append(": ").append(App.str(m, "text")).append('\n');
        }
        // the judge has num_ctx 2048: 24 long lines would overflow it and Ollama would cut the START (the instructions);
        // keep the newest ~4,000 chars (about 1,100 tokens)
        if (lines.length() > 4000) {
            int cut = lines.indexOf("\n", lines.length() - 4000);
            lines.delete(0, cut < 0 ? lines.length() - 4000 : cut + 1);
        }
        String oldNotes = t.notes.length() > 1200 ? t.notes.substring(0, 1200) : t.notes;
        Map<String, String> v = new LinkedHashMap<>();
        v.put("name", p.name());
        v.put("user_name", user);
        v.put("notes", oldNotes.isBlank() ? "(none)" : oldNotes);
        v.put("turns", lines.toString());
        String notes = judge.notes(v);
        if (notes == null) return false;
        t.notes = notes;
        t.notesTurns = turns;
        return true;
    }
}
