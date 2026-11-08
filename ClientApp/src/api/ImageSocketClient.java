package api;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public final class ImageSocketClient {

    private static final String HOST = System.getenv().getOrDefault("SERVER_HOST", "localhost");
    private static final int PORT = 8888;
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
        final String safeName = requested;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 2500);
            socket.setSoTimeout(8000);

            try (DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                 DataInputStream input = new DataInputStream(socket.getInputStream())) {
                output.write(("IMG/1 " + safeName + "\n").getBytes(StandardCharsets.US_ASCII));
                output.flush();

                long fileSize = input.readLong();
                if (fileSize <= 0L || fileSize > MAX_IMAGE_BYTES) {
                    System.err.println("[IMG-CLIENT] Server khong co anh hoac anh vuot gioi han: " + safeName
                            + " (size=" + fileSize + ")");
                    return null;
                }

                ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) fileSize);
                byte[] chunk = new byte[CHUNK_BYTES];
                long remaining = fileSize;
                while (remaining > 0L) {
                    int count = input.read(chunk, 0, (int) Math.min(chunk.length, remaining));
                    if (count < 0) throw new IOException("Anh chua nhan du byte: " + imageName);
                    bytes.write(chunk, 0, count);
                    remaining -= count;
                }

                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes.toByteArray()));
                return decoded;
            }
        } catch (IOException ex) {
            System.err.println("[IMG-CLIENT] Loi TCP " + safeName + " @ " + HOST + ":" + PORT + ": " + ex.getMessage());
            return null;
        }
    }
}
