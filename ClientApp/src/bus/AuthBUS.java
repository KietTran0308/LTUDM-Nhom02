package bus;

import api.SocketClient;
import dto.*;

/**
 * BUS chi gui yeu cau va lay phan hoi, khong phu thuoc GUI/callback.
 */
public class AuthBUS {
    private final SocketClient socketClient = new SocketClient();

    public AuthBUS() {
        socketClient.connect("localhost", 8888, null);
    }

    public boolean login(String account, String password) {
        if (!socketClient.isConnected() && !socketClient.connect("localhost", 8888, null)) return false;
        return socketClient.sendRequest(new SocketPacket(SocketAction.LOGIN, null, account, password));
    }

    public boolean register(String fullName, String email, String phone, String password) {
        if (!socketClient.isConnected() && !socketClient.connect("localhost", 8888, null)) return false;
        UserDTO user = new UserDTO();
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(password); // mat khau goc chi gui qua ket noi; Server phai hash
        return socketClient.sendRequest(new SocketPacket(SocketAction.REGISTER, null, user, null));
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
