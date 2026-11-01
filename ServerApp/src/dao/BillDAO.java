package dao;

import dto.BillDTO;

import java.sql.*;

public class BillDAO {

    /**
     * Thanh toán hóa đơn & Giải phóng bàn (status = TRUE)
     */
    public static boolean createBillAndReleaseTable(BillDTO bill, Long tableId) {
        if (bill == null || tableId == null
                || bill.getTableSessionId() == null
                || bill.getPaymentMethod() == null) {
            return false;
        }

        String sqlSession = """
            SELECT id FROM table_sessions
            WHERE id = ? AND table_id = ? AND status = 'ACTIVE'
            FOR UPDATE
            """;

        String sqlBill = """
            INSERT INTO bill (
                table_session_id, total_amount, discount, tax,
                payment_method, payment_status, paid_at
            )
            VALUES (?, ?, ?, ?, ?, 'PAID', CURRENT_TIMESTAMP(3))
            """;

        String sqlCloseSession = """
            UPDATE table_sessions
            SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP(3)
            WHERE id = ? AND status = 'ACTIVE'
            """;

        String sqlTable = """
            UPDATE restaurant_tables SET status = TRUE WHERE id = ?
            """;

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Xác nhận phiên còn mở và thuộc đúng bàn.
                try (PreparedStatement ps = conn.prepareStatement(sqlSession)) {
                    ps.setLong(1, bill.getTableSessionId());
                    ps.setLong(2, tableId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                    }
                }

                // Lưu hóa đơn đã thanh toán.
                try (PreparedStatement ps = conn.prepareStatement(sqlBill)) {
                    ps.setLong(1, bill.getTableSessionId());
                    ps.setBigDecimal(2, bill.getTotalAmount());
                    ps.setBigDecimal(3, bill.getDiscount());
                    ps.setBigDecimal(4, bill.getTax());
                    ps.setString(5, bill.getPaymentMethod().name());
                    ps.executeUpdate();
                }

                // Kết thúc lượt khách.
                try (PreparedStatement ps = conn.prepareStatement(sqlCloseSession)) {
                    ps.setLong(1, bill.getTableSessionId());

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Không thể đóng phiên bàn");
                    }
                }

                // Đánh dấu bàn trống.
                try (PreparedStatement ps = conn.prepareStatement(sqlTable)) {
                    ps.setLong(1, tableId);

                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Không tìm thấy bàn");
                    }
                }

                conn.commit();
                return true;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public java.util.List<BillDTO> getAllBills() {
        java.util.List<BillDTO> list = new java.util.ArrayList<>();
        String sql = "SELECT * FROM bill ORDER BY paid_at DESC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new BillDTO(
                        rs.getLong("id"),
                        rs.getLong("table_session_id"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("discount"),
                        rs.getBigDecimal("tax"),
                        rs.getString("payment_method") == null
                                ? null
                                : dto.PaymentMethod.valueOf(rs.getString("payment_method")),
                        rs.getObject("paid_at", java.time.LocalDateTime.class)
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
    }
}