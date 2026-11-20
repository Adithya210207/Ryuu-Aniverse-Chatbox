package aniverse;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Developer tool: runs 3 turns (fact, trap, assistant-bait from eval/characters/*.tests.json) for many characters with the
 * models loaded ONCE, and writes one JSON line per character to the report file.
 *
 *   java -cp out -Daniverse.home=<scratch copy> aniverse.BatchCheck <report.jsonl> [numbers like 1,2,3 | all]
 *
 * Action lines are off (saves time); the judge is on. Use a scratch home: chats are saved in <home>/history.
 */
public final class BatchCheck {
    private static final Pattern DENY = Pattern.compile("\\b(not|isn't|is not|never|no,|wasn't|aren't|don't have|haven't|didn't|wrong|mistaken|actually)\\b");
    private static final Pattern CODE = Pattern.compile("```|\\bdef \\w+\\(|\\bimport \\w+|print\\(|\\bas an ai\\b|language model", Pattern.CASE_INSENSITIVE);

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        Path report = Path.of(args[0]);
        Knowledge k = new Knowledge();
        List<Knowledge.Entry> todo = new ArrayList<>();
        String sel = args.length > 1 ? args[1] : "all";
        if (sel.equals("all")) { for (Knowledge.Entry e : k.roster()) if (k.hasPack(e.file())) todo.add(e); }
        else for (String n : sel.split(",")) { Knowledge.Entry e = k.find(n.strip()); if (e != null && k.hasPack(e.file())) todo.add(e); }

        Settings s = new Settings();
        s.actionLines = false;
        Persona persona = Persona.load();
        persona.under18 = false;
        persona.setGlobal(new Persona.Profile("Sam", "", ""));
        TurnPipeline pipe = new TurnPipeline(k, s, persona);

        if (!Ollama.ensureRunning()) { System.out.println("Ollama private server could not start"); System.exit(3); }
        Ollama.load(Ollama.BRAIN);
        pipe.judge.ready = Ollama.loadJudge();
        System.out.println("models ready, judge " + (pipe.judge.ready ? "on" : "off") + ", " + todo.size() + " characters");
        int done = 0;
        try (PrintStream out = new PrintStream(Files.newOutputStream(report, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND), true, StandardCharsets.UTF_8)) {
            for (Knowledge.Entry e : todo) {
                Map<String, Object> rec = new LinkedHashMap<>();
                rec.put("file", e.file());
                rec.put("name", e.name());
                try {
                    Knowledge.Pack p = k.pack(e.file());
                    Path tf = App.HOME.resolve("eval").resolve("characters").resolve(e.file() + ".tests.json");
                    Map<String, Object> tests = Files.exists(tf) ? App.readJson(tf) : Map.of();
                    Threads threads = Threads.load(e.file());
                    Threads.ChatThread t = threads.create();
                    pipe.prime(p, t);
                    List<Object> turns = new ArrayList<>();

                    Map<String, Object> fact = first(tests, "facts");
                    if (fact != null) {
                        Map<String, Object> r = turn(pipe, p, threads, t, App.str(fact, "ask"));
                        String reply = App.str(r, "reply").toLowerCase(Locale.ROOT);
                        boolean hit = false;
                        for (String ex : App.strings(fact, "expect")) if (reply.contains(ex.toLowerCase(Locale.ROOT))) hit = true;
                        r.put("kind", "fact");
                        r.put("expect", App.strings(fact, "expect"));
                        r.put("pass", hit);
                        turns.add(r);
                    }
                    Map<String, Object> trap = first(tests, "traps");
                    if (trap != null) {
                        Map<String, Object> r = turn(pipe, p, threads, t, App.str(trap, "ask"));
                        String reply = App.str(r, "reply").toLowerCase(Locale.ROOT);
                        r.put("kind", "trap");
                        r.put("truth", App.str(trap, "truth"));
                        r.put("pass", DENY.matcher(reply).find() || r.get("fallback").equals(true) || ((Number) r.get("tries")).intValue() > 1 && DENY.matcher(reply).find());
                        turns.add(r);
                    }
                    Map<String, Object> bait = first(tests, "assistant_bait");
                    if (bait != null) {
                        Map<String, Object> r = turn(pipe, p, threads, t, App.str(bait, "ask"));
                        r.put("kind", "assistant_bait");
                        r.put("pass", !CODE.matcher(App.str(r, "reply")).find());
                        turns.add(r);
                    }
                    rec.put("turns", turns);
                } catch (Exception ex) {
                    rec.put("error", String.valueOf(ex));
                }
                out.println(Json.write(rec));
                done++;
                System.out.println(done + "/" + todo.size() + " " + e.file());
            }
        } finally {
            Ollama.unload(Ollama.JUDGE);
            Ollama.unload(Ollama.BRAIN);
            Ollama.stopIfStartedByUs();
        }
        System.out.println("BATCH DONE");
    }

    private static Map<String, Object> turn(TurnPipeline pipe, Knowledge.Pack p, Threads threads, Threads.ChatThread t, String ask) throws Exception {
        TurnPipeline.Result r = pipe.run(p, threads, t, ask, null, null);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ask", ask);
        m.put("reply", r.reply());
        m.put("tries", r.tries());
        m.put("fallback", r.fallback());
        m.put("seconds", Math.round(r.seconds() * 10) / 10.0);
        m.put("log", r.log());
        return m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> first(Map<String, Object> tests, String key) {
        List<Object> l = App.list(tests, key);
        return l.isEmpty() ? null : (Map<String, Object>) l.get(0);
    }
}
