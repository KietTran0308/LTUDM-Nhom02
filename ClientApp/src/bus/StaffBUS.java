package bus;

import api.SocketClient;
import dto.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StaffBUS {
    private final SocketClient client;
    private List<TableDTO> tables = new ArrayList<>();

    public StaffBUS(SocketClient session) {
        client = Objects.requireNonNull(session);
    }

    @SuppressWarnings("unchecked")
    public SocketPacket pollPacket() {
        SocketPacket packet = client.pollPacket();
        if (packet != null && packet.isSuccess() && packet.getAction() == SocketAction.LIST_TABLES
                && packet.getData() instanceof List<?>) {
            tables = (List<TableDTO>) packet.getData();
        }
        return packet;
    }

    public boolean requestListTables() {
        return client.sendRequest(new SocketPacket(SocketAction.LIST_TABLES, null, null, null));
    }

    public boolean enterCustomerMode(Long tableId, Long sessionId) {
        if (tableId == null || sessionId == null) return false;
        return client.sendRequest(new SocketPacket(SocketAction.ENTER_CUSTOMER_MODE, tableId, sessionId, null));
    }

    public List<TableDTO> getTables() {
        return java.util.Collections.unmodifiableList(tables);
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public void logout() {
        client.logout();
        client.close();
    }
}
