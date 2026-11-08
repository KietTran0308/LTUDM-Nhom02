package components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class CToggleButton extends JToggleButton {
    private static final Color ACTIVE_COLOR = new Color(206, 93, 52);
    private static final Color INACTIVE_COLOR = new Color(106, 118, 110);
    private static final int TAB_HEIGHT = 50;

    public CToggleButton(String text) {
        this(text, 220, false);
    }

    public CToggleButton(String text, boolean selected) {
        this(text, 220, selected);
    }

    public CToggleButton(String text, int width, boolean selected) {
        super(text, selected);
        setFont(new Font("SansSerif", Font.BOLD, 14));
        setPreferredSize(new Dimension(width, TAB_HEIGHT));
        setContentAreaFilled(false);
        setBorderPainted(true);
        setFocusPainted(false);
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.CENTER);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // ItemListener chạy khi ButtonGroup đổi tab, không cần GUI cập nhật style.
        addItemListener(e -> updateTabStyle());
        updateTabStyle();
    }

    private void updateTabStyle() {
        Color color = isSelected() ? ACTIVE_COLOR : INACTIVE_COLOR;
        setForeground(color);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0,
                        isSelected() ? ACTIVE_COLOR : Color.WHITE),
                new EmptyBorder(11, 12, 10, 12)));
    }
}
