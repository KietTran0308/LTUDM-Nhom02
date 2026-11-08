package bus;

import api.SocketClient;
import dto.*;

import java.util.HashMap;
import java.util.Map;

/**
 * BUS chi gui yeu cau va lay phan hoi, khong phu thuoc GUI/callback.
 */
public class AuthBUS {
    private final SocketClient socketClient = new SocketClient();
    private final String serverHost = System.getenv().getOrDefault("SERVER_HOST", "localhost");

    public AuthBUS() {
        socketClient.connect(serverHost, 8888, null);
    }

    /** Đăng nhập đồng bộ cho POS/Admin trên kết nối độc lập; gọi ở SwingWorker. */
    public static UserDTO loginWorkstation(SocketClient socket, String account, String password) {
        String host = System.getenv().getOrDefault("SERVER_HOST", "localhost");
        if (socket == null || (!socket.isConnected() && !socket.connect(host, 8888, null)))
            throw new IllegalStateException("Không kết nối được tới Server " + host + ":8888");
        SocketPacket response = socket.requestAndWait(
                new SocketPacket(SocketAction.LOGIN, null, account, password), 7000);
        if (!(response.getData() instanceof UserDTO))
            throw new IllegalStateException("Phản hồi đăng nhập không có thông tin tài khoản");
        return (UserDTO) response.getData();
    }

    public boolean login(String account, String password) {
        if (!socketClient.isConnected() && !socketClient.connect(serverHost, 8888, null)) return false;
        return socketClient.sendRequest(new SocketPacket(SocketAction.LOGIN_STAFF, null, account, password));
    }

    public boolean register(String fullName, String email, String phone, String password) {
        if (!socketClient.isConnected() && !socketClient.connect(serverHost, 8888, null)) return false;
        UserDTO user = new UserDTO();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(password); // mat khau goc chi gui qua ket noi; Server phai hash
        return socketClient.sendRequest(new SocketPacket(SocketAction.REGISTER, null, user, null));
    }

    /**
     * Yêu cầu gửi mã xác minh tới email đã đăng ký.
     */
    public boolean requestPasswordReset(String email) {
        if (!socketClient.isConnected() && !socketClient.connect(serverHost, 8888, null)) return false;
        return socketClient.sendRequest(new SocketPacket(
                SocketAction.REQUEST_PASSWORD_RESET, null, email.trim(), null));
    }

    /**
     * Chỉ gửi mật khẩu mới tới máy chủ cùng OTP. Không trực tiếp sửa database.
     */
    public boolean confirmPasswordReset(String email, String otp, String newPassword) {
        if (!socketClient.isConnected() && !socketClient.connect(serverHost, 8888, null)) return false;
        Map<String, String> payload = new HashMap<>();
        payload.put("email", email.trim());
        payload.put("otp", otp.trim());
        payload.put("newPassword", newPassword);
        return socketClient.sendRequest(new SocketPacket(
                SocketAction.CONFIRM_PASSWORD_RESET, null, payload, null));
    }

    public SocketPacket pollPacket() {
        return socketClient.pollPacket();
    }

    public SocketClient getSocketClient() {
        return socketClient;
    }

    public boolean isServerConnected() {
        return socketClient.isConnected();
    }

    public void close() {
        socketClient.close();
    }
}
