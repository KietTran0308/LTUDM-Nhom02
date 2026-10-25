package dao;

import dto.*;

import java.sql.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chuyển đổi Repository/JPA của RestaurantKDS thành JDBC thuần, dùng dbConnection của project gốc.
 */
public class KdsManagementDAO {
    private static void bind(PreparedStatement ps, Object... values) throws SQLException {
        for (int i = 0; i < values.length; i++) ps.setObject(i + 1, values[i]);
    }

    private static long insert(String sql, Object... values) throws SQLException {
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, values);
            if (ps.executeUpdate() != 1) throw new SQLException("Không thể thêm bản ghi");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        throw new SQLException("Không thể lấy ID mới");
    }

    private static void update(String sql, Object... values) throws SQLException {
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, values);
            if (ps.executeUpdate() == 0) throw new SQLException("Không tìm thấy bản ghi hoặc không có thay đổi");
        }
    }

    public List<CategoryDTO> categories() {
        return new CategoryDAO().getAllCategories();
    }

    public long addCategory(CategoryDTO v) throws SQLException {
        return insert("INSERT INTO categories (name,slug,description,created_at) VALUES (?,?,?,NOW())", v.getName(), v.getSlug(), v.getDescription());
    }

    public void updateCategory(CategoryDTO v) throws SQLException {
        update("UPDATE categories SET name=?,slug=?,description=? WHERE id=?", v.getName(), v.getSlug(), v.getDescription(), v.getId());
    }

    public void deleteCategory(long id) throws SQLException {
        update("DELETE FROM categories WHERE id=?", id);
    }

    public List<TableDTO> tables() {
        return TableDAO.getAllTables();
    }

    public long addTable(TableDTO v) throws SQLException {
        return insert("INSERT INTO restaurant_tables (table_name,capacity,status) VALUES (?,?,?)", v.getTableName(), v.getCapacity(), v.getStatus());
    }

    public void updateTable(TableDTO v) throws SQLException {
        update("UPDATE restaurant_tables SET table_name=?,capacity=?,status=? WHERE id=?", v.getTableName(), v.getCapacity(), v.getStatus(), v.getId());
    }

    public void deleteTable(long id) throws SQLException {
        update("DELETE FROM restaurant_tables WHERE id=?", id);
    }

    public List<FoodDTO> foods(String keyword, Long categoryId) {
        String key = keyword == null ? "" : keyword.trim().toLowerCase(java.util.Locale.ROOT);
        List<FoodDTO> found = new ArrayList<>();
        for (FoodDTO food : new FoodDAO().getAllFoodsForAdmin()) {
            if (categoryId != null && !categoryId.equals(food.getCategoryId())) continue;
            String text = (food.getName() == null ? "" : food.getName()) + " " + (food.getDescription() == null ? "" : food.getDescription());
            if (text.toLowerCase(java.util.Locale.ROOT).contains(key)) found.add(food);
        }
        return found;
    }

    public List<UserDTO> users() throws SQLException {
        List<UserDTO> data = new ArrayList<>();
        String sql = "SELECT u.id,u.roleID,u.email,u.full_name,u.phone,u.is_active,u.created_at,u.updated_at,r.nameRole " +
                "FROM users u LEFT JOIN roles r ON u.roleID=r.roleID ORDER BY u.id DESC";
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setRoleID(rs.getLong("roleID"));
                u.setEmail(rs.getString("email"));
                u.setFullName(rs.getString("full_name"));
                u.setPhone(rs.getString("phone"));
                u.setIsActive(rs.getBoolean("is_active"));
                u.setRoleName(rs.getString("nameRole"));
                u.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
                u.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
                data.add(u); // Không bao giờ gửi password_hash trong DTO quản trị
            }
        }
        return data;
    }

    public void updateUser(UserDTO v) throws SQLException {
        update("UPDATE users SET email=?,full_name=?,phone=?,roleID=?,is_active=?,updated_at=NOW() WHERE id=?",
                v.getEmail(), v.getFullName(), v.getPhone(), v.getRoleID(), v.getIsActive(), v.getId());
    }

    public void disableUser(long id) throws SQLException {
        update("UPDATE users SET is_active=FALSE WHERE id=?", id);
    }

    public void changePassword(long id, String encoded) throws SQLException {
        update("UPDATE users SET password_hash=?,updated_at=NOW() WHERE id=?", encoded, id);
    }

    public String getPasswordHash(long id) throws SQLException {
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM users WHERE id=?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString(1);
            }
        }
        throw new SQLException("Không tìm thấy người dùng");
    }

    public List<VoucherDTO> vouchers() throws SQLException {
        List<VoucherDTO> data = new ArrayList<>();
        try (Connection c = dbConnection.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM vouchers ORDER BY id DESC"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) data.add(mapVoucher(rs));
        }
        return data;
    }

    private VoucherDTO mapVoucher(ResultSet rs) throws SQLException {
        VoucherDTO v = new VoucherDTO();
        v.setId(rs.getLong("id"));
        v.setCode(rs.getString("code"));
        v.setName(rs.getString("name"));
        v.setDescription(rs.getString("description"));
        v.setDiscountType(DiscountType.valueOf(rs.getString("discount_type")));
        v.setDiscountValue(rs.getBigDecimal("discount_value"));
        v.setMinOrderAmount(rs.getBigDecimal("min_order_amount"));
        v.setMaxDiscountAmount(rs.getBigDecimal("max_discount_amount"));
        v.setStartAt(rs.getObject("start_at", LocalDateTime.class));
        v.setEndAt(rs.getObject("end_at", LocalDateTime.class));
        v.setUsageLimit((Integer) rs.getObject("usage_limit"));
        v.setUsedCount((Integer) rs.getObject("used_count"));
        v.setIsActive(rs.getBoolean("is_active"));
        return v;
    }

    public long addVoucher(VoucherDTO v) throws SQLException {
        return insert("INSERT INTO vouchers (code,name,description,discount_type,discount_value,min_order_amount,max_discount_amount,start_at,end_at,usage_limit,used_count,is_active,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,0,TRUE,NOW(),NOW())",
                v.getCode(), v.getName(), v.getDescription(), v.getDiscountType().name(), v.getDiscountValue(), v.getMinOrderAmount(), v.getMaxDiscountAmount(), v.getStartAt(), v.getEndAt(), v.getUsageLimit());
    }

    public void updateVoucher(VoucherDTO v) throws SQLException {
        update("UPDATE vouchers SET code=?,name=?,description=?,discount_type=?,discount_value=?,min_order_amount=?,max_discount_amount=?,start_at=?,end_at=?,usage_limit=?,is_active=?,updated_at=NOW() WHERE id=?",
                v.getCode(), v.getName(), v.getDescription(), v.getDiscountType().name(), v.getDiscountValue(), v.getMinOrderAmount(), v.getMaxDiscountAmount(), v.getStartAt(), v.getEndAt(), v.getUsageLimit(), v.getIsActive(), v.getId());
    }

    public void disableVoucher(long id) throws SQLException {
        update("UPDATE vouchers SET is_active=FALSE WHERE id=?", id);
    }
}
