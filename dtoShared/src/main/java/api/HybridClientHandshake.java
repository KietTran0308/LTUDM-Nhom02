package api;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HexFormat;
import javax.crypto.SecretKey;
import security.AESGCMUtil;
import security.RSAKeyExchange;

public final class HybridClientHandshake {
    public static final int MAX_FRAME_SIZE = 1024 * 1024;

    private HybridClientHandshake() { }

    public static final class Session {
        public final DataInputStream in;
        public final DataOutputStream out;
        public final SecretKey key;

        private Session(DataInputStream in, DataOutputStream out, SecretKey key) {
            this.in = in;
            this.out = out;
            this.key = key;
        }
    }

    public static Session start(Socket socket) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

        // Chua gui thong tin bi mat cho den khi public key duoc xac minh.
        PublicKey serverKey = RSAKeyExchange.stringToPublicKey(
                new String(readFrame(in), StandardCharsets.UTF_8));
        verifyServerKey(serverKey);

        SecretKey aesKey = RSAKeyExchange.generateAESKey();
        String encryptedKey = RSAKeyExchange.encryptAESKey(aesKey, serverKey);
        writeFrame(out, encryptedKey.getBytes(StandardCharsets.UTF_8));

        // Server chung minh so huu private key bang ACK ma hoa AES-GCM.
        byte[] ack = AESGCMUtil.decrypt(readFrame(in), aesKey);
        if (!MessageDigest.isEqual(ack, "OK".getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("Server khong xac nhan duoc AES session key");
        }
        return new Session(in, out, aesKey);
    }

    public static void verifyServerKey(PublicKey key) throws Exception {
        String configured = ServerConfig.SERVER_RSA_SHA256;
        if (configured == null || configured.isBlank()) {

            throw new SecurityException("Chua cau hinh SERVER_RSA_SHA256 (64 ky tu hex); "
                    + "lay fingerprint tu man hinh Server, kiem tra qua kenh tin cay truoc khi ket noi");
        }
        String pin = configured.trim().replace(":", "");
        if (!pin.matches("(?i)[0-9a-f]{64}")) {
            throw new SecurityException("SERVER_RSA_SHA256 khong hop le (phai la SHA-256 hex)");
        }
        if (!(key instanceof RSAPublicKey)
                || ((RSAPublicKey) key).getModulus().bitLength() < 2048) {
            throw new SecurityException("Server RSA key phai >= 2048 bit");
        }
        byte[] actual = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        if (!MessageDigest.isEqual(actual, HexFormat.of().parseHex(pin))) {
            throw new SecurityException("RSA Public Key Server KHONG khop fingerprint tin cay. "
                    + "Tu choi ket noi (nguy co MITM hoac Server da doi khoa)");
        }
    }

    public static byte[] readFrame(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_FRAME_SIZE) {
            throw new IOException("Kich thuoc frame khong hop le: " + size);
        }
        byte[] buffer = new byte[size];
        input.readFully(buffer);
        return buffer;
    }

    public static void writeFrame(DataOutputStream output, byte[] buffer) throws IOException {
        if (buffer == null || buffer.length == 0 || buffer.length > MAX_FRAME_SIZE) {
            throw new IOException("Kich thuoc frame gui khong hop le");
        }
        output.writeInt(buffer.length);
        output.write(buffer);
        output.flush();
    }

    public static void sendEncrypted(Session session, byte[] rawData) throws Exception {
        writeFrame(session.out, AESGCMUtil.encrypt(rawData, session.key));
    }

    public static byte[] receiveDecrypted(Session session) throws Exception {
        return AESGCMUtil.decrypt(readFrame(session.in), session.key);
    }
}
