
package dao;

import dto.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    // =====================================================
    // 1. TÌM ĐƠN GỐC ĐANG HOẠT ĐỘNG CỦA BÀN
    // =====================================================

    /**
     * Tìm đơn hàng gốc đang hoạt động của một bàn.
     * Đơn gốc có parent_order_id = NULL.
     */
    public Long findActiveRootOrderByTable(Long tableId) {

        String sql = "SELECT id FROM orders " +
                "WHERE table_id = ? " +
                "AND parent_order_id IS NULL " +
                "AND status NOT IN ('COMPLETED', 'CANCELLED') " +
                "ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tableId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    // =====================================================
    // 2. TẠO ĐƠN HÀNG VÀ LƯU CHI TIẾT ĐƠN
    // =====================================================

    /**
     * Tạo đơn hàng bằng Transaction:
     * 1. Lưu orders.
     * 2. Lưu order_items.
     * 3. Kiểm tra và trừ kho ingredients theo recipes.
     * 4. Cập nhật trạng thái bàn.
     *
     * Nếu có lỗi thì rollback toàn bộ.
     */
    public Long createTableOrderTransaction(OrderDTO order)
            throws SQLException {

        if (order == null) {
            throw new IllegalArgumentException("Thông tin đơn hàng không được để trống.");
        }

        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng phải có ít nhất một món.");
        }

        String sqlOrder = "INSERT INTO orders (" +
                "order_code, parent_order_id, user_id, table_id, voucher_id, " +
                "order_type, status, subtotal, shipping_fee, discount_amount, " +
                "total_amount, note" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String sqlItem = "INSERT INTO order_items (" +
                "order_id, foods_id, foods_name, unit_price, quantity, line_total, status" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?)";

        // Kiểm tra nguyên liệu có đủ số lượng trước khi trừ.
        String sqlCheckStock = "SELECT i.id " +
                "FROM ingredients i " +
                "JOIN recipes r ON i.id = r.ingredient_id " +
                "WHERE r.foods_id = ? " +
                "AND i.stock_quantity < (r.quantity_required * ?) " +
                "FOR UPDATE";

        // Trừ nguyên liệu theo công thức món ăn.
        String sqlDeductStock = "UPDATE ingredients i " +
                "JOIN recipes r ON i.id = r.ingredient_id " +
                "SET i.stock_quantity = i.stock_quantity - (r.quantity_required * ?) " +
                "WHERE r.foods_id = ?";

        String sqlUpdateTable =
                "UPDATE restaurant_tables SET status = FALSE WHERE id = ?";

        Connection conn = null;

        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);

            Long generatedOrderId;

            // 1. Tạo đơn hàng
            try (PreparedStatement psOrder = conn.prepareStatement(
                    sqlOrder, Statement.RETURN_GENERATED_KEYS)) {

                psOrder.setString(1, order.getOrderCode());
                psOrder.setObject(2, order.getParentOrderId(), Types.BIGINT);
                psOrder.setLong(3, order.getUserId());
                psOrder.setObject(4, order.getTableId(), Types.BIGINT);
                psOrder.setObject(5, order.getVoucherId(), Types.BIGINT);
                psOrder.setString(6, order.getOrderType().name());
                psOrder.setString(7, order.getStatus().name());
                psOrder.setBigDecimal(8, order.getSubtotal());
                psOrder.setBigDecimal(9, order.getShippingFee());
                psOrder.setBigDecimal(10, order.getDiscountAmount());
                psOrder.setBigDecimal(11, order.getTotalAmount());
                psOrder.setString(12, order.getNote());

                psOrder.executeUpdate();

                try (ResultSet rs = psOrder.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException(
                                "Không thể lấy ID đơn hàng vừa tạo."
                        );
                    }

                    generatedOrderId = rs.getLong(1);
                }
            }

            // 2. Lưu chi tiết đơn hàng và xử lý kho nguyên liệu
            try (PreparedStatement psItem = conn.prepareStatement(sqlItem);
                 PreparedStatement psCheckStock = conn.prepareStatement(sqlCheckStock);
                 PreparedStatement psDeductStock = conn.prepareStatement(sqlDeductStock)) {

                for (OrderItemDTO item : order.getItems()) {

                    if (item.getFoodsId() == null
                            || item.getQuantity() == null
                            || item.getQuantity() <= 0
                            || item.getUnitPrice() == null
                            || item.getLineTotal() == null
                            || item.getStatus() == null) {

                        throw new SQLException(
                                "Thông tin món ăn trong đơn hàng không hợp lệ."
                        );
                    }

                    // 2.1. Kiểm tra nguyên liệu còn đủ không
                    psCheckStock.setLong(1, item.getFoodsId());
                    psCheckStock.setInt(2, item.getQuantity());

                    try (ResultSet rs = psCheckStock.executeQuery()) {
                        if (rs.next()) {
                            throw new SQLException(
                                    "Không đủ nguyên liệu để chế biến món: "
                                            + item.getFoodsName()
                            );
                        }
                    }

                    // 2.2. Lưu món vào order_items
                    psItem.setLong(1, generatedOrderId);
                    psItem.setLong(2, item.getFoodsId());
                    psItem.setString(3, item.getFoodsName());
                    psItem.setBigDecimal(4, item.getUnitPrice());
                    psItem.setInt(5, item.getQuantity());
                    psItem.setBigDecimal(6, item.getLineTotal());
                    psItem.setString(7, item.getStatus().name());

                    psItem.executeUpdate();

                    // 2.3. Trừ nguyên liệu
                    psDeductStock.setInt(1, item.getQuantity());
                    psDeductStock.setLong(2, item.getFoodsId());
                    psDeductStock.executeUpdate();
                }
            }

            // 3. Cập nhật trạng thái bàn nếu là đơn tại bàn
            if (order.getTableId() != null) {

                try (PreparedStatement psTable =
                             conn.prepareStatement(sqlUpdateTable)) {

                    psTable.setLong(1, order.getTableId());
                    psTable.executeUpdate();
                }
            }

            conn.commit();

            return generatedOrderId;

        } catch (SQLException | RuntimeException e) {

            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
            }

            throw e;

        } finally {

            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } finally {
                    conn.close();
                }
            }
        }
    }

    // =====================================================
    // 3. CẬP NHẬT TRẠNG THÁI MỘT MÓN
    // =====================================================

    /**
     * Dành cho màn hình Bếp:
     * Cập nhật trạng thái từng món trong order_items.
     */
    public boolean updateOrderItemStatus(
            Long orderItemId,
            OrderStatus newStatus
    ) {

        String sql = "UPDATE order_items SET status = ? WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus.name());
            ps.setLong(2, orderItemId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================
    // 4. CẬP NHẬT TRẠNG THÁI ĐƠN HÀNG
    // =====================================================

    public boolean updateOrderStatus(
            Long orderId,
            OrderStatus newStatus
    ) {

        String sql = "UPDATE orders SET status = ?, " +
                "updated_at = CURRENT_TIMESTAMP(3) " +
                "WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus.name());
            ps.setLong(2, orderId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================
    // 5. THEO DÕI MÓN ĂN THEO BÀN
    // =====================================================

    /**
     * Lấy các món đã gọi của một bàn,
     * bao gồm đơn gốc và các đơn gọi thêm.
     */
    public List<OrderItemDTO> getTrackingItemsByTable(Long tableId) {

        List<OrderItemDTO> items = new ArrayList<>();

        String sql = "SELECT oi.* FROM order_items oi " +
                "JOIN orders o ON oi.order_id = o.id " +
                "WHERE o.table_id = ? " +
                "AND o.status != 'CANCELLED' " +
                "ORDER BY oi.id DESC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, tableId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    items.add(mapOrderItem(rs));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return items;
    }

    // =====================================================
    // 6. GỘP ĐƠN GỐC VÀ CÁC ĐƠN GỌI THÊM ĐỂ THANH TOÁN
    // =====================================================

    /**
     * Dành cho Thu ngân:
     * Gộp đơn gốc và các đơn con có parent_order_id
     * để tính tổng tiền thanh toán cho một bàn.
     */
    public OrderDTO getMergedTableOrderForCheckout(Long tableId) {

        Long rootOrderId = findActiveRootOrderByTable(tableId);

        if (rootOrderId == null) {
            return null;
        }

        OrderDTO mergedOrder = new OrderDTO();
        mergedOrder.setId(rootOrderId);
        mergedOrder.setTableId(tableId);

        String sqlSum = "SELECT " +
                "MAX(CASE WHEN id = ? THEN order_code END) AS root_code, " +
                "COALESCE(SUM(subtotal), 0) AS total_sub, " +
                "COALESCE(SUM(shipping_fee), 0) AS total_shipping, " +
                "COALESCE(SUM(discount_amount), 0) AS total_disc, " +
                "COALESCE(SUM(total_amount), 0) AS total_amount " +
                "FROM orders " +
                "WHERE (id = ? OR parent_order_id = ?) " +
                "AND status != 'CANCELLED'";

        String sqlItems = "SELECT oi.* FROM order_items oi " +
                "JOIN orders o ON oi.order_id = o.id " +
                "WHERE (o.id = ? OR o.parent_order_id = ?) " +
                "AND o.status != 'CANCELLED' " +
                "AND oi.status != 'CANCELLED' " +
                "ORDER BY oi.id ASC";

        try (Connection conn = dbConnection.getConnection()) {

            // 1. Tính tổng tiền các lượt gọi món
            try (PreparedStatement psSum = conn.prepareStatement(sqlSum)) {

                psSum.setLong(1, rootOrderId);
                psSum.setLong(2, rootOrderId);
                psSum.setLong(3, rootOrderId);

                try (ResultSet rs = psSum.executeQuery()) {

                    if (rs.next()) {
                        mergedOrder.setOrderCode(rs.getString("root_code"));
                        mergedOrder.setSubtotal(rs.getBigDecimal("total_sub"));
                        mergedOrder.setShippingFee(rs.getBigDecimal("total_shipping"));
                        mergedOrder.setDiscountAmount(rs.getBigDecimal("total_disc"));
                        mergedOrder.setTotalAmount(rs.getBigDecimal("total_amount"));
                    }
                }
            }

            // 2. Lấy toàn bộ món từ đơn gốc và đơn gọi thêm
            List<OrderItemDTO> allItems = new ArrayList<>();

            try (PreparedStatement psItems = conn.prepareStatement(sqlItems)) {

                psItems.setLong(1, rootOrderId);
                psItems.setLong(2, rootOrderId);

                try (ResultSet rs = psItems.executeQuery()) {

                    while (rs.next()) {
                        allItems.add(mapOrderItem(rs));
                    }
                }
            }

            mergedOrder.setItems(allItems);

            return mergedOrder;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    // =====================================================
    // 7. LẤY ĐƠN HÀNG THEO ID
    // =====================================================

    public OrderDTO getOrderById(Long orderId) {

        String sql = "SELECT * FROM orders WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    OrderDTO order = mapOrder(rs);
                    order.setItems(getOrderItemsByOrderId(orderId));
                    return order;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    // =====================================================
    // 8. LẤY ĐƠN HÀNG THEO MÃ
    // =====================================================

    public OrderDTO getOrderByCode(String orderCode) {

        String sql = "SELECT * FROM orders WHERE order_code = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, orderCode);

            try (ResultSet rs = ps.executeQuery()) {

                if (rs.next()) {
                    OrderDTO order = mapOrder(rs);
                    order.setItems(
                            getOrderItemsByOrderId(order.getId())
                    );
                    return order;
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    // =====================================================
    // 9. LẤY LỊCH SỬ ĐƠN HÀNG CỦA NGƯỜI DÙNG
    // =====================================================

    public List<OrderDTO> getOrdersByUserId(Long userId) {

        List<OrderDTO> orders = new ArrayList<>();

        String sql = "SELECT * FROM orders " +
                "WHERE user_id = ? " +
                "ORDER BY created_at DESC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    OrderDTO order = mapOrder(rs);
                    order.setItems(
                            getOrderItemsByOrderId(order.getId())
                    );
                    orders.add(order);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return orders;
    }

    // =====================================================
    // 10. LẤY CHI TIẾT MÓN CỦA MỘT ĐƠN
    // =====================================================

    public List<OrderItemDTO> getOrderItemsByOrderId(Long orderId) {

        List<OrderItemDTO> items = new ArrayList<>();

        String sql = "SELECT * FROM order_items " +
                "WHERE order_id = ? ORDER BY id ASC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, orderId);

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    items.add(mapOrderItem(rs));
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return items;
    }

    // =====================================================
    // 11. HỦY ĐƠN HÀNG
    // =====================================================

    public boolean cancelOrder(Long orderId, String cancelReason) {

        String sql = "UPDATE orders SET " +
                "status = ?, " +
                "cancel_reason = ?, " +
                "cancelled_at = CURRENT_TIMESTAMP(3), " +
                "updated_at = CURRENT_TIMESTAMP(3) " +
                "WHERE id = ?";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, OrderStatus.CANCELLED.name());
            ps.setString(2, cancelReason);
            ps.setLong(3, orderId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // =====================================================
    // 12. MAPPER
    // =====================================================

    private OrderItemDTO mapOrderItem(ResultSet rs) throws SQLException {

        return new OrderItemDTO(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getLong("foods_id"),
                rs.getString("foods_name"),
                rs.getBigDecimal("unit_price"),
                rs.getInt("quantity"),
                rs.getBigDecimal("line_total"),
                OrderStatus.valueOf(rs.getString("status"))
        );
    }

    private OrderDTO mapOrder(ResultSet rs) throws SQLException {

        OrderDTO order = new OrderDTO();

        order.setId(rs.getLong("id"));
        order.setOrderCode(rs.getString("order_code"));

        long parentId = rs.getLong("parent_order_id");
        order.setParentOrderId(rs.wasNull() ? null : parentId);

        long userId = rs.getLong("user_id");
        order.setUserId(rs.wasNull() ? null : userId);

        long tableId = rs.getLong("table_id");
        order.setTableId(rs.wasNull() ? null : tableId);

        long voucherId = rs.getLong("voucher_id");
        order.setVoucherId(rs.wasNull() ? null : voucherId);

        order.setOrderType(
                OrderType.valueOf(rs.getString("order_type"))
        );

        order.setStatus(
                OrderStatus.valueOf(rs.getString("status"))
        );

        order.setSubtotal(rs.getBigDecimal("subtotal"));
        order.setShippingFee(rs.getBigDecimal("shipping_fee"));
        order.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        order.setTotalAmount(rs.getBigDecimal("total_amount"));

        order.setNote(rs.getString("note"));
        order.setCancelReason(rs.getString("cancel_reason"));

        long confirmedBy = rs.getLong("confirmed_by");
        order.setConfirmedBy(rs.wasNull() ? null : confirmedBy);

        Timestamp cancelledAt = rs.getTimestamp("cancelled_at");
        if (cancelledAt != null) {
            order.setCancelledAt(cancelledAt.toLocalDateTime());
        }

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) {
            order.setCreatedAt(createdAt.toLocalDateTime());
        }

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            order.setUpdatedAt(updatedAt.toLocalDateTime());
        }

        return order;
    }
}
