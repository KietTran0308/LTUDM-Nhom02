package bus;

import dao.OrderDAO;
import dao.TableDAO;
import dto.*;

import java.sql.SQLException;
import java.util.List;
import api.SocketServer;

public final class PosBUS {
    private final UserDTO operator;
    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderBUS orderBUS = new OrderBUS();

    public PosBUS(UserDTO operator) {
        if (operator == null || operator.getId() == null
                || !"ADMIN".equalsIgnoreCase(operator.getRoleName())) {
            throw new SecurityException("POS chỉ dành cho ADMIN đã xác thực qua Server.");
        }
        this.operator = operator;
    }

    public UserDTO getOperator() { return operator; }

    public List<TableDTO> listTables() { return TableDAO.getAllTables(); }

    /** POS mo phien, khong phai tablet cua nhan vien. */
    public long openTable(long tableId) throws SQLException {
        long sid = TableDAO.openOrGetActiveSession(tableId, operator.getId());
        SocketServer.broadcastTablesChanged();
        return sid;
    }

    public OrderDTO preview(long tableId) { return orderBUS.getTableBillPreview(tableId); }

    public void pay(long tableId, BillDTO bill, String revision) {
        PaymentMethod method = bill.getPaymentMethod();
        if (method != PaymentMethod.CASH && method != PaymentMethod.BANK_TRANSFER)
            throw new IllegalArgumentException("Hãy chọn tiền mặt hoặc chuyển khoản ngân hàng.");
        if (revision == null || revision.isBlank())
            throw new IllegalArgumentException("Hãy tải và xem lại hóa đơn trước khi thanh toán.");
        long sessionId = bill.getTableSessionId();
        orderBUS.processTablePayment(bill, tableId, revision);
        SocketServer.notifySessionClosed(sessionId, tableId);
        SocketServer.broadcastTablesChanged();
        SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
    }

}
