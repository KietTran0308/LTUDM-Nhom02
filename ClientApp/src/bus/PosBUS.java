package bus;

import api.SocketClient;
import dto.*;

import java.util.List;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PosBUS {
    private final SocketClient socket;
    private final UserDTO operator;

    public PosBUS(UserDTO operator, SocketClient socket) {
        if (operator == null || socket == null || !socket.isConnected()
                || !"ADMIN".equalsIgnoreCase(operator.getRoleName())) {
            throw new SecurityException(
                    "POS chỉ dành cho ADMIN có phiên kết nối hợp lệ");
        }

        this.operator = operator;
        this.socket = socket;
    }

    public UserDTO getOperator() {
        return operator;
    }

    private SocketPacket request(
            SocketAction action,
            Long tableId,
            Object data,
            Object extra) {

        return socket.requestAndWait(
                new SocketPacket(action, tableId, data, extra),
                5000);
    }

    @SuppressWarnings("unchecked")
    public List<TableDTO> listTables() {
        return (List<TableDTO>) request(
                SocketAction.GET_ALL_TABLES,
                null, null, null).getData();
    }

    public long openTable(long tableId) {
        return (Long) request(
                SocketAction.OPEN_TABLE_SESSION,
                tableId, null, null).getData();
    }

    public OrderDTO preview(long tableId) {
        return (OrderDTO) request(
                SocketAction.GET_TABLE_BILL_PREVIEW,
                tableId, null, null).getData();
    }

    public void markServed(long itemId) {
        request(SocketAction.MARK_ITEM_SERVED,
                null, itemId, null);
    }

    public void pay(
            long tableId,
            long sessionId,
            PaymentMethod method) {

        BillDTO bill = new BillDTO();
        bill.setTableSessionId(sessionId);
        bill.setPaymentMethod(method);

        request(SocketAction.PAY_TABLE_BILL,
                tableId, bill, null);
    }

    /** Chỉ dùng để hiển thị. Server/DAO tự tính lại tổng tiền khi thanh toán. */
    public BigDecimal previewVat(OrderDTO order) {
        if (order == null || order.getSubtotal() == null) return BigDecimal.ZERO;
        BigDecimal discount = order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount();
        return order.getSubtotal().subtract(discount).max(BigDecimal.ZERO)
                .multiply(new BigDecimal("0.08")).setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal previewTotal(OrderDTO order) {
        if (order == null || order.getTotalAmount() == null) return BigDecimal.ZERO;
        return order.getTotalAmount().add(previewVat(order));
    }

    public void close() {
        socket.logout();
        socket.close();
    }
}
