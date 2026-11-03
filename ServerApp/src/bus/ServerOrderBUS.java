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

    /**
     * Xử lý nghiệp vụ khi nhận 1 OrderDTO từ Tablet khách hàng gửi qua TCP Socket
     */
    public OrderDTO processIncomingTableOrder(OrderDTO order, String voucherCode) throws Exception {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Don khong co mon");
        }
        // DAO khoa phien ban, kiem tra ACTIVE, lay gia mon tu DB, tru kho va tao
        // orders/order_items trong MOT transaction. Khong tin gia do Client gui.
        order.setOrderCode("ORD" + java.util.UUID.randomUUID().toString()
                .replace("-", "").substring(0, 16).toUpperCase(java.util.Locale.ROOT));
        order.setOrderType(OrderType.DINE_IN);
        order.setStatus(OrderStatus.PENDING);
        Long id = orderDAO.createTableOrderTransaction(order);
        return orderDAO.getOrderById(id); // Tra ve itemId that sau khi INSERT.
    }

    /**
     * Dành cho Bếp: Đổi trạng thái món ăn
     */
    public boolean changeDishStatusByKitchen(Long orderItemId, OrderStatus newStatus) {
        return orderDAO.updateOrderItemStatus(orderItemId, newStatus);
    }

    /**
     * Trả về danh sách các món đã gọi của bàn để hiển thị tab "Theo dõi đơn"
     */
    public List<OrderItemDTO> getTableTrackingItems(Long tableId) {
        return orderDAO.getTrackingItemsByTable(tableId);
    }

    public List<TableDTO> getAllRestaurantTables() {
        return TableDAO.getAllTables();
    }

    public OrderDTO getTableBillPreview(Long tableId) {
        return orderDAO.getMergedTableOrderForCheckout(tableId);
    }

    public boolean processTablePayment(BillDTO bill, Long tableId) {
        return BillDAO.createBillAndReleaseTable(bill, tableId);
    }
}