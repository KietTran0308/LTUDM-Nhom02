package dao;

import dto.IngredientDTO;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class IngredientDAO {

    /**
     * Lấy toàn bộ nguyên liệu, ưu tiên đẩy các nguyên liệu sắp hết (stock <= min_stock) lên đầu bảng
     */
    public List<IngredientDTO> getAllIngredients() {
        List<IngredientDTO> list = new ArrayList<>();
        String sql = "SELECT * FROM ingredients ORDER BY (stock_quantity <= min_stock_level) DESC, id ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();

            while (rs.next()) {
                list.add(new IngredientDTO(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("unit"),
                        rs.getBigDecimal("stock_quantity"),
                        rs.getBigDecimal("min_stock_level"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class)
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
     * Nhập thêm hàng vào kho (Cộng dồn vào stock_quantity hiện tại)
     */
    public boolean restockIngredient(Long ingredientId, BigDecimal addedAmount) {
        String sql = "UPDATE ingredients SET stock_quantity = stock_quantity + ? WHERE id = ?";
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setBigDecimal(1, addedAmount);
            ps.setLong(2, ingredientId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }

    /**
     * Thêm nguyên liệu mới vào kho
     */
    public boolean insertIngredient(IngredientDTO ing) {
        String sql = "INSERT INTO ingredients (name, unit, stock_quantity, min_stock_level) VALUES (?, ?, ?, ?)";
        Connection conn = null;
        PreparedStatement ps = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, ing.getName());
            ps.setString(2, ing.getUnit());
            ps.setBigDecimal(3, ing.getStockQuantity());
            ps.setBigDecimal(4, ing.getMinStockLevel());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            dbConnection.closeConnection(conn, ps, null);
        }
    }
}