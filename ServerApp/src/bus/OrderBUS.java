package bus;

import dao.BillDAO;
import dao.OrderDAO;
import dao.TableDAO;
import dto.BillDTO;
import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.TableDTO;
import dto.dbEnums;

import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class OrderBUS {

    private final OrderDAO orderDAO = new OrderDAO();

    public OrderDTO processIncomingTableOrder(OrderDTO order, String voucherCode) throws Exception {
        if (order == null || order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn không có món");
        }

        String code = "ORD" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase(Locale.ROOT);
        order.setOrderCode(code);
        order.setStatus(dbEnums.OrderStatus.PENDING);

        Long id = orderDAO.createTableOrderTransaction(order);
        if (voucherCode != null && !voucherCode.isBlank()) {
            orderDAO.applyVoucherToTableSession(order.getTableId(), voucherCode.trim());
        }
        return orderDAO.getOrderById(id); // Trả về itemId thật sau khi INSERT
    }

    public boolean changeDishStatusByKitchen(Long orderItemId, dbEnums.OrderStatus newStatus) {
        return orderDAO.updateOrderItemStatus(orderItemId, newStatus);
    }

    public List<OrderItemDTO> getTableTrackingItems(Long tableId) {
        return orderDAO.getTrackingItemsByTable(tableId);
    }

    public List<TableDTO> getAllRestaurantTables() {
        return TableDAO.getAllTables();
    }

    // Kiểm tra phiên bàn tại BUS: ClientHandler không phụ thuộc trực tiếp TableDAO.
    public boolean isActiveTableSession(Long tableId, Long sessionId) throws SQLException {
        return isPositiveId(tableId) && isPositiveId(sessionId)
                && TableDAO.isActiveSession(tableId, sessionId);
    }

    public boolean sessionBelongsToTable(Long tableId, Long sessionId) throws SQLException {
        return isActiveTableSession(tableId, sessionId);
    }

    public List<OrderDTO> getKitchenQueue() {
        return orderDAO.getKitchenQueue();
    }

    public Long getTableIdByOrderItem(Long itemId) {
        return isPositiveId(itemId) ? orderDAO.getTableIdByOrderItem(itemId) : null;
    }

    public OrderDTO getOrderByItem(Long itemId) {
        if (!isPositiveId(itemId)) return null;
        Long orderId = orderDAO.getOrderIdByItem(itemId);
        return orderId == null ? null : orderDAO.getOrderById(orderId);
    }

    public OrderDTO getCompletedOrderIfReady(Long itemId) {
        return isPositiveId(itemId) ? orderDAO.completedOrderIfReady(itemId) : null;
    }

    public OrderDTO getTableBillPreview(Long tableId) {
        return orderDAO.getMergedTableOrderForCheckout(tableId);
    }

    public void processTablePayment(BillDTO bill, long tableId, String revision) {
        BillDAO.createBillAndReleaseTable(bill, tableId, revision);
    }

    private static boolean isPositiveId(Long id) {
        return id != null && id > 0;
    }
}