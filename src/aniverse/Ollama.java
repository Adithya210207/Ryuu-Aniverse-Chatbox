package aniverse;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Talks to the app's OWN Ollama server on 127.0.0.1:11436 (plan section 4.3).
 * The normal Ollama (11434) must keep 1 model at a time, and TQQ Chatbox v2 uses 11435; this app needs
 * the brain + the judge together, so it starts a private "ollama serve" with 2 models allowed and the same
 * model folder, and stops it on exit (only if it started it). Adapted from TQQ's Ollama class.
 */
public final class Ollama {
    public static final String PORT = "11436";
    public static final String URL = "http://127.0.0.1:" + PORT;
    public static final String BRAIN = "aniverse-brain";
    public static final String JUDGE = "aniverse-judge";

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static volatile Process startedByUs;
    private static boolean hookAdded;

    private Ollama() {}

    /** Lets the UI stop a reply while it streams. */
    public static final class Handle {
        volatile InputStream in;
        volatile boolean cancelled;
        public volatile boolean truncated;
        public void cancel() {
            cancelled = true;
            try { if (in != null) in.close(); } catch (IOException ignored) {}
        }
        public boolean isCancelled() { return cancelled; }
    }

    /** A non-streamed answer. */
    public record Answer(String text, boolean truncated, long evalCount, double seconds) {}

    // ------------------------------------------------------------ server

    public static boolean ping() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/api/tags")).timeout(Duration.ofSeconds(3)).GET().build();
            return HTTP.send(req, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** Starts the private server if nothing answers on 11436. True when it is up. */
    public static synchronized boolean ensureRunning() {
        if (ping()) return true;
        // our server is still starting (an earlier call timed out): wait for it, don't start a second one
        // (a second start would overwrite startedByUs, so the first server would never be stopped)
        Process mine = startedByUs;
        if (mine != null && mine.isAlive()) {
            for (int k = 0; k < 30; k++) {
                try { Thread.sleep(1000); } catch (InterruptedException e) { return false; }
                if (ping()) return true;
                if (!mine.isAlive()) break;
            }
            if (mine.isAlive()) return false;
        }
        Path exe = findExe();
        if (exe == null) return false;
        try {
            ProcessBuilder pb = new ProcessBuilder(exe.toString(), "serve")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            Map<String, String> env = pb.environment();
            env.put("OLLAMA_HOST", "127.0.0.1:" + PORT);
            env.put("OLLAMA_MAX_LOADED_MODELS", "2");
            if (env.get("OLLAMA_MODELS") == null || env.get("OLLAMA_MODELS").isBlank()) {
                String dir = userEnv("OLLAMA_MODELS");
                if (dir != null) env.put("OLLAMA_MODELS", dir);
            }
            startedByUs = pb.start();
            if (!hookAdded) {
                hookAdded = true;
                Runtime.getRuntime().addShutdownHook(new Thread(Ollama::stopIfStartedByUs, "stop-private-ollama"));
            }
        } catch (IOException e) {
            return false;
        }
        for (int k = 0; k < 30; k++) {
            try { Thread.sleep(1000); } catch (InterruptedException e) { return false; }
            if (ping()) return true;
        }
        return false;
    }

    public static boolean startedByUs() { return startedByUs != null; }

    public static void stopIfStartedByUs() {
        Process p = startedByUs;
        if (p == null) return;
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
        startedByUs = null;
    }

    private static String userEnv(String name) {
        try {
            Process p = new ProcessBuilder("reg", "query", "HKCU\\Environment", "/v", name).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            p.waitFor();
            for (String line : out.split("\\R")) {
                String t = line.strip();
                if (t.startsWith(name)) {
                    String[] parts = t.split("\\s{2,}|\\t", 3);
                    if (parts.length == 3 && !parts[2].isBlank()) return expand(parts[2].strip());
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /** REG_EXPAND_SZ values keep %VAR% (e.g. %USERPROFILE%\.ollama\models): expand them. */
    private static String expand(String v) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("%([^%]+)%").matcher(v);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            String val = System.getenv(m.group(1));
            m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(val == null ? m.group() : val));
        }
        m.appendTail(b);
        return b.toString();
    }

    private static Path findExe() {
        List<Path> c = new ArrayList<>();
        String local = System.getenv("LOCALAPPDATA");
        String pf = System.getenv("ProgramFiles");
        try {
            if (local != null) c.add(Path.of(local, "Programs", "Ollama", "ollama.exe"));
            if (pf != null) c.add(Path.of(pf, "Ollama", "ollama.exe"));
        } catch (Exception ignored) {}
        String path = System.getenv("PATH");
        // PATH entries can be quoted or hold characters Windows paths don't allow: skip those instead of crashing
        if (path != null) for (String dir : path.split(java.io.File.pathSeparator)) {
            String d = dir.strip().replace("\"", "");
            if (d.isEmpty()) continue;
            try { c.add(Path.of(d, "ollama.exe")); } catch (Exception ignored) {}
        }
        for (Path p : c) if (Files.isRegularFile(p)) return p;
        return null;
    }

    // ------------------------------------------------------------ models

    @SuppressWarnings("unchecked")
    private static List<String> names(String path) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL + path)).timeout(Duration.ofSeconds(5)).GET().build();
            Map<String, Object> m = (Map<String, Object>) Json.parse(HTTP.send(req, HttpResponse.BodyHandlers.ofString()).body());
            List<String> out = new ArrayList<>();
            for (Object o : (List<Object>) m.get("models")) out.add(String.valueOf(((Map<String, Object>) o).get("name")));
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static List<String> models() { return names("/api/tags"); }
    public static List<String> loaded() { return names("/api/ps"); }

