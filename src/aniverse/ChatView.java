package aniverse;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * One character's chat: rail (home + recent), drawer (threads), centre (messages + composer), stage (portrait).
 * The turn itself runs in TurnPipeline on a background thread.
 */
public final class ChatView extends StackPane {
    private final Main app;
    private final Knowledge.Entry entry;
    private final Knowledge.Pack pack;
    private final Threads threads;
    private Threads.ChatThread thread;

    private final VBox messages = new VBox(16);
    private final ScrollPane scroll = new ScrollPane();
    private final TextField input = new TextField();
    private final Button send = new Button();
    private final VBox drawerList = new VBox(4);
    private final Label heading = new Label();
    private final Label sub = new Label();
    private final Label count = new Label();
    private final Label judgeBadge = new Label();
    private final Label stageStatus = new Label();
    private final Circle statusDot = new Circle(4);
    private final FlowPane starters = new FlowPane(8, 8);
    private final Button actionsToggle = new Button();
    private final Button typingToggle = new Button();
    private final Button retryBtn = new Button("Try again");
    private static final int MAX_INPUT = 1500;
    private javafx.animation.Timeline typing;
    private Runnable typingEnd;
    private ProfileSheet sheet;
    private boolean disposed;

    private volatile boolean busy;
    private volatile boolean ready;
    private Ollama.Handle handle;
    private Node pendingCard;

    public ChatView(Main app, Knowledge.Entry entry, Knowledge.Pack pack, boolean newThread, String starter) {
        this.app = app;
        this.entry = entry;
        this.pack = pack;
        this.threads = Threads.load(entry.file());
        this.thread = newThread && !threads.active().messages.isEmpty() ? threads.create() : threads.active();
        getStyleClass().add("chat-view");
        setStyle(Ui.accentVars(entry.accent()));

        HBox row = new HBox(rail(), drawer(), centre(), stage());
        getChildren().add(row);
        loadThread(thread);
        if (starter != null) { input.setText(starter); input.positionCaret(starter.length()); }

        // first time with this character: a quick "who are you to them?" sheet inside the chat (also asks the age once)
        if (!Boolean.getBoolean("aniverse.smoke") && System.getProperty("aniverse.shots") == null && System.getProperty("aniverse.live") == null
                && app.persona().override(entry.file()) == null) Platform.runLater(() -> showProfile(true));

        retryBtn.setOnAction(e -> { app.resetModels(); connect(); });
        connect();
    }

    /** Loads the models (first chat of the session) and enables the composer; on failure shows the reason and a Try again button. */
    private void connect() {
        input.setDisable(true);
        retryBtn.setVisible(false);
        setStatus("Loading the models… (about 15 s the first time)", "dot-busy");
        app.models().thenAccept(err -> Platform.runLater(() -> {
            if (disposed) return;                       // the user already left this chat
            if (err != null) { setStatus(err, "dot-error"); retryBtn.setVisible(true); return; }
            ready = true;
            input.setDisable(false);
            if (sheet == null) input.requestFocus();    // don't pull focus out of the open profile sheet
            updateBadge();
            setStatus("Here", "dot-ok");
            Thread t = new Thread(() -> app.pipeline().prime(pack, thread), "prime");
            t.setDaemon(true);
            t.start();
        }));
    }

    public Knowledge.Entry entry() { return entry; }

    private void showProfile(boolean meeting) {
        if (sheet != null || disposed) return;          // one sheet at a time
        ProfileSheet[] s = new ProfileSheet[1];
        s[0] = new ProfileSheet(app, entry, pack, meeting, () -> {
            getChildren().remove(s[0]);
            sheet = null;
            if (ready && !busy) input.requestFocus();
        });
        sheet = s[0];
        getChildren().add(s[0]);
    }

    /** Esc: closes the profile sheet (unless the age answer is still needed). */
    boolean escape() { return sheet != null && sheet.escape(); }

    public void dispose() {
        disposed = true;
        endTyping();
        if (handle != null) handle.cancel();
        threads.save();
    }

    /** Stops the typing effect and shows the whole reply (a stopped Timeline does not run its onFinished). */
    private void endTyping() {
        if (typing != null) typing.stop();
        typing = null;
        if (typingEnd != null) { Runnable r = typingEnd; typingEnd = null; r.run(); }
    }

