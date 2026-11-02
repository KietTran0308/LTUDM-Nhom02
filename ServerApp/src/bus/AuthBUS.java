package bus;

import dao.UserDAO;
import dto.UserDTO;
import utils.PasswordReset;

public class AuthBUS {
    private final UserDAO userDAO = new UserDAO();

    private static final PasswordReset RESET_SERVICE = new PasswordReset();

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
        if (userDAO.existsByEmail(reqUser.getEmail())) {
            throw new Exception("Email '" + reqUser.getEmail() + "' đã được sử dụng!");
        }
        reqUser.setPasswordHash(PasswordUtil.encode(reqUser.getPasswordHash()));
        UserDTO created = userDAO.registerUser(reqUser);
        if (created != null) created.setPasswordHash(null);
        return created;
    }
}