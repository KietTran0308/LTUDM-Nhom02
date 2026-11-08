package bus;

import dao.*;
import dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

public class ServerOrderBUS {
    private final OrderDAO orderDAO = new OrderDAO();
    private final VoucherDAO voucherDAO = new VoucherDAO();

    public OrderDTO processIncomingTableOrder(OrderDTO order, String voucherCode) throws Exception {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Don khong co mon");
        }

        order.setOrderCode("ORD" + java.util.UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT));
        order.setOrderType(OrderType.DINE_IN);
        order.setStatus(OrderStatus.PENDING);
        Long id = orderDAO.createTableOrderTransaction(order);
        return orderDAO.getOrderById(id); // Tra ve itemId that sau khi INSERT.
    }

    public boolean changeDishStatusByKitchen(Long orderItemId, OrderStatus newStatus) {
        return orderDAO.updateOrderItemStatus(orderItemId, newStatus);
    }

    public List<OrderItemDTO> getTableTrackingItems(Long tableId) {
        return orderDAO.getTrackingItemsByTable(tableId);
    }

    public List<TableDTO> getAllRestaurantTables() {
        return TableDAO.getAllTables();
    }

    // Kiem tra phien ban tai BUS: ClientHandler khong duoc phu thuoc TableDAO.
    public boolean isActiveTableSession(Long tableId, Long sessionId) throws SQLException {
        return tableId != null && tableId > 0 && sessionId != null && sessionId > 0
                && TableDAO.isActiveSession(tableId, sessionId);
    }

    // Bep: doi trang thai mon va truy van hang doi thong qua BUS.
    public List<OrderDTO> getKitchenQueue() {
        return orderDAO.getKitchenQueue();
    }

    public Long getTableIdByOrderItem(Long itemId) {
        if (itemId == null || itemId <= 0) return null;
        return orderDAO.getTableIdByOrderItem(itemId);
    }

    public OrderDTO getOrderByItem(Long itemId) {
        if (itemId == null || itemId <= 0) return null;
        Long orderId = orderDAO.getOrderIdByItem(itemId);
        return orderId == null ? null : orderDAO.getOrderById(orderId);
    }

    public OrderDTO getCompletedOrderIfReady(Long itemId) {
        if (itemId == null || itemId <= 0) return null;
        return orderDAO.completedOrderIfReady(itemId);
    }

    public OrderDTO getTableBillPreview(Long tableId) {
        return orderDAO.getMergedTableOrderForCheckout(tableId);
    }

    public boolean processTablePayment(BillDTO bill, Long tableId) {
        return BillDAO.createBillAndReleaseTable(bill, tableId);
    }
}