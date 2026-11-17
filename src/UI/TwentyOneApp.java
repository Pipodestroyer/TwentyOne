package UI;

import Net.LanDiscoveryService;
import Net.P2PManager;
import Net.RailwayRelayServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class TwentyOneApp extends Application {
    public static final double BASE_WIDTH = 1080.0;
    public static final double BASE_HEIGHT = 680.0;
    public static final String VERSION_TEXT = "v0.1.0";
    public static final String DEFAULT_RAILWAY_URL = "https://twentyone-production.up.railway.app";
    private static final Path SETTINGS_FILE = Path.of(System.getProperty("user.home"), ".twentyone_settings.properties");

    private Stage primaryStage;
    private StackPane rootContainer;
    private Scale viewportScale;

    private String playerName = "Jugador";
    private int defaultPort = 7777;
    private boolean borderless = false;
    private String railwayServerUrl = resolveDefaultRailwayUrl();

    private final LanDiscoveryService discoveryService = new LanDiscoveryService();
    private final P2PManager p2pManager = new P2PManager();
    private RailwayRelayServer localRelayServer;

    public TwentyOneApp() {
        loadPersistentSettings();
    }

    private void loadPersistentSettings() {
        if (!Files.isRegularFile(SETTINGS_FILE)) {
            return;
        }
        Properties props = new Properties();
        try (Reader r = Files.newBufferedReader(SETTINGS_FILE, StandardCharsets.UTF_8)) {
            props.load(r);
            String savedName = props.getProperty("playerName");
            if (savedName != null && !savedName.isBlank()) {
                this.playerName = savedName.trim();
            }
            String savedVol = props.getProperty("volume");
            if (savedVol != null && !savedVol.isBlank()) {
                try {
                    SoundManager.setVolume(Double.parseDouble(savedVol));
                } catch (NumberFormatException _) {
                    // Ignore malformed volume
                }
            }
        } catch (IOException _) {
            // Ignore unreadable settings file
        }
    }

    private void savePersistentSettings() {
        Properties props = new Properties();
        props.setProperty("playerName", playerName != null ? playerName : "Jugador");
        props.setProperty("volume", String.valueOf(SoundManager.getVolume()));
        try (Writer w = Files.newBufferedWriter(SETTINGS_FILE, StandardCharsets.UTF_8)) {
            props.store(w, "TwentyOne Player Settings");
        } catch (IOException _) {
            // Ignore persistence failure on read-only environments
        }
    }

    private static String resolveDefaultRailwayUrl() {
        String env = System.getenv("TWENTYONE_SERVER_URL");
        return (env != null && !env.isBlank()) ? env.trim() : DEFAULT_RAILWAY_URL;
    }

    public Pane createScaledRoot() {
        if (rootContainer == null) {
            rootContainer = new StackPane();
            rootContainer.setStyle("-fx-background-color: #FFFFFF;");
            viewportScale = new Scale(1.0, 1.0, 0.0, 0.0);
            rootContainer.getTransforms().add(viewportScale);
        }

        Pane rootWrapper = new Pane(rootContainer) {
            @Override
            protected void layoutChildren() {
                double w = getWidth();
                double h = getHeight();
                if (w <= 0 || h <= 0) return;
                double scale = Math.min(w / BASE_WIDTH, h / BASE_HEIGHT);
                if (scale <= 0) scale = 1.0;
                double logicalW = w / scale;
                double logicalH = h / scale;
                rootContainer.resizeRelocate(0, 0, logicalW, logicalH);
                viewportScale.setX(scale);
                viewportScale.setY(scale);
            }
        };
        rootWrapper.setStyle("-fx-background-color: #FFFFFF;");
        return rootWrapper;
    }

    public Scale getViewportScale() {
        return viewportScale;
    }

    public StackPane getRootContainer() {
        return rootContainer;
    }

    @Override
    public void start(Stage stage) {
        Platform.setImplicitExit(false);
        this.primaryStage = stage;
        if (borderless) {
            stage.initStyle(StageStyle.UNDECORATED);
        }

        double[] size = computeDefaultWindowSize();
        Scene scene = new Scene(createScaledRoot(), size[0], size[1]);
        attachGlobalShortcuts(scene);
        configureStage(stage, scene, borderless);

        showMainMenu();
        stage.show();
    }

    public void attachGlobalShortcuts(Scene scene) {
        if (scene == null) return;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F11) {
                setBorderless(!isBorderless());
                e.consume();
            }
        });
    }

    private double[] computeDefaultWindowSize() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double aspect = BASE_WIDTH / BASE_HEIGHT;
        double targetH = Math.min(bounds.getHeight() * 0.84, bounds.getHeight() * 0.88);
        double targetW = targetH * aspect;
        if (targetW > bounds.getWidth() * 0.90) {
            targetW = bounds.getWidth() * 0.90;
            targetH = targetW / aspect;
        }
        return new double[]{targetW, targetH};
    }

    private void configureStage(Stage stage, Scene scene, boolean borderlessMode) {
        stage.setTitle("21 - TwentyOne");
        stage.setMinWidth(720);
        stage.setMinHeight(460);
        stage.setScene(scene);

        if (borderlessMode) {
            Rectangle2D screenBounds = Screen.getPrimary().getBounds();
            stage.setX(screenBounds.getMinX());
            stage.setY(screenBounds.getMinY());
            stage.setWidth(screenBounds.getWidth());
            stage.setHeight(screenBounds.getHeight());
        } else {
            double[] size = computeDefaultWindowSize();
            stage.setWidth(size[0]);
            stage.setHeight(size[1]);
            stage.centerOnScreen();
        }

        stage.setOnCloseRequest(_ -> {
            shutdownNetworking();
            stopLocalRelay();
            Platform.exit();
        });
    }

    public synchronized void ensureLocalRelayIfLocalhost() {
        String httpUrl = P2PManager.normalizeHttpUrl(railwayServerUrl);
        if (!httpUrl.startsWith("http://127.0.0.1:8080") && !httpUrl.startsWith("http://localhost:8080")) {
            return;
        }
        if (localRelayServer != null && localRelayServer.isRunning()) {
            return;
        }
        try {
            RailwayRelayServer srv = new RailwayRelayServer();
            srv.start(8080);
            localRelayServer = srv;
        } catch (IOException _) {
            // Port 8080 may already be running an external local relay instance
        }
    }

    public synchronized void stopLocalRelay() {
        if (localRelayServer != null) {
            localRelayServer.stop();
            localRelayServer = null;
        }
    }

    private Label createVersionWatermark() {
        Label wm = PixelUI.label(VERSION_TEXT, 11.5, true);
        wm.setStyle("-fx-text-fill: rgba(16, 16, 16, 0.48);");
        wm.setMouseTransparent(true);
        StackPane.setAlignment(wm, Pos.BOTTOM_LEFT);
        StackPane.setMargin(wm, new Insets(0, 0, 8, 12));
        return wm;
    }

    public void showMainMenu() {
        shutdownNetworking();
        SoundManager.playBgm();
        MainMenuView menu = new MainMenuView(this);
        rootContainer.getChildren().setAll(menu, createVersionWatermark());
    }

    public void showLobby() {
        shutdownNetworking();
        LobbyView lobby = new LobbyView(this, discoveryService, p2pManager);
        rootContainer.getChildren().setAll(lobby, createVersionWatermark());
    }

    public void showGameTable() {
        discoveryService.stop();
        GameTableView table = new GameTableView(this, p2pManager);
        rootContainer.getChildren().setAll(table, createVersionWatermark());
    }

    public void shutdownNetworking() {
        discoveryService.stop();
        p2pManager.close();
    }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            this.playerName = playerName.trim();
            savePersistentSettings();
        }
    }

    public int getDefaultPort() { return defaultPort; }
    public void setDefaultPort(int defaultPort) {
        if (defaultPort >= 1024 && defaultPort <= 65535) {
            this.defaultPort = defaultPort;
        }
    }

    public boolean isBorderless() { return borderless; }

    public void setBorderless(boolean borderless) {
        if (this.borderless == borderless) {
            return;
        }
        this.borderless = borderless;
        if (primaryStage != null && primaryStage.getScene() != null) {
            Stage oldStage = primaryStage;
            Scene currentScene = oldStage.getScene();
            oldStage.setOnCloseRequest(null);
            oldStage.setScene(null);

            Stage newStage = new Stage(borderless ? StageStyle.UNDECORATED : StageStyle.DECORATED);
            configureStage(newStage, currentScene, borderless);
            this.primaryStage = newStage;
            newStage.show();
            oldStage.hide();
        }
    }

    public String getRailwayServerUrl() { return railwayServerUrl; }

    public void setRailwayServerUrl(String railwayServerUrl) {
        if (railwayServerUrl != null && !railwayServerUrl.isBlank()) {
            this.railwayServerUrl = railwayServerUrl.trim();
        }
    }

    public Stage getPrimaryStage() { return primaryStage; }

    public double getVolume() {
        return SoundManager.getVolume();
    }

    public void setVolume(double volume) {
        SoundManager.setVolume(volume);
        savePersistentSettings();
    }
}
