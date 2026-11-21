package api;

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
    private ServerSocket serverSocket;
    private volatile boolean running;
    private final ExecutorService workers = Executors.newCachedThreadPool();
    private static final Map<Long, Set<ClientHandler>> sessionClients = new ConcurrentHashMap<>();
    private static final Set<ClientHandler> kitchenClients = ConcurrentHashMap.newKeySet();
    private static final Set<ClientHandler> staffClients = ConcurrentHashMap.newKeySet();

    private RSAKeyManager rsaKeyManager;

    public SocketServer() {
    }

    public static void registerKitchen(ClientHandler h) {
        kitchenClients.add(h);
    }

    public static void registerStaff(ClientHandler h) {
        staffClients.add(h);
    }

    public static void registerSession(Long sessionId, ClientHandler h) {
        if (sessionId != null) sessionClients.computeIfAbsent(sessionId,
                x -> ConcurrentHashMap.newKeySet()).add(h);
    }

    public static void removeConnection(ClientHandler h) {
        kitchenClients.remove(h);
        staffClients.remove(h);
        for (Set<ClientHandler> peers : sessionClients.values()) peers.remove(h);
        sessionClients.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    private static void emit(Set<ClientHandler> recipients, SocketPacket p) {
        if (recipients != null) for (ClientHandler h : recipients) h.sendPacket(p);
    }

    public static void toSession(long sessionId, SocketPacket packet) {
        emit(sessionClients.get(sessionId), packet);
    }

    public static void notifySessionClosed(long sessionId, long tableId) {
        Set<ClientHandler> peers = sessionClients.remove(sessionId);
        SocketPacket closed = new SocketPacket(SocketAction.SESSION_CLOSED, true,
                "Phien ban da duoc thanh toan va dong", sessionId, null);
        closed.setTableId(tableId);
        emit(peers, closed);
        // POS khac trong mang LAN cap nhat so do ban khi mot POS thanh toan.
        emit(staffClients, closed);
    }

    public static void broadcastTablesChanged() {
        emit(staffClients, new SocketPacket(SocketAction.LIST_TABLES, true,
                "So do ban da thay doi", new bus.OrderBUS().getAllRestaurantTables(), null));
    }

    public static void broadcastOrderCreated(OrderDTO order) {
        SocketPacket packet = new SocketPacket(SocketAction.ORDER_CREATED, true,
                "Co don moi tai ban #" + order.getTableId(), order, null);
        emit(kitchenClients, packet);
        emit(staffClients, packet);
    }

    public static void broadcastItemStatus(long sessionId, long itemId, dbEnums.OrderStatus state) {
        SocketPacket p = new SocketPacket(SocketAction.ITEM_STATUS_CHANGED, true,
                "Mon #" + itemId + " -> " + state, itemId, state);
        toSession(sessionId, p);
        emit(kitchenClients, p);
        emit(staffClients, p);
    }

    public static void broadcastOrderCompleted(OrderDTO order) {
        SocketPacket p = new SocketPacket(SocketAction.ORDER_COMPLETED, true,
                "Don #" + order.getId() + " da che bien xong", order.getId(), null);
        if (order.getTableSessionId() != null) toSession(order.getTableSessionId(), p);
        emit(staffClients, p);
    }

    public static void broadcastKitchenQueue(List<OrderDTO> orders) {
        emit(kitchenClients, new SocketPacket(SocketAction.GET_KITCHEN_QUEUE,
                true, "Danh sach bep cap nhat", orders, null));
    }

    public static void notifyStaff(Long tableId, String note) {
        emit(staffClients, new SocketPacket(SocketAction.CALL_STAFF, true,
                "Ban #" + tableId + ": " + note, note, null));
    }

    public synchronized void startServer() {
        if (running) return;
        // Bind dong bo: neu port 8888 ban, giao dien trung tam khong bao sai "Dang chay".
        try {
            String customDir = System.getenv("SERVER_KEY_DIR");
            Path keyDir = (customDir == null || customDir.isBlank())
                    ? Path.of("data", "keys") : Path.of(customDir);
            rsaKeyManager = new RSAKeyManager(keyDir);
            System.out.println("RSA Key Pair đã sẵn sàng!");
            System.out.println("[SERVER_RSA_SHA256] " + rsaKeyManager.getFingerprint());
            System.out.println("Cung cap fingerprint nay cho cac Client qua kenh tin cay; KHONG chia se private.key.");

        } catch (Exception e) {

            throw new IllegalStateException(
                    "Không thể khởi tạo RSA Key Pair",
                    e
            );
        }
        try {
            serverSocket = new ServerSocket(PORT);
            running = true;
        } catch (IOException ex) {
            throw new IllegalStateException("Khong the khoi dong TCP Server tai cong " + PORT, ex);
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
        } catch (IOException ignored) {
        }
        workers.shutdownNow();
    }
}