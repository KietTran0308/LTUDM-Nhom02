package api;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

public class ImageSocketClient {
    private static final String HOST = "localhost";
    private static final int PORT = 8889;
    private static final long MAX_IMAGE_BYTES = 10_000_000L;

    public static BufferedImage fetchImage(String imageName) {
        if (imageName == null || imageName.isBlank()) return null;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), 1200);
            socket.setSoTimeout(3000);
            try (DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 DataInputStream dis = new DataInputStream(socket.getInputStream())) {
                dos.writeUTF(imageName);
                dos.flush();

                long fileSize = dis.readLong();
                if (fileSize <= 0 || fileSize > MAX_IMAGE_BYTES) return null;

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] data = new byte[8192];
                long totalRead = 0;
                while (totalRead < fileSize) {
                    int bytesRead = dis.read(data, 0,
                            (int) Math.min(data.length, fileSize - totalRead));
                    if (bytesRead < 0) return null;
                    buffer.write(data, 0, bytesRead);
                    totalRead += bytesRead;
                }
                return ImageIO.read(new ByteArrayInputStream(buffer.toByteArray()));
            }
        } catch (IOException e) {
            return null;
        }
    }
}
