package gui.components;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;

public class CPasswordField extends JPasswordField {
    private String placeholder;
    private boolean showPassword = false;

    private ImageIcon loadAndResizeIcon(String path, int width, int height) {
        java.net.URL imgURL = getClass().getResource(path);
        if (imgURL != null) {
            ImageIcon originalIcon = new ImageIcon(imgURL);
            Image img = originalIcon.getImage();
            Image resizedImage = img.getScaledInstance(width, height, Image.SCALE_SMOOTH);
            return new ImageIcon(resizedImage);
        } else {
            System.err.println("Lỗi: Không tìm thấy ảnh tại " + path);
            return null;
        }
    }

    private ImageIcon iconShow = loadAndResizeIcon("/img/eye.png", 20, 20);
    private ImageIcon iconHide = loadAndResizeIcon("/img/eye_off.png", 20, 20);

    private static final Dimension DEFAULT_SIZE = new Dimension(420, 45);
    private static final Font DEFAULT_FONT = new Font("Inter", Font.PLAIN, 20);

    public CPasswordField(String placeholder) {
        this(placeholder, DEFAULT_SIZE.width, DEFAULT_SIZE.height, DEFAULT_FONT);
    }

    public CPasswordField(String placeholder, int width, int height) {
        this(placeholder, width, height, DEFAULT_FONT);
    }

    public CPasswordField(String placeholder, int width, int height, Font font) {
        this.placeholder = placeholder;

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 45));
        setEchoChar('•');

        setPreferredSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
        setFont(font);

        // Bắt sự kiện focus
        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { repaint(); }
            public void focusLost(FocusEvent e) { repaint(); }
        });

        // Lắng nghe sự thay đổi dữ liệu để đổi màu viền realtime
        getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { repaint(); }
            public void removeUpdate(DocumentEvent e) { repaint(); }
            public void changedUpdate(DocumentEvent e) { repaint(); }
        });

        // Bắt sự kiện click chuột vào icon
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                int iconWidth = (iconShow != null && iconShow.getIconWidth() > 0) ? iconShow.getIconWidth() : 24;
                int clickX = getWidth() - iconWidth - 15;

                if (e.getX() >= clickX) {
                    showPassword = !showPassword;
                    setEchoChar(showPassword ? (char) 0 : '•');
                    repaint();
                }
            }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        super.paintComponent(g);

        // Vẽ placeholder
        if (getPassword().length == 0 && !hasFocus()) {
            g2.setColor(new Color(156, 163, 175));
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }

        ImageIcon currentIcon = showPassword ? iconShow : iconHide;
        if (currentIcon != null && currentIcon.getImageLoadStatus() == MediaTracker.COMPLETE) {
            int iconX = getWidth() - currentIcon.getIconWidth() - 15;
            int iconY = (getHeight() - currentIcon.getIconHeight()) / 2;
            currentIcon.paintIcon(this, g2, iconX, iconY);
        } else {
            g2.setColor(Color.LIGHT_GRAY);
            g2.fillOval(getWidth() - 35, getHeight() / 2 - 10, 20, 20);
        }
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Cập nhật logic: Nếu đang focus HOẶC có dữ liệu thì viền màu cam
        boolean hasData = getPassword().length > 0;
        if (isFocusOwner() || hasData) {
            g2.setColor(new Color(206, 93, 52));
        } else {
            g2.setColor(new Color(97, 97, 97));
        }

        g2.drawRoundRect(1, 0, getWidth() - 2, getHeight() - 1, 10, 10);
    }
}