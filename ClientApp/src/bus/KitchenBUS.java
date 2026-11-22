package bus;

import api.SocketClient;
import dto.*;

import java.util.*;

public class KitchenBUS {
    public enum Type { QUEUE_UPDATED, ITEM_UPDATED, NEW_ORDER_ALERT, ERROR }

    public static final class Event {
        public final Type type;
        public final String message;
        public final Long itemId;

        private Event(Type type, String message, Long itemId) {
            this.type = type;
            this.message = message;
            this.itemId = itemId;
        }
    }

    private final SocketClient socket;
    private List<OrderDTO> dailyOrders = new ArrayList<>();
    private final Set<Long> knownPendingIds = new HashSet<>();
    private boolean initialLoadDone = false;

    public KitchenBUS(SocketClient authenticatedSocket) {
        this.socket = Objects.requireNonNull(authenticatedSocket, "Cần Socket đăng nhập");
        if (!socket.isConnected()) {
            throw new IllegalStateException("Mất kết nối Server");
        }
    }

    public dbEnums.OrderStatus nextStatus(OrderItemDTO item) {
        if (item == null || item.getStatus() == null) return null;
        return switch (item.getStatus()) {
            case PENDING -> dbEnums.OrderStatus.RECEIVED;
            case RECEIVED -> dbEnums.OrderStatus.COOKING;
            case COOKING -> dbEnums.OrderStatus.COMPLETED;
            case COMPLETED -> dbEnums.OrderStatus.SERVED;
            default -> null;
        };
    }

    public boolean refreshQueue() {
        return socket.sendRequest(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, null, null, null));
    }

    public boolean updateItemStatus(Long itemId, dbEnums.OrderStatus status) {
        return itemId != null && status != null && socket.sendRequest(
                new SocketPacket(SocketAction.UPDATE_ITEM_STATUS, null, itemId, status));
    }

    @SuppressWarnings("unchecked")
    public Event pollEvent() {
        SocketPacket p;
        while ((p = socket.pollPacket()) != null) {
            if (!p.isSuccess()) {
                return new Event(Type.ERROR, p.getMessage(), null);
            }

            switch (p.getAction()) {
                case GET_KITCHEN_QUEUE -> {
                    if (!(p.getData() instanceof List<?> rawList)) {
                        return new Event(Type.ERROR, "Server trả về danh sách món trong ngày không hợp lệ", null);
                    }
                    List<OrderDTO> updated = new ArrayList<>((List<OrderDTO>) rawList);
                    boolean hasNewPending = detectNewPendingItems(updated);
                    dailyOrders = updated;
                    return hasNewPending
                            ? new Event(Type.NEW_ORDER_ALERT, "Có món mới vào bếp", null)
                            : new Event(Type.QUEUE_UPDATED, p.getMessage(), null);
                }
                case UPDATE_ITEM_STATUS -> {
                    refreshQueue();
                    Long updatedId = p.getData() instanceof Long id ? id : null;
                    return new Event(Type.ITEM_UPDATED, p.getMessage(), updatedId);
                }
                case ORDER_CREATED, ITEM_STATUS_CHANGED, ORDER_COMPLETED -> refreshQueue();
                default -> { }
            }
        }
        return null;
    }

    private boolean detectNewPendingItems(List<OrderDTO> orders) {
        Set<Long> currentPending = new HashSet<>();
        for (OrderDTO order : orders) {
            if (order.getItems() == null) continue;
            for (OrderItemDTO item : order.getItems()) {
                if (item.getId() != null && item.getStatus() == dbEnums.OrderStatus.PENDING) {
                    currentPending.add(item.getId());
                }
            }
        }

        // Chỉ báo chuông từ lần đồng bộ thứ 2 trở đi
        boolean foundNew = initialLoadDone && !knownPendingIds.containsAll(currentPending);
        knownPendingIds.clear();
        knownPendingIds.addAll(currentPending);
        initialLoadDone = true;
        return foundNew;
    }

    public List<OrderDTO> getQueue() {
        return new ArrayList<>(dailyOrders);
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    public void logout() {
        socket.logout();
        socket.close();
    }
}