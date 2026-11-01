package components;

import javax.swing.JScrollBar;

public class CScrollBar extends JScrollBar {

    public CScrollBar() {
        super(JScrollBar.VERTICAL);
        setUnitIncrement(24);
    }
}