package dao;

import dto.BillDTO;
import dto.PaymentMethod;
import dto.PaymentStatus;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class BillDAO {

    public static boolean createBillAndReleaseTable(BillDTO bill, Long tableId) {
        if (bill == null) {
            throw new IllegalArgumentException("Thong tin hoa don khong duoc rong");
        }
        if (bill.getTableSessionId() == null && tableId == null) {
            throw new IllegalArgumentException("Can tableSessionId hoac tableId de xac dinh phien ban");
        }
        if (bill.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Chua chon phuong thuc thanh toan");
        }
        if (bill.getTotalAmount() == null || bill.getTotalAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Tong thanh toan khong hop le");
        }
        if (bill.getDiscount() != null && bill.getDiscount().signum() < 0) {
            throw new IllegalArgumentException("Tien giam gia khong hop le");
        }
        if (bill.getTax() != null && bill.getTax().signum() < 0) {
            throw new IllegalArgumentException("Tien thue khong hop le");
        }

        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Khoa phien ban ACTIVE trong transaction de tranh thanh toan dong thoi.
                final boolean hasSessionId = bill.getTableSessionId() != null;
                final String findSql = hasSessionId
                        ? "SELECT id, table_id FROM table_sessions WHERE id = ? AND status = 'ACTIVE' FOR UPDATE"
                        : "SELECT id, table_id FROM table_sessions WHERE table_id = ? AND status = 'ACTIVE' FOR UPDATE";

                long sessionId;
                long actualTableId;
                try (PreparedStatement ps = conn.prepareStatement(findSql)) {
                    ps.setLong(1, hasSessionId ? bill.getTableSessionId() : tableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Khong tim thay phien ban ACTIVE de thanh toan");
                        }
                        sessionId = rs.getLong("id");
                        actualTableId = rs.getLong("table_id");
                    }
                }
                if (tableId != null && tableId.longValue() != actualTableId) {
                    throw new SQLException("tableId khong thuoc tableSessionId da chon");
                }

                // Neu bill.table_session_id UNIQUE thi DB cung tu choi khi thanh toan trung.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM bill WHERE table_session_id = ?")) {
                    ps.setLong(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) throw new SQLException("Phien ban da co hoa don");
                    }
                }

                // Khong tu tao phien CLOSED de xuat hoa don cho ban chua co phien hoat dong.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM orders WHERE table_session_id = ? AND status <> 'CANCELLED' LIMIT 1")) {
                    ps.setLong(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Phien ban chua co don hop le de thanh toan");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO bill (table_session_id, total_amount, discount, tax, " +
                                "payment_method, paid_at, payment_status) " +
                                "VALUES (?, ?, ?, ?, ?, NOW(3), 'PAID')")) {
                    ps.setLong(1, sessionId);
                    ps.setBigDecimal(2, bill.getTotalAmount());
                    ps.setBigDecimal(3, bill.getDiscount() == null ? BigDecimal.ZERO : bill.getDiscount());
                    ps.setBigDecimal(4, bill.getTax() == null ? BigDecimal.ZERO : bill.getTax());
                    ps.setString(5, bill.getPaymentMethod().name());
                    ps.executeUpdate();
                }

                // Cap nhat tat ca luot goi mon thuoc phien ban, khong dung parent_order_id.
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
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Dong phien ban that bai");
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE restaurant_tables SET status = 1 WHERE id = ?")) {
                    ps.setLong(1, actualTableId);
                    if (ps.executeUpdate() != 1) {
                        throw new SQLException("Khong the giai phong ban");
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    public List<BillDTO> getAllBills() {
        List<BillDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM bill ORDER BY created_at DESC";

        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                String pmStr = rs.getString("payment_method");
                PaymentMethod pm = pmStr == null ? null : PaymentMethod.valueOf(pmStr);
                String statusStr = rs.getString("payment_status");
                PaymentStatus paymentStatus = statusStr == null ? null : PaymentStatus.valueOf(statusStr);
                Timestamp paidAt = rs.getTimestamp("paid_at");
                Timestamp createdAt = rs.getTimestamp("created_at");

                list.add(new BillDTO(
                        rs.getLong("id"),
                        rs.getLong("table_session_id"),
                        rs.getBigDecimal("total_amount"),
                        rs.getBigDecimal("discount"),
                        rs.getBigDecimal("tax"),
                        pm,
                        paidAt == null ? null : paidAt.toLocalDateTime(),
                        paymentStatus,
                        createdAt == null ? null : createdAt.toLocalDateTime()
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }
}
