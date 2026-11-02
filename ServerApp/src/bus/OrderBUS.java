package bus;

import dao.OrderDAO;
import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.OrderStatus;
import dto.OrderType;

import java.sql.SQLException;
import java.util.List;

/** Business rules for table orders and kitchen status. */
public class OrderBUS {
    private final OrderDAO orderDAO;

    public OrderBUS() {
        this.orderDAO = new OrderDAO();
    }

    public Long findActiveRootOrderByTable(Long tableId) {
        validateId(tableId, "ID bàn");
        return orderDAO.findActiveRootOrderByTable(tableId);
    }

    public Long createTableOrder(OrderDTO order) throws SQLException {
        validateOrder(order);
        // Root order and session must be selected under the DAO transaction's
        // row lock, NOT in a separate BUS query (avoids concurrent roots).
        return orderDAO.createTableOrderTransaction(order);
    }

    public OrderDTO getOrderById(Long orderId) {
        validateId(orderId, "ID đơn hàng");
        return orderDAO.getOrderById(orderId);
    }

    public OrderDTO getOrderByCode(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống");
        }
        return orderDAO.getOrderByCode(orderCode.trim());
    }

    public List<OrderDTO> getOrdersByUserId(Long userId) {
        validateId(userId, "ID người dùng");
        return orderDAO.getOrdersByUserId(userId);
    }

    public List<OrderItemDTO> getOrderItemsByOrderId(Long orderId) {
        validateId(orderId, "ID đơn hàng");
        return orderDAO.getOrderItemsByOrderId(orderId);
    }

    public boolean updateOrderItemStatus(Long orderItemId, OrderStatus newStatus) {
        validateId(orderItemId, "ID món trong đơn");
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái món không hợp lệ");
        return orderDAO.updateOrderItemStatus(orderItemId, newStatus);
    }

    public boolean updateOrderStatus(Long orderId, OrderStatus newStatus) {
        validateId(orderId, "ID đơn hàng");
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái đơn không hợp lệ");
        return orderDAO.updateOrderStatus(orderId, newStatus);
    }

    public List<OrderItemDTO> getTrackingItemsByTable(Long tableId) {
        validateId(tableId, "ID bàn");
        return orderDAO.getTrackingItemsByTable(tableId);
    }

    public OrderDTO getMergedTableOrderForCheckout(Long tableId) {
        validateId(tableId, "ID bàn");
        return orderDAO.getMergedTableOrderForCheckout(tableId);
    }

    public boolean cancelOrder(Long orderId, String reason) {
        validateId(orderId, "ID đơn hàng");
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Lý do hủy không được để trống");
        }
        return orderDAO.cancelOrder(orderId, reason.trim());
    }

    private void validateOrder(OrderDTO order) {
        if (order == null) throw new IllegalArgumentException("Đơn hàng không được để trống");
        if (order.getOrderType() != OrderType.DINE_IN) {
            throw new IllegalArgumentException("createTableOrder() chỉ dùng cho đơn ăn tại bàn");
        }
        validateId(order.getTableId(), "ID bàn");
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn phải có ít nhất một món");
        }
        for (OrderItemDTO item : order.getItems()) {
            if (item == null) throw new IllegalArgumentException("Thông tin món không hợp lệ");
            validateId(item.getFoodsId(), "ID món");
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng món phải lớn hơn 0");
            }
        }
    }

    private void validateId(Long id, String label) {
        if (id == null || id <= 0) throw new IllegalArgumentException(label + " không hợp lệ");
    }
}
