package dao;

import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.OrderStatus;
import dto.OrderType;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * Data access for orders, kitchen tracking and table-session checkout.
 * SQL targets MySQL 8 / MariaDB 10.4 and the DTO schema supplied with this project.
 */
public class OrderDAO {

    private static final String ACTIVE_ROOT_SQL =
            "SELECT o.id FROM orders o " +
                    "JOIN table_sessions ts ON ts.id = o.table_session_id " +
                    "WHERE ts.table_id = ? AND ts.status = 'ACTIVE' " +
                    "AND o.parent_order_id IS NULL AND o.status <> 'CANCELLED' " +
                    "ORDER BY o.created_at ASC LIMIT 1";

    public Long findActiveRootOrderByTable(Long tableId) {
        if (tableId == null || tableId <= 0) return null;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(ACTIVE_ROOT_SQL)) {
            ps.setLong(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi tìm đơn gốc đang hoạt động", e);
        }
    }

    /**
     * A transaction owns the table lock, active session, root/sub-order selection,
     * stock deduction, order/items creation, and restaurant-table status.
     * Never use client-supplied prices or totals.
     */
    public Long createTableOrderTransaction(OrderDTO order) throws SQLException {
        if (order == null || order.getOrderType() != OrderType.DINE_IN ||
                order.getTableId() == null || order.getTableId() <= 0 ||
                order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn tại bàn phải có bàn và ít nhất một món");
        }

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock the physical table to serialize first orders and add-on orders.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM restaurant_tables WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, order.getTableId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Bàn không tồn tại");
                    }
                }

                Long sessionId = findActiveSession(conn, order.getTableId());
                if (sessionId == null || order.getTableSessionId() == null) {
                    throw new SQLException("Nhân viên chưa mở bàn hoặc thiết bị chưa được xác minh");
                }
                if (order.getTableSessionId() != null && !order.getTableSessionId().equals(sessionId)) {
                    throw new SQLException("Phiên bàn đã thay đổi; hãy tải lại thông tin bàn");
                }
                order.setTableSessionId(sessionId);

                Long rootOrderId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM orders WHERE table_session_id = ? " +
                                "AND parent_order_id IS NULL AND status <> 'CANCELLED' " +
                                "ORDER BY created_at ASC LIMIT 1")) {
                    ps.setLong(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) rootOrderId = rs.getLong(1);
                    }
                }
                order.setParentOrderId(rootOrderId); // Ignore a stale parent sent by a client.
                if (order.getOrderCode() == null || order.getOrderCode().trim().isEmpty()) {
                    order.setOrderCode("ORD" + UUID.randomUUID().toString()
                            .replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
                }

                Map<Long, BigDecimal> ingredientRequirements = new HashMap<>();
                BigDecimal subtotal = BigDecimal.ZERO;
                for (OrderItemDTO item : order.getItems()) {
                    if (item == null || item.getFoodsId() == null || item.getFoodsId() <= 0 ||
                            item.getQuantity() == null || item.getQuantity() <= 0) {
                        throw new IllegalArgumentException("Mã món hoặc số lượng không hợp lệ");
                    }
                    // Price/name/publication status come from DB, not tablet JSON.
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT name, price, is_published, is_deleted FROM foods WHERE id = ?")) {
                        ps.setLong(1, item.getFoodsId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next() || !rs.getBoolean("is_published") || rs.getBoolean("is_deleted")) {
                                throw new SQLException("Món không tồn tại hoặc đã ngừng bán: " + item.getFoodsId());
                            }
                            item.setFoodsName(rs.getString("name"));
                            item.setUnitPrice(rs.getBigDecimal("price"));
                        }
                    }
                    if (item.getUnitPrice() == null || item.getUnitPrice().signum() < 0) {
                        throw new SQLException("Giá món không hợp lệ: " + item.getFoodsId());
                    }
                    item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                    item.setStatus(OrderStatus.PENDING);
                    subtotal = subtotal.add(item.getLineTotal());
                    collectIngredients(conn, item.getFoodsId(), item.getQuantity(), ingredientRequirements);
                }

                // No voucher logic is provided here. The client cannot set discounts.
                order.setVoucherId(null);
                order.setSubtotal(subtotal);
                order.setShippingFee(BigDecimal.ZERO);
                order.setDiscountAmount(BigDecimal.ZERO);
                order.setTotalAmount(subtotal);
                order.setStatus(OrderStatus.PENDING);
                deductIngredients(conn, ingredientRequirements);

                final String insertOrder =
                        "INSERT INTO orders (order_code, parent_order_id, user_id, table_id, " +
                                "table_session_id, voucher_id, order_type, status, subtotal, shipping_fee, " +
                                "discount_amount, total_amount, note) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
                Long orderId;
                try (PreparedStatement ps = conn.prepareStatement(insertOrder, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, order.getOrderCode());
                    ps.setObject(2, order.getParentOrderId(), Types.BIGINT);
                    ps.setObject(3, order.getUserId(), Types.BIGINT); // Guests can be anonymous.
                    ps.setLong(4, order.getTableId());
                    ps.setLong(5, sessionId);
                    ps.setNull(6, Types.BIGINT);
                    ps.setString(7, order.getOrderType().name());
                    ps.setString(8, order.getStatus().name());
                    ps.setBigDecimal(9, order.getSubtotal());
                    ps.setBigDecimal(10, order.getShippingFee());
                    ps.setBigDecimal(11, order.getDiscountAmount());
                    ps.setBigDecimal(12, order.getTotalAmount());
                    ps.setString(13, order.getNote());
                    if (ps.executeUpdate() != 1) throw new SQLException("Không thể tạo đơn hàng");
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Không nhận được ID đơn hàng mới");
                        orderId = rs.getLong(1);
                    }
                }
                order.setId(orderId);

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO order_items (order_id, foods_id, foods_name, unit_price, " +
                                "quantity, line_total, status, note) VALUES (?,?,?,?,?,?,?,?)")) {
                    for (OrderItemDTO item : order.getItems()) {
                        item.setOrderId(orderId);
                        ps.setLong(1, orderId);
                        ps.setLong(2, item.getFoodsId());
                        ps.setString(3, item.getFoodsName());
                        ps.setBigDecimal(4, item.getUnitPrice());
                        ps.setInt(5, item.getQuantity());
                        ps.setBigDecimal(6, item.getLineTotal());
                        ps.setString(7, OrderStatus.PENDING.name());
                        ps.setString(8, item.getNote());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE restaurant_tables SET status = FALSE WHERE id = ?")) {
                    ps.setLong(1, order.getTableId());
                    if (ps.executeUpdate() != 1) throw new SQLException("Không thể cập nhật trạng thái bàn");
                }

                conn.commit();
                return orderId;
            } catch (SQLException | RuntimeException ex) {
                rollback(conn, ex);
                throw ex;
            }
        }
    }

    private Long findActiveSession(Connection conn, Long tableId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM table_sessions WHERE table_id = ? AND status = 'ACTIVE' FOR UPDATE")) {
            ps.setLong(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private void collectIngredients(Connection conn, long foodId, int quantity,
                                    Map<Long, BigDecimal> requirements) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT ingredient_id, quantity_required FROM recipes WHERE foods_id = ?")) {
            ps.setLong(1, foodId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long ingredientId = rs.getLong("ingredient_id");
                    BigDecimal required = rs.getBigDecimal("quantity_required");
                    if (required == null || required.signum() < 0) {
                        throw new SQLException("Định lượng nguyên liệu không hợp lệ");
                    }
                    requirements.merge(ingredientId,
                            required.multiply(BigDecimal.valueOf(quantity)), BigDecimal::add);
                }
            }
        }
    }

    private void deductIngredients(Connection conn, Map<Long, BigDecimal> requirements) throws SQLException {
        // Lock in ascending order to reduce the chance of deadlocks.
        List<Long> ingredientIds = new ArrayList<>(requirements.keySet());
        Collections.sort(ingredientIds);
        for (Long id : ingredientIds) {
            BigDecimal amount = requirements.get(id);
            if (amount.signum() == 0) continue;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ingredients SET stock_quantity = stock_quantity - ? " +
                            "WHERE id = ? AND stock_quantity >= ?")) {
                ps.setBigDecimal(1, amount);
                ps.setLong(2, id);
                ps.setBigDecimal(3, amount);
                if (ps.executeUpdate() != 1) {
                    throw new SQLException("Nguyên liệu ID " + id + " không đủ số lượng hoặc không tồn tại");
                }
            }
        }
    }

    private void restoreIngredients(Connection conn, Map<Long, BigDecimal> requirements) throws SQLException {
        for (Map.Entry<Long, BigDecimal> entry : requirements.entrySet()) {
            if (entry.getValue().signum() == 0) continue;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE ingredients SET stock_quantity = stock_quantity + ? WHERE id = ?")) {
                ps.setBigDecimal(1, entry.getValue());
                ps.setLong(2, entry.getKey());
                if (ps.executeUpdate() != 1) throw new SQLException("Không thể hoàn kho nguyên liệu");
            }
        }
    }

    public OrderDTO getOrderById(Long orderId) {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE id = ?")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                OrderDTO order = mapOrder(rs);
                order.setItems(readOrderItems(conn, orderId));
                return order;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi truy vấn đơn hàng ID " + orderId, e);
        }
    }

    public OrderDTO getOrderByCode(String orderCode) {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM orders WHERE order_code = ?")) {
            ps.setString(1, orderCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                OrderDTO order = mapOrder(rs);
                order.setItems(readOrderItems(conn, order.getId()));
                return order;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi tìm đơn hàng theo mã", e);
        }
    }

    public List<OrderDTO> getOrdersByUserId(Long userId) {
        List<OrderDTO> result = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM orders WHERE user_id = ? ORDER BY created_at DESC")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapOrder(rs));
            }
            return result;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi lấy đơn hàng theo người dùng", e);
        }
    }

    public List<OrderItemDTO> getOrderItemsByOrderId(Long orderId) {
        try (Connection conn = dbConnection.getConnection()) {
            return readOrderItems(conn, orderId);
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi truy vấn chi tiết đơn", e);
        }
    }

    private List<OrderItemDTO> readOrderItems(Connection conn, Long orderId) throws SQLException {
        List<OrderItemDTO> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM order_items WHERE order_id = ? ORDER BY id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapItem(rs));
            }
        }
        return list;
    }

    /** Kitchen queue only includes orders from ACTIVE table sessions. */
    public List<OrderDTO> getKitchenQueue() {
        List<OrderDTO> orders = new ArrayList<>();
        String sql = "SELECT o.* FROM orders o " +
                "JOIN table_sessions ts ON ts.id = o.table_session_id " +
                "WHERE ts.status = 'ACTIVE' AND o.status <> 'CANCELLED' " +
                "AND EXISTS (SELECT 1 FROM order_items oi WHERE oi.order_id = o.id " +
                "AND oi.status NOT IN ('SERVED','CANCELLED')) ORDER BY o.created_at ASC";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                OrderDTO o = mapOrder(rs);
                o.setItems(readOrderItems(conn, o.getId()));
                orders.add(o);
            }
        } catch (SQLException e) { throw new IllegalStateException("Lỗi truy vấn hàng đợi bếp", e); }
        return orders;
    }

    public Long getTableIdByOrderItem(long itemId) {
        String sql = "SELECT o.table_id FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                "JOIN table_sessions ts ON ts.id = o.table_session_id " +
                "WHERE oi.id = ? AND ts.status = 'ACTIVE'";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : null; }
        } catch (SQLException e) { throw new IllegalStateException("Lỗi tìm bàn theo món", e); }
    }

    public boolean updateOrderItemStatus(Long orderItemId, OrderStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái món không hợp lệ");
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long orderId;
                long foodId;
                int quantity;
                OrderStatus oldStatus;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT oi.order_id, oi.foods_id, oi.quantity, oi.status, " +
                                "o.status AS order_status, ts.status AS session_status " +
                                "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                                "LEFT JOIN table_sessions ts ON ts.id = o.table_session_id " +
                                "WHERE oi.id = ? FOR UPDATE")) {
                    ps.setLong(1, orderItemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        orderId = rs.getLong("order_id");
                        foodId = rs.getLong("foods_id");
                        quantity = rs.getInt("quantity");
                        oldStatus = OrderStatus.valueOf(rs.getString("status"));
                        String orderStatus = rs.getString("order_status");
                        String sessionStatus = rs.getString("session_status");
                        // A kitchen can mark an order COMPLETED before it is SERVED.
                        // The session's CLOSED state (payment) is the final barrier.
                        if ("CANCELLED".equals(orderStatus) || "CLOSED".equals(sessionStatus) ||
                                ("COMPLETED".equals(orderStatus) && sessionStatus == null)) {
                            conn.rollback(); return false;
                        }
                    }
                }
                if (oldStatus == newStatus) { conn.commit(); return true; }
                boolean permitted = (oldStatus == OrderStatus.PENDING && newStatus == OrderStatus.RECEIVED)
                        || (oldStatus == OrderStatus.RECEIVED && newStatus == OrderStatus.COOKING)
                        || (oldStatus == OrderStatus.COOKING && newStatus == OrderStatus.COMPLETED)
                        || (oldStatus == OrderStatus.COMPLETED && newStatus == OrderStatus.SERVED)
                        || ((oldStatus == OrderStatus.PENDING || oldStatus == OrderStatus.RECEIVED)
                        && newStatus == OrderStatus.CANCELLED);
                if (!permitted) { conn.rollback(); return false; }

                if (newStatus == OrderStatus.CANCELLED) {
                    Map<Long, BigDecimal> ingredients = new HashMap<>();
                    collectIngredients(conn, foodId, quantity, ingredients);
                    restoreIngredients(conn, ingredients);
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE order_items SET status = ?, " +
                                "completed_at = CASE WHEN ? IN ('COMPLETED','SERVED') " +
                                "THEN COALESCE(completed_at, NOW(3)) ELSE completed_at END WHERE id = ?")) {
                    ps.setString(1, newStatus.name());
                    ps.setString(2, newStatus.name());
                    ps.setLong(3, orderItemId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Không cập nhật được trạng thái món");
                }
                if (newStatus == OrderStatus.CANCELLED) recalculateOrderAmounts(conn, orderId);
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(conn, ex);
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi cập nhật trạng thái món", e);
        }
    }

    public Long getOrderIdByItem(long itemId) {
        try (Connection c = dbConnection.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT order_id FROM order_items WHERE id = ?")) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : null; }
        } catch (SQLException e) { throw new IllegalStateException("Loi tim don cua mon", e); }
    }

    /** Goi sau khi cap nhat mon; chi dung khi tat ca mon da che bien xong. */
    public OrderDTO completedOrderIfReady(long itemId) {
        Long orderId = getOrderIdByItem(itemId);
        if (orderId == null) return null;
        String q = "SELECT o.id FROM orders o JOIN table_sessions s ON s.id = o.table_session_id " +
                "WHERE o.id = ? AND s.status = 'ACTIVE' AND " +
                "EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id AND i.status <> 'CANCELLED') " +
                "AND NOT EXISTS (SELECT 1 FROM order_items i WHERE i.order_id = o.id " +
                "AND i.status NOT IN ('COMPLETED','SERVED','CANCELLED'))";
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(q)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
            }
        } catch (SQLException e) { throw new IllegalStateException("Khong kiem tra duoc don hoan tat", e); }
        return getOrderById(orderId);
    }

    private void recalculateOrderAmounts(Connection conn, Long orderId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "UPDATE orders SET subtotal = " +
                        "(SELECT COALESCE(SUM(line_total), 0) FROM order_items " +
                        "WHERE order_id = ? AND status <> 'CANCELLED'), " +
                        "total_amount = (SELECT COALESCE(SUM(line_total), 0) FROM order_items " +
                        "WHERE order_id = ? AND status <> 'CANCELLED') " +
                        "- COALESCE(discount_amount, 0) + COALESCE(shipping_fee, 0) WHERE id = ?")) {
            ps.setLong(1, orderId);
            ps.setLong(2, orderId);
            ps.setLong(3, orderId);
            ps.executeUpdate();
        }
    }

    public boolean updateOrderStatus(Long orderId, OrderStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        if (newStatus == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Hủy đơn phải có lý do; dùng cancelOrder()");
        }
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                OrderStatus oldStatus;
                String sessionStatus;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT o.status, ts.status AS session_status FROM orders o " +
                                "LEFT JOIN table_sessions ts ON ts.id = o.table_session_id " +
                                "WHERE o.id = ? FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { conn.rollback(); return false; }
                        oldStatus = OrderStatus.valueOf(rs.getString("status"));
                        sessionStatus = rs.getString("session_status");
                    }
                }
                if (oldStatus == OrderStatus.CANCELLED || "CLOSED".equals(sessionStatus)) {
                    conn.rollback(); return false;
                }
                if (oldStatus == newStatus) { conn.commit(); return true; }
                if (newStatus.ordinal() <= oldStatus.ordinal()) {
                    conn.rollback(); return false;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET status = ? WHERE id = ?")) {
                    ps.setString(1, newStatus.name());
                    ps.setLong(2, orderId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Không cập nhật được trạng thái đơn");
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(conn, ex);
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi cập nhật trạng thái đơn", e);
        }
    }

    public List<OrderItemDTO> getTrackingItemsByTable(Long tableId) {
        List<OrderItemDTO> list = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT oi.* FROM order_items oi " +
                             "JOIN orders o ON oi.order_id = o.id " +
                             "JOIN table_sessions ts ON ts.id = o.table_session_id " +
                             "WHERE ts.table_id = ? AND ts.status = 'ACTIVE' " +
                             "AND o.status <> 'CANCELLED' ORDER BY oi.id DESC")) {
            ps.setLong(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapItem(rs));
            }
            return list;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi theo dõi món tại bàn", e);
        }
    }

    /** All orders in the ACTIVE session, not historical orders at the same table. */
    public OrderDTO getMergedTableOrderForCheckout(Long tableId) {
        try (Connection conn = dbConnection.getConnection()) {
            Long sessionId = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM table_sessions WHERE table_id = ? AND status = 'ACTIVE'")) {
                ps.setLong(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) sessionId = rs.getLong(1);
                }
            }
            if (sessionId == null) return null;
            OrderDTO merged = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM orders WHERE table_session_id = ? " +
                            "AND parent_order_id IS NULL AND status <> 'CANCELLED' " +
                            "ORDER BY created_at ASC LIMIT 1")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) merged = mapOrder(rs);
                }
            }
            if (merged == null) return null;
            merged.setTableId(tableId);
            merged.setTableSessionId(sessionId);

            List<OrderItemDTO> allItems = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT oi.* FROM order_items oi JOIN orders o ON oi.order_id = o.id " +
                            "WHERE o.table_session_id = ? AND o.status <> 'CANCELLED' " +
                            "AND oi.status <> 'CANCELLED' ORDER BY oi.id")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) allItems.add(mapItem(rs));
                }
            }
            merged.setItems(allItems);
            BigDecimal subtotal = BigDecimal.ZERO;
            for (OrderItemDTO item : allItems) subtotal = subtotal.add(item.getLineTotal());
            BigDecimal discount = BigDecimal.ZERO;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(discount_amount),0) AS discount " +
                            "FROM orders WHERE table_session_id = ? AND status <> 'CANCELLED'")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) discount = rs.getBigDecimal("discount");
                }
            }
            merged.setSubtotal(subtotal);
            merged.setDiscountAmount(discount);
            merged.setTotalAmount(subtotal.subtract(discount).max(BigDecimal.ZERO));
            return merged;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi tổng hợp đơn để thanh toán", e);
        }
    }

    /** Cancel only orders whose items have not begun cooking, and restore stock. */
    public boolean cancelOrder(Long orderId, String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Phải cung cấp lý do hủy đơn");
        }
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT status FROM orders WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next() || !"PENDING".equals(rs.getString(1)) &&
                                !"RECEIVED".equals(rs.getString(1))) {
                            conn.rollback(); return false;
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM orders WHERE parent_order_id = ? " +
                                "AND status <> 'CANCELLED' LIMIT 1")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) { conn.rollback(); return false; }
                    }
                }
                Map<Long, BigDecimal> requirements = new HashMap<>();
                List<long[]> refundableItems = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT foods_id, quantity, status FROM order_items " +
                                "WHERE order_id = ? FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String itemStatus = rs.getString("status");
                            if (!"PENDING".equals(itemStatus) && !"RECEIVED".equals(itemStatus) &&
                                    !"CANCELLED".equals(itemStatus)) {
                                conn.rollback(); return false;
                            }
                            if (!"CANCELLED".equals(itemStatus)) {
                                refundableItems.add(new long[]{rs.getLong("foods_id"), rs.getInt("quantity")});
                            }
                        }
                    }
                }
                for (long[] item : refundableItems) {
                    collectIngredients(conn, item[0], (int) item[1], requirements);
                }
                restoreIngredients(conn, requirements);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE order_items SET status = 'CANCELLED' WHERE order_id = ? " +
                                "AND status <> 'CANCELLED'")) {
                    ps.setLong(1, orderId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET status = 'CANCELLED', cancel_reason = ?, " +
                                "cancelled_at = NOW(3) WHERE id = ?")) {
                    ps.setString(1, reason.trim());
                    ps.setLong(2, orderId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Hủy đơn không thành công");
                }
                conn.commit();
                return true;
            } catch (SQLException | RuntimeException ex) {
                rollback(conn, ex);
                throw ex;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi hủy đơn", e);
        }
    }

    private static void rollback(Connection conn, Throwable cause) {
        try { conn.rollback(); }
        catch (SQLException e) { cause.addSuppressed(e); }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long n = rs.getLong(column);
        return rs.wasNull() ? null : n;
    }

    private static OrderDTO mapOrder(ResultSet rs) throws SQLException {
        OrderDTO order = new OrderDTO();
        order.setId(nullableLong(rs, "id"));
        order.setOrderCode(rs.getString("order_code"));
        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) order.setCreatedAt(createdAt.toLocalDateTime());
        order.setParentOrderId(nullableLong(rs, "parent_order_id"));
        order.setUserId(nullableLong(rs, "user_id"));
        order.setTableId(nullableLong(rs, "table_id"));
        order.setTableSessionId(nullableLong(rs, "table_session_id"));
        order.setVoucherId(nullableLong(rs, "voucher_id"));
        String type = rs.getString("order_type");
        if (type != null) order.setOrderType(OrderType.valueOf(type));
        String status = rs.getString("status");
        if (status != null) order.setStatus(OrderStatus.valueOf(status));
        order.setSubtotal(rs.getBigDecimal("subtotal"));
        order.setShippingFee(rs.getBigDecimal("shipping_fee"));
        order.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        order.setTotalAmount(rs.getBigDecimal("total_amount"));
        order.setNote(rs.getString("note"));
        return order;
    }

    private static OrderItemDTO mapItem(ResultSet rs) throws SQLException {
        OrderItemDTO item = new OrderItemDTO(
                nullableLong(rs, "id"), nullableLong(rs, "order_id"),
                nullableLong(rs, "foods_id"), rs.getString("foods_name"),
                rs.getBigDecimal("unit_price"), rs.getInt("quantity"),
                rs.getBigDecimal("line_total"), OrderStatus.valueOf(rs.getString("status")));
        item.setNote(rs.getString("note"));
        return item;
    }
}
