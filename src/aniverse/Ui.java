package aniverse;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Shared UI pieces (from TQQ): icons, animations, per-character colours, portraits and avatars. */
public final class Ui {
    private Ui() {}

    // Segoe Fluent Icons (built into Windows 11)
    public static final String I_HOME = "", I_ADD = "", I_SEARCH = "", I_DELETE = "", I_EDIT = "",
            I_SEND = "", I_STOP = "", I_CLOSE = "", I_EXPORT = "", I_STAR = "", I_STAR_ON = "",
            I_PERSON = "";

    public static Label icon(String glyph) {
        Label l = new Label(glyph);
        l.getStyleClass().add("icon");
        return l;
    }

    public static Button iconButton(String glyph, String tip) {
        Button b = new Button();
        b.setGraphic(icon(glyph));
        b.getStyleClass().add("icon-btn");
        if (tip != null) b.setTooltip(new Tooltip(tip));
        return b;
    }

    public static Button button(String glyph, String text, String... classes) {
        Button b = new Button(text);
        if (glyph != null) b.setGraphic(icon(glyph));
        b.getStyleClass().addAll(classes);
        return b;
    }

    public static Region grow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** CSS looked-up colours for one character: -accent, -accent-soft, -accent-glow, -accent-line, -accent-deep. */
    public static String accentVars(String hex) {
        Color c = Color.web(hex);
        Color deep = c.deriveColor(0, 1.0, 0.45, 1.0);
        return "-accent: " + hex + ";"
                + "-accent-soft: " + rgba(c, 0.14) + ";"
                + "-accent-glow: " + rgba(c, 0.38) + ";"
                + "-accent-line: " + rgba(c, 0.55) + ";"
                + "-accent-deep: " + rgba(deep, 1.0) + ";";
    }

