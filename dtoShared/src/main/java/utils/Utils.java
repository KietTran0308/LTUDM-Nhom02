package utils;

import java.awt.*;
import javax.swing.ImageIcon;
import java.net.URL;

public class Utils {

    public static final Color FOREST_BG = new Color(32, 43, 37);
    public static final Color FOREST_CIRCLE_1 = new Color(29, 41, 34);
    public static final Color FOREST_CIRCLE_2 = new Color(33, 45, 38);
    public static final Color FOREST_DARK_LOG = new Color(20, 29, 24);
    public static final Color TERRACOTTA = new Color(206, 93, 52);
    public static final Color GOLD_ACCENT = new Color(201, 142, 84);
    public static final Color CREAM_BG = new Color(247, 248, 246);
    public static final Color TEXT_DARK = new Color(23, 31, 26);
    public static final Color TEXT_MUTED = new Color(140, 150, 145);
    public static final Color SAFE_GREEN = new Color(38, 92, 62);
    public static final Color DANGER_RED = new Color(185, 45, 45);

    public static Font getCustomFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    public static ImageIcon getScaledIcon(String path, int width, int height) {
        if (path == null || path.isBlank()) return null;

        String resourcePath = path.startsWith("/") ? path : "/" + path;
        URL url = Utils.class.getResource(resourcePath);
        if (url == null) {
            System.err.println("Không tìm thấy ảnh: " + resourcePath);
            return null;
        }

        Image image = new ImageIcon(url).getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
        return new ImageIcon(image);
    }
}