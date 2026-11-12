package api;

import dto.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import security.AESGCMUtil;
import security.RSAKeyExchange;


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


    private static final int MAX_PACKET_SIZE = 1024 * 1024;
    private void writeFrame(DataOutputStream output, byte[] data)
            throws IOException {

        if (data == null || data.length <= 0 || data.length > MAX_PACKET_SIZE) {
            throw new IOException("Kích thước gói tin gửi đi không hợp lệ");
        }

        output.writeInt(data.length);
        output.write(data);
        output.flush();
    }
    private byte[] readFrame(DataInputStream input) throws IOException {
        int length = input.readInt();

        if (length <= 0 || length > MAX_PACKET_SIZE) {
            throw new IOException("Kích thước gói tin không hợp lệ");
        }
        byte[] data = new byte[length];
        input.readFully(data);
        return data;
    }
    private void writeFrame(DataOutputStream output, String data) throws IOException {
        writeFrame(output, data.getBytes(StandardCharsets.UTF_8));
    }

    private String readFrameAsString(DataInputStream input) throws IOException {
        return new String(readFrame(input), StandardCharsets.UTF_8);
    }

    


    public synchronized boolean connect(String host, int port, Long tableId) {
        if (connected && socket != null && !socket.isClosed()) return true;
        if (socket != null) close(); // Dọn kết nối cũ trước khi kết nối lại.
        this.tableId = tableId;
        Socket newSocket = new Socket();
        try {
            newSocket.connect(new java.net.InetSocketAddress(host, port), 3000);
            newSocket.setKeepAlive(true);
            newSocket.setSoTimeout(5000);
            socket = newSocket;

            DataInputStream input = new DataInputStream(new BufferedInputStream(newSocket.getInputStream()));
            DataOutputStream output = new DataOutputStream(new BufferedOutputStream(newSocket.getOutputStream()));
            
            // 1/ Nhận Public key RSA từ Server
            String publicKeyString = readFrameAsString(input);
            // 2. Chuyển chuỗi Base64 thành PublicKey
            PublicKey serverPublicKey = RSAKeyExchange.stringToPublicKey(publicKeyString);
            // 3. Tạo khóa AES
            SecretKey aesKey = RSAKeyExchange.generateAESKey();
            // 4. Mã hóa khóa AES bằng Public Key RSA
            String encryptedAESKey = RSAKeyExchange.encryptAESKey(aesKey, serverPublicKey);

            // 5. Gửi khóa AES đã mã hóa cho Server
            writeFrame(output, encryptedAESKey);
            // Chờ Server xác nhận handshake
            String handshakeResponse = readFrameAsString(input);

            if (!"OK".equals(handshakeResponse)) {
                throw new IOException("Server xác nhận handshake thất bại");
            }

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
                System.err.println("Lỗi nhận hoặc giải mã dữ liệu từ Server: " + e.getMessage());
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
            System.err.println("Không thể mã háo/ gửi yêu cầu " + packet.getAction() + ": " + e.getMessage());
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
        return sendRequest(new SocketPacket(SocketAction.PLACE_ORDER, tableId, order, voucherCode));
    }

    public void requestTableTracking() {
        sendRequest(new SocketPacket(SocketAction.GET_TABLE_TRACKING, tableId, null, null));
    }

    public boolean callStaff(String note) {
        return sendRequest(new SocketPacket(SocketAction.CALL_STAFF, tableId, note, null));
    }

    public boolean registerTable(Long tableId, String token) {
        if (tableId == null || tableId <= 0 || token == null || token.isBlank()) return false;
        this.tableId = tableId;
        return sendRequest(new SocketPacket(SocketAction.REGISTER_TABLE, tableId, token, null));
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
        responseQueue.clear();
        pendingAction = null;
    }
}