    public static String rgba(Color c, double a) {
        return String.format("rgba(%d,%d,%d,%.2f)", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255), a);
    }

    public static void rise(Node n, double dy, int ms, int delayMs) {
        n.setOpacity(0);
        n.setTranslateY(dy);
        FadeTransition f = new FadeTransition(Duration.millis(ms), n);
        f.setToValue(1);
        TranslateTransition t = new TranslateTransition(Duration.millis(ms), n);
        t.setToY(0);
        t.setInterpolator(Interpolator.SPLINE(0.2, 0.8, 0.2, 1.0));
        ParallelTransition p = new ParallelTransition(f, t);
        p.setDelay(Duration.millis(delayMs));
        p.play();
    }

    /** Animated "typing" dots (only animate while shown). */
    public static HBox typingDots() {
        HBox box = new HBox(5);
        box.getStyleClass().add("typing");
        Timeline tl = new Timeline();
        for (int k = 0; k < 3; k++) {
            Circle c = new Circle(3.6);
            c.getStyleClass().add("typing-dot");
            c.setOpacity(0.25);
            box.getChildren().add(c);
            double off = k * 160;
            tl.getKeyFrames().addAll(new KeyFrame(Duration.millis(off), new KeyValue(c.opacityProperty(), 0.25)),
                    new KeyFrame(Duration.millis(off + 260), new KeyValue(c.opacityProperty(), 1.0)),
                    new KeyFrame(Duration.millis(off + 560), new KeyValue(c.opacityProperty(), 0.25)));
        }
        tl.getKeyFrames().add(new KeyFrame(Duration.millis(1100)));
        tl.setCycleCount(Timeline.INDEFINITE);
        box.visibleProperty().addListener((o, a, v) -> { if (v && box.getScene() != null) tl.play(); else tl.stop(); });
        box.sceneProperty().addListener((o, a, sc) -> { if (sc == null) tl.stop(); else if (box.isVisible()) tl.play(); });
        return box;
    }

    public static Timeline breathe(Node n) {
        Timeline tl = new Timeline(new KeyFrame(Duration.ZERO, new KeyValue(n.opacityProperty(), 1.0)),
                new KeyFrame(Duration.millis(900), new KeyValue(n.opacityProperty(), 0.3, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(1800), new KeyValue(n.opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
        tl.setCycleCount(Timeline.INDEFINITE);
        return tl;
    }

    // ------------------------------------------------------------ portraits and avatars

    private static final Map<String, Image> IMAGES = new HashMap<>();
    private static final Map<String, Path> FILES = new HashMap<>();

    /** assets/portraits/NNN_<id>.png|jpg|jpeg, or null if the user hasn't added one (a generated avatar is used then). */
    private static Path portraitFile(Knowledge.Entry e) {
        if (FILES.containsKey(e.file())) return FILES.get(e.file());
        Path found = null;
        for (String ext : new String[] {".png", ".jpg", ".jpeg"}) {
            Path p = App.assets().resolve("portraits").resolve(e.file() + ext);
            if (Files.isRegularFile(p)) { found = p; break; }
        }
        FILES.put(e.file(), found);
        return found;
    }

    public static boolean hasPortrait(Knowledge.Entry e) { return portraitFile(e) != null; }

    /** The portrait scaled down to about `width` px and loaded in the background (100 full-size pictures would be hundreds of MB). */
    public static Image portrait(Knowledge.Entry e, int width) {
        Path f = portraitFile(e);
        if (f == null) return null;
        String key = e.file() + "@" + width;
        return IMAGES.computeIfAbsent(key, k -> new Image(f.toUri().toString(), width, 0, true, true, true));
    }

    /**
     * Runs `r` once the (background-loading) image knows its size. The images are cached and shared, so the
     * listeners are removed again after use: otherwise every card/avatar ever built would stay in memory.
     */
    static void whenLoaded(Image img, Runnable r) {
        if (img.getWidth() > 0 && img.getHeight() > 0) { r.run(); return; }
        if (img.isError()) return;
        javafx.beans.InvalidationListener[] l = new javafx.beans.InvalidationListener[1];
        l[0] = o -> {
            boolean ok = img.getWidth() > 0 && img.getHeight() > 0;
            if (!ok && !img.isError()) return;
            img.widthProperty().removeListener(l[0]);
            img.heightProperty().removeListener(l[0]);
            img.errorProperty().removeListener(l[0]);
            if (ok) r.run();
        };
        img.widthProperty().addListener(l[0]);
        img.heightProperty().addListener(l[0]);
        img.errorProperty().addListener(l[0]);
    }

    /** Round avatar; without a portrait: the character's glyph on their colour. */
    public static Node avatar(Knowledge.Entry e, double size) {
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setPrefSize(size, size);
        box.setStyle(accentVars(e.accent()));
        Image img = portrait(e, 220);
        if (img == null) {
            Label k = new Label(e.glyph().substring(0, 1));
            k.getStyleClass().add("avatar-kanji");
            k.setStyle("-fx-font-size: " + Math.round(size * 0.45) + "px;");
            box.getChildren().add(k);
            box.getStyleClass().add("avatar-fallback");
            return box;
        }
        ImageView iv = new ImageView(img);
        iv.setSmooth(true);
        Runnable crop = () -> {          // the picture loads in the background: crop once its size is known
            if (img.getWidth() <= 0) return;
            double side = Math.min(img.getWidth(), img.getHeight()) * 0.56;
            double y = Math.min(img.getHeight() * 0.10, img.getHeight() - side);
            iv.setViewport(new Rectangle2D((img.getWidth() - side) / 2, y, side, side));
        };
        whenLoaded(img, crop);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setClip(new Circle(size / 2, size / 2, size / 2));
        box.getChildren().add(iv);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("avatar");
        return box;
    }

    /** Big picture fill for panels/cards/stage: the portrait, or a glyph-on-gradient when there is none. */
    public static Node picture(Knowledge.Entry e, double focusY, double glyphSize, int width) {
        Image img = portrait(e, width);
        if (img != null) return new Cover(img, focusY);
        StackPane p = new StackPane();
        p.getStyleClass().add("picture-fallback");
        Label g = new Label(e.glyph());
        g.getStyleClass().add("picture-glyph");
        // several characters side by side must still fit the panel, so shrink them
        double fit = glyphSize / (1 + 0.55 * (Math.max(1, e.glyph().length()) - 1));
        g.setStyle("-fx-font-size: " + Math.round(fit) + "px;");
        g.setMinWidth(Region.USE_PREF_SIZE);
        g.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);
        p.getChildren().add(g);
        p.setMinSize(0, 0);
        return p;
    }
}
