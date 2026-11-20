package aniverse;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Home: the Spotlight (5 tall panels, hover to meet, click to talk) and the Gallery of all 100 characters
 * with search, series filter, favourites and sort. A card opens the character's profile sheet.
 */
public final class HomeView extends StackPane {
    private final Main app;
    private final Knowledge k;
    private final StackPane overlay = new StackPane();
    private final FlowPane grid = new FlowPane(16, 16);
    private final Label count = new Label();
    private final TextField search = new TextField();
    private final ComboBox<String> seriesBox = new ComboBox<>();
    private final ComboBox<String> sortBox = new ComboBox<>();
    private final ToggleButton favOnly = new ToggleButton("★ Favourites");

    public HomeView(Main app) {
        this.app = app;
        this.k = app.knowledge();
        getStyleClass().add("home");

        VBox page = new VBox(0, spotlight(), gallery());
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("clear-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        overlay.setVisible(false);
        overlay.setManaged(false);
        getChildren().addAll(scroll, overlay);
        refresh();
    }

    // ------------------------------------------------------------ spotlight

    /** Five characters: favourites first, then a daily rotation of the rest. */
    private List<Knowledge.Entry> pick() {
        List<Knowledge.Entry> ready = new ArrayList<>();
        for (Knowledge.Entry e : k.roster()) if (k.hasPack(e.file())) ready.add(e);
        List<Knowledge.Entry> out = new ArrayList<>();
        for (Knowledge.Entry e : ready) if (app.favorites().isFavorite(e.file()) && out.size() < 5) out.add(e);
        int start = ready.isEmpty() ? 0 : (LocalDate.now().getDayOfYear() * 5) % ready.size();
        for (int i = 0; i < ready.size() && out.size() < 5; i++) {
            Knowledge.Entry e = ready.get((start + i) % ready.size());
            if (!out.contains(e)) out.add(e);
        }
        return out;
    }

    private Node spotlight() {
        StackPane root = new StackPane();
        root.setMinHeight(470);
        root.setPrefHeight(470);
        root.setMaxHeight(470);
        Quint quint = new Quint();
        List<Knowledge.Entry> chosen = pick();
        for (int i = 0; i < chosen.size(); i++) {
            Node p = panel(chosen.get(i), i, quint);
            quint.add(p);
            Ui.rise(p, 60, 600, 100 + i * 80);
        }
        quint.setOnMouseExited(e -> quint.focus(-1));

        Label logo = new Label("RYUU ANIVERSE");
        logo.getStyleClass().add("logo");
        Label sub = new Label(App.TAGLINE);
        sub.getStyleClass().add("logo-sub");
        VBox brand = new VBox(-2, logo, sub);
        Circle dot = new Circle(4);
        dot.getStyleClass().addAll("dot", "dot-busy");
        Timeline breath = Ui.breathe(dot);
        breath.play();
        sceneProperty().addListener((o, a, sc) -> { if (sc == null) breath.stop(); });
        Label status = new Label("Waking up Ollama…");
        status.getStyleClass().add("home-status-text");
        HBox pill = new HBox(8, dot, status);
        pill.setAlignment(Pos.CENTER);
        pill.getStyleClass().add("glass-pill");
        HBox top = new HBox(brand, Ui.grow(), pill);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(20, 28, 0, 28));
        top.setMaxHeight(Region.USE_PREF_SIZE);
        top.setMouseTransparent(true);
        StackPane.setAlignment(top, Pos.TOP_CENTER);
        app.server().thenAcceptAsync(ok -> {
            boolean brain = ok && Ollama.hasModel(Ollama.BRAIN);
            Platform.runLater(() -> {
                breath.stop();
                dot.setOpacity(1);
                dot.getStyleClass().removeAll("dot-busy", "dot-ok", "dot-error");
                dot.getStyleClass().add(ok && brain ? "dot-ok" : "dot-error");
                status.setText(!ok ? "Ollama not running" : brain ? "Local AI ready" : "AI models missing: run \"Setup models\" once");
            });
        });
        Label hint = new Label("hover to meet them  ·  click to talk  ·  scroll down for all characters");
        hint.getStyleClass().add("home-hint");
        hint.setMouseTransparent(true);
        StackPane.setAlignment(hint, Pos.BOTTOM_CENTER);
        StackPane.setMargin(hint, new Insets(0, 0, 14, 0));
        root.getChildren().addAll(quint, top, hint);
        Ui.rise(top, -16, 500, 40);
        return root;
    }

