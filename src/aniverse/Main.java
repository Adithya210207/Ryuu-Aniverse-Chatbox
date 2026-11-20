package aniverse;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** App shell: loads everything, swaps between Home and a character's chat, starts/stops the private Ollama. */
public final class Main extends Application {
    private static final boolean SMOKE = Boolean.getBoolean("aniverse.smoke");
    private static final boolean TEST = SMOKE || System.getProperty("aniverse.shots") != null || System.getProperty("aniverse.live") != null;

    private Knowledge knowledge;
    private Settings settings;
    private Persona persona;
    private Favorites favorites;
    private TurnPipeline pipeline;
    private CompletableFuture<Boolean> server;
    private CompletableFuture<String> models;
    private final StackPane content = new StackPane();
    private ChatView chat;
    private Stage stage;

    public static void main(String[] args) { launch(args); }

    public Knowledge knowledge() { return knowledge; }
    public Settings settings() { return settings; }
    public Persona persona() { return persona; }
    public Favorites favorites() { return favorites; }
    public TurnPipeline pipeline() { return pipeline; }
    public CompletableFuture<Boolean> server() { return server; }
    public Stage stage() { return stage; }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        Thread.setDefaultUncaughtExceptionHandler((th, ex) -> Log.error("Uncaught error in thread " + th.getName(), ex));
        knowledge = new Knowledge();
        settings = Settings.load();
        persona = Persona.load();
        favorites = Favorites.load();
        pipeline = new TurnPipeline(knowledge, settings, persona);
        // Ollama (the private server) starts now; the models are only loaded when a chat opens (saves heat/memory)
        server = (SMOKE || System.getProperty("aniverse.shots") != null || System.getProperty("aniverse.dialogs") != null) ? CompletableFuture.completedFuture(true) : CompletableFuture.supplyAsync(Ollama::ensureRunning);

