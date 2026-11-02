
package dao;

import dto.CartItemDTO;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class CartDAO {
    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    /**
     * Lấy danh sách món ăn trong giỏ hàng của người dùng
     */
    public List<CartItemDTO> getCartByUserId(Long userId) {

        List<CartItemDTO> list = new ArrayList<>();

        String sql = "SELECT ci.*, f.name AS food_name, f.price AS unit_price " +
                "FROM cart_items ci " +
                "JOIN foods f ON ci.foods_id = f.id " +
                "WHERE ci.user_id = ? " +
                "ORDER BY ci.created_at DESC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setLong(1, userId);

            rs = ps.executeQuery();

            while (rs.next()) {

                CartItemDTO item = new CartItemDTO(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getLong("foods_id"),
                        rs.getInt("quantity"),
                        toLocalDateTime(rs.getTimestamp("created_at")),
                        toLocalDateTime(rs.getTimestamp("updated_at"))
                );

                item.setFoodName(rs.getString("food_name"));
                item.setUnitPrice(rs.getBigDecimal("unit_price"));

                list.add(item);
            }

        } catch (SQLException e) {
            throw new IllegalStateException("Không thể tải giỏ hàng", e);
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }

        return list;
    }

    /**
     * Thêm món vào giỏ hàng.
     * Nếu món đã tồn tại thì tăng số lượng.
     * Chỉ thêm món đang được công khai và chưa bị xóa.
     */
    public boolean addItem(Long userId, Long foodId, int quantity) {

        String sql = "INSERT INTO cart_items (user_id, foods_id, quantity) " +
                "SELECT ?, f.id, ? FROM foods f " +
                "WHERE f.id = ? " +
                "AND f.is_published = TRUE " +
                "AND f.is_deleted = FALSE " +
                "ON DUPLICATE KEY UPDATE " +
                "quantity = quantity + VALUES(quantity), " +
                "updated_at = CURRENT_TIMESTAMP(3)";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);

            ps.setLong(1, userId);
            ps.setInt(2, quantity);
            ps.setLong(3, foodId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi SQL trong CartDAO", e);
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }

    /**
     * Cập nhật số lượng món trong giỏ hàng.
     * Không cập nhật nếu món đã bị ẩn hoặc xóa.
     */
    public boolean updateQuantity(Long userId, Long foodId, int quantity) {

        String sql = "UPDATE cart_items ci " +
                "JOIN foods f ON ci.foods_id = f.id " +
                "SET ci.quantity = ?, " +
                "ci.updated_at = CURRENT_TIMESTAMP(3) " +
                "WHERE ci.user_id = ? " +
                "AND ci.foods_id = ? " +
                "AND f.is_published = TRUE " +
                "AND f.is_deleted = FALSE";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);

            ps.setInt(1, quantity);
            ps.setLong(2, userId);
            ps.setLong(3, foodId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi SQL trong CartDAO", e);
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }

    /**
     * Xóa một món khỏi giỏ hàng
     */
    public boolean removeItem(Long userId, Long foodId) {

        String sql = "DELETE FROM cart_items " +
                "WHERE user_id = ? AND foods_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);

            ps.setLong(1, userId);
            ps.setLong(2, foodId);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi SQL trong CartDAO", e);
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }

    /**
     * Xóa toàn bộ giỏ hàng của người dùng
     */
    public boolean clearCart(Long userId) {

        String sql = "DELETE FROM cart_items WHERE user_id = ?";

        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);

            ps.setLong(1, userId);
            ps.executeUpdate();

            return true;

        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi SQL trong CartDAO", e);
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }
}
