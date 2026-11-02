package dao;

import dto.CategoryDTO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {
    public List<CategoryDTO> getAllCategories() {
        List<CategoryDTO> list = new ArrayList<>();
        String sql = "SELECT id, name, slug, description, created_at FROM categories ORDER BY id ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
                list.add(mapCategory(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tải danh mục từ database", e);
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
    }


    // Read one category (DB exceptions propagate to BUS).
    public CategoryDTO getCategoryById(long id) throws SQLException {
        String sql = "SELECT id, name, slug, description, created_at FROM categories WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapCategory(rs);
            }
        }
    }

    private CategoryDTO mapCategory(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        return new CategoryDTO(rs.getLong("id"), rs.getString("name"),
                rs.getString("slug"), rs.getString("description"),
                created == null ? null : created.toLocalDateTime());
    }

    private boolean exists(String sql, Object... params) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean existsByName(String name) throws SQLException {
        return exists("SELECT 1 FROM categories WHERE name = ? LIMIT 1", name);
    }

    public boolean existsBySlug(String slug) throws SQLException {
        return exists("SELECT 1 FROM categories WHERE slug = ? LIMIT 1", slug);
    }

    public boolean existsByNameExceptId(String name, Long id) throws SQLException {
        return exists("SELECT 1 FROM categories WHERE name = ? AND id <> ? LIMIT 1", name, id);
    }

    public boolean existsBySlugExceptId(String slug, Long id) throws SQLException {
        return exists("SELECT 1 FROM categories WHERE slug = ? AND id <> ? LIMIT 1", slug, id);
    }

    public CategoryDTO createCategory(CategoryDTO category) throws SQLException {
        String sql = "INSERT INTO categories (name, slug, description) VALUES (?, ?, ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getSlug());
            ps.setString(3, category.getDescription());
            if (ps.executeUpdate() != 1) throw new SQLException("Không tạo được danh mục");
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("Không lấy được ID danh mục mới");
                category.setId(keys.getLong(1));
            }
        }
        return getCategoryById(category.getId());
    }

    public CategoryDTO updateCategory(CategoryDTO category) throws SQLException {
        String sql = "UPDATE categories SET name = ?, slug = ?, description = ? WHERE id = ?";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, category.getName());
            ps.setString(2, category.getSlug());
            ps.setString(3, category.getDescription());
            ps.setLong(4, category.getId());
            // Some JDBC drivers return zero when values were unchanged.
            ps.executeUpdate();
        }
        return getCategoryById(category.getId());
    }

    public boolean deleteCategory(long id) throws SQLException {
        // Never silently delete or detach categories used by foods.
        String sql = "DELETE FROM categories WHERE id = ? " +
                "AND NOT EXISTS (SELECT 1 FROM foods WHERE category_id = ?)";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        }
    }
}
