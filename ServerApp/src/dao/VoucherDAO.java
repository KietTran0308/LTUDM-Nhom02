package dao;

import dto.DiscountType;
import dto.VoucherDTO;
import java.sql.*;
import java.time.LocalDateTime;

public class VoucherDAO {
    public VoucherDTO getValidVoucherByCode(String code) {
        String sql = "SELECT * FROM vouchers " +
                "WHERE code = ? AND is_active = TRUE " +
                "AND NOW(3) BETWEEN start_at AND end_at " +
                "AND (usage_limit IS NULL OR used_count < usage_limit)";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, code);
            rs = ps.executeQuery();

            if (rs.next()) {
                return new VoucherDTO(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        DiscountType.valueOf(rs.getString("discount_type")),
                        rs.getBigDecimal("discount_value"),
                        rs.getBigDecimal("min_order_amount"),
                        rs.getBigDecimal("max_discount_amount"),
                        rs.getObject("start_at", LocalDateTime.class),
                        rs.getObject("end_at", LocalDateTime.class),
                        rs.getObject("usage_limit", Integer.class),
                        rs.getInt("used_count"),
                        rs.getBoolean("is_active"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class)
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return null;
    }
}