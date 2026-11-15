package Net;

import Game.config;
import Game.player;
import Game.trumpCard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class P2PManager {
    public static class PublicRoom {
        public final String code;
        public final String hostName;
        public final byte idJudge;
        public final byte betMode;
        public final byte initialChips;

        public PublicRoom(String code, String hostName, byte idJudge, byte betMode, byte initialChips) {
            this.code = code;
            this.hostName = hostName;
            this.idJudge = idJudge;
            this.betMode = betMode;
            this.initialChips = initialChips;
        }
    }

    public interface Listener {
        void onConnected(String peerInfo);
        void onStateUpdated(GameStateSnapshot snapshot);
        void onDisconnected(String reason);
        void onError(String message);
    }

    private static final ExecutorService HTTP_EXEC = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "railway-http-client");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .executor(HTTP_EXEC)
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private final Object gameLock = new Object();
    private final Object writeLock = new Object();
    private final Object snapshotSendLock = new Object();

    private volatile boolean active;
    private volatile boolean isHost;
    private volatile ServerSocket serverSocket;
    private volatile Socket socket;
    private volatile BufferedReader in;
    private volatile BufferedWriter out;
    private volatile WebSocket webSocket;
    private CompletableFuture<Void> wsSendChain = CompletableFuture.completedFuture(null);
    private volatile Listener listener;
    private volatile GameStateSnapshot latestSnapshot;

    private volatile player hostPlayer;
    private volatile player clientPlayer;
    private volatile boolean hostRematchReady;
    private volatile boolean clientRematchReady;
    private Thread syncThread;

    public void setListener(Listener newListener) {
        this.listener = newListener;
        GameStateSnapshot snap = this.latestSnapshot;
        if (newListener != null && snap != null) {
            newListener.onStateUpdated(snap);
        }
    }

    public int startHost(GameRules rules, int preferredPort, Listener listener) throws IOException {
        close();
        this.listener = listener;
        this.isHost = true;
        this.active = true;

        serverSocket = bindServerSocket(preferredPort);
        int boundPort = serverSocket.getLocalPort();

        Thread acceptThread = new Thread(() -> {
            try {
                Socket accepted = serverSocket.accept();
                if (!active) {
                    accepted.close();
                    return;
                }
                accepted.setTcpNoDelay(true);
                accepted.setKeepAlive(true);
                socket = accepted;
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                String helloLine = in.readLine();
                String clientName = "Cliente";
                if (helloLine != null && helloLine.startsWith("HELLO|")) {
                    clientName = unb64(helloLine.substring("HELLO|".length()));
                }
                startHostMatchWithClient(rules, clientName);

                String line;
                while (active && (line = in.readLine()) != null) {
                    if (line.startsWith("CMD|")) {
                        handleClientCommand(line.substring("CMD|".length()));
                    }
                }
                if (active) {
                    Listener l = this.listener;
                    if (l != null) l.onDisconnected("El oponente se desconecto.");
                }
            } catch (Throwable t) {
                if (active) {
                    Listener l = this.listener;
                    if (l != null) l.onDisconnected("Conexion cerrada: " + t.getMessage());
                }
            } finally {
                closeQuietly();
            }
        }, "p2p-host-thread");
        acceptThread.setDaemon(true);
        acceptThread.start();

        return boundPort;
    }

    private void startHostMatchWithClient(GameRules rules, String rawClientName) {
        String clientName = (rawClientName == null || rawClientName.isBlank()) ? "Cliente" : rawClientName.trim();
        if (clientName.equalsIgnoreCase(rules.hostName)) {
            clientName = clientName + " (2)";
        }

        byte startingChips = rules.initialChips;

        synchronized (gameLock) {
            hostRematchReady = false;
            clientRematchReady = false;
            hostPlayer = new player(rules.hostName, startingChips);
            clientPlayer = new player(clientName, startingChips);
            config.setConfiguration(rules.idJudge, rules.betMode, trumpCard.getPool(), hostPlayer, clientPlayer);
            config.startRound();
        }

        Listener currentListener = this.listener;
        if (currentListener != null && active) {
            currentListener.onConnected(clientName);
        }
        broadcastSnapshots();
        startHostSyncLoop();
    }

    private void startHostSyncLoop() {
        syncThread = new Thread(() -> {
            while (active) {
                try {
                    Thread.sleep(1000);
                    if (!active || (out == null && webSocket == null) || clientPlayer == null) continue;
                    synchronized (snapshotSendLock) {
                        GameStateSnapshot clientSnap;
                        synchronized (gameLock) {
                            if (clientPlayer == null) continue;
                            clientSnap = GameStateSnapshot.fromGameFor(clientPlayer);
                            clientSnap.myRematchReady = clientRematchReady;
                            clientSnap.enemyRematchReady = hostRematchReady;
                        }
                        sendRawLine("STATE|" + clientSnap.serialize());
                    }
                } catch (InterruptedException _) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Throwable _) {
                    // Ignore transient sync errors; reader thread handles disconnect
                }
            }
        }, "p2p-host-sync-thread");
        syncThread.setDaemon(true);
        syncThread.start();
    }

    private ServerSocket bindServerSocket(int preferredPort) throws IOException {
        for (int port = preferredPort; port <= preferredPort + 20; port++) {
            try {
                ServerSocket ss = new ServerSocket();
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(port));
                return ss;
            } catch (IOException _) {
                // Try next port
            }
        }
        return new ServerSocket(0);
    }

    public void connectToHost(String hostIp, int port, String playerName, Listener listener) {
        close();
        this.listener = listener;
        this.isHost = false;
        this.active = true;

        Thread clientThread = new Thread(() -> {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(hostIp, port), 5000);
                if (!active) {
                    s.close();
                    return;
                }
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                socket = s;
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                String safeName = (playerName == null || playerName.isBlank()) ? "Cliente" : playerName.trim();
                sendRawLine("HELLO|" + b64(safeName));

                Listener currentListener = this.listener;
                if (currentListener != null) {
                    currentListener.onConnected(hostIp + ":" + port);
                }

                String line;
                while (active && (line = in.readLine()) != null) {
                    if (line.startsWith("STATE|")) {
                        GameStateSnapshot snapshot = GameStateSnapshot.deserialize(line.substring("STATE|".length()));
                        latestSnapshot = snapshot;
                        Listener l = this.listener;
                        if (l != null) {
                            l.onStateUpdated(snapshot);
                        }
                    }
                }
                if (active) {
                    Listener l = this.listener;
                    if (l != null) l.onDisconnected("El anfitrion cerro la partida.");
                }
            } catch (Throwable t) {
                if (active) {
                    Listener l = this.listener;
                    if (l != null) l.onError("No se pudo conectar a " + hostIp + ":" + port + " (" + t.getMessage() + ")");
                }
            } finally {
                closeQuietly();
            }
        }, "p2p-client-thread");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    public static String normalizeWsUrl(String rawUrl) {
        String url = (rawUrl == null || rawUrl.isBlank())
                ? "wss://twentyone-production.up.railway.app"
                : rawUrl.trim();
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring("https://".length());
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring("http://".length());
        } else if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
            boolean local = url.startsWith("127.0.0.1") || url.startsWith("localhost")
                    || url.startsWith("192.168.") || url.startsWith("10.");
            url = (local ? "ws://" : "wss://") + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/ws")) {
            url = url + "/ws";
        }
        return url;
    }

    public static String normalizeHttpUrl(String rawUrl) {
        String url = (rawUrl == null || rawUrl.isBlank())
                ? "https://twentyone-production.up.railway.app"
                : rawUrl.trim();
        if (url.startsWith("wss://")) {
            url = "https://" + url.substring("wss://".length());
        } else if (url.startsWith("ws://")) {
            url = "http://" + url.substring("ws://".length());
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            boolean local = url.startsWith("127.0.0.1") || url.startsWith("localhost")
                    || url.startsWith("192.168.") || url.startsWith("10.");
            url = (local ? "http://" : "https://") + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/ws")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    public static List<PublicRoom> fetchPublicRooms(String serverUrl) {
        List<PublicRoom> result = new ArrayList<>();
        try {
            URI uri = URI.create(normalizeHttpUrl(serverUrl) + "/rooms");
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                return result;
            }
            for (String line : resp.body().split("\\R")) {
                if (!line.startsWith("ROOM|")) continue;
                String[] parts = line.split("\\|", -1);
                if (parts.length < 6) continue;
                try {
                    result.add(new PublicRoom(
                            parts[1],
                            unb64(parts[2]),
                            Byte.parseByte(parts[3]),
                            Byte.parseByte(parts[4]),
                            Byte.parseByte(parts[5])
                    ));
                } catch (Exception _) {}
            }
        } catch (Throwable _) {
            // Relay unavailable or offline
        }
        return result;
    }

    public void startRailwayHost(String serverUrl, GameRules rules, boolean isPublic,
                                 Consumer<String> onCodeAssigned, Listener listener) {
        close();
        this.listener = listener;
        this.isHost = true;
        this.active = true;

        URI wsUri = URI.create(normalizeWsUrl(serverUrl));
        final StringBuilder textBuffer = new StringBuilder();
        final boolean[] paired = {false};

        HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .buildAsync(wsUri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        if (!active) {
                            ws.abort();
                            return;
                        }
                        webSocket = ws;
                        sendRawLine("HOST|" + b64(rules.hostName) + "|" + rules.idJudge
                                + "|" + rules.betMode + "|" + rules.initialChips + "|" + (isPublic ? "1" : "0"));
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        textBuffer.append(data);
                        if (last) {
                            String msg = textBuffer.toString();
                            textBuffer.setLength(0);
                            handleRailwayHostMessage(msg, rules, paired, onCodeAssigned);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        if (active) {
                            Listener l = P2PManager.this.listener;
                            if (l != null) {
                                if (paired[0]) {
                                    l.onDisconnected("Conexion cerrada con el servidor.");
                                } else {
                                    l.onError("El servidor cerro la sala.");
                                }
                            }
                        }
                        closeQuietly();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        if (active) {
                            Listener l = P2PManager.this.listener;
                            if (l != null) {
                                l.onError("Error Railway: " + error.getMessage());
                            }
                        }
                        closeQuietly();
                    }
                })
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        if (active) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            Listener l = this.listener;
                            if (l != null) {
                                l.onError("No se pudo conectar a Railway: " + cause.getMessage());
                            }
                        }
                        closeQuietly();
                    } else if (!active && ws != null) {
                        ws.abort();
                    }
                });
    }

    private void handleRailwayHostMessage(String msg, GameRules rules, boolean[] paired,
                                          Consumer<String> onCodeAssigned) {
        if (msg.startsWith("ROOM_CREATED|")) {
            String code = msg.substring("ROOM_CREATED|".length()).trim();
            if (onCodeAssigned != null) {
                onCodeAssigned.accept(code);
            }
        } else if (msg.startsWith("HELLO|")) {
            paired[0] = true;
            String clientName = unb64(msg.substring("HELLO|".length()));
            startHostMatchWithClient(rules, clientName);
        } else if (msg.startsWith("CMD|")) {
            handleClientCommand(msg.substring("CMD|".length()));
        } else if (msg.startsWith("PEER_LEFT|")) {
            if (active) {
                Listener l = this.listener;
                if (l != null) {
                    l.onDisconnected(msg.substring("PEER_LEFT|".length()));
                }
            }
            closeQuietly();
        } else if (msg.startsWith("ERROR|")) {
            if (active) {
                Listener l = this.listener;
                if (l != null) {
                    l.onError(msg.substring("ERROR|".length()));
                }
            }
            closeQuietly();
        }
    }

    public void connectToRailwayRoom(String serverUrl, String roomCode, String playerName, Listener listener) {
        close();
        this.listener = listener;
        this.isHost = false;
        this.active = true;

        String safeName = (playerName == null || playerName.isBlank()) ? "Cliente" : playerName.trim();
        String safeCode = roomCode == null ? "" : roomCode.trim();
        URI wsUri = URI.create(normalizeWsUrl(serverUrl));
        final StringBuilder textBuffer = new StringBuilder();
        final boolean[] paired = {false};

        HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(6))
                .buildAsync(wsUri, new WebSocket.Listener() {
                    @Override
                    public void onOpen(WebSocket ws) {
                        if (!active) {
                            ws.abort();
                            return;
                        }
                        webSocket = ws;
                        sendRawLine("JOIN|" + safeCode + "|" + b64(safeName));
                        ws.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                        textBuffer.append(data);
                        if (last) {
                            String msg = textBuffer.toString();
                            textBuffer.setLength(0);
                            handleRailwayClientMessage(msg, paired);
                        }
                        ws.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                        if (active) {
                            Listener l = P2PManager.this.listener;
                            if (l != null) {
                                if (paired[0]) {
                                    l.onDisconnected("El anfitrion cerro la partida.");
                                } else {
                                    l.onError("No se pudo unir a la sala #" + safeCode);
                                }
                            }
                        }
                        closeQuietly();
                        return null;
                    }

                    @Override
                    public void onError(WebSocket ws, Throwable error) {
                        if (active) {
                            Listener l = P2PManager.this.listener;
                            if (l != null) {
                                l.onError("Error Railway: " + error.getMessage());
                            }
                        }
                        closeQuietly();
                    }
                })
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        if (active) {
                            Throwable cause = err.getCause() != null ? err.getCause() : err;
                            Listener l = this.listener;
                            if (l != null) {
                                l.onError("No se pudo conectar a Railway: " + cause.getMessage());
                            }
                        }
                        closeQuietly();
                    } else if (!active && ws != null) {
                        ws.abort();
                    }
                });
    }

    private void handleRailwayClientMessage(String msg, boolean[] paired) {
        if (msg.startsWith("PAIRED|")) {
            paired[0] = true;
            String hostName = unb64(msg.substring("PAIRED|".length()));
            Listener l = this.listener;
            if (l != null && active) {
                l.onConnected(hostName);
            }
        } else if (msg.startsWith("STATE|")) {
            GameStateSnapshot snapshot = GameStateSnapshot.deserialize(msg.substring("STATE|".length()));
            latestSnapshot = snapshot;
            Listener l = this.listener;
            if (l != null && active) {
                l.onStateUpdated(snapshot);
            }
        } else if (msg.startsWith("PEER_LEFT|")) {
            if (active) {
                Listener l = this.listener;
                if (l != null) {
                    l.onDisconnected(msg.substring("PEER_LEFT|".length()));
                }
            }
            closeQuietly();
        } else if (msg.startsWith("ERROR|")) {
            if (active) {
                Listener l = this.listener;
                if (l != null) {
                    l.onError(msg.substring("ERROR|".length()));
                }
            }
            closeQuietly();
        }
    }

    public void performLocalDraw() {
        if (isHost) {
            executeActorCommand(hostPlayer, "DRAW");
        } else {
            sendToHost("CMD|DRAW");
        }
    }

    public void performLocalStand() {
        if (isHost) {
            executeActorCommand(hostPlayer, "STAND");
        } else {
            sendToHost("CMD|STAND");
        }
    }

    public void performLocalUseTrump(String trumpName) {
        if (isHost) {
            executeActorCommand(hostPlayer, "USE_TRUMP|" + b64(trumpName));
        } else {
            sendToHost("CMD|USE_TRUMP|" + b64(trumpName));
        }
    }

    public void performLocalNextRound() {
        if (isHost) {
            executeActorCommand(hostPlayer, "NEXT_ROUND");
        } else {
            sendToHost("CMD|NEXT_ROUND");
        }
    }

    public void performLocalSurrender() {
        if (isHost) {
            executeActorCommand(hostPlayer, "SURRENDER");
        } else {
            sendToHost("CMD|SURRENDER");
        }
    }

    public void performLocalRematchReady() {
        if (isHost) {
            executeActorCommand(hostPlayer, "REMATCH_OK");
        } else {
            sendToHost("CMD|REMATCH_OK");
        }
    }

    private void sendToHost(String msg) {
        if (!active) return;
        sendRawLine(msg);
    }

    private void sendRawLine(String msg) {
        synchronized (writeLock) {
            if (!active) return;
            WebSocket ws = this.webSocket;
            if (ws != null) {
                wsSendChain = wsSendChain.thenCompose(_ -> {
                    if (!active || this.webSocket != ws) {
                        return CompletableFuture.completedFuture(null);
                    }
                    try {
                        return ws.sendText(msg, true).handle((_, _) -> null);
                    } catch (Throwable _) {
                        return CompletableFuture.completedFuture(null);
                    }
                });
                return;
            }
            BufferedWriter writer = this.out;
            if (writer == null) return;
            try {
                writer.write(msg);
                writer.write('\n');
                writer.flush();
            } catch (IOException _) {
                // Reader thread handles disconnect
            }
        }
    }

    private void handleClientCommand(String cmd) {
        executeActorCommand(clientPlayer, cmd);
    }

    private void executeActorCommand(player actor, String cmd) {
        if (actor == null) return;
        boolean changed = false;
        synchronized (gameLock) {
            if ("SURRENDER".equals(cmd)) {
                if (config.isInGame()) {
                    config.surrender(actor);
                    changed = true;
                }
            } else if ("NEXT_ROUND".equals(cmd)) {
                if (config.isRoundOver() && config.isInGame()) {
                    config.advanceToNextRound();
                    changed = true;
                }
            } else if ("REMATCH_OK".equals(cmd)) {
                if (config.isRoundOver() && !config.isInGame()) {
                    if (actor == hostPlayer) {
                        hostRematchReady = true;
                    } else if (actor == clientPlayer) {
                        clientRematchReady = true;
                    }
                    if (hostRematchReady && clientRematchReady) {
                        hostRematchReady = false;
                        clientRematchReady = false;
                        config.restartMatch();
                    }
                    changed = true;
                }
            } else if (!config.isRoundOver() && config.isInGame() && config.getCurrentPlayer() == actor) {
                if ("DRAW".equals(cmd)) {
                    if (!actor.isBusted()) {
                        actor.drawNumber();
                        changed = true;
                    }
                } else if ("STAND".equals(cmd)) {
                    actor.keepHand();
                    changed = true;
                } else if (cmd.startsWith("USE_TRUMP|")) {
                    String trumpName = unb64(cmd.substring("USE_TRUMP|".length()));
                    trumpCard tc = trumpCard.getTrump(trumpName);
                    if (tc != null && actor.hasTrumpSaved(tc)) {
                        actor.useTrump(tc);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            broadcastSnapshots();
        }
    }

    private void broadcastSnapshots() {
        GameStateSnapshot hostSnap;
        synchronized (snapshotSendLock) {
            GameStateSnapshot clientSnap;
            synchronized (gameLock) {
                if (hostPlayer == null || clientPlayer == null) return;
                hostSnap = GameStateSnapshot.fromGameFor(hostPlayer);
                hostSnap.myRematchReady = hostRematchReady;
                hostSnap.enemyRematchReady = clientRematchReady;
                clientSnap = GameStateSnapshot.fromGameFor(clientPlayer);
                clientSnap.myRematchReady = clientRematchReady;
                clientSnap.enemyRematchReady = hostRematchReady;
            }
            sendRawLine("STATE|" + clientSnap.serialize());
            latestSnapshot = hostSnap;
        }
        Listener currentListener = this.listener;
        if (currentListener != null && active) {
            currentListener.onStateUpdated(hostSnap);
        }
    }

    public boolean isHost() { return isHost; }

    public synchronized void close() {
        active = false;
        latestSnapshot = null;
        if (syncThread != null) {
            syncThread.interrupt();
            syncThread = null;
        }
        closeQuietly();
    }

    private void closeQuietly() {
        active = false;
        hostRematchReady = false;
        clientRematchReady = false;
        if (syncThread != null) {
            syncThread.interrupt();
            syncThread = null;
        }
        try {
            Socket s = this.socket;
            if (s != null) {
                s.close();
                this.socket = null;
            }
        } catch (IOException _) {}
        try {
            ServerSocket ss = this.serverSocket;
            if (ss != null) {
                ss.close();
                this.serverSocket = null;
            }
        } catch (IOException _) {}
        try {
            WebSocket ws = this.webSocket;
            if (ws != null) {
                this.webSocket = null;
                try {
                    ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
                } catch (Throwable _) {
                    ws.abort();
                }
            }
        } catch (Throwable _) {}
        wsSendChain = CompletableFuture.completedFuture(null);
        this.in = null;
        this.out = null;
        this.hostPlayer = null;
        this.clientPlayer = null;
    }

    private static String b64(String str) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((str == null ? "" : str).getBytes(StandardCharsets.UTF_8));
    }

    private static String unb64(String token) {
        if (token == null || token.isEmpty()) return "";
        return new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }
}