    // ------------------------------------------------------------ rail

    private Node rail() {
        VBox rail = new VBox(12);
        rail.getStyleClass().add("rail");
        rail.setAlignment(Pos.TOP_CENTER);
        Button home = Ui.iconButton(Ui.I_HOME, "Home");
        home.setOnAction(e -> app.showHome());
        Region div = new Region();
        div.getStyleClass().add("rail-div");
        rail.getChildren().addAll(home, div);
        for (String f : app.favorites().recent()) {
            Knowledge.Entry e = app.knowledge().entry(f);
            if (e == null || !app.knowledge().hasPack(f)) continue;
            Node av = Ui.avatar(e, 40);
            StackPane w = new StackPane(av);
            w.setMaxSize(40, 40);
            w.getStyleClass().add("rail-sis");
            if (e == entry) w.getStyleClass().add("rail-sis-active");
            w.setOnMouseClicked(ev -> app.openChat(e, false, null));
            Tooltip.install(w, new Tooltip(e.name()));
            rail.getChildren().add(w);
        }
        Region g = new Region();
        VBox.setVgrow(g, Priority.ALWAYS);
        Label brand = new Label("ANIVERSE");
        brand.getStyleClass().add("rail-brand");
        StackPane bw = new StackPane(brand);
        bw.setMinHeight(90);
        rail.getChildren().addAll(g, bw);
        return rail;
    }

    // ------------------------------------------------------------ drawer

