package dao;

import dto.*;

import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * JDBC thuần chuyển từ OrderService/OrderRepository của RestaurantKDS.
 */
public class KdsOrderDAO {
    private static BigDecimal zero(BigDecimal x) {
        return x == null ? BigDecimal.ZERO : x;
    }

    private static Long nullableLong(ResultSet rs, String col) throws SQLException {
        return (Long) rs.getObject(col, Long.class);
    }

    private static OrderDTO readOrder(ResultSet rs) throws SQLException {
        OrderDTO o = new OrderDTO();
        o.setId(rs.getLong("id"));
        o.setOrderCode(rs.getString("order_code"));
        o.setParentOrderId(nullableLong(rs, "parent_order_id"));
        o.setUserId(nullableLong(rs, "user_id"));
        o.setTableId(nullableLong(rs, "table_id"));
        o.setVoucherId(nullableLong(rs, "voucher_id"));
        String type = rs.getString("order_type"), status = rs.getString("status");
        if (type != null) o.setOrderType(OrderType.valueOf(type));
        if (status != null) o.setStatus(OrderStatus.valueOf(status));
        o.setSubtotal(rs.getBigDecimal("subtotal"));
        o.setShippingFee(rs.getBigDecimal("shipping_fee"));
        o.setDiscountAmount(rs.getBigDecimal("discount_amount"));
        o.setTotalAmount(rs.getBigDecimal("total_amount"));
        o.setReceiverName(rs.getString("receiver_name"));
        o.setReceiverPhone(rs.getString("receiver_phone"));
        o.setShippingAddress(rs.getString("shipping_address"));
        o.setNote(rs.getString("note"));
        o.setCancelReason(rs.getString("cancel_reason"));
        o.setCancelledAt(rs.getObject("cancelled_at", LocalDateTime.class));
        o.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        return o;
    }

