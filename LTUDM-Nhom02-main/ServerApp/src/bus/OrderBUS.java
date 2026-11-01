
package bus;

import dao.OrderDAO;
import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.OrderStatus;

import java.sql.SQLException;
import java.util.List;

public class OrderBUS {

    private final OrderDAO orderDAO;

    public OrderBUS() {
        this.orderDAO = new OrderDAO();
    }

    // Tìm đơn hàng gốc đang hoạt động của bàn
    public Long findActiveRootOrderByTable(Long tableId) {
        if (tableId == null || tableId <= 0) {
            throw new IllegalArgumentException("ID bàn không hợp lệ");
        }

        return orderDAO.findActiveRootOrderByTable(tableId);
    }

    // Tạo đơn hàng tại bàn
    public Long createTableOrder(OrderDTO order) throws SQLException {
        validateOrder(order);

        // Nếu là đơn ăn tại bàn, kiểm tra đơn gốc của bàn
        if (order.getOrderType() != null
                && order.getOrderType().name().equals("DINE_IN")
                && order.getTableId() != null) {

            Long rootOrderId =
                    orderDAO.findActiveRootOrderByTable(order.getTableId());

            // Nếu bàn đã có đơn gốc thì tạo đơn con
            if (rootOrderId != null) {
                order.setParentOrderId(rootOrderId);
            }
        }

        return orderDAO.createTableOrderTransaction(order);
    }

    // Lấy đơn hàng theo ID
    public OrderDTO getOrderById(Long orderId) {
        validateId(orderId, "ID đơn hàng");

        return orderDAO.getOrderById(orderId);
    }

    // Lấy đơn hàng theo mã
    public OrderDTO getOrderByCode(String orderCode) {
        if (orderCode == null || orderCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Mã đơn hàng không được để trống");
        }

        return orderDAO.getOrderByCode(orderCode.trim());
    }

    // Lấy danh sách đơn hàng của người dùng
    public List<OrderDTO> getOrdersByUserId(Long userId) {
        validateId(userId, "ID người dùng");

        return orderDAO.getOrdersByUserId(userId);
    }

    // Lấy danh sách món trong đơn hàng
    public List<OrderItemDTO> getOrderItemsByOrderId(Long orderId) {
        validateId(orderId, "ID đơn hàng");

        return orderDAO.getOrderItemsByOrderId(orderId);
    }

    // Cập nhật trạng thái món trong đơn
    public boolean updateOrderItemStatus(
            Long orderItemId,
            OrderStatus newStatus
    ) {
        validateId(orderItemId, "ID món trong đơn");

        if (newStatus == null) {
            throw new IllegalArgumentException("Trạng thái mới không hợp lệ");
        }

        return orderDAO.updateOrderItemStatus(orderItemId, newStatus);
    }

    // Cập nhật trạng thái đơn hàng
    public boolean updateOrderStatus(
            Long orderId,
            OrderStatus newStatus
    ) {
        validateId(orderId, "ID đơn hàng");

        if (newStatus == null) {
            throw new IllegalArgumentException("Trạng thái mới không hợp lệ");
        }

        return orderDAO.updateOrderStatus(orderId, newStatus);
    }

    // Lấy danh sách món đang được theo dõi tại bàn
    public List<OrderItemDTO> getTrackingItemsByTable(Long tableId) {
        validateId(tableId, "ID bàn");

        return orderDAO.getTrackingItemsByTable(tableId);
    }

    // Lấy đơn tổng hợp của bàn để thanh toán
    public OrderDTO getMergedTableOrderForCheckout(Long tableId) {
        validateId(tableId, "ID bàn");

        return orderDAO.getMergedTableOrderForCheckout(tableId);
    }

    // Hủy đơn hàng
    public boolean cancelOrder(Long orderId, String reason) {
        validateId(orderId, "ID đơn hàng");

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Lý do hủy không được để trống");
        }

        return orderDAO.cancelOrder(orderId, reason.trim());
    }

    // ================= VALIDATION =================

    private void validateOrder(OrderDTO order) {
        if (order == null) {
            throw new IllegalArgumentException("Thông tin đơn hàng không được để trống");
        }

        if (order.getOrderType() == null) {
            throw new IllegalArgumentException("Loại đơn hàng không hợp lệ");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất một món");
        }

        if (order.getOrderType().name().equals("DINE_IN")
                && (order.getTableId() == null || order.getTableId() <= 0)) {
            throw new IllegalArgumentException("Đơn tại bàn phải có ID bàn hợp lệ");
        }

        for (OrderItemDTO item : order.getItems()) {
            if (item == null) {
                throw new IllegalArgumentException("Thông tin món ăn không hợp lệ");
            }

            if (item.getFoodsId() == null || item.getFoodsId() <= 0) {
                throw new IllegalArgumentException("ID món ăn không hợp lệ");
            }

            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new IllegalArgumentException("Số lượng món ăn phải lớn hơn 0");
            }
        }
    }

    private void validateId(Long id, String fieldName) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(fieldName + " không hợp lệ");
        }
    }
}
