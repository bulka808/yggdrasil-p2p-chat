package dev.p2pchat;

import org.bouncycastle.operator.OperatorCreationException;
import dev.p2pchat.message.FileMessage;
import dev.p2pchat.message.ImageMessage;
import dev.p2pchat.message.Message;
import dev.p2pchat.message.TextMessage;
import tech.kwik.core.QuicClientConnection;
import tech.kwik.core.QuicConnection;
import tech.kwik.core.QuicStream;
import tech.kwik.core.log.SysOutLogger;
import tech.kwik.core.server.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static dev.p2pchat.InMemoryKeyStore.generateInMemoryKeyStore;

public class P2PNode implements AutoCloseable {

    private final AppConfig config;
    private final StreamHandler handler;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Map<String, QuicConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, Integer> reconnectionAttempts = new ConcurrentHashMap<>();
    private ServerConnector server;

    private final List<P2PNodeListener> listeners = new CopyOnWriteArrayList<>();

    private final static Duration MAX_IDLE_TIMEOUT = Duration.ofSeconds(30);
    private final static String PROTOCOL_NAME = "my-protocol";
    private static final int MAX_RECONNECT_ATTEMPTS = 5;
    private static final int[] RECONNECT_DELAYS = {5, 10, 20, 30, 60};

    public P2PNode(AppConfig config) {
        this.config = config;
        this.handler = new StreamHandler(
                config.imageLimitBytes(),
                config.fileLimitBytes(),
                (long) config.storageLimitMb() << 20,
                config.ownAddress(),
                msg -> {
                    Logger.info("onText callback: from=" + msg.from() + " displayName=" + msg.displayName() + " text=" + msg.text());
                    emit(l -> l.onTextMessage(msg.from(), msg.displayName(), msg.text(), msg.sentAt()));
                },
                (msg, filePath) -> {
                    Logger.info("onImage callback: from=" + msg.from() + " displayName=" + msg.displayName() + " filename=" + msg.filename());
                    emit(l -> l.onImageMessage(msg.from(), msg.displayName(), msg.image(), msg.filename(), filePath, msg.sentAt()));
                },
                (msg, filePath) -> {
                    Logger.info("onFile callback: from=" + msg.from() + " displayName=" + msg.displayName() + " filename=" + msg.filename());
                    emit(l -> l.onFileMessage(msg.from(), msg.displayName(), msg.file(), msg.filename(), msg.extension(), filePath, msg.sentAt()));
                }
        );
    }

    private Message withFrom(Message msg, String from) {
        return switch (msg) {
            case TextMessage t -> new TextMessage(from, t.to(), t.displayName(), t.sentAt(), t.text());
            case ImageMessage i -> new ImageMessage(from, i.to(), i.displayName(), i.image(), i.filename(), i.sentAt());
            case FileMessage f -> new FileMessage(from, f.to(), f.displayName(), f.file(), f.filename(), f.extension(), f.sentAt());
            default -> msg;
        };
    }

    public void addListener(P2PNodeListener l) { listeners.add(l); }
    public void removeListener(P2PNodeListener l) { listeners.remove(l); }

    public boolean isConnected(String peerId) {
        QuicConnection conn = connections.get(peerId);
        return conn != null;
    }

    public void emit(Consumer<P2PNodeListener> fn) {
        Logger.debug("emit() called, listeners count: " + listeners.size());
        for (var l : listeners) {
            Logger.debug("emit() -> notifying listener: " + l.getClass().getSimpleName());
            fn.accept(l);
        }
    }

    public void sendMessage(Message msg) throws IOException {
        if (msg.from().isEmpty()) {
            msg = withFrom(msg, config.ownAddress());
        }
        Logger.debug("sendMessage: to=" + msg.to() + " from=" + msg.from() + " type=" + msg.getClass().getSimpleName());
        sendMessage(msg, config.serverPort());
    }

    public void sendMessage(Message msg, int port) throws IOException {
        Message outgoing = msg.from().isEmpty() ? withFrom(msg, config.ownAddress()) : msg;
        byte[] data = outgoing.toRaw().pack();
        Logger.debug("sendMessage: packed " + data.length + " bytes");
        QuicConnection conn = connections.get(outgoing.to());

        if (conn == null) {
            Logger.debug("No existing connection to " + outgoing.to() + ", connecting...");
            conn = connectWithRetry(outgoing.to(), port);
        }

        QuicStream stream;
        try {
            stream = conn.createStream(true);
        } catch (Exception e) {
            Logger.error("Stream creation failed, reconnecting: " + e.getMessage());
            connections.remove(outgoing.to());
            conn = connectWithRetry(outgoing.to(), port);
            stream = conn.createStream(true);
        }
        OutputStream out = stream.getOutputStream();
        out.write(data);
        out.flush();
        out.close();
        byte[] ackData = stream.getInputStream().readAllBytes();
        if (ackData.length > 0) {
            try {
                dev.p2pchat.message.RawMessage ackMsg = StreamHandler.unpack(ackData);
                if (ackMsg.typeId() == 4) {
                    var envelope = StreamHandler.extractEnvelope(ackMsg);
                    long timestamp = (long) envelope.getOrDefault("timestamp", 0L);
                    Logger.debug("ACK received for timestamp=" + timestamp);
                    emit(l -> l.onMessageDelivered(timestamp));
                }
            } catch (Exception e) {
                Logger.debug("ACK parse skipped: " + e.getMessage());
            }
        }
        Logger.debug("Message sent to " + outgoing.to() + " (" + data.length + " bytes)");
        emit(l -> l.onMessageSent(outgoing.to(), data, true));
    }

