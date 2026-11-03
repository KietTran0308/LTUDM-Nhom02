package components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;

public class CScrollBar extends JScrollBar {
    private static final int BAR_WIDTH = 14;
    private static final Color TRACK_COLOR = new Color(235, 237, 233);
    private static final Color THUMB_COLOR = new Color(206, 93, 52);
    private static final Color THUMB_DRAG_COLOR = THUMB_COLOR.darker();

    public CScrollBar() {
        super(JScrollBar.VERTICAL);

        setPreferredSize(new Dimension(BAR_WIDTH, 0));
        setUnitIncrement(24);
        setBlockIncrement(160);
        setBorder(BorderFactory.createEmptyBorder());
        setOpaque(false);

        setUI(new BasicScrollBarUI() {
            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(TRACK_COLOR);
                    g2.fillRoundRect(bounds.x + 3, bounds.y,
                            Math.max(0, bounds.width - 6), bounds.height, 10, 10);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle bounds) {
                if (!scrollbar.isEnabled() || bounds.isEmpty()) return;

                Graphics2D g2 = (Graphics2D) g.create();
                try {
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isDragging ? THUMB_DRAG_COLOR : THUMB_COLOR);
                    g2.fillRoundRect(bounds.x + 3, bounds.y + 2,
                            Math.max(0, bounds.width - 6),
                            Math.max(0, bounds.height - 4), 10, 10);
                } finally {
                    g2.dispose();
                }
            }

            @Override
            protected Dimension getMinimumThumbSize() {
                return new Dimension(BAR_WIDTH, 30);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroSizeButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroSizeButton();
            }

            private JButton createZeroSizeButton() {
                JButton button = new JButton();
                Dimension zero = new Dimension(0, 0);
                button.setPreferredSize(zero);
                button.setMinimumSize(zero);
                button.setMaximumSize(zero);
                button.setBorder(null);
                button.setFocusable(false);
                return button;
            }
        });
    }
}
