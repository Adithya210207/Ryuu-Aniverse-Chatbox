package aniverse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Favourites and recently opened characters (favorites.json). */
public final class Favorites {
    private final Set<String> favs = new LinkedHashSet<>();
    private final List<String> recent = new ArrayList<>();

    private static Path file() { return App.DATA.resolve("favorites.json"); }

    public static Favorites load() {
        Favorites f = new Favorites();
        try {
            if (Files.exists(file())) {
                Map<String, Object> m = App.readJson(file());
                f.favs.addAll(App.strings(m, "favorites"));
                f.recent.addAll(App.strings(m, "recent"));
            }
        } catch (Exception ignored) {}
        return f;
    }

    public void save() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("favorites", new ArrayList<>(favs));
        m.put("recent", recent);
        App.writeAtomic(file(), Json.pretty(m));
    }

    public boolean isFavorite(String file) { return favs.contains(file); }
    public Set<String> favorites() { return favs; }
    public List<String> recent() { return recent; }

    public void toggle(String file) {
        if (!favs.remove(file)) favs.add(file);
        save();
    }

    /** Moves a character to the front of the recent list (max 8). */
    public void touch(String file) {
        recent.remove(file);
        recent.add(0, file);
        while (recent.size() > 8) recent.remove(recent.size() - 1);
        save();
    }
}
