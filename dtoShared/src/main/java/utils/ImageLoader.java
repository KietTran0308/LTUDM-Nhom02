
package utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ImageLoader {

    private ImageLoader() {}

    public static BufferedImage readImage(String source) {
        if (source == null || source.isBlank()) return null;

        String path = source.trim();

        try {
            if (path.startsWith("http://") || path.startsWith("https://")) {
                URLConnection conn = new URL(path).openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(4000);

                try (InputStream input = conn.getInputStream()) {
                    return ImageIO.read(input);
                }
            }

            if (path.startsWith("file:")) {
                return ImageIO.read(new URL(path));
            }

            Path filePath = Paths.get(path);

            if (Files.isRegularFile(filePath)) {
                return ImageIO.read(filePath.toFile());
            }

        } catch (IOException | IllegalArgumentException e) {
            System.err.println(
                    "[ImageLoader] Không đọc được "
                            + path + ": " + e.getMessage()
            );
        }

        String resourcePath = path.startsWith("/") ? path : "/" + path;

        String[] extensions = resourcePath.matches("(?i).*\\.(png|jpg|jpeg|gif)$") ? new String[]{""} : new String[]{".png", ".jpg", ".jpeg", ".gif"};

        for (String ext : extensions) {
            try (InputStream input = ImageLoader.class.getResourceAsStream(resourcePath + ext)) {
                if (input == null) continue;
                BufferedImage image = ImageIO.read(input);
                if (image != null) return image;
            } catch (IOException e) {
                System.err.println("[ImageLoader] Lỗi classpath: " + resourcePath + ext
                );
            }
        }

        System.err.println(
                "[ImageLoader] Không tìm thấy ảnh: " + path
        );

        return null;
    }
}
