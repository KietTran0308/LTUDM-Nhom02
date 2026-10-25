package dao;

import dto.UserDTO;
import java.sql.*;
import java.time.LocalDateTime;

public class UserDAO {

    /**
     * Kiểm tra đăng nhập bằng Email HOẶC Số điện thoại kèm mật khẩu
     */
    public UserDTO authenticate(String emailOrPhone, String passwordHash) {
        String sql = "SELECT u.*, r.nameRole FROM users u " +
                "JOIN roles r ON u.roleID = r.roleID " +
                "WHERE (u.email = ? OR u.phone = ?) AND u.is_active = TRUE";

        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, emailOrPhone.trim());
            ps.setString(2, emailOrPhone.trim());
            rs = ps.executeQuery();

            if (rs.next() && bus.PasswordUtil.matches(passwordHash, rs.getString("password_hash"))) {
                UserDTO user = new UserDTO(
                        rs.getLong("id"),
                        rs.getLong("roleID"),
                        rs.getString("email"),
                        rs.getString("password_hash"),
                        rs.getString("full_name"),
                        rs.getString("phone"),
                        rs.getBoolean("is_active"),
                        rs.getObject("created_at", LocalDateTime.class),
                        rs.getObject("updated_at", LocalDateTime.class)
                );
                user.setRoleName(rs.getString("nameRole"));
                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
        return null;
    }

    /**
     * Kiểm tra trùng Email khi đăng ký
     */
    public boolean existsByEmail(String email) {
        String sql = "SELECT id FROM users WHERE email = ? LIMIT 1";
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            conn = dbConnection.getConnection();
            ps = conn.prepareStatement(sql);
            ps.setString(1, email.trim());
            rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            dbConnection.closeConnection(conn, ps, rs);
        }
    }

    /**
     * Đăng ký tài khoản mới (Mặc định gán roleID của khách hàng/bàn hoặc tìm role thấp nhất)
     */
    public UserDTO registerUser(UserDTO newUser) throws SQLException {
        // Tìm roleID dành cho khách hàng (hoặc lấy roleID mặc định = 3 nếu có)
        String sqlRole = "SELECT roleID, nameRole FROM roles WHERE UPPER(nameRole) IN ('CUSTOMER', 'TABLE_KIOSK', 'USER') LIMIT 1";
        String sqlInsert = "INSERT INTO users (roleID, email, password_hash, full_name, phone, is_active) VALUES (?, ?, ?, ?, ?, TRUE)";

        Connection conn = null;
        PreparedStatement psRole = null;
        PreparedStatement psInsert = null;
        ResultSet rsRole = null;
        ResultSet rsKey = null;

        try {
            conn = dbConnection.getConnection();
            long defaultRoleId = 3L;
            String defaultRoleName = "CUSTOMER";

            psRole = conn.prepareStatement(sqlRole);
            rsRole = psRole.executeQuery();
            if (rsRole.next()) {
                defaultRoleId = rsRole.getLong("roleID");
                defaultRoleName = rsRole.getString("nameRole");
            }

            psInsert = conn.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);
            psInsert.setLong(1, defaultRoleId);
            psInsert.setString(2, newUser.getEmail().trim());
            psInsert.setString(3, newUser.getPasswordHash().trim());
            psInsert.setString(4, newUser.getFullName().trim());
            psInsert.setString(5, newUser.getPhone().trim());
            psInsert.executeUpdate();

            rsKey = psInsert.getGeneratedKeys();
            if (rsKey.next()) {
                newUser.setId(rsKey.getLong(1));
                newUser.setRoleID(defaultRoleId);
                newUser.setRoleName(defaultRoleName);
                newUser.setIsActive(true);
                return newUser;
            }
            return null;
        } finally {
            if (rsRole != null) rsRole.close();
            if (psRole != null) psRole.close();
            dbConnection.closeConnection(conn, psInsert, rsKey);
        }
    }
}