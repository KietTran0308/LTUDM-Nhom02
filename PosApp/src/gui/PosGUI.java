package gui;

import api.ImageSocketClient;
import api.SocketClient;
import java.awt.image.BufferedImage;

import bus.PosBUS;
import components.CButton;
import components.CScrollBar;
import components.CToast;
import components.CToggleButton;
import dto.*;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

public final class PosGUI extends JFrame {
    // Màu sơ đồ bàn lấy từ StaffGUI
    private static final Color ACTIVE_COLOR = new Color(202, 227, 207);
    private static final Color EMPTY_COLOR = new Color(244, 231, 224);
    private static final Color SELECT_COLOR = new Color(35, 101, 66);
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.## 'VNĐ'",
            DecimalFormatSymbols.getInstance(Locale.forLanguageTag("vi-VN")));

    private final PosBUS pos;
    private final CardLayout workspaceLayout = new CardLayout();
    private final JPanel workspace = new JPanel(workspaceLayout);
    private final JPanel tableGridPanel = new JPanel(new GridLayout(0, 4, 14, 14));
    private final JPanel floorList = new JPanel();
    private final JLabel floorHeading = new JLabel("Đang tải các tầng...");
    private List<TableDTO> tableSnapshot = new ArrayList<>();
    private Integer selectedFloor;
    private final DefaultTableModel billItemsModel = new DefaultTableModel(
            new String[]{"Lượt gọi", "Tên món ăn", "Đơn giá", "Số lượng", "Thành tiền", "Trạng thái"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JLabel lblSelectedTableInfo = new JLabel();
    private final JLabel lblSessionInfo = new JLabel();
    private final JLabel lblSubtotal = valueLabel("0 VNĐ", 14, Utils.TEXT_DARK);
    private final JLabel lblDiscount = valueLabel("0 VNĐ", 14, new Color(46, 125, 50));
    private final JLabel lblTaxVAT = valueLabel("0 VNĐ", 14, Utils.TEXT_DARK);
    private final JLabel lblFinalTotal = valueLabel("0 VNĐ", 22, Utils.TERRACOTTA);
    private final JLabel lblStatus = new JLabel("POS trung tâm — sẵn sàng");
    private final ButtonGroup paymentMethods = new ButtonGroup();
    private final CToggleButton cashButton = new CToggleButton("Tiền mặt", 100, false);
    private final CToggleButton bankButton = new CToggleButton("Chuyển khoản ngân hàng", 205, false);
    private final CButton btnCreateCustomer = new CButton("Tạo tài khoản khách", 205, 42);

    private final CButton btnCheckout = new CButton("Xuất hóa đơn", 490, 52);
    private final CButton btnBack = new CButton("Quay lại sơ đồ", 160, 40);

    private TableDTO selectedTable;
    private OrderDTO billPreview;
    private boolean busy;
    private boolean refreshing;
    private boolean reviewing;
    private final Timer refreshTimer;
    private final JLabel lblClock = new JLabel("", SwingConstants.RIGHT);
    private static final java.time.format.DateTimeFormatter CLOCK_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss  |  dd/MM/yyyy");
    private Timer clockTimer;


    public static void showPosLogin() {
        JDialog dialog = new JDialog((Frame) null, "Đăng nhập POS — An Nhiên", true);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(20, 22, 20, 22));
        JPanel form = new JPanel(new GridLayout(2, 2, 12, 12));
        JTextField username = new JTextField(23);
        JPasswordField password = new JPasswordField(23);
        form.add(new JLabel("Email / SĐT nhân viên:"));
        form.add(username);
        form.add(new JLabel("Mật khẩu:"));
        form.add(password);
        JLabel feedback = new JLabel("Chỉ tài khoản ADMIN trên máy POS được cấp phép mới có quyền thu ngân.");
        feedback.setForeground(new Color(135, 72, 42));
        JButton submit = new JButton("Đăng nhập POS");
        JButton cancel = new JButton("Hủy");
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancel);
        footer.add(submit);
        cancel.addActionListener(e -> dialog.dispose());
        submit.addActionListener(e -> {
            char[] typed = password.getPassword();
            String account = username.getText().trim();
            if (account.isEmpty() || typed.length == 0) {
                java.util.Arrays.fill(typed, '\0');
                feedback.setText("Vui lòng nhập đủ tài khoản và mật khẩu.");
                return;
            }
            String secret = new String(typed);
            java.util.Arrays.fill(typed, '\0');
            password.setText("");
            submit.setEnabled(false);
            feedback.setText("Đang xác thực trên Server...");
            new SwingWorker<PosBUS, Void>() {
                @Override
                protected PosBUS doInBackground() {
                    return PosBUS.loginPos(account, secret);
                }

                @Override
                protected void done() {
                    submit.setEnabled(true);
                    try {
                        PosBUS pos = get();
                        if (!dialog.isDisplayable()) {
                            pos.close();
                            return;
                        }
                        dialog.dispose();
                        new PosGUI(pos).setVisible(true);
                    } catch (Exception ex) {
                        Throwable cause = ex instanceof ExecutionException ? ex.getCause() : ex;
                        feedback.setText(cause == null ? "Không đăng nhập được POS"
                                : "Lỗi: " + cause.getMessage());
                    }
                }
            }.execute();
        });
        content.add(form, BorderLayout.CENTER);
        content.add(feedback, BorderLayout.NORTH);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(submit);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(PosGUI::showPosLogin);
    }

    private PosGUI(PosBUS pos) {
        this.pos = Objects.requireNonNull(pos);
        setTitle("An Nhiên | POS thu ngân — " + pos.getOperator().getFullName());
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setSize(1360, 820);
        setMinimumSize(new Dimension(1040, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Không tắt ServerApp
        setLayout(new BorderLayout());

        initBrandHeader();
        initMainWorkspace();
        initStatusBar();
        updateButtons();

        refreshTimer = new Timer(6000, e -> refreshTables(false));
        refreshTimer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshTables(true);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                refreshTimer.stop();
                if (clockTimer != null) clockTimer.stop();
                pos.close();
            }
        });
    }

    /**
     * Phan header lay tu StaffGUI cu, duoc doi ten cho POS.
     */
    private void initBrandHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Utils.FOREST_CIRCLE_1);
                g2.fillOval(getWidth() / 2 - 150, -180, 380, 380);
                g2.dispose();
            }
        };
        header.setBackground(Utils.FOREST_BG);
        header.setPreferredSize(new Dimension(1360, 78));
        header.setBorder(new EmptyBorder(14, 28, 14, 28));

        JPanel leftBrand = new JPanel();
        leftBrand.setLayout(new BoxLayout(leftBrand, BoxLayout.X_AXIS));
        leftBrand.setOpaque(false);
        JLabel lblIcon = createSocketLogo("things/logo.png", 40);
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 10));
        JPanel titleCol = new JPanel();
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleCol.setOpaque(false);
        JLabel brand = new JLabel("Nhà hàng An Nhiên  |  QUẢN LÝ BÀN & THU NGÂN");
        brand.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        brand.setForeground(Color.WHITE);

        titleCol.add(brand);
        titleCol.add(Box.createVerticalStrut(3));

        leftBrand.add(lblIcon);
        leftBrand.add(titleCol);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        actions.setOpaque(false);
        btnCreateCustomer.addActionListener(e -> showCreateCustomerDialog());
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setBackground(new Color(52, 66, 58));
        btnLogout.setFocusPainted(false);
        btnLogout.setPreferredSize(new Dimension(115, 42));
        btnLogout.setBorder(new LineBorder(new Color(80, 95, 86), 1, true));
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.addActionListener(e -> dispose());
        // Đồng hồ: giờ:phút:giây | ngày/tháng/năm, đặt trước nút Tạo tài khoản khách và Đăng xuất.
        lblClock.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblClock.setForeground(Color.WHITE);
        lblClock.setPreferredSize(new Dimension(220, 42));
        lblClock.setBorder(new EmptyBorder(0, 0, 0, 6));
        updateClock();
        clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.start();
        actions.add(lblClock);
        actions.add(btnCreateCustomer);
        actions.add(btnLogout);

        header.add(leftBrand, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void updateClock() {
        lblClock.setText(java.time.LocalDateTime.now().format(CLOCK_FORMAT));
    }

    private void showCreateCustomerDialog() {
        JDialog dialog = new JDialog(this, "Tạo tài khoản khách hàng", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JTextField nameField = new JTextField(23);
        JTextField phoneField = new JTextField(23);
        JTextField emailField = new JTextField(23);
        JPanel form = new JPanel(new GridLayout(3, 2, 12, 10));
        form.add(new JLabel("Họ và tên"));
        form.add(nameField);
        form.add(new JLabel("Số điện thoại"));
        form.add(phoneField);
        form.add(new JLabel("Email (tùy chọn)"));
        form.add(emailField);

        JLabel feedback = new JLabel("Mật khẩu ban đầu là số điện thoại của khách.");
        JButton submit = new JButton("Tạo tài khoản");
        JButton cancel = new JButton("Hủy");
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancel);
        footer.add(submit);
        cancel.addActionListener(e -> dialog.dispose());
        submit.addActionListener(e -> {
            if (!submit.isEnabled()) return;
            String name = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            String email = emailField.getText().trim();
            if (name.isEmpty() || name.length() > 150) {
                feedback.setText("Nhập họ tên, tối đa 150 ký tự.");
                return;
            }
            if (!phone.matches("(0|\\+84)\\d{9}")) {
                feedback.setText("Số điện thoại không hợp lệ. Ví dụ 0901234567.");
                return;
            }
            if (!email.isEmpty() && (email.length() > 255 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))) {
                feedback.setText("Email không hợp lệ.");
                return;
            }

            feedback.setText("Đang tạo tài khoản...");
            submit.setEnabled(false);
            cancel.setEnabled(false);
            nameField.setEditable(false);
            phoneField.setEditable(false);
            emailField.setEditable(false);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    pos.createCustomerAccount(name, phone, email);
                    return null;
                }

                @Override
                protected void done() {
                    if (!dialog.isDisplayable()) return;
                    submit.setEnabled(true);
                    cancel.setEnabled(true);
                    nameField.setEditable(true);
                    phoneField.setEditable(true);
                    emailField.setEditable(true);
                    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
                    try {
                        get();
                        dialog.dispose();
                        lblStatus.setText("Đã tạo tài khoản khách hàng");
                        CToast.success(PosGUI.this, "Đã tạo tài khoản cho " + name);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        feedback.setText("Thao tác bị gián đoạn.");
                    } catch (ExecutionException ex) {
                        feedback.setText("Kiểm tra thông tin và thử lại.");
                        JOptionPane.showMessageDialog(dialog, ex.getCause().getMessage(),
                                "Không tạo được tài khoản", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(new EmptyBorder(18, 22, 18, 22));
        content.add(feedback, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(submit);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);

        busy = true;
        updateButtons();
        try {
            dialog.setVisible(true);
        } finally {
            busy = false;
            updateButtons();
        }
    }

    private void initMainWorkspace() {
        workspace.setBackground(Utils.CREAM_BG);
        workspace.setBorder(new EmptyBorder(22, 25, 18, 25));
        workspace.add(createTableMap(), "map");
        workspace.add(createTableDetail(), "detail");
        add(workspace, BorderLayout.CENTER);
    }

    private JPanel createTableMap() {
        // Bố cục lấy từ StaffGUI: sidebar chọn tầng (trái) + lưới bàn của tầng (giữa)
        JPanel mapPanel = new JPanel(new BorderLayout(18, 0));
        mapPanel.setOpaque(false);
        mapPanel.add(menuFloor(), BorderLayout.WEST);

        JPanel tableArea = new JPanel(new BorderLayout(0, 12));
        tableArea.setBackground(Color.WHITE);
        tableArea.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel tableHeader = new JPanel(new GridLayout(0, 1, 0, 5));
        tableHeader.setOpaque(false);
        floorHeading.setFont(Utils.getCustomFont("Inter", Font.BOLD, 20));
        floorHeading.setForeground(Utils.FOREST_BG);
        tableHeader.add(floorHeading);
        tableArea.add(tableHeader, BorderLayout.NORTH);

        tableGridPanel.setBackground(Color.WHITE);
        tableGridPanel.setBorder(new EmptyBorder(6, 4, 12, 4));
        // Neo lưới lên đầu: 1-2 bàn không bị kéo cao theo toàn bộ vùng cuộn.
        JPanel tableAnchor = new JPanel(new BorderLayout());
        tableAnchor.setBackground(Color.WHITE);
        tableAnchor.add(tableGridPanel, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(tableAnchor);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBar(new CScrollBar());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        tableArea.add(scroll, BorderLayout.CENTER);
        mapPanel.add(tableArea, BorderLayout.CENTER);
        return mapPanel;
    }

    private JPanel menuFloor() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 14));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(new EmptyBorder(20, 12, 16, 12));
        sidebar.setPreferredSize(new Dimension(245, 0));
        JLabel label = new JLabel("CHỌN TẦNG");
        label.setFont(Utils.getCustomFont("Inter", Font.BOLD, 17));
        label.setForeground(Utils.FOREST_BG);
        sidebar.add(label, BorderLayout.NORTH);

        floorList.setLayout(new BoxLayout(floorList, BoxLayout.Y_AXIS));
        floorList.setBackground(Color.WHITE);
        JScrollPane floorsScroll = new JScrollPane(floorList);
        floorsScroll.setBorder(BorderFactory.createEmptyBorder());
        floorsScroll.getViewport().setBackground(Color.WHITE);
        floorsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        floorsScroll.setVerticalScrollBar(new CScrollBar());
        floorsScroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(floorsScroll, BorderLayout.CENTER);
        return sidebar;
    }

    private static String floorName(int floor) {
        return "Tầng " + floor;
    }

    private void selectFloor(int floor) {
        if (Objects.equals(selectedFloor, floor)) return;
        selectedFloor = floor;
        renderTableMap(tableSnapshot); // Chỉ render lại snapshot cục bộ, không gọi server.
    }

    private void renderFloorSidebar(List<TableDTO> tables, Set<Integer> floors) {
        floorList.removeAll();
        if (floors.isEmpty()) {
            JLabel empty = new JLabel("Chưa có tầng nào");
            empty.setBorder(new EmptyBorder(10, 7, 10, 7));
            floorList.add(empty);
        }
        for (int floor : floors) {
            int total = 0;
            int active = 0;
            for (TableDTO t : tables) {
                if (Objects.equals(t.getFloorNumber(), floor)) {
                    total++;
                    if (t.getActiveSessionId() != null) active++;
                }
            }
            final boolean picked = Objects.equals(selectedFloor, floor);

            JButton button = new JButton("<html><div style='text-align:left;'><b>" + floorName(floor)
                    + "</b><br/>" + total + " bàn  •  " + active + " đang phục vụ</div></html>");
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
            button.setForeground(picked ? Color.WHITE : new Color(37, 65, 49));
            button.setBackground(picked ? SELECT_COLOR : new Color(244, 247, 243));
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(picked ? SELECT_COLOR : new Color(224, 230, 223), 1, true),
                    new EmptyBorder(11, 14, 11, 8)));
            button.setPreferredSize(new Dimension(209, 65));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.addActionListener(e -> selectFloor(floor));
            floorList.add(button);
            floorList.add(Box.createVerticalStrut(9));
        }
        floorList.revalidate();
        floorList.repaint();
    }

    private static String safeHtml(String raw) {
        if (raw == null) return "Bàn";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private JPanel createTableDetail() {
        JPanel detail = new JPanel(new BorderLayout(0, 16));
        detail.setOpaque(false);
        JPanel heading = new JPanel(new BorderLayout(14, 0));
        heading.setOpaque(false);
        btnBack.addActionListener(e -> showTableMap());
        heading.add(btnBack, BorderLayout.WEST);
        lblSelectedTableInfo.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblSelectedTableInfo.setForeground(Utils.TEXT_DARK);
        heading.add(lblSelectedTableInfo, BorderLayout.CENTER);
        lblSessionInfo.setForeground(Utils.TEXT_MUTED);
        heading.add(lblSessionInfo, BorderLayout.EAST);
        detail.add(heading, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 22, 0));
        columns.setOpaque(false);
        JPanel items = createWhiteCard(20, 20, 20, 20);
        items.setLayout(new BorderLayout(0, 15));
        items.add(sectionTitle("Món đã gọi"), BorderLayout.NORTH);
        items.add(createBillScroll(), BorderLayout.CENTER);
        columns.add(items);

        JPanel checkout = createWhiteCard(20, 24, 20, 24);
        checkout.setLayout(new BorderLayout(0, 15));
        checkout.add(sectionTitle("Thanh toán"), BorderLayout.NORTH);

        JPanel summary = new JPanel(new GridLayout(0, 1, 0, 12));
        summary.setBackground(Utils.CREAM_BG);
        summary.setBorder(new EmptyBorder(16, 18, 16, 18));
        summary.add(amountRow("Tổng tiền món", lblSubtotal));
        summary.add(amountRow("Giảm giá", lblDiscount));
        summary.add(amountRow("Thuế GTGT (VAT 8%)", lblTaxVAT));
        summary.add(titleLabel("Phương thức thanh toán"));

        JPanel methods = new JPanel(new GridLayout(1, 2, 8, 0));
        methods.setOpaque(false);
        paymentMethods.add(cashButton);
        paymentMethods.add(bankButton);
        cashButton.addActionListener(e -> updateButtons());
        bankButton.addActionListener(e -> updateButtons());
        methods.add(cashButton);
        methods.add(bankButton);
        summary.add(methods);
        summary.add(amountRow("Tổng thanh toán", lblFinalTotal));
        JPanel payment = new JPanel(new BorderLayout(0, 16));
        payment.setOpaque(false);
        payment.add(summary, BorderLayout.NORTH);
        btnCheckout.addActionListener(e -> paySelectedTable());
        payment.add(btnCheckout, BorderLayout.SOUTH);
        checkout.add(payment, BorderLayout.CENTER);
        columns.add(checkout);
        detail.add(columns, BorderLayout.CENTER);
        return detail;
    }

    private JScrollPane createBillScroll() {
        JTable table = new JTable(billItemsModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getParent() instanceof JViewport viewport && viewport.getWidth() >= getMinimumSize().width;
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent event) {
                int row = rowAtPoint(event.getPoint());
                int column = columnAtPoint(event.getPoint());
                return row < 0 || column < 0 ? null : Objects.toString(getValueAt(row, column), "");
            }
        };
        table.setToolTipText("");
        table.setRowHeight(34);
        table.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(240, 240, 240));
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        header.setBackground(Utils.FOREST_BG);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 38));
        int[] widths = {90, 115, 80, 67, 90, 105};
        for (int i = 0; i < widths.length; i++) {
            var column = table.getColumnModel().getColumn(i);
            column.setMinWidth(widths[i]);
            column.setPreferredWidth(i == 1 ? 220 : widths[i]);
        }
        JScrollPane scroll = new JScrollPane(table);
        scroll.setVerticalScrollBar(new CScrollBar());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        label.setForeground(Utils.TEXT_DARK);
        return label;
    }

    private static JPanel amountRow(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(titleLabel(label), BorderLayout.WEST);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void initStatusBar() {
        lblStatus.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        lblStatus.setForeground(Utils.TEXT_MUTED);
        lblStatus.setBorder(new EmptyBorder(0, 28, 12, 28));
        add(lblStatus, BorderLayout.SOUTH);
    }

    private static JPanel createWhiteCard(int top, int left, int bottom, int right) {
        JPanel p = new JPanel();
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(228, 232, 228), 1, true),
                new EmptyBorder(top, left, bottom, right)));
        return p;
    }

    private static JLabel titleLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
        lbl.setForeground(Utils.TEXT_DARK);
        return lbl;
    }

    private static JLabel valueLabel(String value, int size, Color color) {
        JLabel lbl = new JLabel(value, SwingConstants.RIGHT);
        lbl.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, size));
        lbl.setForeground(color);
        return lbl;
    }

    private void updateButtons() {
        boolean hasTable = selectedTable != null;
        boolean active = hasTable && selectedTable.getActiveSessionId() != null;
        boolean available = !busy && !reviewing;
        boolean billReady = active && billPreview != null && billPreview.getCheckoutRevision() != null
                && Objects.equals(selectedTable.getActiveSessionId(), billPreview.getTableSessionId());
        btnBack.setEnabled(available);
        btnCreateCustomer.setEnabled(available);
        cashButton.setEnabled(available && billReady);
        bankButton.setEnabled(available && billReady);
        btnCheckout.setEnabled(available && billReady && selectedPaymentMethod() != null);
    }

    private PaymentMethod selectedPaymentMethod() {
        if (cashButton.isSelected()) return PaymentMethod.CASH;
        if (bankButton.isSelected()) return PaymentMethod.BANK_TRANSFER;
        return null;
    }

    private void renderTableMap(List<TableDTO> tables) {
        tableSnapshot = new ArrayList<>(tables);
        Set<Integer> floors = new TreeSet<>();
        for (TableDTO t : tables) floors.add(t.getFloorNumber());
        if (selectedFloor == null || !floors.contains(selectedFloor)) {
            selectedFloor = floors.isEmpty() ? null : floors.iterator().next();
        }

        renderFloorSidebar(tables, floors);
        List<TableDTO> visible = new ArrayList<>();
        for (TableDTO t : tables) {
            if (Objects.equals(t.getFloorNumber(), selectedFloor)) visible.add(t);
        }
        floorHeading.setText(selectedFloor == null ? "SƠ ĐỒ BÀN"
                : "SƠ ĐỒ BÀN · " + floorName(selectedFloor).toUpperCase());

        tableGridPanel.removeAll();
        if (tables.isEmpty()) {
            JLabel empty = new JLabel("Server chưa có dữ liệu bàn trong restaurant_tables.", SwingConstants.CENTER);
            empty.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
            tableGridPanel.add(empty);
        } else if (visible.isEmpty()) {
            JLabel empty = new JLabel("Tầng này hiện chưa có bàn.", SwingConstants.CENTER);
            empty.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
            tableGridPanel.add(empty);
        }
        for (TableDTO t : visible) {
            boolean active = t.getActiveSessionId() != null;
            JButton button = new JButton("<html><center><b>" + safeHtml(t.getTableName()) + "</b><br/>"
                    + "Sức chứa: " + t.getCapacity() + " người<br/>"
                    + (active ? "KHÁCH ĐANG DÙNG BỮA" : "BÀN TRỐNG") + "</center></html>");
            button.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
            button.setPreferredSize(new Dimension(195, 125));
            button.setBackground(active ? ACTIVE_COLOR : EMPTY_COLOR);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(new LineBorder(new Color(220, 224, 217), 1, true));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            // Khác StaffGUI: ở POS, bấm vào bàn sẽ mở màn hình chi tiết/thanh toán.
            button.addActionListener(e -> selectTable(t));
            tableGridPanel.add(button);
        }
        tableGridPanel.revalidate();
        tableGridPanel.repaint();
    }

    private void selectTable(TableDTO table) {
        if (busy || reviewing) return;
        // Bàn trống: hỏi xác nhận mở phiên ngay tại sơ đồ, không chuyển sang màn hình chi tiết.
        if (table.getActiveSessionId() == null) {
            confirmAndOpenTable(table);
            return;
        }
        selectedTable = table;
        paymentMethods.clearSelection();
        clearInvoicePanel();
        updateTableHeading();
        workspaceLayout.show(workspace, "detail");
        if (table.getActiveSessionId() != null) loadPreview();
    }

    private void showTableMap() {
        selectedTable = null;
        paymentMethods.clearSelection();
        clearInvoicePanel();
        lblStatus.setText("Chọn bàn để xem chi tiết");
        workspaceLayout.show(workspace, "map");
    }

    private void updateTableHeading() {
        lblSelectedTableInfo.setText(selectedTable.getTableName());
        Long sessionId = selectedTable.getActiveSessionId();
        lblSessionInfo.setText(sessionId == null ? "Bàn trống"
                : "Phiên #" + sessionId + " · Đang dùng bữa");
    }

    private void clearInvoicePanel() {
        billPreview = null;
        billItemsModel.setRowCount(0);
        lblSubtotal.setText("0 VNĐ");
        lblDiscount.setText("0 VNĐ");
        lblTaxVAT.setText("0 VNĐ");
        lblFinalTotal.setText("0 VNĐ");
        updateButtons();
    }

    private void renderMergedBillPreview(OrderDTO order) {
        clearInvoicePanel();
        if (order == null) {
            lblStatus.setText("Phiên đã mở, chưa có món để thanh toán");
            return;
        }
        if (!Objects.equals(order.getTableSessionId(), selectedTable.getActiveSessionId())) {
            lblStatus.setText("Phiên bàn đã thay đổi. Đang cập nhật lại.");
            refreshTables(false);
            return;
        }
        if (order.getCheckoutTax() == null || order.getCheckoutRevision() == null) {
            lblStatus.setText("Cần cập nhật ServerApp để xem và thanh toán hóa đơn.");
            return;
        }

        billPreview = order;
        for (OrderItemDTO item : order.getItems()) {
            String turn = Objects.equals(item.getOrderId(), order.getId())
                    ? "Đơn gốc (#" + item.getOrderId() + ")"
                    : "Gọi thêm (#" + item.getOrderId() + ")";
            billItemsModel.addRow(new Object[]{turn, item.getFoodsName(),
                    money(item.getUnitPrice()), item.getQuantity(), money(item.getLineTotal()),
                    itemStatusText(item.getStatus())});
        }
        lblSubtotal.setText(money(order.getSubtotal()));
        lblDiscount.setText(money(order.getDiscountAmount()));
        lblTaxVAT.setText(money(order.getCheckoutTax()));
        lblFinalTotal.setText(money(order.getCheckoutTotal()));
        updateButtons();
    }

    private static String itemStatusText(OrderStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case PENDING -> "Chờ bếp nhận";
            case RECEIVED -> "Bếp đã nhận";
            case COOKING -> "Đang chế biến";
            case COMPLETED -> "Nấu xong";
            case SERVED -> "Đã phục vụ";
            case CANCELLED -> "Đã hủy";
        };
    }

    private static String money(BigDecimal v) {
        return MONEY.format(v == null ? BigDecimal.ZERO : v);
    }

    private <T> void execute(String description, java.util.concurrent.Callable<T> task,
                             java.util.function.Consumer<T> onSuccess, Runnable onFailure) {
        if (busy || !isDisplayable()) return;
        busy = true;
        lblStatus.setText(description + "...");
        updateButtons();
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return task.call();
            }

            @Override
            protected void done() {
                busy = false;
                if (!isDisplayable()) return;
                try {
                    T result = get();
                    lblStatus.setText(description + " — hoàn tất");
                    onSuccess.accept(result);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException
                            && ex.getCause() != null ? ex.getCause() : ex;

                    String msg = cause.getMessage() == null
                            ? "Không thực hiện được thao tác"
                            : cause.getMessage();
                    lblStatus.setText(msg);
                    CToast.error(PosGUI.this, msg);
                    onFailure.run();
                } finally {
                    updateButtons();
                }
            }
        }.execute();
    }

    private void refreshTables(boolean notify) {
        if (busy || refreshing || reviewing || !isDisplayable()) return;
        refreshing = true;
        updateButtons();
        new SwingWorker<List<TableDTO>, Void>() {
            @Override
            protected List<TableDTO> doInBackground() {
                return pos.listTables();
            }

            @Override
            protected void done() {
                refreshing = false;
                if (!isDisplayable() || busy || reviewing) return;
                try {
                    List<TableDTO> tables = get();
                    renderTableMap(tables);
                    if (selectedTable == null) {
                        if (notify) lblStatus.setText("Chọn bàn để xem chi tiết");
                        return;
                    }

                    Long selectedId = selectedTable.getId();
                    Long previousSession = selectedTable.getActiveSessionId();
                    selectedTable = tables.stream()
                            .filter(t -> Objects.equals(t.getId(), selectedId)).findFirst().orElse(null);
                    if (selectedTable == null) {
                        showTableMap();
                        return;
                    }
                    if (!Objects.equals(previousSession, selectedTable.getActiveSessionId())) {
                        paymentMethods.clearSelection();
                        clearInvoicePanel();
                    }
                    updateTableHeading();
                    if (selectedTable.getActiveSessionId() != null) loadPreview();
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException
                            && ex.getCause() != null ? ex.getCause() : ex;

                    lblStatus.setText("Không thể cập nhật danh sách bàn");
                    System.err.println("[POS] Lỗi tải bàn: " + cause);
                } finally {
                    updateButtons();
                }
            }
        }.execute();
    }

    private void loadPreview() {
        TableDTO snapshot = selectedTable;
        if (snapshot == null || snapshot.getActiveSessionId() == null) return;
        execute("Đọc hóa đơn " + snapshot.getTableName(),
                () -> pos.preview(snapshot.getId()), result -> {
                    if (selectedTable != null &&
                            Objects.equals(selectedTable.getId(), snapshot.getId()) &&
                            Objects.equals(selectedTable.getActiveSessionId(), snapshot.getActiveSessionId())) {
                        renderMergedBillPreview(result);
                    }
                }, this::clearInvoicePanel);
    }

    private void confirmAndOpenTable(TableDTO snapshot) {
        if (snapshot == null || snapshot.getActiveSessionId() != null) return;
        // Tạm dừng tự làm mới trong lúc hộp thoại mở để dữ liệu bàn không đổi bất ngờ.
        reviewing = true;
        updateButtons();
        int answer;
        try {
            answer = JOptionPane.showConfirmDialog(this,
                    "Xác nhận mở phiên phục vụ mới cho " + snapshot.getTableName() + "?",
                    "POS — Xác nhận mở phiên bàn", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
        } finally {
            reviewing = false;
            updateButtons();
        }
        if (answer != JOptionPane.YES_OPTION) return;
        execute("Mở phiên " + snapshot.getTableName(),
                () -> pos.openTable(snapshot.getId()), sid -> {
                    CToast.success(this, "Đã mở phiên #" + sid + " cho " + snapshot.getTableName());
                    refreshTables(false); // Tải lại sơ đồ bàn
                }, () -> refreshTables(false));
    }

    private void paySelectedTable() {
        if (!btnCheckout.isEnabled()) return;
        TableDTO table = selectedTable;
        OrderDTO preview = billPreview;
        PaymentMethod method = selectedPaymentMethod();
        reviewing = true;
        updateButtons();
        boolean confirmed;
        try {
            confirmed = reviewBill(table, preview, method);
        } finally {
            reviewing = false;
            updateButtons();
        }
        if (!confirmed) return;

        execute("Thanh toán " + table.getTableName(), () -> {
            pos.pay(table.getId(), preview, method);
            return true;
        }, ignored -> {
            CToast.success(this, "Đã thanh toán và đóng phiên #" + preview.getTableSessionId());
            showTableMap();
            refreshTables(false);
        }, () -> {
            clearInvoicePanel();
            refreshTables(false);
        });
    }

    private boolean reviewBill(TableDTO table, OrderDTO preview, PaymentMethod method) {
        JPanel review = new JPanel(new BorderLayout(0, 12));
        review.add(sectionTitle(table.getTableName() + " · Phiên #" + preview.getTableSessionId()),
                BorderLayout.NORTH);
        JScrollPane items = createBillScroll();
        items.setPreferredSize(new Dimension(Math.min(760, getWidth() - 100), 230));
        review.add(items, BorderLayout.CENTER);

        JPanel summary = new JPanel(new GridLayout(0, 1, 0, 6));
        summary.add(amountRow("Tổng tiền món", valueLabel(money(preview.getSubtotal()), 14, Utils.TEXT_DARK)));
        summary.add(amountRow("Giảm giá", valueLabel(money(preview.getDiscountAmount()), 14, Utils.TEXT_DARK)));
        summary.add(amountRow("Thuế GTGT (VAT 8%)", valueLabel(money(preview.getCheckoutTax()), 14, Utils.TEXT_DARK)));
        boolean cash = method == PaymentMethod.CASH;
        summary.add(amountRow("Phương thức thanh toán",
                valueLabel(cash ? "Tiền mặt" : "Chuyển khoản ngân hàng", 14, Utils.TEXT_DARK)));
        summary.add(amountRow("Tổng thanh toán",
                valueLabel(money(preview.getCheckoutTotal()), 22, Utils.TERRACOTTA)));
        review.add(summary, BorderLayout.SOUTH);

        Object[] options = {"Quay lại", cash ? "Xác nhận đã nhận tiền" : "Xác nhận đã nhận chuyển khoản"};
        return JOptionPane.showOptionDialog(this, review, "Xem lại hóa đơn",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]) == 1;
    }

    private JLabel createSocketLogo(String imageKey, int size) {
        JLabel label = new JLabel("AN", SwingConstants.CENTER);
        Dimension dimension = new Dimension(size, size);
        label.setPreferredSize(dimension);
        label.setMinimumSize(dimension);
        label.setMaximumSize(dimension);
        label.setForeground(Color.WHITE);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, size / 3)));

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                return ImageSocketClient.fetchImage(imageKey);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage received = get();
                    if (received == null) {
                        System.err.println("[LOGO-TCP] Khong tai duoc " + imageKey);
                        return;
                    }
                    label.setIcon(new ImageIcon(
                            received.getScaledInstance(size, size, Image.SCALE_SMOOTH)));
                    label.setText("");
                    if ("things/logo.png".equals(imageKey)) setIconImage(received);
                    label.revalidate();
                    label.repaint();
                    System.out.println("[LOGO-TCP] OK " + imageKey + " "
                            + received.getWidth() + "x" + received.getHeight());
                } catch (Exception ex) {
                    System.err.println("[LOGO-TCP] Loi " + imageKey + ": " + ex.getMessage());
                }
            }
        }.execute();
        return label;
    }


}