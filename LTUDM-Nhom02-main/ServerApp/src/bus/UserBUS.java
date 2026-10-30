
package bus;

import dao.UserDAO;
import dto.UserDTO;

import java.sql.SQLException;
import java.util.List;

public class UserBUS {

    private final UserDAO userDAO;

    public UserBUS() {
        this.userDAO = new UserDAO();
    }

    // Lấy danh sách tất cả người dùng
    public List<UserDTO> getAllUsers() throws SQLException {
        return userDAO.findAll();
    }

    // Tìm người dùng theo ID
    public UserDTO getUserById(long userId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("ID người dùng không hợp lệ");
        }

        return userDAO.findById(userId);
    }

    // Tìm người dùng theo số điện thoại
    public UserDTO getUserByPhone(String phone) throws SQLException {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập số điện thoại");
        }

        return userDAO.findByPhone(phone.trim());
    }

    // Cập nhật thông tin người dùng
    public boolean updateUser(UserDTO user) throws SQLException {
        if (user == null || user.getId() <= 0) {
            throw new IllegalArgumentException("Thông tin người dùng không hợp lệ");
        }

        if (user.getFullName() == null || user.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Họ tên không được để trống");
        }

        if (user.getEmail() == null || !user.getEmail().contains("@")) {
            throw new IllegalArgumentException("Email không hợp lệ");
        }

        if (user.getPhone() == null || !user.getPhone().matches("^[0-9]{10,11}$")) {
            throw new IllegalArgumentException("Số điện thoại không hợp lệ");
        }

        return userDAO.update(user);
    }

    // Khóa tài khoản
    public boolean deactivateUser(long userId) throws SQLException {
        if (userId <= 0) {
            throw new IllegalArgumentException("ID người dùng không hợp lệ");
        }

        return userDAO.deactivate(userId);
    }

    // Kiểm tra email tồn tại
    public boolean isEmailExists(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }

        return userDAO.existsByEmail(email.trim());
    }

    // Kiểm tra số điện thoại tồn tại
    public boolean isPhoneExists(String phone) throws SQLException {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }

        return userDAO.existsByPhone(phone.trim());
    }
}
