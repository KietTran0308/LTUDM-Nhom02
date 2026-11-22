package utils;

import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

public class Utils {

    public static final Color FOREST_BG = new Color(32, 43, 37);
    public static final Color FOREST_CIRCLE_1 = new Color(29, 41, 34);
    public static final Color FOREST_CIRCLE_2 = new Color(33, 45, 38);
    public static final Color FOREST_DARK_LOG = new Color(20, 29, 24);
    public static final Color ACTIVE_COLOR = new Color(202, 227, 207);
    public static final Color EMPTY_COLOR = new Color(244, 231, 224);
    public static final Color SELECT_COLOR = new Color(35, 101, 66);
    public static final Color TERRACOTTA = new Color(206, 93, 52);
    public static final Color GOLD_ACCENT = new Color(201, 142, 84);
    public static final Color CREAM_BG = new Color(247, 248, 246);
    public static final Color TEXT_DARK = new Color(23, 31, 26);
    public static final Color TEXT_MUTED = new Color(140, 150, 145);
    public static final Color SAFE_GREEN = new Color(38, 92, 62);
    public static final Color DANGER_RED = new Color(185, 45, 45);
    public static final Color BORDER_SOFT = new Color(232, 235, 232);
    public static final Color HOVER_ROW = new Color(246, 247, 244);
    public static final Color SELECT_BG = new Color(253, 237, 232);
    public static final Color NEUTRAL_BTN = new Color(238, 241, 238);

    public static Font getCustomFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss  |  dd/MM/yyyy");

    public static JLabel createClockLabel() {
        JLabel lblClock = new JLabel(LocalDateTime.now().format(CLOCK_FORMAT), SwingConstants.RIGHT);
        lblClock.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblClock.setForeground(Color.WHITE);
        lblClock.setPreferredSize(new Dimension(220, 42));
        lblClock.setBorder(new EmptyBorder(0, 0, 0, 6));

        Timer timer = new Timer(1000, e ->
                lblClock.setText(LocalDateTime.now().format(CLOCK_FORMAT))
        );

        lblClock.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                if (!timer.isRunning()) timer.start();
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                timer.stop();
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {}
        });

        return lblClock;
    }
}