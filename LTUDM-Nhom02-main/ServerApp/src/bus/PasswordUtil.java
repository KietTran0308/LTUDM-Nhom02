package bus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Java thuần; lưu PBKDF2 mới và đọc mật khẩu plaintext cũ để không phá tài khoản hiện có.
 */
public final class PasswordUtil {
    private PasswordUtil() {
    }

    private static final int ITERATIONS = 210_000;

    public static String encode(String password) {
        if (password == null || password.isBlank()) throw new IllegalArgumentException("Mật khẩu không được trống");
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        byte[] digest = derive(password, salt, ITERATIONS);
        return "pbkdf2$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(digest);
    }

    private static byte[] derive(String password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, 256);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("Không hỗ trợ PBKDF2", e);
        } finally {
            spec.clearPassword();
        }
    }

    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) return false;
        if (!stored.startsWith("pbkdf2$")) {
            // Tương thích tài khoản do ứng dụng Java thuần cũ tạo. Nên đổi mật khẩu để nâng cấp hash.
            return MessageDigest.isEqual(raw.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8));
        }
        try {
            String[] p = stored.split("\\$", 4);
            if (p.length != 4) return false;
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] expected = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(expected, derive(raw, salt, Integer.parseInt(p[1])));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
