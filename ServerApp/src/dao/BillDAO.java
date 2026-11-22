package dao;

import dto.BillDTO;
import dto.OrderDTO;
import dto.dbEnums;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class BillDAO {

    public static void createBillAndReleaseTable(BillDTO bill, long tableId, String revision) {
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Khóa bàn rồi khóa phiên, cùng thứ tự với thao tác mở bàn và gọi thêm món.
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM tables WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, tableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Bàn không tồn tại.");
                    }
                }
                long sessionId = bill.getTableSessionId();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM table_sessions WHERE id = ? AND table_id = ? AND status = 'ACTIVE' FOR UPDATE")) {
                    ps.setLong(1, sessionId);
                    ps.setLong(2, tableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("Phiên bàn đã đóng hoặc đã thay đổi.");
                    }
                }

                OrderDTO current = OrderDAO.readCheckout(conn, tableId, sessionId, true);
                if (current == null || !revision.equals(current.getCheckoutRevision()))
                    throw new IllegalStateException("Hóa đơn đã thay đổi. Hãy xem lại trước khi thanh toán.");

                Long customerId = bill.getUserId();
                if (customerId != null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT u.id FROM users u JOIN roles r ON r.roleID = u.roleID " +
                                    "WHERE u.id = ? AND u.is_active = 1 AND r.nameRole = 'CUSTOMER'")) {
                        ps.setLong(1, customerId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException("Thành viên không hợp lệ hoặc đã bị khóa.");
                        }
                    }
                }

                // Giá trị ghi vào hóa đơn luôn lấy từ dữ liệu server vừa tính lại.
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO bill (table_session_id, user_id, total_amount, discount, tax, " +
                                "payment_method, paid_at, payment_status) " +
                                "VALUES (?, ?, ?, ?, ?, ?, NOW(3), 'PAID')", PreparedStatement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, sessionId);
                    if (customerId == null) ps.setNull(2, java.sql.Types.BIGINT);
                    else ps.setLong(2, customerId);
                    ps.setBigDecimal(3, current.getCheckoutTotal());
                    ps.setBigDecimal(4, current.getDiscountAmount());
                    ps.setBigDecimal(5, current.getCheckoutTax());
                    ps.setString(6, bill.getPaymentMethod().name());
                    ps.executeUpdate();
                    if (customerId != null) {
                        try (ResultSet keys = ps.getGeneratedKeys()) {
                            if (!keys.next()) throw new SQLException("Không lấy được mã hóa đơn.");
                            earnPoints(conn, customerId, keys.getLong(1), current.getCheckoutTotal());
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "WHERE v.id IN (SELECT DISTINCT o.voucher_id FROM orders o WHERE o.table_session_id = ? AND o.status <> 'CANCELLED' AND o.voucher_id IS NOT NULL)")) {
                    ps.setLong(1, sessionId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE orders SET status = 'COMPLETED' " +
                                "WHERE table_session_id = ? AND status <> 'CANCELLED'")) {
                    ps.setLong(1, sessionId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE table_sessions SET status = 'CLOSED', closed_at = NOW(3) " +
                                "WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setLong(1, sessionId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Không đóng được phiên bàn.");
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE tables SET status = 1 WHERE id = ?")) {
                    ps.setLong(1, tableId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Không giải phóng được bàn.");
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không lưu được hóa đơn. Hãy tải lại bàn và kiểm tra trước khi thử lại.", e);
        }
    }

    // 10.000 đồng thanh toán = 1 điểm; hóa đơn và điểm cùng commit hoặc cùng rollback.
    private static void earnPoints(Connection conn, long customerId, long billId, BigDecimal paidAmount)
            throws SQLException {
        int earned = paidAmount.max(BigDecimal.ZERO).divideToIntegralValue(BigDecimal.valueOf(10000)).intValueExact();
        if (earned == 0) return;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO points (user_id, current_points, total_earned_points) VALUES (?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE current_points = current_points + VALUES(current_points), " +
                        "total_earned_points = total_earned_points + VALUES(total_earned_points)")) {
            ps.setLong(1, customerId);
            ps.setInt(2, earned);
            ps.setInt(3, earned);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO point_transactions (user_id, bill_id, type, points_change, note) " +
                        "VALUES (?, ?, 'EARN', ?, 'Tích điểm khi thanh toán tại POS')")) {
            ps.setLong(1, customerId);
            ps.setLong(2, billId);
            ps.setInt(3, earned);
            ps.executeUpdate();
        }
    }

    public record TopFood(String name, long quantity) {}

    public record ReportRows(List<BillDTO> bills, List<TopFood> topFoods) {}

    public ReportRows getReportRows(LocalDate from, LocalDate to, dbEnums.PaymentMethod payment, String billNumber)
            throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<String> params = new ArrayList<>();
        if (from != null && to != null) {
            where.append(" AND b.paid_at BETWEEN ? AND ?");
            params.add(from + " 00:00:00");
            params.add(to + " 23:59:59.999"); // paid_at có độ chính xác đến mili giây.
        }
        if (payment != null) {
            where.append(" AND b.payment_method = ?");
            params.add(payment.name());
        }
        if (billNumber != null && !billNumber.isBlank()) {
            where.append(" AND LOCATE(?, CAST(b.id AS CHAR)) > 0");
            params.add(billNumber);
        }

        String billSql = "SELECT b.* FROM bill b" + where + " ORDER BY b.created_at DESC";
        String topSql = """
                SELECT f.name, SUM(i.quantity) AS quantity
                FROM bill b
                JOIN orders o ON o.table_session_id = b.table_session_id AND o.status <> 'CANCELLED'
                JOIN order_items i ON i.order_id = o.id AND i.status <> 'CANCELLED'
                JOIN foods f ON f.id = i.foods_id
                """ + where + """
                 AND b.payment_status = 'PAID' AND b.paid_at IS NOT NULL
                 GROUP BY f.id, f.name
                 ORDER BY quantity DESC, f.id
                 LIMIT 10
                """;
        try (Connection conn = dbConnection.getConnection()) {
            // Hai truy vấn cùng nhìn một trạng thái CSDL khi POS đang chốt hóa đơn.
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setReadOnly(true);
            conn.setAutoCommit(false);
            try (PreparedStatement bills = conn.prepareStatement(billSql);
                 PreparedStatement top = conn.prepareStatement(topSql)) {
                for (int i = 0; i < params.size(); i++) {
                    bills.setString(i + 1, params.get(i));
                    top.setString(i + 1, params.get(i));
                }
                List<BillDTO> billRows = readBills(bills);
                List<TopFood> topFoods = new ArrayList<>();
                try (ResultSet rows = top.executeQuery()) {
                    while (rows.next()) {
                        topFoods.add(new TopFood(rows.getString("name"), rows.getLong("quantity")));
                    }
                }
                conn.commit();
                return new ReportRows(billRows, topFoods);
            }
        }
    }

    public List<BillDTO> getAllBills() {
        String sql = "SELECT * FROM bill ORDER BY created_at DESC";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            return readBills(ps);
        } catch (SQLException e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private List<BillDTO> readBills(PreparedStatement ps) throws SQLException {
        List<BillDTO> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String pmStr = rs.getString("payment_method");
                dbEnums.PaymentMethod pm = pmStr == null ? null : dbEnums.PaymentMethod.valueOf(pmStr);
                String statusStr = rs.getString("payment_status");
                dbEnums.PaymentStatus paymentStatus = statusStr == null ? null : dbEnums.PaymentStatus.valueOf(statusStr);
                Timestamp paidAt = rs.getTimestamp("paid_at");
                Timestamp createdAt = rs.getTimestamp("created_at");

                BillDTO dto = new BillDTO(
                        rs.getLong("id"),
                        rs.getLong("table_session_id"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("discount"),
                        rs.getBigDecimal("tax"),
                        pm,
                        paidAt == null ? null : paidAt.toLocalDateTime(),
                        paymentStatus,
                        createdAt == null ? null : createdAt.toLocalDateTime()
                );
                long uid = rs.getLong("user_id");
                if (!rs.wasNull()) dto.setUserId(uid);
                list.add(dto);
            }
        }
        return list;
    }
}
