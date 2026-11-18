package com.kyc.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Relais local vers Neon quand le port 5432 ne termine pas le SSL Postgres.
 * Le client JDBC parle en clair à 127.0.0.1 ; le relais porte les mêmes octets
 * dans un WebSocket TLS sur le port 443 ({@code wss://hôte/v2}).
 */
final class NeonLocalBridge {

    private static final Pattern HOST = Pattern.compile("^jdbc:postgresql://([^/:?]+)");
    private static final byte[] SSL_REQUEST = {0, 0, 0, 8, 4, (byte) 210, 22, 47};
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static volatile NeonLocalBridge running;

    private final String host;
    private final int port;
    private final ServerSocket server;

    private NeonLocalBridge(String host, int port, ServerSocket server) {
        this.host = host;
        this.port = port;
        this.server = server;
    }

    static String hostOf(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = HOST.matcher(url);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String localJdbcUrl(String original, int port) {
        int scheme = original.indexOf("://");
        int path = original.indexOf('/', scheme + 3);
        if (scheme < 0 || path < 0) {
            throw new IllegalArgumentException("URL JDBC illisible");
        }
        String rest = original.substring(path + 1);
        int queryAt = rest.indexOf('?');
        String database = queryAt < 0 ? rest : rest.substring(0, queryAt);
        String query = queryAt < 0 ? "" : rest.substring(queryAt + 1);
        StringBuilder kept = new StringBuilder();
        if (!query.isEmpty()) {
            for (String part : query.split("&")) {
                if (part.isEmpty()) {
                    continue;
                }
                String name = part.split("=", 2)[0];
                if (name.equals("sslmode") || name.equals("ssl")
                        || name.equals("channelBinding") || name.equals("channel_binding")) {
                    continue;
                }
                if (kept.length() > 0) {
                    kept.append('&');
                }
                kept.append(part);
            }
        }
        if (kept.length() > 0) {
            kept.append('&');
        }
        kept.append("sslmode=disable&gssEncMode=disable");
        return "jdbc:postgresql://127.0.0.1:" + port + "/" + database + "?" + kept;
    }

    /** Vrai si le serveur répond l'octet {@code S} à une demande SSL Postgres. */
    static boolean directSslReady(String host) {
        if (!host.matches("[A-Za-z0-9.-]+")) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, 5432), 4000);
            socket.setSoTimeout(4000);
            OutputStream out = socket.getOutputStream();
            out.write(SSL_REQUEST);
            out.flush();
            return socket.getInputStream().read() == 'S';
        } catch (IOException ex) {
            return false;
        }
    }

    static synchronized int ensureStarted(String host) {
        if (!host.matches("[A-Za-z0-9.-]+") || !host.endsWith(".neon.tech")) {
            throw new IllegalArgumentException("Hôte Neon inattendu");
        }
        NeonLocalBridge current = running;
        if (current != null && current.host.equals(host) && !current.server.isClosed()) {
            return current.port;
        }
        try {
            ServerSocket server = new ServerSocket();
            server.bind(new InetSocketAddress("127.0.0.1", 0));
            NeonLocalBridge bridge = new NeonLocalBridge(host, server.getLocalPort(), server);
            Thread acceptor = new Thread(bridge::acceptLoop, "neon-bridge");
            acceptor.setDaemon(true);
            acceptor.start();
            running = bridge;
            return bridge.port;
        } catch (IOException ex) {
            throw new IllegalStateException("Pont Neon local impossible à ouvrir", ex);
        }
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket socket = server.accept();
                Thread pump = new Thread(() -> pipe(socket), "neon-bridge-conn");
                pump.setDaemon(true);
                pump.start();
            } catch (IOException ex) {
                if (!server.isClosed()) {
                    System.err.println("Neon pont: accept interrompu");
                }
                return;
            }
        }
    }

    private void pipe(Socket socket) {
        WebSocket webSocket = null;
        try {
            socket.setTcpNoDelay(true);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            webSocket = HTTP.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(15))
                    .buildAsync(URI.create("wss://" + host + "/v2"), new Pump(socket, out))
                    .join();
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0) {
                byte[] copy = Arrays.copyOf(buf, n);
                webSocket.sendBinary(ByteBuffer.wrap(copy), true).join();
            }
        } catch (Exception ex) {
            // fin de session JDBC ou coupure du WebSocket
        } finally {
            if (webSocket != null) {
                try {
                    webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                            .orTimeout(2, TimeUnit.SECONDS)
                            .exceptionally(error -> null)
                            .join();
                } catch (Exception ignored) {
                    webSocket.abort();
                }
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // déjà fermé
            }
        }
    }

    private static final class Pump implements WebSocket.Listener {
        private final Socket socket;
        private final OutputStream out;

        private Pump(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
            try {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                synchronized (out) {
                    out.write(bytes);
                    out.flush();
                }
            } catch (IOException ex) {
                webSocket.abort();
                return null;
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            System.err.println("Neon pont: message texte inattendu");
            webSocket.abort();
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            closeSocket();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            closeSocket();
        }

        private void closeSocket() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // déjà fermé
            }
        }
    }
}
