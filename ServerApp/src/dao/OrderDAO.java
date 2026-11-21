package dao;

import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.dbEnums;
import org.json.JSONArray;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

    public Long createTableOrderTransaction(OrderDTO order) throws SQLException {
        if (order == null || order.getOrderType() != dbEnums.OrderType.DINE_IN ||
                order.getTableId() == null || order.getTableId() <= 0 ||
                order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn tại bàn phải có bàn và ít nhất một món");
        }

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM tables WHERE id = ? FOR UPDATE")) {
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
                    item.setStatus(dbEnums.OrderStatus.PENDING);
                    subtotal = subtotal.add(item.getLineTotal());
                }

                // No voucher logic is provided here. The client cannot set discounts.
                order.setVoucherId(null);
                order.setSubtotal(subtotal);
                order.setShippingFee(BigDecimal.ZERO);
                order.setDiscountAmount(BigDecimal.ZERO);
                order.setTotalAmount(subtotal);
                order.setStatus(dbEnums.OrderStatus.PENDING);

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
                        ps.setString(7, dbEnums.OrderStatus.PENDING.name());
                        ps.setString(8, item.getNote());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE tables SET status = FALSE WHERE id = ?")) {
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

    public List<OrderDTO> getKitchenQueue() {
        List<OrderDTO> orders = new ArrayList<>();
        Map<Long, OrderDTO> orderMap = new LinkedHashMap<>();

        String sqlOrders = """
            SELECT o.*, rt.table_name AS display_table_name
            FROM orders o
            LEFT JOIN tables rt ON rt.id = o.table_id
            WHERE o.created_at >= CURDATE()
              AND o.created_at < CURDATE() + INTERVAL 1 DAY
            ORDER BY o.created_at ASC
            """;

        String sqlItems = """
            SELECT oi.*
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE o.created_at >= CURDATE()
              AND o.created_at < CURDATE() + INTERVAL 1 DAY
            ORDER BY oi.id ASC
            """;

        try (Connection conn = dbConnection.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(sqlOrders);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderDTO o = mapOrder(rs);
                    o.setTableName(rs.getString("display_table_name"));
                    o.setItems(new ArrayList<>());
                    orders.add(o);
                    orderMap.put(o.getId(), o);
                }
            }
            if (orderMap.isEmpty()) return orders;

            try (PreparedStatement ps = conn.prepareStatement(sqlItems);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItemDTO item = mapItem(rs);
                    OrderDTO parent = orderMap.get(item.getOrderId());
                    if (parent != null) parent.getItems().add(item);
                }
            }
            orders.removeIf(o -> o.getItems().isEmpty());
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi truy vấn danh sách món trong ngày của bếp", e);
        }
        return orders;
    }

    public Long getTableIdByOrderItem(long itemId) {
        // Bỏ điều kiện ts.status = 'ACTIVE' để bếp vẫn có thể xác nhận Đã phục vụ (SERVED)
        // cho những món đã nấu xong (COMPLETED) dù bàn đã thanh toán xong phiên.
        String sql = "SELECT o.table_id FROM order_items oi " +
                "JOIN orders o ON o.id = oi.order_id " +
                "WHERE oi.id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi tìm bàn theo món", e);
        }
    }

    public boolean updateOrderItemStatus(Long orderItemId, dbEnums.OrderStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái món không hợp lệ");
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long orderId;
                dbEnums.OrderStatus oldStatus;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT oi.order_id, oi.status, " +
                                "o.status AS order_status, ts.status AS session_status " +
                                "FROM order_items oi JOIN orders o ON o.id = oi.order_id " +
                                "LEFT JOIN table_sessions ts ON ts.id = o.table_session_id " +
                                "WHERE oi.id = ? FOR UPDATE")) {
                    ps.setLong(1, orderItemId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        orderId = rs.getLong("order_id");
                        oldStatus = dbEnums.OrderStatus.valueOf(rs.getString("status"));
                        String orderStatus = rs.getString("order_status");
                        String sessionStatus = rs.getString("session_status");

                        if ("CANCELLED".equals(orderStatus) ||
                                ("COMPLETED".equals(orderStatus) && sessionStatus == null)) {
                            conn.rollback();
                            return false;
                        }
                        // Nếu phiên bàn đã CLOSED (khách đã thanh toán), chỉ cho phép chốt từ COMPLETED -> SERVED
                        if ("CLOSED".equals(sessionStatus) &&
                                !(oldStatus == dbEnums.OrderStatus.COMPLETED && newStatus == dbEnums.OrderStatus.SERVED)) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                if (oldStatus == newStatus) {
                    conn.commit();
                    return true;
                }
                boolean permitted = (oldStatus == dbEnums.OrderStatus.PENDING && newStatus == dbEnums.OrderStatus.RECEIVED)
                        || (oldStatus == dbEnums.OrderStatus.RECEIVED && newStatus == dbEnums.OrderStatus.COOKING)
                        || (oldStatus == dbEnums.OrderStatus.COOKING && newStatus == dbEnums.OrderStatus.COMPLETED)
                        || (oldStatus == dbEnums.OrderStatus.COMPLETED && newStatus == dbEnums.OrderStatus.SERVED)
                        || ((oldStatus == dbEnums.OrderStatus.PENDING || oldStatus == dbEnums.OrderStatus.RECEIVED)
                        && newStatus == dbEnums.OrderStatus.CANCELLED);
                if (!permitted) {
                    conn.rollback();
                    return false;
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
                if (newStatus == dbEnums.OrderStatus.CANCELLED) recalculateOrderAmounts(conn, orderId);
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
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Loi tim don cua mon", e);
        }
    }

    /**
     * Goi sau khi cap nhat mon; chi dung khi tat ca mon da che bien xong.
     */
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
        } catch (SQLException e) {
            throw new IllegalStateException("Khong kiem tra duoc don hoan tat", e);
        }
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

    public boolean updateOrderStatus(Long orderId, dbEnums.OrderStatus newStatus) {
        if (newStatus == null) throw new IllegalArgumentException("Trạng thái không hợp lệ");
        if (newStatus == dbEnums.OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("Hủy đơn phải có lý do; dùng cancelOrder()");
        }
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                dbEnums.OrderStatus oldStatus;
                String sessionStatus;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT o.status, ts.status AS session_status FROM orders o " +
                                "LEFT JOIN table_sessions ts ON ts.id = o.table_session_id " +
                                "WHERE o.id = ? FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        oldStatus = dbEnums.OrderStatus.valueOf(rs.getString("status"));
                        sessionStatus = rs.getString("session_status");
                    }
                }
                if (oldStatus == dbEnums.OrderStatus.CANCELLED || "CLOSED".equals(sessionStatus)) {
                    conn.rollback();
                    return false;
                }
                if (oldStatus == newStatus) {
                    conn.commit();
                    return true;
                }
                if (newStatus.ordinal() <= oldStatus.ordinal()) {
                    conn.rollback();
                    return false;
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

    /**
     * All orders in the ACTIVE session, not historical orders at the same table.
     */
    public OrderDTO getMergedTableOrderForCheckout(Long tableId) {
        try (Connection conn = dbConnection.getConnection()) {
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);
            Long sessionId = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM table_sessions WHERE table_id = ? AND status = 'ACTIVE'")) {
                ps.setLong(1, tableId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) sessionId = rs.getLong(1);
                }
            }
            OrderDTO preview = sessionId == null ? null : readCheckout(conn, tableId, sessionId, false);
            conn.commit();
            return preview;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi tổng hợp đơn để thanh toán", e);
        }
    }

    // Khi thanh toán, đọc và khóa dữ liệu trong chính giao dịch ghi hóa đơn.
    static OrderDTO readCheckout(Connection conn, long tableId, long sessionId, boolean lock)
            throws SQLException {
        String rowLock = lock ? " FOR UPDATE" : "";
        OrderDTO merged = null;
        BigDecimal discount = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM orders WHERE table_session_id = ? AND status <> 'CANCELLED' ORDER BY id"
                        + rowLock)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (merged == null && rs.getObject("parent_order_id") == null) merged = mapOrder(rs);
                    discount = discount.add(rs.getBigDecimal("discount_amount"));
                }
            }
        }
        if (merged == null) return null;

        List<OrderItemDTO> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT oi.* FROM order_items oi JOIN orders o ON oi.order_id = o.id " +
                        "WHERE o.table_session_id = ? AND o.status <> 'CANCELLED' " +
                        "AND oi.status <> 'CANCELLED' ORDER BY oi.id" + rowLock)) {
            ps.setLong(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderItemDTO item = mapItem(rs);
                    items.add(item);
                    subtotal = subtotal.add(item.getLineTotal());
                }
            }
        }
        if (items.isEmpty()) return null;

        BigDecimal net = subtotal.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal tax = net.multiply(new BigDecimal("0.08")).setScale(0, RoundingMode.HALF_UP);
        merged.setTableId(tableId);
        merged.setTableSessionId(sessionId);
        merged.setItems(items);
        merged.setSubtotal(subtotal);
        merged.setDiscountAmount(discount);
        merged.setTotalAmount(net);
        merged.setCheckoutTax(tax);
        merged.setCheckoutRevision(checkoutRevision(merged));
        return merged;
    }

    private static String checkoutRevision(OrderDTO preview) {
        JSONArray snapshot = new JSONArray().put(preview.getTableSessionId()).put(preview.getId())
                .put(preview.getSubtotal()).put(preview.getDiscountAmount()).put(preview.getCheckoutTax());
        for (OrderItemDTO item : preview.getItems()) {
            snapshot.put(new JSONArray().put(item.getId()).put(item.getOrderId()).put(item.getFoodsName())
                    .put(item.getQuantity()).put(item.getUnitPrice()).put(item.getLineTotal()).put(item.getStatus()));
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(snapshot.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

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
                            conn.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM orders WHERE parent_order_id = ? " +
                                "AND status <> 'CANCELLED' LIMIT 1")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT foods_id, quantity, status FROM order_items " +
                                "WHERE order_id = ? FOR UPDATE")) {
                    ps.setLong(1, orderId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String itemStatus = rs.getString("status");
                            if (!"PENDING".equals(itemStatus) && !"RECEIVED".equals(itemStatus) &&
                                    !"CANCELLED".equals(itemStatus)) {
                                conn.rollback();
                                return false;
                            }
                        }
                    }
                }
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
        try {
            conn.rollback();
        } catch (SQLException e) {
            cause.addSuppressed(e);
        }
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
        if (type != null) order.setOrderType(dbEnums.OrderType.valueOf(type));
        String status = rs.getString("status");
        if (status != null) order.setStatus(dbEnums.OrderStatus.valueOf(status));
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
                rs.getBigDecimal("line_total"), dbEnums.OrderStatus.valueOf(rs.getString("status")));
        item.setNote(rs.getString("note"));
        return item;
    }
}
