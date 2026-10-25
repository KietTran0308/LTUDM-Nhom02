package dao;

import dto.TableDTO;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TableDAO {

    public static List<TableDTO> getAllTables() {
        List<TableDTO> list = new ArrayList<>();
        String sql = "SELECT id, table_name, capacity, status FROM restaurant_tables ORDER BY id ASC";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new TableDTO(
                        rs.getLong("id"),
                        rs.getString("table_name"),
                        rs.getInt("capacity"),
                        rs.getBoolean("status")
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return list;
    }
}