package bus;

import api.SocketClient;
import dto.SocketAction;
import dto.SocketPacket;
import dto.UserDTO;
import java.util.function.Consumer;

public class AuthBUS {
    private final SocketClient socketClient = new SocketClient();
    private final Consumer<SocketPacket> uiCallback;

    public AuthBUS(Consumer<SocketPacket> uiCallback) {
        this.uiCallback = uiCallback;
        socketClient.connect("localhost", 8888, -1L, packet -> {
            if (this.uiCallback != null) {
                this.uiCallback.accept(packet);
            }
        });
    }

    public boolean login(String emailOrPhone, String password) {
        if (!socketClient.isConnected()) return false;
        socketClient.sendRequest(new SocketPacket(SocketAction.LOGIN, -1L, emailOrPhone, password));
        return true;
    }

    public boolean register(String fullName, String email, String phone, String password) {
        if (!socketClient.isConnected()) return false;
        UserDTO newUser = new UserDTO();
        newUser.setFullName(fullName);
        newUser.setEmail(email);
        newUser.setPhone(phone);
        newUser.setPasswordHash(password);
        socketClient.sendRequest(new SocketPacket(SocketAction.REGISTER, -1L, newUser, null));
        return true;
    }

    public boolean isServerConnected() {
        return socketClient.isConnected();
    }
}