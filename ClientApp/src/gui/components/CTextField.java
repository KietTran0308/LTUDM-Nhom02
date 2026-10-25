package gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CTextField extends JTextField {
    private String placeholder;

    // Thuộc tính mặc định
    private static final Dimension DEFAULT_SIZE = new Dimension(420, 45);
    private static final Font DEFAULT_FONT = new Font("Inter", Font.PLAIN, 20);

    public CTextField(String placeholder) {
        this(placeholder, DEFAULT_SIZE.width, DEFAULT_SIZE.height, DEFAULT_FONT);
    }

    public CTextField(String placeholder, int width, int height){
        this.placeholder = placeholder;

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        setPreferredSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
        setFont(DEFAULT_FONT);

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { repaint(); }
            public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    public CTextField(String placeholder, int width, int height, Font font) {
        this.placeholder = placeholder;

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        setPreferredSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
        setFont(font);

        addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { repaint(); }
            public void focusLost(FocusEvent e) { repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
        super.paintComponent(g);

        if (getText().isEmpty()) {
            g2.setColor(new Color(156, 163, 175)); // Màu xám nhạt
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(placeholder, getInsets().left, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
        }
    }

    @Override
    protected void paintBorder(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean hasData = !(getText().isEmpty());

        if (isFocusOwner() || hasData) g2.setColor(new Color(206, 93, 52));
        else g2.setColor(new Color(97, 97, 97));
        g2.drawRoundRect(1, 0, getWidth() - 2, getHeight() - 1, 10, 10);
    }
}