    private Node panel(Knowledge.Entry e, int index, Quint quint) {
        Knowledge.Pack pack = k.pack(e.file());
        StackPane p = new StackPane();
        p.getStyleClass().add("panel");
        p.setStyle(Ui.accentVars(e.accent()));
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(p.widthProperty());
        clip.heightProperty().bind(p.heightProperty());
        p.setClip(clip);

        Node photo = Ui.picture(e, 0.12, 150, 760);
        Region shade = new Region();
        shade.getStyleClass().add("panel-shade");
        Region wash = new Region();
        wash.getStyleClass().add("panel-wash");
        wash.setOpacity(0.45);
        Label kanji = new Label(String.join("\n", e.glyph().split("")));
        kanji.getStyleClass().add("panel-kanji");
        kanji.setOpacity(0);
        kanji.setMouseTransparent(true);
        StackPane.setAlignment(kanji, Pos.TOP_RIGHT);
        StackPane.setMargin(kanji, new Insets(90, 22, 0, 0));

        Label numText = new Label(e.num());
        numText.getStyleClass().add("panel-num");
        Label small = new Label(e.glyph());
        small.getStyleClass().add("panel-kanji-small");
        HBox num = new HBox(10, numText, small);
        num.setAlignment(Pos.BASELINE_LEFT);
        Region bar = new Region();
        bar.getStyleClass().add("panel-bar");
        Label name = new Label(e.name());
        name.getStyleClass().add("panel-name");
        name.setMinWidth(Region.USE_PREF_SIZE);
        int len = Math.max(8, e.name().length());
        p.widthProperty().addListener((o, a, w) -> name.setStyle("-fx-font-size: "
                + Math.round(Math.max(22, Math.min(52, (w.doubleValue() - 52) / (0.58 * len)))) + "px;"));
        Label tag = new Label(e.seriesTitle() + " · " + pack.tagline());
        tag.getStyleClass().add("panel-tag");
        tag.setWrapText(true);
        tag.setMinWidth(0);
        tag.setMinHeight(36);
        tag.setPrefHeight(36);
        tag.setMaxHeight(36);
        tag.setAlignment(Pos.TOP_LEFT);
        Label quote = new Label("“" + pack.greeting() + "”");
        quote.getStyleClass().add("panel-quote");
        quote.setWrapText(true);
        quote.setMinWidth(0);
        quote.setPrefWidth(320);
        quote.setMaxWidth(320);
        quote.setMinHeight(72);
        quote.setPrefHeight(72);
        quote.setMaxHeight(72);
        quote.setAlignment(Pos.BOTTOM_LEFT);
        Label talk = new Label("Talk   →");
        talk.getStyleClass().add("panel-talk");
        VBox reveal = new VBox(12, quote, talk);
        reveal.setOpacity(0);
        reveal.setTranslateY(14);
        VBox info = new VBox(6, num, bar, name, tag, reveal);
        info.setAlignment(Pos.BOTTOM_LEFT);
        info.setPadding(new Insets(0, 24, 40, 24));
        VBox.setMargin(reveal, new Insets(10, 0, 0, 0));
        StackPane.setAlignment(info, Pos.BOTTOM_LEFT);
        p.getChildren().addAll(photo, shade, wash, kanji, info);

        ParallelTransition[] running = new ParallelTransition[1];
        quint.revealer(index, in -> {
            if (running[0] != null) running[0].stop();
            running[0] = animate(reveal, wash, kanji, in);
        });
        p.setOnMouseEntered(ev -> quint.focus(index));
        p.setOnMouseClicked(ev -> app.openChat(e, false, null));
        return p;
    }

