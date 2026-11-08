package utils;

import dao.UserDAO;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class PasswordReset {

    private static final long TTL_MS = 10 * 60_000L;
    private static final long COOLDOWN_MS = 60_000L;
    private static final long WINDOW_MS = 60 * 60_000L;
    private static final int MAX_SENDS_PER_HOUR = 3;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,63}$",
            Pattern.CASE_INSENSITIVE);

    private final UserDAO userDAO;
    private final SecureRandom rng = new SecureRandom();
    private final Map<String, Pending> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, SendLimit> sendLimits = new ConcurrentHashMap<>();

    public PasswordReset() {
        this(new UserDAO());
    }

    // Cho phép kiểm thử nghiệp vụ với UserDAO giả mà không cần MySQL/SMTP thật.
    PasswordReset(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    private static final class Pending {
        final byte[] salt;
        final byte[] digest;
        final long expiresAt;
        int failedAttempts;

        Pending(byte[] salt, byte[] digest, long expiresAt) {
            this.salt = salt;
            this.digest = digest;
            this.expiresAt = expiresAt;
        }
    }

    private static final class SendLimit {
        long windowStart;
        long lastSent;
        int count;
    }

    private static String normalizeEmail(String input) {
        if (input == null || input.length() > 254) throw new IllegalArgumentException("Email không hợp lệ.");
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(value).matches()) throw new IllegalArgumentException("Vui lòng nhập đúng địa chỉ email.");
        return value;
    }

    /** Tạo mã, băm mã, gửi qua SMTP. Phản hồi luôn chung cho email có/không tồn tại. */
    public void requestCode(String rawEmail) throws Exception {
        String email = normalizeEmail(rawEmail);
        if (!smtpConfigured()) throw new IllegalStateException("Server chưa cấu hình SMTP");

        long now = System.currentTimeMillis();
        SendLimit limit = sendLimits.computeIfAbsent(email, k -> new SendLimit());
        synchronized (limit) {
            if (now - limit.windowStart >= WINDOW_MS) {
                limit.windowStart = now;
                limit.count = 0;
                limit.lastSent = 0L;
            }
            if (now - limit.lastSent < COOLDOWN_MS || limit.count >= MAX_SENDS_PER_HOUR) {
                return; // Không tiết lộ trạng thái tài khoản hoặc rate limit qua response.
            }
            limit.lastSent = now;
            limit.count++;
        }

        boolean exists;
        try {
            exists = userDAO.existsActiveByEmail(email);
        } catch (SQLException ex) {
            rollbackLimit(limit);
            throw ex;
        }
        if (!exists) return;

        String code = String.format(Locale.ROOT, "%06d", rng.nextInt(1_000_000));
        byte[] salt = new byte[16];
        rng.nextBytes(salt);
        Pending entry = new Pending(salt, hashOtp(email, code, salt), now + TTL_MS);
        pendingCodes.put(email, entry);
        try {
            sendOtpMail(email, code);
        } catch (Exception ex) {
            pendingCodes.remove(email, entry);
            rollbackLimit(limit);
            throw ex;
        }
    }

    private void rollbackLimit(SendLimit limit) {
        synchronized (limit) {
            if (limit.count > 0) limit.count--;
            limit.lastSent = 0L;
        }
    }

    /** Mã OTP đúng, chưa hết hạn mới được cập nhật mật khẩu băm trong users. */
    public boolean confirm(String rawEmail, String otp, String newPassword) throws Exception {
        String email = normalizeEmail(rawEmail);
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 72 || newPassword.isBlank()) {
            throw new IllegalArgumentException("Mật khẩu mới phải từ 8 đến 72 ký tự.");
        }
        if (otp == null || !otp.matches("[0-9]{6}")) return false;

        Pending entry = pendingCodes.get(email);
        if (entry == null) return false;
        synchronized (entry) {
            if (pendingCodes.get(email) != entry || System.currentTimeMillis() > entry.expiresAt) {
                pendingCodes.remove(email, entry);
                return false;
            }
            if (entry.failedAttempts >= MAX_OTP_ATTEMPTS) {
                pendingCodes.remove(email, entry);
                return false;
            }
            if (!MessageDigest.isEqual(entry.digest, hashOtp(email, otp, entry.salt))) {
                entry.failedAttempts++;
                if (entry.failedAttempts >= MAX_OTP_ATTEMPTS) pendingCodes.remove(email, entry);
                return false;
            }
            String encoded = PasswordUtil.encode(newPassword);
            boolean updated = userDAO.updatePasswordByEmail(email, encoded);
            if (updated) {
                pendingCodes.remove(email, entry); // Mã chỉ được dùng một lần.
                Arrays.fill(entry.digest, (byte) 0);
            }
            return updated;
        }
    }

    private static byte[] hashOtp(String email, String code, byte[] salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            md.update(email.getBytes(StandardCharsets.UTF_8));
            md.update((byte) ':');
            return md.digest(code.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) {
            throw new IllegalStateException("Không khởi tạo được SHA-256", e);
        }
    }

    private static boolean smtpConfigured() {
        return !env("SMTP_HOST").isBlank() && !env("SMTP_USER").isBlank()
                && !env("SMTP_PASSWORD").isBlank() && !env("SMTP_FROM").isBlank();
    }

    private static String env(String key) {
        String value = System.getenv(key);
        return value == null ? "" : value.trim();
    }

    void sendOtpMail(String recipient, String code) throws Exception {
        String host = env("SMTP_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("SMTP_PORT", "587"));
        String user = env("SMTP_USER");
        String password = env("SMTP_PASSWORD");
        String from = env("SMTP_FROM");

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", Integer.toString(port));
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        if (port == 465) {
            props.put("mail.smtp.ssl.enable", "true");
        } else {
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
        }

        Session session = Session.getInstance(props);
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(from));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipient, true));
        message.setSubject("An Nhiên - Xác minh đặt lại mật khẩu", "UTF-8");
        message.setText("Mã xác minh của bạn: " + code + "\nMã có hiệu lực 10 phút và chỉ dùng một lần."
                + "\nNếu bạn không yêu cầu, hãy bỏ qua email này.", "UTF-8");
        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(host, port, user, password);
            transport.sendMessage(message, message.getAllRecipients());
        } finally {
            transport.close();
        }
    }
}
