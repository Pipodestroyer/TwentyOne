package UI;

import Game.config;
import Net.GameStateSnapshot;
import Net.P2PManager;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

public class GameTableView extends StackPane {
    private static final double DRAWER_BODY_HEIGHT = 188;

    private final TwentyOneApp app;
    private final P2PManager p2pManager;

    private Label enemyScoreLabel;
    private Label enemyInfoLabel;
    private Label myScoreLabel;
    private Label myInfoLabel;
    private FlowPane enemyChipsVisualPane;
    private FlowPane myChipsVisualPane;
    private Label enemyChipsTextLabel;
    private Label myChipsTextLabel;
    private Label betTextLabel;

    private HBox enemyCardsRow;
    private HBox continuousTrumpsRow;
    private HBox myCardsRow;

    private Label roundInfoLabel;
    private Label turnLabel;
    private Label actionMsgLabel;
    private Button drawBtn;
    private Button standBtn;

    private VBox roundResultBox;
    private Label roundResultTitle;
    private Label roundResultDetails;
    private Button nextRoundBtn;

    private VBox trumpDrawerZone;
    private HBox trumpCardsContainer;
    private Label trumpDescLabel;
    private boolean drawerOpen = false;
    private TranslateTransition drawerAnim;

    private StackPane pauseOverlay;
    private StackPane disconnectOverlay;
    private GameStateSnapshot currentSnapshot;
    private String lastRenderedKey;

