package aniverse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Tiny error log (app.log in the user-data folder) so a problem on someone's PC can be diagnosed. Never throws. */
public final class Log {
    private Log() {}

    private static final long MAX = 512 * 1024;

    public static synchronized void error(String what, Throwable t) {
        try {
            Path f = App.DATA.resolve("app.log");
            Files.createDirectories(f.getParent());
            if (Files.exists(f) && Files.size(f) > MAX) Files.move(f, f.resolveSibling("app.old.log"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            StringWriter sw = new StringWriter();
            sw.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("  ").append(what).append('\n');
            if (t != null) t.printStackTrace(new PrintWriter(sw));
            Files.writeString(f, sw.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {}
    }
}
