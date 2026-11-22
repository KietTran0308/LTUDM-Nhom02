package bus;

import api.SocketClient;
import dto.SocketAction;
import dto.SocketPacket;
import dto.TableDTO;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class StaffBUS {

    public enum Type { TABLES, HANDOFF, HANDOFF_ERROR, SESSION_CLOSED, NOTICE, ERROR }

    public static final class Event {
        public final Type type;
        public final String message;
        public final Long tableId;
        public final Long sessionId;

        private Event(Type type, String message, Long tableId, Long sessionId) {
            this.type = type;
            this.message = message;
            this.tableId = tableId;
            this.sessionId = sessionId;
        }
    }

    private final SocketClient client;
    private List<TableDTO> tables = new ArrayList<>();
    private Long pendingTableId;
    private Long pendingSessionId;

    public StaffBUS(SocketClient session) {
        this.client = Objects.requireNonNull(session);
    }

    @SuppressWarnings("unchecked")
    public Event pollEvent() {
        SocketPacket p;
        while ((p = client.pollPacket()) != null) {
            if (p.getAction() == SocketAction.ENTER_CUSTOMER_MODE) {
                boolean matched = p.isSuccess()
                        && p.getData() instanceof Long
                        && Objects.equals(p.getTableId(), pendingTableId)
                        && Objects.equals(p.getData(), pendingSessionId);
                Event event = new Event(
                        matched ? Type.HANDOFF : Type.HANDOFF_ERROR,
                        matched ? p.getMessage() : "Phản hồi bàn giao không hợp lệ: " + p.getMessage(),
                        pendingTableId,
                        pendingSessionId
                );
                clearPendingHandoff();
                return event;
            }

            if (!p.isSuccess()) {
                return new Event(Type.ERROR, p.getMessage(), p.getTableId(), null);
            }

            switch (p.getAction()) {
                case LIST_TABLES -> {
                    if (!(p.getData() instanceof List<?> rawList)) {
                        return new Event(Type.ERROR, "Dữ liệu danh sách bàn không hợp lệ", null, null);
                    }
                    tables = new ArrayList<>((List<TableDTO>) rawList);
                    return new Event(Type.TABLES, p.getMessage(), null, null);
                }
                case SESSION_CLOSED -> {
                    return new Event(Type.SESSION_CLOSED, p.getMessage(), p.getTableId(), null);
                }
                case ORDER_COMPLETED -> {
                    return new Event(Type.NOTICE, p.getMessage(), p.getTableId(), null);
                }
                default -> { }
            }
        }
        return null;
    }

    public boolean requestListTables() {
        return client.sendRequest(new SocketPacket(SocketAction.LIST_TABLES, null, null, null));
    }

    public boolean enterCustomerMode(Long tableId, Long sessionId) {
        if (tableId == null || sessionId == null || pendingSessionId != null) {
            return false;
        }
        pendingTableId = tableId;
        pendingSessionId = sessionId;
        if (client.sendRequest(new SocketPacket(SocketAction.ENTER_CUSTOMER_MODE, tableId, sessionId, null))) {
            return true;
        }
        clearPendingHandoff();
        return false;
    }

    private void clearPendingHandoff() {
        pendingTableId = null;
        pendingSessionId = null;
    }

    public List<TableDTO> getTables() { return Collections.unmodifiableList(tables); }
    public boolean isConnected() { return client.isConnected(); }
    public void logout() { client.logout(); client.close(); }
}