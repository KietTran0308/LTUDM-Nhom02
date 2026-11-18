package api;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

public final class ImageSocketClient {

    private static final String HOST = ServerConfig.HOST;
    private static final int PORT = ServerConfig.PORT;

    private static final int CHUNK_BYTES = 8192;
    private static final long MAX_IMAGE_BYTES = 10_000_000L;

    private ImageSocketClient() { }

    public static BufferedImage fetchImage(String imageName) {
        if (imageName == null) return null;

        String requested = imageName.trim().replace('\\', '/');
        if (requested.startsWith("/")) requested = requested.substring(1);
        if (requested.regionMatches(true, 0, "img/", 0, 4)) requested = requested.substring(4);
        if (!requested.matches("(?i)(?:(?:foods|things)/)?[a-z0-9_-]+\\.(png|jpg|jpeg|webp)")) {
            System.err.println("[IMG-CLIENT] Duong dan anh khong hop le: " + imageName);
            return null;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2500);
            socket.setSoTimeout(8000);
            HybridClientHandshake.Session session = HybridClientHandshake.start(socket);
            HybridClientHandshake.sendEncrypted(session,
                    ("IMG/2 " + requested).getBytes(StandardCharsets.UTF_8));

            byte[] encryptedLength = HybridClientHandshake.receiveDecrypted(session);
            if (encryptedLength.length != Long.BYTES) throw new IOException("IMG/2 sai metadata");
            long size = ByteBuffer.wrap(encryptedLength).getLong();
            if (size <= 0 || size > MAX_IMAGE_BYTES) {
                if (size != 0) throw new IOException("IMG/2 kich thuoc anh khong hop le: " + size);
                return null;
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) size);
            long remaining = size;
            while (remaining > 0) {
                // Frame thuc su chua ciphertext, nonce va tag. Gia tri giai ma la byte anh.
                byte[] chunk = HybridClientHandshake.receiveDecrypted(session);
                if (chunk.length == 0 || chunk.length > CHUNK_BYTES || chunk.length > remaining) {
                    throw new IOException("IMG/2 block anh khong hop le");
                }
                bytes.write(chunk);
                remaining -= chunk.length;
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
            if (image == null) throw new IOException("Noi dung anh khong hop le");
            return image;
        } catch (Exception ex) {
            System.err.println("[IMG-CLIENT] Khong tai duoc " + requested + ": " + ex.getMessage());
            return null;
        }
    }
}
