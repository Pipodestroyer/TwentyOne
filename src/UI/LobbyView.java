package UI;

import Game.config;
import Net.GameRules;
import Net.GameStateSnapshot;
import Net.LanDiscoveryService;
import Net.P2PManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LobbyView extends StackPane {
    private final TwentyOneApp app;
    private final LanDiscoveryService discoveryService;
    private final P2PManager p2pManager;
    private final Random random = new Random();

    private VBox hostsListContainer;
    private Label scanStatusLabel;
    private Label feedbackLabel;
    private StackPane waitingOverlay;
    private Label waitingCodeLabel;

    private ScheduledExecutorService onlinePoller;
    private volatile boolean scanningEnabled = true;
    private volatile List<P2PManager.PublicRoom> latestPublicRooms = Collections.emptyList();
    private volatile List<LanDiscoveryService.DiscoveredHost> latestLanHosts = Collections.emptyList();

    public LobbyView(TwentyOneApp app, LanDiscoveryService discoveryService, P2PManager p2pManager) {
        this.app = app;
        this.discoveryService = discoveryService;
        this.p2pManager = p2pManager;

        setStyle("-fx-background-color: #FFFFFF;");

        BorderPane layout = new BorderPane();
        layout.setPadding(new Insets(18, 28, 18, 28));

        // Top Bar
        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(0, 0, 12, 0));

        Button backBtn = PixelUI.pixelButton("< VOLVER", 13, 110, 34);
        backBtn.setOnAction(_ -> {
            stopAllScanning();
            p2pManager.close();
            app.showMainMenu();
        });

        Label titleLbl = PixelUI.label("MULTIJUGADOR (ONLINE + LAN)", 20, true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label playerLbl = PixelUI.label("JUGADOR: " + app.getPlayerName().toUpperCase(), 14, true);

        topBar.getChildren().addAll(backBtn, titleLbl, spacer, playerLbl);
        layout.setTop(topBar);

        // Left: Hostear partida (Railway por defecto + toggle LAN) | Right: Buscador de servers (Publicas + LAN)
        HBox columns = new HBox(24);
        columns.setAlignment(Pos.TOP_CENTER);

        VBox leftPanel = buildHostPanel();
        VBox rightPanel = buildJoinPanel();
        HBox.setHgrow(leftPanel, Priority.ALWAYS);
        HBox.setHgrow(rightPanel, Priority.ALWAYS);
        leftPanel.setMaxWidth(Double.MAX_VALUE);
        rightPanel.setMaxWidth(Double.MAX_VALUE);

        columns.getChildren().addAll(leftPanel, rightPanel);
        layout.setCenter(columns);

        // Bottom feedback
        feedbackLabel = PixelUI.label("", 13, false);
        feedbackLabel.setPadding(new Insets(8, 0, 0, 0));
        layout.setBottom(feedbackLabel);

        getChildren().add(layout);

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                stopAllScanning();
            }
        });

        startCombinedScan();
    }

    private VBox buildHostPanel() {
        VBox panel = new VBox(8);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");

        Label header = PixelUI.label("HOSTEAR PARTIDA (TUS REGLAS)", 15.5, true);

        // Mode toggles: Online by default, toggleable to LAN P2P
        PixelUI.PixelCheckBox lanModeCheck = pixelCheckBox("Modo LAN (Red Local P2P)", false);
        PixelUI.PixelCheckBox publicRoomCheck = pixelCheckBox("Partida Publica (visible en buscador)", true);

        // Judges
        Label judgeTitle = PixelUI.label("JUEZ (marca varios para aleatorio):", 12.5, true);
        PixelUI.PixelCheckBox jHost = pixelCheckBox("El Anfitrion (+0 fichas, ganador lleva todo)", true);
        PixelUI.PixelCheckBox jBank = pixelCheckBox("El Banco (+6 fichas, perdedor paga al juez)", false);
        PixelUI.PixelCheckBox jMafia = pixelCheckBox("La Mafia (+2 fichas, 1 ficha al juez desde R2)", false);

        // Bet Modes
        Label betTitle = PixelUI.label("APUESTAS (marca ambos para aleatorio):", 12.5, true);
        HBox betRow = new HBox(14);
        PixelUI.PixelCheckBox bSingle = pixelCheckBox("1 Apuesta (compartida)", true);
        PixelUI.PixelCheckBox bDouble = pixelCheckBox("2 Apuestas (individual)", false);
        betRow.getChildren().addAll(bSingle, bDouble);

        // Chips
        Label chipsTitle = PixelUI.label("FICHAS INICIALES (marca varias para aleatorio):", 12.5, true);
        HBox chipsRow = new HBox(16);
        PixelUI.PixelCheckBox c5 = pixelCheckBox("5", false);
        PixelUI.PixelCheckBox c7 = pixelCheckBox("7", true);
        PixelUI.PixelCheckBox c9 = pixelCheckBox("9", false);
        PixelUI.PixelCheckBox c11 = pixelCheckBox("11", false);
        chipsRow.getChildren().addAll(c5, c7, c9, c11);

        // Create button
        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER_RIGHT);
        actionRow.setPadding(new Insets(4, 0, 0, 0));
        Button createBtn = PixelUI.invertedButton("CREAR SALA", 14.5, 160, 40);

        Runnable syncModeUi = () -> {
            boolean isLan = lanModeCheck.isSelected();
            publicRoomCheck.setDisable(isLan);
            publicRoomCheck.setOpacity(isLan ? 0.45 : 1.0);
        };
        lanModeCheck.setOnChanged(syncModeUi);
        syncModeUi.run();

        createBtn.setOnAction(_ -> {
            List<Byte> judges = new ArrayList<>();
            if (jHost.isSelected()) judges.add(config.JUDGE_HOST);
            if (jBank.isSelected()) judges.add(config.JUDGE_BANK);
            if (jMafia.isSelected()) judges.add(config.JUDGE_MAFIA);

            List<Byte> betModes = new ArrayList<>();
            if (bSingle.isSelected()) betModes.add(config.BET_SINGLE);
            if (bDouble.isSelected()) betModes.add(config.BET_DOUBLE);

            List<Byte> chipsOptions = new ArrayList<>();
            if (c5.isSelected()) chipsOptions.add((byte) 5);
            if (c7.isSelected()) chipsOptions.add((byte) 7);
            if (c9.isSelected()) chipsOptions.add((byte) 9);
            if (c11.isSelected()) chipsOptions.add((byte) 11);

            if (judges.isEmpty() || betModes.isEmpty() || chipsOptions.isEmpty()) {
                feedbackLabel.setText("Selecciona al menos una opcion de Juez, Apuesta y Fichas.");
                return;
            }

            byte chosenJudge = judges.get(random.nextInt(judges.size()));
            byte chosenBet = betModes.get(random.nextInt(betModes.size()));
            byte chosenChips = chipsOptions.get(random.nextInt(chipsOptions.size()));
            GameRules rules = new GameRules(app.getPlayerName(), chosenJudge, chosenBet, chosenChips);

            if (lanModeCheck.isSelected()) {
                startHostingLanRoom(rules, app.getDefaultPort());
            } else {
                startHostingRailwayRoom(rules, publicRoomCheck.isSelected());
            }
        });

        Region vSpacer = new Region();
        VBox.setVgrow(vSpacer, Priority.ALWAYS);

        actionRow.getChildren().add(createBtn);
        panel.getChildren().addAll(
                header, lanModeCheck, publicRoomCheck,
                judgeTitle, jHost, jBank, jMafia,
                betTitle, betRow,
                chipsTitle, chipsRow,
                vSpacer,
                actionRow
        );
        return panel;
    }

    private VBox buildJoinPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(16));
        panel.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");

        Label header = PixelUI.label("BUSCADOR DE SERVERS (PUBLICAS + LAN)", 15.5, true);
        HBox scanHeaderRow = new HBox(10);
        scanHeaderRow.setAlignment(Pos.CENTER_LEFT);
        scanStatusLabel = PixelUI.label("Buscando partidas publicas y LAN...", 11.5, false);
        Region scanSpacer = new Region();
        HBox.setHgrow(scanSpacer, Priority.ALWAYS);
        PixelUI.PixelCheckBox autoScanCheck = new PixelUI.PixelCheckBox("Auto-escanear", true);
        autoScanCheck.setOnChanged(() -> {
            if (autoScanCheck.isSelected()) {
                startCombinedScan();
            } else {
                stopAllScanning();
                scanStatusLabel.setText("Escaneo pausado.");
            }
        });
        scanHeaderRow.getChildren().addAll(scanStatusLabel, scanSpacer, autoScanCheck);

        hostsListContainer = new VBox(8);
        hostsListContainer.setPadding(new Insets(4));

        ScrollPane scroll = new ScrollPane(hostsListContainer);
        scroll.setFitToWidth(true);
        scroll.setPrefHeight(220);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        scroll.setStyle("-fx-background: #FFFFFF; -fx-background-color: #FFFFFF; "
                + "-fx-border-color: #101010; -fx-border-width: 2;");

        // Join by 4-digit room code
        Label codeTitle = PixelUI.label("UNIRSE POR CODIGO (4 DIGITOS):", 12.5, true);
        HBox codeBox = new HBox(10);
        codeBox.setAlignment(Pos.CENTER_LEFT);
        TextField codeField = PixelUI.pixelField("", 120);
        codeField.setPromptText("Ej: 4821");
        Button joinCodeBtn = PixelUI.invertedButton("UNIRSE CODIGO", 12.5, 135, 34);
        joinCodeBtn.setOnAction(_ -> {
            String code = codeField.getText().trim();
            if (!code.matches("\\d{4}")) {
                feedbackLabel.setText("Ingresa un codigo valido de 4 digitos.");
                return;
            }
            connectToRailwayCode(code);
        });
        codeBox.getChildren().addAll(codeField, joinCodeBtn);

        // Direct LAN connect
        Label manualTitle = PixelUI.label("CONEXION DIRECTA LAN (IP):", 12.5, true);
        HBox manualBox = new HBox(10);
        manualBox.setAlignment(Pos.CENTER_LEFT);
        TextField ipField = PixelUI.pixelField("127.0.0.1", 190);
        Button connectBtn = PixelUI.pixelButton("CONECTAR LAN", 12.5, 135, 34);

        connectBtn.setOnAction(_ -> {
            String ip = ipField.getText().trim();
            if (ip.isEmpty()) {
                feedbackLabel.setText("Ingresa una IP valida.");
                return;
            }
            connectAsLanClient(ip, app.getDefaultPort());
        });

        manualBox.getChildren().addAll(ipField, connectBtn);
        panel.getChildren().addAll(header, scanHeaderRow, scroll, codeTitle, codeBox, manualTitle, manualBox);
        return panel;
    }

    private PixelUI.PixelCheckBox pixelCheckBox(String text, boolean selected) {
        return new PixelUI.PixelCheckBox(text, selected);
    }

    private synchronized void startCombinedScan() {
        stopOnlinePoller();
        scanningEnabled = true;

        discoveryService.startScanning(hosts -> {
            latestLanHosts = hosts;
            Platform.runLater(this::renderCombinedServers);
        });

        onlinePoller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "lobby-railway-poller");
            t.setDaemon(true);
            return t;
        });
        onlinePoller.scheduleAtFixedRate(() -> {
            if (!scanningEnabled) return;
            List<P2PManager.PublicRoom> rooms = P2PManager.fetchPublicRooms(app.getRailwayServerUrl());
            latestPublicRooms = rooms;
            Platform.runLater(this::renderCombinedServers);
        }, 0, 2, TimeUnit.SECONDS);
    }

    private synchronized void stopOnlinePoller() {
        if (onlinePoller != null) {
            onlinePoller.shutdownNow();
            onlinePoller = null;
        }
    }

    private synchronized void stopAllScanning() {
        scanningEnabled = false;
        discoveryService.stop();
        stopOnlinePoller();
    }

    private void renderCombinedServers() {
        if (!scanningEnabled) return;
        hostsListContainer.getChildren().clear();

        List<P2PManager.PublicRoom> onlineRooms = latestPublicRooms;
        List<LanDiscoveryService.DiscoveredHost> lanHosts = latestLanHosts;
        int total = onlineRooms.size() + lanHosts.size();

        scanStatusLabel.setText("Online: " + onlineRooms.size() + " | LAN: " + lanHosts.size());

        if (total == 0) {
            Label empty = PixelUI.label("No hay partidas publicas ni LAN activas.", 12, false);
            empty.setPadding(new Insets(12));
            hostsListContainer.getChildren().add(empty);
            return;
        }

        for (P2PManager.PublicRoom room : onlineRooms) {
            HBox card = makeServerCard(
                    "[ONLINE #" + room.code + "] " + room.hostName,
                    formatRules(room.idJudge, room.betMode, room.initialChips),
                    "#19498A",
                    () -> connectToRailwayCode(room.code)
            );
            hostsListContainer.getChildren().add(card);
        }

        for (LanDiscoveryService.DiscoveredHost host : lanHosts) {
            HBox card = makeServerCard(
                    "[LAN " + host.hostAddress + "] " + host.hostName,
                    formatRules(host.idJudge, host.betMode, host.initialChips),
                    "#101010",
                    () -> connectAsLanClient(host.hostAddress, host.tcpPort)
            );
            hostsListContainer.getChildren().add(card);
        }
    }

    private HBox makeServerCard(String title, String subtitle, String tagColorHex, Runnable onJoin) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(9));
        card.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 2;");

        VBox info = new VBox(3);
        Label nameLbl = PixelUI.label(title, 12.5, true);
        nameLbl.setStyle("-fx-text-fill: " + tagColorHex + ";");
        Label rulesLbl = PixelUI.label(subtitle, 11.5, false);
        info.getChildren().addAll(nameLbl, rulesLbl);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button joinBtn = PixelUI.invertedButton("UNIRSE", 12, 86, 30);
        joinBtn.setOnAction(_ -> onJoin.run());

        card.getChildren().addAll(info, spacer, joinBtn);
        return card;
    }

    private String formatRules(byte idJudge, byte betMode, byte initialChips) {
        String judgeStr = switch (idJudge) {
            case config.JUDGE_BANK -> "El Banco";
            case config.JUDGE_MAFIA -> "La Mafia";
            default -> "El Anfitrion";
        };
        String betStr = betMode == config.BET_DOUBLE ? "2 Apuestas" : "1 Apuesta";
        return "Juez: " + judgeStr + " | " + betStr + " | Fichas: " + initialChips;
    }

    private void connectToRailwayCode(String code) {
        feedbackLabel.setText("Conectando a sala #" + code + "...");
        stopAllScanning();
        app.ensureLocalRelayIfLocalhost();
        p2pManager.connectToRailwayRoom(app.getRailwayServerUrl(), code, app.getPlayerName(), createClientListener());
    }

    private void connectAsLanClient(String ip, int port) {
        feedbackLabel.setText("Conectando via LAN a " + ip + "...");
        stopAllScanning();
        p2pManager.connectToHost(ip, port, app.getPlayerName(), createClientListener());
    }

    private P2PManager.Listener createClientListener() {
        return new P2PManager.Listener() {
            @Override
            public void onConnected(String peerInfo) {
                Platform.runLater(app::showGameTable);
            }

            @Override
            public void onStateUpdated(GameStateSnapshot snapshot) {}

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    feedbackLabel.setText(reason);
                    startCombinedScan();
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    feedbackLabel.setText(message);
                    startCombinedScan();
                });
            }
        };
    }

    private void startHostingRailwayRoom(GameRules rules, boolean isPublic) {
        stopAllScanning();
        app.ensureLocalRelayIfLocalhost();
        feedbackLabel.setText("Creando sala online...");
        showWaitingOverlay(
                "SALA ONLINE - ESPERANDO OPONENTE",
                "CODIGO: ....",
                "Visibilidad: " + (isPublic ? "Publica en buscador" : "Privada (solo codigo)") + "\n"
                        + formatRules(rules.idJudge, rules.betMode, rules.initialChips)
        );
        p2pManager.startRailwayHost(
                app.getRailwayServerUrl(),
                rules,
                isPublic,
                code -> Platform.runLater(() -> {
                    if (waitingCodeLabel != null) {
                        waitingCodeLabel.setText("CODIGO: " + code);
                    }
                    feedbackLabel.setText("Sala online creada con codigo #" + code);
                }),
                createHostListener()
        );
    }

    private void startHostingLanRoom(GameRules rules, int port) {
        stopAllScanning();
        try {
            int boundPort = p2pManager.startHost(rules, port, createHostListener());
            discoveryService.startHostingBroadcast(rules, boundPort);
            showWaitingOverlay(
                    "SALA LAN CREADA - ESPERANDO OPONENTE",
                    null,
                    "IP Local: " + getLocalIpSummary() + "\n"
                            + formatRules(rules.idJudge, rules.betMode, rules.initialChips)
            );
        } catch (IOException e) {
            feedbackLabel.setText("No se pudo abrir sala LAN: " + e.getMessage());
            startCombinedScan();
        }
    }

    private P2PManager.Listener createHostListener() {
        return new P2PManager.Listener() {
            @Override
            public void onConnected(String peerInfo) {
                Platform.runLater(() -> {
                    closeWaitingOverlay();
                    app.showGameTable();
                });
            }

            @Override
            public void onStateUpdated(GameStateSnapshot snapshot) {}

            @Override
            public void onDisconnected(String reason) {
                Platform.runLater(() -> {
                    closeWaitingOverlay();
                    feedbackLabel.setText(reason);
                    startCombinedScan();
                });
            }

            @Override
            public void onError(String message) {
                Platform.runLater(() -> {
                    closeWaitingOverlay();
                    feedbackLabel.setText(message);
                    startCombinedScan();
                });
            }
        };
    }

    private void showWaitingOverlay(String titleText, String codeHeadline, String detailText) {
        closeWaitingOverlay();
        waitingOverlay = new StackPane();
        waitingOverlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.88);");

        VBox box = new VBox(12);
        box.setAlignment(Pos.CENTER);
        box.setMaxWidth(500);
        box.setMaxHeight(290);
        box.setPadding(new Insets(26));
        box.setStyle("-fx-background-color: #FFFFFF; -fx-border-color: #101010; -fx-border-width: 3;");

        Label title = PixelUI.label(titleText, 16.5, true);
        box.getChildren().add(title);

        if (codeHeadline != null) {
            waitingCodeLabel = PixelUI.label(codeHeadline, 26, true);
            waitingCodeLabel.setStyle("-fx-text-fill: #19498A;");
            box.getChildren().add(waitingCodeLabel);
        } else {
            waitingCodeLabel = null;
        }

        Label details = PixelUI.label(detailText, 12, false);
        details.setWrapText(true);

        Button cancelBtn = PixelUI.pixelButton("CANCELAR SALA", 13, 150, 38);
        cancelBtn.setOnAction(_ -> {
            discoveryService.stop();
            p2pManager.close();
            closeWaitingOverlay();
            startCombinedScan();
        });

        box.getChildren().addAll(details, cancelBtn);
        waitingOverlay.getChildren().add(box);
        getChildren().add(waitingOverlay);
    }

    private void closeWaitingOverlay() {
        if (waitingOverlay != null) {
            getChildren().remove(waitingOverlay);
            waitingOverlay = null;
            waitingCodeLabel = null;
        }
    }

    private String getLocalIpSummary() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
            while (nis.hasMoreElements()) {
                NetworkInterface ni = nis.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        ips.add(addr.getHostAddress());
                    }
                }
            }
        } catch (Exception _) {}
        return ips.isEmpty() ? "127.0.0.1" : String.join(", ", ips);
    }
}