    private static OrderItemDTO readItem(ResultSet rs) throws SQLException {
        return new OrderItemDTO(rs.getLong("id"), rs.getLong("order_id"), rs.getLong("foods_id"), rs.getString("foods_name"),
                rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total"), OrderStatus.valueOf(rs.getString("status")));
    }

    private static List<OrderItemDTO> items(Connection c, long orderId) throws SQLException {
        List<OrderItemDTO> result = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM order_items WHERE order_id=? ORDER BY id")) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(readItem(rs));
            }
        }
        return result;
    }

    private static OrderDTO get(Connection c, long id, boolean lock) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM orders WHERE id=?" + (lock ? " FOR UPDATE" : ""))) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Không tìm thấy đơn hàng #" + id);
                OrderDTO o = readOrder(rs);
                o.setItems(items(c, id));
                return o;
            }
        }
    }

    private static int exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps.executeUpdate();
        }
    }

    private static void requireActive(OrderDTO o) throws SQLException {
        if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.COMPLETED)
            throw new SQLException("Đơn đã hoàn tất hoặc hủy");
    }

    /**
     * Tạo đơn từ GUI KDS: lấy giá món từ DB, bảo đảm lưu đơn/món/trừ kho cùng transaction.
     */
    public OrderDTO createOrder(OrderDTO order) throws SQLException {
        if (order.getUserId() == null) throw new SQLException("Cần ID tài khoản khách hàng");
        if (order.getOrderType() == null) order.setOrderType(OrderType.DINE_IN);
        if (order.getOrderType() == OrderType.DINE_IN && order.getTableId() == null)
            throw new SQLException("Đơn ăn tại bàn cần ID bàn");
        if (order.getItems() == null || order.getItems().isEmpty()) throw new SQLException("Cần ít nhất một món");
        try (Connection c = dbConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (order.getTableId() != null) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT status FROM restaurant_tables WHERE id=? FOR UPDATE")) {
                        ps.setLong(1, order.getTableId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Bàn không tồn tại");
                        }
                    }
                }
                // Dùng parent_order_id tương thích nhiều lượt gọi món trên Tablet.
                Long parent = null;
                if (order.getTableId() != null) {
                    try (PreparedStatement ps = c.prepareStatement("SELECT id FROM orders WHERE table_id=? AND parent_order_id IS NULL AND status NOT IN ('COMPLETED','CANCELLED') ORDER BY id DESC LIMIT 1")) {
                        ps.setLong(1, order.getTableId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) parent = rs.getLong(1);
                        }
                    }
                }
                order.setParentOrderId(parent);
                BigDecimal subtotal = BigDecimal.ZERO;
                for (OrderItemDTO item : order.getItems()) {
                    if (item.getFoodsId() == null || item.getQuantity() == null || item.getQuantity() <= 0)
                        throw new SQLException("ID món hoặc số lượng không hợp lệ");
                    try (PreparedStatement ps = c.prepareStatement("SELECT name,price FROM foods WHERE id=? AND is_deleted=FALSE AND is_published=TRUE")) {
                        ps.setLong(1, item.getFoodsId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Món không có trong thực đơn: " + item.getFoodsId());
                            item.setFoodsName(rs.getString("name"));
                            item.setUnitPrice(rs.getBigDecimal("price"));
                            item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                            item.setStatus(OrderStatus.PENDING);
                            subtotal = subtotal.add(item.getLineTotal());
                        }
                    }
                }
                String code = "KDS-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyMMddHHmmss")) + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
                order.setOrderCode(code);
                order.setStatus(OrderStatus.PENDING);
                order.setSubtotal(subtotal);
                order.setDiscountAmount(BigDecimal.ZERO);
                if (order.getShippingFee() == null) order.setShippingFee(BigDecimal.ZERO);
                order.setTotalAmount(subtotal.add(order.getShippingFee()));
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO orders (order_code,parent_order_id,user_id,table_id,order_type,status,subtotal,shipping_fee,discount_amount,total_amount,receiver_name,receiver_phone,shipping_address,note,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, ?,NOW(),NOW())", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, code);
                    ps.setObject(2, parent);
                    ps.setLong(3, order.getUserId());
                    ps.setObject(4, order.getTableId());
                    ps.setString(5, order.getOrderType().name());
                    ps.setString(6, "PENDING");
                    ps.setBigDecimal(7, subtotal);
                    ps.setBigDecimal(8, order.getShippingFee());
                    ps.setBigDecimal(9, BigDecimal.ZERO);
                    ps.setBigDecimal(10, order.getTotalAmount());
                    ps.setString(11, order.getReceiverName());
                    ps.setString(12, order.getReceiverPhone());
                    ps.setString(13, order.getShippingAddress());
                    ps.setString(14, order.getNote());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Không lấy được ID đơn vừa tạo");
                        order.setId(keys.getLong(1));
                    }
                }
                for (OrderItemDTO item : order.getItems()) {
                    exec(c, "INSERT INTO order_items (order_id,foods_id,foods_name,unit_price,quantity,line_total,status) VALUES (?,?,?,?,?,?,?)",
                            order.getId(), item.getFoodsId(), item.getFoodsName(), item.getUnitPrice(), item.getQuantity(), item.getLineTotal(), "PENDING");
                    exec(c, "UPDATE ingredients i JOIN recipes r ON i.id=r.ingredient_id SET i.stock_quantity=i.stock_quantity-r.quantity_required*? WHERE r.foods_id=?", item.getQuantity(), item.getFoodsId());
                }
                if (order.getTableId() != null)
                    exec(c, "UPDATE restaurant_tables SET status=FALSE WHERE id=?", order.getTableId());
                c.commit();
                return order;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * Một truy vấn duy nhất để đồng bộ hàng đợi bếp, kể cả đơn tạo từ GUI KDS.
     */
    /**
    public List<KitchenTaskDTO> getKitchenQueue() throws SQLException {
        List<KitchenTaskDTO> result = new ArrayList<>();
        String sql = "SELECT oi.id AS item_id, o.table_id, o.id AS order_id, oi.foods_name, oi.quantity, oi.status " +
                "FROM order_items oi JOIN orders o ON o.id=oi.order_id " +
                "WHERE o.status NOT IN ('COMPLETED','CANCELLED') AND oi.status NOT IN ('SERVED','CANCELLED') " +
                "ORDER BY o.created_at ASC,oi.id ASC";
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next())
                result.add(new KitchenTaskDTO(rs.getLong("item_id"), nullableLong(rs, "table_id"), rs.getLong("order_id"),
                        rs.getString("foods_name"), rs.getInt("quantity"), OrderStatus.valueOf(rs.getString("status"))));
        }
        return result;
    }

    public List<OrderDTO> getOrders(String status) throws SQLException {
        List<OrderDTO> result = new ArrayList<>();
        String sql = "SELECT * FROM orders " + (status == null || status.isBlank() ? "" : "WHERE status=? ") + "ORDER BY created_at DESC LIMIT 500";
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (status != null && !status.isBlank()) ps.setString(1, status);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(readOrder(rs));
            }
            for (OrderDTO o : result) o.setItems(items(c, o.getId()));
        }
        return result;
    }**/

    public OrderDTO getOrder(long id) throws SQLException {
        try (Connection c = dbConnection.getConnection()) {
            return get(c, id, false);
        }
    }

    public OrderDTO getActiveOrder(long tableId) throws SQLException {
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT id FROM orders WHERE table_id=? AND status NOT IN ('COMPLETED','CANCELLED') ORDER BY created_at DESC LIMIT 1")) {
            ps.setLong(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return get(c, rs.getLong(1), false);
            }
        }
        throw new SQLException("Bàn không có đơn đang hoạt động");
    }

    public void updateNote(long id, String note, OrderType type) throws SQLException {
        try (Connection c = dbConnection.getConnection()) {
            OrderDTO o = get(c, id, false);
            requireActive(o);
            exec(c, "UPDATE orders SET note=?,order_type=? WHERE id=?", note, type == null ? o.getOrderType().name() : type.name(), id);
        }
    }

    public void updateStatus(long id, OrderStatus status) throws SQLException {
        if (status == null || status == OrderStatus.CANCELLED || status == OrderStatus.COMPLETED)
            throw new SQLException("Hủy đơn dùng cancelOrder; hoàn tất đơn dùng chức năng thanh toán");
        try (Connection c = dbConnection.getConnection()) {
            OrderDTO order = get(c, id, false);
            requireActive(order);
            if (exec(c, "UPDATE orders SET status=? WHERE id=?", status.name(), id) != 1)
                throw new SQLException("Không cập nhật được đơn");
        }
    }

    public void updateItemStatus(long orderId, long itemId, OrderStatus status) throws SQLException {
        if (status == null || status == OrderStatus.CANCELLED) throw new SQLException("Trạng thái món không hợp lệ");
        try (Connection c = dbConnection.getConnection()) {
            requireActive(get(c, orderId, false));
            if (exec(c, "UPDATE order_items SET status=? WHERE id=? AND order_id=?", status.name(), itemId, orderId) != 1)
                throw new SQLException("Món không thuộc đơn hàng này");
        }
    }

    public void changeTable(long orderId, long newTableId) throws SQLException {
        try (Connection c = dbConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                OrderDTO order = get(c, orderId, true);
                requireActive(order);
                if (order.getTableId() != null && order.getTableId() == newTableId) {
                    c.commit();
                    return;
                }
                try (PreparedStatement ps = c.prepareStatement("SELECT status FROM restaurant_tables WHERE id=? FOR UPDATE")) {
                    ps.setLong(1, newTableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Không tồn tại bàn mới");
                        if (!rs.getBoolean(1)) throw new SQLException("Bàn đích đang có khách");
                    }
                }
                if (order.getParentOrderId() != null) throw new SQLException("Hãy chọn đơn gốc khi chuyển bàn");
                // Đổi cả các lượt gọi thêm thuộc đơn gốc cùng một bàn.
                exec(c, "UPDATE orders SET table_id=? WHERE id=? OR parent_order_id=?", newTableId, orderId, orderId);
                exec(c, "UPDATE restaurant_tables SET status=FALSE WHERE id=?", newTableId);
                if (order.getTableId() != null) releaseTableIfIdle(c, order.getTableId());
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void releaseTableIfIdle(Connection c, long tableId) throws SQLException {
        exec(c, "UPDATE restaurant_tables SET status=TRUE WHERE id=? AND NOT EXISTS " +
                "(SELECT 1 FROM orders WHERE table_id=? AND status NOT IN ('COMPLETED','CANCELLED'))", tableId, tableId);
    }

    public void cancelOrder(long id, String reason) throws SQLException {
        if (reason == null || reason.isBlank()) throw new SQLException("Vui lòng nhập lý do hủy");
        try (Connection c = dbConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                OrderDTO order = get(c, id, true);
                requireActive(order);
                // Hoàn lại định lượng nguyên liệu đã trừ khi đặt món.
                exec(c, "UPDATE ingredients i JOIN recipes r ON r.ingredient_id=i.id JOIN order_items oi ON oi.foods_id=r.foods_id " +
                        "SET i.stock_quantity=i.stock_quantity + r.quantity_required * oi.quantity WHERE oi.order_id=?", id);
                exec(c, "UPDATE orders SET status='CANCELLED',cancel_reason=?,cancelled_at=NOW() WHERE id=?", reason, id);
                exec(c, "UPDATE order_items SET status='CANCELLED' WHERE order_id=?", id);
                if (order.getTableId() != null) releaseTableIfIdle(c, order.getTableId());
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    private static void recalc(Connection c, long orderId) throws SQLException {
        OrderDTO o = get(c, orderId, true);
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemDTO i : o.getItems()) subtotal = subtotal.add(zero(i.getLineTotal()));
        BigDecimal discount = BigDecimal.ZERO;
        if (o.getVoucherId() != null) {
            try (PreparedStatement ps = c.prepareStatement("SELECT discount_type,discount_value,min_order_amount,max_discount_amount FROM vouchers WHERE id=?")) {
                ps.setLong(1, o.getVoucherId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && subtotal.compareTo(zero(rs.getBigDecimal("min_order_amount"))) >= 0) {
                        if ("PERCENT".equals(rs.getString("discount_type")))
                            discount = subtotal.multiply(rs.getBigDecimal("discount_value")).divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
                        else discount = rs.getBigDecimal("discount_value");
                        BigDecimal max = rs.getBigDecimal("max_discount_amount");
                        if (max != null && discount.compareTo(max) > 0) discount = max;
                    }
                }
            }
        }
        if (discount.compareTo(subtotal) > 0) discount = subtotal;
        exec(c, "UPDATE orders SET subtotal=?,discount_amount=?,total_amount=? WHERE id=?", subtotal, discount,
                subtotal.add(zero(o.getShippingFee())).subtract(discount), orderId);
    }

    public void addItems(long orderId, Map<Long, Integer> quantities) throws SQLException {
        if (quantities == null || quantities.isEmpty()) throw new SQLException("Chưa chọn món để thêm");
        try (Connection c = dbConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                OrderDTO o = get(c, orderId, true);
                requireActive(o);
                for (Map.Entry<Long, Integer> row : quantities.entrySet()) {
                    if (row.getValue() == null || row.getValue() < 1) throw new SQLException("Số lượng phải lớn hơn 0");
                    try (PreparedStatement ps = c.prepareStatement("SELECT name,price FROM foods WHERE id=? AND is_deleted=FALSE")) {
                        ps.setLong(1, row.getKey());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Món ăn không tồn tại: " + row.getKey());
                            String name = rs.getString(1);
                            BigDecimal price = rs.getBigDecimal(2);
                            exec(c, "INSERT INTO order_items (order_id,foods_id,foods_name,unit_price,quantity,line_total,status) VALUES (?,?,?,?,?,?,?)",
                                    orderId, row.getKey(), name, price, row.getValue(), price.multiply(BigDecimal.valueOf(row.getValue())), "PENDING");
                            exec(c, "UPDATE ingredients i JOIN recipes r ON r.ingredient_id=i.id SET i.stock_quantity=i.stock_quantity-r.quantity_required*? WHERE r.foods_id=?",
                                    row.getValue(), row.getKey());
                        }
                    }
                }
                recalc(c, orderId);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void applyVoucher(long orderId, String code) throws SQLException {
        try (Connection c = dbConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                OrderDTO o = get(c, orderId, true);
                requireActive(o);
                if (code == null || code.isBlank()) {
                    exec(c, "UPDATE orders SET voucher_id=NULL WHERE id=?", orderId);
                } else {
                    Long voucherId = null;
                    try (PreparedStatement ps = c.prepareStatement("SELECT id,min_order_amount FROM vouchers WHERE code=? AND is_active=TRUE " +
                            "AND NOW() BETWEEN start_at AND end_at AND (usage_limit IS NULL OR used_count<usage_limit)")) {
                        ps.setString(1, code.trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                if (zero(o.getSubtotal()).compareTo(zero(rs.getBigDecimal("min_order_amount"))) < 0)
                                    throw new SQLException("Chưa đạt giá trị đơn tối thiểu");
                                voucherId = rs.getLong("id");
                            }
                        }
                    }
                    if (voucherId == null) throw new SQLException("Voucher không hợp lệ hoặc đã hết hạn");
                    exec(c, "UPDATE orders SET voucher_id=? WHERE id=?", voucherId, orderId);
                }
                recalc(c, orderId);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void mergeOrders(long mainId, long sourceId) throws SQLException {
        if (mainId == sourceId) throw new SQLException("Không thể gộp cùng một đơn");
        try (Connection c = dbConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                OrderDTO main = get(c, mainId, true), source = get(c, sourceId, true);
                requireActive(main);
                requireActive(source);
                if (!Objects.equals(main.getTableId(), source.getTableId()))
                    throw new SQLException("Chỉ được gộp các đơn cùng một bàn");
                try (PreparedStatement check = c.prepareStatement("SELECT COUNT(*) FROM orders WHERE parent_order_id=? AND status NOT IN ('COMPLETED','CANCELLED')")) {
                    check.setLong(1, sourceId);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0)
                            throw new SQLException("Không thể gộp đơn có lượt gọi thêm chưa hoàn tất");
                    }
                }
                exec(c, "UPDATE order_items SET order_id=? WHERE order_id=?", mainId, sourceId);
                exec(c, "UPDATE orders SET status='CANCELLED',cancel_reason=?,cancelled_at=NOW(),subtotal=0,discount_amount=0,total_amount=0 WHERE id=?",
                        "Đã gộp vào đơn #" + mainId, sourceId);
                recalc(c, mainId);
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        }
    }
}
