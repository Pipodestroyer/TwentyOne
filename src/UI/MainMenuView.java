package UI;

import javafx.animation.AnimationTimer;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MainMenuView extends StackPane {
    private static class CarouselSlot {
        final ImageView view;
        double progress; // 0.0 (bottom) -> 1.0 (top)

        CarouselSlot(ImageView view, double progress) {
            this.view = view;
            this.progress = progress;
        }
    }

    private final TwentyOneApp app;
    private final Random random = new Random();
    private final List<CarouselSlot> slots = new ArrayList<>();
    private List<Image> packCards;
    private AnimationTimer carouselTimer;
    private StackPane optionsOverlay;
    private StackPane tutorialOverlay;

    public MainMenuView(TwentyOneApp app) {
        this.app = app;
        setStyle("-fx-background-color: #FFFFFF;");

        HBox mainLayout = new HBox();
        mainLayout.setAlignment(Pos.CENTER_LEFT);
        mainLayout.setPadding(new Insets(0, 50, 0, 70));
        mainLayout.setSpacing(40);

        VBox leftColumn = new VBox(28);
        leftColumn.setAlignment(Pos.CENTER_LEFT);
        leftColumn.setPadding(new Insets(40, 0, 40, 0));
        leftColumn.setMinWidth(340);

        ImageView ace21View = new ImageView(AssetManager.getMenuAce21(4));
        ace21View.setSmooth(false);
        ace21View.setPreserveRatio(true);

        VBox menuButtons = new VBox(22);
        menuButtons.setAlignment(Pos.TOP_LEFT);
        menuButtons.setPadding(new Insets(18, 0, 0, 0));

        VBox playBtn = createLineMenuItem("JUGAR", 28, 290, 5, () -> {
            stopAnimation();
            app.showLobby();
        });
        VBox optionsBtn = createLineMenuItem("OPCIONES", 18, 195, 3, this::openOptions);
        VBox exitBtn = createLineMenuItem("SALIR", 18, 195, 3, () -> {
            stopAnimation();
            app.shutdownNetworking();
            Platform.exit();
        });

        menuButtons.getChildren().addAll(playBtn, optionsBtn, exitBtn);
        leftColumn.getChildren().addAll(ace21View, menuButtons);

        Pane carouselPane = new Pane();
        HBox.setHgrow(carouselPane, Priority.ALWAYS);
        carouselPane.setMinWidth(320);

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(carouselPane.widthProperty());
        clip.heightProperty().bind(carouselPane.heightProperty());
        carouselPane.setClip(clip);

        setupCarousel(carouselPane);

        mainLayout.getChildren().addAll(leftColumn, carouselPane);
        getChildren().add(mainLayout);

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                stopAnimation();
            }
        });
    }

    private VBox createLineMenuItem(String text, double fontSize, double lineWidth, double lineHeight, Runnable action) {
        VBox container = new VBox(6);
        container.setAlignment(Pos.CENTER_LEFT);
        container.setMinWidth(lineWidth);
        container.setMaxWidth(lineWidth + 24);
        container.setCursor(Cursor.HAND);

        Label lbl = PixelUI.label(text, fontSize, true);
        Rectangle blackLine = new Rectangle(0, lineHeight, Color.rgb(16, 16, 16));

        container.getChildren().addAll(lbl, blackLine);

        final Timeline[] activeTimeline = new Timeline[1];
        container.setOnMouseEntered(_ -> {
            if (activeTimeline[0] != null) {
                activeTimeline[0].stop();
            }
            activeTimeline[0] = new Timeline(
                    new KeyFrame(Duration.millis(180),
                            new KeyValue(blackLine.widthProperty(), lineWidth, Interpolator.EASE_OUT))
            );
            activeTimeline[0].play();
        });
        container.setOnMouseExited(_ -> {
            if (activeTimeline[0] != null) {
                activeTimeline[0].stop();
            }
            activeTimeline[0] = new Timeline(
                    new KeyFrame(Duration.millis(140),
                            new KeyValue(blackLine.widthProperty(), 0.0, Interpolator.EASE_IN))
            );
            activeTimeline[0].play();
        });
        container.setOnMouseClicked(_ -> action.run());
        return container;
    }

    private void setupCarousel(Pane carouselPane) {
        packCards = AssetManager.getAllPackCardsForMenu(3);
        int slotCount = 7;
        for (int i = 0; i < slotCount; i++) {
            ImageView iv = new ImageView(pickRandomCard());
            iv.setSmooth(false);
            iv.setPreserveRatio(true);
            carouselPane.getChildren().add(iv);
            slots.add(new CarouselSlot(iv, (double) i / slotCount));
        }

        carouselTimer = new AnimationTimer() {
            private long lastNs = 0;

            @Override
            public void handle(long now) {
                if (lastNs == 0) {
                    lastNs = now;
                    return;
                }
                double dt = (now - lastNs) / 1_000_000_000.0;
                lastNs = now;
                if (dt > 0.1) dt = 0.016;

                double w = carouselPane.getWidth();
                double h = carouselPane.getHeight();
                if (w <= 10 || h <= 10) return;

                double cardScale = Math.max(1.0, h / 680.0);
                double speed = 0.075; // Slow, smooth, ~13s full cycle
                for (CarouselSlot slot : slots) {
                    slot.progress += dt * speed;
                    if (slot.progress >= 1.0) {
                        slot.progress -= 1.0;
                        slot.view.setImage(pickRandomCard());
                    }

                    double t = slot.progress;
                    Image img = slot.view.getImage();
                    double cardW = img != null ? img.getWidth() : 117;
                    double cardH = img != null ? img.getHeight() : 165;

                    slot.view.setScaleX(cardScale);
                    slot.view.setScaleY(cardScale);

                    // Circular arc moving upward from below bottom edge to above top edge
                    double effCardH = cardH * cardScale;
                    double scalePadY = (effCardH - cardH) * 0.5;
                    double yStart = h + scalePadY + 12.0;
                    double yEnd = -cardH - scalePadY - 12.0;
                    double y = yStart + t * (yEnd - yStart);
                    double arcCurve = Math.sin(t * Math.PI) * Math.min(70.0, w * 0.16);
                    double x = (w * 0.52) - (cardW * 0.5) + (35.0 - arcCurve);

                    // Smooth gentle rotation along the circular path
                    double angle = (0.5 - t) * 52.0;

                    double alpha = 1.0;
                    if (t < 0.10) {
                        alpha = t / 0.10;
                    } else if (t > 0.90) {
                        alpha = (1.0 - t) / 0.10;
                    }

                    slot.view.setLayoutX(x);
                    slot.view.setLayoutY(y);
                    slot.view.setRotate(angle);
                    slot.view.setOpacity(Math.max(0.0, Math.min(1.0, alpha)));
                }
            }
        };
        carouselTimer.start();
    }

    private Image pickRandomCard() {
        if (packCards == null || packCards.isEmpty()) {
            return AssetManager.getVisibleNumberCard(1, 3);
        }
        return packCards.get(random.nextInt(packCards.size()));
    }

    private void openOptions() {
        if (optionsOverlay != null) return;

        optionsOverlay = new StackPane();
        optionsOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.85);");

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(420);
        box.setMaxHeight(410);
        box.setPadding(new Insets(26));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label("OPCIONES", 22, true);
        Label nameLbl = PixelUI.label("NOMBRE DEL JUGADOR:", 13, true);
        TextField nameField = PixelUI.pixelField(app.getPlayerName(), 340);

        Label volumeLbl = PixelUI.label("VOLUMEN DE MUSICA:", 13, true);
        PixelUI.PixelSlider volumeSlider = new PixelUI.PixelSlider(app.getVolume(), app::setVolume);

        PixelUI.PixelCheckBox borderlessCheck = new PixelUI.PixelCheckBox("Ventana sin bordes", app.isBorderless());

        Button tutorialBtn = PixelUI.pixelButton("TUTORIAL: COMO JUGAR", 12.5, 340, 36);
        tutorialBtn.setOnAction(_ -> openTutorialModal());

        Label errorLbl = PixelUI.label("", 12, false);
        errorLbl.setStyle("-fx-text-fill: #101010;");

        HBox actions = new HBox(14);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = PixelUI.pixelButton("CANCELAR", 13, 110, 36);
        Button saveBtn = PixelUI.invertedButton("GUARDAR", 13, 110, 36);

        cancelBtn.setOnAction(_ -> closeOptions());
        saveBtn.setOnAction(_ -> {
            String newName = nameField.getText().trim();
            if (newName.isEmpty()) {
                errorLbl.setText("El nombre no puede estar vacio.");
                return;
            }
            app.setPlayerName(newName);
            app.setBorderless(borderlessCheck.isSelected());
            closeOptions();
        });

        actions.getChildren().addAll(cancelBtn, saveBtn);
        box.getChildren().addAll(title, nameLbl, nameField, volumeLbl, volumeSlider, borderlessCheck, tutorialBtn, errorLbl, actions);
        optionsOverlay.getChildren().add(box);
        getChildren().add(optionsOverlay);
    }

    private void openTutorialModal() {
        if (tutorialOverlay != null) return;

        tutorialOverlay = new StackPane();
        tutorialOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.88);");

        VBox box = new VBox(12);
        box.setAlignment(Pos.TOP_LEFT);
        box.setMaxWidth(600);
        box.setMaxHeight(520);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label("TUTORIAL: COMO JUGAR", 20, true);
        String tutorialText =
                "1. OBJETIVO DEL JUEGO\n"
                + "   • Suma cartas numeradas del 1 al 11 para acercarte lo mas posible a 21 (o a la meta actual) sin pasarte.\n"
                + "   • Tu primera carta (en BLANCO Y NEGRO) es constante y permanece oculta para tu rival hasta el final de la ronda.\n"
                + "   • Si ambos jugadores superan la meta, gana quien quede mas cerca del numero objetivo.\n\n"
                + "2. CONTROLES EN PARTIDA (1 MOVIMIENTO POR TURNO)\n"
                + "   • COMER CARTA: Haz TRIPLE CLICK IZQUIERDO en cualquier zona vacia de la mesa.\n"
                + "   • SALTAR / PLANTARSE: Haz TRIPLE CLICK DERECHO en cualquier zona vacia.\n"
                + "   • CARTAS TRUMP (COMODINES): Abre el cajon inferior (^) y haz click en una carta para usarla sin limite por turno. "
                + "Jugar cartas Trump no reinicia el reloj.\n"
                + "   • HOVER EN MESA: Pasa el raton por cualquier carta Trump en el centro para ver que hace.\n\n"
                + "3. SOBRE EL OBJETIVO Y CALAVERA (∞)\n"
                + "   • Si superas la meta aparecera el aviso \"Sobre el objetivo\" y no podras comer mas cartas en esa ronda.\n"
                + "   • Si se agota el temporizador sin que juegues (cuando hay limite de tiempo), recibiras la CALAVERA (∞): "
                + "pierdes la ronda en cuanto el oponente actue o salte.\n\n"
                + "4. TIPO Y CANTIDAD DE APUESTA\n"
                + "   • Tipo - Normal (+0 fichas): El ganador se queda con las fichas apostadas.\n"
                + "   • Tipo - Deficit (+6 fichas): Nadie se queda con las fichas apostadas (el perdedor igual pierde sus fichas).\n"
                + "   • Tipo - Merma (+2 fichas): Despues de la primera ronda, el ganador pierde 1 ficha de la apuesta ganada.\n"
                + "   • Cantidad - Unica: Ambos jugadores juegan con la misma apuesta; ambos pueden aumentarla y bajarla.\n"
                + "   • Cantidad - Individual: Cada jugador juega con su propia apuesta; solo aumentan la del oponente y disminuyen su propia.\n"
                + "   • La apuesta base sube +1 despues de cada ronda. Gana quien deje a su oponente con 0 fichas.";

        Label bodyLbl = PixelUI.label(tutorialText, 11.5, false);
        bodyLbl.setWrapText(true);
        bodyLbl.setMaxWidth(530);

        ScrollPane scroll = new ScrollPane(bodyLbl);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(370);
        scroll.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 2;");

        Button closeBtn = PixelUI.invertedButton("VOLVER A OPCIONES", 13, 210, 38);
        closeBtn.setOnAction(_ -> closeTutorialModal());
        HBox btnRow = new HBox(closeBtn);
        btnRow.setAlignment(Pos.CENTER);

        box.getChildren().addAll(title, scroll, btnRow);
        tutorialOverlay.getChildren().add(box);
        getChildren().add(tutorialOverlay);
    }

    private void closeTutorialModal() {
        if (tutorialOverlay != null) {
            getChildren().remove(tutorialOverlay);
            tutorialOverlay = null;
        }
    }

    private void closeOptions() {
        closeTutorialModal();
        if (optionsOverlay != null) {
            getChildren().remove(optionsOverlay);
            optionsOverlay = null;
        }
    }

    private void stopAnimation() {
        if (carouselTimer != null) {
            carouselTimer.stop();
        }
    }
}
