package UI;

import Net.LanDiscoveryService;
import Net.P2PManager;
import Net.RailwayRelayServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class TwentyOneApp extends Application {
    public static final double BASE_WIDTH = 1080.0;
    public static final double BASE_HEIGHT = 680.0;

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

    private static String resolveDefaultRailwayUrl() {
        String env = System.getenv("TWENTYONE_SERVER_URL");
        return (env != null && !env.isBlank()) ? env.trim() : "ws://127.0.0.1:8080";
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
        configureStage(stage, scene, borderless);

        showMainMenu();
        stage.show();
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

    public void showMainMenu() {
        shutdownNetworking();
        MainMenuView menu = new MainMenuView(this);
        rootContainer.getChildren().setAll(menu);
    }

    public void showLobby() {
        shutdownNetworking();
        LobbyView lobby = new LobbyView(this, discoveryService, p2pManager);
        rootContainer.getChildren().setAll(lobby);
    }

    public void showGameTable() {
        discoveryService.stop();
        GameTableView table = new GameTableView(this, p2pManager);
        rootContainer.getChildren().setAll(table);
    }

    public void shutdownNetworking() {
        discoveryService.stop();
        p2pManager.close();
    }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) {
        if (playerName != null && !playerName.isBlank()) {
            this.playerName = playerName.trim();
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
}
