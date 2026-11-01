package dao;
import dto.UserDTO;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {

    /**
     * Chuyển dữ liệu từ ResultSet thành UserDTO.
     */
    private UserDTO mapUser(ResultSet rs) throws SQLException {
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

    /**
     * Đăng nhập bằng Email hoặc số điện thoại và mật khẩu.
     */
    public UserDTO findByEmailOrPhone(String emailOrPhone, String password) {
        String sql = "SELECT u.*, r.nameRole FROM users u " +
                "JOIN roles r ON u.roleID = r.roleID " +
                "WHERE (u.email = ? OR u.phone = ?) AND u.is_active = TRUE";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, emailOrPhone.trim());
            ps.setString(2, emailOrPhone.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() &&
                    bus.PasswordUtil.matches(
                        password, rs.getString("password_hash")
                    )) {
                    return mapUser(rs);
                }
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Kiểm tra email đã tồn tại hay chưa.
     */
    public boolean existsByEmail(String email) throws SQLException {
        String sql = "SELECT id FROM users WHERE email = ? LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, email.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Kiểm tra số điện thoại đã tồn tại hay chưa.
     */
    public boolean existsByPhone(String phone) throws SQLException {
        String sql = "SELECT id FROM users WHERE phone = ? LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, phone.trim());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Tìm roleID theo tên quyền.
     */
    public Long findRoleIdByName(String roleName) throws SQLException {
        String sql = "SELECT roleID FROM roles " +
                     "WHERE UPPER(nameRole) = UPPER(?) LIMIT 1";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, roleName.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("roleID");
                }
            }
        }

        return null;
    }

    /**
     * Đăng ký tài khoản mới.
     */
    public UserDTO registerUser(UserDTO newUser) throws SQLException {
        String sqlRole = "SELECT roleID, nameRole FROM roles " +
                "WHERE UPPER(nameRole) IN ('CUSTOMER', 'TABLE_KIOSK', 'USER') " +
                "LIMIT 1";

        String sqlInsert = "INSERT INTO users " +
                "(roleID, email, password_hash, full_name, phone, is_active) " +
                "VALUES (?, ?, ?, ?, ?, TRUE)";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement psRole = conn.prepareStatement(sqlRole)
        ) {
            long roleId;
            String roleName;

            try (ResultSet rsRole = psRole.executeQuery()) {
                if (!rsRole.next()) {
                    throw new SQLException("Không tìm thấy role CUSTOMER!");
                }

                roleId = rsRole.getLong("roleID");
                roleName = rsRole.getString("nameRole");
            }

            try (
                PreparedStatement psInsert = conn.prepareStatement(
                    sqlInsert, Statement.RETURN_GENERATED_KEYS
                )
            ) {
                psInsert.setLong(1, roleId);
                psInsert.setString(2, newUser.getEmail().trim());
                psInsert.setString(3, newUser.getPasswordHash().trim());
                psInsert.setString(4, newUser.getFullName().trim());
                psInsert.setString(5, newUser.getPhone().trim());

                psInsert.executeUpdate();

                try (ResultSet rsKey = psInsert.getGeneratedKeys()) {
                    if (rsKey.next()) {
                        newUser.setId(rsKey.getLong(1));
                        newUser.setRoleID(roleId);
                        newUser.setRoleName(roleName);
                        newUser.setIsActive(true);

                        return newUser;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Lấy danh sách tất cả người dùng.
     */
    public List<UserDTO> findAll() throws SQLException {
        List<UserDTO> userList = new ArrayList<>();

        String sql = "SELECT u.*, r.nameRole FROM users u " +
                     "JOIN roles r ON u.roleID = r.roleID";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                userList.add(mapUser(rs));
            }
        }

        return userList;
    }

    /**
     * Tìm người dùng theo ID.
     */
    public UserDTO findById(long userId) throws SQLException {
        String sql = "SELECT u.*, r.nameRole FROM users u " +
                     "JOIN roles r ON u.roleID = r.roleID " +
                     "WHERE u.id = ?";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, userId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }

        return null;
    }

    /**
     * Tìm người dùng theo số điện thoại.
     */
    public UserDTO findByPhone(String phone) throws SQLException {
        String sql = "SELECT u.*, r.nameRole FROM users u " +
                     "JOIN roles r ON u.roleID = r.roleID " +
                     "WHERE u.phone = ?";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, phone.trim());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }

        return null;
    }

    /**
     * Cập nhật thông tin người dùng.
     */
    public boolean update(UserDTO user) throws SQLException {
        String sql = "UPDATE users SET " +
                     "full_name = ?, phone = ?, email = ? " +
                     "WHERE id = ?";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, user.getFullName().trim());
            ps.setString(2, user.getPhone().trim());
            ps.setString(3, user.getEmail().trim());
            ps.setLong(4, user.getId());

            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Vô hiệu hóa tài khoản thay vì xóa hẳn khỏi database.
     */
    public boolean deactivate(long userId) throws SQLException {
        String sql = "UPDATE users SET is_active = FALSE WHERE id = ?";

        try (
            Connection conn = dbConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, userId);

            return ps.executeUpdate() > 0;
        }
    }
}
