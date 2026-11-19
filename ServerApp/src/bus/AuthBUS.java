package bus;

import dao.UserDAO;
import dto.UserDTO;
import utils.PasswordReset;
import utils.PasswordUtil;

import java.sql.SQLException;

public class AuthBUS {
    private final UserDAO userDAO = new UserDAO();

    private static final PasswordReset RESET_SERVICE = new PasswordReset();
    private static final Object REGISTER_LOCK = new Object();

    public UserDTO login(String emailOrPhone, String password) throws Exception {
        if (emailOrPhone == null || emailOrPhone.trim().isEmpty() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập đầy đủ tài khoản và mật khẩu!");
        }
        UserDTO user = userDAO.findByEmailOrPhone(emailOrPhone, password);
        if (user == null) {
            throw new Exception("Tài khoản hoặc mật khẩu không chính xác (hoặc đã bị khóa)!");
        }
        user.setPasswordHash(null);
        return user;
    }

    public void requestPasswordReset(String email) throws Exception {
        RESET_SERVICE.requestCode(email);
    }

    public boolean confirmPasswordReset(String email, String otp, String newPassword) throws Exception {
        return RESET_SERVICE.confirm(email, otp, newPassword);
    }

    public UserDTO register(UserDTO reqUser) throws Exception {
        // Các kết nối cùng dùng một khóa để hai yêu cầu không thể cùng vượt qua bước kiểm tra trùng.
        synchronized (REGISTER_LOCK) {
            if (userDAO.existsByEmail(reqUser.getEmail())) {
                throw new IllegalArgumentException("Email đã được sử dụng.");
            }
            String phone = reqUser.getPhone();
            if (phone != null && !phone.isBlank() && isPhoneRegistered(phone.trim())) {
                throw new IllegalArgumentException("Số điện thoại đã được sử dụng.");
            }
            reqUser.setPasswordHash(PasswordUtil.encode(reqUser.getPasswordHash()));
            UserDTO created = userDAO.registerUser(reqUser);
            if (created == null) throw new IllegalStateException("Không thể tạo tài khoản.");
            created.setPasswordHash(null);
            return created;
        }
    }

    private boolean isPhoneRegistered(String phone) throws SQLException {
        if (userDAO.existsByPhone(phone)) return true;
        if (phone.matches("0\\d{9}")) {
            return userDAO.existsByPhone("+84" + phone.substring(1));
        }
        if (phone.matches("\\+84\\d{9}")) {
            return userDAO.existsByPhone("0" + phone.substring(3));
        }
        return false;
    }
}
