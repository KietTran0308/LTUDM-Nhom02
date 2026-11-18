package api;

import dto.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import security.AESGCMUtil;


import javax.crypto.SecretKey;

public class SocketClient {

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


    // Dung chung dung dinh dang frame voi IMG/2 va handshake.
    private void writeFrame(DataOutputStream output, byte[] data) throws IOException {
        HybridClientHandshake.writeFrame(output, data);
    }

    private byte[] readFrame(DataInputStream input) throws IOException {
        return HybridClientHandshake.readFrame(input);
    }

    public boolean connect(Long tableId) {
        return connect(ServerConfig.HOST, ServerConfig.PORT, tableId);
    }

    public synchronized boolean connect(String host, int port, Long tableId) {
        if (connected && socket != null && !socket.isClosed()) return true;
        if (socket != null) close(); // Dọn kết nối cũ trước khi kết nối lại.
        this.tableId = tableId;
        lastReaderFailure = null;
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new java.net.InetSocketAddress(host, port), 3000);
            newSocket.setKeepAlive(true);
            newSocket.setSoTimeout(5000);
            socket = newSocket;

            // Xac thuc RSA fingerprint TRUOC KHI gui AES key.
            // ACK duoc ma hoa AES-GCM de chung minh server giu private key.
            HybridClientHandshake.Session handshake = HybridClientHandshake.start(newSocket);
            System.out.println("[TCP] Handshake RSA/AES hoàn tất | " + host + ":" + port);
            DataInputStream input = handshake.in;
            DataOutputStream output = handshake.out;
            SecretKey aesKey = handshake.key;

            // Handshake thành công, bỏ giới hạn thời gian chờ
            newSocket.setSoTimeout(0);

            // 6. Lưu thông tin kết nối
            socket = newSocket;
            in = input;
            out = output;
            sessionKey = aesKey;

            connected = true;
            Thread reader = new Thread(
                    () -> listenFromServer(newSocket, input, aesKey), "client-socket-reader");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (Exception e) {
            System.err.println("Lỗi kết nối hoặc trao đổi khóa RSA: " + e.getMessage());

            close();

            return false;
        }
    }

    private void listenFromServer(Socket activeSocket, DataInputStream activeInput,  SecretKey activeSessionKey) {
        try {
            while (connected && socket == activeSocket) {
                // 1. Nhận dữ liệu đã mã hóa
                byte[] encryptedData = readFrame(activeInput);

                // 2. Giải mã AES-GCM
                byte[] decryptedData = AESGCMUtil.decrypt(encryptedData, activeSessionKey);

                // 3. Chuyển byte thành JSON
                String json = new String( decryptedData,StandardCharsets.UTF_8);

                // 4. Chuyển JSON thành SocketPacket
                SocketPacket packet = SocketJsonMapper.fromJsonString(json);
                System.out.println("[Server -> Client] action=" + packet.getAction()
                        + " success=" + packet.isSuccess()
                        + " pending=" + pendingAction);

                // 5. Giữ nguyên cơ chế phân loại response và realtime
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
                e.printStackTrace();
            }
        } finally {
            if (socket == activeSocket) {
                close();
            } else {
                try {
                    activeSocket.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public synchronized boolean sendRequest(SocketPacket packet) {
        if (!connected || out == null || packet == null) return false;
        try {
            // Tuyet doi khong log JSON thuc: LOGIN chua mat khau, REGISTER_TABLE chua token.
            String json = SocketJsonMapper.toJsonString(packet);

            byte[] encryptedData = AESGCMUtil.encrypt( json.getBytes(StandardCharsets.UTF_8),sessionKey);

            writeFrame(out, encryptedData);
            return  true;

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

    /**
     * Goi SAU khi Server phan hoi ENTER_CUSTOMER_MODE thanh cong.
     */
    public void bindCustomerTable(Long tableId) {
        this.tableId = tableId;
    }

    /**
     * Tra ve false neu request khong duoc gui (ket noi da dong / loi Socket).
     */
    public boolean requestMenuAndCategories() {
        return sendRequest(new SocketPacket(SocketAction.GET_MENU_AND_CATEGORIES, tableId, null, null));
    }

    public boolean sendOrderToServer(OrderDTO order, String voucherCode) {
        return sendRequest(new SocketPacket(SocketAction.ORDER_SUBMIT, tableId, order, voucherCode));
    }

    public void requestTableTracking() {
        sendRequest(new SocketPacket(SocketAction.GET_TABLE_TRACKING, tableId, null, null));
    }

    public boolean callStaff(String note) {
        return sendRequest(new SocketPacket(SocketAction.CALL_STAFF, tableId, note, null));
    }

    /** Chỉ dành cho các kết nối POS/Admin có một request đồng bộ tại một thời điểm. */
    public SocketPacket requestAndWait(SocketPacket request, long timeoutMs) {
        if (request == null || request.getAction() == null || timeoutMs <= 0)
            throw new IllegalArgumentException("Yêu cầu hoặc timeout không hợp lệ");
        synchronized (requestLock) {
            // Response cũ không được ghép nhầm vào yêu cầu tiếp theo.
            responseQueue.clear();
            pendingAction = request.getAction();
            try {
                System.out.println("[Client -> Server] action=" + request.getAction());
                if (!sendRequest(request))
                    throw new IllegalStateException("Không gửi được yêu cầu đến server");
                SocketPacket response = responseQueue.poll(timeoutMs,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
                if (response == null) {
                    // Không có request-id trong giao thức cũ: đóng kết nối để chống nhầm phản hồi trễ.
                    close();
                    throw new IllegalStateException("Server không phản hồi đúng hạn; hãy đăng nhập lại");
                }
                if (!response.isSuccess())
                    throw new IllegalStateException(response.getMessage() == null
                            ? "Server từ chối thao tác" : response.getMessage());
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
            } catch (IOException ignored) {
            }
        }
        socket = null;
        in = null;
        out = null;
        sessionKey = null;
        tableId = null;
        packetQueue.clear();
        // Nếu luồng đọc bị đóng trong lúc requestAndWait đang đợi, đánh thức nó ngay.
        // Không xóa phản hồi thật đã nhận được.
        SocketAction waiting = pendingAction;
        if (waiting == null) {
            responseQueue.clear();
        } else if (responseQueue.isEmpty()) {
            String reason = lastReaderFailure == null ? "Mất kết nối tới Server"
                    : "Mất kết nối tới Server: " + lastReaderFailure;
            responseQueue.offer(new SocketPacket(waiting, false, reason, null, null));
        }
        pendingAction = null;
    }
}