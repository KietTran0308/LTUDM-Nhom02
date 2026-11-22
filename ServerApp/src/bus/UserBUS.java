package bus;

import dao.UserDAO;
import dto.UserDTO;

import java.sql.SQLException;
import java.util.List;
import java.util.regex.Pattern;

public class UserBUS {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("0\\d{9}");

    private final UserDAO userDAO;

    public UserBUS() {
        this.userDAO = new UserDAO();
    }

    public List<UserDTO> getAllUsers() throws SQLException {
        return userDAO.findAll();
    }

    public UserDTO getUserById(long userId) throws SQLException {
        requireValidUserId(userId, "ID người dùng không hợp lệ");
        return userDAO.findById(userId);
    }

    public UserDTO getUserByPhone(String phone) throws SQLException {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập số điện thoại");
        }
        return userDAO.findByPhone(phone.trim());
    }

    public boolean updateUser(UserDTO user) throws SQLException {
        if (user == null || user.getId() <= 0) {
            throw new IllegalArgumentException("Thông tin người dùng không hợp lệ");
        }
        if (user.getFullName() == null || user.getFullName().trim().isEmpty()) {
            throw new IllegalArgumentException("Họ tên không được để trống");
        }
        if (user.getEmail() == null || !EMAIL_PATTERN.matcher(user.getEmail().trim()).matches()) {
            throw new IllegalArgumentException("Email bắt buộc nhập và phải hợp lệ");
        }
        if (user.getPhone() == null || !PHONE_PATTERN.matcher(user.getPhone().trim()).matches()) {
            throw new IllegalArgumentException("Số điện thoại bắt buộc nhập (10 chữ số, bắt đầu bằng 0)");
        }

        user.setFullName(user.getFullName().trim());
        user.setEmail(user.getEmail().trim());
        user.setPhone(user.getPhone().trim());
        return userDAO.update(user);
    }

    public boolean deactivateUser(long userId) throws SQLException {
        requireValidUserId(userId, "ID người dùng không hợp lệ");
        return userDAO.deactivate(userId);
    }

    public boolean isEmailExists(String email) throws SQLException {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        return userDAO.existsByEmail(email.trim());
    }

    public boolean isPhoneExists(String phone) throws SQLException {
        if (phone == null || phone.trim().isEmpty()) {
            return false;
        }
        return userDAO.existsByPhone(phone.trim());
    }

    public boolean toggleAccountActive(long accountId) throws SQLException {
        requireValidUserId(accountId, "ID không hợp lệ");
        return userDAO.toggleAccountActive(accountId);
    }

    public boolean setAccountActive(long accountId, boolean active) throws SQLException {
        requireValidUserId(accountId, "ID tài khoản không hợp lệ");
        return userDAO.setActive(accountId, active);
    }

    private static void requireValidUserId(long userId, String message) {
        if (userId <= 0) {
            throw new IllegalArgumentException(message);
        }
    }
}