    private static ParallelTransition animate(Node reveal, Node wash, Node kanji, boolean in) {
        Duration d = Duration.millis(in ? 340 : 220);
        FadeTransition f = new FadeTransition(d, reveal);
        f.setToValue(in ? 1 : 0);
        TranslateTransition t = new TranslateTransition(d, reveal);
        t.setToY(in ? 0 : 14);
        FadeTransition w = new FadeTransition(d, wash);
        w.setToValue(in ? 1.0 : 0.45);
        FadeTransition kk = new FadeTransition(d, kanji);
        kk.setToValue(in ? 0.92 : 0.0);
        ParallelTransition all = new ParallelTransition(f, t, w, kk);
        all.play();
        return all;
    }

    /** Lays panels out side by side; widths follow animated weights (from TQQ). */
    private static final class Quint extends Pane {
        private final List<Node> panels = new ArrayList<>();
        private final List<DoubleProperty> weights = new ArrayList<>();
        private final Map<Integer, Consumer<Boolean>> revealers = new HashMap<>();
        private int revealed = -1;
        private Timeline anim;

        void revealer(int idx, Consumer<Boolean> r) { revealers.put(idx, r); }

        void add(Node n) {
            DoubleProperty w = new SimpleDoubleProperty(1);
            w.addListener((o, a, b) -> requestLayout());
            panels.add(n);
            weights.add(w);
            getChildren().add(n);
        }

        void focus(int idx) {
            if (idx != revealed) {
                if (revealed >= 0 && revealers.containsKey(revealed)) revealers.get(revealed).accept(false);
                if (idx >= 0 && revealers.containsKey(idx)) revealers.get(idx).accept(true);
                revealed = idx;
            }
            if (anim != null) anim.stop();
            List<KeyValue> kv = new ArrayList<>();
            for (int i = 0; i < weights.size(); i++) {
                double target = idx < 0 ? 1.0 : (i == idx ? 2.3 : 0.84);
                kv.add(new KeyValue(weights.get(i), target, Interpolator.SPLINE(0.25, 0.8, 0.25, 1.0)));
            }
            anim = new Timeline(new KeyFrame(Duration.millis(520), kv.toArray(new KeyValue[0])));
            anim.play();
        }

        @Override
        protected void layoutChildren() {
            double gap = 2, total = 0;
            for (DoubleProperty w : weights) total += w.get();
            if (panels.isEmpty()) return;
            double avail = getWidth() - gap * (panels.size() - 1), x = 0;
            for (int i = 0; i < panels.size(); i++) {
                double w = avail * weights.get(i).get() / total;
                panels.get(i).resizeRelocate(Math.round(x), 0, Math.round(w), getHeight());
                x += w + gap;
            }
        }
    }

    // ------------------------------------------------------------ gallery

    private Node gallery() {
        Label title = new Label("All characters");
        title.getStyleClass().add("gallery-title");
        count.getStyleClass().add("drawer-count");
        search.setPromptText("Search 100 characters…");
        search.getStyleClass().add("search-box");
        search.setPrefWidth(300);
        search.textProperty().addListener((o, a, b) -> refresh());
        seriesBox.getItems().add("All series");
        TreeSet<String> series = new TreeSet<>();
        for (Knowledge.Entry e : k.roster()) series.add(e.seriesTitle());
        seriesBox.getItems().addAll(series);
        seriesBox.setValue("All series");
        seriesBox.getStyleClass().add("filter-box");
        seriesBox.valueProperty().addListener((o, a, b) -> refresh());
        sortBox.getItems().addAll("Number", "Name", "Series");
        sortBox.setValue("Number");
        sortBox.getStyleClass().add("filter-box");
        sortBox.valueProperty().addListener((o, a, b) -> refresh());
        favOnly.getStyleClass().add("filter-chip");
        favOnly.selectedProperty().addListener((o, a, b) -> { favOnly.getStyleClass().remove("filter-chip-on"); if (b) favOnly.getStyleClass().add("filter-chip-on"); refresh(); });

        // in a small window only the search box and the series list give way (the rest showed "All charact…", "Num…")
        for (Region r : new Region[] {title, count, sortBox, favOnly}) r.setMinWidth(Region.USE_PREF_SIZE);
        search.setMinWidth(150);
        seriesBox.setPrefWidth(210);
        seriesBox.setMinWidth(130);
        HBox bar = new HBox(12, title, count, Ui.grow(), search, seriesBox, sortBox, favOnly);
        bar.setAlignment(Pos.CENTER_LEFT);
        grid.setPadding(new Insets(18, 0, 0, 0));
        grid.setAlignment(Pos.TOP_CENTER);
        VBox box = new VBox(8, bar, grid);
        box.setPadding(new Insets(26, 32, 40, 32));
        return box;
    }

