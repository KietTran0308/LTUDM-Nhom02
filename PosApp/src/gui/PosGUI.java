package gui;

import api.ImageSocketClient;
import bus.PosBUS;
import components.CButton;
import components.CDialog;
import components.CScrollBar;
import components.CToast;
import components.CToggleButton;
import dto.OrderDTO;
import dto.OrderItemDTO;
import dto.TableDTO;
import dto.UserDTO;
import dto.dbEnums;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class PosGUI extends JFrame {

    private static final String CARD_MAP = "map";
    private static final String CARD_DETAIL = "detail";

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

    private List<UserDTO> cachedCustomers = new ArrayList<>();
    private UserDTO selectedCustomer;
    private final JTextField txtCustomerSearch = new JTextField();
    private final JPopupMenu customerDropdown = new JPopupMenu();
    private final JLabel lblCustomerInfo = new JLabel("Khách lẻ (Không tích điểm)");
    private final JButton btnClearCustomer = new JButton("✕");

    private final JTextField txtVoucherCode = new JTextField();
    private final CButton btnApplyVoucher = new CButton("Áp dụng", 95, 34);
    private final JLabel lblVoucherStatus = new JLabel("Nhập mã ưu đãi (nếu có)");

    private final Timer refreshTimer;
    private final JLabel lblClock = Utils.createClockLabel();

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
                Arrays.fill(typed, '\0');
                feedback.setText("Vui lòng nhập đủ tài khoản và mật khẩu.");
                return;
            }
            String secret = new String(typed);
            Arrays.fill(typed, '\0');
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

        content.add(feedback, BorderLayout.NORTH);
        content.add(form, BorderLayout.CENTER);
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
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());

        initBrandHeader();
        initMainWorkspace();
        updateButtons();

        refreshTimer = new Timer(6000, e -> refreshTables());
        refreshTimer.start();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                refreshTables();
                loadCustomersAsync();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                refreshTimer.stop();
                pos.close();
            }
        });
    }

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

        JLabel brand = new JLabel("Nhà hàng An Nhiên  |  QUẢN LÝ BÀN & THU NGÂN");
        brand.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        brand.setForeground(Color.WHITE);

        leftBrand.add(lblIcon);
        leftBrand.add(brand);

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

        actions.add(lblClock);
        actions.add(btnCreateCustomer);
        actions.add(btnLogout);

        header.add(leftBrand, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void showCreateCustomerDialog() {
        CDialog dialog = new CDialog(
                this,
                "AN NHIÊN  /  KHÁCH HÀNG THÀNH VIÊN",
                "Tạo tài khoản khách hàng",
                "Nhập thông tin khách hàng mới để tích điểm thành viên."
        );

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);

        JLabel sectionLbl = new JLabel("THÔNG TIN TÀI KHOẢN");
        sectionLbl.setFont(Utils.getCustomFont("Inter", Font.BOLD, 13));
        sectionLbl.setForeground(CDialog.DARK_TEXT);

        JTextField nameField = new JTextField(23);
        JTextField phoneField = new JTextField(23);
        JTextField emailField = new JTextField(23);
        for (JTextField field : new JTextField[]{nameField, phoneField, emailField}) {
            styleInputField(field, new Color(249, 251, 249), CDialog.LINE_COLOR, 8, 12, Font.PLAIN, 14);
        }

        JPanel form = new JPanel(new GridLayout(3, 2, 14, 12));
        form.setOpaque(false);
        form.add(createCustomerFieldLabel("Họ và tên"));
        form.add(nameField);
        form.add(createCustomerFieldLabel("Số điện thoại"));
        form.add(phoneField);
        form.add(createCustomerFieldLabel("Email (tùy chọn)"));
        form.add(emailField);

        body.add(sectionLbl, BorderLayout.NORTH);
        body.add(form, BorderLayout.CENTER);

        CButton cancel = CDialog.createSecondaryButton("Hủy", 122, 44);
        cancel.addActionListener(e -> {
            if (cancel.isEnabled()) dialog.dispose();
        });

        CButton submit = new CButton("Tạo tài khoản", 185, 44);
        submit.addActionListener(e -> {
            if (!submit.isEnabled()) return;
            String name = nameField.getText().trim();
            String phone = phoneField.getText().trim();
            String email = emailField.getText().trim();

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
                        CToast.success(PosGUI.this, "Đã tạo tài khoản cho " + name);
                        loadCustomersAsync();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException ex) {
                        JOptionPane.showMessageDialog(dialog, ex.getCause().getMessage(),
                                "Không tạo được tài khoản", JOptionPane.ERROR_MESSAGE);
                    }
                }
            }.execute();
        });

        busy = true;
        updateButtons();
        try {
            dialog.setBody(body)
                    .setFooterButtons(submit, cancel, submit)
                    .setMinSize(580, 460)
                    .showModal();
        } finally {
            busy = false;
            updateButtons();
        }
    }

    private static JLabel createCustomerFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
        label.setForeground(CDialog.DARK_TEXT);
        return label;
    }

    private static void styleInputField(JTextField field, Color bg, Color borderColor,
                                        int vPad, int hPad, int fontStyle, int fontSize) {
        field.setFont(Utils.getCustomFont("Inter", fontStyle, fontSize));
        field.setForeground(Utils.TEXT_DARK);
        field.setBackground(bg);
        field.setCaretColor(Utils.TEXT_DARK);
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(borderColor, 1, true),
                new EmptyBorder(vPad, hPad, vPad, hPad)));
    }

    private void initMainWorkspace() {
        workspace.setBackground(Utils.CREAM_BG);
        workspace.setBorder(new EmptyBorder(22, 25, 18, 25));
        workspace.add(createTableMap(), CARD_MAP);
        workspace.add(createTableDetail(), CARD_DETAIL);
        add(workspace, BorderLayout.CENTER);
    }

    private JPanel createTableMap() {
        JPanel mapPanel = new JPanel(new BorderLayout(18, 0));
        mapPanel.setOpaque(false);
        mapPanel.add(createFloorSidebar(), BorderLayout.WEST);

        JPanel tableArea = new JPanel(new BorderLayout(0, 12));
        tableArea.setBackground(Color.WHITE);
        tableArea.setBorder(new EmptyBorder(18, 18, 18, 18));

        floorHeading.setFont(Utils.getCustomFont("Inter", Font.BOLD, 20));
        floorHeading.setForeground(Utils.FOREST_BG);
        tableArea.add(floorHeading, BorderLayout.NORTH);

        tableGridPanel.setBackground(Color.WHITE);
        tableGridPanel.setBorder(new EmptyBorder(6, 4, 12, 4));
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

    private JPanel createFloorSidebar() {
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
        renderTableMap(tableSnapshot);
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
            button.setBackground(picked ? Utils.SELECT_COLOR : new Color(244, 247, 243));
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(picked ? Utils.SELECT_COLOR : new Color(224, 230, 223), 1, true),
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

        lblSessionInfo.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
        lblSessionInfo.setForeground(Utils.TEXT_MUTED);
        heading.add(lblSessionInfo, BorderLayout.EAST);
        detail.add(heading, BorderLayout.NORTH);

        JPanel columns = new JPanel(new GridLayout(1, 2, 22, 0));
        columns.setOpaque(false);

        JPanel itemsCard = createWhiteCard(20, 20, 20, 20);
        itemsCard.setLayout(new BorderLayout(0, 15));
        itemsCard.add(sectionTitle("Danh sách các món đã gọi:"), BorderLayout.NORTH);
        itemsCard.add(createBillScroll(), BorderLayout.CENTER);
        columns.add(itemsCard);

        JPanel checkoutCard = createWhiteCard(20, 24, 20, 24);
        checkoutCard.setLayout(new BorderLayout(0, 16));
        checkoutCard.add(sectionTitle("Thanh toán:"), BorderLayout.NORTH);
        checkoutCard.add(createPaymentPanel(), BorderLayout.CENTER);
        columns.add(checkoutCard);

        detail.add(columns, BorderLayout.CENTER);
        return detail;
    }

    private JPanel createPaymentPanel() {
        JPanel payment = new JPanel(new BorderLayout(0, 16));
        payment.setOpaque(false);

        JPanel loyaltyBox = createLoyaltyCustomerBox();
        JPanel voucherBox = createVoucherBox();

        JPanel breakdownBox = new JPanel(new GridLayout(3, 1, 0, 10));
        breakdownBox.setBackground(Utils.CREAM_BG);
        breakdownBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 231, 225), 1, false),
                new EmptyBorder(14, 18, 14, 18)));
        breakdownBox.add(amountRow("Tổng tiền món", lblSubtotal));
        breakdownBox.add(amountRow("Giảm giá", lblDiscount));
        breakdownBox.add(amountRow("Thuế GTGT (VAT 8%)", lblTaxVAT));

        JPanel methodBox = createPaymentMethodBox();

        JPanel totalBox = new JPanel(new BorderLayout(8, 0));
        totalBox.setBackground(Utils.CREAM_BG);
        totalBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(215, 222, 215), 1, false),
                new EmptyBorder(14, 18, 14, 18)));
        JLabel lblTotalTitle = new JLabel("Tổng thanh toán");
        lblTotalTitle.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 16));
        lblTotalTitle.setForeground(Utils.TEXT_DARK);
        totalBox.add(lblTotalTitle, BorderLayout.WEST);
        totalBox.add(lblFinalTotal, BorderLayout.EAST);

        JPanel summaryStack = new JPanel();
        summaryStack.setLayout(new BoxLayout(summaryStack, BoxLayout.Y_AXIS));
        summaryStack.setOpaque(false);

        JPanel[] sections = {loyaltyBox, voucherBox, breakdownBox, methodBox, totalBox};
        for (int i = 0; i < sections.length; i++) {
            JPanel sec = sections[i];
            sec.setMaximumSize(new Dimension(Integer.MAX_VALUE, sec.getPreferredSize().height));
            summaryStack.add(sec);
            if (i < sections.length - 1) {
                summaryStack.add(Box.createVerticalStrut(10));
            }
        }

        payment.add(summaryStack, BorderLayout.NORTH);

        btnCheckout.addActionListener(e -> paySelectedTable());
        JPanel checkoutFooter = new JPanel(new BorderLayout());
        checkoutFooter.setOpaque(false);
        checkoutFooter.add(btnCheckout, BorderLayout.CENTER);
        payment.add(checkoutFooter, BorderLayout.SOUTH);

        return payment;
    }

    private JPanel createPaymentMethodBox() {
        JPanel methodBox = new JPanel(new BorderLayout(0, 12));
        methodBox.setBackground(Color.WHITE);
        methodBox.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 231, 225), 1, false),
                new EmptyBorder(14, 18, 16, 18)));
        methodBox.add(titleLabel("Phương thức thanh toán"), BorderLayout.NORTH);

        cashButton.setText("<html><center>TIỀN MẶT</center></html>");
        bankButton.setText("<html><center>CHUYỂN KHOẢN<br/>NGÂN HÀNG</center></html>");

        paymentMethods.add(cashButton);
        paymentMethods.add(bankButton);
        styleSquarePaymentBox(cashButton);
        styleSquarePaymentBox(bankButton);

        Runnable refreshPaymentBoxes = () -> {
            updatePaymentBoxVisual(cashButton);
            updatePaymentBoxVisual(bankButton);
            updateButtons();
        };
        cashButton.addActionListener(e -> refreshPaymentBoxes.run());
        bankButton.addActionListener(e -> refreshPaymentBoxes.run());
        refreshPaymentBoxes.run();

        JPanel methods = new JPanel(new GridLayout(1, 2, 12, 0));
        methods.setOpaque(false);
        methods.add(cashButton);
        methods.add(bankButton);
        methodBox.add(methods, BorderLayout.CENTER);
        return methodBox;
    }

    private void styleSquarePaymentBox(CToggleButton button) {
        button.setPreferredSize(new Dimension(0, 100));
        button.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setVerticalAlignment(SwingConstants.CENTER);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void updatePaymentBoxVisual(CToggleButton button) {
        boolean selected = button.isSelected();
        button.setBackground(selected ? Utils.SELECT_COLOR : new Color(248, 250, 248));
        button.setForeground(selected ? Color.WHITE : Utils.TEXT_DARK);
        button.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(selected ? Utils.SELECT_COLOR : new Color(200, 209, 202), selected ? 3 : 1, false),
                new EmptyBorder(12, 12, 12, 12)));
        button.repaint();
    }

    private JScrollPane createBillScroll() {
        JTable table = new JTable(billItemsModel) {
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getParent() instanceof JViewport viewport && viewport.getWidth() >= getMinimumSize().width;
            }
        };
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
        boolean active = selectedTable != null && selectedTable.getActiveSessionId() != null;
        boolean available = !busy && !reviewing;
        boolean billReady = active && billPreview != null && billPreview.getCheckoutRevision() != null
                && Objects.equals(selectedTable.getActiveSessionId(), billPreview.getTableSessionId());

        txtCustomerSearch.setEnabled(available && billReady);
        btnBack.setEnabled(available);
        btnCreateCustomer.setEnabled(available);
        cashButton.setEnabled(available && billReady);
        bankButton.setEnabled(available && billReady);
        btnCheckout.setEnabled(available && billReady && selectedPaymentMethod() != null);
        txtVoucherCode.setEnabled(available && billReady);
        btnApplyVoucher.setEnabled(available && billReady);
    }

    private dbEnums.PaymentMethod selectedPaymentMethod() {
        if (cashButton.isSelected()) return dbEnums.PaymentMethod.CASH;
        if (bankButton.isSelected()) return dbEnums.PaymentMethod.BANK_TRANSFER;
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
            button.setBackground(active ? Utils.ACTIVE_COLOR : Utils.EMPTY_COLOR);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(new LineBorder(new Color(220, 224, 217), 1, true));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.addActionListener(e -> selectTable(t));
            tableGridPanel.add(button);
        }

        tableGridPanel.revalidate();
        tableGridPanel.repaint();
    }

    private void selectTable(TableDTO table) {
        if (busy || reviewing) return;
        if (table.getActiveSessionId() == null) {
            confirmAndOpenTable(table);
            return;
        }
        selectedTable = table;
        paymentMethods.clearSelection();
        clearSelectedCustomer();
        clearInvoicePanel();
        updateTableHeading();
        workspaceLayout.show(workspace, CARD_DETAIL);
        loadPreview();
    }

    private void showTableMap() {
        selectedTable = null;
        paymentMethods.clearSelection();
        clearSelectedCustomer();
        clearInvoicePanel();
        workspaceLayout.show(workspace, CARD_MAP);
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
        txtVoucherCode.setText("");
        lblVoucherStatus.setFont(Utils.getCustomFont("Inter", Font.ITALIC, 12));
        lblVoucherStatus.setForeground(Utils.TEXT_MUTED);
        lblVoucherStatus.setText("Nhập mã ưu đãi (nếu có)");
        updateButtons();
    }

    private void renderMergedBillPreview(OrderDTO order) {
        billPreview = order;
        billItemsModel.setRowCount(0);
        for (OrderItemDTO item : order.getItems()) {
            String turn = Objects.equals(item.getOrderId(), order.getId())
                    ? "Đơn gốc (#" + item.getOrderId() + ")"
                    : "Gọi thêm (#" + item.getOrderId() + ")";
            billItemsModel.addRow(new Object[]{
                    turn,
                    item.getFoodsName(),
                    money(item.getUnitPrice()),
                    item.getQuantity(),
                    money(item.getLineTotal()),
                    itemStatusText(item.getStatus())
            });
        }
        lblSubtotal.setText(money(order.getSubtotal()));
        lblDiscount.setText(money(order.getDiscountAmount()));
        lblTaxVAT.setText(money(order.getCheckoutTax()));
        lblFinalTotal.setText(money(order.getCheckoutTotal()));
        updateButtons();
    }

    private static String itemStatusText(dbEnums.OrderStatus status) {
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

    private <T> void execute(Callable<T> task, Consumer<T> onSuccess, Runnable onFailure) {
        if (busy || !isDisplayable()) return;
        busy = true;
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
                    onSuccess.accept(get());
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
                    String msg = cause.getMessage() == null
                            ? "Không thực hiện được thao tác" : cause.getMessage();
                    CToast.error(PosGUI.this, msg);
                    onFailure.run();
                } finally {
                    updateButtons();
                }
            }
        }.execute();
    }

    private void refreshTables() {
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
                    if (selectedTable == null) return;

                    Long selectedId = selectedTable.getId();
                    Long previousSession = selectedTable.getActiveSessionId();
                    selectedTable = tables.stream()
                            .filter(t -> Objects.equals(t.getId(), selectedId))
                            .findFirst()
                            .orElse(null);

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
                    Throwable cause = ex instanceof ExecutionException && ex.getCause() != null
                            ? ex.getCause() : ex;
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

        execute(() -> pos.preview(snapshot.getId()), result -> {
            if (selectedTable != null
                    && Objects.equals(selectedTable.getId(), snapshot.getId())
                    && Objects.equals(selectedTable.getActiveSessionId(), snapshot.getActiveSessionId())) {
                renderMergedBillPreview(result);
            }
        }, this::clearInvoicePanel);
    }

    private void confirmAndOpenTable(TableDTO snapshot) {
        if (snapshot == null || snapshot.getActiveSessionId() != null) return;

        reviewing = true;
        updateButtons();
        boolean confirmed;
        try {
            confirmed = confirmOpenTableModal(snapshot);
        } finally {
            reviewing = false;
            updateButtons();
        }
        if (!confirmed) return;

        execute(() -> pos.openTable(snapshot.getId()), sid -> {
            CToast.success(this, "Đã mở phiên #" + sid + " cho " + snapshot.getTableName());
            refreshTables();
        }, this::refreshTables);
    }

    private boolean confirmOpenTableModal(TableDTO table) {
        final boolean[] confirmed = {false};

        CDialog dialog = new CDialog(
                this,
                "AN NHIÊN  /  POS THU NGÂN",
                "Xác nhận mở phiên phục vụ",
                "Xác nhận mở phiên phục vụ mới cho " + table.getTableName() + "?"
        );

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);

        JLabel detailsTitle = new JLabel("THÔNG TIN BÀN ĐƯỢC CHỌN");
        detailsTitle.setFont(Utils.getCustomFont("Inter", Font.BOLD, 13));
        detailsTitle.setForeground(CDialog.DARK_TEXT);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 12));
        details.setOpaque(false);
        details.add(CDialog.createInfoRow("Bàn", table.getTableName()));
        details.add(CDialog.createInfoRow("Tầng", floorName(table.getFloorNumber())));
        details.add(CDialog.createInfoRow("Sức chứa", table.getCapacity() + " người"));
        details.add(CDialog.createInfoRow("Trạng thái hiện tại", "Bàn trống"));
        details.add(CDialog.createInfoRow("Thu ngân", pos.getOperator().getFullName()));

        body.add(detailsTitle, BorderLayout.NORTH);
        body.add(details, BorderLayout.CENTER);

        CButton cancel = CDialog.createSecondaryButton("Hủy bỏ", 122, 44);
        cancel.addActionListener(e -> dialog.dispose());

        CButton accept = new CButton("Xác nhận mở phiên", 212, 44);
        accept.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        dialog.setBody(body)
                .setFooterButtons(accept, cancel, accept)
                .setMinSize(580, 480)
                .showModal();

        return confirmed[0];
    }

    private void paySelectedTable() {
        if (!btnCheckout.isEnabled()) return;

        TableDTO table = selectedTable;
        OrderDTO preview = billPreview;
        dbEnums.PaymentMethod method = selectedPaymentMethod();
        UserDTO customer = selectedCustomer;

        reviewing = true;
        updateButtons();
        boolean confirmed;
        try {
            confirmed = reviewBill(table, preview, method, customer);
        } finally {
            reviewing = false;
            updateButtons();
        }
        if (!confirmed) return;

        execute(() -> {
            pos.pay(table.getId(), preview, method, customer);
            return true;
        }, ignored -> {
            CToast.success(this, "Đã thanh toán và đóng phiên #" + preview.getTableSessionId()
                    + (customer != null ? " — Tích điểm cho " + customer.getFullName() : ""));
            showTableMap();
            refreshTables();
            if (customer != null) loadCustomersAsync();
        }, () -> {
            clearInvoicePanel();
            refreshTables();
        });
    }

    private boolean reviewBill(TableDTO table, OrderDTO preview, dbEnums.PaymentMethod method, UserDTO customer) {
        final boolean[] confirmed = {false};
        boolean cash = method == dbEnums.PaymentMethod.CASH;

        CDialog dialog = new CDialog(
                this,
                "AN NHIÊN  /  XÁC NHẬN THANH TOÁN",
                "Xem lại hóa đơn",
                table.getTableName() + " · Phiên #" + preview.getTableSessionId()
        );

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);

        JScrollPane items = createBillScroll();
        items.setPreferredSize(new Dimension(Math.min(760, Math.max(540, getWidth() - 140)), 210));
        body.add(items, BorderLayout.CENTER);

        String customerDisplay = customer == null
                ? "Khách lẻ (Không tích điểm)"
                : customer.getFullName() + " (" + (customer.getPhone() != null && !customer.getPhone().isBlank()
                ? customer.getPhone() : customer.getEmail()) + ")";

        JPanel summary = new JPanel(new GridLayout(0, 1, 0, 8));
        summary.setBackground(Utils.CREAM_BG);
        summary.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(CDialog.LINE_COLOR, 1, false),
                new EmptyBorder(14, 18, 14, 18)));
        summary.add(amountRow("Tổng tiền món", valueLabel(money(preview.getSubtotal()), 14, Utils.TEXT_DARK)));
        summary.add(amountRow("Giảm giá", valueLabel(money(preview.getDiscountAmount()), 14, new Color(46, 125, 50))));
        summary.add(amountRow("Thuế GTGT (VAT 8%)", valueLabel(money(preview.getCheckoutTax()), 14, Utils.TEXT_DARK)));
        summary.add(amountRow("Khách hàng tích điểm",
                valueLabel(customerDisplay, 14, customer != null ? Utils.SELECT_COLOR : Utils.TEXT_MUTED)));
        summary.add(amountRow("Phương thức thanh toán",
                valueLabel(cash ? "Tiền mặt" : "Chuyển khoản ngân hàng", 14, Utils.TEXT_DARK)));
        summary.add(amountRow("Tổng thanh toán",
                valueLabel(money(preview.getCheckoutTotal()), 22, Utils.TERRACOTTA)));
        body.add(summary, BorderLayout.SOUTH);

        CButton backBtn = CDialog.createSecondaryButton("Quay lại", 130, 44);
        backBtn.addActionListener(e -> dialog.dispose());

        String acceptText = cash ? "Xác nhận đã nhận tiền" : "Xác nhận đã nhận chuyển khoản";
        CButton acceptBtn = new CButton(acceptText, cash ? 230 : 275, 44);
        acceptBtn.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        dialog.setBody(body)
                .setFooterButtons(acceptBtn, backBtn, acceptBtn)
                .showModal();

        return confirmed[0];
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
                    if (received == null) return;
                    label.setIcon(new ImageIcon(received.getScaledInstance(size, size, Image.SCALE_SMOOTH)));
                    label.setText("");
                    if ("things/logo.png".equals(imageKey)) setIconImage(received);
                    label.revalidate();
                    label.repaint();
                } catch (Exception ex) {
                    System.err.println("[LOGO-TCP] Lỗi " + imageKey + ": " + ex.getMessage());
                }
            }
        }.execute();
        return label;
    }

    private JPanel createLoyaltyCustomerBox() {
        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 231, 225), 1, false),
                new EmptyBorder(12, 18, 14, 18)));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.add(titleLabel("Khách hàng tích điểm (SĐT hoặc Gmail)"), BorderLayout.WEST);

        btnClearCustomer.setFont(Utils.getCustomFont("Inter", Font.BOLD, 11));
        btnClearCustomer.setForeground(Utils.TERRACOTTA);
        btnClearCustomer.setBackground(new Color(252, 244, 240));
        btnClearCustomer.setFocusPainted(false);
        btnClearCustomer.setBorder(new EmptyBorder(2, 8, 2, 8));
        btnClearCustomer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClearCustomer.setVisible(false);
        btnClearCustomer.addActionListener(e -> clearSelectedCustomer());
        topRow.add(btnClearCustomer, BorderLayout.EAST);

        styleInputField(txtCustomerSearch, Color.WHITE, new Color(210, 218, 211), 8, 12, Font.PLAIN, 13);

        lblCustomerInfo.setFont(Utils.getCustomFont("Inter", Font.ITALIC, 12));
        lblCustomerInfo.setForeground(Utils.TEXT_MUTED);

        customerDropdown.setFocusable(false);
        customerDropdown.setBorder(new LineBorder(new Color(200, 209, 202), 1));

        txtCustomerSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filterCustomerDropdown(); }
            @Override public void removeUpdate(DocumentEvent e) { filterCustomerDropdown(); }
            @Override public void changedUpdate(DocumentEvent e) { filterCustomerDropdown(); }
        });

        txtCustomerSearch.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                filterCustomerDropdown();
            }
        });

        box.add(topRow, BorderLayout.NORTH);
        box.add(txtCustomerSearch, BorderLayout.CENTER);
        box.add(lblCustomerInfo, BorderLayout.SOUTH);
        return box;
    }

    private void filterCustomerDropdown() {
        if (!txtCustomerSearch.isShowing() || !txtCustomerSearch.isEnabled()) return;
        String keyword = txtCustomerSearch.getText().trim().toLowerCase(Locale.ROOT);
        customerDropdown.removeAll();

        if (keyword.isEmpty()) {
            customerDropdown.setVisible(false);
            return;
        }

        int count = 0;
        for (UserDTO c : cachedCustomers) {
            String phone = c.getPhone() == null ? "" : c.getPhone();
            String email = c.getEmail() == null ? "" : c.getEmail();
            String name = c.getFullName() == null ? "Khách hàng" : c.getFullName();

            if (phone.toLowerCase(Locale.ROOT).contains(keyword)
                    || email.toLowerCase(Locale.ROOT).contains(keyword)
                    || name.toLowerCase(Locale.ROOT).contains(keyword)) {
                String contact = !phone.isEmpty() && !email.isEmpty()
                        ? phone + " • " + email
                        : (!phone.isEmpty() ? phone : email);
                JMenuItem item = new JMenuItem("<html><b>" + safeHtml(name) + "</b> — " + safeHtml(contact) + "</html>");
                item.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
                item.setBackground(Color.WHITE);
                item.setPreferredSize(new Dimension(Math.max(360, txtCustomerSearch.getWidth()), 36));
                item.addActionListener(e -> selectLoyaltyCustomer(c));
                customerDropdown.add(item);
                if (++count >= 6) break;
            }
        }

        if (count == 0) {
            JMenuItem empty = new JMenuItem("Không tìm thấy khách hàng (Bấm 'Tạo tài khoản khách' ở góc trên)");
            empty.setEnabled(false);
            empty.setFont(Utils.getCustomFont("Inter", Font.ITALIC, 12));
            customerDropdown.add(empty);
        }

        customerDropdown.show(txtCustomerSearch, 0, txtCustomerSearch.getHeight());
    }

    private void selectLoyaltyCustomer(UserDTO customer) {
        this.selectedCustomer = customer;
        customerDropdown.setVisible(false);
        String contact = customer.getPhone() != null && !customer.getPhone().isBlank()
                ? customer.getPhone() : customer.getEmail();
        txtCustomerSearch.setText(contact);
        lblCustomerInfo.setFont(Utils.getCustomFont("Inter", Font.BOLD, 12));
        lblCustomerInfo.setForeground(Utils.SELECT_COLOR);
        lblCustomerInfo.setText("✓ Thành viên: " + customer.getFullName() + " (" + contact + ") — "
                + (customer.getCurrentPoints() == null ? 0 : customer.getCurrentPoints()) + " điểm");
        btnClearCustomer.setVisible(true);
    }

    private void clearSelectedCustomer() {
        this.selectedCustomer = null;
        customerDropdown.setVisible(false);
        txtCustomerSearch.setText("");
        lblCustomerInfo.setFont(Utils.getCustomFont("Inter", Font.ITALIC, 12));
        lblCustomerInfo.setForeground(Utils.TEXT_MUTED);
        lblCustomerInfo.setText("Khách lẻ (Không tích điểm)");
        btnClearCustomer.setVisible(false);
    }

    private void loadCustomersAsync() {
        new SwingWorker<List<UserDTO>, Void>() {
            @Override
            protected List<UserDTO> doInBackground() {
                return pos.listCustomers();
            }

            @Override
            protected void done() {
                try {
                    cachedCustomers = get();
                } catch (Exception ex) {
                    System.err.println("[POS] Không tải được danh sách khách hàng: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private JPanel createVoucherBox() {
        JPanel box = new JPanel(new BorderLayout(0, 8));
        box.setBackground(Color.WHITE);
        box.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(226, 231, 225), 1, false),
                new EmptyBorder(12, 18, 14, 18)));

        box.add(titleLabel("Mã giảm giá / Voucher (Nếu có)"), BorderLayout.NORTH);

        styleInputField(txtVoucherCode, Color.WHITE, new Color(210, 218, 211), 7, 12, Font.BOLD, 13);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setOpaque(false);
        inputRow.add(txtVoucherCode, BorderLayout.CENTER);
        inputRow.add(btnApplyVoucher, BorderLayout.EAST);

        lblVoucherStatus.setFont(Utils.getCustomFont("Inter", Font.ITALIC, 12));
        lblVoucherStatus.setForeground(Utils.TEXT_MUTED);

        btnApplyVoucher.addActionListener(e -> applyVoucherAction());
        txtVoucherCode.addActionListener(e -> applyVoucherAction());

        box.add(inputRow, BorderLayout.CENTER);
        box.add(lblVoucherStatus, BorderLayout.SOUTH);
        return box;
    }

    private void applyVoucherAction() {
        if (selectedTable == null || selectedTable.getActiveSessionId() == null) return;
        String code = txtVoucherCode.getText().trim().toUpperCase(Locale.ROOT);
        TableDTO snapshot = selectedTable;

        execute(() -> pos.applyVoucher(snapshot.getId(), code),
                updatedOrder -> {
                    if (selectedTable != null && Objects.equals(selectedTable.getId(), snapshot.getId())) {
                        renderMergedBillPreview(updatedOrder);
                        if (!code.isEmpty()) {
                            txtVoucherCode.setText(code);
                            lblVoucherStatus.setFont(Utils.getCustomFont("Inter", Font.BOLD, 12));
                            lblVoucherStatus.setForeground(Utils.SELECT_COLOR);
                            lblVoucherStatus.setText("✓ Đã áp dụng mã " + code
                                    + " (Giảm " + money(updatedOrder.getDiscountAmount()) + ")");
                            CToast.success(this, "Áp dụng mã " + code + " thành công");
                        } else {
                            lblVoucherStatus.setFont(Utils.getCustomFont("Inter", Font.ITALIC, 12));
                            lblVoucherStatus.setForeground(Utils.TEXT_MUTED);
                            lblVoucherStatus.setText("Nhập mã ưu đãi (nếu có)");
                            CToast.success(this, "Đã gỡ mã giảm giá");
                        }
                    }
                },
                () -> {
                    lblVoucherStatus.setFont(Utils.getCustomFont("Inter", Font.BOLD, 12));
                    lblVoucherStatus.setForeground(Utils.TERRACOTTA);
                    lblVoucherStatus.setText("✕ Mã không hợp lệ hoặc không đủ điều kiện áp dụng");
                });
    }
}