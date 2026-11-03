package dao;

import dto.TableDTO;
import java.sql.*;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

public class TableDAO {

    public static List<TableDTO> getAllTables() {
        List<TableDTO> list = new ArrayList<>();
        String sql = "SELECT t.id, t.table_name, t.capacity, t.status, " +
                "s.id AS active_session_id FROM restaurant_tables t " +
                "LEFT JOIN table_sessions s ON s.table_id = t.id AND s.status = 'ACTIVE' " +
                "ORDER BY t.id ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                TableDTO t = new TableDTO(rs.getLong("id"), rs.getString("table_name"),
                        rs.getInt("capacity"), rs.getBoolean("status"));
                long sid = rs.getLong("active_session_id");
                if (!rs.wasNull()) t.setActiveSessionId(sid);
                list.add(t);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Khong lay duoc danh sach ban", e);
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
    }
    /** POS mo phien moi neu ban trong; tra ve phien ACTIVE neu ban dang phuc vu.
     * Khong phat ma ghep: ipad nhan vien ENTER_CUSTOMER_MODE truc tiep.
     */
    public static long openOrGetActiveSession(long tableId, long staffId) throws SQLException {
        if (tableId <= 0 || staffId <= 0) throw new IllegalArgumentException("Ma ban hoac nhan vien khong hop le");
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Khoa ban de hai POS khong cung tao hai phien ACTIVE.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM restaurant_tables WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, tableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Ban khong ton tai");
                    }
                }
                Long active = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM table_sessions WHERE table_id = ? AND status = 'ACTIVE' FOR UPDATE")) {
                    ps.setLong(1, tableId);
                    try (ResultSet rs = ps.executeQuery()) { if (rs.next()) active = rs.getLong(1); }
                }
                if (active == null) {
                    // NOT NULL access_token_hash cua schema cu van duoc gan gia tri ngau nhien
                    // NHUNG khong cap cho khach/ipad, khong dung trong flow moi.
                    String randomHash = sha256(java.util.UUID.randomUUID().toString()
                            + java.util.UUID.randomUUID());
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO table_sessions (table_id, opened_by, status, access_token_hash, opened_at) " +
                                    "VALUES (?, ?, 'ACTIVE', ?, NOW(3))", Statement.RETURN_GENERATED_KEYS)) {
                        ps.setLong(1, tableId);
                        ps.setLong(2, staffId);
                        ps.setString(3, randomHash);
                        if (ps.executeUpdate() != 1) throw new SQLException("Khong mo duoc phien ban");
                        try (ResultSet rs = ps.getGeneratedKeys()) {
                            if (!rs.next()) throw new SQLException("Khong lay duoc sessionId moi");
                            active = rs.getLong(1);
                        }
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE restaurant_tables SET status = 0 WHERE id = ?")) {
                    ps.setLong(1, tableId);
                    ps.executeUpdate();
                }
                conn.commit();
                return active;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Rechecked for every order, so a device cannot order after checkout. */
    public static boolean isActiveSession(long tableId, long sessionId) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM table_sessions WHERE id = ? AND table_id = ? " +
                             "AND status = 'ACTIVE' LIMIT 1")) {
            ps.setLong(1, sessionId);
            ps.setLong(2, tableId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** Phien ban ACTIVE thuoc dung tableId; dung cho ENTER_CUSTOMER_MODE. */
    public static boolean sessionBelongsToTable(long tableId, long sessionId) throws SQLException {
        return isActiveSession(tableId, sessionId);
    }

    private static String sha256(String s) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : bytes) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

}