    private void refresh() {
        String q = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        String ser = seriesBox.getValue();
        List<Knowledge.Entry> list = new ArrayList<>();
        for (Knowledge.Entry e : k.roster()) {
            if (favOnly.isSelected() && !app.favorites().isFavorite(e.file())) continue;
            if (ser != null && !ser.equals("All series") && !ser.equals(e.seriesTitle())) continue;
            if (!q.isEmpty()) {
                Knowledge.Pack p = k.hasPack(e.file()) ? k.pack(e.file()) : null;
                String hay = (e.name() + " " + e.seriesTitle() + " " + (p == null ? "" : p.tagline() + " " + p.nativeName())).toLowerCase(Locale.ROOT);
                if (!hay.contains(q)) continue;
            }
            list.add(e);
        }
        switch (sortBox.getValue() == null ? "Number" : sortBox.getValue()) {
            case "Name" -> list.sort(Comparator.comparing(Knowledge.Entry::name));
            case "Series" -> list.sort(Comparator.comparing(Knowledge.Entry::seriesTitle).thenComparingInt(Knowledge.Entry::number));
            default -> list.sort(Comparator.comparingInt(Knowledge.Entry::number));
        }
        grid.getChildren().clear();
        for (Knowledge.Entry e : list) grid.getChildren().add(card(e));
        count.setText(list.size() + " of " + k.roster().size());
    }

    private Node card(Knowledge.Entry e) {
        boolean ready = k.hasPack(e.file());
        StackPane c = new StackPane();          // outer: border + glow (not clipped)
        c.getStyleClass().add("gallery-card");
        c.setStyle(Ui.accentVars(e.accent()));
        c.setMinSize(168, 224);
        c.setPrefSize(168, 224);
        c.setMaxSize(168, 224);
        StackPane in = new StackPane();         // inner: the picture, clipped to the rounded corners
        Rectangle clip = new Rectangle(168, 224);
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        in.setClip(clip);
        c.getChildren().add(in);
        Node pic = Ui.picture(e, 0.1, 80, 360);
        Region shade = new Region();
        shade.getStyleClass().add("panel-shade");
        Label num = new Label(e.num());
        num.getStyleClass().add("card-num");
        Label name = new Label(e.name());
        name.getStyleClass().add("card-name");
        name.setWrapText(true);
        Label ser = new Label(ready ? e.seriesTitle() : e.seriesTitle() + " · coming soon");
        ser.getStyleClass().add("card-series");
        ser.setWrapText(true);
        Region bar = new Region();
        bar.getStyleClass().add("panel-bar");
        VBox info = new VBox(3, num, bar, name, ser);
        info.setAlignment(Pos.BOTTOM_LEFT);
        info.setPadding(new Insets(0, 12, 12, 12));
        StackPane.setAlignment(info, Pos.BOTTOM_LEFT);
        in.getChildren().addAll(pic, shade, info);
        if (ready) {
            Button star = new Button(app.favorites().isFavorite(e.file()) ? Ui.I_STAR_ON : Ui.I_STAR);
            star.getStyleClass().addAll("icon", "card-fav");
            star.setOnAction(ev -> { app.favorites().toggle(e.file()); star.setText(app.favorites().isFavorite(e.file()) ? Ui.I_STAR_ON : Ui.I_STAR); if (favOnly.isSelected()) refresh(); });
            star.addEventHandler(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);  // the click must not also open the card's profile
            StackPane.setAlignment(star, Pos.TOP_RIGHT);
            StackPane.setMargin(star, new Insets(8, 8, 0, 0));
            c.getChildren().add(star);
            c.setOnMouseClicked(ev -> showProfile(e));
        } else {
            c.setOpacity(0.45);
        }
        return c;
    }

    // ------------------------------------------------------------ profile sheet

