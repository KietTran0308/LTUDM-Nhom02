package bus;

import api.SocketClient;
import dto.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
    private List<OrderDTO> queue = new ArrayList<>();
    private final java.util.Set<Long> knownPendingIds = new java.util.HashSet<>();
    private boolean queueInitialized = false;

    public KitchenBUS(SocketClient authenticatedSocket) {
        socket = Objects.requireNonNull(authenticatedSocket, "Can Socket dang nhap");
        if (!socket.isConnected()) throw new IllegalStateException("Mat ket noi Server");
    }

    /** Luong chuyen trang thai mon hop le cua bep. */
    public OrderStatus nextStatus(OrderItemDTO item) {
        if (item == null || item.getStatus() == null) return null;
        switch (item.getStatus()) {
            case PENDING: return OrderStatus.RECEIVED;
            case RECEIVED: return OrderStatus.COOKING;
            case COOKING: return OrderStatus.COMPLETED;
            case COMPLETED: return OrderStatus.SERVED;
            default: return null;
        }
    }

    public boolean refreshQueue() {
        return socket.sendRequest(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, null, null, null));
    }

    public boolean updateItemStatus(Long itemId, OrderStatus status) {
        return itemId != null && status != null && socket.sendRequest(
                new SocketPacket(SocketAction.UPDATE_ITEM_STATUS, null, itemId, status));
    }

    /** Chi doc queue, khong block EDT. */
    @SuppressWarnings("unchecked")
    public Event pollEvent() {
        SocketPacket p;
        while ((p = socket.pollPacket()) != null) {
            if (!p.isSuccess()) return new Event(Type.ERROR, p.getMessage(), null);

            switch (p.getAction()) {
                case GET_KITCHEN_QUEUE:
                    if (!(p.getData() instanceof List<?>))
                        return new Event(Type.ERROR, "Server tra ve hang doi bep khong hop le", null);
                    List<OrderDTO> updated = new ArrayList<>((List<OrderDTO>) p.getData());
                    boolean hasNewPending = detectNewPendingItems(updated);
                    queue = updated;
                    if (hasNewPending) {
                        return new Event(Type.NEW_ORDER_ALERT, "Có món mới vào bếp", null);
                    }
                    return new Event(Type.QUEUE_UPDATED, p.getMessage(), null);
                case UPDATE_ITEM_STATUS:
                    // ACK da xu ly. Lay lai snapshot tu Server de tranh du lieu cu.
                    refreshQueue();
                    return new Event(Type.ITEM_UPDATED, p.getMessage(),
                            p.getData() instanceof Long ? (Long) p.getData() : null);
                case ORDER_CREATED:
                case ITEM_STATUS_CHANGED:
                case ORDER_COMPLETED:
                    // PUSH real-time: du lieu moi duoc lay qua hang doi nhu cu.
                    refreshQueue();
                    continue;
                default:
                    continue;
            }
        }
        return null;
    }

    private boolean detectNewPendingItems(List<OrderDTO> orders) {
        java.util.Set<Long> currentPending = new java.util.HashSet<>();
        for (OrderDTO o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItemDTO item : o.getItems()) {
                if (item.getId() != null && item.getStatus() == OrderStatus.PENDING) {
                    currentPending.add(item.getId());
                }
            }
        }
        boolean foundNew = false;
        if (queueInitialized) {
            for (Long id : currentPending) {
                if (!knownPendingIds.contains(id)) {
                    foundNew = true;
                    break;
                }
            }
        }
        knownPendingIds.clear();
        knownPendingIds.addAll(currentPending);
        queueInitialized = true;
        return foundNew;
    }

    public List<OrderDTO> getQueue() { return new ArrayList<>(queue); }
    public boolean isConnected() { return socket.isConnected(); }
    public void logout() { socket.logout(); socket.close(); }
}
