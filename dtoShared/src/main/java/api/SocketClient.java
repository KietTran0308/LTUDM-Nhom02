package api;

import dto.OrderDTO;
import dto.SocketAction;
import dto.SocketPacket;
import security.AESGCMUtil;

import javax.crypto.SecretKey;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SocketClient {

    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    private SecretKey sessionKey;
    private volatile boolean connected;
    private Long tableId;

    private final BlockingQueue<SocketPacket> packetQueue = new LinkedBlockingQueue<>();
    private final Object requestLock = new Object();
    private final BlockingQueue<SocketPacket> responseQueue = new LinkedBlockingQueue<>();
    private volatile SocketAction pendingAction;
    private volatile String lastReaderFailure;

    public boolean connect(Long tableId) {
        return connect(ServerConfig.HOST, ServerConfig.PORT, tableId);
    }

    public synchronized boolean connect(String host, int port, Long tableId) {
        if (connected && socket != null && !socket.isClosed()) return true;
        if (socket != null) close();

        this.tableId = tableId;
        this.lastReaderFailure = null;
        Socket newSocket = new Socket();

        try {
            newSocket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            newSocket.setKeepAlive(true);
            newSocket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            this.socket = newSocket;

            HybridClientHandshake.Session handshake = HybridClientHandshake.start(newSocket);
            System.out.println("[TCP] Handshake RSA/AES hoàn tất | " + host + ":" + port);

            newSocket.setSoTimeout(0);
            this.in = handshake.in;
            this.out = handshake.out;
            this.sessionKey = handshake.key;
            this.connected = true;

            Thread reader = new Thread(
                    () -> listenFromServer(newSocket, handshake.in, handshake.key),
                    "client-socket-reader"
            );
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi kết nối hoặc trao đổi khóa RSA: " + e.getMessage());
            close();
            return false;
        }
    }

    private void listenFromServer(Socket activeSocket, DataInputStream activeInput, SecretKey activeSessionKey) {
        try {
            while (connected && socket == activeSocket) {
                byte[] encryptedData = HybridClientHandshake.readFrame(activeInput);
                byte[] decryptedData = AESGCMUtil.decrypt(encryptedData, activeSessionKey);
                String json = new String(decryptedData, StandardCharsets.UTF_8);

                SocketPacket packet = SocketJsonMapper.fromJsonString(json);
                System.out.println("[Server -> Client] action=" + packet.getAction()
                        + " success=" + packet.isSuccess()
                        + " pending=" + pendingAction);

                SocketAction pending = pendingAction;
                if (pending != null && packet.getAction() == pending) {
                    responseQueue.offer(packet);
                } else {
                    packetQueue.offer(packet);
                }
            }
        } catch (Exception e) {
            if (connected) {
                lastReaderFailure = e.getClass().getSimpleName() + ": " + e.getMessage();
                System.err.println("Lỗi nhận hoặc giải mã dữ liệu từ Server: " + lastReaderFailure);
            }
        } finally {
            if (socket == activeSocket) {
                close();
            } else {
                try {
                    activeSocket.close();
                } catch (IOException ignored) { }
            }
        }
    }

    public synchronized boolean sendRequest(SocketPacket packet) {
        if (!connected || out == null || packet == null) return false;
        try {
            String json = SocketJsonMapper.toJsonString(packet);
            byte[] encryptedData = AESGCMUtil.encrypt(json.getBytes(StandardCharsets.UTF_8), sessionKey);
            HybridClientHandshake.writeFrame(out, encryptedData);
            return true;
        } catch (Exception e) {
            System.err.println("Không thể mã hóa/gửi yêu cầu " + packet.getAction() + ": " + e.getMessage());
            close();
            return false;
        }
    }

    public SocketPacket pollPacket() {
        return packetQueue.poll();
    }

    public boolean isConnected() {
        return connected;
    }

    public void bindCustomerTable(Long tableId) {
        this.tableId = tableId;
    }

    public boolean requestMenuAndCategories() {
        return sendRequest(new SocketPacket(SocketAction.GET_MENU_AND_CATEGORIES, tableId, null, null));
    }

    public boolean sendOrderToServer(OrderDTO order, String voucherCode) {
        return sendRequest(new SocketPacket(SocketAction.ORDER_SUBMIT, tableId, order, voucherCode));
    }

    public void requestTableTracking() {
        sendRequest(new SocketPacket(SocketAction.GET_TABLE_TRACKING, tableId, null, null));
    }

    /** Chỉ dành cho các kết nối POS/Admin có một request đồng bộ tại một thời điểm. */
    public SocketPacket requestAndWait(SocketPacket request, long timeoutMs) {
        if (request == null || request.getAction() == null || timeoutMs <= 0) {
            throw new IllegalArgumentException("Yêu cầu hoặc timeout không hợp lệ");
        }
        synchronized (requestLock) {
            responseQueue.clear();
            pendingAction = request.getAction();
            try {
                System.out.println("[Client -> Server] action=" + request.getAction());
                if (!sendRequest(request)) {
                    throw new IllegalStateException("Không gửi được yêu cầu đến server");
                }
                SocketPacket response = responseQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
                if (response == null) {
                    close();
                    throw new IllegalStateException("Server không phản hồi đúng hạn; hãy đăng nhập lại");
                }
                if (!response.isSuccess()) {
                    throw new IllegalStateException(response.getMessage() == null
                            ? "Server từ chối thao tác" : response.getMessage());
                }
                return response;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                close();
                throw new IllegalStateException("Yêu cầu đã bị gián đoạn", e);
            } finally {
                pendingAction = null;
                responseQueue.clear();
            }
        }
    }

    public void logout() {
        sendRequest(new SocketPacket(SocketAction.LOGOUT, null, null, null));
    }

    public synchronized void close() {
        connected = false;
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) { }
        }
        socket = null;
        in = null;
        out = null;
        sessionKey = null;
        tableId = null;
        packetQueue.clear();

        SocketAction waiting = pendingAction;
        if (waiting == null) {
            responseQueue.clear();
        } else if (responseQueue.isEmpty()) {
            String reason = lastReaderFailure == null
                    ? "Mất kết nối tới Server"
                    : "Mất kết nối tới Server: " + lastReaderFailure;
            responseQueue.offer(new SocketPacket(waiting, false, reason, null, null));
        }
        pendingAction = null;
    }
}