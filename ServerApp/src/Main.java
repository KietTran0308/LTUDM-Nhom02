import api.ImageSocketServer;
import api.SocketServer;

public class Main {

    public static void main(String[] args) {
        SocketServer server = new SocketServer(null);
        server.startServer();

        ImageSocketServer.start(8889);
        System.out.println(
                "Server đang hoạt động tại cổng 8888"
        );
    }
}