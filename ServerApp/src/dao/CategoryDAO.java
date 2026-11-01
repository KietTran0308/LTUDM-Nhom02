package dao;

import dto.CategoryDTO;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CategoryDAO {

    private CategoryDTO mapCategory(ResultSet rs) throws SQLException {
        return new CategoryDTO(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("description"),
            rs.getObject("created_at", LocalDateTime.class)
        );
    }
    public List<CategoryDTO> getAllCategories() throws SQLException {
        List<CategoryDTO> list = new ArrayList<>();

        String sql = "SELECT id, name, slug, description, created_at FROM categories ORDER BY id ASC";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                list.add(mapCategory(rs));
            }
        }

        return list;
    }

    public CategoryDTO getCategoryById(long id) throws SQLException {
        String sql = "SELECT id, name, slug, description, created_at FROM categories WHERE id = ?";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapCategory(rs);
                }
            }
        }

        return null;
    }

    public CategoryDTO createCategory(CategoryDTO category) throws SQLException {
        String sql = "INSERT INTO categories (name, slug, description) VALUES (?, ?, ?)";
        String name = (category.getName() != null) ? category.getName().trim() : null;
        String slug = (category.getSlug() != null) ? category.getSlug().trim() : null;
        String description = (category.getDescription() != null) ? category.getDescription().trim() : null;
        try (
            Connection conn= dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS) 
        ){
            ps.setString(1, name);
            ps.setString(2, slug);
            ps.setString(3, description);

            int affectedRows = ps.executeUpdate();
            if (affectedRows == 0)  throw new SQLException("Thêm category thất bại, không có dòng nào được tạo.");
            try (ResultSet rs = ps.getGeneratedKeys()){
                if (rs.next()){
                    category.setId(rs.getLong(1));
                    category.setCreatedAt(LocalDateTime.now());

                    return category; 
                }
                else
                    throw new SQLException("Thêm category thất bại, không lấy được ID.");
            }
        }
    }


    public CategoryDTO updateCategory(CategoryDTO category) throws SQLException {
        String sql = "UPDATE categories SET "
                + "name = COALESCE(?, name), "
                + "slug = COALESCE(?, slug), "
                + "description = COALESCE(?, description) "
                + "WHERE id = ?";
        
        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ) {
            // Truyền thẳng vào, nếu bị null nó sẽ truyền chữ null xuống DB cho COALESCE tự xử lý
            ps.setString(1, category.getName() != null ? category.getName().trim() : null);
            ps.setString(2, category.getSlug() != null ? category.getSlug().trim() : null);
            ps.setString(3, category.getDescription() != null ? category.getDescription().trim() : null);
            
            // BẮT BUỘC phải có dòng này để truyền ID
            ps.setLong(4, category.getId());

            int affectedRows = ps.executeUpdate();
            if (affectedRows > 0) {
                return category;
            } else {
                throw new SQLException("Không tìm thấy category với ID: " + category.getId());
            }
        }
    }
    
    public boolean deleteCategory(long id) throws SQLException {
        String sql = "DELETE FROM categories WHERE id = ?";
        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ){
            ps.setLong(1, id);
            int affectedRows = ps.executeUpdate();

            return affectedRows > 0;
        }
    }
    public Boolean existsByName(String name) throws SQLException {
        String sql = "SELECT id FROM categories WHERE name = ? LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, name != null ? name.trim() : "");

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public Boolean existsBySlug(String slug) throws SQLException {
        String sql = "SELECT id FROM categories WHERE slug = ? LIMIT 1";
        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
        ){
            ps.setString(1, slug != null ? slug.trim() : "");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
        }
        }
    }

    public boolean existsByNameExceptId(String name, long id) throws SQLException {
        String sql = "SELECT id FROM categories WHERE name = ? AND id != ? LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, name.trim());
            ps.setLong(2, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsBySlugExceptId(String slug, long id) throws SQLException {
        String sql = "SELECT id FROM categories WHERE slug = ? AND id != ? LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, slug.trim());
            ps.setLong(2, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}