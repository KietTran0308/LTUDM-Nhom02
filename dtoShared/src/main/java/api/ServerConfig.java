package api;

public final class ServerConfig {

    //103.211.207.183
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 8888;
    //RSA LOCAL (MÁY KIỆT): 099ACE050CEDB652FA53675B37BBE7694D76223BF79D13185C7DB9136E5C3698
    //RSA VPS: 9715D33C6E236E0D1CC266C0634AF312479C95218F4EE1FB5577A89AB108A9B2
    public static final String SERVER_RSA_SHA256 = "9715D33C6E236E0D1CC266C0634AF312479C95218F4EE1FB5577A89AB108A9B2";

    public static final String HOST = loadHost();
    public static final int PORT = loadPort();

    private ServerConfig() {
    }

    private static String loadHost() {
        String host = System.getProperty("server.host");

        if (host == null || host.isBlank()) {
            host = System.getenv("SERVER_HOST");
        }

        return (host == null || host.isBlank())
                ? DEFAULT_HOST
                : host.trim();
    }

    private static int loadPort() {
        String value = System.getProperty("server.port");

        if (value == null || value.isBlank()) value = System.getenv("SERVER_PORT");
        if (value == null || value.isBlank()) return DEFAULT_PORT;

        try {
            int port = Integer.parseInt(value.trim());
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) {
        }

        throw new IllegalArgumentException(
                "SERVER_PORT khong hop le: " + value
        );
    }
}