    private void showProfile(Knowledge.Entry e) {
        Knowledge.Pack p = k.pack(e.file());
        if (p == null) return;
        VBox sheet = new VBox(14);
        sheet.getStyleClass().add("profile-sheet");
        sheet.setStyle(Ui.accentVars(e.accent()));
        sheet.setMaxWidth(860);
        sheet.setMaxHeight(Region.USE_PREF_SIZE);

        StackPane pic = new StackPane(Ui.picture(e, 0.1, 110, 520));
        pic.setMinSize(220, 300);
        pic.setPrefSize(220, 300);
        pic.setMaxSize(220, 300);
        Rectangle clip = new Rectangle(220, 300);
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        pic.setClip(clip);

        Label name = new Label(e.name());
        name.getStyleClass().add("hist-title");
        Label nat = new Label(p.nativeName());
        nat.getStyleClass().add("profile-native");
        Label meta = new Label(e.seriesTitle() + " · " + p.tagline());
        meta.getStyleClass().add("thread-sub");
        FlowPane tags = new FlowPane(6, 6);
        for (String t : App.strings(p.raw, "tags")) {
            Label l = new Label(t);
            l.getStyleClass().add("tag-chip");
            tags.getChildren().add(l);
        }
        Label persona = new Label(blurb(App.str(p.raw, "persona")));
        persona.getStyleClass().add("opt-text");
        persona.setWrapText(true);
        Label snap = new Label("Where they are in the story: " + App.str(p.raw, "snapshot"));
        snap.getStyleClass().add("snapshot-note");
        snap.setWrapText(true);
        Label spoil = new Label("⚠ Spoilers: characters are at the end of their story, so they know how it ends.");
        spoil.getStyleClass().add("setup-note");
        spoil.setWrapText(true);
        Label tryLbl = new Label("TRY SAYING");
        tryLbl.getStyleClass().add("setup-label");
        FlowPane starters = new FlowPane(8, 8);
        for (String s : p.starters()) {
            Button b = new Button(s);
            b.getStyleClass().add("starter");
            b.setOnAction(ev -> app.openChat(e, false, s));
            starters.getChildren().add(b);
        }
        boolean hasHistory = Files.exists(Threads.path(e.file()));
        Button go = Ui.button(null, hasHistory ? "Continue chat" : "Start chat", "panel-talk");
        go.setOnAction(ev -> app.openChat(e, false, null));
        Button fresh = Ui.button(Ui.I_ADD, "New chat", "mini");
        fresh.setOnAction(ev -> app.openChat(e, true, null));
        Button close = Ui.iconButton(Ui.I_CLOSE, "Close");
        close.setOnAction(ev -> hideProfile());
        HBox actions = new HBox(10, go);
        if (hasHistory) actions.getChildren().add(fresh);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox right = new VBox(8, new HBox(name, Ui.grow(), close), nat, meta, tags, persona, snap, spoil, tryLbl, starters, actions);
        HBox.setHgrow(right, Priority.ALWAYS);
        sheet.getChildren().add(new HBox(24, pic, right));
        overlay.getChildren().setAll(sheet);
        overlay.getStyleClass().setAll("hist-overlay");
        StackPane.setAlignment(sheet, Pos.CENTER);
        overlay.setOnMouseClicked(ev -> { if (ev.getTarget() == overlay) hideProfile(); });
        overlay.setManaged(true);
        overlay.setVisible(true);
        Ui.rise(sheet, 20, 280, 0);
    }

    /** First two sentences of the (second-person) persona, turned into third person for the profile sheet. */
    static String blurb(String persona) {
        String s = persona.strip().replaceFirst("^You are ", "");
        String[] parts = s.split("(?<=[.!?])\\s+");
        String out = parts.length > 1 ? parts[0] + " " + parts[1] : s;
        return out.replaceAll("\\byour\\b", "their").replaceAll("\\bYour\\b", "Their").replaceAll("\\byou\\b", "they")
                .replaceAll("\\bYou\\b", "They").replaceAll("\\bthey are\\b", "they're");
    }

    /** Esc: closes the profile sheet if one is open. */
    boolean escape() {
        if (!overlay.isVisible()) return false;
        hideProfile();
        return true;
    }

    private void hideProfile() {
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.getChildren().clear();
    }
}
