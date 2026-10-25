package api;

import dto.OrderStatus;
import dto.SocketAction;
import dto.SocketPacket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SocketServer {
    private static final int PORT = 8888;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    // ThreadPool giúp quản lý hàng chục Tablet bàn khách kết nối cùng lúc mà không tràn bộ nhớ
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    // Lưu trữ kết nối đang hoạt động theo table_id: Key = tableId, Value = ClientHandler
    private static final Map<Long, ClientHandler> activeTables = new ConcurrentHashMap<>();

    // Callback để thông báo cho Giao diện Bếp/Admin trên Server khi có đơn mới hoặc khách gọi
    private Consumer<SocketPacket> onKitchenNotification;

    public SocketServer(Consumer<SocketPacket> onKitchenNotification) {
        this.onKitchenNotification = onKitchenNotification;
    }

    public void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(PORT);
                isRunning = true;
                System.out.println("[TCP Server] Đang lắng nghe tại cổng " + PORT + "...");

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("[TCP Server] Kết nối mới từ: " + clientSocket.getInetAddress());
                    ClientHandler handler = new ClientHandler(clientSocket, onKitchenNotification);
                    threadPool.execute(handler);
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("[TCP Server] Lỗi Server: " + e.getMessage());
                }
            }
        }).start();
    }

    /**
     * Đăng ký Tablet của bàn vào danh sách quản lý trực tuyến
     */
    public static void registerTableSession(Long tableId, ClientHandler handler) {
        if (tableId != null) {
            activeTables.put(tableId, handler);
            System.out.println("[TCP Server] Đã gắn kết nối cho Bàn số: " + tableId);
        }
    }

    public static void removeTableSession(Long tableId) {
        if (tableId != null) {
            activeTables.remove(tableId);
            System.out.println("[TCP Server] Bàn số " + tableId + " đã ngắt kết nối.");
        }
    }

    public static void notifyTableCleared(Long tableId) {
        ClientHandler handler = activeTables.get(tableId);
        if (handler != null) {
            handler.sendPacket(new SocketPacket(
                    SocketAction.TABLE_CLEARED_RESET, true,
                    "Cảm ơn quý khách đã dùng bữa tại Haidilao!", null, null
            ));
        }
    }

    /**
     * Dành cho Màn hình Bếp (Server): Đẩy thông báo trạng thái món ăn thời gian thực về đúng Tablet của bàn
     */
    public static void pushStatusToTable(Long tableId, Long orderItemId, OrderStatus newStatus) {
        ClientHandler handler = activeTables.get(tableId);
        if (handler != null) {
            SocketPacket pushPacket = new SocketPacket(
                    SocketAction.KITCHEN_UPDATE_STATUS,
                    true,
                    "Bếp vừa cập nhật trạng thái món #" + orderItemId + " sang: " + newStatus.name(),
                    orderItemId,
                    newStatus
            );
            handler.sendPacket(pushPacket);
        }
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null) serverSocket.close();
            threadPool.shutdownNow();
        } catch (IOException ignored) {}
    }


}