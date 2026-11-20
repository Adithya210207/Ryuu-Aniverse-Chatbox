package aniverse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One character's chat threads, saved in history/NNN_id.json (adapted from TQQ):
 * { "active_id": "...", "threads": [ {id, title, created, updated, scene, notes, notes_turns,
 *   messages: [{time, role: user|character, text, action, fixed}]} ] }
 */
public final class Threads {
    public static final String USER = "user";
    public static final String CHARACTER = "character";
    public static final String NEW_TITLE = "New chat";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static final class ChatThread {
        public String id, title, created, updated;
        public String scene = "";
        public String notes = "";
        /** User turns already covered by the notes. */
        public int notesTurns = 0;
        public final List<Map<String, Object>> messages = new ArrayList<>();

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("title", title); m.put("created", created); m.put("updated", updated);
            m.put("scene", scene); m.put("notes", notes); m.put("notes_turns", notesTurns);
            m.put("messages", messages);
            return m;
        }

        @SuppressWarnings("unchecked")
        static ChatThread fromMap(Map<String, Object> m) {
            ChatThread t = new ChatThread();
            t.id = App.str(m, "id");
            t.title = m.get("title") == null ? NEW_TITLE : App.str(m, "title");
            t.created = m.get("created") == null ? now() : App.str(m, "created");
            t.updated = m.get("updated") == null ? t.created : App.str(m, "updated");
            t.scene = App.str(m, "scene");
            t.notes = App.str(m, "notes");
            t.notesTurns = m.get("notes_turns") instanceof Number n ? n.intValue() : 0;
            for (Object o : App.list(m, "messages")) if (o instanceof Map<?, ?> mm) t.messages.add((Map<String, Object>) mm);
            return t;
        }

        public int userTurns() {
            int n = 0;
            for (Map<String, Object> m : messages) if (USER.equals(m.get("role"))) n++;
            return n;
        }

        /** Last character reply text, or null. */
        public String lastReply() {
            for (int i = messages.size() - 1; i >= 0; i--) if (CHARACTER.equals(messages.get(i).get("role"))) return App.str(messages.get(i), "text");
            return null;
        }

