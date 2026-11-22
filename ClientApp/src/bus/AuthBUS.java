package bus;

import api.SocketClient;
import dto.SocketAction;
import dto.SocketPacket;
import dto.UserDTO;

import java.util.HashMap;
import java.util.Map;

public class AuthBUS {
    public enum Type { LOGIN, REGISTER, RESET_CODE, RESET_PASSWORD }

    public static final class Event {
        public final Type type;
        public final boolean success;
        public final String message;
        public final UserDTO user;

        private Event(Type type, SocketPacket packet) {
            this.type = type;
            this.success = packet.isSuccess();
            this.message = packet.getMessage();
            this.user = packet.getData() instanceof UserDTO u ? u : null;
        }
    }

    private final SocketClient socketClient = new SocketClient();

    public static UserDTO loginWorkstation(SocketClient socket, String account, String password) {
        if (socket == null) {
            throw new IllegalArgumentException("Socket không hợp lệ");
        }
        if (account == null || account.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Vui lòng nhập tài khoản và mật khẩu");
        }
        if (!socket.isConnected() && !socket.connect(null)) {
            throw new IllegalStateException("Không kết nối được Server");
        }

        SocketPacket response = socket.requestAndWait(
                new SocketPacket(SocketAction.LOGIN, null, account.trim(), password), 7000);
        if (!(response.getData() instanceof UserDTO user)) {
            throw new IllegalStateException("Phản hồi đăng nhập không hợp lệ");
        }
        return user;
    }

    private boolean ready() {
        return socketClient.isConnected() || socketClient.connect(null);
    }

    public boolean login(String account, String password) {
        return ready() && socketClient.sendRequest(
                new SocketPacket(SocketAction.LOGIN_STAFF, null, account, password));
    }

    public boolean requestPasswordReset(String email) {
        return ready() && socketClient.sendRequest(
                new SocketPacket(SocketAction.REQUEST_PASSWORD_RESET, null, email.trim(), null));
    }

    public boolean confirmPasswordReset(String email, String otp, String password) {
        if (!ready()) return false;
        Map<String, String> data = new HashMap<>();
        data.put("email", email.trim());
        data.put("otp", otp.trim());
        data.put("newPassword", password);
        return socketClient.sendRequest(
                new SocketPacket(SocketAction.CONFIRM_PASSWORD_RESET, null, data, null));
    }

    public Event pollEvent() {
        SocketPacket packet;
        while ((packet = socketClient.pollPacket()) != null) {
            Type type = switch (packet.getAction()) {
                case LOGIN, LOGIN_STAFF -> Type.LOGIN;
                case REGISTER -> Type.REGISTER;
                case REQUEST_PASSWORD_RESET -> Type.RESET_CODE;
                case CONFIRM_PASSWORD_RESET -> Type.RESET_PASSWORD;
                default -> null;
            };
            if (type != null) {
                return new Event(type, packet);
            }
        }
        return null;
    }

    public SocketClient getSocketClient() {
        return socketClient;
    }

    public void close() {
        socketClient.close();
    }
}