    public GameTableView(TwentyOneApp app, P2PManager p2pManager) {
        this.app = app;
        this.p2pManager = p2pManager;

        setStyle("-fx-background-color: #FFFFFF;");

        // Clip main table so slide-up drawer stays cleanly tucked below edge
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        BorderPane tableLayout = new BorderPane();
        tableLayout.setPadding(new Insets(18, 26, 38, 26));

        tableLayout.setLeft(buildLeftColumn());
        tableLayout.setCenter(buildCenterTable());
        tableLayout.setRight(buildRightColumn());

        // Top-right 3 horizontal pause bars
        VBox pauseIconBtn = buildPauseIcon();
        StackPane.setAlignment(pauseIconBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(pauseIconBtn, new Insets(18, 22, 0, 0));

        // Bottom-center upward arrow + slide-up Trump Cards drawer
        trumpDrawerZone = buildTrumpDrawer();
        StackPane.setAlignment(trumpDrawerZone, Pos.BOTTOM_CENTER);
        trumpDrawerZone.setTranslateY(DRAWER_BODY_HEIGHT);

        getChildren().addAll(tableLayout, pauseIconBtn, trumpDrawerZone);

        p2pManager.setListener(new P2PManager.Listener() {
            @Override
            public void onConnected(String peerInfo) {}

            @Override
            public void onStateUpdated(GameStateSnapshot snapshot) {
                Platform.runLater(() -> renderSnapshot(snapshot));
            }

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> showDisconnectModal(reason));
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> showDisconnectModal(message));
            }
        });
    }

    private VBox buildLeftColumn() {
        VBox left = new VBox(14);
        left.setAlignment(Pos.CENTER_LEFT);
        left.setMinWidth(210);
        left.setMaxWidth(230);
        left.setPadding(new Insets(10, 12, 10, 8));

        // Enemy score block (Top-left)
        VBox enemyBlock = new VBox(4);
        enemyScoreLabel = PixelUI.label("? + 0 / 21", 28, true);
        enemyScoreLabel.setStyle("-fx-text-fill: #B82535;");
        enemyInfoLabel = PixelUI.label("Oponente", 12, false);
        enemyInfoLabel.setStyle("-fx-text-fill: #B82535;");
        enemyBlock.getChildren().addAll(enemyScoreLabel, enemyInfoLabel);

        Region spacer1 = new Region();
        VBox.setVgrow(spacer1, Priority.ALWAYS);

        // Chips block (Center-left)
        VBox chipsBlock = new VBox(6);
        chipsBlock.setAlignment(Pos.CENTER_LEFT);
        enemyChipsTextLabel = PixelUI.label("RIVAL: 0", 12, true);
        enemyChipsTextLabel.setStyle("-fx-text-fill: #B82535;");
        enemyChipsVisualPane = new FlowPane(4, 4);
        enemyChipsVisualPane.setPrefWrapLength(180);

        myChipsTextLabel = PixelUI.label("TUS FICHAS: 0", 12, true);
        myChipsTextLabel.setStyle("-fx-text-fill: #19498A;");
        myChipsVisualPane = new FlowPane(4, 4);
        myChipsVisualPane.setPrefWrapLength(180);

        betTextLabel = PixelUI.label("APUESTA: 1", 12, true);
        chipsBlock.getChildren().addAll(enemyChipsTextLabel, enemyChipsVisualPane, myChipsTextLabel, myChipsVisualPane, betTextLabel);

        Region spacer2 = new Region();
        VBox.setVgrow(spacer2, Priority.ALWAYS);

        // Player score block (Bottom-left)
        VBox myBlock = new VBox(4);
        myScoreLabel = PixelUI.label("0 / 21", 28, true);
        myScoreLabel.setStyle("-fx-text-fill: #19498A;");
        myInfoLabel = PixelUI.label("Tu", 12, false);
        myInfoLabel.setStyle("-fx-text-fill: #19498A;");
        myBlock.getChildren().addAll(myScoreLabel, myInfoLabel);

        left.getChildren().addAll(enemyBlock, spacer1, chipsBlock, spacer2, myBlock);
        return left;
    }

    private VBox buildCenterTable() {
        VBox center = new VBox(14);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10, 16, 10, 16));

        enemyCardsRow = new HBox(12);
        enemyCardsRow.setAlignment(Pos.CENTER);
        enemyCardsRow.setMinHeight(165);

        // Horizontal continuous trump cards zone in center (like sketch's horizontal card)
        continuousTrumpsRow = new HBox(10);
        continuousTrumpsRow.setAlignment(Pos.CENTER);
        continuousTrumpsRow.setMinHeight(64);
        continuousTrumpsRow.setMaxWidth(520);
        continuousTrumpsRow.setPadding(new Insets(6, 12, 6, 12));
        continuousTrumpsRow.setStyle("-fx-border-color: #101010; -fx-border-width: 1; -fx-background-color: #FFFFFF;");

        myCardsRow = new HBox(12);
        myCardsRow.setAlignment(Pos.CENTER);
        myCardsRow.setMinHeight(165);

        center.getChildren().addAll(enemyCardsRow, continuousTrumpsRow, myCardsRow);
        return center;
    }

    private VBox buildRightColumn() {
        VBox right = new VBox(14);
        right.setAlignment(Pos.CENTER);
        right.setMinWidth(210);
        right.setMaxWidth(235);
        right.setPadding(new Insets(46, 8, 10, 12));

        roundInfoLabel = PixelUI.label("RONDA 1", 14, true);
        turnLabel = PixelUI.label("ESPERANDO...", 15, true);
        actionMsgLabel = PixelUI.label("", 11.5, false);
        actionMsgLabel.setWrapText(true);
        actionMsgLabel.setMaxWidth(205);

        drawBtn = PixelUI.invertedButton("COMER CARTA", 13, 185, 42);
        standBtn = PixelUI.pixelButton("PLANTARSE", 13, 185, 42);

        drawBtn.setOnAction(_ -> p2pManager.performLocalDraw());
        standBtn.setOnAction(_ -> p2pManager.performLocalStand());

        roundResultBox = new VBox(10);
        roundResultBox.setAlignment(Pos.CENTER);
        roundResultBox.setPadding(new Insets(12));
        roundResultBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");
        roundResultBox.setVisible(false);
        roundResultBox.setManaged(false);

        roundResultTitle = PixelUI.label("", 14, true);
        roundResultTitle.setWrapText(true);
        roundResultDetails = PixelUI.label("", 11.5, false);
        roundResultDetails.setWrapText(true);
        nextRoundBtn = PixelUI.invertedButton("SIGUIENTE RONDA", 12, 175, 36);
        nextRoundBtn.setOnAction(_ -> {
            if (currentSnapshot != null && currentSnapshot.gameOver) {
                app.showMainMenu();
            } else {
                p2pManager.performLocalNextRound();
            }
        });
        roundResultBox.getChildren().addAll(roundResultTitle, roundResultDetails, nextRoundBtn);

        right.getChildren().addAll(roundInfoLabel, turnLabel, actionMsgLabel, drawBtn, standBtn, roundResultBox);
        return right;
    }

    private VBox buildPauseIcon() {
        VBox bars = new VBox(5);
        bars.setAlignment(Pos.CENTER);
        bars.setPadding(new Insets(8));
        bars.setCursor(Cursor.HAND);
        bars.setMaxSize(46, 38);

        for (int i = 0; i < 3; i++) {
            Rectangle bar = new Rectangle(30, 4, Color.rgb(16, 16, 16));
            bars.getChildren().add(bar);
        }

        bars.setOnMouseEntered(_ -> bars.setOpacity(0.6));
        bars.setOnMouseExited(_ -> bars.setOpacity(1.0));
        bars.setOnMouseClicked(_ -> openPauseModal());
        return bars;
    }

    private VBox buildTrumpDrawer() {
        VBox zone = new VBox(0);
        zone.setAlignment(Pos.BOTTOM_CENTER);
        zone.setMaxWidth(760);
        zone.setMaxHeight(DRAWER_BODY_HEIGHT + 36);

        // Top trigger strip: crisp centered upward chevron "^"
        StackPane arrowTab = new StackPane();
        arrowTab.setPrefHeight(36);
        arrowTab.setMaxWidth(140);
        arrowTab.setCursor(Cursor.HAND);
        arrowTab.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2 2 0 2;");

        Polygon chevron = new Polygon(
                -12.0, 6.0,
                0.0, -6.0,
                12.0, 6.0,
                8.0, 6.0,
                0.0, -2.0,
                -8.0, 6.0
        );
        chevron.setFill(Color.rgb(16, 16, 16));
        arrowTab.getChildren().add(chevron);

        // Drawer body
        VBox body = new VBox(8);
        body.setPrefHeight(DRAWER_BODY_HEIGHT);
        body.setMinHeight(DRAWER_BODY_HEIGHT);
        body.setMaxHeight(DRAWER_BODY_HEIGHT);
        body.setPadding(new Insets(10, 14, 10, 14));
        body.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2 2 0 2;");

        trumpCardsContainer = new HBox(12);
        trumpCardsContainer.setAlignment(Pos.CENTER_LEFT);
        trumpCardsContainer.setPadding(new Insets(4));

        ScrollPane scroll = new ScrollPane(trumpCardsContainer);
        scroll.setFitToHeight(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefHeight(138);
        scroll.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; -fx-border-color: transparent;");

        trumpDescLabel = PixelUI.label("Pasa el mouse sobre un comodin para ver su efecto.", 12, false);
        body.getChildren().addAll(scroll, trumpDescLabel);

        zone.getChildren().addAll(arrowTab, body);

        zone.setOnMouseEntered(_ -> setDrawerOpen(true));
        zone.setOnMouseExited(_ -> setDrawerOpen(false));
        return zone;
    }

    private void setDrawerOpen(boolean open) {
        if (this.drawerOpen == open) return;
        this.drawerOpen = open;
        if (drawerAnim != null) {
            drawerAnim.stop();
        }
        drawerAnim = new TranslateTransition(Duration.millis(180), trumpDrawerZone);
        drawerAnim.setToY(open ? 0 : DRAWER_BODY_HEIGHT);
        drawerAnim.play();
    }

    private void renderSnapshot(GameStateSnapshot s) {
        if (s == null) return;
        String key = s.serialize();
        if (key.equals(lastRenderedKey)) {
            return;
        }
        lastRenderedKey = key;
        this.currentSnapshot = s;

        // Left counters
        int hiddenCount = s.getEnemyHiddenCount();
        int enemyVisSum = s.getEnemyVisibleSum();
        if (hiddenCount > 0 && !s.roundOver) {
            enemyScoreLabel.setText("? + " + enemyVisSum + " / " + s.targetScore);
        } else {
            enemyScoreLabel.setText(enemyVisSum + " / " + s.targetScore);
        }
        enemyInfoLabel.setText(s.enemyName + (s.enemyStood ? " [PLANTADO]" : (!s.myTurn && !s.roundOver ? " [SU TURNO]" : "")));

        myScoreLabel.setText(s.getMyTotalSum() + " / " + s.targetScore);
        myInfoLabel.setText(s.myName + (s.myStood ? " [PLANTADO]" : (s.myTurn ? " [TU TURNO]" : "")));

        // Enemy red chips & Player blue chips (1 chip image = 1 point)
        renderChipRow(enemyChipsVisualPane, s.enemyChips, AssetManager.getEnemyChipImage(2));
        renderChipRow(myChipsVisualPane, s.myChips, AssetManager.getPlayerChipImage(2));
        enemyChipsTextLabel.setText("RIVAL: " + s.enemyChips);
        myChipsTextLabel.setText("TUS FICHAS: " + s.myChips);
        if (s.betMode == config.BET_DOUBLE) {
            betTextLabel.setText("APUESTAS -> TU: " + s.myBet + " | RIVAL: " + s.enemyBet);
        } else {
            betTextLabel.setText("APUESTA EN MESA: " + s.sharedBet);
        }

        // Enemy Cards Row (Black ? hidden card + Red Hearts visible cards)
        enemyCardsRow.getChildren().clear();
        for (GameStateSnapshot.CardView cv : s.enemyCards) {
            Image img;
            if (cv.number < 0) {
                img = AssetManager.getEnemyHiddenCard(3);
            } else if (cv.hiddenCard) {
                img = AssetManager.getPlayerHiddenNumberCard(cv.number, 3);
            } else {
                img = AssetManager.getEnemyVisibleNumberCard(cv.number, 3);
            }
            enemyCardsRow.getChildren().add(makeCardNode(img));
        }

        // Player Cards Row (Black hidden card + Blue Diamonds visible cards)
        myCardsRow.getChildren().clear();
        for (GameStateSnapshot.CardView cv : s.myCards) {
            Image img = cv.hiddenCard
                    ? AssetManager.getPlayerHiddenNumberCard(cv.number, 3)
                    : AssetManager.getPlayerVisibleNumberCard(cv.number, 3);
            myCardsRow.getChildren().add(makeCardNode(img));
        }

        // Continuous Trumps Row
        continuousTrumpsRow.getChildren().clear();
        if (s.myContinuousTrumps.isEmpty() && s.enemyContinuousTrumps.isEmpty()) {
            continuousTrumpsRow.getChildren().add(PixelUI.label("SIN EFECTOS CONTINUOS EN MESA", 11, false));
        } else {
            for (String name : s.enemyContinuousTrumps) {
                continuousTrumpsRow.getChildren().add(makeContinuousBadge(name, false));
            }
            for (String name : s.myContinuousTrumps) {
                continuousTrumpsRow.getChildren().add(makeContinuousBadge(name, true));
            }
        }

        // Right controls
        roundInfoLabel.setText("RONDA " + s.roundNumber + " (META: " + s.targetScore + ")");
        if (s.gameOver) {
            turnLabel.setText("FIN DE PARTIDA");
        } else if (s.roundOver) {
            turnLabel.setText("FIN DE RONDA");
        } else {
            turnLabel.setText(s.myTurn ? "TU TURNO" : "TURNO DE " + s.enemyName.toUpperCase());
        }
        actionMsgLabel.setText(s.lastActionMessage);

        boolean canAct = s.myTurn && !s.roundOver && !s.gameOver;
        drawBtn.setDisable(!canAct);
        standBtn.setDisable(!canAct);
        drawBtn.setOpacity(canAct ? 1.0 : 0.4);
        standBtn.setOpacity(canAct ? 1.0 : 0.4);

        // Round summary / game over box
        if (s.roundOver || s.gameOver) {
            roundResultBox.setVisible(true);
            roundResultBox.setManaged(true);
            if (s.gameOver) {
                boolean iWon = s.myChips > s.enemyChips;
                roundResultTitle.setText(iWon ? "VICTORIA!" : "DERROTA");
                roundResultDetails.setText(s.summaryReason + "\nFichas finales: " + s.myChips + " vs " + s.enemyChips);
                nextRoundBtn.setText("VOLVER AL MENU");
            } else {
                String deltaStr = (s.summaryMyDelta >= 0 ? "+" : "") + s.summaryMyDelta + " fichas";
                roundResultTitle.setText("GANADOR: " + s.summaryWinner);
                roundResultDetails.setText(s.summaryReason + "\nResultado: " + deltaStr
                        + (s.summaryBless ? "\n(Bless salvo tus fichas)" : ""));
                nextRoundBtn.setText("SIGUIENTE RONDA");
            }
        } else {
            roundResultBox.setVisible(false);
            roundResultBox.setManaged(false);
        }

        // Bottom Trump Cards Drawer
        renderTrumpDrawer(s, canAct);
    }

    private ImageView makeCardNode(Image img) {
        ImageView iv = new ImageView(img);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        FadeTransition ft = new FadeTransition(Duration.millis(140), iv);
        ft.setFromValue(0.65);
        ft.setToValue(1.0);
        ft.play();
        return iv;
    }

    private void renderChipRow(FlowPane pane, int count, Image chipImg) {
        pane.getChildren().clear();
        int visualCount = Math.min(count, 12);
        for (int i = 0; i < visualCount; i++) {
            ImageView chipIv = new ImageView(chipImg);
            chipIv.setSmooth(false);
            pane.getChildren().add(chipIv);
        }
        if (count > 12) {
            pane.getChildren().add(PixelUI.label("+" + (count - 12), 11, true));
        }
    }

    private HBox makeContinuousBadge(String trumpName, boolean isMine) {
        String colorHex = isMine ? "#19498A" : "#B82535";
        String ownerTag = isMine ? "TU" : "RIVAL";
        HBox box = new HBox(6);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(4, 8, 4, 8));
        box.setStyle("-fx-background-color: " + colorHex + "; -fx-border-color: #101010; -fx-border-width: 1;");

        ImageView mini = new ImageView(AssetManager.getTrumpCardImage(trumpName, 1));
        mini.setSmooth(false);

        Label txt = new Label(trumpName + " (" + ownerTag + ")");
        txt.setFont(PixelUI.font(11));
        txt.setStyle("-fx-text-fill: #FFFFFF;");

        box.getChildren().addAll(mini, txt);
        return box;
    }

    private void renderTrumpDrawer(GameStateSnapshot s, boolean canAct) {
        trumpCardsContainer.getChildren().clear();
        if (s.myTrumps.isEmpty()) {
            trumpCardsContainer.getChildren().add(PixelUI.label("No tienes comodines disponibles.", 12, false));
            return;
        }
        for (GameStateSnapshot.TrumpView tv : s.myTrumps) {
            VBox cardBox = new VBox(4);
            cardBox.setAlignment(Pos.CENTER);
            cardBox.setPadding(new Insets(6));
            cardBox.setMinWidth(104);
            cardBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");
            cardBox.setCursor(canAct ? Cursor.HAND : Cursor.DEFAULT);
            cardBox.setOpacity(canAct ? 1.0 : 0.55);

            ImageView iv = new ImageView(AssetManager.getTrumpCardImage(tv.name, 2));
            iv.setSmooth(false);
            iv.setFitHeight(84);
            iv.setPreserveRatio(true);

            Label nameLbl = PixelUI.label(tv.name + " x" + tv.count, 10.5, true);
            cardBox.getChildren().addAll(iv, nameLbl);

            cardBox.setOnMouseEntered(_ -> {
                trumpDescLabel.setText(tv.name + " [" + tv.type + "]: " + tv.description);
                if (canAct) {
                    cardBox.setStyle("-fx-background-color: #101010; -fx-border-color: #101010; -fx-border-width: 2;");
                    nameLbl.setStyle("-fx-text-fill: #FFFFFF;");
                }
            });
            cardBox.setOnMouseExited(_ -> {
                cardBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");
                nameLbl.setStyle("-fx-text-fill: #101010;");
            });
            cardBox.setOnMouseClicked(_ -> {
                if (canAct) {
                    p2pManager.performLocalUseTrump(tv.name);
                }
            });
            trumpCardsContainer.getChildren().add(cardBox);
        }
    }

    private void openPauseModal() {
        if (pauseOverlay != null) return;

        pauseOverlay = new StackPane();
        pauseOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.85);");

        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(300);
        box.setMaxHeight(210);
        box.setPadding(new Insets(26));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label("PAUSA", 22, true);
        Button continueBtn = PixelUI.invertedButton("CONTINUAR", 14, 190, 40);
        Button surrenderBtn = PixelUI.pixelButton("RENDIRSE", 14, 190, 40);

        continueBtn.setOnAction(_ -> closePauseModal());
        surrenderBtn.setOnAction(_ -> {
            closePauseModal();
            p2pManager.performLocalSurrender();
        });

        box.getChildren().addAll(title, continueBtn, surrenderBtn);
        pauseOverlay.getChildren().add(box);
        getChildren().add(pauseOverlay);
    }

    private void closePauseModal() {
        if (pauseOverlay != null) {
            getChildren().remove(pauseOverlay);
            pauseOverlay = null;
        }
    }

    private void showDisconnectModal(String reason) {
        if (disconnectOverlay != null) return;
        disconnectOverlay = new StackPane();
        disconnectOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.88);");

        VBox box = new VBox(16);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(380);
        box.setMaxHeight(210);
        box.setPadding(new Insets(26));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label("DESCONECTADO", 20, true);
        Label msg = PixelUI.label(reason, 13, false);
        msg.setWrapText(true);
        Button menuBtn = PixelUI.invertedButton("VOLVER AL MENU", 13, 180, 40);
        menuBtn.setOnAction(_ -> app.showMainMenu());

        box.getChildren().addAll(title, msg, menuBtn);
        disconnectOverlay.getChildren().add(box);
        getChildren().add(disconnectOverlay);
    }
}
