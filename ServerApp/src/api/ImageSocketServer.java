package api;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ImageSocketServer {

    public static void start(int port) {
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(port)) {
                while (true) {
                    Socket socket = server.accept();
                    new Thread(() -> handleClient(socket)).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleClient(Socket socket) {
        try (DataInputStream dis = new DataInputStream(socket.getInputStream());
             DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

            // Đọc tên file ảnh client gửi lên
            String imageName = dis.readUTF();

            // Mở file ảnh từ thư mục tài nguyên hoặc ổ đĩa
            InputStream in = ImageSocketServer.class.getResourceAsStream("/img/foods/" + imageName);
            if (in == null) {
                in = ImageSocketServer.class.getResourceAsStream("/gui/img/foods/" + imageName);
            }

            if (in == null) {
                dos.writeLong(0); // Báo không tìm thấy ảnh
                return;
            }

            byte[] fileBytes = in.readAllBytes();
            dos.writeLong(fileBytes.length); // Gửi kích thước file trước

            // Truyền dữ liệu nhị phân theo khối 8KB (chunked binary)
            int offset = 0;
            while (offset < fileBytes.length) {
                int length = Math.min(8192, fileBytes.length - offset);
                dos.write(fileBytes, offset, length);
                offset += length;
            }
            dos.flush();
            in.close();

        } catch (IOException e) {
            // Lỗi kết nối ngầm
        }
    }
}