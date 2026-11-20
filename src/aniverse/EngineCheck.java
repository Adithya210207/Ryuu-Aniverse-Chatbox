package aniverse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Console test driver for the engine (plan Phase 3, also used in Phases 5-6). No UI.
 *
 *   java -cp out -Daniverse.home=<scratch copy> aniverse.EngineCheck <character> [options] [-- line1 | line2 ...]
 *
 * character: file (020_naruto_uzumaki), id, number or part of the name.
 * Options: --prompt (print the system prompt and exit, no Ollama) · --under18 · --name "Alex" · --bio "..."
 *          --no-judge · --no-actions · --keep (don't stop the private server at the end)
 * Lines: after "--", separated by " | "; otherwise read from stdin, one per line.
 * Chats are saved in <home>/history like the app: always use a scratch home for tests.
 */
public final class EngineCheck {
    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        if (args.length == 0) { out.println("usage: EngineCheck <character> [--prompt] [--under18] [--name X] [--bio X] [--no-judge] [--no-actions] [--keep] [-- a | b]"); return; }
        Knowledge k = new Knowledge();
        Knowledge.Entry e = k.find(args[0]);
        if (e == null) { out.println("No character matches '" + args[0] + "'"); System.exit(2); }
        Knowledge.Pack p = k.pack(e.file());
        if (p == null) { out.println("No knowledge pack yet for " + e.file()); System.exit(2); }

        Settings s = new Settings();
        Persona persona = Persona.load();
        boolean promptOnly = false, keep = false;
        List<String> lines = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--prompt" -> promptOnly = true;
                case "--under18" -> persona.under18 = true;
                case "--name" -> persona.setGlobal(new Persona.Profile(args[++i], persona.global().bio(), persona.global().howMet()));
                case "--bio" -> persona.setGlobal(new Persona.Profile(persona.global().name(), args[++i], persona.global().howMet()));
                case "--no-judge" -> s.judgeOn = false;
                case "--no-actions" -> s.actionLines = false;
                case "--keep" -> keep = true;
                case "--" -> {
                    // each argument is one line; an argument may also hold several lines split by " | "
                    for (int j = i + 1; j < args.length; j++)
                        for (String l : args[j].split("\\s+\\|\\s+")) if (!l.isBlank()) lines.add(l.strip());
                    i = args.length;
                }
                default -> { out.println("unknown option " + args[i]); System.exit(2); }
            }
        }
        if (persona.under18 == null) persona.under18 = false;

        TurnPipeline pipe = new TurnPipeline(k, s, persona);
        Threads threads = Threads.load(e.file());
        Threads.ChatThread t = threads.create();
        String sys = pipe.system(p, t);
        out.println("== " + p.name() + " (" + e.file() + ") · band " + p.ageBand() + " · romance "
                + PromptBuilder.romance(p.ageBand(), persona.under18) + " · system prompt " + PromptBuilder.tokens(sys) + " tokens");
        if (promptOnly) { out.println(sys); return; }

        if (lines.isEmpty()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String l;
            while ((l = br.readLine()) != null) if (!l.isBlank()) lines.add(l.strip());
        }

        if (!Ollama.ensureRunning()) { out.println("Ollama private server could not start on " + Ollama.PORT); System.exit(3); }
        long t0 = System.nanoTime();
        Ollama.load(Ollama.BRAIN);
        pipe.judge.ready = s.judgeOn && Ollama.loadJudge();
        out.printf("== models ready in %.1f s · judge %s%n", (System.nanoTime() - t0) / 1e9, pipe.judge.ready ? "on" : "off");
        pipe.prime(p, t);
        out.println(p.name() + " (greeting): " + p.greeting());
        try {
            for (String line : lines) {
                out.println();
                out.println("YOU: " + line);
                TurnPipeline.Result r = pipe.run(p, threads, t, line, null, null);
                if (r.crisis()) out.println("   [crisis note: " + App.str(k.crisisNote, "text") + "]");
                if (!r.action().isBlank()) out.println("   *" + r.action() + "*" + (r.actionPlain() ? " (plain)" : ""));
                out.println(p.name().toUpperCase() + ": " + r.reply());
                out.printf("   [%.1f s · tries %d%s%s · %s]%n", r.seconds(), r.tries(), r.fixed().isEmpty() ? "" : " · fixed " + r.fixed(),
                        r.fallback() ? " · FALLBACK" : "", r.judgeState());
                for (String l : r.log()) out.println("   - " + l);
                if (pipe.updateNotesIfDue(p, t)) out.println("   [notes updated]\n" + t.notes);
            }
            threads.save();
        } finally {
            if (!keep) {
                Ollama.unload(Ollama.JUDGE);
                Ollama.unload(Ollama.BRAIN);
                Ollama.stopIfStartedByUs();
            }
        }
    }
}
