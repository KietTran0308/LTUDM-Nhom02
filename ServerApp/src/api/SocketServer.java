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

public class SocketServer {
    private static final int PORT = 8888;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    // ThreadPool giúp quản lý hàng chục Tablet bàn khách kết nối cùng lúc mà không tràn bộ nhớ
    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    // Lưu trữ kết nối đang hoạt động theo table_id: Key = tableId, Value = ClientHandler

    private static final Map<Long, java.util.Set<ClientHandler>> activeTables = new ConcurrentHashMap<>();
    private static final java.util.Set<ClientHandler> kitchenClients = ConcurrentHashMap.newKeySet();
    private static final java.util.Set<ClientHandler> staffClients = ConcurrentHashMap.newKeySet();

    public SocketServer(Object o) { }

    public static void registerKitchen(ClientHandler client) { kitchenClients.add(client); }
    public static void registerStaff(ClientHandler client) { staffClients.add(client); }
    public static void removeConnection(ClientHandler client) {
        kitchenClients.remove(client);
        staffClients.remove(client);
        for (java.util.Set<ClientHandler> handlers : activeTables.values()) handlers.remove(client);
        activeTables.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    public static void broadcastKitchenQueue(java.util.List<dto.OrderDTO> orders) {
        for (ClientHandler client : kitchenClients) {
            client.sendPacket(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, true,
                    "Hàng đợi bếp đã thay đổi", orders, null));
        }
    }

    public static void notifyStaff(Long tableId, String note) {
        for (ClientHandler client : staffClients) {
            client.sendPacket(new SocketPacket(SocketAction.CALL_STAFF, true,
                    "Bàn #" + tableId + ": " + note, note, null));
        }
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
                    ClientHandler handler = new ClientHandler(clientSocket);
                    threadPool.execute(handler);
                }
            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("[TCP Server] Lỗi Server: " + e.getMessage());
                }
            }
        }).start();
    }

    public static void registerTableSession(Long tableId, ClientHandler handler) {
        if (tableId != null && handler != null) {
            activeTables.computeIfAbsent(tableId, k -> ConcurrentHashMap.newKeySet()).add(handler);
        }
    }

    public static void notifyTableCleared(Long tableId) {
        java.util.Set<ClientHandler> clients = activeTables.remove(tableId);
        if (clients == null) return;
        for (ClientHandler client : clients) {
            client.sendPacket(new SocketPacket(SocketAction.TABLE_CLEARED_RESET, true,
                    "Cảm ơn quý khách đã dùng bữa tại An Nhiên!", null, null));
        }
    }

    public static void pushStatusToTable(Long tableId, Long orderItemId, OrderStatus newStatus) {
        java.util.Set<ClientHandler> clients = activeTables.get(tableId);
        if (clients == null) return;
        for (ClientHandler client : clients) {
            client.sendPacket(new SocketPacket(SocketAction.KITCHEN_UPDATE_STATUS, true,
                    "Bếp cập nhật trạng thái món #" + orderItemId,
                    orderItemId, newStatus));
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