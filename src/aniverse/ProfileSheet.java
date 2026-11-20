package aniverse;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * "Who are you to this character?" - a sheet shown inside the chat (no pop-up window).
 * Every character keeps their own profile. "Are you under 18?" is asked once (the first profile ever saved).
 */
final class ProfileSheet extends StackPane {
    private static final String[] MET = {
        "Childhood friends", "We met at school", "Travelling together", "You saved me once", "Rivals who respect each other",
        "We work together", "Just met today", "Neighbours",
    };

    private final Button skip;

    ProfileSheet(Main app, Knowledge.Entry e, Knowledge.Pack p, boolean meeting, Runnable done) {
        Persona persona = app.persona();
        boolean askAge = persona.under18 == null;
        getStyleClass().add("hist-overlay");

        VBox sheet = new VBox();
        sheet.getStyleClass().add("profile-sheet");
        sheet.setStyle(Ui.accentVars(e.accent()));
        sheet.setMaxWidth(880);
        sheet.setMaxHeight(Region.USE_PREF_SIZE);

        // ---- left: the character
        StackPane pic = new StackPane(Ui.picture(e, 0.1, 110, 520));
        Region shade = new Region();
        shade.getStyleClass().add("panel-shade");
        Label num = new Label(e.num());
        num.getStyleClass().add("card-num");
        Region bar = new Region();
        bar.getStyleClass().add("panel-bar");
        Label cname = new Label(e.name());
        cname.getStyleClass().add("card-name");
        cname.setWrapText(true);
        Label cser = new Label(e.seriesTitle());
        cser.getStyleClass().add("card-series");
        VBox cinfo = new VBox(3, num, bar, cname, cser);
        cinfo.setAlignment(Pos.BOTTOM_LEFT);
        cinfo.setPadding(new Insets(0, 14, 14, 14));
        StackPane.setAlignment(cinfo, Pos.BOTTOM_LEFT);
        StackPane left = new StackPane(pic, shade, cinfo);
        left.setMinSize(210, 0);            // may get shorter in a small window (1100 x 640), so the sheet still fits
        left.setPrefSize(210, 560);
        left.setMaxSize(210, 560);
        Rectangle clip = new Rectangle(210, 560);
        clip.heightProperty().bind(left.heightProperty());
        clip.setArcWidth(30);
        clip.setArcHeight(30);
        left.setClip(clip);

        // ---- right: you
        Persona.Profile cur = persona.forCharacter(e.file());
        Label title = new Label(meeting ? "Who are you to " + e.name().split(" ")[0] + "?" : "How " + e.name().split(" ")[0] + " sees you");
        title.getStyleClass().add("hist-title");
        Label sub = new Label("Only for " + e.name().split(" ")[0] + ". Every character keeps their own profile, so you can be someone different with each. It stays on this computer.");
        sub.getStyleClass().add("setup-sub");
        sub.setWrapText(true);
        sub.setMinHeight(Region.USE_PREF_SIZE);

        TextField name = new TextField(cur.name());
        name.setPromptText("Your name or nickname");
        name.getStyleClass().add("setup-field");
        HBox.setHgrow(name, Priority.ALWAYS);
        Label initial = new Label();
        initial.getStyleClass().add("avatar-kanji");
        initial.setStyle("-fx-font-size: 22px;");
        StackPane you = new StackPane(initial);
        you.getStyleClass().add("avatar-fallback");
        you.setMinSize(48, 48);
        you.setMaxSize(48, 48);
        Runnable paint = () -> {
            String t = name.getText() == null ? "" : name.getText().strip();
            initial.setText(t.isEmpty() ? "?" : t.substring(0, 1).toUpperCase());
        };
        name.textProperty().addListener((o, a, b) -> paint.run());
        paint.run();
        HBox who = new HBox(12, you, name);
        who.setAlignment(Pos.CENTER_LEFT);

        TextArea bio = new TextArea(cur.bio());
        bio.setPromptText("A line about you, e.g. a student who loves cooking and bad puns");
        bio.setPrefRowCount(2);
        bio.setWrapText(true);
        bio.getStyleClass().add("setup-area");
        TextArea how = new TextArea(cur.howMet());
        how.setPromptText("How you met " + e.name().split(" ")[0] + " - pick an idea or write your own");
        how.setPrefRowCount(2);
        how.setWrapText(true);
        how.getStyleClass().add("setup-area");
        FlowPane ideas = new FlowPane(6, 6);
        for (String s : MET) {
            Button b = new Button(s);
            b.getStyleClass().add("starter");
            b.setOnAction(ev -> how.setText(s + "."));
            ideas.getChildren().add(b);
        }

        VBox right = new VBox(8, title, sub, label("YOU"), who, label("ABOUT YOU"), bio, label("HOW YOU MET"), ideas, how);

        ToggleGroup g = new ToggleGroup();
        ToggleButton under = seg("I'm under 18", g);
        ToggleButton adult = seg("I'm 18 or older", g);
        if (persona.under18 != null) (persona.under18 ? under : adult).setSelected(true);
        if (askAge) {
            Label note = new Label("Asked once. It decides how characters talk to you: friendly with everyone, and sweet romance only between "
                    + "teens and under-18s, or adults and 18+. Never explicit.");
            note.getStyleClass().add("setup-note");
            note.setWrapText(true);
            note.setMinHeight(Region.USE_PREF_SIZE);
            right.getChildren().addAll(label("ARE YOU UNDER 18?"), new HBox(8, under, adult), note);
        }

        Button save = Ui.button(null, "Save", "panel-talk");
        save.setDisable(askAge);
        g.selectedToggleProperty().addListener((o, a, b) -> save.setDisable(askAge && b == null));
        Button skip = Ui.button(null, meeting ? "Skip for now" : "Cancel", "mini");
        HBox actions = new HBox(10, save, skip);
        actions.setAlignment(Pos.CENTER_LEFT);
        if (askAge) skip.setVisible(false);       // the age answer can't be skipped
        skip.managedProperty().bind(skip.visibleProperty());
        VBox.setMargin(actions, new Insets(8, 0, 0, 0));
        right.getChildren().add(actions);
        HBox.setHgrow(right, Priority.ALWAYS);
        right.setPrefWidth(580);
        right.setMinWidth(0);

        save.setOnAction(ev -> {
            if (askAge) persona.under18 = under.isSelected();
            persona.setOverride(e.file(), new Persona.Profile(name.getText(), bio.getText(), how.getText()));
            persona.save();
            done.run();
        });
        skip.setOnAction(ev -> {
            if (meeting && persona.override(e.file()) == null) {       // don't ask again for this character
                persona.setOverride(e.file(), new Persona.Profile(cur.name(), cur.bio(), cur.howMet()));
                persona.save();
            }
            done.run();
        });
        setOnMouseClicked(ev -> { if (ev.getTarget() == this && !askAge) skip.fire(); });
        this.skip = skip;

        HBox body = new HBox(24, left, right);
        body.setAlignment(Pos.TOP_LEFT);
        // scrolls when the window is too low for the whole sheet (1100 x 640: Save was cut off)
        javafx.scene.control.ScrollPane sp = new javafx.scene.control.ScrollPane(body);
        sp.setFitToWidth(true);
        sp.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("clear-scroll");
        sheet.getChildren().add(sp);
        getChildren().add(sheet);
        StackPane.setAlignment(sheet, Pos.CENTER);
        setStyle("-fx-padding: 14px 24px;");     // the shared overlay padding (46 px) leaves too little room at 640 px high
        sheet.setStyle(sheet.getStyle() + "-fx-padding: 20px 28px;");
        Ui.rise(sheet, 20, 280, 0);
    }

    /** Esc = Skip / Cancel; not while the age question still needs an answer. */
    boolean escape() {
        if (!skip.isVisible()) return false;
        skip.fire();
        return true;
    }

    private static ToggleButton seg(String text, ToggleGroup g) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(g);
        b.getStyleClass().add("seg");
        return b;
    }

    private static Node label(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("setup-label");
        return l;
    }
}
