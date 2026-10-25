package dao;

import dto.BillDTO;

import java.sql.*;

public class BillDAO {

    /**
     * Thanh toán hóa đơn & Giải phóng bàn (status = TRUE)
     */
    public static boolean createBillAndReleaseTable(BillDTO bill, Long tableId) {
        String sqlBill = "INSERT INTO bill (order_id, total_amount, discount, tax, payment_method) VALUES (?, ?, ?, ?, ?)";
        String sqlOrder = "UPDATE orders SET status = 'COMPLETED' WHERE id = ? OR parent_order_id = ?";
        String sqlTable = "UPDATE restaurant_tables SET status = TRUE WHERE id = ?";

        Connection conn = null;
        PreparedStatement psBill = null;
        PreparedStatement psOrder = null;
        PreparedStatement psTable = null;

        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);

            psBill = conn.prepareStatement(sqlBill);
            psBill.setLong(1, bill.getOrderId());
            psBill.setBigDecimal(2, bill.getTotalAmount());
            psBill.setBigDecimal(3, bill.getDiscount());
            psBill.setBigDecimal(4, bill.getTax());
            psBill.setString(5, bill.getPaymentMethod().name());
            psBill.executeUpdate();

            psOrder = conn.prepareStatement(sqlOrder);
            psOrder.setLong(1, bill.getOrderId());
            psOrder.setLong(2, bill.getOrderId());
            psOrder.executeUpdate();

            if (tableId != null) {
                psTable = conn.prepareStatement(sqlTable);
                psTable.setLong(1, tableId);
                psTable.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ex) {}
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (psOrder != null) psOrder.close();
                if (psTable != null) psTable.close();
            } catch (SQLException ignored) {}
            dbConnection.closeConnection(conn, psBill, null);
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
                        rs.getLong("order_id"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("discount"),
                        rs.getBigDecimal("tax"),
                        dto.PaymentMethod.valueOf(rs.getString("payment_method")),
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