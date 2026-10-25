package dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class dbConnection {
    private static final String URL = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/annhien_datmon?useSSL=false&serverTimezone=UTC&characterEncoding=UTF-8");
    private static final String USER = System.getenv().getOrDefault("DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("DB_PASSWORD", "");

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("[MySQL JDBC] Không tìm thấy Driver MySQL: " + e.getMessage());
        }
    }


    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
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