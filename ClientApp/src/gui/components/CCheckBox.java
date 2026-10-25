package gui.components;

import javax.swing.*;
import java.awt.*;

public class CCheckBox implements Icon {
    private boolean selected;

    public CCheckBox(boolean selected) {
        this.selected = selected;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        if (selected) {
            g2.setColor(new Color(206, 93, 52));
            g2.fillRoundRect(x, y + 2, 16, 16, 4, 4);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2));
            g2.drawLine(x + 4, y + 10, x + 7, y + 13);
            g2.drawLine(x + 7, y + 13, x + 12, y + 6);
        } else {
            g2.setColor(new Color(229, 231, 235));
            g2.drawRoundRect(x, y + 2, 15, 15, 4, 4);
        }
    }

    @Override
    public int getIconWidth() { return 16; }

    @Override
    public int getIconHeight() { return 18; }
}