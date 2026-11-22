package dao;

import dto.VoucherDTO;
import dto.dbEnums;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class VoucherDAO {

    public List<VoucherDTO> getAllVouchers() throws SQLException {
        String sql = "SELECT * FROM vouchers ORDER BY id DESC";
        List<VoucherDTO> list = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                VoucherDTO v = new VoucherDTO();
                v.setId(rs.getLong("id"));
                v.setCode(rs.getString("code"));
                v.setName(rs.getString("name"));
                v.setDescription(rs.getString("description"));
                v.setDiscountType(dbEnums.DiscountType.valueOf(rs.getString("discount_type")));
                v.setDiscountValue(rs.getBigDecimal("discount_value"));
                v.setMinOrderAmount(rs.getBigDecimal("min_order_amount"));
                v.setMaxDiscountAmount(rs.getBigDecimal("max_discount_amount"));
                Timestamp start = rs.getTimestamp("start_at");
                Timestamp end = rs.getTimestamp("end_at");
                if (start != null) v.setStartAt(start.toLocalDateTime());
                if (end != null) v.setEndAt(end.toLocalDateTime());
                int limit = rs.getInt("usage_limit");
                v.setUsageLimit(rs.wasNull() ? null : limit);
                v.setUsedCount(rs.getInt("used_count"));
                v.setIsActive(rs.getBoolean("is_active"));
                Timestamp created = rs.getTimestamp("created_at");
                Timestamp updated = rs.getTimestamp("updated_at");
                if (created != null) v.setCreatedAt(created.toLocalDateTime());
                if (updated != null) v.setUpdatedAt(updated.toLocalDateTime());
                list.add(v);
            }
        }
        return list;
    }

    public boolean existsByCode(String code) throws SQLException {
        String sql = "SELECT 1 FROM vouchers WHERE UPPER(code) = UPPER(?) LIMIT 1";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean insertVoucher(VoucherDTO v) throws SQLException {
        String sql = """
            INSERT INTO vouchers (code, name, description, discount_type, discount_value,
                                  min_order_amount, max_discount_amount, start_at, end_at,
                                  usage_limit, used_count, is_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
            """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getCode());
            ps.setString(2, v.getName());
            ps.setString(3, v.getDescription());
            ps.setString(4, v.getDiscountType().name());
            ps.setBigDecimal(5, v.getDiscountValue());
            ps.setBigDecimal(6, v.getMinOrderAmount());
            if (v.getMaxDiscountAmount() != null) ps.setBigDecimal(7, v.getMaxDiscountAmount());
            else ps.setNull(7, Types.DECIMAL);
            ps.setTimestamp(8, Timestamp.valueOf(v.getStartAt()));
            ps.setTimestamp(9, Timestamp.valueOf(v.getEndAt()));
            if (v.getUsageLimit() != null) ps.setInt(10, v.getUsageLimit());
            else ps.setNull(10, Types.INTEGER);
            ps.setBoolean(11, Boolean.TRUE.equals(v.getIsActive()));
            return ps.executeUpdate() > 0;
        }
    }

    public boolean updateVoucher(VoucherDTO v) throws SQLException {
        String sql = """
            UPDATE vouchers
               SET name = ?, description = ?, discount_type = ?, discount_value = ?,
                   min_order_amount = ?, max_discount_amount = ?, start_at = ?, end_at = ?,
                   usage_limit = ?, is_active = ?, updated_at = CURRENT_TIMESTAMP(3)
             WHERE id = ?
            """;
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, v.getName());
            ps.setString(2, v.getDescription());
            ps.setString(3, v.getDiscountType().name());
            ps.setBigDecimal(4, v.getDiscountValue());
            ps.setBigDecimal(5, v.getMinOrderAmount());
            if (v.getMaxDiscountAmount() != null) ps.setBigDecimal(6, v.getMaxDiscountAmount());
            else ps.setNull(6, Types.DECIMAL);
            ps.setTimestamp(7, Timestamp.valueOf(v.getStartAt()));
            ps.setTimestamp(8, Timestamp.valueOf(v.getEndAt()));
            if (v.getUsageLimit() != null) ps.setInt(9, v.getUsageLimit());
            else ps.setNull(9, Types.INTEGER);
            ps.setBoolean(10, Boolean.TRUE.equals(v.getIsActive()));
            ps.setLong(11, v.getId());
            return ps.executeUpdate() > 0;
        }
    }

    public boolean setVoucherActive(long id, boolean active) throws SQLException {
        String sql = "UPDATE vouchers SET is_active = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, active);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }
}