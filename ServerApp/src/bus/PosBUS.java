package bus;

import api.SocketServer;
import dao.OrderDAO;
import dao.TableDAO;
import dto.BillDTO;
import dto.OrderDTO;
import dto.TableDTO;
import dto.UserDTO;
import dto.dbEnums;

import java.sql.SQLException;
import java.util.List;

public final class PosBUS {

    private static final String ROLE_ADMIN = "ADMIN";

    private final UserDTO operator;
    private final OrderDAO orderDAO = new OrderDAO();
    private final OrderBUS orderBUS = new OrderBUS();

    public PosBUS(UserDTO operator) {
        if (operator == null || operator.getId() == null
                || !ROLE_ADMIN.equalsIgnoreCase(operator.getRoleName())) {
            throw new SecurityException("POS chỉ dành cho ADMIN đã xác thực qua Server.");
        }
        this.operator = operator;
    }

    public UserDTO getOperator() {
        return operator;
    }

    public List<TableDTO> listTables() {
        return TableDAO.getAllTables();
    }

    /** POS mở phiên, không phải tablet của nhân viên. */
    public long openTable(long tableId) throws SQLException {
        long sid = TableDAO.openOrGetActiveSession(tableId, operator.getId());
        SocketServer.broadcastTablesChanged();
        return sid;
    }

    public OrderDTO preview(long tableId) {
        return orderBUS.getTableBillPreview(tableId);
    }

    public void pay(long tableId, BillDTO bill, String revision) {
        dbEnums.PaymentMethod method = bill.getPaymentMethod();
        if (method != dbEnums.PaymentMethod.CASH && method != dbEnums.PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException("Hãy chọn tiền mặt hoặc chuyển khoản ngân hàng.");
        }
        if (revision == null || revision.isBlank()) {
            throw new IllegalArgumentException("Hãy tải và xem lại hóa đơn trước khi thanh toán.");
        }
        long sessionId = bill.getTableSessionId();
        orderBUS.processTablePayment(bill, tableId, revision);
        SocketServer.notifySessionClosed(sessionId, tableId);
        SocketServer.broadcastTablesChanged();
        SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
    }

    public OrderDTO applyVoucher(long tableId, String voucherCode) {
        return orderDAO.applyVoucherToTableSession(tableId, voucherCode);
    }
}