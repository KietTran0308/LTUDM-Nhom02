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

    private static final ThreadLocal<ImageConn> THREAD_CONN = new ThreadLocal<>();

    private static final class ImageConn {
        final Socket socket;
        final HybridClientHandshake.Session session;

        ImageConn(Socket socket, HybridClientHandshake.Session session) {
            this.socket = socket;
            this.session = session;
        }

        void closeQuietly() {
            try { socket.close(); } catch (IOException ignored) { }
        }
    }

    private ImageSocketClient() { }

    private static ImageConn getOrCreateConn() throws Exception {
        ImageConn conn = THREAD_CONN.get();
        if (conn != null && !conn.socket.isClosed() && conn.socket.isConnected()) {
            return conn;
        }
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(HOST, PORT), 2500);
        socket.setSoTimeout(8000);
        socket.setKeepAlive(true);
        HybridClientHandshake.Session session = HybridClientHandshake.start(socket);
        conn = new ImageConn(socket, session);
        THREAD_CONN.set(conn);
        return conn;
    }

    public static BufferedImage fetchImage(String imageName) {
        if (imageName == null) return null;

        String requested = imageName.trim().replace('\\', '/');
        if (requested.startsWith("/")) requested = requested.substring(1);
        if (requested.regionMatches(true, 0, "img/", 0, 4)) requested = requested.substring(4);
        if (!requested.matches("(?i)(?:(?:foods|things)/)?[a-z0-9_-]+\\.(png|jpg|jpeg|webp)")) {
            System.err.println("[IMG-CLIENT] Duong dan anh khong hop le: " + imageName);
            return null;
        }

        // Thử tối đa 2 lần (nếu kết nối cũ bị Server đóng do idle thì tự mở lại 1 lần)
        for (int attempt = 0; attempt < 2; attempt++) {
            ImageConn conn = null;
            try {
                conn = getOrCreateConn();
                HybridClientHandshake.sendEncrypted(conn.session,
                        ("IMG/2 " + requested).getBytes(StandardCharsets.UTF_8));

                byte[] encryptedLength = HybridClientHandshake.receiveDecrypted(conn.session);
                if (encryptedLength.length != Long.BYTES) throw new IOException("IMG/2 sai metadata");
                long size = ByteBuffer.wrap(encryptedLength).getLong();
                if (size <= 0 || size > MAX_IMAGE_BYTES) {
                    if (size != 0) throw new IOException("IMG/2 kich thuoc anh khong hop le: " + size);
                    return null; // Ảnh không tồn tại, nhưng kết nối vẫn sạch để dùng tiếp!
                }

                ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) size);
                long remaining = size;
                while (remaining > 0) {
                    byte[] chunk = HybridClientHandshake.receiveDecrypted(conn.session);
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
                if (conn != null) conn.closeQuietly();
                THREAD_CONN.remove();
                if (attempt == 1) {
                    System.err.println("[IMG-CLIENT] Khong tai duoc " + requested + ": " + ex.getMessage());
                }
            }
        }
        return null;
    }
}