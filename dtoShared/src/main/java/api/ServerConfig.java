package api;

public final class ServerConfig {

    private static final String DEFAULT_HOST = "103.211.207.183";
    private static final int DEFAULT_PORT = 8888;
    public static final String SERVER_RSA_SHA256 = "C61F903F299F92F9506940F632D40AD3255C31345970FAF0389D45010B25A604";

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

        if (value == null || value.isBlank()) {
            value = System.getenv("SERVER_PORT");
        }

        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }

        try {
            int port = Integer.parseInt(value.trim());

            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }

        throw new IllegalArgumentException(
                "SERVER_PORT khong hop le: " + value
        );
    }
}