    public void sendMessageAsync(Message msg) {
        new Thread(() -> {
            try {
                sendMessage(msg);
            } catch (IOException e) {
                Logger.error("Send failed: " + e.getMessage());
                emit(l -> l.onMessageSent(msg.to(), new byte[0], false));
            }
        }).start();
    }

    private QuicClientConnection connectToPeer(String peerId, int port) throws IOException {
        Logger.debug("Connecting to " + peerId + ":" + port);
        QuicClientConnection conn = QuicClientConnection.newBuilder()
                .uri(URI.create("quic://[" + peerId + "]"))
                .port(port)
                .maxIdleTimeout(MAX_IDLE_TIMEOUT)
                .applicationProtocol(PROTOCOL_NAME)
                .noServerCertificateCheck()
                .build();
        conn.setPeerInitiatedStreamCallback(stream -> {
            Logger.debug("Peer-initiated stream callback fired for " + peerId);
            executor.submit(() -> handler.handle(stream, peerId));
        });
        conn.setConnectionListener(connectionTerminatedEvent -> {
            connections.remove(peerId, conn);
            reconnectionAttempts.remove(peerId);
            Logger.info("Peer disconnected: " + peerId);
            emit(l -> l.onPeerDisconnected(peerId));
            scheduleReconnection(peerId);
        });
        conn.connect();
        conn.keepAlive(10);
        addConnection(peerId, conn);
        Logger.info("Connected to " + peerId);
        emit(l -> l.onPeerConnected(peerId));
        return conn;
    }

    private QuicConnection connectWithRetry(String peerId, int port) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return connectToPeer(peerId, port);
            } catch (IOException e) {
                Logger.error("Connection attempt " + (attempt + 1) + " failed for " + peerId + ": " + e.getMessage());
                last = e;
            }
        }
        throw last;
    }

    public void startServer(int port) throws CertificateException, NoSuchAlgorithmException,
            KeyStoreException, IOException, OperatorCreationException {

        KeyStore keyStore = generateInMemoryKeyStore("localhost");
        ServerConnectionConfig config = ServerConnectionConfig.builder()
                .maxOpenPeerInitiatedBidirectionalStreams(100)
                .maxIdleTimeout((int) MAX_IDLE_TIMEOUT.toMillis())
                .build();

        this.server = ServerConnector.builder()
                .withPort(port)
                .withKeyStore(keyStore, "servercert", "secret".toCharArray())
                .withConfiguration(config)
                .withLogger(new SysOutLogger())
                .build();

        server.registerApplicationProtocol(PROTOCOL_NAME, new ServerFactory());
        server.start();
    }

    public void stop() {
        if (server != null) {
            try {
                server.close();
            } catch (Exception ignored) {
            }
            server = null;
        }

        for (QuicConnection conn : connections.values()) {
            try {
                conn.close();
            } catch (Exception ignored) {
            }
        }
        connections.clear();

        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws Exception {
        stop();
    }

    class ServerFactory implements ApplicationProtocolConnectionFactory {
        @Override
        public ApplicationProtocolConnection createConnection(String protocol, QuicConnection quicConnection) {
            String peerId = ((ServerConnection) quicConnection)
                    .getInitialRemoteAddress()
                    .getAddress()
                    .getHostAddress();
            Logger.debug("ServerFactory: new connection from " + peerId + " protocol=" + protocol);
            quicConnection.setConnectionListener(
                    connectionTerminatedEvent -> {
                        connections.remove(peerId, quicConnection);
                        emit(l -> l.onPeerDisconnected(peerId));
                    }
            );
            addConnection(peerId, quicConnection);
            emit(l -> l.onPeerConnected(peerId));

            return new ApplicationProtocolConnection() {
                @Override
                public void acceptPeerInitiatedStream(QuicStream stream) {
                    Logger.debug("Accepted peer-initiated stream from " + peerId);
                    executor.submit(() -> handler.handle(stream, peerId));
                }
            };
        }
    }

    private void addConnection(String peerId, QuicConnection connection) {
        QuicConnection old = connections.put(peerId, connection);
        if (old != null && old != connection) {
            executor.submit(() -> {
                try {
                    old.close();
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void scheduleReconnection(String peerId) {
        int attempts = reconnectionAttempts.getOrDefault(peerId, 0);
        if (attempts >= MAX_RECONNECT_ATTEMPTS) {
            Logger.info("Max reconnection attempts reached for " + peerId);
            reconnectionAttempts.remove(peerId);
            return;
        }

        int delay = RECONNECT_DELAYS[Math.min(attempts, RECONNECT_DELAYS.length - 1)];
        reconnectionAttempts.put(peerId, attempts + 1);

        Logger.debug("Scheduling reconnection to " + peerId + " in " + delay + "s (attempt " + (attempts + 1) + "/" + MAX_RECONNECT_ATTEMPTS + ")");
        scheduler.schedule(() -> {
            try {
                if (!connections.containsKey(peerId)) {
                    Logger.info("Reconnecting to " + peerId + "...");
                    connectToPeer(peerId, config.serverPort());
                }
            } catch (Exception e) {
                Logger.error("Reconnection to " + peerId + " failed: " + e.getMessage());
                scheduleReconnection(peerId);
            }
        }, delay, TimeUnit.SECONDS);
    }
}
