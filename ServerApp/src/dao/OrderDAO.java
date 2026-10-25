package dao;

import dto.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class OrderDAO {

    public Long findActiveRootOrderByTable(Long tableId) {
        String sql = "SELECT id FROM orders " +
                "WHERE table_id = ? AND parent_order_id IS NULL " +
                "AND status NOT IN ('COMPLETED', 'CANCELLED') " +
                "ORDER BY created_at DESC LIMIT 1";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, tableId);
            rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return null;
    }

    /**
     * Tạo đơn hàng mới từ Tablet (Kèm Transaction: Lưu Order -> Lưu OrderItems -> Trừ kho Ingredients -> Cập nhật trạng thái Bàn)
     */
    public Long createTableOrderTransaction(OrderDTO order) throws SQLException {
        String sqlOrder = "INSERT INTO orders (order_code, parent_order_id, user_id, table_id, voucher_id, " +
                "order_type, status, subtotal, shipping_fee, discount_amount, total_amount, note) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        String sqlItem = "INSERT INTO order_items (order_id, foods_id, foods_name, unit_price, quantity, line_total, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?)";

        // Câu lệnh tự động trừ kho nguyên liệu dựa theo công thức (recipes)
        String sqlDeductStock = "UPDATE ingredients i " +
                "JOIN recipes r ON i.id = r.ingredient_id " +
                "SET i.stock_quantity = i.stock_quantity - (r.quantity_required * ?) " +
                "WHERE r.foods_id = ?";

        String sqlUpdateTable = "UPDATE restaurant_tables SET status = FALSE WHERE id = ?";

        Connection conn = null;
        PreparedStatement psOrder = null;
        PreparedStatement psItem = null;
        PreparedStatement psStock = null;
        PreparedStatement psTable = null;
        ResultSet rsKeys = null;

        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu Transaction

            // 1. Insert bảng orders
            psOrder = conn.prepareStatement(sqlOrder, Statement.RETURN_GENERATED_KEYS);
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

            rsKeys = psOrder.getGeneratedKeys();
            Long generatedOrderId = null;
            if (rsKeys.next()) {
                generatedOrderId = rsKeys.getLong(1);
            } else {
                throw new SQLException("Không thể lấy ID đơn hàng vừa tạo.");
            }

            // 2. Insert hàng loạt bảng order_items & trừ kho nguyên liệu
            psItem = conn.prepareStatement(sqlItem);
            psStock = conn.prepareStatement(sqlDeductStock);

            for (OrderItemDTO item : order.getItems()) {
                psItem.setLong(1, generatedOrderId);
                psItem.setLong(2, item.getFoodsId());
                psItem.setString(3, item.getFoodsName());
                psItem.setBigDecimal(4, item.getUnitPrice());
                psItem.setInt(5, item.getQuantity());
                psItem.setBigDecimal(6, item.getLineTotal());
                psItem.setString(7, item.getStatus().name());
                psItem.addBatch();

                // Trừ định lượng nguyên liệu tương ứng với số lượng món khách gọi
                psStock.setInt(1, item.getQuantity());
                psStock.setLong(2, item.getFoodsId());
                psStock.addBatch();
            }
            psItem.executeBatch();
            psStock.executeBatch();

            // 3. Đánh dấu bàn đang có khách ngồi (status = FALSE)
            if (order.getTableId() != null) {
                psTable = conn.prepareStatement(sqlUpdateTable);
                psTable.setLong(1, order.getTableId());
                psTable.executeUpdate();
            }

            conn.commit(); // Thành công toàn bộ
            return generatedOrderId;

        } catch (SQLException e) {
            if (conn != null) conn.rollback(); // Hủy toàn bộ nếu có lỗi
            throw e;
        } finally {
            if (psTable != null) psTable.close();
            if (psStock != null) psStock.close();
            if (psItem != null) psItem.close();
            dbConnection.closeConnection(conn, psOrder, rsKeys);
        }
    }

    /**
     * Dành cho màn hình Bếp: Cập nhật trạng thái từng món (PENDING -> COOKING -> SERVED)
     */
    public boolean updateOrderItemStatus(Long orderItemId, OrderStatus newStatus) {
        String sql = "UPDATE order_items SET status = ? WHERE id = ?";
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, newStatus.name());
            ps.setLong(2, orderItemId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }

    /**
     * Lấy toàn bộ các món đã gọi của 1 bàn (bao gồm cả đơn gốc và các lượt gọi thêm parent_order_id)
     * Giúp khách hàng theo dõi trạng thái trực tiếp trên Tablet
     */
    public List<OrderItemDTO> getTrackingItemsByTable(Long tableId) {
        List<OrderItemDTO> items = new ArrayList<>();
        String sql = "SELECT oi.* FROM order_items oi " +
                "JOIN orders o ON oi.order_id = o.id " +
                "WHERE o.table_id = ? AND o.status NOT IN ('CANCELLED') " +
                "ORDER BY oi.id DESC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, tableId);
            rs = ps.executeQuery();

            while (rs.next()) {
                items.add(new OrderItemDTO(
                        rs.getLong("id"),
                        rs.getLong("order_id"),
                        rs.getLong("foods_id"),
                        rs.getString("foods_name"),
                        rs.getBigDecimal("unit_price"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("line_total"),
                        OrderStatus.valueOf(rs.getString("status"))
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return items;
    }

    /**
     * Dành cho Thu ngân (Staff POS): Gộp đơn gốc và tất cả các lượt gọi thêm (parent_order_id) của 1 bàn
     */
    public OrderDTO getMergedTableOrderForCheckout(Long tableId) {
        Long rootOrderId = findActiveRootOrderByTable(tableId);
        if (rootOrderId == null) return null;

        OrderDTO mergedOrder = new OrderDTO();
        mergedOrder.setId(rootOrderId);
        mergedOrder.setTableId(tableId);

        // 1. Tính tổng Subtotal & Discount từ đơn gốc và tất cả các đơn con gọi thêm
        String sqlSum = "SELECT MAX(order_code) AS root_code, " +
                "SUM(subtotal) AS total_sub, " +
                "SUM(discount_amount) AS total_disc " +
                "FROM orders " +
                "WHERE (id = ? OR parent_order_id = ?) AND status != 'CANCELLED'";

        // 2. Lấy toàn bộ món ăn từ tất cả các lượt gọi của bàn này
        String sqlItems = "SELECT oi.* FROM order_items oi " +
                "JOIN orders o ON oi.order_id = o.id " +
                "WHERE (o.id = ? OR o.parent_order_id = ?) " +
                "AND o.status != 'CANCELLED' AND oi.status != 'CANCELLED' " +
                "ORDER BY oi.id ASC";

        Connection conn = null;
        PreparedStatement psSum = null;
        PreparedStatement psItems = null;
        ResultSet rsSum = null;
        ResultSet rsItems = null;

        try {
            conn = dbConnection.getConnection();

            psSum = conn.prepareStatement(sqlSum);
            psSum.setLong(1, rootOrderId);
            psSum.setLong(2, rootOrderId);
            rsSum = psSum.executeQuery();

            if (rsSum.next()) {
                mergedOrder.setOrderCode(rsSum.getString("root_code"));
                mergedOrder.setSubtotal(rsSum.getBigDecimal("total_sub"));
                mergedOrder.setDiscountAmount(rsSum.getBigDecimal("total_disc"));
            }

            psItems = conn.prepareStatement(sqlItems);
            psItems.setLong(1, rootOrderId);
            psItems.setLong(2, rootOrderId);
            rsItems = psItems.executeQuery();

            List<OrderItemDTO> allItems = new ArrayList<>();
            while (rsItems.next()) {
                allItems.add(new OrderItemDTO(
                        rsItems.getLong("id"),
                        rsItems.getLong("order_id"),
                        rsItems.getLong("foods_id"),
                        rsItems.getString("foods_name"),
                        rsItems.getBigDecimal("unit_price"),
                        rsItems.getInt("quantity"),
                        rsItems.getBigDecimal("line_total"),
                        OrderStatus.valueOf(rsItems.getString("status"))
                ));
            }
            mergedOrder.setItems(allItems);
            return mergedOrder;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (rsItems != null) rsItems.close();
                if (psItems != null) psItems.close();
            } catch (SQLException ignored) {}
            dbConnection.closeConnection(conn, psSum, rsSum);
        }
    }
}