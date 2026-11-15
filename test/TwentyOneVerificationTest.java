import Game.config;
import Game.numberCard;
import Game.player;
import Game.trumpCard;
import Net.GameRules;
import Net.GameStateSnapshot;
import Net.LanDiscoveryService;
import Net.P2PManager;
import Net.RailwayRelayServer;
import UI.AssetManager;
import UI.GameTableView;
import UI.LobbyView;
import UI.MainMenuView;
import UI.PixelUI;
import UI.SoundManager;
import UI.TwentyOneApp;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Pane;
import javafx.scene.text.Font;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TwentyOneVerificationTest {

    @BeforeAll
    static void initJavaFxToolkit() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
            latch.await(5, TimeUnit.SECONDS);
        } catch (IllegalStateException _) {
            // Toolkit already initialized
        }
    }

    @Test
    void testAll25TrumpsAndGameLogic() {
        assertEquals(25, trumpCard.getPool().size(), "Debe haber 25 comodines en el pool");

        player p1 = new player("Host", (byte) 7);
        player p2 = new player("Client", (byte) 7);
        config.setConfiguration(config.JUDGE_HOST, config.BET_SINGLE, trumpCard.getPool(), p1, p2);
        config.startRound();

        // Give every trump card to current player and execute it safely
        for (trumpCard tc : trumpCard.getPool()) {
            player cur = config.getCurrentPlayer();
            cur.addTrump(tc);
            cur.useTrump(tc);
        }
        assertTrue(config.getNumberCardScore() == 17 || config.getNumberCardScore() == 21
                || config.getNumberCardScore() == 24 || config.getNumberCardScore() == 27);

        // Verify Go for 17 + Destroy reverts to 21
        p1 = new player("Host", (byte) 7);
        p2 = new player("Client", (byte) 7);
        config.setConfiguration(config.JUDGE_HOST, config.BET_SINGLE, trumpCard.getPool(), p1, p2);
        config.startRound();
        config.setCurrentPlayer(p1);
        config.setNextPlayer(p2);

        p1.addTrump(trumpCard.getTrump("Go for 17"));
        p1.useTrump(trumpCard.getTrump("Go for 17"));
        assertEquals(17, config.getNumberCardScore());

        p2.addTrump(trumpCard.getTrump("Destroy"));
        p2.useTrump(trumpCard.getTrump("Destroy"));
        assertEquals(21, config.getNumberCardScore());

        // Verify One-Up + Destroy reverts bet back to 1
        p1.addTrump(trumpCard.getTrump("One-Up"));
        p1.useTrump(trumpCard.getTrump("One-Up"));
        assertEquals(2, config.getCurrentBet());
        p2.addTrump(trumpCard.getTrump("Destroy"));
        p2.useTrump(trumpCard.getTrump("Destroy"));
        assertEquals(1, config.getCurrentBet());

        // Verify Perfect Draw picks optimal card <= target
        p1.getNumberCards().clear();
        p2.getNumberCards().clear();
        numberCard.getPool().clear();
        for (int i = 1; i <= 11; i++) numberCard.addToPool((byte) i);
        p1.addNumber((byte) 10);
        p1.addNumber((byte) 6); // sum = 16, target = 21, best available is 5
        p1.addTrump(trumpCard.getTrump("Perfect Draw"));
        p1.useTrump(trumpCard.getTrump("Perfect Draw"));
        assertEquals(21, p1.getTotalNumberSum());

        // Verify played trumps history & GameStateSnapshot roundtrip
        assertFalse(config.getPlayedTrumpsHistory().isEmpty());
        GameStateSnapshot snap = GameStateSnapshot.fromGameFor(p1);
        assertEquals(config.getPlayedTrumpsHistory().size(), snap.playedTrumps.size());
        GameStateSnapshot decoded = GameStateSnapshot.deserialize(snap.serialize());
        assertEquals(snap.playedTrumps.size(), decoded.playedTrumps.size());
        assertEquals("Perfect Draw", decoded.playedTrumps.getLast().name);
        assertFalse(decoded.playedTrumps.getLast().description.isEmpty());

        // Verify busted player (> targetScore) is prohibited from drawing more cards
        p1.getNumberCards().clear();
        numberCard.getPool().clear();
        for (int i = 1; i <= 11; i++) numberCard.addToPool((byte) i);
        p1.addNumber((byte) 11);
        p1.addNumber((byte) 10);
        p1.addNumber((byte) 2); // total 23 > 21
        assertTrue(p1.isBusted());
        int countBefore = p1.getNumberCards().size();
        p1.drawNumber();
        assertEquals(countBefore, p1.getNumberCards().size(), "No debe poder robar si ya se paso de 21");

        // Verify both skipping without playing trump cards immediately ends the round
        config.setConfiguration(config.JUDGE_HOST, config.BET_SINGLE, trumpCard.getPool(), p1, p2);
        config.startRound();
        assertFalse(config.isRoundOver());
        config.getCurrentPlayer().keepHand();
        assertFalse(config.isRoundOver());
        config.getCurrentPlayer().keepHand();
        assertTrue(config.isRoundOver(), "Si ambos saltan sin jugar comodines debe terminar la ronda");
    }

    @Test
    void testJudgesDoubleBetBlessAndRoundResolution() {
        // Test Bless prevents bankruptcy
        player p1 = new player("P1", (byte) 1);
        player p2 = new player("P2", (byte) 7);
        config.setConfiguration(config.JUDGE_BANK, config.BET_DOUBLE, trumpCard.getPool(), p1, p2);
        config.startRound();

        p1.getNumberCards().clear();
        p2.getNumberCards().clear();
        p1.addNumber((byte) 2, p2); // total 2
        p2.addNumber((byte) 10, p1);
        p2.addNumber((byte) 11); // total 21

        p1.addTrump(trumpCard.getTrump("Bless"));
        p1.useTrump(trumpCard.getTrump("Bless"));

        // Both stand -> round resolves, p1 loses but Bless saves p1's 1 chip
        p1.keepHand();
        p2.keepHand();
        assertTrue(config.isRoundOver());
        assertEquals(1, p1.getChips(), "Bless debe evitar perder fichas por bancarrota");
        assertTrue(config.isInGame());

        // Advance to next round: loser (p1) starts, base bet stays 1 while round counter increments
        config.advanceToNextRound();
        assertFalse(config.isRoundOver());
        assertEquals(p1, config.getCurrentPlayer());
        assertEquals(2, config.getOriginalBet());
        assertEquals(1, config.getCurrentBet());

        // Direct 1-to-1 chip transfer: p1 loses final chip -> p2 takes all chips and match ends
        p1.getNumberCards().clear();
        p2.getNumberCards().clear();
        p1.addNumber((byte) 3, p2);
        p2.addNumber((byte) 10, p1);
        p2.addNumber((byte) 11);
        p1.keepHand();
        p2.keepHand();
        assertTrue(config.isRoundOver());
        assertEquals(0, p1.getChips(), "Perdedor debe quedar en 0 fichas");
        assertEquals(8, p2.getChips(), "Ganador debe recibir la ficha del perdedor directamente");
        assertFalse(config.isInGame(), "Al llegar a 0 fichas debe finalizar toda la partida");

        // restartMatch restores initial chips & round 1
        config.restartMatch();
        assertTrue(config.isInGame());
        assertFalse(config.isRoundOver());
        assertEquals(1, config.getOriginalBet());
        assertEquals(1, config.getCurrentBet());
    }

    @Test
    void testP2PManagerAndHiddenCardPrivacy() throws Exception {
        P2PManager hostMgr = new P2PManager();
        P2PManager clientMgr = new P2PManager();

        CountDownLatch bothInitLatch = new CountDownLatch(2);
        CountDownLatch roundOverLatch = new CountDownLatch(2);
        AtomicReference<GameStateSnapshot> hostSnapRef = new AtomicReference<>();
        AtomicReference<GameStateSnapshot> clientSnapRef = new AtomicReference<>();

        int port = hostMgr.startHost(new GameRules("Anfitrion", config.JUDGE_HOST, config.BET_SINGLE, (byte) 7), 17777,
                new P2PManager.Listener() {
                    @Override public void onConnected(String peerInfo) {}
                    @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                        hostSnapRef.set(snapshot);
                        bothInitLatch.countDown();
                        if (snapshot.roundOver) roundOverLatch.countDown();
                    }
                    @Override public void onDisconnected(String reason) {}
                    @Override public void onError(String message) { fail(message); }
                });

        clientMgr.connectToHost("127.0.0.1", port, "Invitado", new P2PManager.Listener() {
            @Override public void onConnected(String peerInfo) {}
            @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                clientSnapRef.set(snapshot);
                bothInitLatch.countDown();
                if (snapshot.roundOver) roundOverLatch.countDown();
            }
            @Override public void onDisconnected(String reason) {}
            @Override public void onError(String message) { fail(message); }
        });

        assertTrue(bothInitLatch.await(5, TimeUnit.SECONDS), "Host y Cliente deben recibir snapshot inicial");
        GameStateSnapshot hSnap = hostSnapRef.get();
        GameStateSnapshot cSnap = clientSnapRef.get();
        assertNotNull(hSnap);
        assertNotNull(cSnap);

        // Verify enemy hidden card is NOT leaked before roundOver
        assertEquals(1, hSnap.getEnemyHiddenCount());
        assertEquals(1, cSnap.getEnemyHiddenCount());

        // Test multi-turn Draw + Stand + Stand + NextRound synchronization
        boolean hostStarted = hSnap.myTurn;
        CountDownLatch drawTurnFlippedLatch = new CountDownLatch(2);
        CountDownLatch round2Latch = new CountDownLatch(2);
        CountDownLatch bothRoundOverLatch = new CountDownLatch(2);

        hostMgr.setListener(new P2PManager.Listener() {
            @Override public void onConnected(String peerInfo) {}
            @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                hostSnapRef.set(snapshot);
                if (snapshot.myTurn != hostStarted && !snapshot.roundOver) drawTurnFlippedLatch.countDown();
                if (snapshot.roundOver) bothRoundOverLatch.countDown();
                if (snapshot.roundNumber == 2 && !snapshot.roundOver) round2Latch.countDown();
            }
            @Override public void onDisconnected(String reason) {}
            @Override public void onError(String message) { fail(message); }
        });
        clientMgr.setListener(new P2PManager.Listener() {
            @Override public void onConnected(String peerInfo) {}
            @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                clientSnapRef.set(snapshot);
                if (snapshot.myTurn == hostStarted && !snapshot.roundOver) drawTurnFlippedLatch.countDown();
                if (snapshot.roundOver) bothRoundOverLatch.countDown();
                if (snapshot.roundNumber == 2 && !snapshot.roundOver) round2Latch.countDown();
            }
            @Override public void onDisconnected(String reason) {}
            @Override public void onError(String message) { fail(message); }
        });

        // Player with initial turn draws a card -> turn flips to the other player
        if (hostStarted) {
            hostMgr.performLocalDraw();
        } else {
            clientMgr.performLocalDraw();
        }
        assertTrue(drawTurnFlippedLatch.await(5, TimeUnit.SECONDS), "Ambos jugadores deben recibir cambio de turno tras robar");

        // Second player stands, then first player stands
        CountDownLatch firstStandLatch = new CountDownLatch(2);
        hostMgr.setListener(new P2PManager.Listener() {
            @Override public void onConnected(String peerInfo) {}
            @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                hostSnapRef.set(snapshot);
                if (snapshot.myStood || snapshot.enemyStood) firstStandLatch.countDown();
                if (snapshot.roundOver) bothRoundOverLatch.countDown();
                if (snapshot.roundNumber == 2 && !snapshot.roundOver) round2Latch.countDown();
            }
            @Override public void onDisconnected(String reason) {}
            @Override public void onError(String message) { fail(message); }
        });
        clientMgr.setListener(new P2PManager.Listener() {
            @Override public void onConnected(String peerInfo) {}
            @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                clientSnapRef.set(snapshot);
                if (snapshot.myStood || snapshot.enemyStood) firstStandLatch.countDown();
                if (snapshot.roundOver) bothRoundOverLatch.countDown();
                if (snapshot.roundNumber == 2 && !snapshot.roundOver) round2Latch.countDown();
            }
            @Override public void onDisconnected(String reason) {}
            @Override public void onError(String message) { fail(message); }
        });

        if (hostSnapRef.get().myTurn) {
            hostMgr.performLocalStand();
        } else {
            clientMgr.performLocalStand();
        }
        assertTrue(firstStandLatch.await(5, TimeUnit.SECONDS), "Debe sincronizarse el primer plantarse");

        if (hostSnapRef.get().myTurn) {
            hostMgr.performLocalStand();
        } else {
            clientMgr.performLocalStand();
        }

        assertTrue(bothRoundOverLatch.await(5, TimeUnit.SECONDS), "Ambos plantados debe cerrar ronda");
        assertTrue(hostSnapRef.get().roundOver);
        assertTrue(clientSnapRef.get().roundOver);
        assertEquals(0, hostSnapRef.get().getEnemyHiddenCount(), "Al acabar ronda, cartas ocultas deben revelarse");
        assertEquals(0, clientSnapRef.get().getEnemyHiddenCount(), "Al acabar ronda, cartas ocultas deben revelarse");

        // Client triggers next round and both sync to round 2
        clientMgr.performLocalNextRound();
        assertTrue(round2Latch.await(5, TimeUnit.SECONDS), "Ambos jugadores deben avanzar a ronda 2");
        assertEquals(2, hostSnapRef.get().roundNumber);
        assertEquals(2, clientSnapRef.get().roundNumber);

        clientMgr.close();
        hostMgr.close();
    }

    @Test
    void testLanDiscoveryService() throws Exception {
        LanDiscoveryService broadcaster = new LanDiscoveryService();
        LanDiscoveryService scanner = new LanDiscoveryService();

        CountDownLatch foundLatch = new CountDownLatch(1);
        AtomicReference<List<LanDiscoveryService.DiscoveredHost>> foundRef = new AtomicReference<>();

        broadcaster.startHostingBroadcast(new GameRules("SalaPrueba", config.JUDGE_MAFIA, config.BET_DOUBLE, (byte) 9), 18888);
        scanner.startScanning(hosts -> {
            if (!hosts.isEmpty()) {
                foundRef.set(hosts);
                foundLatch.countDown();
            }
        });

        assertTrue(foundLatch.await(5, TimeUnit.SECONDS), "Debe descubrir la sala en red local / loopback");
        LanDiscoveryService.DiscoveredHost host = foundRef.get().getFirst();
        assertEquals("SalaPrueba", host.hostName);
        assertEquals(18888, host.tcpPort);
        assertEquals(config.JUDGE_MAFIA, host.idJudge);

        scanner.stop();
        broadcaster.stop();
    }

    @Test
    void testAssetManagerLoadsAllAssets() {
        Path root = AssetManager.getAssetsRoot();
        assertTrue(Files.isDirectory(root), "Carpeta Snoblin_Deck_of_Playing_Cards debe existir");

        Image ace21 = AssetManager.getMenuAce21(4);
        assertEquals(39 * 4, (int) ace21.getWidth());
        assertEquals(55 * 4, (int) ace21.getHeight());

        for (int i = 1; i <= 11; i++) {
            assertNotNull(AssetManager.getPlayerVisibleNumberCard(i, 2));
            assertNotNull(AssetManager.getEnemyVisibleNumberCard(i, 2));
            assertNotNull(AssetManager.getPlayerHiddenNumberCard(i, 2));
        }
        assertNotNull(AssetManager.getEnemyHiddenCard(2));
        assertNotNull(AssetManager.getPlayerChipImage(2));
        assertNotNull(AssetManager.getEnemyChipImage(2));

        Font boldFont = PixelUI.font(16);
        Font regFont = PixelUI.fontRegular(14);
        assertTrue(boldFont.getFamily().contains("Pixelify"), "Debe cargar Pixelify Sans Bold: " + boldFont.getFamily());
        assertTrue(regFont.getFamily().contains("Pixelify"), "Debe cargar Pixelify Sans Regular: " + regFont.getFamily());

        for (trumpCard tc : trumpCard.getPool()) {
            Image img = AssetManager.getTrumpCardImage(tc.getName(), 2);
            assertNotNull(img);
            assertEquals(39 * 2, (int) img.getWidth());
        }

        List<Image> carousel = AssetManager.getAllPackCardsForMenu(2);
        assertTrue(carousel.size() >= 50, "Debe cargar todas las cartas del pack para el carrusel: " + carousel.size());
    }

    @Test
    void testJavaFxViewsConstructCleanly() throws Exception {
        CountDownLatch uiLatch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            LanDiscoveryService discovery = new LanDiscoveryService();
            P2PManager p2p = new P2PManager();
            try {
                TwentyOneApp app = new TwentyOneApp();
                assertTrue(app.getRailwayServerUrl().contains("twentyone-production.up.railway.app"));
                MainMenuView menu = new MainMenuView(app);
                LobbyView lobby = new LobbyView(app, discovery, p2p);
                GameTableView table = new GameTableView(app, p2p);
                PixelUI.PixelCheckBox cb = new PixelUI.PixelCheckBox("Test", false);
                assertFalse(cb.isSelected());
                cb.setSelected(true);
                assertTrue(cb.isSelected());

                // Verify SoundManager volume clamping and PixelSlider callback
                double origVol = app.getVolume();
                SoundManager.playBgm();
                AtomicReference<Double> sliderCallbackVal = new AtomicReference<>(0.0);
                PixelUI.PixelSlider slider = new PixelUI.PixelSlider(0.35, v -> {
                    sliderCallbackVal.set(v);
                    app.setVolume(v);
                });
                assertEquals(0.35, slider.getValue(), 1e-4);
                slider.setValue(0.8);
                assertEquals(0.8, sliderCallbackVal.get(), 1e-4);
                assertEquals(0.8, app.getVolume(), 1e-4);
                slider.setValue(1.5);
                assertEquals(1.0, slider.getValue(), 1e-4);
                app.setVolume(origVol);

                assertFalse(app.isBorderless());
                app.setBorderless(true);
                assertTrue(app.isBorderless());
                app.setBorderless(false);
                assertFalse(app.isBorderless());

                assertNotNull(menu);
                assertNotNull(lobby);
                assertNotNull(table);

                // Verify proportional viewport scaling across monitor resolutions
                Pane scaledRoot = app.createScaledRoot();
                Scene testScene = new Scene(scaledRoot, 1080, 680);
                app.attachGlobalShortcuts(testScene);
                app.getRootContainer().getChildren().setAll(menu);

                // Verify F11 shortcut toggles borderless mode
                assertFalse(app.isBorderless());
                KeyEvent f11 = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.F11, false, false, false, false);
                testScene.getRoot().fireEvent(f11);
                assertTrue(app.isBorderless());
                testScene.getRoot().fireEvent(f11);
                assertFalse(app.isBorderless());

                scaledRoot.resize(1080, 680);
                scaledRoot.layout();
                assertEquals(1.0, app.getViewportScale().getX(), 1e-4);
                assertEquals(1080.0, app.getRootContainer().getWidth(), 1e-2);
                assertEquals(680.0, app.getRootContainer().getHeight(), 1e-2);

                scaledRoot.resize(1920, 1080);
                scaledRoot.layout();
                double expected1080pScale = 1080.0 / TwentyOneApp.BASE_HEIGHT;
                assertEquals(expected1080pScale, app.getViewportScale().getX(), 1e-4);
                assertEquals(1920.0 / expected1080pScale, app.getRootContainer().getWidth(), 1e-2);
                assertEquals(680.0, app.getRootContainer().getHeight(), 1e-2);

                scaledRoot.resize(2560, 1440);
                scaledRoot.layout();
                double expected1440pScale = 1440.0 / TwentyOneApp.BASE_HEIGHT;
                assertEquals(expected1440pScale, app.getViewportScale().getX(), 1e-4);
                assertEquals(2560.0 / expected1440pScale, app.getRootContainer().getWidth(), 1e-2);
                assertEquals(680.0, app.getRootContainer().getHeight(), 1e-2);
                assertNotNull(testScene);

                app.getRootContainer().getChildren().setAll(lobby);
                app.getRootContainer().getChildren().clear();
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                discovery.stop();
                p2p.close();
                uiLatch.countDown();
            }
        });

        assertTrue(uiLatch.await(10, TimeUnit.SECONDS), "La construccion de vistas JavaFX no debe bloquearse");
        if (errorRef.get() != null) {
            fail("Error al construir las vistas JavaFX: " + errorRef.get().getMessage(), errorRef.get());
        }
    }

    @Test
    void testRailwayRelayServerCodeAndPublicRooms() throws Exception {
        assertEquals("wss://mi-app.up.railway.app/ws",
                P2PManager.normalizeWsUrl("https://mi-app.up.railway.app/"));
        assertEquals("https://mi-app.up.railway.app",
                P2PManager.normalizeHttpUrl("wss://mi-app.up.railway.app/ws"));

        RailwayRelayServer relayServer = new RailwayRelayServer();
        int port = relayServer.start(0);
        String serverUrl = "ws://127.0.0.1:" + port;

        P2PManager hostMgr = new P2PManager();
        P2PManager clientMgr = new P2PManager();
        try {
            CountDownLatch codeLatch = new CountDownLatch(1);
            CountDownLatch bothInitLatch = new CountDownLatch(2);
            CountDownLatch drawTurnFlippedLatch = new CountDownLatch(2);
            AtomicReference<String> codeRef = new AtomicReference<>();
            AtomicReference<GameStateSnapshot> hostSnapRef = new AtomicReference<>();
            AtomicReference<GameStateSnapshot> clientSnapRef = new AtomicReference<>();
            AtomicReference<Boolean> hostStartedRef = new AtomicReference<>();

            hostMgr.startRailwayHost(
                    serverUrl,
                    new GameRules("HostRailway", config.JUDGE_HOST, config.BET_DOUBLE, (byte) 9),
                    true,
                    code -> {
                        codeRef.set(code);
                        codeLatch.countDown();
                    },
                    new P2PManager.Listener() {
                        @Override public void onConnected(String peerInfo) {}
                        @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                            boolean first = hostSnapRef.getAndSet(snapshot) == null;
                            if (first) {
                                hostStartedRef.compareAndSet(null, snapshot.myTurn);
                                bothInitLatch.countDown();
                            } else if (hostStartedRef.get() != null && snapshot.myTurn != hostStartedRef.get()) {
                                drawTurnFlippedLatch.countDown();
                            }
                        }
                        @Override public void onDisconnected(String reason) {}
                        @Override public void onError(String message) { fail("Host error: " + message); }
                    }
            );

            assertTrue(codeLatch.await(5, TimeUnit.SECONDS), "Debe asignarse codigo de sala de 4 digitos");
            String roomCode = codeRef.get();
            assertNotNull(roomCode);
            assertTrue(roomCode.matches("\\d{4}"), "Codigo debe tener 4 digitos: " + roomCode);

            List<P2PManager.PublicRoom> publicRooms = P2PManager.fetchPublicRooms(serverUrl);
            assertEquals(1, publicRooms.size(), "Debe listar 1 sala publica en /rooms");
            assertEquals(roomCode, publicRooms.getFirst().code);
            assertEquals("HostRailway", publicRooms.getFirst().hostName);
            assertEquals(config.BET_DOUBLE, publicRooms.getFirst().betMode);
            assertEquals(9, publicRooms.getFirst().initialChips);

            clientMgr.connectToRailwayRoom(serverUrl, roomCode, "ClienteRailway", new P2PManager.Listener() {
                @Override public void onConnected(String peerInfo) {}
                @Override public void onStateUpdated(GameStateSnapshot snapshot) {
                    boolean first = clientSnapRef.getAndSet(snapshot) == null;
                    if (first) {
                        bothInitLatch.countDown();
                    } else if (hostStartedRef.get() != null && snapshot.myTurn == hostStartedRef.get()) {
                        drawTurnFlippedLatch.countDown();
                    }
                }
                @Override public void onDisconnected(String reason) {}
                @Override public void onError(String message) { fail("Client error: " + message); }
            });

            assertTrue(bothInitLatch.await(5, TimeUnit.SECONDS), "Host y Cliente deben recibir estado inicial via Railway");
            assertEquals(1, hostSnapRef.get().getEnemyHiddenCount());
            assertEquals(1, clientSnapRef.get().getEnemyHiddenCount());

            if (Boolean.TRUE.equals(hostStartedRef.get())) {
                hostMgr.performLocalDraw();
            } else {
                clientMgr.performLocalDraw();
            }
            assertTrue(drawTurnFlippedLatch.await(5, TimeUnit.SECONDS), "Jugada debe sincronizarse via Railway");
        } finally {
            clientMgr.close();
            hostMgr.close();
            relayServer.stop();
        }
    }

    @Test
    void testP2PRematchOkStartsFreshMatch() throws Exception {
        P2PManager hostMgr = new P2PManager();
        P2PManager clientMgr = new P2PManager();
        try {
            CountDownLatch initLatch = new CountDownLatch(2);
            CountDownLatch gameOverLatch = new CountDownLatch(2);
            CountDownLatch oneReadyLatch = new CountDownLatch(2);
            CountDownLatch rematchStartedLatch = new CountDownLatch(2);

            AtomicReference<GameStateSnapshot> hostSnap = new AtomicReference<>();
            AtomicReference<GameStateSnapshot> clientSnap = new AtomicReference<>();

            int port = hostMgr.startHost(
                    new GameRules("AnfitrionRematch", config.JUDGE_HOST, config.BET_SINGLE, (byte) 5),
                    17779,
                    new P2PManager.Listener() {
                        @Override public void onConnected(String peerInfo) {}
                        @Override public void onStateUpdated(GameStateSnapshot s) {
                            hostSnap.set(s);
                            if (s.roundNumber == 1 && !s.roundOver && !s.gameOver && initLatch.getCount() > 0) {
                                initLatch.countDown();
                            }
                            if (s.gameOver && !s.myRematchReady && !s.enemyRematchReady) {
                                gameOverLatch.countDown();
                            }
                            if (s.gameOver && s.myRematchReady && !s.enemyRematchReady) {
                                oneReadyLatch.countDown();
                            }
                            if (gameOverLatch.getCount() == 0 && !s.gameOver && !s.roundOver && s.myChips == 5 && s.enemyChips == 5) {
                                rematchStartedLatch.countDown();
                            }
                        }
                        @Override public void onDisconnected(String reason) {}
                        @Override public void onError(String message) { fail(message); }
                    }
            );

            clientMgr.connectToHost("127.0.0.1", port, "ClienteRematch", new P2PManager.Listener() {
                @Override public void onConnected(String peerInfo) {}
                @Override public void onStateUpdated(GameStateSnapshot s) {
                    clientSnap.set(s);
                    if (s.roundNumber == 1 && !s.roundOver && !s.gameOver && initLatch.getCount() > 0) {
                        initLatch.countDown();
                    }
                    if (s.gameOver && !s.myRematchReady && !s.enemyRematchReady) {
                        gameOverLatch.countDown();
                    }
                    if (s.gameOver && s.enemyRematchReady && !s.myRematchReady) {
                        oneReadyLatch.countDown();
                    }
                    if (gameOverLatch.getCount() == 0 && !s.gameOver && !s.roundOver && s.myChips == 5 && s.enemyChips == 5) {
                        rematchStartedLatch.countDown();
                    }
                }
                @Override public void onDisconnected(String reason) {}
                @Override public void onError(String message) { fail(message); }
            });

            assertTrue(initLatch.await(5, TimeUnit.SECONDS), "Deben recibir estado inicial");
            assertEquals(5, hostSnap.get().myChips);
            assertEquals(5, clientSnap.get().myChips);

            hostMgr.performLocalSurrender();
            assertTrue(gameOverLatch.await(5, TimeUnit.SECONDS), "Debe sincronizarse fin de partida");

            hostMgr.performLocalRematchReady();
            assertTrue(oneReadyLatch.await(5, TimeUnit.SECONDS), "Ambos deben ver OK 1/2");
            assertTrue(hostSnap.get().myRematchReady);
            assertTrue(clientSnap.get().enemyRematchReady);
            assertTrue(hostSnap.get().gameOver);

            clientMgr.performLocalRematchReady();
            assertTrue(rematchStartedLatch.await(5, TimeUnit.SECONDS), "Tras ambos OK debe iniciar nueva partida");
            assertFalse(hostSnap.get().gameOver);
            assertFalse(clientSnap.get().gameOver);
            assertEquals(1, hostSnap.get().roundNumber);
            assertEquals(5, hostSnap.get().myChips);
            assertEquals(5, clientSnap.get().myChips);
            assertFalse(hostSnap.get().myRematchReady);
            assertFalse(clientSnap.get().myRematchReady);
        } finally {
            clientMgr.close();
            hostMgr.close();
        }
    }
}
