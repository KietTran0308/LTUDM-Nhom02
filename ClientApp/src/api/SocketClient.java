package api;

import dto.*;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SocketClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private volatile boolean connected;
    private Long tableId;
    private final BlockingQueue<SocketPacket> packetQueue = new LinkedBlockingQueue<>();

    private final Object requestLock = new Object();
    private final BlockingQueue<SocketPacket> responseQueue = new LinkedBlockingQueue<>();
    private volatile SocketAction pendingAction;

    public synchronized boolean connect(String host, int port, Long tableId) {
        if (connected && socket != null && !socket.isClosed()) return true;
        if (socket != null) close(); // Dọn kết nối cũ trước khi kết nối lại.
        this.tableId = tableId;
        try {
            Socket s = new Socket();
            s.connect(new java.net.InetSocketAddress(host, port), 3000);
            s.setKeepAlive(true);
            socket = s;
            out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            connected = true;
            BufferedReader activeInput = in;

            Thread reader = new Thread(
                    () -> listenFromServer(s, activeInput), "client-socket-reader");
            reader.setDaemon(true);
            reader.start();
            return true;
        } catch (IOException e) {
            System.err.println("Khong ket noi TCP " + host + ":" + port + " - " + e.getMessage());
            close();
            return false;
        }
    }

    private void listenFromServer(Socket activeSocket, BufferedReader activeInput) {
        try {
            String line;
            while (connected && socket == activeSocket
                    && (line = activeInput.readLine()) != null) {
                try {
                    SocketPacket packet = SocketJsonMapper.fromJsonString(line);
                    SocketAction pending = pendingAction;
                    if (pending != null && packet.getAction() == pending) responseQueue.offer(packet);
                    else packetQueue.offer(packet);
                } catch (RuntimeException badPacket) {
                    System.err.println("Goi tin JSON khong hop le: " + badPacket.getMessage());
                }
            }
        } catch (IOException e) {
            if (connected) System.err.println("Mat ket noi Server: " + e.getMessage());
        } finally {
            // Không cho thread cũ vô tình đánh dấu kết nối mới là đã ngắt.
            if (socket == activeSocket) {
                connected = false;
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
            out.println(SocketJsonMapper.toJsonString(packet));
            if (out.checkError()) {
                connected = false;
                System.err.println("Gui yeu cau that bai: " + packet.getAction());
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            System.err.println("Khong the ma hoa yeu cau " + packet.getAction() + ": " + e.getMessage());
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
        if (socket != null) try {
            socket.close();
        } catch (IOException ignored) {
        }
        if (out != null) out.close();
        if (in != null) try {
            in.close();
        } catch (IOException ignored) {
        }
        socket = null;
        in = null;
        out = null;
        tableId = null;
        packetQueue.clear();
        responseQueue.clear();
        pendingAction = null;
    }
}