    private static boolean contains(List<String> list, String model) {
        String want = model.toLowerCase();
        for (String n : list) { String h = n.toLowerCase(); if (h.equals(want) || h.equals(want + ":latest")) return true; }
        return false;
    }
    public static boolean hasModel(String model) { return contains(models(), model); }
    public static boolean isLoaded(String model) { return contains(loaded(), model); }

    public static void load(String model) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("keep_alive", "30m");
        post("/api/generate", body, Duration.ofMinutes(5));
    }

    public static void unload(String model) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("keep_alive", 0);
            post("/api/generate", body, Duration.ofSeconds(20));
        } catch (Exception ignored) {}
    }

    /**
     * Loads the judge next to the brain and checks the brain is still in memory (needs 2 loaded models).
     * False (judge not used) if the judge is missing or pushed the brain out; the brain is then reloaded.
     */
    public static boolean loadJudge() {
        try {
            if (!hasModel(JUDGE)) return false;
            load(JUDGE);
            if (isLoaded(BRAIN)) return true;
            unload(JUDGE);
            load(BRAIN);
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    static String post(String path, Map<String, Object> body, Duration timeout) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + path)).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8)).build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() != 200) throw new IOException("Ollama " + r.statusCode() + ": " + r.body());
        return r.body();
    }

    // ------------------------------------------------------------ chat

    private static Map<String, Object> chatBody(String model, List<Map<String, Object>> messages, Map<String, Object> options, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("keep_alive", "30m");
        if (options != null && !options.isEmpty()) body.put("options", options);
        return body;
    }

    /** One non-streamed reply. */
    @SuppressWarnings("unchecked")
    public static Answer chatOnce(String model, List<Map<String, Object>> messages, Map<String, Object> options, Duration timeout)
            throws IOException, InterruptedException {
        long t0 = System.nanoTime();
        Map<String, Object> r = (Map<String, Object>) Json.parse(post("/api/chat", chatBody(model, messages, options, false), timeout));
        Map<String, Object> msg = (Map<String, Object>) r.get("message");
        String text = msg == null || msg.get("content") == null ? "" : String.valueOf(msg.get("content"));
        long evals = r.get("eval_count") instanceof Number n ? n.longValue() : 0;
        return new Answer(text, "length".equals(r.get("done_reason")), evals, (System.nanoTime() - t0) / 1e9);
    }

    /** Streams one reply; onToken gets each piece. Returns the whole text. */
    @SuppressWarnings("unchecked")
    public static String chat(String model, List<Map<String, Object>> messages, Map<String, Object> options,
                              Consumer<String> onToken, Handle handle) throws IOException, InterruptedException {
        // timeout = waiting for the response headers (model load + prompt read); without it a stuck server hangs the turn forever
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL + "/api/chat")).timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(chatBody(model, messages, options, true)), StandardCharsets.UTF_8)).build();
        HttpResponse<InputStream> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() != 200) {
            throw new IOException("Ollama " + resp.statusCode() + ": " + new String(resp.body().readAllBytes(), StandardCharsets.UTF_8));
        }
        handle.in = resp.body();
        if (handle.cancelled) handle.cancel();
        StringBuilder all = new StringBuilder();
        // safety net: a stalled model must end in an error message, never in an endless wait
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(180_000); } catch (InterruptedException done) { return; }
            try { resp.body().close(); } catch (Exception ignored) {}
        }, "chat-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                Map<String, Object> m = (Map<String, Object>) Json.parse(line);
                if (m.containsKey("error")) throw new IOException(String.valueOf(m.get("error")));
                Map<String, Object> msg = (Map<String, Object>) m.get("message");
                if (msg != null && msg.get("content") instanceof String tok && !tok.isEmpty()) {
                    all.append(tok);
                    if (onToken != null) onToken.accept(tok);
                }
                if (Boolean.TRUE.equals(m.get("done"))) {
                    if ("length".equals(m.get("done_reason"))) handle.truncated = true;
                    break;
                }
            }
        } catch (IOException e) {
            if (!handle.cancelled) throw e;
        } finally {
            watchdog.interrupt();
        }
        return all.toString();
    }

    /** Pre-reads a prompt so the first reply after opening a chat is fast (TQQ: 11.8 s -> 3.7 s). */
    public static void prime(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("num_predict", 1);
            post("/api/chat", chatBody(BRAIN, messages, options, false), Duration.ofMinutes(3));
        } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ text helpers (TQQ)

    /** A cut-off reply: keep it up to its last full sentence if that keeps a third of it, else end with "…". */
    public static String trimToSentence(String s) {
        String t = s.strip();
        if (t.isEmpty() || endsSentence(t)) return t;
        int cut = -1;
        for (int k = t.length() - 1; k >= 0; k--) {
            char c = t.charAt(k);
            if (c == '.' || c == '!' || c == '?' || c == '…') { cut = k; break; }
        }
        if (cut >= t.length() / 3) {
            int end = cut + 1;
            while (end < t.length() && "\"'”’)".indexOf(t.charAt(end)) >= 0) end++;
            return t.substring(0, end).strip();
        }
        return t.replaceAll("[\\s,;:\\-]+$", "") + "…";
    }

    public static boolean endsSentence(String t) {
        return !t.isEmpty() && ".!?…~\"'”’)".indexOf(t.charAt(t.length() - 1)) >= 0;
    }
}
