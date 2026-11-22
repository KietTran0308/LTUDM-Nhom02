package bus;

import dao.UserDAO;
import dto.UserDTO;
import utils.PasswordReset;
import utils.PasswordUtil;

import java.sql.SQLException;
import java.util.regex.Pattern;

public class AuthBUS {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE_10_DIGITS = Pattern.compile("0\\d{9}");
    private static final Pattern PHONE_PLUS84 = Pattern.compile("\\+84\\d{9}");
    private static final PasswordReset RESET_SERVICE = new PasswordReset();
    private static final Object REGISTER_LOCK = new Object();

    private final UserDAO userDAO = new UserDAO();

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
        return createAccountInternal(reqUser, null);
    }

    public UserDTO registerWithRole(UserDTO reqUser, String roleName) throws Exception {
        return createAccountInternal(reqUser, roleName);
    }

    private UserDTO createAccountInternal(UserDTO reqUser, String roleName) throws Exception {
        synchronized (REGISTER_LOCK) {
            validateRequiredContact(reqUser);
            if (userDAO.existsByEmail(reqUser.getEmail())) {
                throw new IllegalArgumentException("Email đã được sử dụng.");
            }
            String phone = reqUser.getPhone();
            if (phone != null && !phone.isBlank() && isPhoneRegistered(phone.trim())) {
                throw new IllegalArgumentException("Số điện thoại đã được sử dụng.");
            }
            reqUser.setPasswordHash(PasswordUtil.encode(reqUser.getPasswordHash()));

            UserDTO created = roleName == null
                    ? userDAO.registerUser(reqUser)
                    : userDAO.registerUserWithRole(reqUser, roleName);
            if (created == null) {
                throw new IllegalStateException("Không thể tạo tài khoản.");
            }
            created.setPasswordHash(null);
            return created;
        }
    }

    private boolean isPhoneRegistered(String phone) throws SQLException {
        if (userDAO.existsByPhone(phone)) return true;
        if (PHONE_10_DIGITS.matcher(phone).matches()) {
            return userDAO.existsByPhone("+84" + phone.substring(1));
        }
        if (PHONE_PLUS84.matcher(phone).matches()) {
            return userDAO.existsByPhone("0" + phone.substring(3));
        }
        return false;
    }

    private static void validateRequiredContact(UserDTO reqUser) {
        if (reqUser == null) {
            throw new IllegalArgumentException("Thông tin đăng ký không hợp lệ.");
        }
        if (reqUser.getFullName() == null || reqUser.getFullName().isBlank()) {
            throw new IllegalArgumentException("Họ tên không được để trống.");
        }
        String email = reqUser.getEmail();
        if (email == null || email.isBlank() || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("Email bắt buộc nhập và phải đúng định dạng.");
        }
        String phone = reqUser.getPhone();
        if (phone == null || !PHONE_10_DIGITS.matcher(phone.trim()).matches()) {
            throw new IllegalArgumentException("Số điện thoại bắt buộc nhập (10 chữ số, bắt đầu bằng 0).");
        }
        reqUser.setFullName(reqUser.getFullName().trim());
        reqUser.setEmail(email.trim());
        reqUser.setPhone(phone.trim());
    }
}