package Net;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight HTTP + RFC 6455 WebSocket relay server designed for Railway deployment.
 * Manages 4-digit room codes (0000-9999), lists public rooms on GET /rooms,
 * and relays game messages between Host and Client over /ws.
 */
public class RailwayRelayServer {
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final int MAX_FRAME_BYTES = 2 * 1024 * 1024;

    private enum Role { NONE, HOST, CLIENT }

    private static class Room {
        final String code;
        final String hostNameB64;
        final byte idJudge;
        final byte betMode;
        final byte initialChips;
        final boolean isPublic;
        final WsSession host;
        volatile WsSession client;

        Room(String code, String hostNameB64, byte idJudge, byte betMode, byte initialChips,
             boolean isPublic, WsSession host) {
            this.code = code;
            this.hostNameB64 = hostNameB64;
            this.idJudge = idJudge;
            this.betMode = betMode;
            this.initialChips = initialChips;
            this.isPublic = isPublic;
            this.host = host;
        }
    }

    private class WsSession {
        final Socket socket;
        final InputStream in;
        final OutputStream out;
        final Object writeLock = new Object();
        volatile boolean open = true;
        volatile Role role = Role.NONE;
        volatile Room room;

        WsSession(Socket socket, InputStream in, OutputStream out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }

        void sendText(String msg) {
            if (!open) return;
            sendFrame(0x1, msg.getBytes(StandardCharsets.UTF_8));
        }

        void sendPing() {
            if (!open) return;
            sendFrame(0x9, new byte[0]);
        }

        void sendPong(byte[] payload) {
            if (!open) return;
            sendFrame(0xA, payload);
        }

        void sendClose() {
            if (!open) return;
            open = false;
            try {
                sendFrameRaw(0x8, new byte[0]);
            } catch (IOException ignored) {}
            closeSocketQuietly();
        }

        private void sendFrame(int opcode, byte[] payload) {
            try {
                sendFrameRaw(opcode, payload);
            } catch (IOException ignored) {
                open = false;
                closeSocketQuietly();
            }
        }

        private void sendFrameRaw(int opcode, byte[] payload) throws IOException {
            synchronized (writeLock) {
                int len = payload.length;
                ByteArrayOutputStream frame = new ByteArrayOutputStream(len + 10);
                frame.write(0x80 | (opcode & 0x0F));
                if (len <= 125) {
                    frame.write(len);
                } else if (len <= 65535) {
                    frame.write(126);
                    frame.write((len >> 8) & 0xFF);
                    frame.write(len & 0xFF);
                } else {
                    frame.write(127);
                    long llen = len;
                    for (int shift = 56; shift >= 0; shift -= 8) {
                        frame.write((int) ((llen >> shift) & 0xFF));
                    }
                }
                frame.write(payload);
                out.write(frame.toByteArray());
                out.flush();
            }
        }

        void closeSocketQuietly() {
            open = false;
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    private final ConcurrentHashMap<String, Room> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<WsSession, Boolean> activeSessions = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private volatile boolean running;
    private volatile ServerSocket serverSocket;
    private Thread acceptThread;
    private ScheduledExecutorService keepAliveExec;

    public static void main(String[] args) throws Exception {
        int port = 8080;
        String envPort = System.getenv("PORT");
        if (envPort != null && !envPort.isBlank()) {
            try {
                port = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException ignored) {}
        }
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                if ("--port".equals(args[i]) && i + 1 < args.length) {
                    port = Integer.parseInt(args[i + 1]);
                }
            }
        }

