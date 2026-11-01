package bus;

import api.SocketClient;
import dto.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Chi gui/nhan Socket; KHONG truy cap JDBC, DAO hay SocketServer. */
public class KitchenBUS {
    private final SocketClient socket;
    private List<OrderDTO> queue = new ArrayList<>();

    public KitchenBUS(SocketClient authenticatedSocket) {
        this.socket = Objects.requireNonNull(authenticatedSocket, "Can Socket dang nhap");
        if (!socket.isConnected()) throw new IllegalStateException("Mat ket noi Server");
    }

    public boolean refreshQueue() {
        return socket.sendRequest(new SocketPacket(SocketAction.GET_KITCHEN_QUEUE, null, null, null));
    }

    public boolean updateItemStatus(Long itemId, OrderStatus status) {
        if (itemId == null || status == null) return false;
        return socket.sendRequest(new SocketPacket(SocketAction.SET_KITCHEN_ITEM_STATUS, null, itemId, status));
    }

    @SuppressWarnings("unchecked")
    public SocketPacket pollPacket() {
        SocketPacket packet = socket.pollPacket();
        if (packet != null && packet.isSuccess() && packet.getAction() == SocketAction.GET_KITCHEN_QUEUE) {
            if (packet.getData() instanceof List<?>) {
                queue = (List<OrderDTO>) packet.getData();
            }
        }
        return packet;
    }

    public List<OrderDTO> getQueue() { return new ArrayList<>(queue); }
    public boolean isConnected() { return socket.isConnected(); }
    public void logout() {
        socket.logout();
        socket.close();
    }
}
