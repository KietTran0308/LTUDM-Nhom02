package api;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;

public final class ImageSocketServer {
    private static final int CHUNK_BYTES = 8192;
    private static final long MAX_IMAGE_BYTES = 10_000_000L;
    private static final int READ_TIMEOUT_MS = 8000;
    private ImageSocketServer() { }

    public static void sendImage(Socket client, String imageName) throws IOException {
        if (client == null) throw new IllegalArgumentException("Missing image socket");

        client.setSoTimeout(READ_TIMEOUT_MS);
        DataOutputStream output = new DataOutputStream(client.getOutputStream());
        // Khong nhan duong dan he dieu hanh, chi chap nhan foods/ hoac things/.
        if (imageName != null && imageName.regionMatches(true, 0, "img/", 0, 4)) {
            imageName = imageName.substring(4); // Chap nhan client cu gui img/things/logo.png
        }
        if (imageName == null || !imageName.matches("(?i)(?:(?:foods|things)/)?[a-z0-9_-]+\\.(png|jpg|jpeg|webp)")) {
            output.writeLong(0L);
            output.flush();
            return;
        }
        sendResourceBytes(imageName, output);
    }

    private static void sendResourceBytes(String imageName, DataOutputStream output) throws IOException {
        // IMG/1 ten-file.png: tuong thich anh mon cu, mac dinh folder foods.
        // IMG/1 things/logo.png: anh logo UI duoc gui tu ServerApp qua TCP.
        String folder = "foods";
        String filename = imageName;
        int slash = imageName.indexOf('/');
        if (slash >= 0) {
            folder = imageName.substring(0, slash).toLowerCase(java.util.Locale.ROOT);
            filename = imageName.substring(slash + 1);
        }
        URL resource = ImageSocketServer.class.getResource("/img/" + folder + "/" + filename);
        if (resource == null) {
            resource = ImageSocketServer.class.getResource("/gui/img/" + folder + "/" + filename);
        }
        if (resource == null) {
            System.err.println("[IMG-SERVER] Khong tim thay /img/" + folder + "/" + filename
                    + " trong ServerApp classpath (kiem tra Clean & Build)");
            output.writeLong(0L);
            output.flush();
            return;
        }
        URLConnection connection = resource.openConnection();
        connection.setUseCaches(false);
        long size = connection.getContentLengthLong();
        if (size == 0L || size > MAX_IMAGE_BYTES) {
            System.err.println("[IMG-SERVER] Anh rong/qua gioi han: " + imageName + " (" + size + " bytes)");
            output.writeLong(0L);
            output.flush();
            return;
        }
        try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
            if (size < 0L) {
                // Resource trong JAR khong luon tra duoc Content-Length: doc gioi han.
                byte[] bytes = readBounded(input);
                if (bytes == null || bytes.length == 0) {
                    output.writeLong(0L);
                } else {
                    output.writeLong(bytes.length);
                    for (int offset = 0; offset < bytes.length; offset += CHUNK_BYTES) {
                        output.write(bytes, offset, Math.min(CHUNK_BYTES, bytes.length - offset));
                    }
                }
            } else {
                // Truyen streaming that su theo tung khoi nho, khong ma hoa Base64.
                output.writeLong(size);
                byte[] chunk = new byte[CHUNK_BYTES];
                long remaining = size;
                while (remaining > 0L) {
                    int read = input.read(chunk, 0, (int) Math.min(chunk.length, remaining));
                    if (read < 0) throw new IOException("Image file cut short");
                    output.write(chunk, 0, read);
                    remaining -= read;
                }
            }
            output.flush();
        }
    }

    private static byte[] readBounded(InputStream source) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] chunk = new byte[CHUNK_BYTES];
        int count;
        while ((count = source.read(chunk)) != -1) {
            if (output.size() + (long) count > MAX_IMAGE_BYTES) return null;
            output.write(chunk, 0, count);
        }
        return output.toByteArray();
    }
}
