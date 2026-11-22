package dao;

import api.ServerConfig;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class dbConnection {

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[MySQL JDBC] Không tìm thấy Driver MySQL: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(ServerConfig.DB_URL, ServerConfig.DB_USER, ServerConfig.DB_PASSWORD);
    }

    public static void closeConnection(Connection conn, PreparedStatement ps, ResultSet rs) {
        try {
            if (rs != null && !rs.isClosed()) rs.close();
            if (ps != null && !ps.isClosed()) ps.close();
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (SQLException e) {
            System.err.println("[MySQL JDBC] Lỗi khi đóng kết nối: " + e.getMessage());
        }
    }
}