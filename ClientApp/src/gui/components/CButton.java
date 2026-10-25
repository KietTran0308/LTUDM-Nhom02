package gui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class CButton extends JButton {

    private boolean isHover = false;

    private static final Dimension DEFAULT_SIZE = new Dimension(420, 50);
    private static final Font DEFAULT_FONT = new Font("Inter", Font.BOLD, 15);

    public CButton(String text) {
        this(text, DEFAULT_SIZE.width, DEFAULT_SIZE.height, DEFAULT_FONT);
    }

    public CButton(String text, int width, int height){
        this(text, width, height, DEFAULT_FONT);
    }

    public CButton(String text, int width, int height, Font font) {
        super(text);
        setOpaque(false);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        setPreferredSize(new Dimension(width, height));
        setMaximumSize(new Dimension(width, height));
        setFont(font);

        addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { isHover = true; repaint(); }
            public void mouseExited(MouseEvent e) { isHover = false; repaint(); }
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isHover ? new Color(181, 79, 44) : new Color(206, 93, 52));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
        super.paintComponent(g);
    }
}