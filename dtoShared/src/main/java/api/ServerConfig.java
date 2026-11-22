package api;

public final class ServerConfig {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;

    public static final String SERVER_RSA_SHA256 = "9715D33C6E236E0D1CC266C0634AF312479C95218F4EE1FB5577A89AB108A9B2";

    public static final String HOST = loadString("server.host", "SERVER_HOST", DEFAULT_HOST);
    public static final int PORT = loadPort("server.port", "SERVER_PORT", DEFAULT_PORT, "SERVER_PORT");

    private static final String DEFAULT_DB_URL =
            "jdbc:mysql://127.0.0.1:3306/annhien_datmon?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&characterEncoding=UTF-8";
    private static final String DEFAULT_DB_USER = "root";
    private static final String DEFAULT_DB_PASSWORD = "";

    public static final String DB_URL = loadString("db.url", "DB_URL", DEFAULT_DB_URL);
    public static final String DB_USER = loadString("db.user", "DB_USER", DEFAULT_DB_USER);
    // Riêng mật khẩu DB cho phép chuỗi rỗng "" nếu XAMPP không đặt pass
    public static final String DB_PASSWORD = loadRaw("db.password", "DB_PASSWORD", DEFAULT_DB_PASSWORD);

    // --- 3. CẤU HÌNH SMTP GỬI OTP QUÊN MẬT KHẨU ---
    private static final String DEFAULT_SMTP_HOST = "smtp.gmail.com";
    private static final int DEFAULT_SMTP_PORT = 587;
    private static final String DEFAULT_SMTP_USER = "annhienbep211@gmail.com";
    private static final String DEFAULT_SMTP_PASSWORD = "vocvkrsahuexwdzy";
    private static final String DEFAULT_SMTP_FROM = "annhienbep211@gmail.com";

    public static final String SMTP_HOST = loadString("smtp.host", "SMTP_HOST", DEFAULT_SMTP_HOST);
    public static final int SMTP_PORT = loadPort("smtp.port", "SMTP_PORT", DEFAULT_SMTP_PORT, "SMTP_PORT");
    public static final String SMTP_USER = loadString("smtp.user", "SMTP_USER", DEFAULT_SMTP_USER);
    public static final String SMTP_PASSWORD = loadString("smtp.password", "SMTP_PASSWORD", DEFAULT_SMTP_PASSWORD);
    public static final String SMTP_FROM = loadString("smtp.from", "SMTP_FROM", DEFAULT_SMTP_FROM);

    private ServerConfig() { }

    private static String readConfigValue(String sysProp, String envVar) {
        String value = System.getProperty(sysProp);
        if (value == null || value.isBlank()) value = System.getenv(envVar);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private static String loadString(String sysProp, String envVar, String defaultValue) {
        String val = readConfigValue(sysProp, envVar);
        return val != null ? val : defaultValue;
    }

    private static String loadRaw(String sysProp, String envVar, String defaultValue) {
        String value = System.getProperty(sysProp);
        if (value == null) value = System.getenv(envVar);
        return value != null ? value : defaultValue;
    }

    private static int loadPort(String sysProp, String envVar, int defaultPort, String label) {
        String value = readConfigValue(sysProp, envVar);
        if (value == null) return defaultPort;

        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) { }

        throw new IllegalArgumentException(label + " không hợp lệ: " + value);
    }
}