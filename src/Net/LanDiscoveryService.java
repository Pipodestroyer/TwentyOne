package Net;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class LanDiscoveryService {
    public static final int DISCOVERY_PORT = 44455;
    private static final String PREFIX_HOST = "TOP21_HOST|";
    private static final String PREFIX_DISCOVER = "TOP21_DISCOVER";
    private static final long HOST_TTL_MS = 5000;

    public static class DiscoveredHost {
        public final String hostAddress;
        public final int tcpPort;
        public final String hostName;
        public final byte idJudge;
        public final byte betMode;
        public final byte initialChips;
        public volatile long lastSeenMs;

        public DiscoveredHost(String hostAddress, int tcpPort, String hostName,
                              byte idJudge, byte betMode, byte initialChips, long lastSeenMs) {
            this.hostAddress = hostAddress;
            this.tcpPort = tcpPort;
            this.hostName = hostName;
            this.idJudge = idJudge;
            this.betMode = betMode;
            this.initialChips = initialChips;
            this.lastSeenMs = lastSeenMs;
        }

        public String key() {
            return hostAddress + ":" + tcpPort;
        }
    }

    private volatile boolean running;
    private DatagramSocket listenerSocket;
    private Thread broadcasterThread;
    private Thread listenerThread;
    private Thread scannerThread;
    private final Map<String, DiscoveredHost> discoveredHosts = new ConcurrentHashMap<>();

    public synchronized void startHostingBroadcast(GameRules rules, int tcpPort) {
        stop();
        running = true;

        String encodedName = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rules.hostName.getBytes(StandardCharsets.UTF_8));
        String payload = PREFIX_HOST + encodedName + "|" + tcpPort + "|" +
                rules.idJudge + "|" + rules.betMode + "|" + rules.initialChips;
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        broadcasterThread = new Thread(() -> {
            try (DatagramSocket sendSocket = new DatagramSocket()) {
                sendSocket.setBroadcast(true);
                while (running) {
                    sendToAllBroadcasts(sendSocket, payloadBytes);
                    Thread.sleep(1200);
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (IOException _) {
                // Ignore transient network errors during host broadcast
            }
        }, "lan-host-broadcaster");
        broadcasterThread.setDaemon(true);
        broadcasterThread.start();

        // Also respond immediately when a scanner sends TOP21_DISCOVER
        listenerThread = new Thread(() -> {
            try (DatagramSocket recvSocket = createReusableUdpSocket()) {
                listenerSocket = recvSocket;
                recvSocket.setSoTimeout(800);
                byte[] buf = new byte[512];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        recvSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        if (PREFIX_DISCOVER.equals(msg)) {
                            DatagramPacket reply = new DatagramPacket(
                                    payloadBytes, payloadBytes.length, packet.getAddress(), packet.getPort());
                            recvSocket.send(reply);
                        }
                    } catch (SocketTimeoutException _) {
                        // Poll running flag
                    }
                }
            } catch (IOException _) {
                // Listener port already in use or closed; periodic broadcaster still runs
            } finally {
                listenerSocket = null;
            }
        }, "lan-host-responder");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public synchronized void startScanning(Consumer<List<DiscoveredHost>> onHostsUpdated) {
        stop();
        running = true;
        discoveredHosts.clear();

        listenerThread = new Thread(() -> {
            try (DatagramSocket recvSocket = createReusableUdpSocket()) {
                listenerSocket = recvSocket;
                recvSocket.setSoTimeout(700);
                byte[] buf = new byte[1024];
                while (running) {
                    try {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        recvSocket.receive(packet);
                        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                        if (msg.startsWith(PREFIX_HOST)) {
                            parseAndStoreHost(packet.getAddress().getHostAddress(), msg);
                            notifyHosts(onHostsUpdated);
                        }
                    } catch (SocketTimeoutException _) {
                        long now = System.currentTimeMillis();
                        boolean removed = discoveredHosts.entrySet()
                                .removeIf(e -> now - e.getValue().lastSeenMs > HOST_TTL_MS);
                        if (removed) {
                            notifyHosts(onHostsUpdated);
                        }
                    }
                }
            } catch (IOException _) {
                // Socket closed or unavailable
            } finally {
                listenerSocket = null;
            }
        }, "lan-scanner-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();

        scannerThread = new Thread(() -> {
            byte[] discoverBytes = PREFIX_DISCOVER.getBytes(StandardCharsets.UTF_8);
            try (DatagramSocket sendSocket = new DatagramSocket()) {
                sendSocket.setBroadcast(true);
                sendSocket.setSoTimeout(500);
                while (running) {
                    sendToAllBroadcasts(sendSocket, discoverBytes);
                    // Also read direct unicast replies on sendSocket for ~700ms
                    long until = System.currentTimeMillis() + 700;
                    byte[] buf = new byte[1024];
                    while (running && System.currentTimeMillis() < until) {
                        try {
                            DatagramPacket reply = new DatagramPacket(buf, buf.length);
                            sendSocket.receive(reply);
                            String msg = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8);
                            if (msg.startsWith(PREFIX_HOST)) {
                                parseAndStoreHost(reply.getAddress().getHostAddress(), msg);
                                notifyHosts(onHostsUpdated);
                            }
                        } catch (SocketTimeoutException _) {
                            break;
                        }
                    }
                    long now = System.currentTimeMillis();
                    discoveredHosts.entrySet().removeIf(e -> now - e.getValue().lastSeenMs > HOST_TTL_MS);
                    notifyHosts(onHostsUpdated);
                    Thread.sleep(800);
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (IOException _) {
                // Ignore transient errors
            }
        }, "lan-scanner-pinger");
        scannerThread.setDaemon(true);
        scannerThread.start();
    }

    private DatagramSocket createReusableUdpSocket() throws IOException {
        DatagramSocket socket = new DatagramSocket(null);
        socket.setReuseAddress(true);
        socket.setBroadcast(true);
        socket.bind(new InetSocketAddress(DISCOVERY_PORT));
        return socket;
    }

    private void sendToAllBroadcasts(DatagramSocket socket, byte[] data) {
        Set<InetAddress> targets = new LinkedHashSet<>();
        try {
            targets.add(InetAddress.getByName("255.255.255.255"));
            targets.add(InetAddress.getLoopbackAddress());
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress bcast = ia.getBroadcast();
                    if (bcast != null) {
                        targets.add(bcast);
                    }
                }
            }
        } catch (IOException _) {
            // Best effort
        }

        for (InetAddress target : targets) {
            try {
                DatagramPacket packet = new DatagramPacket(data, data.length, target, DISCOVERY_PORT);
                socket.send(packet);
            } catch (IOException _) {
                // Ignore unreachable interface
            }
        }
    }

    private void parseAndStoreHost(String address, String msg) {
        try {
            String[] parts = msg.split("\\|");
            if (parts.length < 6) return;
            String hostName = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            int tcpPort = Integer.parseInt(parts[2]);
            byte idJudge = Byte.parseByte(parts[3]);
            byte betMode = Byte.parseByte(parts[4]);
            byte initialChips = Byte.parseByte(parts[5]);
            DiscoveredHost host = new DiscoveredHost(
                    address, tcpPort, hostName, idJudge, betMode, initialChips, System.currentTimeMillis());
            discoveredHosts.put(host.key(), host);
        } catch (Exception _) {
            // Malformed packet ignored
        }
    }

    private void notifyHosts(Consumer<List<DiscoveredHost>> callback) {
        if (callback != null) {
            callback.accept(new ArrayList<>(discoveredHosts.values()));
        }
    }

    public synchronized void stop() {
        running = false;
        if (listenerSocket != null) {
            listenerSocket.close();
            listenerSocket = null;
        }
        if (broadcasterThread != null) {
            broadcasterThread.interrupt();
            broadcasterThread = null;
        }
        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
        if (scannerThread != null) {
            scannerThread.interrupt();
            scannerThread = null;
        }
    }
}
