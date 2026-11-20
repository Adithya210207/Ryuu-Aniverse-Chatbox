package aniverse;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.Optional;

/** "Where are you two?" - pick or write the scene for this chat. Returns the new scene text, "" for the default, or null if cancelled. */
public final class SceneDialog {
    private SceneDialog() {}

    private static final String[] IDEAS = {
        "A quiet cafe after closing time, rain on the windows.",
        "Walking home together at sunset.",
        "The school rooftop during lunch break.",
        "Lying on a hill under the stars at night.",
        "A festival street with lanterns and food stalls.",
        "An early-morning training ground, nobody else around.",
        "A train ride through the countryside.",
        "A cosy room on a snowy evening, tea on the table.",
    };

    public static String show(javafx.stage.Window owner, String current, String defaultScene) {
        Dialog<ButtonType> d = new Dialog<>();
        if (owner != null) d.initOwner(owner);      // centred on the app window
        d.setTitle("Scene");
        DialogPane pane = d.getDialogPane();
        pane.getStyleClass().add("tqq-dialog");
        pane.getStylesheets().add(Main.css());
        pane.setPrefWidth(520);

        Label head = new Label("Where are you two?");
        head.getStyleClass().add("opt-title");
        Label sub = new Label("The character will act as if they are really there. Pick an idea or write your own.");
        sub.getStyleClass().add("setup-sub");
        sub.setWrapText(true);

        TextArea text = new TextArea(current == null || current.isBlank() ? defaultScene : current);
        text.setWrapText(true);
        text.setPrefRowCount(3);
        text.getStyleClass().add("setup-area");

        FlowPane ideas = new FlowPane(8, 8);
        for (String s : IDEAS) {
            Button b = new Button(s);
            b.getStyleClass().add("starter");
            b.setOnAction(e -> text.setText(s));
            ideas.getChildren().add(b);
        }
        Button reset = new Button("Use their usual scene");
        reset.getStyleClass().add("mini");
        reset.setOnAction(e -> text.setText(defaultScene));

        VBox box = new VBox(10, head, sub, label("IDEAS"), ideas, label("YOUR SCENE"), text, reset);
        box.setPadding(new Insets(8, 8, 4, 8));
        pane.setContent(box);
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> res = d.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.OK) return null;
        String t = text.getText() == null ? "" : text.getText().strip();
        return t.equals(defaultScene.strip()) ? "" : t;
    }

    private static Label label(String t) {
        Label l = new Label(t);
        l.getStyleClass().add("setup-label");
        return l;
    }
}
