package aniverse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** App-wide constants and small file helpers. */
public final class App {
    private App() {}

    public static final String NAME = "Ryuu Aniverse";
    public static final String VERSION = "v2 (with portraits)";
    public static final String TAGLINE = "One Universe. Infinite Characters. Endless Conversations.";

    /**
     * The app folder (knowledge/, assets/, history/, settings). From -Daniverse.home (the packaged exe and
     * tests on a scratch copy set it), otherwise the current folder.
     */
    public static final Path HOME = Path.of(System.getProperty("aniverse.home", ".")).toAbsolutePath().normalize();

    /**
     * Where the user's own files go (chats, profile, settings, favourites, log). From -Daniverse.data; the packaged app
     * (-Daniverse.packaged=true) uses %LOCALAPPDATA%\Ryuu Aniverse so it works from a read-only install folder; otherwise HOME.
     */
    public static final Path DATA = dataDir();

    private static Path dataDir() {
        String d = System.getProperty("aniverse.data");
        if (d != null && !d.isBlank()) return Path.of(d).toAbsolutePath().normalize();
        if (Boolean.getBoolean("aniverse.packaged")) {
            String base = System.getenv("LOCALAPPDATA");
            if (base == null || base.isBlank()) base = System.getProperty("user.home");
            return Path.of(base, "Ryuu Aniverse").toAbsolutePath().normalize();
        }
        return HOME;
    }

    public static Path knowledge() { return HOME.resolve("knowledge"); }
    public static Path history() { return DATA.resolve("history"); }
    public static Path assets() { return HOME.resolve("assets"); }

    public static String read(Path p) {
        try { return Files.readString(p, StandardCharsets.UTF_8); }
        catch (Exception e) { throw new IllegalStateException("Cannot read " + p + ": " + e.getMessage(), e); }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readJson(Path p) {
        return (Map<String, Object>) Json.parse(read(p));
    }

    /** Writes via a temp file then moves it in place, so a crash can't leave a half-written file (TQQ). */
    /** synchronized: the turn thread (notes) and the UI thread can save the same file at once and share its .tmp name. */
    public static synchronized void writeAtomic(Path f, String text) {
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot write " + f + ": " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- loose JSON getters

    public static String str(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o == null ? "" : String.valueOf(o);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String key) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Map<?, ?> mm ? (Map<String, Object>) mm : Map.of();
    }

    public static List<String> strings(Map<String, Object> m, String key) {
        return list(m, key).stream().map(String::valueOf).toList();
    }

    public static boolean bool(Map<String, Object> m, String key, boolean def) {
        Object o = m == null ? null : m.get(key);
        return o instanceof Boolean b ? b : def;
    }
}
