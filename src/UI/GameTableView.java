package UI;

import Game.config;
import Net.GameStateSnapshot;
import Net.P2PManager;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
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
    private Label trumpHoverBanner;
    private HBox myCardsRow;

    private Label roundInfoLabel;
    private Label turnLabel;
    private Label actionMsgLabel;
    private Button drawBtn;
    private Button standBtn;

    private VBox roundResultBox;
    private Label roundResultTitle;
    private Label roundResultDetails;
    private Label roundCountdownLabel;

    private VBox trumpDrawerZone;
    private HBox trumpCardsContainer;
    private Label trumpDescLabel;
    private boolean drawerOpen = false;
    private TranslateTransition drawerAnim;

    private StackPane pauseOverlay;
    private StackPane disconnectOverlay;
    private StackPane gameOverOverlay;
    private Label gameOverRematchLabel;
    private Label gameOverTimerLabel;
    private Button gameOverOkBtn;
    private Timeline roundCountdownTimeline;
    private int roundCountdownSeconds;
    private Timeline gameOverTimeline;
    private int gameOverSecondsLeft;
    private ParallelTransition cardsExitAnim;

    private GameStateSnapshot currentSnapshot;
    private String lastRenderedKey;
    private int lastRenderedPlayedTrumpsCount = 0;
    private int lastRenderedEnemyCardCount = 0;
    private int lastRenderedMyCardCount = 0;
    private int lastRenderedRound = -1;
    private boolean wasRoundOver = false;
    private boolean wasGameOver = false;

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

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                stopAllTimersAndAnimations();
            }
        });

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
        VBox center = new VBox(10);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(10, 16, 10, 16));

        enemyCardsRow = new HBox(12);
        enemyCardsRow.setAlignment(Pos.CENTER);
        enemyCardsRow.setMinHeight(160);

        // Center zone where played trump cards drop onto the table
        VBox centerTrumpsZone = new VBox(4);
        centerTrumpsZone.setAlignment(Pos.CENTER);

        continuousTrumpsRow = new HBox(10);
        continuousTrumpsRow.setAlignment(Pos.CENTER);
        continuousTrumpsRow.setMinHeight(94);
        continuousTrumpsRow.setMaxWidth(620);
        continuousTrumpsRow.setPadding(new Insets(4, 10, 4, 10));

        trumpHoverBanner = PixelUI.label("", 11.5, true);
        trumpHoverBanner.setWrapText(true);
        trumpHoverBanner.setMaxWidth(580);
        trumpHoverBanner.setMinHeight(22);
        trumpHoverBanner.setStyle("-fx-text-fill: #101010; -fx-background-color: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 1; -fx-padding: 3 8 3 8;");
        trumpHoverBanner.setVisible(false);

        centerTrumpsZone.getChildren().addAll(continuousTrumpsRow, trumpHoverBanner);

        myCardsRow = new HBox(12);
        myCardsRow.setAlignment(Pos.CENTER);
        myCardsRow.setMinHeight(160);

        center.getChildren().addAll(enemyCardsRow, centerTrumpsZone, myCardsRow);
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
        standBtn = PixelUI.pixelButton("SALTAR / PLANTARSE", 13, 185, 42);

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
        roundCountdownLabel = PixelUI.label("", 12, true);
        roundCountdownLabel.setStyle("-fx-text-fill: #19498A;");
        roundResultBox.getChildren().addAll(roundResultTitle, roundResultDetails, roundCountdownLabel);

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

        boolean isRematchReset = wasGameOver && !s.gameOver;
        boolean isNewRound = (s.roundNumber != lastRenderedRound) && !isRematchReset;

        if (isNewRound) {
            stopRoundCountdown();
            stopCardsExitAnim();
            resetCardRowsVisualState();
            lastRenderedRound = s.roundNumber;
            lastRenderedPlayedTrumpsCount = 0;
            lastRenderedEnemyCardCount = 0;
            lastRenderedMyCardCount = 0;
            wasRoundOver = false;
            wasGameOver = false;
        }

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
        boolean isBusted = s.getMyTotalSum() > s.targetScore;
        drawBtn.setDisable(!canAct || isBusted);
        standBtn.setDisable(!canAct);
        drawBtn.setOpacity((canAct && !isBusted) ? 1.0 : 0.35);
        standBtn.setOpacity(canAct ? 1.0 : 0.4);

        // Bottom Trump Cards Drawer
        renderTrumpDrawer(s, canAct);

        boolean justRevealed = s.roundOver && !wasRoundOver;
        if (justRevealed && s.summaryMyDelta != 0) {
            animateChipTransfer(s.summaryMyDelta);
        }
        wasRoundOver = s.roundOver;

        if (isRematchReset) {
            stopGameOverCountdown();
            closeGameOverModal();
            stopRoundCountdown();
            wasGameOver = false;
            wasRoundOver = false;
            lastRenderedRound = s.roundNumber;
            lastRenderedPlayedTrumpsCount = 0;
            lastRenderedEnemyCardCount = 0;
            lastRenderedMyCardCount = 0;
            roundResultBox.setVisible(false);
            roundResultBox.setManaged(false);
            playCardsSlideDownFadeOut(() -> renderTableCardsAndTrumps(
                    this.currentSnapshot != null ? this.currentSnapshot : s, false));
            return;
        }

        if (cardsExitAnim == null || cardsExitAnim.getStatus() != Animation.Status.RUNNING) {
            renderTableCardsAndTrumps(s, justRevealed);
        }

        if (s.gameOver) {
            stopRoundCountdown();
            roundResultBox.setVisible(false);
            roundResultBox.setManaged(false);
            openOrUpdateGameOverModal(s);
            wasGameOver = true;
        } else if (s.roundOver) {
            closeGameOverModal();
            stopGameOverCountdown();
            wasGameOver = false;
            roundResultBox.setVisible(true);
            roundResultBox.setManaged(true);
            String deltaStr = (s.summaryMyDelta >= 0 ? "+" : "") + s.summaryMyDelta + " fichas";
            roundResultTitle.setText("GANADOR: " + s.summaryWinner);
            roundResultDetails.setText(s.summaryReason + "\nResultado: " + deltaStr
                    + (s.summaryBless ? "\n(Bless salvo tus fichas)" : ""));
            if (roundCountdownTimeline == null) {
                startRoundCountdown();
            }
        } else {
            closeGameOverModal();
            stopGameOverCountdown();
            stopRoundCountdown();
            wasGameOver = false;
            roundResultBox.setVisible(false);
            roundResultBox.setManaged(false);
        }
    }

    private void renderTableCardsAndTrumps(GameStateSnapshot s, boolean justRevealed) {
        if (s == null) return;
        resetCardRowsVisualState();

        int prevEnemyCount = lastRenderedEnemyCardCount;
        int prevMyCount = lastRenderedMyCardCount;
        boolean initialDealBatch = (prevEnemyCount == 0 && prevMyCount == 0);

        enemyCardsRow.getChildren().clear();
        for (int i = 0; i < s.enemyCards.size(); i++) {
            GameStateSnapshot.CardView cv = s.enemyCards.get(i);
            boolean animateDeal = (i >= prevEnemyCount);
            double dealDelay = animateDeal ? (i - prevEnemyCount) * 110.0 : 0.0;
            if (cv.number < 0) {
                enemyCardsRow.getChildren().add(makeDealerCardNode(AssetManager.getEnemyHiddenCard(3), animateDeal, dealDelay));
            } else if (cv.hiddenCard && justRevealed) {
                enemyCardsRow.getChildren().add(makeFlipRevealCardNode(cv.number));
            } else if (cv.hiddenCard) {
                enemyCardsRow.getChildren().add(makeCardNode(AssetManager.getPlayerHiddenNumberCard(cv.number, 3)));
            } else {
                enemyCardsRow.getChildren().add(makeDealerCardNode(AssetManager.getEnemyVisibleNumberCard(cv.number, 3), animateDeal, dealDelay));
            }
        }
        lastRenderedEnemyCardCount = s.enemyCards.size();

        myCardsRow.getChildren().clear();
        double baseMyDelay = initialDealBatch ? s.enemyCards.size() * 110.0 : 0.0;
        for (int i = 0; i < s.myCards.size(); i++) {
            GameStateSnapshot.CardView cv = s.myCards.get(i);
            boolean animateDeal = (i >= prevMyCount);
            double dealDelay = animateDeal ? (baseMyDelay + (i - prevMyCount) * 110.0) : 0.0;
            Image img = cv.hiddenCard
                    ? AssetManager.getPlayerHiddenNumberCard(cv.number, 3)
                    : AssetManager.getPlayerVisibleNumberCard(cv.number, 3);
            myCardsRow.getChildren().add(makeDealerCardNode(img, animateDeal, dealDelay));
        }
        lastRenderedMyCardCount = s.myCards.size();

        continuousTrumpsRow.getChildren().clear();
        trumpHoverBanner.setVisible(false);
        trumpHoverBanner.setText("");
        int totalPlayed = s.playedTrumps.size();
        for (int i = 0; i < totalPlayed; i++) {
            GameStateSnapshot.PlayedTrumpView pt = s.playedTrumps.get(i);
            boolean animateDrop = i >= lastRenderedPlayedTrumpsCount;
            continuousTrumpsRow.getChildren().add(makeDroppedTrumpNode(pt, i, animateDrop));
        }
        lastRenderedPlayedTrumpsCount = totalPlayed;
    }

    private ImageView makeDealerCardNode(Image img, boolean animateDeal, double delayMs) {
        ImageView iv = makeCardNode(img);
        if (!animateDeal) {
            return iv;
        }
        double startX = Math.max(580.0, getWidth() > 0 ? getWidth() * 0.72 + 180.0 : 640.0);
        iv.setTranslateX(startX);
        iv.setRotate(-6.0);
        iv.setOpacity(0.85);

        TranslateTransition slide = new TranslateTransition(Duration.millis(320), iv);
        slide.setDelay(Duration.millis(delayMs));
        slide.setFromX(startX);
        slide.setToX(0.0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        RotateTransition rot = new RotateTransition(Duration.millis(320), iv);
        rot.setDelay(Duration.millis(delayMs));
        rot.setFromAngle(-6.0);
        rot.setToAngle(0.0);
        rot.setInterpolator(Interpolator.EASE_OUT);

        FadeTransition fade = new FadeTransition(Duration.millis(180), iv);
        fade.setDelay(Duration.millis(delayMs));
        fade.setFromValue(0.85);
        fade.setToValue(1.0);

        new ParallelTransition(slide, rot, fade).play();
        return iv;
    }

    private ImageView makeCardNode(Image img) {
        ImageView iv = new ImageView(img);
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        return iv;
    }

    private void animateChipTransfer(int myDelta) {
        if (myDelta == 0) return;
        Platform.runLater(() -> {
            if (getScene() == null) return;
            boolean toPlayer = myDelta > 0;
            FlowPane fromPane = toPlayer ? enemyChipsVisualPane : myChipsVisualPane;
            FlowPane toPane = toPlayer ? myChipsVisualPane : enemyChipsVisualPane;
            Bounds fromScene = fromPane.localToScene(fromPane.getBoundsInLocal());
            Bounds toScene = toPane.localToScene(toPane.getBoundsInLocal());
            if (fromScene == null || toScene == null) return;

            Point2D startPt = sceneToLocal(fromScene.getMinX() + 14.0, fromScene.getMinY() + 10.0);
            Point2D endPt = sceneToLocal(toScene.getMinX() + 14.0, toScene.getMinY() + 10.0);
            if (startPt == null || endPt == null) return;

            int visualCoins = Math.min(3, Math.abs(myDelta));
            Image chipImg = toPlayer ? AssetManager.getEnemyChipImage(2) : AssetManager.getPlayerChipImage(2);
            for (int i = 0; i < visualCoins; i++) {
                ImageView flyingChip = new ImageView(chipImg);
                flyingChip.setSmooth(false);
                flyingChip.setManaged(false);
                flyingChip.setMouseTransparent(true);
                flyingChip.setLayoutX(startPt.getX() + i * 8.0);
                flyingChip.setLayoutY(startPt.getY());
                getChildren().add(flyingChip);

                double dx = endPt.getX() - startPt.getX();
                double dy = endPt.getY() - startPt.getY();

                TranslateTransition move = new TranslateTransition(Duration.millis(560), flyingChip);
                move.setDelay(Duration.millis(i * 95.0));
                move.setFromX(0.0);
                move.setFromY(0.0);
                move.setToX(dx);
                move.setToY(dy);
                move.setInterpolator(Interpolator.EASE_IN);

                ScaleTransition pulse = new ScaleTransition(Duration.millis(280), flyingChip);
                pulse.setDelay(Duration.millis(i * 95.0));
                pulse.setFromX(1.0);
                pulse.setFromY(1.0);
                pulse.setToX(1.32);
                pulse.setToY(1.32);
                pulse.setAutoReverse(true);
                pulse.setCycleCount(2);

                ParallelTransition pt = new ParallelTransition(move, pulse);
                pt.setOnFinished(_ -> getChildren().remove(flyingChip));
                pt.play();
            }
        });
    }

    private void playCardsSlideDownFadeOut(Runnable onFinished) {
        stopCardsExitAnim();
        if (enemyCardsRow.getChildren().isEmpty()
                && myCardsRow.getChildren().isEmpty()
                && continuousTrumpsRow.getChildren().isEmpty()) {
            if (onFinished != null) onFinished.run();
            return;
        }
        ParallelTransition exit = new ParallelTransition();
        for (javafx.scene.Node row : new javafx.scene.Node[]{enemyCardsRow, continuousTrumpsRow, myCardsRow}) {
            TranslateTransition down = new TranslateTransition(Duration.millis(380), row);
            down.setFromY(0.0);
            down.setToY(95.0);
            down.setInterpolator(Interpolator.EASE_IN);

            FadeTransition fade = new FadeTransition(Duration.millis(380), row);
            fade.setFromValue(row.getOpacity());
            fade.setToValue(0.0);
            exit.getChildren().addAll(down, fade);
        }
        cardsExitAnim = exit;
        exit.setOnFinished(_ -> {
            cardsExitAnim = null;
            if (onFinished != null) {
                onFinished.run();
            }
        });
        exit.play();
    }

    private void stopCardsExitAnim() {
        if (cardsExitAnim != null) {
            cardsExitAnim.stop();
            cardsExitAnim = null;
        }
    }

    private void resetCardRowsVisualState() {
        for (javafx.scene.Node row : new javafx.scene.Node[]{enemyCardsRow, continuousTrumpsRow, myCardsRow}) {
            row.setTranslateY(0.0);
            row.setOpacity(1.0);
        }
    }

    private void startRoundCountdown() {
        stopRoundCountdown();
        roundCountdownSeconds = 10;
        roundCountdownLabel.setText("SIGUIENTE RONDA EN 10s...");
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            roundCountdownSeconds--;
            if (roundCountdownSeconds > 0) {
                roundCountdownLabel.setText("SIGUIENTE RONDA EN " + roundCountdownSeconds + "s...");
            } else {
                stopRoundCountdown();
                roundCountdownLabel.setText("INICIANDO...");
                playCardsSlideDownFadeOut(() -> {
                    if (getScene() == null || currentSnapshot == null
                            || !currentSnapshot.roundOver || currentSnapshot.gameOver) {
                        return;
                    }
                    if (p2pManager.isHost()) {
                        p2pManager.performLocalNextRound();
                    } else {
                        int waitedRound = currentSnapshot.roundNumber;
                        PauseTransition fallback = new PauseTransition(Duration.millis(800));
                        fallback.setOnFinished(_ -> {
                            if (getScene() != null && currentSnapshot != null
                                    && currentSnapshot.roundOver && !currentSnapshot.gameOver
                                    && currentSnapshot.roundNumber == waitedRound) {
                                p2pManager.performLocalNextRound();
                            }
                        });
                        fallback.play();
                    }
                });
            }
        }));
        tl.setCycleCount(10);
        roundCountdownTimeline = tl;
        tl.play();
    }

    private void stopRoundCountdown() {
        if (roundCountdownTimeline != null) {
            roundCountdownTimeline.stop();
            roundCountdownTimeline = null;
        }
    }

    private void openOrUpdateGameOverModal(GameStateSnapshot s) {
        if (gameOverOverlay == null) {
            gameOverOverlay = new StackPane();
            gameOverOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.85);");

            VBox box = new VBox(14);
            box.setAlignment(Pos.CENTER);
            box.setMaxWidth(450);
            box.setMaxHeight(330);
            box.setPadding(new Insets(26));
            box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

            Label title = PixelUI.label("PARTIDA FINALIZADA", 20, true);

            boolean iWon = s.myChips > s.enemyChips;
            String outcomeText = iWon ? "GANASTE LA PARTIDA!" : (s.myChips < s.enemyChips ? "PERDISTE LA PARTIDA" : "EMPATE FINAL");
            Label outcomeLbl = PixelUI.label(outcomeText, 16, true);
            outcomeLbl.setStyle("-fx-text-fill: " + (iWon ? "#19498A" : "#B82535") + ";");

            Label detailsLbl = PixelUI.label(
                    s.summaryReason + "\nFichas finales -> Tu: " + s.myChips + " | Rival: " + s.enemyChips,
                    12.5,
                    false
            );
            detailsLbl.setWrapText(true);

            gameOverRematchLabel = PixelUI.label("", 12, true);
            gameOverRematchLabel.setWrapText(true);

            gameOverTimerLabel = PixelUI.label("", 11.5, false);
            gameOverTimerLabel.setStyle("-fx-text-fill: #B82535;");
            gameOverTimerLabel.setWrapText(true);

            HBox btnRow = new HBox(14);
            btnRow.setAlignment(Pos.CENTER);

            gameOverOkBtn = PixelUI.invertedButton("OK", 14, 150, 42);
            gameOverOkBtn.setOnAction(_ -> p2pManager.performLocalRematchReady());

            Button exitBtn = PixelUI.pixelButton("SALIR", 13, 120, 42);
            exitBtn.setOnAction(_ -> {
                stopAllTimersAndAnimations();
                p2pManager.close();
                app.showMainMenu();
            });

            btnRow.getChildren().addAll(gameOverOkBtn, exitBtn);
            box.getChildren().addAll(title, outcomeLbl, detailsLbl, gameOverRematchLabel, gameOverTimerLabel, btnRow);
            gameOverOverlay.getChildren().add(box);
            getChildren().add(gameOverOverlay);
            startGameOverCountdown();
        }

        if (s.myRematchReady) {
            gameOverOkBtn.setText("ESPERANDO...");
            gameOverOkBtn.setDisable(true);
            gameOverOkBtn.setOpacity(0.5);
        } else {
            gameOverOkBtn.setText("OK");
            gameOverOkBtn.setDisable(false);
            gameOverOkBtn.setOpacity(1.0);
        }

        if (s.myRematchReady && s.enemyRematchReady) {
            gameOverRematchLabel.setText("Ambos confirmaron. Iniciando nueva partida... (2/2)");
        } else if (s.myRematchReady) {
            gameOverRematchLabel.setText("Esperando confirmacion del rival para otra partida... (1/2)");
        } else if (s.enemyRematchReady) {
            gameOverRematchLabel.setText("El rival quiere jugar otra partida. Pulsa OK! (1/2)");
        } else {
            gameOverRematchLabel.setText("Pulsa OK para jugar otra partida (0/2 listos)");
        }
    }

    private void startGameOverCountdown() {
        stopGameOverCountdown();
        gameOverSecondsLeft = 30;
        if (gameOverTimerLabel != null) {
            gameOverTimerLabel.setText("La partida terminara en 30s si ambos no confirman.");
        }
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            gameOverSecondsLeft--;
            if (gameOverSecondsLeft > 0) {
                if (gameOverTimerLabel != null) {
                    gameOverTimerLabel.setText("La partida terminara en " + gameOverSecondsLeft + "s si ambos no confirman.");
                }
            } else {
                stopAllTimersAndAnimations();
                p2pManager.close();
                app.showMainMenu();
            }
        }));
        tl.setCycleCount(30);
        gameOverTimeline = tl;
        tl.play();
    }

    private void stopGameOverCountdown() {
        if (gameOverTimeline != null) {
            gameOverTimeline.stop();
            gameOverTimeline = null;
        }
    }

    private void closeGameOverModal() {
        if (gameOverOverlay != null) {
            getChildren().remove(gameOverOverlay);
            gameOverOverlay = null;
            gameOverRematchLabel = null;
            gameOverTimerLabel = null;
            gameOverOkBtn = null;
        }
    }

    private void stopAllTimersAndAnimations() {
        stopRoundCountdown();
        stopGameOverCountdown();
        stopCardsExitAnim();
    }

    private ImageView makeFlipRevealCardNode(int revealedNumber) {
        ImageView iv = new ImageView(AssetManager.getEnemyHiddenCard(3));
        iv.setSmooth(false);
        iv.setPreserveRatio(true);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(150), iv);
        shrink.setFromX(1.0);
        shrink.setToX(0.0);
        shrink.setOnFinished(_ -> iv.setImage(AssetManager.getPlayerHiddenNumberCard(revealedNumber, 3)));

        ScaleTransition expand = new ScaleTransition(Duration.millis(150), iv);
        expand.setFromX(0.0);
        expand.setToX(1.0);

        new SequentialTransition(shrink, expand).play();
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

    private StackPane makeDroppedTrumpNode(GameStateSnapshot.PlayedTrumpView pt, int index, boolean animateDrop) {
        StackPane cardFrame = new StackPane();
        cardFrame.setPadding(Insets.EMPTY);
        cardFrame.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        cardFrame.setCursor(Cursor.HAND);

        if ("CONTINUOUS".equals(pt.type) && !pt.activeContinuous) {
            cardFrame.setOpacity(0.42);
        }

        ImageView iv = new ImageView(AssetManager.getTrumpCardImage(pt.name, 2));
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        iv.setFitHeight(82);
        cardFrame.getChildren().add(iv);

        double restAngle = ((index % 3) - 1) * 3.5;
        cardFrame.setRotate(restAngle);

        if (animateDrop) {
            TranslateTransition drop = new TranslateTransition(Duration.millis(240), cardFrame);
            drop.setFromY(pt.mine ? 55.0 : -55.0);
            drop.setToY(0.0);

            ScaleTransition scale = new ScaleTransition(Duration.millis(240), cardFrame);
            scale.setFromX(1.4);
            scale.setFromY(1.4);
            scale.setToX(1.0);
            scale.setToY(1.0);

            RotateTransition rot = new RotateTransition(Duration.millis(240), cardFrame);
            rot.setFromAngle(pt.mine ? -12.0 : 12.0);
            rot.setToAngle(restAngle);

            new ParallelTransition(drop, scale, rot).play();
        }

        String ownerTag = pt.mine ? "TU" : pt.ownerName.toUpperCase();
        cardFrame.setOnMouseEntered(_ -> {
            cardFrame.setTranslateY(-6);
            trumpHoverBanner.setText("[" + ownerTag + "] " + pt.name + ": " + pt.description);
            trumpHoverBanner.setVisible(true);
        });
        cardFrame.setOnMouseExited(_ -> {
            cardFrame.setTranslateY(0);
            trumpHoverBanner.setVisible(false);
            trumpHoverBanner.setText("");
        });
        return cardFrame;
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
        stopAllTimersAndAnimations();
        closeGameOverModal();
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
        menuBtn.setOnAction(_ -> {
            stopAllTimersAndAnimations();
            p2pManager.close();
            app.showMainMenu();
        });

        box.getChildren().addAll(title, msg, menuBtn);
        disconnectOverlay.getChildren().add(box);
        getChildren().add(disconnectOverlay);
    }
}
