package api;

import security.AESGCMUtil;
import security.RSAKeyExchange;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HexFormat;

public final class HybridClientHandshake {

    public static final int MAX_FRAME_SIZE = 1024 * 1024;
    private static final byte[] EXPECTED_ACK = "OK".getBytes(StandardCharsets.US_ASCII);

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

        // Chỉ gửi AES session key sau khi xác minh fingerprint của Server Public Key.
        PublicKey serverKey = RSAKeyExchange.stringToPublicKey(
                new String(readFrame(in), StandardCharsets.UTF_8));
        verifyServerKey(serverKey);

        SecretKey aesKey = RSAKeyExchange.generateAESKey();
        String encryptedKey = RSAKeyExchange.encryptAESKey(aesKey, serverKey);
        writeFrame(out, encryptedKey.getBytes(StandardCharsets.UTF_8));

        // Server chứng minh sở hữu private key bằng ACK mã hóa AES-GCM.
        byte[] ack = AESGCMUtil.decrypt(readFrame(in), aesKey);
        if (!MessageDigest.isEqual(ack, EXPECTED_ACK)) {
            throw new IOException("Server không xác nhận được AES session key");
        }
        return new Session(in, out, aesKey);
    }

    public static void verifyServerKey(PublicKey key) throws Exception {
        String configured = ServerConfig.SERVER_RSA_SHA256;
        if (configured == null || configured.isBlank()) {
            throw new SecurityException("Chưa cấu hình SERVER_RSA_SHA256 (64 ký tự hex); "
                    + "lấy fingerprint từ màn hình Server và kiểm tra qua kênh tin cậy trước khi kết nối");
        }

        String pin = configured.trim().replace(":", "");
        if (!pin.matches("(?i)[0-9a-f]{64}")) {
            throw new SecurityException("SERVER_RSA_SHA256 không hợp lệ (phải là chuỗi SHA-256 hex 64 ký tự)");
        }
        if (!(key instanceof RSAPublicKey rsaKey) || rsaKey.getModulus().bitLength() < 2048) {
            throw new SecurityException("Server RSA key phải >= 2048 bit");
        }

        byte[] actual = MessageDigest.getInstance("SHA-256").digest(key.getEncoded());
        if (!MessageDigest.isEqual(actual, HexFormat.of().parseHex(pin))) {
            throw new SecurityException("RSA Public Key của Server KHÔNG khớp fingerprint tin cậy. "
                    + "Từ chối kết nối (nguy cơ MITM hoặc Server đã đổi khóa)");
        }
    }

    public static byte[] readFrame(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size <= 0 || size > MAX_FRAME_SIZE) {
            throw new IOException("Kích thước frame nhận không hợp lệ: " + size);
        }
        byte[] buffer = new byte[size];
        input.readFully(buffer);
        return buffer;
    }

    public static void writeFrame(DataOutputStream output, byte[] buffer) throws IOException {
        if (buffer == null || buffer.length == 0 || buffer.length > MAX_FRAME_SIZE) {
            throw new IOException("Kích thước frame gửi không hợp lệ");
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