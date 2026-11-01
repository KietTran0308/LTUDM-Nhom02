
package dao;

import dto.FoodDTO;
import dto.FoodImageDTO;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FoodDAO {

    // ================= MAP DATA =================

    private FoodDTO mapFood(ResultSet rs) throws SQLException {
        FoodDTO food = new FoodDTO(
            rs.getLong("id"),
            rs.getString("sku"),
            rs.getObject("category_id", Long.class),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("description"),
            rs.getBigDecimal("price"),
            rs.getBoolean("is_published"),
            rs.getBoolean("is_deleted"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
        );

        food.setCategoryName(rs.getString("category_name"));
        food.setCoverImageUrl(rs.getString("cover_url"));

        return food;
    }

    private static final String SELECT_FOOD =
        "SELECT f.id, f.sku, f.category_id, f.name, f.slug, " +
        "f.description, f.price, f.is_published, f.is_deleted, " +
        "f.created_at, f.updated_at, " +
        "c.name AS category_name, fi.url AS cover_url " +
        "FROM foods f " +
        "LEFT JOIN categories c ON f.category_id = c.id " +
        "LEFT JOIN food_images fi " +
        "ON f.id = fi.foods_id AND fi.is_cover = TRUE ";

    // ================= GET DATA =================

    // Lấy món đang mở bán cho Tablet
    public List<FoodDTO> getPublishedFoodsForTablet()
            throws SQLException {

        List<FoodDTO> list = new ArrayList<>();

        String sql = SELECT_FOOD +
            "WHERE f.is_published = TRUE " +
            "AND f.is_deleted = FALSE " +
            "ORDER BY f.category_id ASC, f.id ASC";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                list.add(mapFood(rs));
            }
        }

        return list;
    }

    // Lấy tất cả món chưa bị xóa cho Admin
    // Bao gồm cả món đang ẩn
    public List<FoodDTO> getAllFoodsForAdmin()
            throws SQLException {

        List<FoodDTO> list = new ArrayList<>();

        String sql = SELECT_FOOD +
            "WHERE f.is_deleted = FALSE " +
            "ORDER BY f.id DESC";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                list.add(mapFood(rs));
            }
        }

        return list;
    }

    // Lấy món theo ID
    public FoodDTO getFoodById(Long foodId) throws SQLException {
        String sql = SELECT_FOOD +
            "WHERE f.id = ? AND f.is_deleted = FALSE";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, foodId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    FoodDTO food = mapFood(rs);
                    food.setImages(getImagesByFoodId(foodId));
                    return food;
                }
            }
        }

        return null;
    }

    // Lấy món theo danh mục
    // Dành cho màn hình Tablet: chỉ lấy món đang mở bán
    public List<FoodDTO> getPublishedFoodsByCategoryId(Long categoryId)
            throws SQLException {

        List<FoodDTO> list = new ArrayList<>();

        String sql = SELECT_FOOD +
            "WHERE f.category_id = ? " +
            "AND f.is_published = TRUE " +
            "AND f.is_deleted = FALSE " +
            "ORDER BY f.id ASC";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, categoryId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFood(rs));
                }
            }
        }

        return list;
    }

    // Lấy tất cả món theo danh mục cho Admin
    public List<FoodDTO> getFoodsByCategoryId(Long categoryId)
            throws SQLException {

        List<FoodDTO> list = new ArrayList<>();

        String sql = SELECT_FOOD +
            "WHERE f.category_id = ? " +
            "AND f.is_deleted = FALSE " +
            "ORDER BY f.id ASC";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, categoryId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapFood(rs));
                }
            }
        }

        return list;
    }

    // Lấy danh sách ảnh của một món
    public List<FoodImageDTO> getImagesByFoodId(Long foodId)
            throws SQLException {

        List<FoodImageDTO> images = new ArrayList<>();

        String sql =
            "SELECT id, foods_id, url, is_cover, sort_order, created_at " +
            "FROM food_images WHERE foods_id = ? " +
            "ORDER BY sort_order ASC, id ASC";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, foodId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    images.add(new FoodImageDTO(
                        rs.getLong("id"),
                        rs.getLong("foods_id"),
                        rs.getString("url"),
                        rs.getBoolean("is_cover"),
                        rs.getInt("sort_order"),
                        rs.getObject("created_at", LocalDateTime.class)
                    ));
                }
            }
        }

        return images;
    }

    // ================= CREATE =================

    // Thêm món và ảnh đại diện trong cùng transaction
    public FoodDTO insertFoodWithCover(FoodDTO food)
            throws SQLException {

        String sqlFood =
            "INSERT INTO foods " +
            "(sku, category_id, name, slug, description, price, " +
            "is_published, is_deleted) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)";

        String sqlImage =
            "INSERT INTO food_images " +
            "(foods_id, url, is_cover, sort_order) " +
            "VALUES (?, ?, TRUE, 1)";

        Connection conn = null;
        boolean oldAutoCommit = true;
        Long newFoodId = null;

        try {
            conn = dbConnection.getConnection();
            oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);

            try (
                PreparedStatement psFood = conn.prepareStatement(
                    sqlFood, Statement.RETURN_GENERATED_KEYS
                )
            ) {
                psFood.setString(1, food.getSku());
                psFood.setObject(
                    2, food.getCategoryId(), Types.BIGINT
                );
                psFood.setString(3, food.getName());
                psFood.setString(4, food.getSlug());
                psFood.setString(5, food.getDescription());
                psFood.setBigDecimal(6, food.getPrice());
                psFood.setBoolean(
                    7,
                    food.getIsPublished() != null
                        ? food.getIsPublished()
                        : true
                );

                psFood.executeUpdate();

                try (ResultSet rs = psFood.getGeneratedKeys()) {
                    if (rs.next()) {
                        newFoodId = rs.getLong(1);
                    } else {
                        throw new SQLException(
                            "Không lấy được ID món ăn vừa tạo."
                        );
                    }
                }
            }

            // Chỉ thêm ảnh nếu có URL ảnh đại diện
            String coverUrl = food.getCoverImageUrl();

            if (coverUrl != null && !coverUrl.trim().isEmpty()) {
                try (PreparedStatement psImage =
                        conn.prepareStatement(sqlImage)) {

                    psImage.setLong(1, newFoodId);
                    psImage.setString(2, coverUrl.trim());
                    psImage.executeUpdate();
                }
            }

            conn.commit();

            food.setId(newFoodId);

        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw e;

        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(oldAutoCommit);
                } finally {
                    conn.close();
                }
            }
        }

        return getFoodById(newFoodId);
    }

    // ================= UPDATE =================

    // Cập nhật thông tin cơ bản của món
    public FoodDTO updateFoodBasicInfo(FoodDTO food)
            throws SQLException {

        String sql =
            "UPDATE foods SET " +
            "sku = ?, category_id = ?, name = ?, slug = ?, " +
            "description = ?, price = ?, is_published = ? " +
            "WHERE id = ? AND is_deleted = FALSE";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, food.getSku());
            ps.setObject(
                2, food.getCategoryId(), Types.BIGINT
            );
            ps.setString(3, food.getName());
            ps.setString(4, food.getSlug());
            ps.setString(5, food.getDescription());
            ps.setBigDecimal(6, food.getPrice());
            ps.setBoolean(7, food.getIsPublished());
            ps.setLong(8, food.getId());

            int affectedRows = ps.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException(
                    "Không tìm thấy món ăn với ID: " + food.getId()
                );
            }
        }

        return getFoodById(food.getId());
    }

    // Ẩn hoặc hiện món
    public boolean updatePublishedStatus(
            Long foodId, boolean isPublished) throws SQLException {

        String sql =
            "UPDATE foods SET is_published = ? " +
            "WHERE id = ? AND is_deleted = FALSE";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setBoolean(1, isPublished);
            ps.setLong(2, foodId);

            return ps.executeUpdate() > 0;
        }
    }

    // Xóa mềm món ăn
    public boolean softDeleteFood(Long foodId)
            throws SQLException {

        String sql =
            "UPDATE foods SET " +
            "is_deleted = TRUE, is_published = FALSE " +
            "WHERE id = ? AND is_deleted = FALSE";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, foodId);
            return ps.executeUpdate() > 0;
        }
    }

    // ================= CHECK DUPLICATE =================

    public boolean existsBySku(String sku) throws SQLException {
        String sql =
            "SELECT id FROM foods " +
            "WHERE sku = ? AND is_deleted = FALSE LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, sku);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsByName(String name) throws SQLException {
        String sql =
            "SELECT id FROM foods " +
            "WHERE name = ? AND is_deleted = FALSE LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsBySlug(String slug) throws SQLException {
        String sql =
            "SELECT id FROM foods " +
            "WHERE slug = ? AND is_deleted = FALSE LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, slug);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsBySkuExceptId(String sku, Long id)
            throws SQLException {

        String sql =
            "SELECT id FROM foods WHERE sku = ? " +
            "AND id != ? AND is_deleted = FALSE LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, sku);
            ps.setLong(2, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsByNameExceptId(String name, Long id)
            throws SQLException {

        String sql =
            "SELECT id FROM foods WHERE name = ? " +
            "AND id != ? AND is_deleted = FALSE LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, name);
            ps.setLong(2, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean existsBySlugExceptId(String slug, Long id)
            throws SQLException {

        String sql =
            "SELECT id FROM foods WHERE slug = ? " +
            "AND id != ? AND is_deleted = FALSE LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, slug);
            ps.setLong(2, id);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
