package api;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import javax.crypto.SecretKey;
import security.AESGCMUtil;

/** Phuc vu anh IMG/2: moi block byte duoc ma hoa AES-GCM theo khoa phien. */
public final class ImageSocketServer {

    private static final int CHUNK_BYTES = 8192;
    private static final long MAX_IMAGE_BYTES = 10_000_000L;
    private ImageSocketServer() { }

    public static void sendImage(String imageName, DataOutputStream out, SecretKey key) throws Exception {
        if (imageName == null || !imageName.matches(
                "(?i)(?:(?:foods|things)/)?[a-z0-9_-]+\\.(png|jpg|jpeg|webp)")) {
            writeEncrypted(out, key, ByteBuffer.allocate(Long.BYTES).putLong(0).array());
            return;
        }
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
            writeEncrypted(out, key, ByteBuffer.allocate(Long.BYTES).putLong(0).array());
            return;
        }
        URLConnection conn = resource.openConnection();
        conn.setUseCaches(false);
        long length = conn.getContentLengthLong();
        if (length == 0 || length > MAX_IMAGE_BYTES) {
            writeEncrypted(out, key, ByteBuffer.allocate(Long.BYTES).putLong(0).array());
            return;
        }
        try (InputStream input = new BufferedInputStream(conn.getInputStream())) {
            if (length < 0) {
                // JAR co the khong tra Content-Length. Gioi han bo nho 10 MB.
                byte[] data = readBounded(input);
                if (data == null || data.length == 0) {
                    writeEncrypted(out, key, ByteBuffer.allocate(Long.BYTES).putLong(0).array());
                    return;
                }
                writeEncrypted(out, key, ByteBuffer.allocate(Long.BYTES).putLong(data.length).array());
                for (int i = 0; i < data.length; i += CHUNK_BYTES) {
                    int n = Math.min(CHUNK_BYTES, data.length - i);
                    writeEncrypted(out, key, java.util.Arrays.copyOfRange(data, i, i + n));
                }
            } else {
                writeEncrypted(out, key, ByteBuffer.allocate(Long.BYTES).putLong(length).array());
                byte[] chunk = new byte[CHUNK_BYTES];
                long remaining = length;
                while (remaining > 0) {
                    int n = input.read(chunk, 0, (int) Math.min(remaining, chunk.length));
                    if (n < 0) throw new IOException("Image resource bi cat ngang");
                    if (n == 0) continue;
                    writeEncrypted(out, key, java.util.Arrays.copyOf(chunk, n));
                    remaining -= n;
                }
            }
        }
    }

    private static void writeEncrypted(DataOutputStream out, SecretKey key, byte[] content) throws Exception {
        byte[] ciphertext = AESGCMUtil.encrypt(content, key);
        if (ciphertext.length > 1024 * 1024) throw new IOException("Frame qua lon");
        out.writeInt(ciphertext.length);
        out.write(ciphertext);
        out.flush();
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[CHUNK_BYTES];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (bytes.size() + (long) n > MAX_IMAGE_BYTES) return null;
            bytes.write(buffer, 0, n);
        }
        return bytes.toByteArray();
    }
}
