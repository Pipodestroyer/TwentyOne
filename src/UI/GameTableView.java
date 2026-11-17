package UI;

import Game.config;
import Game.numberCard;
import Net.GameStateSnapshot;
import Net.P2PManager;
import javafx.animation.Animation;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.RotateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class GameTableView extends StackPane {
    private static final double DRAWER_BODY_HEIGHT = 204;
    private static final double CARD_BASE_HEIGHT = 110.0;
    private static final long VOID_TRIPLE_CLICK_WINDOW_MS = 900;
    private static final int TURN_TIME_LIMIT_SECONDS = 45;

    private static final Interpolator FRICTION_DECEL = new Interpolator() {
        @Override
        protected double curve(double t) {
            double inv = 1.0 - t;
            return 1.0 - (inv * inv * inv * inv);
        }
    };

    private static final String[] DRAW_WORDS = {
            "¿otra carta?", "¿te atreves?", "21 o nada", "el dealer mira...",
            "riesgo...", "una mas...", "hmm...", "al limite",
            "sin miedo", "tentando a la suerte", "pide otra...", "¿te pasaras?"
    };

    private static final String[] STAND_WORDS = {
            "me planto...", "suficiente", "mano firme", "sin arriesgar",
            "calma...", "que hable la mesa", "¿miedo?", "hasta aqui",
            "¿bastara?", "cierro turno", "respira...", "a esperar..."
    };

    private static class CursorWordTracker {
        final Label label;
        final long birthNs;
        final double offsetX;
        final double offsetY;
        double currentX;
        double currentY;

        CursorWordTracker(Label label, long birthNs, double startX, double startY, double offsetX, double offsetY) {
            this.label = label;
            this.birthNs = birthNs;
            this.currentX = startX;
            this.currentY = startY;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
        }
    }

    private final TwentyOneApp app;
    private final P2PManager p2pManager;
    private final Random random = new Random();

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
    private HBox overTargetBox;
    private Canvas overTargetIconCanvas;
    private Label overTargetLabel;
    private Timeline overTargetFlashTimeline;
    private HBox myCardsRow;
    private ColorAdjust myCardsColorAdjust;
    private Timeline myCardsTurnAnim;
    private Boolean lastMyCardsActiveVisual;

    private StackPane turnTimerBox;
    private Label turnTimerLabel;
    private Timeline turnTimerTimeline;
    private int turnSecondsLeft = TURN_TIME_LIMIT_SECONDS;
    private boolean lastTurnState = false;
    private int lastTurnActionStamp = -1;

    private Label roundInfoLabel;
    private Label turnLabel;
    private Label actionMsgLabel;
    private VBox pauseIconBtn;

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
    private StackPane rulesOverlay;
    private StackPane disconnectOverlay;
    private StackPane gameOverOverlay;
    private Label gameOverRematchLabel;
    private Label gameOverTimerLabel;
    private Button gameOverOkBtn;
    private PauseTransition roundTransitionPause;
    private Timeline gameOverTimeline;
    private int gameOverSecondsLeft;
    private ParallelTransition cardsExitAnim;

    private final Set<Integer> seenTrumpIds = new HashSet<>();
    private final Set<Integer> expiredInstantTrumpIds = new HashSet<>();
    private final Set<Integer> scheduledExpirationIds = new HashSet<>();
    private final Map<Integer, StackPane> activeTrumpNodes = new LinkedHashMap<>();
    private final List<Animation> activeTrumpAnimations = new ArrayList<>();

    private double lastMouseX = 540.0;
    private double lastMouseY = 340.0;
    private int voidLeftClicks = 0;
    private long lastVoidLeftClickMs = 0;
    private int voidRightClicks = 0;
    private long lastVoidRightClickMs = 0;
    private boolean awaitingMoveAck = false;
    private final List<CursorWordTracker> activeCursorWords = new ArrayList<>();
    private AnimationTimer cursorWordsTimer;

    private GameStateSnapshot currentSnapshot;
    private String lastRenderedKey;
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
        pauseIconBtn = buildPauseIcon();
        StackPane.setAlignment(pauseIconBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(pauseIconBtn, new Insets(18, 22, 0, 0));

        // Top-center 45s turn timer
        turnTimerBox = buildTurnTimerBox();
        StackPane.setAlignment(turnTimerBox, Pos.TOP_CENTER);
        StackPane.setMargin(turnTimerBox, new Insets(14, 0, 0, 0));
        turnTimerBox.setMouseTransparent(true);

        // Bottom-center upward arrow + slide-up Trump Cards drawer
        trumpDrawerZone = buildTrumpDrawer();
        StackPane.setAlignment(trumpDrawerZone, Pos.BOTTOM_CENTER);
        trumpDrawerZone.setTranslateY(DRAWER_BODY_HEIGHT);

        getChildren().addAll(tableLayout, turnTimerBox, pauseIconBtn, trumpDrawerZone);
        setupVoidInteractionHandlers();

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
                Platform.runLater(() -> {
                    awaitingMoveAck = false;
                    renderSnapshot(snapshot);
                });
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
        left.setMinWidth(215);
        left.setMaxWidth(240);
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
        enemyChipsVisualPane = new FlowPane(6, 6);
        enemyChipsVisualPane.setPrefWrapLength(210);

        myChipsTextLabel = PixelUI.label("TUS FICHAS: 0", 12, true);
        myChipsTextLabel.setStyle("-fx-text-fill: #19498A;");
        myChipsVisualPane = new FlowPane(6, 6);
        myChipsVisualPane.setPrefWrapLength(210);

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
        enemyCardsRow.setMinHeight(120);

        // Center zone where played trump cards drop onto the table
        VBox centerTrumpsZone = new VBox(4);
        centerTrumpsZone.setAlignment(Pos.CENTER);

        continuousTrumpsRow = new HBox(10);
        continuousTrumpsRow.setAlignment(Pos.CENTER);
        continuousTrumpsRow.setMinHeight(120);
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
        myCardsRow.setMinHeight(120);
        myCardsColorAdjust = new ColorAdjust();
        myCardsRow.setEffect(myCardsColorAdjust);

        overTargetBox = buildOverTargetBox();

        center.getChildren().addAll(enemyCardsRow, centerTrumpsZone, overTargetBox, myCardsRow);
        return center;
    }

    private HBox buildOverTargetBox() {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(5, 12, 5, 12));
        box.setMaxWidth(Region.USE_PREF_SIZE);
        box.setMouseTransparent(true);

        overTargetIconCanvas = new Canvas(18, 16);
        overTargetLabel = PixelUI.label("Sobre el objetivo", 12, true);
        box.getChildren().addAll(overTargetIconCanvas, overTargetLabel);

        applyOverTargetFlashStyle(true);
        box.setVisible(false);
        box.setManaged(false);

        final boolean[] inverted = {true};
        overTargetFlashTimeline = new Timeline(new KeyFrame(Duration.millis(360), _ -> {
            inverted[0] = !inverted[0];
            applyOverTargetFlashStyle(inverted[0]);
        }));
        overTargetFlashTimeline.setCycleCount(Animation.INDEFINITE);
        return box;
    }

    private void applyOverTargetFlashStyle(boolean blackFill) {
        if (overTargetBox == null) return;
        if (blackFill) {
            overTargetBox.setStyle("-fx-background-color: #000000; -fx-border-color: #000000; -fx-border-width: 2;");
            overTargetLabel.setStyle("-fx-text-fill: #FFFFFF;");
            drawPixelHazardIcon(Color.WHITE, Color.BLACK);
        } else {
            overTargetBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #000000; -fx-border-width: 2;");
            overTargetLabel.setStyle("-fx-text-fill: #000000;");
            drawPixelHazardIcon(Color.BLACK, Color.WHITE);
        }
    }

    private void drawPixelHazardIcon(Color triangleColor, Color exclamColor) {
        if (overTargetIconCanvas == null) return;
        GraphicsContext gc = overTargetIconCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, 18, 16);
        gc.setFill(triangleColor);
        // Pixel-art hazard triangle rows (2px pixel blocks)
        gc.fillRect(8, 0, 2, 2);
        gc.fillRect(6, 2, 6, 2);
        gc.fillRect(5, 4, 8, 2);
        gc.fillRect(4, 6, 10, 2);
        gc.fillRect(3, 8, 12, 2);
        gc.fillRect(2, 10, 14, 2);
        gc.fillRect(1, 12, 16, 2);
        gc.fillRect(0, 14, 18, 2);
        // Inner exclamation mark
        gc.setFill(exclamColor);
        gc.fillRect(8, 5, 2, 5);
        gc.fillRect(8, 11, 2, 2);
    }

    private StackPane buildTurnTimerBox() {
        StackPane box = new StackPane();
        box.setPadding(new Insets(5, 14, 5, 14));
        box.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");
        turnTimerLabel = PixelUI.label("TIEMPO: 45s", 13.5, true);
        box.getChildren().add(turnTimerLabel);
        box.setVisible(false);
        box.setManaged(false);
        return box;
    }

    private VBox buildRightColumn() {
        VBox right = new VBox(14);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setMinWidth(210);
        right.setMaxWidth(235);
        right.setPadding(new Insets(46, 18, 10, 12));

        roundInfoLabel = PixelUI.label("RONDA 1", 14, true);
        roundInfoLabel.setMaxWidth(215);
        roundInfoLabel.setAlignment(Pos.CENTER_RIGHT);
        roundInfoLabel.setTextAlignment(TextAlignment.RIGHT);

        turnLabel = PixelUI.label("ESPERANDO...", 15, true);
        turnLabel.setMaxWidth(215);
        turnLabel.setAlignment(Pos.CENTER_RIGHT);
        turnLabel.setTextAlignment(TextAlignment.RIGHT);

        actionMsgLabel = PixelUI.label("", 11.5, false);
        actionMsgLabel.setWrapText(true);
        actionMsgLabel.setMaxWidth(215);
        actionMsgLabel.setAlignment(Pos.CENTER_RIGHT);
        actionMsgLabel.setTextAlignment(TextAlignment.RIGHT);

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

        right.getChildren().addAll(roundInfoLabel, turnLabel, actionMsgLabel, roundResultBox);
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
        VBox body = new VBox(6);
        body.setPrefHeight(DRAWER_BODY_HEIGHT);
        body.setMinHeight(DRAWER_BODY_HEIGHT);
        body.setMaxHeight(DRAWER_BODY_HEIGHT);
        body.setPadding(new Insets(8, 14, 8, 14));
        body.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2 2 0 2;");

        HBox drawerHeader = new HBox(8);
        drawerHeader.setAlignment(Pos.CENTER_LEFT);
        Label drawerTitle = PixelUI.label("CARTAS TRUMP (CLICK PARA USAR EN TU TURNO)", 11, true);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        Button rulesBtn = PixelUI.pixelButton("?", 12, 28, 24);
        rulesBtn.setOnAction(_ -> openRulesModal());
        drawerHeader.getChildren().addAll(drawerTitle, headerSpacer, rulesBtn);

        trumpCardsContainer = new HBox(10);
        trumpCardsContainer.setAlignment(Pos.CENTER);
        trumpCardsContainer.setPadding(new Insets(4));
        trumpCardsContainer.setPrefHeight(124);
        trumpCardsContainer.setMinHeight(124);
        trumpCardsContainer.setMaxWidth(732);

        trumpDescLabel = PixelUI.label("Pasa el mouse sobre un comodin para ver su efecto.", 12, false);
        body.getChildren().addAll(drawerHeader, trumpCardsContainer, trumpDescLabel);

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
        awaitingMoveAck = false;

        boolean isRematchReset = wasGameOver && !s.gameOver;
        boolean isNewRound = (s.roundNumber != lastRenderedRound) && !isRematchReset;

        if (isNewRound) {
            boolean resumeAfterPreviousRound = (lastRenderedRound > 0);
            stopRoundCountdown();
            stopCardsExitAnim();
            clearTrackedTrumps();
            resetCardRowsVisualState();
            lastRenderedRound = s.roundNumber;
            lastRenderedEnemyCardCount = 0;
            lastRenderedMyCardCount = 0;
            voidLeftClicks = 0;
            voidRightClicks = 0;
            lastMyCardsActiveVisual = null;
            wasRoundOver = false;
            wasGameOver = false;
            if (resumeAfterPreviousRound) {
                SoundManager.resumeAndFadeIn(750);
            }
        }

        // Left counters
        int hiddenCount = s.getEnemyHiddenCount();
        int enemyVisSum = s.getEnemyVisibleSum();
        boolean enemySkull = s.hasEnemyVisibleSkullCard();
        if (hiddenCount > 0 && !s.roundOver) {
            enemyScoreLabel.setText((enemySkull ? "? + ∞" : "? + " + enemyVisSum) + " / " + s.targetScore);
        } else {
            enemyScoreLabel.setText((enemySkull ? "∞" : String.valueOf(enemyVisSum)) + " / " + s.targetScore);
        }
        enemyInfoLabel.setText(s.enemyName + (s.enemyStood ? " [PLANTADO]" : (!s.myTurn && !s.roundOver ? " [SU TURNO]" : "")));

        boolean mySkull = s.hasMySkullCard();
        myScoreLabel.setText((mySkull ? "∞" : String.valueOf(s.getMyTotalSum())) + " / " + s.targetScore);
        myInfoLabel.setText(s.myName + (s.myStood ? " [PLANTADO]" : (s.myTurn ? " [TU TURNO]" : "")));

        // Enemy red chips & Player blue chips (bigger stacked chips)
        renderChipRow(enemyChipsVisualPane, s.enemyChips, AssetManager.getEnemyChipImage(3));
        renderChipRow(myChipsVisualPane, s.myChips, AssetManager.getPlayerChipImage(3));
        enemyChipsTextLabel.setText("RIVAL: " + s.enemyChips);
        myChipsTextLabel.setText("TUS FICHAS: " + s.myChips);
        String betTypeTag = switch ((byte) s.idJudge) {
            case config.JUDGE_BANK -> " [DEFICIT]";
            case config.JUDGE_MAFIA -> " [MERMA]";
            default -> " [NORMAL]";
        };
        if (s.betMode == config.BET_DOUBLE) {
            betTextLabel.setText("APUESTA INDIVIDUAL -> TU: " + s.myBet + " | RIVAL: " + s.enemyBet + betTypeTag);
        } else {
            betTextLabel.setText("APUESTA UNICA: " + s.sharedBet + betTypeTag);
        }

        // Right info
        roundInfoLabel.setText("RONDA " + s.roundNumber + " (META: " + s.targetScore + ")");
        if (s.gameOver) {
            turnLabel.setText("FIN DE PARTIDA");
        } else if (s.roundOver) {
            turnLabel.setText("");
        } else {
            turnLabel.setText(s.myTurn ? "TU TURNO" : "TURNO DE " + s.enemyName.toUpperCase());
        }
        actionMsgLabel.setText(s.roundOver && !s.gameOver ? "" : s.lastActionMessage);

        boolean canAct = s.myTurn && !s.roundOver && !s.gameOver;
        boolean isBusted = mySkull || s.getMyTotalSum() > s.targetScore;

        updateOverTargetWarning(!s.roundOver && !s.gameOver && isBusted);
        updateTurnTimerForSnapshot(s, isNewRound, isRematchReset);
        updateMyCardsTurnVisual(s.myTurn || s.roundOver || s.gameOver);

        // Bottom Trump Cards Drawer
        renderTrumpDrawer(s, canAct);

        boolean justRevealed = s.roundOver && !wasRoundOver;
        if (justRevealed) {
            SoundManager.fadeOutAndPause(650);
            if (s.summaryMyDelta != 0) {
                animateChipTransfer(s.summaryMyDelta);
            }
        }
        wasRoundOver = s.roundOver;

        if (isRematchReset) {
            stopGameOverCountdown();
            closeGameOverModal();
            stopRoundCountdown();
            clearTrackedTrumps();
            wasGameOver = false;
            wasRoundOver = false;
            lastRenderedRound = s.roundNumber;
            lastRenderedEnemyCardCount = 0;
            lastRenderedMyCardCount = 0;
            voidLeftClicks = 0;
            voidRightClicks = 0;
            roundResultBox.setVisible(false);
            roundResultBox.setManaged(false);
            SoundManager.resumeAndFadeIn(750);
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
            roundResultBox.setVisible(false);
            roundResultBox.setManaged(false);
            if (roundTransitionPause == null) {
                startDynamicRoundTransition(s);
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
            double dealDelay = animateDeal ? (i - prevEnemyCount) * 120.0 : 0.0;
            if (cv.number < 0) {
                enemyCardsRow.getChildren().add(makeDealerCardNode(AssetManager.getEnemyHiddenCard(2), true, animateDeal, dealDelay));
            } else if (numberCard.isSkullCard(cv.number)) {
                enemyCardsRow.getChildren().add(makeDealerCardNode(AssetManager.getSkullCard(2), true, animateDeal, dealDelay));
            } else if (cv.hiddenCard && justRevealed) {
                enemyCardsRow.getChildren().add(makeFlipRevealCardNode(cv.number));
            } else if (cv.hiddenCard) {
                enemyCardsRow.getChildren().add(makeCardNode(AssetManager.getPlayerHiddenNumberCard(cv.number, 2)));
            } else {
                enemyCardsRow.getChildren().add(makeDealerCardNode(AssetManager.getEnemyVisibleNumberCard(cv.number, 2), true, animateDeal, dealDelay));
            }
        }
        lastRenderedEnemyCardCount = s.enemyCards.size();

        myCardsRow.getChildren().clear();
        double baseMyDelay = initialDealBatch ? s.enemyCards.size() * 120.0 : 0.0;
        for (int i = 0; i < s.myCards.size(); i++) {
            GameStateSnapshot.CardView cv = s.myCards.get(i);
            boolean animateDeal = (i >= prevMyCount);
            double dealDelay = animateDeal ? (baseMyDelay + (i - prevMyCount) * 120.0) : 0.0;
            Image img;
            if (numberCard.isSkullCard(cv.number)) {
                img = AssetManager.getSkullCard(2);
            } else if (cv.hiddenCard) {
                img = AssetManager.getPlayerHiddenNumberCard(cv.number, 2);
            } else {
                img = AssetManager.getPlayerVisibleNumberCard(cv.number, 2);
            }
            myCardsRow.getChildren().add(makeDealerCardNode(img, false, animateDeal, dealDelay));
        }
        lastRenderedMyCardCount = s.myCards.size();

        syncTableTrumps(s);
    }

    private void syncTableTrumps(GameStateSnapshot s) {
        if (s.playedTrumps.isEmpty()) {
            continuousTrumpsRow.getChildren().clear();
            activeTrumpNodes.clear();
            seenTrumpIds.clear();
            expiredInstantTrumpIds.clear();
            scheduledExpirationIds.clear();
            trumpHoverBanner.setVisible(false);
            trumpHoverBanner.setText("");
            return;
        }

        List<GameStateSnapshot.PlayedTrumpView> visibleTrumps = new ArrayList<>();
        for (GameStateSnapshot.PlayedTrumpView pt : s.playedTrumps) {
            if (!expiredInstantTrumpIds.contains(pt.id)) {
                visibleTrumps.add(pt);
            }
        }

        int totalVisible = visibleTrumps.size();
        continuousTrumpsRow.setSpacing(totalVisible > 5 ? Math.max(4.0, 10.0 - (totalVisible - 5) * 1.5) : 10.0);
        double targetFitH = (totalVisible > 5)
                ? Math.max(62.0, CARD_BASE_HEIGHT * (5.0 / totalVisible))
                : CARD_BASE_HEIGHT;

        for (int i = 0; i < totalVisible; i++) {
            GameStateSnapshot.PlayedTrumpView pt = visibleTrumps.get(i);
            StackPane existing = activeTrumpNodes.get(pt.id);
            if (existing != null) {
                if ("CONTINUOUS".equals(pt.type) && !pt.activeContinuous) {
                    existing.setOpacity(0.42);
                    if (scheduledExpirationIds.add(pt.id)) {
                        scheduleInstantTrumpExpiration(pt.id, existing, 640.0);
                    }
                }
                for (Node child : existing.getChildren()) {
                    if (child instanceof ImageView iv) {
                        iv.setFitHeight(targetFitH);
                    }
                }
            } else {
                if ("CONTINUOUS".equals(pt.type) && !pt.activeContinuous) {
                    seenTrumpIds.add(pt.id);
                    expiredInstantTrumpIds.add(pt.id);
                    continue;
                }
                boolean animateDrop = !seenTrumpIds.contains(pt.id);
                seenTrumpIds.add(pt.id);
                StackPane node = makeDroppedTrumpNode(pt, i, totalVisible, animateDrop);
                activeTrumpNodes.put(pt.id, node);
                continuousTrumpsRow.getChildren().add(node);
                if ("INSTANTANEOUS".equalsIgnoreCase(pt.type) && scheduledExpirationIds.add(pt.id)) {
                    scheduleInstantTrumpExpiration(pt.id, node, animateDrop ? 640.0 : 0.0);
                }
            }
        }
    }

    private void scheduleInstantTrumpExpiration(int trumpId, StackPane node, double initialDropMs) {
        PauseTransition hold = new PauseTransition(Duration.millis(initialDropMs + 3000.0));
        activeTrumpAnimations.add(hold);
        hold.setOnFinished(_ -> {
            activeTrumpAnimations.remove(hold);
            if (getScene() == null || !continuousTrumpsRow.getChildren().contains(node)) {
                expiredInstantTrumpIds.add(trumpId);
                activeTrumpNodes.remove(trumpId);
                return;
            }
            FadeTransition fadeOut = new FadeTransition(Duration.millis(320), node);
            fadeOut.setFromValue(node.getOpacity());
            fadeOut.setToValue(0.0);
            activeTrumpAnimations.add(fadeOut);
            fadeOut.setOnFinished(_ -> {
                activeTrumpAnimations.remove(fadeOut);
                expiredInstantTrumpIds.add(trumpId);
                activeTrumpNodes.remove(trumpId);

                Map<Node, Double> oldSceneX = new HashMap<>();
                for (Node child : continuousTrumpsRow.getChildren()) {
                    if (child != node) {
                        oldSceneX.put(child, child.localToScene(0, 0).getX());
                    }
                }
                continuousTrumpsRow.getChildren().remove(node);
                continuousTrumpsRow.applyCss();
                continuousTrumpsRow.layout();

                for (Map.Entry<Node, Double> entry : oldSceneX.entrySet()) {
                    Node rem = entry.getKey();
                    double dx = entry.getValue() - rem.localToScene(0, 0).getX();
                    if (Math.abs(dx) > 0.5) {
                        double fromX = rem.getTranslateX() + dx;
                        rem.setTranslateX(fromX);
                        TranslateTransition shift = new TranslateTransition(Duration.millis(260), rem);
                        shift.setFromX(fromX);
                        shift.setToX(0.0);
                        shift.setInterpolator(FRICTION_DECEL);
                        shift.play();
                    }
                }
            });
            fadeOut.play();
        });
        hold.play();
    }

    private void clearTrackedTrumps() {
        for (Animation anim : new ArrayList<>(activeTrumpAnimations)) {
            anim.stop();
        }
        activeTrumpAnimations.clear();
        activeTrumpNodes.clear();
        seenTrumpIds.clear();
        expiredInstantTrumpIds.clear();
        scheduledExpirationIds.clear();
        if (continuousTrumpsRow != null) {
            continuousTrumpsRow.getChildren().clear();
        }
        if (trumpHoverBanner != null) {
            trumpHoverBanner.setVisible(false);
            trumpHoverBanner.setText("");
        }
    }

    private ImageView makeDealerCardNode(Image img, boolean isEnemy, boolean animateDeal, double delayMs) {
        ImageView iv = makeCardNode(img);
        if (!animateDeal) {
            return iv;
        }
        double startX = Math.max(580.0, getWidth() > 0 ? getWidth() * 0.72 + 180.0 : 640.0);
        double startY = isEnemy ? 42.0 : -42.0;
        double startAngle = isEnemy ? 14.0 : -14.0;
        iv.setTranslateX(startX);
        iv.setTranslateY(startY);
        iv.setRotate(startAngle);
        iv.setOpacity(0.85);

        TranslateTransition slide = new TranslateTransition(Duration.millis(580), iv);
        slide.setDelay(Duration.millis(delayMs));
        slide.setFromX(startX);
        slide.setFromY(startY);
        slide.setToX(0.0);
        slide.setToY(0.0);
        slide.setInterpolator(FRICTION_DECEL);

        RotateTransition rot = new RotateTransition(Duration.millis(580), iv);
        rot.setDelay(Duration.millis(delayMs));
        rot.setFromAngle(startAngle);
        rot.setToAngle(0.0);
        rot.setInterpolator(FRICTION_DECEL);

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
        iv.setFitHeight(CARD_BASE_HEIGHT);
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
            Image chipImg = toPlayer ? AssetManager.getEnemyChipImage(3) : AssetManager.getPlayerChipImage(3);
            for (int i = 0; i < visualCoins; i++) {
                ImageView flyingChip = new ImageView(chipImg);
                flyingChip.setSmooth(false);
                flyingChip.setPreserveRatio(true);
                flyingChip.setFitWidth(40);
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
        addRowExitAnimation(exit, enemyCardsRow, -95.0);
        addRowExitAnimation(exit, continuousTrumpsRow, 0.0);
        addRowExitAnimation(exit, myCardsRow, 95.0);
        cardsExitAnim = exit;
        exit.setOnFinished(_ -> {
            cardsExitAnim = null;
            if (onFinished != null) {
                onFinished.run();
            }
        });
        exit.play();
    }

    private void addRowExitAnimation(ParallelTransition exit, javafx.scene.Node row, double targetY) {
        TranslateTransition move = new TranslateTransition(Duration.millis(380), row);
        move.setFromY(0.0);
        move.setToY(targetY);
        move.setInterpolator(Interpolator.EASE_IN);

        FadeTransition fade = new FadeTransition(Duration.millis(380), row);
        fade.setFromValue(row.getOpacity());
        fade.setToValue(0.0);
        exit.getChildren().addAll(move, fade);
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

    private void startDynamicRoundTransition(GameStateSnapshot s) {
        stopRoundCountdown();
        double rawDelaySec = 6.5 - (s.remainingPoolSize / 2.0);
        double totalDelaySec = Math.max(1.0, rawDelaySec);
        double pauseBeforeExitSec = Math.max(0.6, totalDelaySec - 0.38);

        PauseTransition pause = new PauseTransition(Duration.seconds(pauseBeforeExitSec));
        pause.setOnFinished(_ -> {
            roundTransitionPause = null;
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
        });
        roundTransitionPause = pause;
        pause.play();
    }

    private void stopRoundCountdown() {
        if (roundTransitionPause != null) {
            roundTransitionPause.stop();
            roundTransitionPause = null;
        }
    }

    private void updateOverTargetWarning(boolean show) {
        if (overTargetBox == null) return;
        overTargetBox.setVisible(show);
        overTargetBox.setManaged(show);
        if (show) {
            if (overTargetFlashTimeline != null && overTargetFlashTimeline.getStatus() != Animation.Status.RUNNING) {
                overTargetFlashTimeline.play();
            }
        } else {
            if (overTargetFlashTimeline != null) {
                overTargetFlashTimeline.stop();
            }
        }
    }

    private void updateTurnTimerForSnapshot(GameStateSnapshot s, boolean isNewRound, boolean isRematchReset) {
        if (turnTimerBox == null || s == null) return;
        if (s.turnTimeLimitSeconds <= 0 || s.roundOver || s.gameOver) {
            stopTurnTimer();
            turnTimerBox.setVisible(false);
            turnTimerBox.setManaged(false);
            lastTurnState = s.myTurn;
            lastTurnActionStamp = -1;
            return;
        }

        int limitSeconds = Math.max(30, s.turnTimeLimitSeconds);
        turnTimerBox.setVisible(true);
        turnTimerBox.setManaged(true);

        boolean resetTimer = isNewRound || isRematchReset
                || lastTurnActionStamp < 0
                || s.myTurn != lastTurnState;
        lastTurnState = s.myTurn;
        lastTurnActionStamp = 1;

        if (resetTimer || turnTimerTimeline == null) {
            turnSecondsLeft = limitSeconds;
            refreshTurnTimerVisual();
            startTurnTimer(limitSeconds);
        }
    }

    private void startTurnTimer(int limitSeconds) {
        stopTurnTimer();
        Timeline tl = new Timeline(new KeyFrame(Duration.seconds(1), _ -> {
            GameStateSnapshot snap = this.currentSnapshot;
            if (getScene() == null || snap == null || snap.turnTimeLimitSeconds <= 0 || snap.roundOver || snap.gameOver) {
                stopTurnTimer();
                return;
            }
            if (turnSecondsLeft > 0) {
                turnSecondsLeft--;
                refreshTurnTimerVisual();
            }
            if (turnSecondsLeft <= 0) {
                stopTurnTimer();
                if (snap.myTurn && !awaitingMoveAck) {
                    awaitingMoveAck = true;
                    p2pManager.performLocalTimeout();
                }
            }
        }));
        tl.setCycleCount(limitSeconds + 1);
        turnTimerTimeline = tl;
        tl.play();
    }

    private void refreshTurnTimerVisual() {
        if (turnTimerLabel == null || turnTimerBox == null) return;
        turnTimerLabel.setText("TIEMPO: " + Math.max(0, turnSecondsLeft) + "s");
        if (turnSecondsLeft <= 10) {
            turnTimerBox.setStyle("-fx-background-color: #101010; -fx-border-color: #101010; -fx-border-width: 2;");
            turnTimerLabel.setStyle("-fx-text-fill: #FFFFFF;");
        } else {
            turnTimerBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");
            turnTimerLabel.setStyle("-fx-text-fill: #101010;");
        }
    }

    private void stopTurnTimer() {
        if (turnTimerTimeline != null) {
            turnTimerTimeline.stop();
            turnTimerTimeline = null;
        }
    }

    private void updateMyCardsTurnVisual(boolean activeVisual) {
        if (myCardsRow == null || myCardsColorAdjust == null) return;
        double targetScale = activeVisual ? 1.0 : 0.86;
        double targetOpacity = activeVisual ? 1.0 : 0.78;
        double targetSat = activeVisual ? 0.0 : -0.85;
        double targetBright = activeVisual ? 0.0 : -0.08;

        if (lastMyCardsActiveVisual != null && lastMyCardsActiveVisual == activeVisual) {
            return;
        }
        boolean animateTransition = (lastMyCardsActiveVisual != null);
        lastMyCardsActiveVisual = activeVisual;

        if (myCardsTurnAnim != null) {
            myCardsTurnAnim.stop();
            myCardsTurnAnim = null;
        }

        if (!animateTransition) {
            myCardsRow.setScaleX(targetScale);
            myCardsRow.setScaleY(targetScale);
            myCardsRow.setOpacity(targetOpacity);
            myCardsColorAdjust.setSaturation(targetSat);
            myCardsColorAdjust.setBrightness(targetBright);
            return;
        }

        Timeline anim = new Timeline(new KeyFrame(
                Duration.millis(280),
                new KeyValue(myCardsRow.scaleXProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(myCardsRow.scaleYProperty(), targetScale, Interpolator.EASE_BOTH),
                new KeyValue(myCardsRow.opacityProperty(), targetOpacity, Interpolator.EASE_BOTH),
                new KeyValue(myCardsColorAdjust.saturationProperty(), targetSat, Interpolator.EASE_BOTH),
                new KeyValue(myCardsColorAdjust.brightnessProperty(), targetBright, Interpolator.EASE_BOTH)
        ));
        myCardsTurnAnim = anim;
        anim.play();
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
        stopTurnTimer();
        if (myCardsTurnAnim != null) {
            myCardsTurnAnim.stop();
            myCardsTurnAnim = null;
        }
        if (overTargetFlashTimeline != null) {
            overTargetFlashTimeline.stop();
        }
        for (Animation anim : new ArrayList<>(activeTrumpAnimations)) {
            anim.stop();
        }
        activeTrumpAnimations.clear();
        stopGameOverCountdown();
        stopCardsExitAnim();
        stopCursorWords();
    }

    private ImageView makeFlipRevealCardNode(int revealedNumber) {
        ImageView iv = new ImageView(AssetManager.getEnemyHiddenCard(2));
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        iv.setFitHeight(CARD_BASE_HEIGHT);

        ScaleTransition shrink = new ScaleTransition(Duration.millis(150), iv);
        shrink.setFromX(1.0);
        shrink.setToX(0.0);
        shrink.setOnFinished(_ -> iv.setImage(numberCard.isSkullCard(revealedNumber)
                ? AssetManager.getSkullCard(2)
                : AssetManager.getPlayerHiddenNumberCard(revealedNumber, 2)));

        ScaleTransition expand = new ScaleTransition(Duration.millis(150), iv);
        expand.setFromX(0.0);
        expand.setToX(1.0);

        new SequentialTransition(shrink, expand).play();
        return iv;
    }

    private void renderChipRow(FlowPane pane, int count, Image chipImg) {
        pane.getChildren().clear();
        pane.setHgap(8);
        pane.setVgap(8);
        int maxVisual = 24; // up to 6 stacks of 4 chips
        int visualCount = Math.min(count, maxVisual);
        int idx = 0;
        while (idx < visualCount) {
            int stackSize = Math.min(4, visualCount - idx);
            javafx.scene.layout.Pane stackPane = new javafx.scene.layout.Pane();
            stackPane.setPrefSize(40, 62);
            stackPane.setMinSize(40, 62);
            stackPane.setMaxSize(40, 62);
            for (int k = 0; k < stackSize; k++) {
                ImageView chipIv = new ImageView(chipImg);
                chipIv.setSmooth(false);
                chipIv.setPreserveRatio(true);
                chipIv.setFitWidth(40);
                chipIv.setLayoutX(0);
                chipIv.setLayoutY(19.0 - k * 6.0);
                stackPane.getChildren().add(chipIv);
            }
            pane.getChildren().add(stackPane);
            idx += stackSize;
        }
        if (count > maxVisual) {
            pane.getChildren().add(PixelUI.label("+" + (count - maxVisual), 12, true));
        }
    }

    private StackPane makeDroppedTrumpNode(GameStateSnapshot.PlayedTrumpView pt, int index, int totalPlayed, boolean animateDrop) {
        StackPane cardFrame = new StackPane();
        cardFrame.setPadding(Insets.EMPTY);
        cardFrame.setStyle("-fx-background-color: transparent; -fx-border-color: transparent;");
        cardFrame.setCursor(Cursor.HAND);

        if ("CONTINUOUS".equals(pt.type) && !pt.activeContinuous) {
            cardFrame.setOpacity(0.42);
        }

        double fitH = (totalPlayed > 5)
                ? Math.max(62.0, CARD_BASE_HEIGHT * (5.0 / totalPlayed))
                : CARD_BASE_HEIGHT;

        ImageView iv = new ImageView(AssetManager.getTrumpCardImage(pt.name, 2));
        iv.setSmooth(false);
        iv.setPreserveRatio(true);
        iv.setFitHeight(fitH);
        cardFrame.getChildren().add(iv);

        double restAngle = ((index % 3) - 1) * 3.5;
        cardFrame.setRotate(restAngle);

        if (animateDrop) {
            double startX = pt.mine ? -30.0 : 30.0;
            double startY = pt.mine ? 150.0 : -150.0;
            double startAngle = pt.mine ? -18.0 : 18.0;
            double apexY = pt.mine ? -16.0 : 16.0;
            double apexAngle = pt.mine ? 6.0 : -6.0;

            cardFrame.setTranslateX(startX);
            cardFrame.setTranslateY(startY);
            cardFrame.setScaleX(0.85);
            cardFrame.setScaleY(0.85);
            cardFrame.setRotate(startAngle);

            TranslateTransition liftMove = new TranslateTransition(Duration.millis(220), cardFrame);
            liftMove.setFromX(startX);
            liftMove.setFromY(startY);
            liftMove.setToX(0.0);
            liftMove.setToY(apexY);
            liftMove.setInterpolator(Interpolator.EASE_OUT);

            ScaleTransition liftScale = new ScaleTransition(Duration.millis(220), cardFrame);
            liftScale.setFromX(0.85);
            liftScale.setFromY(0.85);
            liftScale.setToX(1.45);
            liftScale.setToY(1.45);
            liftScale.setInterpolator(Interpolator.EASE_OUT);

            RotateTransition liftRot = new RotateTransition(Duration.millis(220), cardFrame);
            liftRot.setFromAngle(startAngle);
            liftRot.setToAngle(apexAngle);
            liftRot.setInterpolator(Interpolator.EASE_OUT);

            ParallelTransition grabLift = new ParallelTransition(liftMove, liftScale, liftRot);

            TranslateTransition slamMove = new TranslateTransition(Duration.millis(185), cardFrame);
            slamMove.setFromX(0.0);
            slamMove.setFromY(apexY);
            slamMove.setToX(0.0);
            slamMove.setToY(0.0);
            slamMove.setInterpolator(Interpolator.EASE_IN);

            ScaleTransition slamScale = new ScaleTransition(Duration.millis(185), cardFrame);
            slamScale.setFromX(1.45);
            slamScale.setFromY(1.45);
            slamScale.setToX(0.95);
            slamScale.setToY(0.95);
            slamScale.setInterpolator(Interpolator.EASE_IN);

            RotateTransition slamRot = new RotateTransition(Duration.millis(185), cardFrame);
            slamRot.setFromAngle(apexAngle);
            slamRot.setToAngle(restAngle);
            slamRot.setInterpolator(Interpolator.EASE_IN);

            ParallelTransition slam = new ParallelTransition(slamMove, slamScale, slamRot);
            slam.setOnFinished(_ -> {
                spawnTrumpImpactParticles(cardFrame, pt.mine);
                ScaleTransition settle = new ScaleTransition(Duration.millis(85), cardFrame);
                settle.setFromX(0.95);
                settle.setFromY(0.95);
                settle.setToX(1.0);
                settle.setToY(1.0);
                settle.setInterpolator(Interpolator.EASE_OUT);
                settle.play();
            });

            new SequentialTransition(grabLift, slam).play();
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

    private void spawnTrumpImpactParticles(Node cardNode, boolean isMine) {
        Platform.runLater(() -> {
            if (getScene() == null || cardNode.getScene() == null) return;
            Bounds sceneBounds = cardNode.localToScene(cardNode.getBoundsInLocal());
            if (sceneBounds == null) return;
            Point2D center = sceneToLocal(
                    sceneBounds.getMinX() + sceneBounds.getWidth() * 0.5,
                    sceneBounds.getMinY() + sceneBounds.getHeight() * 0.5
            );
            if (center == null) return;

            Color accentColor = isMine ? Color.rgb(25, 73, 138) : Color.rgb(184, 37, 53);
            Color darkColor = Color.rgb(16, 16, 16);
            int count = 12;
            for (int i = 0; i < count; i++) {
                double size = (i % 2 == 0) ? 4.0 : 3.0;
                Rectangle particle = new Rectangle(size, size, (i % 2 == 0) ? darkColor : accentColor);
                particle.setManaged(false);
                particle.setMouseTransparent(true);
                particle.setLayoutX(center.getX() - size * 0.5);
                particle.setLayoutY(center.getY() - size * 0.5);
                getChildren().add(particle);

                double angle = (2.0 * Math.PI * i / count) + (random.nextDouble() - 0.5) * 0.35;
                double dist = 20.0 + random.nextDouble() * 26.0;
                double dx = Math.cos(angle) * dist;
                double dy = Math.sin(angle) * dist;

                TranslateTransition burst = new TranslateTransition(Duration.millis(340), particle);
                burst.setFromX(0.0);
                burst.setFromY(0.0);
                burst.setToX(dx);
                burst.setToY(dy);
                burst.setInterpolator(FRICTION_DECEL);

                ScaleTransition shrink = new ScaleTransition(Duration.millis(340), particle);
                shrink.setFromX(1.0);
                shrink.setFromY(1.0);
                shrink.setToX(0.2);
                shrink.setToY(0.2);
                shrink.setInterpolator(FRICTION_DECEL);

                FadeTransition fade = new FadeTransition(Duration.millis(340), particle);
                fade.setFromValue(1.0);
                fade.setToValue(0.0);

                ParallelTransition pt = new ParallelTransition(burst, shrink, fade);
                pt.setOnFinished(_ -> getChildren().remove(particle));
                pt.play();
            }
        });
    }

    private void renderTrumpDrawer(GameStateSnapshot s, boolean canAct) {
        trumpCardsContainer.getChildren().clear();
        if (s.myTrumps.isEmpty()) {
            trumpCardsContainer.getChildren().add(PixelUI.label("No tienes comodines disponibles.", 12, false));
            return;
        }
        int count = s.myTrumps.size();
        double spacing = count <= 6 ? 10.0 : Math.max(3.0, 10.0 - (count - 6));
        trumpCardsContainer.setSpacing(spacing);
        double availableWidth = 716.0 - Math.max(0, count - 1) * spacing;
        double cardWidth = Math.min(104.0, Math.max(38.0, Math.floor(availableWidth / count)));
        double imgHeight = Math.min(76.0, Math.max(30.0, cardWidth * 0.74));
        double fontSize = Math.max(7.5, Math.min(10.5, cardWidth * 0.11));
        double pad = count > 8 ? 3.0 : 5.0;

        for (GameStateSnapshot.TrumpView tv : s.myTrumps) {
            VBox cardBox = new VBox(3);
            cardBox.setAlignment(Pos.CENTER);
            cardBox.setPadding(new Insets(pad));
            cardBox.setMinWidth(cardWidth);
            cardBox.setPrefWidth(cardWidth);
            cardBox.setMaxWidth(cardWidth);
            cardBox.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");
            cardBox.setCursor(canAct ? Cursor.HAND : Cursor.DEFAULT);
            cardBox.setOpacity(canAct ? 1.0 : 0.55);

            ImageView iv = new ImageView(AssetManager.getTrumpCardImage(tv.name, 2));
            iv.setSmooth(false);
            iv.setFitHeight(imgHeight);
            iv.setPreserveRatio(true);

            Label nameLbl = PixelUI.label(tv.name + " x" + tv.count, fontSize, true);
            nameLbl.setMaxWidth(cardWidth - 6);
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

        VBox box = new VBox(14);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(340);
        box.setMaxHeight(310);
        box.setPadding(new Insets(26));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label("PAUSA", 22, true);
        Label volumeLbl = PixelUI.label("VOLUMEN DE MUSICA:", 12.5, true);
        PixelUI.PixelSlider volumeSlider = new PixelUI.PixelSlider(app.getVolume(), app::setVolume);

        Button continueBtn = PixelUI.invertedButton("CONTINUAR", 14, 190, 40);
        Button surrenderBtn = PixelUI.pixelButton("RENDIRSE", 14, 190, 40);

        continueBtn.setOnAction(_ -> closePauseModal());
        surrenderBtn.setOnAction(_ -> {
            closePauseModal();
            p2pManager.performLocalSurrender();
        });

        box.getChildren().addAll(title, volumeLbl, volumeSlider, continueBtn, surrenderBtn);
        pauseOverlay.getChildren().add(box);
        getChildren().add(pauseOverlay);
    }

    private void closePauseModal() {
        if (pauseOverlay != null) {
            getChildren().remove(pauseOverlay);
            pauseOverlay = null;
        }
    }

    private void openRulesModal() {
        if (rulesOverlay != null) return;

        rulesOverlay = new StackPane();
        rulesOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.85);");

        VBox box = new VBox(12);
        box.setAlignment(Pos.TOP_LEFT);
        box.setMaxWidth(580);
        box.setMaxHeight(520);
        box.setPadding(new Insets(22));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label("REGLAS DEL JUEGO", 20, true);
        String rulesText =
                "• OBJETIVO: Acercate a 21 (o a la meta actual) sin pasarte. Si ambos se pasan, gana el mas cercano.\n\n"
                + "• TURNO: En tu turno haz TRIPLE CLICK IZQUIERDO en el vacio para COMER CARTA, "
                + "TRIPLE CLICK DERECHO para SALTAR / PLANTARSE, o juega cartas Trump sin limite (no reinician el reloj).\n\n"
                + "• LIMITE DE TIEMPO: Si el reloj llega a 0 sin que juegues, recibiras la CALAVERA (∞), "
                + "causando derrota inevitable en la ronda.\n\n"
                + "• TIPO DE APUESTA:\n"
                + "   - Normal (+0): El ganador se queda con las fichas apostadas.\n"
                + "   - Deficit (+6): Nadie se queda con las fichas apostadas.\n"
                + "   - Merma (+2): Desde la ronda 2, el ganador pierde 1 ficha de la apuesta ganada.\n\n"
                + "• CANTIDAD DE APUESTAS:\n"
                + "   - Unica: Ambos jugadores juegan con la misma apuesta; ambos pueden aumentarla y bajarla.\n"
                + "   - Individual: Cada jugador juega con su propia apuesta; solo aumentan la del oponente y disminuyen su propia.\n\n"
                + "• FIN DE RONDA: Cuando ambos saltan (o tras la Calavera al actuar el rival) se revelan las cartas. "
                + "Gana quien deje al oponente con 0 fichas.";

        Label bodyLbl = PixelUI.label(rulesText, 11.2, false);
        bodyLbl.setWrapText(true);
        bodyLbl.setMaxWidth(510);

        ScrollPane scroll = new ScrollPane(bodyLbl);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(370);
        scroll.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 2;");

        Button closeBtn = PixelUI.invertedButton("ENTENDIDO", 13, 170, 38);
        closeBtn.setOnAction(_ -> closeRulesModal());
        HBox btnRow = new HBox(closeBtn);
        btnRow.setAlignment(Pos.CENTER);

        box.getChildren().addAll(title, scroll, btnRow);
        rulesOverlay.getChildren().add(box);
        getChildren().add(rulesOverlay);
    }

    private void closeRulesModal() {
        if (rulesOverlay != null) {
            getChildren().remove(rulesOverlay);
            rulesOverlay = null;
        }
    }

    private void setupVoidInteractionHandlers() {
        addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        });
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();
            if (isInteractiveTarget(e.getTarget())) {
                return;
            }
            GameStateSnapshot snap = this.currentSnapshot;
            boolean canAct = snap != null && snap.myTurn && !snap.roundOver && !snap.gameOver;
            boolean isBusted = snap != null && (snap.hasMySkullCard() || snap.getMyTotalSum() > snap.targetScore);
            long now = System.currentTimeMillis();

            if (e.getButton() == MouseButton.PRIMARY) {
                voidRightClicks = 0;
                if (!canAct || isBusted) {
                    voidLeftClicks = 0;
                    return;
                }
                if (now - lastVoidLeftClickMs > VOID_TRIPLE_CLICK_WINDOW_MS) {
                    voidLeftClicks = 1;
                } else {
                    voidLeftClicks++;
                }
                lastVoidLeftClickMs = now;
                String word = DRAW_WORDS[random.nextInt(DRAW_WORDS.length)];
                spawnCursorFollowerWord(word, e.getX(), e.getY(), "#19498A");
                if (voidLeftClicks >= 3) {
                    voidLeftClicks = 0;
                    if (!awaitingMoveAck) {
                        awaitingMoveAck = true;
                        p2pManager.performLocalDraw();
                    }
                }
            } else if (e.getButton() == MouseButton.SECONDARY) {
                voidLeftClicks = 0;
                if (!canAct) {
                    voidRightClicks = 0;
                    return;
                }
                if (now - lastVoidRightClickMs > VOID_TRIPLE_CLICK_WINDOW_MS) {
                    voidRightClicks = 1;
                } else {
                    voidRightClicks++;
                }
                lastVoidRightClickMs = now;
                String word = STAND_WORDS[random.nextInt(STAND_WORDS.length)];
                spawnCursorFollowerWord(word, e.getX(), e.getY(), "#101010");
                if (voidRightClicks >= 3) {
                    voidRightClicks = 0;
                    if (!awaitingMoveAck) {
                        awaitingMoveAck = true;
                        p2pManager.performLocalStand();
                    }
                }
            }
        });
    }

    private boolean isInteractiveTarget(EventTarget target) {
        if (pauseOverlay != null || rulesOverlay != null || disconnectOverlay != null || gameOverOverlay != null) {
            return true;
        }
        if (!(target instanceof Node cur)) {
            return false;
        }
        while (cur != null && cur != this) {
            if (cur == pauseIconBtn || cur == trumpDrawerZone) {
                return true;
            }
            if (cur instanceof Button || cur.getCursor() == Cursor.HAND) {
                return true;
            }
            Parent p = cur.getParent();
            cur = p;
        }
        return false;
    }

    private void spawnCursorFollowerWord(String text, double startX, double startY, String colorHex) {
        Label wordLbl = PixelUI.label(text, 12.0, true);
        wordLbl.setStyle("-fx-text-fill: " + colorHex + ";");
        wordLbl.setManaged(false);
        wordLbl.setMouseTransparent(true);

        double offsetX = -26.0 + random.nextDouble() * 52.0;
        double offsetY = -26.0 - random.nextDouble() * 14.0;
        double initX = startX + offsetX;
        double initY = startY + offsetY;

        getChildren().add(wordLbl);
        wordLbl.applyCss();
        wordLbl.autosize();
        wordLbl.relocate(initX, initY);

        activeCursorWords.add(new CursorWordTracker(wordLbl, System.nanoTime(), initX, initY, offsetX, offsetY));
        startCursorWordsLoop();
    }

    private void startCursorWordsLoop() {
        if (cursorWordsTimer != null) {
            return;
        }
        cursorWordsTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (activeCursorWords.isEmpty()) {
                    stop();
                    cursorWordsTimer = null;
                    return;
                }
                List<CursorWordTracker> finished = null;
                for (CursorWordTracker w : activeCursorWords) {
                    double t = (now - w.birthNs) / 850_000_000.0;
                    if (t >= 1.0) {
                        if (finished == null) finished = new ArrayList<>();
                        finished.add(w);
                        continue;
                    }
                    double targetX = lastMouseX + w.offsetX;
                    double targetY = lastMouseY + w.offsetY - t * 28.0;
                    w.currentX += (targetX - w.currentX) * 0.22;
                    w.currentY += (targetY - w.currentY) * 0.22;
                    w.label.relocate(w.currentX, w.currentY);
                    double alpha = (t < 0.2) ? 1.0 : Math.max(0.0, 1.0 - (t - 0.2) / 0.8);
                    w.label.setOpacity(alpha);
                }
                if (finished != null) {
                    for (CursorWordTracker w : finished) {
                        getChildren().remove(w.label);
                        activeCursorWords.remove(w);
                    }
                }
                if (activeCursorWords.isEmpty()) {
                    stop();
                    cursorWordsTimer = null;
                }
            }
        };
        cursorWordsTimer.start();
    }

    private void stopCursorWords() {
        if (cursorWordsTimer != null) {
            cursorWordsTimer.stop();
            cursorWordsTimer = null;
        }
        for (CursorWordTracker w : activeCursorWords) {
            getChildren().remove(w.label);
        }
        activeCursorWords.clear();
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
