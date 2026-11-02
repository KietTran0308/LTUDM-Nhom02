package dao;

import dto.FoodDTO;
import dto.FoodImageDTO;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FoodDAO {

    /**
     * Lấy danh sách món ăn đang mở bán (is_published = 1, is_deleted = 0)
     * Kết hợp LEFT JOIN để lấy luôn tên danh mục và ảnh đại diện (is_cover = 1) hiển thị lên Tablet
     */
    public List<FoodDTO> getPublishedFoodsForTablet() {
        return loadFoods(FOOD_SELECT +
                "WHERE f.is_published = TRUE AND f.is_deleted = FALSE " +
                "ORDER BY f.category_id ASC, f.id ASC");
    }

    /**
     * Lấy toàn bộ danh sách ảnh phụ của 1 món ăn
     */
    public List<FoodImageDTO> getImagesByFoodId(Long foodId) {
        List<FoodImageDTO> images = new ArrayList<>();
        String sql = "SELECT * FROM food_images WHERE foods_id = ? ORDER BY sort_order ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, foodId);
            rs = ps.executeQuery();

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
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi khi đọc danh sách ảnh món", e);
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return images;
    }

    public List<FoodDTO> getAllFoodsForAdmin() {
        return loadFoods(FOOD_SELECT + "WHERE f.is_deleted = FALSE ORDER BY f.id DESC");
    }

    private List<FoodDTO> loadFoods(String sql) {
        List<FoodDTO> foods = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) foods.add(mapFood(rs));
            return foods;
        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tải danh sách món ăn", e);
        }
    }

    public boolean insertFoodWithCover(FoodDTO food) {
        String sqlFood = "INSERT INTO foods (sku, category_id, name, slug, description, price, is_published, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)";
        String sqlImg = "INSERT INTO food_images (foods_id, url, is_cover, sort_order) VALUES (?, ?, TRUE, 1)";

        Connection conn = null;
        PreparedStatement psFood = null;
        PreparedStatement psImg = null;
        ResultSet rsKey = null;

        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);

            psFood = conn.prepareStatement(sqlFood, Statement.RETURN_GENERATED_KEYS);
            psFood.setString(1, food.getSku());
            psFood.setObject(2, food.getCategoryId(), Types.BIGINT);
            psFood.setString(3, food.getName());
            psFood.setString(4, food.getSlug());
            psFood.setString(5, food.getDescription());
            psFood.setBigDecimal(6, food.getPrice());
            psFood.setBoolean(7, food.getIsPublished());
            psFood.executeUpdate();

            rsKey = psFood.getGeneratedKeys();
            if (!rsKey.next()) throw new SQLException("Không lấy được ID món ăn vừa thêm");
            long newFoodId = rsKey.getLong(1);
            if (food.getCoverImageUrl() != null && !food.getCoverImageUrl().trim().isEmpty()) {
                psImg = conn.prepareStatement(sqlImg);
                psImg.setLong(1, newFoodId);
                psImg.setString(2, food.getCoverImageUrl().trim());
                psImg.executeUpdate();
            }
            conn.commit();
            food.setId(newFoodId);
            return true;
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
            throw new IllegalStateException("Lỗi SQL trong FoodDAO", e);
        } finally {
            try { if (psImg != null) psImg.close(); } catch (SQLException ignored) {}
            dbConnection.closeConnection(conn, psFood, rsKey);
        }
    }

    public boolean updateFoodBasicInfo(FoodDTO food) {
        String sql = "UPDATE foods SET sku = ?, category_id = ?, name = ?, slug = ?, " +
                "description = ?, price = ?, is_published = ? WHERE id = ? AND is_deleted = FALSE";
        String updateCover = "UPDATE food_images SET url = ? WHERE foods_id = ? AND is_cover = TRUE";
        String insertCover = "INSERT INTO food_images (foods_id, url, is_cover, sort_order) " +
                "VALUES (?, ?, TRUE, 1)";
        Connection conn = null;
        try {
            conn = dbConnection.getConnection();
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, food.getSku());
                ps.setObject(2, food.getCategoryId(), Types.BIGINT);
                ps.setString(3, food.getName());
                ps.setString(4, food.getSlug());
                ps.setString(5, food.getDescription());
                ps.setBigDecimal(6, food.getPrice());
                ps.setBoolean(7, Boolean.TRUE.equals(food.getIsPublished()));
                ps.setLong(8, food.getId());
                ps.executeUpdate();
            }
            // A no-op UPDATE may return 0; the BUS has already checked that the food exists.
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM foods WHERE id = ? AND is_deleted = FALSE")) {
                check.setLong(1, food.getId());
                try (ResultSet rs = check.executeQuery()) {
                    if (!rs.next()) { conn.rollback(); return false; }
                }
            }
            // null means 'do not change the existing cover'; blank means remove cover flag.
            if (food.getCoverImageUrl() != null) {
                String url = food.getCoverImageUrl().trim();
                if (url.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "UPDATE food_images SET is_cover = FALSE WHERE foods_id = ? AND is_cover = TRUE")) {
                        ps.setLong(1, food.getId());
                        ps.executeUpdate();
                    }
                } else {
                    int updated;
                    try (PreparedStatement ps = conn.prepareStatement(updateCover)) {
                        ps.setString(1, url);
                        ps.setLong(2, food.getId());
                        updated = ps.executeUpdate();
                    }
                    if (updated == 0) {
                        // Avoid adding a duplicate cover when UPDATE reports 0 for unchanged data.
                        boolean hasCover;
                        try (PreparedStatement ps = conn.prepareStatement(
                                "SELECT 1 FROM food_images WHERE foods_id = ? AND is_cover = TRUE LIMIT 1")) {
                            ps.setLong(1, food.getId());
                            try (ResultSet rs = ps.executeQuery()) { hasCover = rs.next(); }
                        }
                        if (!hasCover) {
                            try (PreparedStatement ps = conn.prepareStatement(insertCover)) {
                                ps.setLong(1, food.getId());
                                ps.setString(2, url);
                                ps.executeUpdate();
                            }
                        }
                    }
                }
            }
            conn.commit();
            return true;
        } catch (SQLException e) {
            if (conn != null) try { conn.rollback(); } catch (SQLException ignored) {}
            throw new IllegalStateException("Không thể cập nhật món ăn", e);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
        }
    }

    public boolean softDeleteFood(Long foodId) {
        String sql = "UPDATE foods SET is_deleted = TRUE, is_published = FALSE WHERE id = ?";
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, foodId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi SQL trong FoodDAO", e);
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }


    // Mapping shared by single-food and category lookups.
    private FoodDTO mapFood(ResultSet rs) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");
        Timestamp updated = rs.getTimestamp("updated_at");
        FoodDTO food = new FoodDTO(
                rs.getLong("id"), rs.getString("sku"), rs.getObject("category_id", Long.class),
                rs.getString("name"), rs.getString("slug"), rs.getString("description"),
                rs.getBigDecimal("price"), rs.getBoolean("is_published"), rs.getBoolean("is_deleted"),
                created == null ? null : created.toLocalDateTime(),
                updated == null ? null : updated.toLocalDateTime());
        food.setCategoryName(rs.getString("category_name"));
        food.setCoverImageUrl(rs.getString("cover_url"));
        return food;
    }

    // Correlated subquery avoids duplicate foods if food_images has multiple cover flags.
    private static final String FOOD_SELECT =
            "SELECT f.*, c.name AS category_name, " +
                    "(SELECT fi.url FROM food_images fi WHERE fi.foods_id = f.id " +
                    "AND fi.is_cover = TRUE ORDER BY fi.id LIMIT 1) AS cover_url " +
                    "FROM foods f LEFT JOIN categories c ON c.id = f.category_id ";

    public FoodDTO getFoodById(Long foodId) throws SQLException {
        String sql = FOOD_SELECT + "WHERE f.id = ? AND f.is_deleted = FALSE";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, foodId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapFood(rs) : null;
            }
        }
    }

    private List<FoodDTO> foodsByCategory(Long categoryId, boolean publishedOnly) throws SQLException {
        String sql = FOOD_SELECT + "WHERE f.category_id = ? AND f.is_deleted = FALSE " +
                (publishedOnly ? "AND f.is_published = TRUE " : "") + "ORDER BY f.id ASC";
        List<FoodDTO> result = new ArrayList<>();
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, categoryId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapFood(rs));
            }
        }
        return result;
    }

    public List<FoodDTO> getPublishedFoodsByCategoryId(Long categoryId) throws SQLException {
        return foodsByCategory(categoryId, true);
    }

    public List<FoodDTO> getFoodsByCategoryId(Long categoryId) throws SQLException {
        return foodsByCategory(categoryId, false);
    }

    private boolean exists(String sql, Object... params) throws SQLException {
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    public boolean existsBySku(String sku) throws SQLException {
        return exists("SELECT 1 FROM foods WHERE sku = ? LIMIT 1", sku);
    }

    public boolean existsByName(String name) throws SQLException {
        return exists("SELECT 1 FROM foods WHERE name = ? LIMIT 1", name);
    }

    public boolean existsBySlug(String slug) throws SQLException {
        return exists("SELECT 1 FROM foods WHERE slug = ? LIMIT 1", slug);
    }

    public boolean existsBySkuExceptId(String sku, Long foodId) throws SQLException {
        return exists("SELECT 1 FROM foods WHERE sku = ? AND id <> ? LIMIT 1", sku, foodId);
    }

    public boolean existsByNameExceptId(String name, Long foodId) throws SQLException {
        return exists("SELECT 1 FROM foods WHERE name = ? AND id <> ? LIMIT 1", name, foodId);
    }

    public boolean existsBySlugExceptId(String slug, Long foodId) throws SQLException {
        return exists("SELECT 1 FROM foods WHERE slug = ? AND id <> ? LIMIT 1", slug, foodId);
    }

    public boolean updatePublishedStatus(Long foodId, boolean isPublished) throws SQLException {
        String sql = "UPDATE foods SET is_published = ? WHERE id = ? AND is_deleted = FALSE";
        try (Connection conn = dbConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, isPublished);
            ps.setLong(2, foodId);
            int count = ps.executeUpdate();
            // Success means food exists even when the requested value was unchanged.
            if (count > 0) return true;
            try (PreparedStatement check = conn.prepareStatement(
                    "SELECT 1 FROM foods WHERE id = ? AND is_deleted = FALSE")) {
                check.setLong(1, foodId);
                try (ResultSet rs = check.executeQuery()) { return rs.next(); }
            }
        }
    }
}
