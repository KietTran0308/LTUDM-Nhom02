package components;

import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.RoundRectangle2D;

public class CDialog extends JDialog {

    public static final Color DARK_TEXT = new Color(39, 54, 44);
    public static final Color MUTED_TEXT = new Color(107, 117, 110);
    public static final Color LINE_COLOR = new Color(226, 231, 225);
    private static final int CORNER_RADIUS = 28;

    private final JFrame ownerFrame;
    private final JPanel bodyWrapper;
    private final JPanel footerPanel;
    private int minWidth = 580;
    private int minHeight = 0;

    public CDialog(JFrame owner, String brandSub, String title, String subtitle) {
        super(owner, title, true);
        this.ownerFrame = owner;

        setUndecorated(true);
        setResizable(false);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                        CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }

            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setClip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                        CORNER_RADIUS, CORNER_RADIUS));
                super.paintChildren(g2);
                g2.setClip(null);
                g2.setColor(LINE_COLOR);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5,
                        getWidth() - 1.0, getHeight() - 1.0, CORNER_RADIUS, CORNER_RADIUS));
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder());

        // --- HEADER ---
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Utils.FOREST_BG);
        header.setBorder(new EmptyBorder(22, 28, 22, 24));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        if (brandSub != null && !brandSub.isBlank()) {
            JLabel lblBrand = new JLabel(brandSub);
            lblBrand.setFont(Utils.getCustomFont("Inter", Font.BOLD, 11));
            lblBrand.setForeground(new Color(201, 222, 207));
            titleBox.add(lblBrand);
            titleBox.add(Box.createVerticalStrut(8));
        }

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 21));
        lblTitle.setForeground(Color.WHITE);
        titleBox.add(lblTitle);

        if (subtitle != null && !subtitle.isBlank()) {
            titleBox.add(Box.createVerticalStrut(5));
            JLabel lblSub = new JLabel(subtitle);
            lblSub.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
            lblSub.setForeground(new Color(224, 235, 226));
            titleBox.add(lblSub);
        }

        header.add(titleBox, BorderLayout.CENTER);
        content.add(header, BorderLayout.NORTH);

        // --- BODY ---
        bodyWrapper = new JPanel(new BorderLayout());
        bodyWrapper.setBackground(Color.WHITE);
        bodyWrapper.setBorder(new EmptyBorder(22, 28, 18, 28));
        content.add(bodyWrapper, BorderLayout.CENTER);

        // --- FOOTER ---
        footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 16));
        footerPanel.setBackground(new Color(249, 250, 249));
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE_COLOR),
                new EmptyBorder(0, 20, 0, 20)));
        content.add(footerPanel, BorderLayout.SOUTH);

        getRootPane().setOpaque(false);
        getLayeredPane().setOpaque(false);
        setContentPane(content);

        // Mặc định cho phép bấm ESC để đóng
        getRootPane().registerKeyboardAction(e -> {
            if (getDefaultCloseOperation() != JDialog.DO_NOTHING_ON_CLOSE) {
                dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Đặt nội dung chính vào phần thân của dialog */
    public CDialog setBody(JComponent body) {
        bodyWrapper.removeAll();
        bodyWrapper.add(body, BorderLayout.CENTER);
        return this;
    }

    /** Thêm các nút ở footer (nút cuối cùng hoặc nút chỉ định sẽ là DefaultButton) */
    public CDialog setFooterButtons(JButton defaultButton, JButton... buttons) {
        footerPanel.removeAll();
        for (JButton btn : buttons) {
            footerPanel.add(btn);
        }
        if (defaultButton != null) {
            getRootPane().setDefaultButton(defaultButton);
        }
        return this;
    }

    /** Thiết lập kích thước tối thiểu cho dialog */
    public CDialog setMinSize(int minWidth, int minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        return this;
    }

    /** Tạo nhanh nút Hủy/Quay lại nền trắng viền mảnh đồng bộ với CDialog */
    public static CButton createSecondaryButton(String text, int width, int height) {
        CButton btn = new CButton(text, width, height);
        btn.setForeground(DARK_TEXT);
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new LineBorder(LINE_COLOR, 1, true));
        return btn;
    }

    /** Tạo nhanh 1 dòng thông tin "Tên trường : Giá trị" dùng chung cho StaffGUI & PosGUI */
    public static JPanel createInfoRow(String field, String value) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);

        JLabel name = new JLabel(field);
        name.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
        name.setForeground(MUTED_TEXT);

        JLabel text = new JLabel(value == null ? "—" : ": " + value);
        text.setFont(Utils.getCustomFont("Inter", Font.BOLD, 18));
        text.setForeground(DARK_TEXT);

        row.add(name, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    /** Hiển thị dialog dạng modal, tự động phủ mờ JFrame cha và khôi phục khi đóng */
    public void showModal() {
        pack();
        setSize(Math.max(minWidth, getWidth()), Math.max(minHeight, getHeight()));
        setLocationRelativeTo(ownerFrame);

        try {
            setBackground(new Color(0, 0, 0, 0));
            setShape(new RoundRectangle2D.Double(0, 0,
                    getWidth(), getHeight(), CORNER_RADIUS, CORNER_RADIUS));
        } catch (UnsupportedOperationException ex) {
            System.err.println("[CDialog] Hệ điều hành không hỗ trợ bo góc cửa sổ: " + ex.getMessage());
        }

        if (ownerFrame == null) {
            setVisible(true);
            return;
        }

        Component oldGlassPane = ownerFrame.getGlassPane();
        boolean oldGlassPaneVisible = oldGlassPane.isVisible();
        JPanel dimOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(45, 45, 45, 155));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        dimOverlay.setOpaque(false);

        try {
            ownerFrame.setGlassPane(dimOverlay);
            dimOverlay.setVisible(true);
            dimOverlay.repaint();
            setVisible(true); // Chặn luồng cho đến khi dialog.dispose()
        } finally {
            dispose();
            dimOverlay.setVisible(false);
            ownerFrame.setGlassPane(oldGlassPane);
            oldGlassPane.setVisible(oldGlassPaneVisible);
        }
    }
}