        content.getStyleClass().add("app-root");
        Scene scene = new Scene(content);
        scene.setFill(javafx.scene.paint.Color.web("#0b0b10"));
        scene.getStylesheets().add(css());
        // Esc closes the open profile sheet (Home or chat), like TQQ's overlays
        scene.addEventHandler(javafx.scene.input.KeyEvent.KEY_PRESSED, ev -> {
            if (ev.getCode() != javafx.scene.input.KeyCode.ESCAPE || content.getChildren().isEmpty()) return;
            javafx.scene.Node top = content.getChildren().get(0);
            if ((top instanceof HomeView h && h.escape()) || (top instanceof ChatView c && c.escape())) ev.consume();
        });
        stage.setScene(scene);
        Path icon = App.assets().resolve("app.png");
        if (Files.isRegularFile(icon)) stage.getIcons().add(new Image(icon.toUri().toString()));
        stage.setTitle(App.NAME + " " + App.VERSION.split(" ")[0] + " — " + App.TAGLINE);
        // fit the window (title bar and its minimise / maximise / close buttons included) inside the screen's usable area
        javafx.geometry.Rectangle2D vb = javafx.stage.Screen.getPrimary().getVisualBounds();
        double w = Math.min(1440, vb.getWidth()), h = Math.min(900, vb.getHeight());
        stage.setMinWidth(Math.min(1100, vb.getWidth()));
        stage.setMinHeight(Math.min(640, vb.getHeight()));
        stage.setWidth(w);
        stage.setHeight(h);
        stage.setX(vb.getMinX() + (vb.getWidth() - w) / 2);
        stage.setY(vb.getMinY() + (vb.getHeight() - h) / 2);
        stage.setOnCloseRequest(e -> shutdown());
        showHome();
        stage.show();
        if (SMOKE) { runSmoke(); return; }
        if (System.getProperty("aniverse.dialogs") != null) {
            Knowledge.Entry first = knowledge.roster().stream().filter(x -> knowledge.hasPack(x.file())).findFirst().orElse(null);
            Shots.dialogs(this, scene, first);
            return;
        }
        String live = System.getProperty("aniverse.live");
        if (live != null) {
            Knowledge.Entry first = knowledge.roster().stream().filter(x -> knowledge.hasPack(x.file())).findFirst().orElse(null);
            Shots.live(this, scene, Path.of(live), first, System.getProperty("aniverse.line", "Hello! Who are you, and what are you doing here?"));
            return;
        }
        String shots = System.getProperty("aniverse.shots");
        if (shots != null) {
            Knowledge.Entry first = knowledge.roster().stream().filter(x -> knowledge.hasPack(x.file())).findFirst().orElse(null);
            Shots.run(this, stage, scene, Path.of(shots), first);
            return;
        }
    }

    // ------------------------------------------------------------ screens

    public void showHome() {
        closeChat();
        content.getChildren().setAll(new HomeView(this));
    }

    /** Opens a character's chat. newThread = start a fresh chat; starter = text put in the box. */
    public void openChat(Knowledge.Entry e, boolean newThread, String starter) {
        Knowledge.Pack p = knowledge.pack(e.file());
        if (p == null) return;
        if (chat != null && chat.entry() == e && !newThread && starter == null) return;
        closeChat();
        favorites.touch(e.file());
        chat = new ChatView(this, e, p, newThread, starter);
        content.getChildren().setAll(chat);
        Ui.rise(chat, 0, 380, 0);
    }

    private void closeChat() {
        if (chat != null) { chat.dispose(); chat = null; }
    }

    /** Loads brain + judge once (first chat). Completes with null = ready, or an error text. */
    /** Screenshot / dialog test modes: pretend the models are ready (no AI is started). */
    private String mockReady() {
        pipeline.judge.ready = true;
        return null;
    }

    /** After a failed model load: forget the failure and start Ollama / the load again on the next models() call. */
    public synchronized void resetModels() {
        models = null;
        server = CompletableFuture.supplyAsync(Ollama::ensureRunning);
    }

    public synchronized CompletableFuture<String> models() {
        if (models == null) {
            models = (SMOKE || System.getProperty("aniverse.shots") != null || System.getProperty("aniverse.dialogs") != null) ? CompletableFuture.completedFuture(SMOKE ? "smoke test: models not loaded" : mockReady()) : server.thenApplyAsync(ok -> {
                if (!ok) return "Ollama was not found or did not start. Install Ollama, then restart the app.";
                if (!Ollama.hasModel(Ollama.BRAIN)) return "The AI models are not installed yet. Run \"Setup models\" once (see README), then press Try again.";
                try {
                    Ollama.load(Ollama.BRAIN);
                } catch (Exception ex) {
                    return "Could not load the brain model: " + ex.getMessage();
                }
                pipeline.judge.ready = settings.judgeOn && Ollama.loadJudge();
                return null;
            });
        }
        return models;
    }

    void shutdownForTest() { shutdown(); }

    private void shutdown() {
        if (chat != null) chat.dispose();
        boolean loaded;
        synchronized (this) { loaded = models != null && !models.isCompletedExceptionally() && (!models.isDone() || models.getNow("x") == null); }   // null = loaded OK
        if (loaded && !Ollama.startedByUs()) {      // someone else's server: free our models (only if this app loaded them), leave it running
            Ollama.unload(Ollama.JUDGE);
            Ollama.unload(Ollama.BRAIN);
        }
        Ollama.stopIfStartedByUs();
        Platform.exit();
        System.exit(0);
    }

    public static String css() { return App.assets().resolve("style.css").toUri().toString(); }

    // ------------------------------------------------------------ smoke test (-Daniverse.smoke=true)

    private void runSmoke() {
        PauseTransition t = new PauseTransition(Duration.seconds(1.5));
        t.setOnFinished(ev -> {
            try {
                Knowledge.Entry first = knowledge.roster().stream().filter(x -> knowledge.hasPack(x.file())).findFirst().orElse(null);
                if (first != null) openChat(first, true, null);
                PauseTransition t2 = new PauseTransition(Duration.seconds(1.5));
                t2.setOnFinished(e2 -> { System.out.println("SMOKE OK " + (first == null ? "(no packs)" : first.file())); Platform.exit(); System.exit(0); });
                t2.play();
            } catch (Throwable ex) {
                ex.printStackTrace();
                System.out.println("SMOKE FAILED");
                System.exit(1);
            }
        });
        t.play();
    }
}
