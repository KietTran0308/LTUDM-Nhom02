package api;

import bus.OrderBUS;
import dto.OrderDTO;
import dto.SocketAction;
import dto.SocketPacket;
import dto.dbEnums;
import security.RSAKeyManager;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketServer {

    private static final int PORT = 8888;
    // 1 phiên bàn chỉ cho phép duy nhất 1 iPad phục vụ
    private static final Map<Long, ClientHandler> sessionClients = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Object> sessionLocks = new ConcurrentHashMap<>();
    private static final Set<ClientHandler> kitchenClients = ConcurrentHashMap.newKeySet();
    private static final Set<ClientHandler> staffClients = ConcurrentHashMap.newKeySet();
    private static final Set<ClientHandler> posClients = ConcurrentHashMap.newKeySet();

    private final ExecutorService workers = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private volatile boolean running;
    private RSAKeyManager rsaKeyManager;

    public static Object getSessionLock(long sessionId) {
        return sessionLocks.computeIfAbsent(sessionId, k -> new Object());
    }

    public static void registerKitchen(ClientHandler h) {
        kitchenClients.add(h);
    }

    public static void registerStaff(ClientHandler h) {
        staffClients.add(h);
    }

    /**
     * Đăng ký độc quyền 1 iPad cho phiên bàn.
     * Trả về true nếu thành công, false nếu đã có iPad khác đang giữ phiên này.
     */
    public static boolean tryClaimSession(long sessionId, ClientHandler h) {
        ClientHandler existing = sessionClients.compute(sessionId, (sid, current) -> {
            if (current == null || !current.isAliveConnection() || current == h) {
                return h;
            }
            return current;
        });
        return existing == h;
    }

    public static boolean isSessionClaimed(long sessionId) {
        ClientHandler current = sessionClients.get(sessionId);
        if (current != null && !current.isAliveConnection()) {
            sessionClients.remove(sessionId, current);
            return false;
        }
        return current != null;
    }

    public static void removeConnection(ClientHandler h) {
        kitchenClients.remove(h);
        staffClients.remove(h);
        posClients.remove(h);
        sessionClients.entrySet().removeIf(e -> e.getValue() == h);
    }

    private static void emit(Set<ClientHandler> recipients, SocketPacket p) {
        if (recipients != null) {
            for (ClientHandler h : recipients) h.sendPacket(p);
        }
    }

    public static void toSession(long sessionId, SocketPacket packet) {
        ClientHandler h = sessionClients.get(sessionId);
        if (h != null) h.sendPacket(packet);
    }

    public static void notifySessionClosed(long sessionId, long tableId) {
        ClientHandler peer = sessionClients.remove(sessionId);
        sessionLocks.remove(sessionId);

        SocketPacket closed = new SocketPacket(SocketAction.SESSION_CLOSED, true,
                "Phiên bàn đã được thanh toán và đóng", sessionId, null);
        closed.setTableId(tableId);

        if (peer != null) {
            peer.resetCustomerMode();
            peer.sendPacket(closed);
        }
        emit(staffClients, closed);
        emit(posClients, closed);
        broadcastTablesChanged();
    }

    public static void broadcastTablesChanged() {
        var tables = new OrderBUS().getAllRestaurantTables();
        emit(staffClients, new SocketPacket(SocketAction.LIST_TABLES, true,
                "Sơ đồ bàn đã thay đổi", tables, null));
        emit(posClients, new SocketPacket(SocketAction.GET_ALL_TABLES, true,
                "Sơ đồ bàn đã thay đổi", tables, null));
    }

    public static void broadcastOrderCreated(OrderDTO order) {
        SocketPacket packet = new SocketPacket(SocketAction.ORDER_CREATED, true,
                "Có đơn mới tại bàn #" + order.getTableId(), order, null);
        emit(kitchenClients, packet);
        emit(staffClients, packet);
        emit(posClients, packet);
    }

    public static void broadcastItemStatus(long sessionId, long itemId, dbEnums.OrderStatus state) {
        SocketPacket p = new SocketPacket(SocketAction.ITEM_STATUS_CHANGED, true,
                "Món #" + itemId + " -> " + state, itemId, state);
        toSession(sessionId, p);
        emit(kitchenClients, p);
        emit(staffClients, p);
    }

    public static void broadcastMenuChanged() {
        try {
            bus.FoodBUS foodBUS = new bus.FoodBUS();
            var foods = foodBUS.getPublishedFoodsForTablet();
            var categories = foodBUS.getAllCategories();
            SocketPacket packet = new SocketPacket(
                    SocketAction.GET_MENU_AND_CATEGORIES, true,
                    "Thực đơn vừa được cập nhật", foods, categories
            );
            for (ClientHandler h : sessionClients.values()) {
                h.sendPacket(packet);
            }
        } catch (Exception e) {
            System.err.println("[SocketServer] Lỗi broadcast thực đơn: " + e.getMessage());
        }
    }

    public static void broadcastOrderCompleted(OrderDTO order) {
        SocketPacket p = new SocketPacket(SocketAction.ORDER_COMPLETED, true,
                "Đơn #" + order.getId() + " đã chế biến xong", order.getId(), null);
        if (order.getTableSessionId() != null) toSession(order.getTableSessionId(), p);
        emit(staffClients, p);
    }

    public static void broadcastKitchenQueue(List<OrderDTO> orders) {
        emit(kitchenClients, new SocketPacket(SocketAction.GET_KITCHEN_QUEUE,
                true, "Danh sách bếp cập nhật", orders, null));
    }

    public static void registerPos(ClientHandler h) {
        posClients.add(h);
    }

    public static void releaseStaffRoles(ClientHandler h) {
        kitchenClients.remove(h);
        staffClients.remove(h);
        posClients.remove(h);
    }

    public synchronized void startServer() {
        if (running) return;
        try {
            String customDir = System.getenv("SERVER_KEY_DIR");
            Path keyDir = (customDir == null || customDir.isBlank())
                    ? Path.of("data", "keys") : Path.of(customDir);
            rsaKeyManager = new RSAKeyManager(keyDir);
            System.out.println("RSA Key Pair đã sẵn sàng!");
            System.out.println("[SERVER_RSA_SHA256] " + rsaKeyManager.getFingerprint());
            System.out.println("Cung cấp fingerprint này cho các Client qua kênh tin cậy; KHÔNG chia sẻ private.key.");
        } catch (Exception e) {
            throw new IllegalStateException("Không thể khởi tạo RSA Key Pair", e);
        }

        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể khởi động TCP Server tại cổng " + PORT, ex);
        }

        Thread thread = new Thread(() -> {
            try {
                while (running) {
                    Socket s = serverSocket.accept();
                    workers.execute(new ClientHandler(s, rsaKeyManager));
                }
            } catch (IOException ex) {
                if (running) System.err.println("Server TCP: " + ex.getMessage());
            } finally {
                running = false;
            }
        }, "socket-server-accept");
        thread.setDaemon(false);
        thread.start();
        System.out.println("Server TCP port " + PORT);
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) { }
        workers.shutdownNow();
    }
}