        RailwayRelayServer server = new RailwayRelayServer();
        int bound = server.start(port);
        System.out.println("TwentyOne Railway Relay Server listening on port " + bound);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "relay-shutdown"));
        Thread.currentThread().join();
    }

    public synchronized int start(int preferredPort) throws IOException {
        if (running) {
            return getPort();
        }
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress("0.0.0.0", preferredPort));
        this.serverSocket = ss;
        this.running = true;

        keepAliveExec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "railway-ws-ping");
            t.setDaemon(true);
            return t;
        });
        keepAliveExec.scheduleAtFixedRate(() -> {
            for (WsSession s : activeSessions.keySet()) {
                if (s.open) {
                    s.sendPing();
                } else {
                    activeSessions.remove(s);
                }
            }
        }, 25, 25, TimeUnit.SECONDS);

        acceptThread = new Thread(this::acceptLoop, "railway-relay-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        return ss.getLocalPort();
    }

    public int getPort() {
        ServerSocket ss = this.serverSocket;
        return ss != null ? ss.getLocalPort() : -1;
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void stop() {
        running = false;
        if (keepAliveExec != null) {
            keepAliveExec.shutdownNow();
            keepAliveExec = null;
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException ignored) {}
        for (WsSession s : activeSessions.keySet()) {
            s.closeSocketQuietly();
        }
        activeSessions.clear();
        rooms.clear();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket client = serverSocket.accept();
                Thread t = new Thread(() -> handleConnection(client), "railway-conn");
                t.setDaemon(true);
                t.start();
            } catch (IOException ignored) {
                if (!running) break;
            }
        }
    }

    private void handleConnection(Socket socket) {
        try {
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            String requestLine = readHttpLine(in);
            if (requestLine == null || requestLine.isBlank()) {
                socket.close();
                return;
            }

            Map<String, String> headers = new HashMap<>();
            String hLine;
            while ((hLine = readHttpLine(in)) != null && !hLine.isEmpty()) {
                int colon = hLine.indexOf(':');
                if (colon > 0) {
                    String k = hLine.substring(0, colon).trim().toLowerCase();
                    String v = hLine.substring(colon + 1).trim();
                    headers.put(k, v);
                }
            }

            String[] parts = requestLine.split(" ");
            String method = parts.length > 0 ? parts[0].toUpperCase() : "GET";
            String rawPath = parts.length > 1 ? parts[1] : "/";
            int qIdx = rawPath.indexOf('?');
            String path = qIdx >= 0 ? rawPath.substring(0, qIdx) : rawPath;

            String upgrade = headers.getOrDefault("upgrade", "");
            String wsKey = headers.get("sec-websocket-key");
            if ("websocket".equalsIgnoreCase(upgrade) && wsKey != null && !wsKey.isBlank()) {
                performWebSocketHandshake(wsKey, out);
                runWebSocketSession(socket, in, out);
                return;
            }

            if ("GET".equals(method) && "/rooms".equals(path)) {
                sendHttpResponse(out, 200, "OK", buildPublicRoomsBody());
            } else {
                sendHttpResponse(out, 200, "OK", "TwentyOne Railway Relay OK\n");
            }
            socket.close();
        } catch (Throwable ignored) {
            try {
                socket.close();
            } catch (IOException ignored2) {}
        }
    }

    private String readHttpLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
            if (buf.size() > 8192) throw new IOException("HTTP header too long");
        }
        if (b == -1 && buf.size() == 0) return null;
        return buf.toString(StandardCharsets.ISO_8859_1);
    }

    private void performWebSocketHandshake(String wsKey, OutputStream out) throws Exception {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] digest = sha1.digest((wsKey.trim() + WS_MAGIC).getBytes(StandardCharsets.ISO_8859_1));
        String accept = Base64.getEncoder().encodeToString(digest);

        String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private void sendHttpResponse(OutputStream out, int code, String status, String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String header = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Cache-Control: no-store\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.ISO_8859_1));
        out.write(bodyBytes);
        out.flush();
    }

    private String buildPublicRoomsBody() {
        List<String> lines = new ArrayList<>();
        for (Room r : rooms.values()) {
            if (r.isPublic && r.client == null && r.host.open) {
                lines.add("ROOM|" + r.code + "|" + r.hostNameB64 + "|"
                        + r.idJudge + "|" + r.betMode + "|" + r.initialChips);
            }
        }
        return String.join("\n", lines);
    }

    private void runWebSocketSession(Socket socket, InputStream in, OutputStream out) {
        WsSession session = new WsSession(socket, in, out);
        activeSessions.put(session, Boolean.TRUE);
        ByteArrayOutputStream fragmentBuf = new ByteArrayOutputStream();
        boolean accumulatingFragments = false;

        try {
            while (running && session.open) {
                int b1 = in.read();
                if (b1 < 0) break;
                int b2 = in.read();
                if (b2 < 0) break;

                boolean fin = (b1 & 0x80) != 0;
                int opcode = b1 & 0x0F;
                boolean masked = (b2 & 0x80) != 0;
                long len = b2 & 0x7F;

                if (len == 126) {
                    int h = in.read();
                    int l = in.read();
                    if (h < 0 || l < 0) break;
                    len = ((h & 0xFF) << 8) | (l & 0xFF);
                } else if (len == 127) {
                    long bigLen = 0;
                    for (int i = 0; i < 8; i++) {
                        int v = in.read();
                        if (v < 0) throw new EOFException();
                        bigLen = (bigLen << 8) | (v & 0xFF);
                    }
                    len = bigLen;
                }

                if (len < 0 || len > MAX_FRAME_BYTES) {
                    break;
                }

                byte[] maskKey = null;
                if (masked) {
                    maskKey = readExact(in, 4);
                }

                byte[] payload = readExact(in, (int) len);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) {
                        payload[i] ^= maskKey[i & 3];
                    }
                }

                if (opcode == 0x8) { // CLOSE
                    session.sendClose();
                    break;
                } else if (opcode == 0x9) { // PING
                    session.sendPong(payload);
                    continue;
                } else if (opcode == 0xA) { // PONG
                    continue;
                }

                if (opcode == 0x1 || opcode == 0x2) { // TEXT or BINARY
                    if (fin) {
                        accumulatingFragments = false;
                        fragmentBuf.reset();
                        handleWsText(session, new String(payload, StandardCharsets.UTF_8));
                    } else {
                        fragmentBuf.reset();
                        fragmentBuf.write(payload);
                        accumulatingFragments = true;
                    }
                } else if (opcode == 0x0 && accumulatingFragments) { // CONTINUATION
                    fragmentBuf.write(payload);
                    if (fin) {
                        accumulatingFragments = false;
                        String msg = fragmentBuf.toString(StandardCharsets.UTF_8);
                        fragmentBuf.reset();
                        handleWsText(session, msg);
                    }
                }
            }
        } catch (Throwable ignored) {
            // Connection closed
        } finally {
            activeSessions.remove(session);
            cleanupSession(session);
            session.closeSocketQuietly();
        }
    }

    private byte[] readExact(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = in.read(buf, offset, length - offset);
            if (read < 0) throw new EOFException();
            offset += read;
        }
        return buf;
    }

    private void handleWsText(WsSession session, String msg) {
        if (msg == null || msg.isEmpty()) return;

        if (session.role == Role.NONE) {
            if (msg.startsWith("HOST|")) {
                String[] parts = msg.split("\\|", -1);
                if (parts.length < 6) {
                    session.sendText("ERROR|Formato de sala invalido.");
                    return;
                }
                String hostNameB64 = parts[1];
                byte idJudge;
                byte betMode;
                byte initialChips;
                try {
                    idJudge = Byte.parseByte(parts[2]);
                    betMode = Byte.parseByte(parts[3]);
                    initialChips = Byte.parseByte(parts[4]);
                } catch (NumberFormatException ignored) {
                    session.sendText("ERROR|Parametros de sala invalidos.");
                    return;
                }
                boolean isPublic = "1".equals(parts[5]) || "true".equalsIgnoreCase(parts[5]);

                Room room = createRoom(hostNameB64, idJudge, betMode, initialChips, isPublic, session);
                if (room == null) {
                    session.sendText("ERROR|No hay codigos de sala disponibles.");
                    session.sendClose();
                    return;
                }
                session.role = Role.HOST;
                session.room = room;
                session.sendText("ROOM_CREATED|" + room.code);
            } else if (msg.startsWith("JOIN|")) {
                String[] parts = msg.split("\\|", -1);
                if (parts.length < 3) {
                    session.sendText("ERROR|Formato JOIN invalido.");
                    return;
                }
                String code = parts[1].trim();
                String clientNameB64 = parts[2];

                Room room = rooms.get(code);
                if (room == null) {
                    session.sendText("ERROR|Codigo de sala '" + code + "' no encontrado.");
                    return;
                }
                synchronized (room) {
                    if (!running || !room.host.open || room.client != null) {
                        session.sendText("ERROR|La sala #" + code + " ya esta llena o cerrada.");
                        return;
                    }
                    room.client = session;
                    session.role = Role.CLIENT;
                    session.room = room;
                }
                session.sendText("PAIRED|" + room.hostNameB64);
                room.host.sendText("HELLO|" + clientNameB64);
            }
            return;
        }

        Room room = session.room;
        if (room == null) return;

        if (session.role == Role.HOST && room.client != null) {
            room.client.sendText(msg);
        } else if (session.role == Role.CLIENT) {
            room.host.sendText(msg);
        }
    }

    private Room createRoom(String hostNameB64, byte idJudge, byte betMode, byte initialChips,
                            boolean isPublic, WsSession host) {
        for (int attempt = 0; attempt < 200; attempt++) {
            String code = String.format("%04d", random.nextInt(10000));
            Room candidate = new Room(code, hostNameB64, idJudge, betMode, initialChips, isPublic, host);
            if (rooms.putIfAbsent(code, candidate) == null) {
                return candidate;
            }
        }
        return null;
    }

    private void cleanupSession(WsSession session) {
        Room room = session.room;
        if (room == null) return;

        synchronized (room) {
            rooms.remove(room.code, room);
            if (session == room.host) {
                WsSession c = room.client;
                room.client = null;
                if (c != null && c.open) {
                    c.sendText("PEER_LEFT|El anfitrion cerro la partida.");
                    c.sendClose();
                }
            } else if (session == room.client) {
                room.client = null;
                if (room.host.open) {
                    room.host.sendText("PEER_LEFT|El oponente se desconecto.");
                    room.host.sendClose();
                }
            }
        }
    }
}
