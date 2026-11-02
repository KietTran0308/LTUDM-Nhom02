package dao;

import dto.TableDTO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.security.SecureRandom;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TableDAO {

    public static List<TableDTO> getAllTables() {
        List<TableDTO> list = new ArrayList<>();
        String sql = "SELECT id, table_name, capacity, status FROM restaurant_tables ORDER BY id ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new TableDTO(
                        rs.getLong("id"),
                        rs.getString("table_name"),
                        rs.getInt("capacity"),
                        rs.getBoolean("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
    }
    /**
     * Staff opens a NEW table session, or issues a NEW pairing code for its current
     * ACTIVE session. Existing authenticated devices stay bound to the same sessionId.
     * Plain text code is returned only to the staff who requested it.
     */
    public static String openOrIssuePairingCode(long tableId, long staffId) throws SQLException {
        if (tableId <= 0 || staffId <= 0) throw new IllegalArgumentException("Mã bàn hoặc nhân viên không hợp lệ");
        String code = generateCode();
        String digest = sha256(normalizeCode(code));
        try (Connection conn = dbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Lock table first: serialize requests and avoid duplicate ACTIVE sessions.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM restaurant_tables WHERE id = ? FOR UPDATE")) {
                    ps.setLong(1, tableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new SQLException("Bàn không tồn tại");
                    }
                }
                Long sessionId = null;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM table_sessions WHERE table_id = ? AND status = 'ACTIVE' FOR UPDATE")) {
                    ps.setLong(1, tableId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) sessionId = rs.getLong(1);
                    }
                }
                if (sessionId == null) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO table_sessions (table_id, opened_by, status, access_token_hash, opened_at) " +
                                    "VALUES (?, ?, 'ACTIVE', ?, NOW(3))")) {
                        ps.setLong(1, tableId);
                        ps.setLong(2, staffId);
                        ps.setString(3, digest);
                        ps.executeUpdate();
                    }
                } else {
                    // Reissuing a code does NOT close the current session or delete orders.
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE table_sessions SET access_token_hash = ? WHERE id = ? AND status = 'ACTIVE'")) {
                        ps.setString(1, digest);
                        ps.setLong(2, sessionId);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE restaurant_tables SET status = 0 WHERE id = ?")) {
                    ps.setLong(1, tableId);
                    ps.executeUpdate();
                }
                conn.commit();
                return code;
            } catch (SQLException | RuntimeException ex) {
                conn.rollback();
                throw ex;
            }
        }
    }

    /** Only the server compares the hash. Unverified table IDs are never authorized. */
    public static Long verifyActiveSession(long tableId, String presentedCode) throws SQLException {
        if (tableId <= 0 || presentedCode == null || presentedCode.isBlank()) return null;
        String actualHash = sha256(normalizeCode(presentedCode));
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, access_token_hash FROM table_sessions " +
                             "WHERE table_id = ? AND status = 'ACTIVE' LIMIT 1")) {
            ps.setLong(1, tableId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String expected = rs.getString("access_token_hash");
                if (expected != null && MessageDigest.isEqual(
                        actualHash.getBytes(StandardCharsets.US_ASCII),
                        expected.getBytes(StandardCharsets.US_ASCII))) {
                    return rs.getLong("id");
                }
            }
        }
        return null;
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

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 10; i++) {
            if (i == 5) sb.append('-');
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }

    private static String normalizeCode(String code) {
        return code.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
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