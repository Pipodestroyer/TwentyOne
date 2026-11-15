package UI;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class PixelUI {
    public static final String FALLBACK_FONT = "Monospaced";

    private static Path fontsRoot;
    private static final Map<String, Font> fontCache = new HashMap<>();
    private static Image checkedBoxImg;
    private static Image uncheckedBoxImg;

    private static synchronized Path getFontsRoot() {
        if (fontsRoot != null) {
            return fontsRoot;
        }
        Path[] candidates = {
                Path.of("fonts"),
                Path.of("twentyoneproject", "fonts"),
                Path.of("..", "fonts")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                fontsRoot = candidate.toAbsolutePath().normalize();
                return fontsRoot;
            }
        }
        fontsRoot = Path.of("fonts").toAbsolutePath();
        return fontsRoot;
    }

    private static Font loadPixelFont(boolean bold, double size) {
        String key = (bold ? "B_" : "R_") + size;
        return fontCache.computeIfAbsent(key, _ -> {
            Path root = getFontsRoot();
            Path primary = bold
                    ? root.resolve("static").resolve("PixelifySans-Bold.ttf")
                    : root.resolve("static").resolve("PixelifySans-Regular.ttf");
            Path secondary = root.resolve("PixelifySans-VariableFont_wght.ttf");

            Font loaded = null;
            if (Files.exists(primary)) {
                loaded = Font.loadFont(primary.toUri().toString(), size);
            }
            if (loaded == null && Files.exists(secondary)) {
                loaded = Font.loadFont(secondary.toUri().toString(), size);
            }
            if (loaded != null) {
                return loaded;
            }
            return Font.font(FALLBACK_FONT, bold ? FontWeight.BOLD : FontWeight.NORMAL, size);
        });
    }

    public static Font font(double size) {
        return loadPixelFont(true, size);
    }

    public static Font fontRegular(double size) {
        return loadPixelFont(false, size);
    }

    public static Label label(String text, double size, boolean bold) {
        Label lbl = new Label(text);
        lbl.setFont(bold ? font(size) : fontRegular(size));
        lbl.setStyle("-fx-text-fill: #101010;");
        return lbl;
    }

    public static Button pixelButton(String text, double fontSize, double minWidth, double minHeight) {
        Button btn = new Button(text);
        btn.setFont(font(fontSize));
        btn.setMinWidth(minWidth);
        btn.setMinHeight(minHeight);
        btn.setCursor(Cursor.HAND);

        String normalStyle = "-fx-background-color: #FFFFFF; -fx-text-fill: #101010; "
                + "-fx-border-color: #101010; -fx-border-width: 2; -fx-background-radius: 0; -fx-border-radius: 0; "
                + "-fx-padding: 6 14 6 14;";
        String hoverStyle = "-fx-background-color: #101010; -fx-text-fill: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 2; -fx-background-radius: 0; -fx-border-radius: 0; "
                + "-fx-padding: 6 14 6 14;";

        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(_ -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(_ -> btn.setStyle(normalStyle));
        return btn;
    }

    public static Button invertedButton(String text, double fontSize, double minWidth, double minHeight) {
        Button btn = new Button(text);
        btn.setFont(font(fontSize));
        btn.setMinWidth(minWidth);
        btn.setMinHeight(minHeight);
        btn.setCursor(Cursor.HAND);

        String normalStyle = "-fx-background-color: #101010; -fx-text-fill: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 2; -fx-background-radius: 0; -fx-border-radius: 0; "
                + "-fx-padding: 6 14 6 14;";
        String hoverStyle = "-fx-background-color: #353535; -fx-text-fill: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 2; -fx-background-radius: 0; -fx-border-radius: 0; "
                + "-fx-padding: 6 14 6 14;";

        btn.setStyle(normalStyle);
        btn.setOnMouseEntered(_ -> btn.setStyle(hoverStyle));
        btn.setOnMouseExited(_ -> btn.setStyle(normalStyle));
        return btn;
    }

    public static TextField pixelField(String initial, double width) {
        TextField tf = new TextField(initial);
        tf.setFont(fontRegular(14));
        tf.setPrefWidth(width);
        tf.setPadding(new Insets(6, 10, 6, 10));
        tf.setStyle("-fx-background-color: #FFFFFF; -fx-text-fill: #101010; "
                + "-fx-border-color: #101010; -fx-border-width: 2; -fx-background-radius: 0; -fx-border-radius: 0;");
        return tf;
    }

    private static Image getCheckBoxImage(boolean selected) {
        if (selected && checkedBoxImg != null) return checkedBoxImg;
        if (!selected && uncheckedBoxImg != null) return uncheckedBoxImg;

        int size = 11;
        WritableImage base = new WritableImage(size, size);
        PixelWriter pw = base.getPixelWriter();
        Color black = Color.rgb(16, 16, 16);
        Color white = Color.WHITE;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean border = (x == 0 || y == 0 || x == size - 1 || y == size - 1);
                pw.setColor(x, y, border ? black : white);
            }
        }

        if (selected) {
            int[][] checkPixels = {
                    {2, 5}, {2, 6},
                    {3, 6}, {3, 7},
                    {4, 7}, {4, 8},
                    {5, 6}, {5, 7},
                    {6, 5}, {6, 6},
                    {7, 4}, {7, 5},
                    {8, 3}, {8, 4}
            };
            for (int[] pt : checkPixels) {
                pw.setColor(pt[0], pt[1], black);
            }
            checkedBoxImg = AssetManager.scaleNearestNeighbor(base, 2);
            return checkedBoxImg;
        } else {
            uncheckedBoxImg = AssetManager.scaleNearestNeighbor(base, 2);
            return uncheckedBoxImg;
        }
    }

    public static class PixelCheckBox extends HBox {
        private boolean selected;
        private final ImageView boxView;
        private final Label textLabel;
        private Runnable onChanged;

        public PixelCheckBox(String text, boolean initialSelected) {
            this.selected = initialSelected;
            setAlignment(Pos.CENTER_LEFT);
            setSpacing(10);
            setPadding(new Insets(2, 4, 2, 0));
            setCursor(Cursor.HAND);

            boxView = new ImageView(getCheckBoxImage(initialSelected));
            boxView.setSmooth(false);
            boxView.setPreserveRatio(true);

            textLabel = label(text, 13.5, false);

            getChildren().addAll(boxView, textLabel);

            setOnMouseClicked(_ -> {
                if (isDisabled()) return;
                setSelected(!this.selected);
                if (onChanged != null) {
                    onChanged.run();
                }
            });
            setOnMouseEntered(_ -> {
                if (!isDisabled()) setOpacity(0.75);
            });
            setOnMouseExited(_ -> {
                if (!isDisabled()) setOpacity(1.0);
            });
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
            boxView.setImage(getCheckBoxImage(selected));
        }

        public void setOnChanged(Runnable onChanged) {
            this.onChanged = onChanged;
        }
    }
}