    private Node drawer() {
        VBox d = new VBox(12);
        d.getStyleClass().add("drawer");
        Label title = new Label(pack.name().split(" ")[0]);
        title.getStyleClass().add("drawer-title");
        count.getStyleClass().add("drawer-count");
        HBox head = new HBox(10, title, count);
        head.setAlignment(Pos.CENTER_LEFT);
        Button nu = Ui.button(Ui.I_ADD, "New chat", "new-chat");
        nu.setMaxWidth(Double.MAX_VALUE);
        nu.setOnAction(e -> { if (busy) return; thread = threads.create(); threads.save(); loadThread(thread); });
        ScrollPane sp = new ScrollPane(drawerList);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("clear-scroll");
        VBox.setVgrow(sp, Priority.ALWAYS);
        Button export = Ui.button(Ui.I_EXPORT, "Export", "mini");
        export.setOnAction(e -> export());
        Button del = Ui.button(Ui.I_DELETE, "Delete", "mini", "mini-danger");
        del.setOnAction(e -> deleteThread());
        Button rename = Ui.button(Ui.I_EDIT, "Rename", "mini");
        rename.setOnAction(e -> renameThread());
        for (Button b : new Button[] {rename, export, del}) {
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, Priority.ALWAYS);
        }
        HBox bottom = new HBox(6, rename, export, del);
        typingToggle.getStyleClass().add("toggle");
        typingToggle.setMaxWidth(Double.MAX_VALUE);
        typingToggle.setTooltip(new Tooltip("Show replies being typed out, letter by letter."));
        typingToggle.setOnAction(e -> {
            app.settings().liveTyping = !app.settings().liveTyping;
            app.settings().save();
            updateTypingToggle();
        });
        updateTypingToggle();
        d.getChildren().addAll(head, nu, sp, typingToggle, bottom);
        return d;
    }

    private void refreshDrawer() {
        drawerList.getChildren().clear();
        List<Threads.ChatThread> all = threads.all();
        count.setText(String.valueOf(all.size()));
        for (Threads.ChatThread t : all) {
            Label title = new Label(t.title);
            title.getStyleClass().add("thread-title");
            Label when = new Label(Threads.ago(t.updated));
            when.getStyleClass().add("thread-when");
            title.setMinWidth(0);
            when.setMinWidth(Region.USE_PREF_SIZE);
            HBox top = new HBox(8, title, Ui.grow(), when);
            Label prev = new Label(Threads.preview(t));
            prev.getStyleClass().add("thread-preview");
            prev.setMaxWidth(220);
            VBox card = new VBox(2, top, prev);
            card.getStyleClass().add("thread-card");
            if (t == thread) card.getStyleClass().add("thread-card-active");
            card.setOnMouseClicked(e -> { if (busy || t == thread) return; thread = t; threads.setActive(t.id); loadThread(t); });
            drawerList.getChildren().add(card);
        }
    }

    // ------------------------------------------------------------ centre

    private Node centre() {
        heading.getStyleClass().add("thread-heading");
        sub.getStyleClass().add("thread-sub");
        VBox titles = new VBox(2, heading, sub);
        judgeBadge.getStyleClass().add("judge-badge");
        actionsToggle.getStyleClass().add("toggle");
        actionsToggle.setOnAction(e -> {
            app.settings().actionLines = !app.settings().actionLines;
            app.settings().save();
            updateToggle();
        });
        Button me = Ui.button(Ui.I_PERSON, "Your profile", "context-chip");
        me.setOnAction(e -> showProfile(false));
        titles.setMinWidth(0);
        heading.setMinWidth(0);
        sub.setMinWidth(0);
        sub.setMaxWidth(Double.MAX_VALUE);
        sub.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        Tooltip subTip = new Tooltip();
        subTip.textProperty().bind(sub.textProperty());
        sub.setTooltip(subTip);
        Tooltip headTip = new Tooltip();
        headTip.textProperty().bind(heading.textProperty());
        heading.setTooltip(headTip);
        sub.getStyleClass().add("scene-link");
        sub.setOnMouseClicked(e -> changeScene());
        HBox.setHgrow(titles, Priority.ALWAYS);
        for (javafx.scene.layout.Region r : new javafx.scene.layout.Region[] {judgeBadge, actionsToggle, me}) r.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(10, titles, judgeBadge, actionsToggle, me);
        top.setAlignment(Pos.CENTER_LEFT);
        top.getStyleClass().add("topbar");

        messages.getStyleClass().add("messages");
        VBox col = new VBox(messages);
        col.getStyleClass().add("column");
        col.setMaxWidth(860);
        HBox centred = new HBox(col);
        centred.setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(col, Priority.ALWAYS);
        scroll.setContent(centred);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("clear-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        Label mark = new Label(String.join("\n", entry.glyph().split("")));
        mark.getStyleClass().add("watermark");
        mark.setMouseTransparent(true);
        StackPane.setAlignment(mark, Pos.CENTER_RIGHT);
        StackPane mid = new StackPane(scroll);   // the stage on the right already shows the portrait or glyph
        VBox.setVgrow(mid, Priority.ALWAYS);

        input.setPromptText("Say something to " + pack.name().split(" ")[0] + "…");
        // a very long message would push the character's instructions out of the model's memory
        input.textProperty().addListener((o, a, b) -> { if (b != null && b.length() > MAX_INPUT) input.setText(b.substring(0, MAX_INPUT)); });
        input.getStyleClass().add("composer-input");
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setOnAction(e -> onSend());
        send.getStyleClass().add("send");
        send.setOnAction(e -> onSend());
        HBox composer = new HBox(8, input, send);
        composer.setAlignment(Pos.CENTER);
        composer.getStyleClass().add("composer");
        input.focusedProperty().addListener((o, a, f) -> { composer.getStyleClass().remove("composer-focus"); if (f) composer.getStyleClass().add("composer-focus"); });
        Label hint = new Label("Enter to send · characters are AI; keep it kind");
        hint.getStyleClass().add("composer-hint");
        VBox wrap = new VBox(6, composer, hint);
        wrap.getStyleClass().add("composer-wrap");
        wrap.setAlignment(Pos.CENTER);

        VBox c = new VBox(top, mid, wrap);
        HBox.setHgrow(c, Priority.ALWAYS);
        updateToggle();
        setSendIcon(false);
        return c;
    }

    private void changeScene() {
        if (busy) return;
        String res = SceneDialog.show(getScene().getWindow(), thread.scene, pack.defaultScene());
        if (res == null) return;
        thread.scene = res;
        threads.save();
        sub.setText("✎  " + (res.isBlank() ? pack.defaultScene() : res));
    }

    private void updateTypingToggle() {
        boolean on = app.settings().liveTyping;
        typingToggle.setText("Typing effect: " + (on ? "on" : "off"));
        typingToggle.getStyleClass().remove("toggle-on");
        if (on) typingToggle.getStyleClass().add("toggle-on");
    }

    /** Types the finished (already checked) reply out letter by letter; an invisible full copy keeps the card size steady. */
    private void typeIn(VBox holder) {
        VBox card = (VBox) holder.getChildren().get(0);
        Label full = (Label) card.lookup(".her-text");
        if (full == null || full.getText().length() < 2) return;
        String text = full.getText();
        int idx = card.getChildren().indexOf(full);
        Label shown = new Label("");
        shown.getStyleClass().addAll(full.getStyleClass());
        shown.setWrapText(true);
        shown.setMaxWidth(600);
        shown.setAlignment(Pos.TOP_LEFT);
        full.setOpacity(0);
        card.getChildren().remove(full);
        StackPane st = new StackPane(full, shown);
        st.setAlignment(Pos.TOP_LEFT);
        card.getChildren().add(idx, st);
        endTyping();
        int total = text.length();
        double ms = Math.min(3200, Math.max(500, total * 16.0));
        int[] done = {0};
        typing = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(30), e -> {
            done[0] = Math.min(total, done[0] + (int) Math.ceil(total / (ms / 30.0)));
            shown.setText(text.substring(0, done[0]));
            scroll.setVvalue(1.0);
        }));
        typing.setCycleCount((int) Math.ceil(ms / 30.0) + 2);
        typingEnd = () -> { full.setOpacity(1); st.getChildren().remove(shown); };
        typing.setOnFinished(e -> endTyping());
        typing.play();
    }

    private void updateToggle() {
        boolean on = app.settings().actionLines;
        actionsToggle.setText("Actions: " + (on ? "on" : "off"));
        actionsToggle.getStyleClass().remove("toggle-on");
        if (on) actionsToggle.getStyleClass().add("toggle-on");
    }

    private void updateBadge() {
        boolean on = app.pipeline().judge.ready && app.settings().judgeOn;
        judgeBadge.setText(on ? "Judge on" : "Judge off");
        judgeBadge.getStyleClass().removeAll("judge-badge-on", "judge-badge-off");
        judgeBadge.getStyleClass().add(on ? "judge-badge-on" : "judge-badge-off");
        judgeBadge.setTooltip(new Tooltip(on ? "phi4-mini checks every reply before you see it."
                : "Word rules only: the judge model is off or could not be loaded."));
    }

    private void setSendIcon(boolean stop) {
        send.setGraphic(Ui.icon(stop ? Ui.I_STOP : Ui.I_SEND));
        send.getStyleClass().remove("send-stop");
        if (stop) send.getStyleClass().add("send-stop");
    }

    // ------------------------------------------------------------ stage

    private Node stage() {
        StackPane s = new StackPane();
        s.getStyleClass().add("stage");
        Node pic = Ui.picture(entry, 0.1, 220, 640);
        Region fade = new Region();
        fade.getStyleClass().add("stage-fade");
        Label num = new Label(entry.num());
        num.getStyleClass().add("stage-num");
        Label kj = new Label(entry.glyph());
        kj.getStyleClass().add("stage-kanji");
        HBox nk = new HBox(10, num, kj);
        nk.setAlignment(Pos.BASELINE_LEFT);
        Label name = new Label(pack.name());
        name.getStyleClass().add("stage-name");
        name.setWrapText(true);
        name.setStyle("-fx-font-size: " + (pack.name().length() > 14 ? 34 : 46) + "px;");
        Label tag = new Label(entry.seriesTitle() + " · " + pack.tagline());
        tag.getStyleClass().add("stage-tag");
        tag.setWrapText(true);
        statusDot.getStyleClass().addAll("dot", "dot-busy");
        stageStatus.getStyleClass().add("stage-status");
        stageStatus.setWrapText(true);
        HBox st = new HBox(8, statusDot, stageStatus);
        st.setAlignment(Pos.CENTER_LEFT);
        Label ai = new Label("AI character");
        ai.getStyleClass().add("ai-label");
        ai.setTooltip(new Tooltip("You are chatting with an AI-written character, not a real person."));
        retryBtn.getStyleClass().add("mini");
        retryBtn.setVisible(false);
        retryBtn.managedProperty().bind(retryBtn.visibleProperty());
        VBox info = new VBox(6, nk, name, tag, st, retryBtn, ai);
        VBox.setMargin(ai, new Insets(6, 0, 0, 0));
        info.setPadding(new Insets(0, 22, 26, 34));
        info.setAlignment(Pos.BOTTOM_LEFT);
        StackPane.setAlignment(info, Pos.BOTTOM_LEFT);
        s.getChildren().addAll(pic, fade, info);
        return s;
    }

    private void setStatus(String text, String dotClass) {
        stageStatus.setText(text);
        statusDot.getStyleClass().removeAll("dot-busy", "dot-ok", "dot-error", "dot-accent");
        statusDot.getStyleClass().add(dotClass);
    }

    // ------------------------------------------------------------ messages

    private void loadThread(Threads.ChatThread t) {
        endTyping();                // its timeline would keep scrolling the newly loaded chat
        thread = t;
        if (t.messages.isEmpty()) { threads.add(t, Threads.CHARACTER, pack.greeting()); threads.save(); }
        heading.setText(t.title);
        sub.setText("✎  " + (t.scene.isBlank() ? pack.defaultScene() : t.scene));
        messages.getChildren().clear();
        for (Map<String, Object> m : t.messages) messages.getChildren().add(card(m));
        starters.getChildren().clear();
        if (t.messages.size() == 1) {
            if (!starters.getStyleClass().contains("starters")) starters.getStyleClass().add("starters");
            for (String s : pack.starters()) {
                Button b = new Button(s);
                b.getStyleClass().add("starter");
                b.setOnAction(e -> { if (!busy && ready) send(s); });
                starters.getChildren().add(b);
            }
            messages.getChildren().add(starters);
        }
        refreshDrawer();
        scrollDown();
    }

    private Node card(Map<String, Object> m) {
        boolean me = Threads.USER.equals(m.get("role"));
        String text = App.str(m, "text");
        if (me) return myCard(text);
        VBox v = herCard(App.str(m, "action"), text, Threads.shortTime(m.get("time")), App.strings(m, "fixed"), false);
        return v;
    }

    private Node myCard(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("my-text");
        l.setWrapText(true);
        l.setMaxWidth(560);
        VBox box = new VBox(l);
        box.getStyleClass().add("my-card");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(box);
        row.setAlignment(Pos.CENTER_RIGHT);
        return row;
    }

    private VBox herCard(String action, String text, String time, List<String> fixed, boolean pending) {
        Label who = new Label(pack.name().toUpperCase());
        who.getStyleClass().add("her-name");
        Label t = new Label(time);
        t.getStyleClass().add("msg-time");
        HBox head = new HBox(8, who, t);
        head.setAlignment(Pos.BASELINE_LEFT);
        if (!fixed.isEmpty()) {
            Label fx = new Label("✓ adjusted");
            fx.getStyleClass().add("fixed-badge");
            fx.setTooltip(new Tooltip("The first try broke a rule (" + String.join(", ", fixed) + "), so it was written again."));
            head.getChildren().add(fx);
        }
        VBox card = new VBox(6, head);
        card.getStyleClass().add("her-card");
        card.setMaxWidth(640);
        if (pending) {
            card.getChildren().add(Ui.typingDots());
        } else {
            if (!action.isBlank()) {
                Label a = new Label(action);
                a.getStyleClass().add("action");
                a.setWrapText(true);
                a.setMaxWidth(600);
                card.getChildren().add(a);
            }
            Label x = new Label(text);
            x.getStyleClass().add("her-text");
            x.setWrapText(true);
            x.setMaxWidth(600);
            card.getChildren().add(x);
        }
        card.setMaxWidth(Region.USE_PREF_SIZE);
        VBox holder = new VBox(card);
        holder.setAlignment(Pos.CENTER_LEFT);
        return holder;
    }

    private void scrollDown() { Platform.runLater(() -> { scroll.layout(); scroll.setVvalue(1.0); }); }

    // ------------------------------------------------------------ sending

    private void onSend() {
        if (busy) { if (handle != null) handle.cancel(); return; }
        String line = input.getText() == null ? "" : input.getText().strip();
        if (line.isEmpty() || !ready) return;
        input.clear();
        send(line);
    }

    private void send(String line) {
        if (busy || !ready) return;
        busy = true;
        messages.getChildren().remove(starters);
        messages.getChildren().add(myCard(line));
        pendingCard = herCard("", "", Threads.now().substring(11), List.of(), true);
        messages.getChildren().add(pendingCard);
        scrollDown();
        input.setDisable(true);
        setSendIcon(true);
        setStatus("Thinking…", "dot-busy");
        Ollama.Handle h = new Ollama.Handle();
        handle = h;
        Threads.ChatThread t = thread;
        Thread worker = new Thread(() -> {
            try {
                TurnPipeline.Result r = app.pipeline().run(pack, threads, t, line, null, h);
                Platform.runLater(() -> finish(t, r, h.isCancelled()));
                if (!h.isCancelled()) {
                    if (app.pipeline().updateNotesIfDue(pack, t)) threads.save();
                }
            } catch (Exception ex) {
                Platform.runLater(() -> fail(ex));
            }
        }, "turn");
        worker.setDaemon(true);
        worker.start();
    }

    private void finish(Threads.ChatThread t, TurnPipeline.Result r, boolean cancelled) {
        if (disposed) return;       // chat closed meanwhile: dispose() saved already; saving this old copy now could overwrite a newer chat view's file
        messages.getChildren().remove(pendingCard);
        if (t != thread) { busy = false; input.setDisable(false); setSendIcon(false); threads.save(); refreshDrawer(); return; }
        busy = false;
        input.setDisable(false);
        setSendIcon(false);
        if (cancelled) {
            setStatus("Stopped", "dot-ok");
            return;
        }
        if (r.crisis()) {
            Label note = new Label(App.str(app.knowledge().crisisNote, "text"));
            note.getStyleClass().addAll("crisis-card");
            note.setWrapText(true);
            note.setMaxWidth(640);
            messages.getChildren().add(note);
        }
        VBox reply = herCard(r.action(), r.reply(), Threads.now().substring(11), r.fixed(), false);
        messages.getChildren().add(reply);
        if (app.settings().liveTyping) typeIn(reply);
        threads.save();
        heading.setText(t.title);
        refreshDrawer();
        updateBadge();
        setStatus(String.format("Here · %.0f s", r.seconds()), "dot-ok");
        scrollDown();
        if (sheet == null) input.requestFocus();
    }

    private void fail(Exception ex) {
        if (disposed) return;
        messages.getChildren().remove(pendingCard);
        busy = false;
        input.setDisable(false);
        setSendIcon(false);
        Log.error("A reply failed", ex);
        Label err = new Label("Something went wrong: " + ex.getMessage());
        err.getStyleClass().addAll("her-text", "text-error");
        err.setWrapText(true);
        err.setMaxWidth(600);
        VBox box = new VBox(err);
        box.getStyleClass().addAll("her-card", "card-error");
        box.setMaxWidth(Region.USE_PREF_SIZE);
        messages.getChildren().add(box);
        setStatus("Error", "dot-error");
        scrollDown();
    }

    // ------------------------------------------------------------ export / delete

    private void export() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export this chat");
        fc.setInitialFileName(pack.name().replaceAll("[^A-Za-z0-9]+", "_") + "_chat.txt");
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try {
            Files.writeString(f.toPath(), Threads.transcript(pack, app.persona().displayName(entry.file()), thread), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            Alert a = new Alert(Alert.AlertType.ERROR, "Could not save: " + ex.getMessage());
            a.initOwner(getScene().getWindow());
            a.showAndWait();
        }
    }

    private void renameThread() {
        if (busy) return;
        javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(thread.title);
        d.setTitle("Rename chat");
        d.setHeaderText(null);
        d.setContentText("New name:");
        d.getDialogPane().getStyleClass().add("tqq-dialog");
        d.getDialogPane().getStylesheets().add(Main.css());
        d.initOwner(getScene().getWindow());     // centred on (and modal to) the app window, not the primary screen
        java.util.Optional<String> r = d.showAndWait();
        if (r.isPresent() && !r.get().isBlank()) {
            String v = r.get().strip();
            thread.title = v.length() > 60 ? v.substring(0, 60) : v;
            threads.save();
            heading.setText(thread.title);
            refreshDrawer();
        }
    }

    private void deleteThread() {
        if (busy) return;
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, "Delete \"" + thread.title + "\"? This can't be undone.", ButtonType.OK, ButtonType.CANCEL);
        a.getDialogPane().getStyleClass().add("tqq-dialog");
        a.getDialogPane().getStylesheets().add(Main.css());
        a.setHeaderText(null);
        a.initOwner(getScene().getWindow());
        if (a.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        threads.delete(thread.id);
        threads.save();
        loadThread(threads.active());
    }
}