        /** All earlier text (both sides), for "mentioned before" checks. */
        public String allText() {
            StringBuilder b = new StringBuilder();
            for (Map<String, Object> m : messages) b.append(App.str(m, "text")).append('\n');
            return b.toString();
        }
    }

    private final String file;
    private final List<ChatThread> threads = new ArrayList<>();
    private String activeId;

    private Threads(String file) { this.file = file; }

    public static Path path(String characterFile) { return App.history().resolve(characterFile + ".json"); }

    @SuppressWarnings("unchecked")
    public static Threads load(String characterFile) {
        Threads t = new Threads(characterFile);
        Path f = path(characterFile);
        try {
            if (Files.exists(f)) {
                Map<String, Object> root = App.readJson(f);
                for (Object o : App.list(root, "threads")) {
                    if (!(o instanceof Map<?, ?> mm)) continue;
                    ChatThread c = ChatThread.fromMap((Map<String, Object>) mm);
                    if (c.id.isBlank() || t.find(c.id) != null) c.id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                    t.threads.add(c);
                }
                Object a = root.get("active_id");
                t.activeId = a == null ? null : String.valueOf(a);
            }
        } catch (Exception bad) {
            // unreadable: keep a copy instead of overwriting it with an empty file on the next save (TQQ)
            try {
                String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                Files.copy(f, f.resolveSibling(characterFile + ".unreadable-" + stamp + ".json"));
            } catch (Exception ignored) {}
            t.threads.clear();
        }
        if (t.threads.isEmpty()) t.create();
        if (t.find(t.activeId) == null) t.activeId = t.sorted().get(0).id;
        return t;
    }

    /** synchronized with add(): the notes update saves from the turn thread while the UI thread may save or a turn may add. */
    public synchronized void save() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("active_id", activeId);
        List<Object> list = new ArrayList<>();
        for (ChatThread t : threads) list.add(t.toMap());
        root.put("threads", list);
        App.writeAtomic(path(file), Json.pretty(root));
    }

    public List<ChatThread> sorted() {
        List<ChatThread> out = new ArrayList<>(threads);
        out.sort(Comparator.comparing((ChatThread t) -> t.updated).reversed());
        return out;
    }

    public ChatThread find(String id) {
        if (id == null) return null;
        for (ChatThread t : threads) if (t.id.equals(id)) return t;
        return null;
    }

    public ChatThread active() {
        ChatThread t = find(activeId);
        if (t == null) { t = sorted().get(0); activeId = t.id; }
        return t;
    }

    public void setActive(String id) { if (find(id) != null) activeId = id; }

    public ChatThread create() {
        ChatThread t = new ChatThread();
        t.id = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        t.title = NEW_TITLE;
        t.created = now();
        t.updated = t.created;
        threads.add(t);
        activeId = t.id;
        return t;
    }

    public void delete(String id) {
        threads.removeIf(t -> t.id.equals(id));
        if (threads.isEmpty()) create();
        if (find(activeId) == null) activeId = sorted().get(0).id;
    }

    public List<ChatThread> all() { return sorted(); }

    public synchronized Map<String, Object> add(ChatThread t, String role, String text) {
        if (text == null) text = "";
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", now());
        m.put("role", role);
        m.put("text", text);
        t.messages.add(m);
        t.updated = App.str(m, "time");
        if (NEW_TITLE.equals(t.title) && USER.equals(role)) {
            String title = text.strip().replaceAll("\\s+", " ");
            t.title = title.length() > 40 ? title.substring(0, 40).strip() + "…" : title;
        }
        return m;
    }

    public static String now() { return LocalDateTime.now().format(FMT); }

    /** "now", "5m", "3h", "yesterday", "Nov 9" */
    public static String ago(String time) {
        try {
            LocalDateTime t = LocalDateTime.parse(time, FMT), n = LocalDateTime.now();
            long min = java.time.Duration.between(t, n).toMinutes();
            if (min < 1) return "now";
            if (min < 60) return min + "m";
            if (t.toLocalDate().equals(n.toLocalDate())) return (min / 60) + "h";
            if (t.toLocalDate().equals(n.toLocalDate().minusDays(1))) return "yesterday";
            return t.format(DateTimeFormatter.ofPattern("MMM d"));
        } catch (Exception e) {
            return time;
        }
    }

    public static String preview(ChatThread t) {
        if (t.messages.isEmpty()) return "No messages yet";
        Map<String, Object> m = t.messages.get(t.messages.size() - 1);
        return (USER.equals(m.get("role")) ? "You: " : "") + App.str(m, "text").replaceAll("\\s+", " ");
    }

    public static String shortTime(Object time) {
        String t = String.valueOf(time);
        int sp = t.lastIndexOf(' ');
        return sp >= 0 ? t.substring(sp + 1) : t;
    }

    /** Plain-text transcript for "Export this chat". */
    public static String transcript(Knowledge.Pack p, String userName, ChatThread t) {
        StringBuilder b = new StringBuilder();
        b.append(p.name()).append(" — ").append(t.title).append('\n');
        b.append(App.NAME).append(" · you as ").append(userName).append(" · exported ").append(now()).append('\n');
        b.append("=".repeat(50)).append("\n\n");
        for (Map<String, Object> m : t.messages) {
            boolean me = USER.equals(m.get("role"));
            b.append('[').append(m.get("time")).append("] ").append(me ? userName : p.name()).append(":\n");
            String act = App.str(m, "action");
            if (!act.isBlank()) b.append('*').append(act).append("*\n");
            b.append(m.get("text")).append("\n\n");
        }
        return b.toString();
    }
}
