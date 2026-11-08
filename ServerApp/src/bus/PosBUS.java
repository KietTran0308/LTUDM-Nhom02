package bus;

import dao.BillDAO;
import dao.OrderDAO;
import dao.TableDAO;
import dto.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.List;
import api.SocketServer;

import api.SocketServer;

public final class PosBUS {
    private final UserDTO operator;
    private final OrderDAO orderDAO = new OrderDAO();
    private final ServerOrderBUS orderBUS = new ServerOrderBUS();

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

    public void markServed(long itemId) {
        Long tableId = orderDAO.getTableIdByOrderItem(itemId);
        if (tableId == null) throw new IllegalStateException("Mon khong thuoc phien ACTIVE.");
        if (!orderDAO.updateOrderItemStatus(itemId, OrderStatus.SERVED))
            throw new IllegalStateException("Chi mon COMPLETED moi duoc danh dau SERVED.");
        OrderDTO order = orderDAO.getOrderById(orderDAO.getOrderIdByItem(itemId));
        if (order != null && order.getTableSessionId() != null)
            SocketServer.broadcastItemStatus(order.getTableSessionId(), itemId, OrderStatus.SERVED);
        SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
    }

    /**
     * Thanh toan mo phong: DAO tinh lai gia tri va dong phien trong transaction.
     * Phai khop dung phien da xem hoa don, tranh thanh toan nham ban moi.
     */
    public void pay(long tableId, long sessionId, PaymentMethod method) {
        if (tableId <= 0 || sessionId <= 0 || method == null)
            throw new IllegalArgumentException("Thong tin thanh toan khong hop le");
        try {
            if (!TableDAO.isActiveSession(tableId, sessionId))
                throw new IllegalStateException("Phien ban da dong hoac khong thuoc ban duoc chon.");
        } catch (SQLException e) {
            throw new IllegalStateException("Khong kiem tra duoc phien ban", e);
        }
        OrderDTO preview = orderBUS.getTableBillPreview(tableId);
        if (preview == null || !Long.valueOf(sessionId).equals(preview.getTableSessionId()))
            throw new IllegalStateException("Hoa don da thay doi phien. Vui long tai lai.");
        BillDTO bill = new BillDTO();
        bill.setTableSessionId(sessionId);
        bill.setPaymentMethod(method);
        // DAO bat buoc tinh lai tong tien, VAT va discount trong transaction.
        bill.setTotalAmount(BigDecimal.ZERO);
        bill.setTax(BigDecimal.ZERO);
        bill.setDiscount(BigDecimal.ZERO);
        if (!BillDAO.createBillAndReleaseTable(bill, tableId))
            throw new IllegalStateException("Thanh toan that bai. Kiem tra don va database.");
        SocketServer.notifySessionClosed(sessionId, tableId);
        SocketServer.broadcastTablesChanged();
        SocketServer.broadcastKitchenQueue(orderDAO.getKitchenQueue());
    }

    public BigDecimal previewVat(OrderDTO order) {
        if (order == null || order.getSubtotal() == null) return BigDecimal.ZERO;
        // Cung cong thuc voi BillDAO: 8% tren tong gia tri sau giam gia.
        BigDecimal net = order.getSubtotal().subtract(
                order.getDiscountAmount() == null ? BigDecimal.ZERO : order.getDiscountAmount());
        return net.max(BigDecimal.ZERO).multiply(new BigDecimal("0.08"))
                .setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal previewTotal(OrderDTO order) {
        if (order == null || order.getTotalAmount() == null) return BigDecimal.ZERO;
        return order.getTotalAmount().add(previewVat(order));
    }
}
