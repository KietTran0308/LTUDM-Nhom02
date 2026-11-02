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
        List<FoodDTO> list = new ArrayList<>();
        String sql = "SELECT f.*, c.name AS category_name, fi.url AS cover_url " +
                "FROM foods f " +
                "LEFT JOIN categories c ON f.category_id = c.id " +
                "LEFT JOIN food_images fi ON f.id = fi.foods_id AND fi.is_cover = TRUE " +
                "WHERE f.is_published = TRUE AND f.is_deleted = FALSE " +
                "ORDER BY f.category_id ASC, f.id ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
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
                list.add(food);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
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
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return images;
    }

    public List<FoodDTO> getAllFoodsForAdmin() {
        List<FoodDTO> list = new ArrayList<>();
        String sql = "SELECT f.*, c.name AS category_name, fi.url AS cover_url " +
                "FROM foods f " +
                "LEFT JOIN categories c ON f.category_id = c.id " +
                "LEFT JOIN food_images fi ON f.id = fi.foods_id AND fi.is_cover = TRUE " +
                "WHERE f.is_deleted = FALSE " +
                "ORDER BY f.id DESC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
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
                list.add(food);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
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
            if (rsKey.next() && food.getCoverImageUrl() != null && !food.getCoverImageUrl().trim().isEmpty()) {
                long newFoodId = rsKey.getLong(1);
                psImg = conn.prepareStatement(sqlImg);
                psImg.setLong(1, newFoodId);
                psImg.setString(2, food.getCoverImageUrl().trim());
                psImg.executeUpdate();
            }

            conn.commit();
            return true;
        } catch (SQLException e) {
            try { if (conn != null) conn.rollback(); } catch (SQLException ignored) {}
            e.printStackTrace();
            return false;
        } finally {
            try { if (psImg != null) psImg.close(); } catch (SQLException ignored) {}
            dbConnection.closeConnection(conn, psFood, rsKey);
        }
    }

    public boolean updateFoodBasicInfo(FoodDTO food) {
        String sql = "UPDATE foods SET name = ?, price = ?, category_id = ?, is_published = ? WHERE id = ?";
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, food.getName());
            ps.setBigDecimal(2, food.getPrice());
            ps.setObject(3, food.getCategoryId(), Types.BIGINT);
            ps.setBoolean(4, food.getIsPublished());
            ps.setLong(5, food.getId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            dbConnection.closeConnection(conn, ps, null);
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
            e.printStackTrace();
            return false;
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }
}