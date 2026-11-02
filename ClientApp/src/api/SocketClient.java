package api;

import dto.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public class SocketClient {

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isConnected = false;
    private Long tableId;

    private Consumer<SocketPacket> onServerPacketReceived;

    public boolean connect(String host, int port, Long tableId, Consumer<SocketPacket> onServerPacketReceived) {
        this.tableId = tableId;
        this.onServerPacketReceived = onServerPacketReceived;

        try {
            socket = new Socket(host, port);
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            isConnected = true;

            Thread listenerThread = new Thread(this::listenFromServer);
            listenerThread.setDaemon(true);
            listenerThread.start();

            if (tableId != null && tableId > 0) {
                sendRequest(new SocketPacket(SocketAction.REGISTER_TABLE, tableId, null, null));
                requestMenuAndCategories();
            }

            return true;
        } catch (IOException e) {
            System.err.println("[SocketClient - JSON] Không kết nối được TCP Server (" + host + ":" + port + ").");
            isConnected = false;
            return false;
        }
    }

    private void listenFromServer() {
        try {
            String rawJsonLine;
            while (isConnected && !socket.isClosed() && (rawJsonLine = in.readLine()) != null) {
                System.out.println("[Client <- Server JSON]: " + rawJsonLine);

                // Giải mã chuỗi JSON nhận được bằng org.json thành SocketPacket
                SocketPacket packet = SocketJsonMapper.fromJsonString(rawJsonLine);
                if (onServerPacketReceived != null) {
                    onServerPacketReceived.accept(packet);
                }
            }
        } catch (Exception e) {
            isConnected = false;
            System.err.println("[SocketClient - JSON] Mất kết nối tới Server: " + e.getMessage());
        }
    }

    /**
     * Đóng gói SocketPacket thành chuỗi JSON bằng org.json và gửi qua TCP Socket
     */
    public synchronized void sendRequest(SocketPacket packet) {
        if (!isConnected || out == null) return;
        try {
            String jsonPayload = SocketJsonMapper.toJsonString(packet);
            System.out.println("[Client -> Server JSON]: " + jsonPayload);
            out.println(jsonPayload); // Gửi 1 dòng JSON kết thúc bằng \n
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void requestMenuAndCategories() {
        sendRequest(new SocketPacket(SocketAction.GET_MENU_AND_CATEGORIES, tableId, null, null));
    }

    // 2. Gửi đối tượng OrderDTO và mã Voucher xuống Server
    public void sendOrderToServer(OrderDTO order, String voucherCode) {
        sendRequest(new SocketPacket(SocketAction.PLACE_ORDER, tableId, order, voucherCode));
    }

    // 3. Yêu cầu làm mới bảng Theo dõi đơn hàng của bàn
    public void requestTableTracking() {
        sendRequest(new SocketPacket(SocketAction.GET_TABLE_TRACKING, tableId, null, null));
    }

    // 4. Bấm chuông gọi nhân viên tại bàn
    public void callStaff(String note) {
        sendRequest(new SocketPacket(SocketAction.CALL_STAFF, tableId, note, null));
    }

    public boolean isConnected() {
        return isConnected;
    }
}