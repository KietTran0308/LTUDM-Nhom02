package bus;

import api.SocketClient;
import dto.*;
import java.util.HashMap;
import java.util.Map;

/** Xu ly phan hoi xac thuc; GUI khong can biet SocketAction. */
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
            this.user = packet.getData() instanceof UserDTO ? (UserDTO) packet.getData() : null;
        }
    }

    private final SocketClient socketClient = new SocketClient();

    public AuthBUS() {

    }

    /** POS/Admin dung ket noi rieng, goi trong SwingWorker. Host/Port lay tu ServerConfig. */
    public static UserDTO loginWorkstation(SocketClient socket, String account, String password) {
        if (socket == null)
            throw new IllegalArgumentException("Socket khong hop le");
        if (account == null || account.isBlank() || password == null || password.isEmpty())
            throw new IllegalArgumentException("Vui long nhap tai khoan va mat khau");

        if (!socket.isConnected() && !socket.connect(null))
            throw new IllegalStateException("Khong ket noi duoc Server");

        SocketPacket response = socket.requestAndWait(
                new SocketPacket(SocketAction.LOGIN, null, account.trim(), password), 7000);
        if (!(response.getData() instanceof UserDTO))
            throw new IllegalStateException("Phan hoi dang nhap khong hop le");
        return (UserDTO) response.getData();
    }

    /** Host/Port lay tu ServerConfig (server.host / SERVER_HOST, server.port / SERVER_PORT). */
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

    /** Chi doc hang doi; duoc goi boi Swing Timer tren EDT. */
    public Event pollEvent() {
        SocketPacket packet;
        while ((packet = socketClient.pollPacket()) != null) {
            Type type;
            switch (packet.getAction()) {
                case LOGIN:
                case LOGIN_STAFF: type = Type.LOGIN; break;
                case REGISTER: type = Type.REGISTER; break;
                case REQUEST_PASSWORD_RESET: type = Type.RESET_CODE; break;
                case CONFIRM_PASSWORD_RESET: type = Type.RESET_PASSWORD; break;
                default: continue;
            }
            return new Event(type, packet);
        }
        return null;
    }

    public SocketClient getSocketClient() { return socketClient; }

    public void close() { socketClient.close(); }
}
