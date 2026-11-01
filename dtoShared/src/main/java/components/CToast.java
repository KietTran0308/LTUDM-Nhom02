package components;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.List;

public final class CToast extends JPanel {
    public static final int INFO = 0;
    public static final int SUCCESS = 1;
    public static final int WARNING = 2;
    public static final int ERROR = 3;

    private static final int TOAST_WIDTH = 390;
    private static final int RIGHT_MARGIN = 22;
    private static final int TOP_MARGIN = 22;
    private static final int GAP = 10;
    private static final int MAX_VISIBLE = 4;
    private static final int DURATION_MS = 4200;
    private static final String ROOT_KEY = CToast.class.getName() + ".activeToasts";
    private static final String LISTENER_KEY = CToast.class.getName() + ".resizeListener";

    private final Color accent;
    private Timer dismissTimer;
    private JRootPane root;

    private CToast(String title, String message, int kind) {
        this.accent = accentFor(kind);
        setOpaque(false);
        setLayout(new BorderLayout(12, 0));
        setBorder(new EmptyBorder(15, 17, 17, 17));
        setFocusable(false);

        JLabel icon = new JLabel(symbolFor(kind), SwingConstants.CENTER);
        icon.setFont(new Font("Dialog", Font.BOLD, 22));
        icon.setForeground(accent);
        icon.setPreferredSize(new Dimension(30, 32));
        add(icon, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new BorderLayout(0, 5));
        textPanel.setOpaque(false);

        JLabel heading = new JLabel(title == null || title.isBlank() ? defaultTitle(kind) : title);
        heading.setFont(new Font("SansSerif", Font.BOLD, 14));
        heading.setForeground(new Color(34, 46, 40));
        textPanel.add(heading, BorderLayout.NORTH);

        JTextArea body = new JTextArea(message == null ? "" : message);
        body.setFont(new Font("SansSerif", Font.PLAIN, 13));
        body.setForeground(new Color(86, 95, 89));
        body.setLineWrap(true);
        body.setWrapStyleWord(true);
        body.setEditable(false);
        body.setFocusable(false);
        body.setOpaque(false);
        body.setBorder(null);
        // Layout gom 390px, tru phan icon/nut dong/le => khoang 270px cho noi dung.
        body.setSize(new Dimension(270, Short.MAX_VALUE));
        int bodyHeight = Math.max(20, Math.min(114, body.getPreferredSize().height));
        body.setPreferredSize(new Dimension(270, bodyHeight));
        textPanel.add(body, BorderLayout.CENTER);
        add(textPanel, BorderLayout.CENTER);

        JButton dismiss = new JButton("×");
        dismiss.setFont(new Font("Dialog", Font.BOLD, 19));
        dismiss.setForeground(new Color(136, 144, 139));
        dismiss.setOpaque(false);
        dismiss.setContentAreaFilled(false);
        dismiss.setBorderPainted(false);
        dismiss.setFocusPainted(false);
        dismiss.setBorder(new EmptyBorder(0, 2, 0, 2));
        dismiss.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        dismiss.setPreferredSize(new Dimension(23, 23));
        dismiss.addActionListener(e -> hideToast());
        add(dismiss, BorderLayout.EAST);

        setPreferredSize(new Dimension(TOAST_WIDTH, Math.max(84, bodyHeight + 62)));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        g2.setColor(new Color(0, 0, 0, 35));
        g2.fillRoundRect(4, 5, w - 5, h - 6, 18, 18);
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(0, 0, w - 6, h - 7, 17, 17);
        g2.setColor(accent);
        g2.fillRoundRect(0, 0, 5, h - 7, 8, 8);
        g2.dispose();
        super.paintComponent(g);
    }

    public static void info(Component owner, String message) {
        show(owner, message, "Thông báo", INFO);
    }

    public static void success(Component owner, String message) {
        show(owner, message, "Thành công", SUCCESS);
    }

    public static void warning(Component owner, String message) {
        show(owner, message, "Lưu ý", WARNING);
    }

    public static void error(Component owner, String message) {
        show(owner, message, "Có lỗi xảy ra", ERROR);
    }

    public static void show(Component owner, String message) {
        info(owner, message);
    }

    public static void show(Component owner, String message, String title, int kind) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(owner, message, title, kind));
            return;
        }
        if (owner == null) return;
        JRootPane root = SwingUtilities.getRootPane(owner);
        if (root == null) return;
        JLayeredPane layer = root.getLayeredPane();

        @SuppressWarnings("unchecked")
        List<CToast> active = (List<CToast>) root.getClientProperty(ROOT_KEY);
        if (active == null) {
            active = new ArrayList<>();
            root.putClientProperty(ROOT_KEY, active);
        }
        final List<CToast> list = active;

        if (root.getClientProperty(LISTENER_KEY) == null) {
            ComponentAdapter listener = new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    reposition(root);
                }
            };
            layer.addComponentListener(listener);
            root.putClientProperty(LISTENER_KEY, listener);
        }

        while (list.size() >= MAX_VISIBLE) {
            list.get(0).hideToast();
        }

        CToast toast = new CToast(title, message, kind);
        toast.root = root;
        list.add(toast);
        layer.add(toast, JLayeredPane.POPUP_LAYER);
        reposition(root);

        toast.dismissTimer = new Timer(DURATION_MS, e -> toast.hideToast());
        toast.dismissTimer.setRepeats(false);
        toast.dismissTimer.start();
    }

    private static void reposition(JRootPane root) {
        @SuppressWarnings("unchecked")
        List<CToast> active = (List<CToast>) root.getClientProperty(ROOT_KEY);
        if (active == null) return;
        JLayeredPane layer = root.getLayeredPane();
        int availableWidth = layer.getWidth() > 0 ? layer.getWidth() : root.getWidth();
        if (availableWidth <= 0) return;

        int y = TOP_MARGIN;
        for (CToast toast : active) {
            Dimension pref = toast.getPreferredSize();
            int width = Math.min(pref.width, Math.max(180, availableWidth - 2 * RIGHT_MARGIN));
            int x = Math.max(8, availableWidth - RIGHT_MARGIN - width);
            toast.setBounds(x, y, width, pref.height);
            y += pref.height + GAP;
        }
        layer.repaint();
    }

    private void hideToast() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::hideToast);
            return;
        }
        if (dismissTimer != null) dismissTimer.stop();
        if (root == null) return;
        JRootPane oldRoot = root;
        root = null;
        @SuppressWarnings("unchecked")
        List<CToast> active = (List<CToast>) oldRoot.getClientProperty(ROOT_KEY);
        if (active != null) active.remove(this);
        oldRoot.getLayeredPane().remove(this);
        reposition(oldRoot);
    }

    private static Color accentFor(int kind) {
        switch (kind) {
            case SUCCESS: return new Color(37, 135, 89);
            case WARNING: return new Color(206, 117, 49);
            case ERROR: return new Color(198, 68, 68);
            default: return new Color(60, 121, 169);
        }
    }

    private static String symbolFor(int kind) {
        switch (kind) {
            case SUCCESS: return "✓";
            case WARNING: return "!";
            case ERROR: return "×";
            default: return "i";
        }
    }

    private static String defaultTitle(int kind) {
        switch (kind) {
            case SUCCESS: return "Thành công";
            case WARNING: return "Lưu ý";
            case ERROR: return "Có lỗi xảy ra";
            default: return "Thông báo";
        }
    }
}
