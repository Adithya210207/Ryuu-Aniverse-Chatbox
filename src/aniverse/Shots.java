package aniverse;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Developer tool (-Daniverse.shots=<folder>): saves PNG screenshots of the main screens so layout bugs can be seen. */
final class Shots {
    private Shots() {}

    static void run(Main app, Stage stage, Scene scene, Path dir, Knowledge.Entry chatEntry) {
        List<Runnable> steps = new ArrayList<>();
        int[] n = {0};
        steps.add(() -> shot(scene, dir, ++n[0] + "_home"));
        steps.add(() -> { Node s = scene.getRoot().lookup(".clear-scroll"); if (s instanceof ScrollPane sp) sp.setVvalue(1.0); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_gallery"));
        steps.add(() -> {
            Node c = scene.getRoot().lookup(".gallery-card");
            if (c != null) Node.class.cast(c).fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, false, null));
        });
        steps.add(() -> shot(scene, dir, ++n[0] + "_profile"));
        steps.add(() -> { if (chatEntry != null) app.openChat(chatEntry, false, null); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_chat"));
        steps.add(() -> { stage.setWidth(1200); stage.setHeight(760); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_chat_small"));
        steps.add(() -> { app.showHome(); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_home_small"));
        steps.add(() -> { stage.setWidth(1100); stage.setHeight(640); });      // the smallest window allowed
        steps.add(() -> shot(scene, dir, ++n[0] + "_home_min"));
        steps.add(() -> { Node s = scene.getRoot().lookup(".clear-scroll"); if (s instanceof ScrollPane sp) sp.setVvalue(0.25); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_gallery_min"));
        steps.add(() -> { app.openChat(chatEntry, false, null); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_chat_min"));
        steps.add(() -> { app.persona().under18 = null; Node b = scene.getRoot().lookup(".context-chip"); if (b instanceof javafx.scene.control.Button bt) bt.fire(); });
        steps.add(() -> shot(scene, dir, ++n[0] + "_profile_sheet"));
        steps.add(() -> { System.out.println("SHOTS DONE"); Platform.exit(); System.exit(0); });
        next(steps, 0);
    }

    /** Live run (-Daniverse.live=<folder>): real models, one real message, screenshots while loading / thinking / answered. */
    static void live(Main app, Scene scene, Path dir, Knowledge.Entry e, String line) {
        app.openChat(e, true, null);
        javafx.scene.control.TextField input = (javafx.scene.control.TextField) scene.getRoot().lookup(".composer-input");
        int[] state = {0};
        int[] ticks = {0};
        javafx.animation.Timeline tl = new javafx.animation.Timeline(new javafx.animation.KeyFrame(Duration.seconds(1), ev -> {
            ticks[0]++;
            try {
                if (ticks[0] == 3) shot(scene, dir, "L1_loading");
                if (state[0] == 0 && !input.isDisabled()) {
                    shot(scene, dir, "L2_ready");
                    input.setText(line);
                    input.fireEvent(new javafx.event.ActionEvent());
                    state[0] = 1; ticks[0] = 0;
                } else if (state[0] == 1 && ticks[0] == 3) {
                    shot(scene, dir, "L3_thinking");
                    state[0] = 2;
                } else if (state[0] == 2 && !input.isDisabled()) {
                    shot(scene, dir, "L4_answered");
                    state[0] = 3; ticks[0] = 0;
                } else if (state[0] == 3 && ticks[0] == 2) {
                    System.out.println("LIVE DONE");
                    app.shutdownForTest();
                }
                if (ticks[0] > 240) { shot(scene, dir, "L9_timeout"); System.out.println("LIVE TIMEOUT"); app.shutdownForTest(); }
            } catch (Throwable ex) { ex.printStackTrace(); }
        }));
        tl.setCycleCount(javafx.animation.Animation.INDEFINITE);
        tl.play();
    }

    // ---------------------------------------------------------------- dialogs walk-through (-Daniverse.dialogs=1)

    private static int pass, fail;

    private static void check(String what, boolean ok) {
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS  " : "FAIL  ") + what);
    }

    private static Node find(Scene scene, String css) { return scene.getRoot().lookup(css); }

    private static javafx.scene.control.Button button(Scene scene, String text) {
        for (Node n : scene.getRoot().lookupAll(".button"))
            if (n instanceof javafx.scene.control.Button b && b.getText() != null && b.getText().startsWith(text)) return b;
        return null;
    }

    /** Waits for a modal dialog to show, runs `drive` on its DialogPane, in the nested event loop of showAndWait. */
    private static void drive(java.util.function.Consumer<javafx.scene.control.DialogPane> drive) {
        Platform.runLater(() -> {
            PauseTransition t = new PauseTransition(Duration.millis(700));
            t.setOnFinished(e -> {
                for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
                    if (w.isShowing() && w.getScene() != null && w.getScene().getRoot() instanceof javafx.scene.control.DialogPane dp) {
                        drive.accept(dp);
                        return;
                    }
                }
                System.out.println("FAIL  dialog did not appear");
                fail++;
            });
            t.play();
        });
    }

    static void dialogs(Main app, Scene scene, Knowledge.Entry e) {
        List<Runnable> steps = new ArrayList<>();
        Persona persona = app.persona();
        steps.add(() -> {                                   // first meeting: the profile sheet appears inside the chat
            persona.under18 = null;
            persona.setOverride(e.file(), null);
            app.openChat(e, false, null);
        });
        steps.add(() -> {
            check("profile sheet shows the first time you open a character", find(scene, ".profile-sheet") != null);
            javafx.scene.control.TextField tf = (javafx.scene.control.TextField) find(scene, ".setup-field");
            tf.setText("Aya");
            javafx.scene.control.TextArea how = (javafx.scene.control.TextArea) scene.getRoot().lookupAll(".setup-area").toArray(new Node[0])[1];
            how.setText("We met at school.");
            javafx.scene.control.Button save = button(scene, "Save");
            check("Save is disabled until the age is chosen", save != null && save.isDisable());
            List<Node> seg = new ArrayList<>(scene.getRoot().lookupAll(".seg"));
            ((javafx.scene.control.ToggleButton) seg.get(1)).setSelected(true);
            check("Save is enabled after choosing the age", !save.isDisable());
            save.fire();
        });
        steps.add(() -> {
            Persona.Profile pr = persona.forCharacter(e.file());
            check("sheet closes after Save", find(scene, ".profile-sheet") == null);
            check("profile saved for this character (name Aya, met at school)", "Aya".equals(pr.name()) && pr.howMet().contains("school"));
            check("age saved (18 or older)", Boolean.FALSE.equals(persona.under18));
        });
        steps.add(() -> {                                   // scene picker
            drive(dp -> {
                javafx.scene.control.TextArea ta = (javafx.scene.control.TextArea) dp.lookup(".setup-area");
                ta.setText("A rooftop at sunset.");
                ((javafx.scene.control.Button) dp.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
            });
            Node link = find(scene, ".scene-link");
            link.fireEvent(new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0, MouseButton.PRIMARY, 1,
                    false, false, false, false, true, false, false, true, false, false, null));
        });
        steps.add(() -> {
            javafx.scene.control.Label sub = (javafx.scene.control.Label) find(scene, ".scene-link");
            check("scene changed to the new text", sub.getText().contains("A rooftop at sunset."));
        });
        steps.add(() -> {                                   // rename
            drive(dp -> {
                javafx.scene.control.TextField tf = (javafx.scene.control.TextField) dp.lookup(".text-field");
                tf.setText("Rooftop talk");
                ((javafx.scene.control.Button) dp.lookupButton(javafx.scene.control.ButtonType.OK)).fire();
            });
            button(scene, "Rename").fire();
        });
        steps.add(() -> {
            javafx.scene.control.Label h = (javafx.scene.control.Label) find(scene, ".thread-heading");
            check("chat renamed (title bar)", "Rooftop talk".equals(h.getText()));
            boolean inList = false;
            for (Node n : scene.getRoot().lookupAll(".thread-title")) if (n instanceof javafx.scene.control.Label l && "Rooftop talk".equals(l.getText())) inList = true;
            check("chat renamed (chat list)", inList);
        });
        steps.add(() -> {                                   // typing effect toggle
            javafx.scene.control.Button t = button(scene, "Typing effect");
            String before = t.getText();
            t.fire();
            check("typing effect toggles (" + before + " -> " + t.getText() + ")", !before.equals(t.getText()));
            t.fire();
        });
        steps.add(() -> {                                   // reopen profile from the chat, cancel
            button(scene, "Your profile").fire();
        });
        steps.add(() -> {
            check("'Your profile' opens the sheet with the saved name", find(scene, ".profile-sheet") != null
                    && "Aya".equals(((javafx.scene.control.TextField) find(scene, ".setup-field")).getText()));
            check("age question is not asked again", scene.getRoot().lookupAll(".seg").isEmpty());
            shot(scene, Path.of(System.getProperty("aniverse.dialogs")), "D_profile");
            button(scene, "Cancel").fire();
        });
        steps.add(() -> {
            check("sheet closes on Cancel", find(scene, ".profile-sheet") == null);
            System.out.println("DIALOGS DONE: " + pass + " passed, " + fail + " failed");
            Platform.exit();
            System.exit(fail == 0 ? 0 : 1);
        });
        next(steps, 0);
    }

    private static void next(List<Runnable> steps, int i) {
        if (i >= steps.size()) return;
        PauseTransition t = new PauseTransition(Duration.millis(1400));
        t.setOnFinished(e -> {
            // run outside the animation pulse: showAndWait (the dialogs) is not allowed inside it
            Platform.runLater(() -> {
                try { steps.get(i).run(); } catch (Throwable ex) { ex.printStackTrace(); }
                next(steps, i + 1);
            });
        });
        t.play();
    }

    private static void shot(Scene scene, Path dir, String name) {
        try {
            Files.createDirectories(dir);
            WritableImage img = scene.snapshot(null);
            int w = (int) img.getWidth(), h = (int) img.getHeight();
            java.awt.image.BufferedImage bi = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            javafx.scene.image.PixelReader pr = img.getPixelReader();
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) bi.setRGB(x, y, pr.getArgb(x, y));
            javax.imageio.ImageIO.write(bi, "png", dir.resolve(name + ".png").toFile());
        } catch (Throwable ex) { ex.printStackTrace(); }
    }
}
