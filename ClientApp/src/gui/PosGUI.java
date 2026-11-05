package gui;

import bus.PosBUS;
import bus.AuthBUS;
import api.SocketClient;
import components.CButton;
import components.CScrollBar;
import components.CToast;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * POS trên ClientApp: mọi nghiệp vụ chạy qua TCP Socket tới ServerApp.
 * Giao dien ke thua bo cuc StaffGUI cu: header xanh rung, luoi ban 3 cot,
 * hoa don chi tiet, bang tong tien/ VAT/ thanh toan. Nghiep vu do PosBUS xu ly.
 * Không truy cập DB hoặc BUS của server trực tiếp.
 */
public final class PosGUI extends JFrame {
    private static final Color FREE_COLOR = new Color(38, 77, 56);
    private static final Color BUSY_COLOR = Utils.TERRACOTTA;
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0 'VNĐ'");

    private final PosBUS pos;
    private final JPanel tableGridPanel = new JPanel(new GridLayout(0, 3, 16, 16));
    private final DefaultTableModel billItemsModel = new DefaultTableModel(
            new String[]{"Lượt gọi", "Tên món ăn", "Đơn giá", "SL", "Thành tiền", "Trạng thái"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable billTable = new JTable(billItemsModel);
    private final JLabel lblSelectedTableInfo = new JLabel("Chưa chọn bàn cần thanh toán");
    private final JLabel lblRootOrderCode = new JLabel("Mã đơn gốc (Root Order): ---");
    private final JLabel lblSubtotal = valueLabel("0 VNĐ", 14, Utils.TEXT_DARK);
    private final JLabel lblDiscount = valueLabel("0 VNĐ", 14, new Color(46, 125, 50));
    private final JLabel lblTaxVAT = valueLabel("0 VNĐ", 14, Utils.TEXT_DARK);
    private final JLabel lblFinalTotal = valueLabel("0 VNĐ", 22, Utils.TERRACOTTA);
    private final JLabel lblStatus = new JLabel("POS trung tâm — sẵn sàng");
    private final JComboBox<PaymentMethod> cboPaymentMethod = new JComboBox<>(PaymentMethod.values());
    private final CButton btnOpenSession = new CButton("Mở phiên bàn", 154, 42);
    private final CButton btnRefresh = new CButton("Làm mới danh sách bàn", 186, 42);
    private final CButton btnCheckout = new CButton("Xuất hóa đơn & Giải phóng bàn", 490, 52);
    private final JButton btnServed = new JButton("Đã phục vụ món được chọn");

    private List<OrderItemDTO> shownItems = Collections.emptyList();
    private TableDTO selectedTable;
    private OrderDTO billPreview;
    private boolean busy;
    private boolean refreshing;
    private final Timer refreshTimer;

    /**
     * Mỗi máy POS đăng nhập bằng tài khoản STAFF/ADMIN cá nhân qua TCP.
     */
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
        JLabel feedback = new JLabel("Dùng tài khoản STAFF/ADMIN cá nhân, kết nối TCP tới Server.");
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
                    SocketClient connection = new SocketClient();
                    try {
                        UserDTO user = AuthBUS.loginWorkstation(connection, account, secret);
                        if (user.getRoleName() == null ||
                                !("STAFF".equalsIgnoreCase(user.getRoleName())
                                        || "ADMIN".equalsIgnoreCase(user.getRoleName())))
                            throw new SecurityException("Tài khoản không có quyền thu ngân");
                        return new PosBUS(user, connection);
                    } catch (RuntimeException ex) {
                        connection.close();
                        throw ex;
                    }
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
        JLabel lblIcon = new JLabel(Utils.getScaledIcon("img/logo.png", 40, 40));
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 10));
        JPanel titleCol = new JPanel();
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleCol.setOpaque(false);
        JLabel brand = new JLabel("Nhà hàng An Nhiên  |  QUẢN LÝ BÀN & THU NGÂN");
        brand.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        brand.setForeground(Color.WHITE);
        JLabel staff = new JLabel("Thu ngân: " + pos.getOperator().getFullName() +
                " (" + pos.getOperator().getEmail() + ")");
        staff.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        staff.setForeground(Utils.GOLD_ACCENT);
        titleCol.add(brand);
        titleCol.add(Box.createVerticalStrut(3));
        titleCol.add(staff);
        leftBrand.add(lblIcon);
        leftBrand.add(titleCol);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        actions.setOpaque(false);
        btnOpenSession.addActionListener(e -> openSelectedTable());
        btnRefresh.addActionListener(e -> refreshTables(true));
        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setBackground(new Color(52, 66, 58));
        btnLogout.setFocusPainted(false);
        btnLogout.setPreferredSize(new Dimension(115, 42));
        btnLogout.setBorder(new LineBorder(new Color(80, 95, 86), 1, true));
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.addActionListener(e -> dispose());
        actions.add(btnOpenSession);
        actions.add(btnRefresh);
        actions.add(btnLogout);

        header.add(leftBrand, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    /**
     * Phan trung tam (so do ban, hoa don, tong tien) lay tu StaffGUI cu.
     */
    private void initMainWorkspace() {
        JPanel workspace = new JPanel(new GridLayout(1, 2, 22, 0));
        workspace.setBackground(Utils.CREAM_BG);
        workspace.setBorder(new EmptyBorder(22, 25, 18, 25));

        JPanel leftCard = createWhiteCard(20, 20, 20, 20);
        leftCard.setLayout(new BorderLayout(0, 15));
        JPanel leftHeader = new JPanel(new BorderLayout(0, 5));
        leftHeader.setOpaque(false);
        JLabel lblMapTitle = new JLabel("Sơ Đồ Bàn Phục Vụ");
        lblMapTitle.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblMapTitle.setForeground(Utils.TEXT_DARK);
        JLabel legend = new JLabel("● Xanh rừng: Bàn trống   |   ● Cam đất: Đang dùng bữa");
        legend.setFont(Utils.getCustomFont("Inter", Font.BOLD, 12));
        legend.setForeground(Utils.GOLD_ACCENT);
        leftHeader.add(lblMapTitle, BorderLayout.NORTH);
        leftHeader.add(legend, BorderLayout.SOUTH);
        tableGridPanel.setBackground(Color.WHITE);
        tableGridPanel.setBorder(new EmptyBorder(10, 5, 10, 5));
        JScrollPane tableScroll = new JScrollPane(tableGridPanel);
        tableScroll.setVerticalScrollBar(new CScrollBar());
        tableScroll.setBorder(null);
        leftCard.add(leftHeader, BorderLayout.NORTH);
        leftCard.add(tableScroll, BorderLayout.CENTER);

        JPanel rightCard = createWhiteCard(20, 24, 20, 24);
        rightCard.setLayout(new BorderLayout(0, 15));
        JPanel billHeader = new JPanel();
        billHeader.setLayout(new BoxLayout(billHeader, BoxLayout.Y_AXIS));
        billHeader.setOpaque(false);
        lblSelectedTableInfo.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblSelectedTableInfo.setForeground(Utils.TEXT_DARK);
        lblRootOrderCode.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        lblRootOrderCode.setForeground(Color.GRAY);
        billHeader.add(lblSelectedTableInfo);
        billHeader.add(Box.createVerticalStrut(4));
        billHeader.add(lblRootOrderCode);

        billTable.setRowHeight(34);
        billTable.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        billTable.setShowVerticalLines(false);
        billTable.setGridColor(new Color(240, 240, 240));
        billTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        billTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateButtons();
        });
        JTableHeader th = billTable.getTableHeader();
        th.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        th.setBackground(Utils.FOREST_BG);
        th.setForeground(Color.WHITE);
        th.setPreferredSize(new Dimension(th.getWidth(), 38));
        JScrollPane billScroll = new JScrollPane(billTable);
        billScroll.setVerticalScrollBar(new CScrollBar());
        billScroll.setBorder(BorderFactory.createEmptyBorder());

        JPanel summaryBox = new JPanel(new GridLayout(5, 2, 10, 10));
        summaryBox.setBackground(Utils.CREAM_BG);
        summaryBox.setBorder(new EmptyBorder(16, 18, 16, 18));
        cboPaymentMethod.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
        cboPaymentMethod.setBackground(Color.WHITE);
        summaryBox.add(titleLabel("Tổng tiền món (Các lượt):"));
        summaryBox.add(lblSubtotal);
        summaryBox.add(titleLabel("Giảm giá (Voucher):"));
        summaryBox.add(lblDiscount);
        summaryBox.add(titleLabel("Thuế GTGT (VAT 8%):"));
        summaryBox.add(lblTaxVAT);
        summaryBox.add(titleLabel("Phương thức thanh toán:"));
        summaryBox.add(cboPaymentMethod);
        summaryBox.add(titleLabel("TỔNG THANH TOÁN (BILL):"));
        summaryBox.add(lblFinalTotal);

        btnServed.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        btnServed.setFocusPainted(false);
        btnServed.setBackground(FREE_COLOR);
        btnServed.setForeground(Color.WHITE);
        btnServed.setBorder(new EmptyBorder(10, 14, 10, 14));
        btnServed.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnServed.setPreferredSize(new Dimension(490, 42));
        btnServed.addActionListener(e -> markSelectedServed());
        btnCheckout.addActionListener(e -> paySelectedTable());
        JPanel actionBox = new JPanel(new GridLayout(2, 1, 0, 10));
        actionBox.setOpaque(false);
        actionBox.add(btnServed);
        actionBox.add(btnCheckout);

        JPanel south = new JPanel(new BorderLayout(0, 14));
        south.setOpaque(false);
        south.add(summaryBox, BorderLayout.CENTER);
        south.add(actionBox, BorderLayout.SOUTH);
        rightCard.add(billHeader, BorderLayout.NORTH);
        rightCard.add(billScroll, BorderLayout.CENTER);
        rightCard.add(south, BorderLayout.SOUTH);

        workspace.add(leftCard);
        workspace.add(rightCard);
        add(workspace, BorderLayout.CENTER);
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
        btnOpenSession.setEnabled(!busy && hasTable && !active);
        btnRefresh.setEnabled(!busy && !refreshing);
        btnCheckout.setEnabled(!busy && active && billPreview != null &&
                Objects.equals(selectedTable.getActiveSessionId(), billPreview.getTableSessionId()));
        int row = billTable.getSelectedRow();
        int modelRow = row < 0 ? -1 : billTable.convertRowIndexToModel(row);
        btnServed.setEnabled(!busy && active && modelRow >= 0 && modelRow < shownItems.size() &&
                shownItems.get(modelRow).getId() != null &&
                shownItems.get(modelRow).getStatus() == OrderStatus.COMPLETED);
    }

    private void renderTableMap(List<TableDTO> tables) {
        tableGridPanel.removeAll();
        for (TableDTO table : tables) {
            boolean active = table.getActiveSessionId() != null;
            boolean isSelected = selectedTable != null && Objects.equals(selectedTable.getId(), table.getId());
            JButton tile = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(active ? BUSY_COLOR : FREE_COLOR);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    if (isSelected) {
                        g2.setColor(Utils.GOLD_ACCENT);
                        g2.setStroke(new BasicStroke(4));
                        g2.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 20, 20);
                    }
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            String name = table.getTableName() == null ? "Bàn #" + table.getId() : table.getTableName();
            String safeName = name.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            tile.setText("<html><center><b>" + safeName + "</b><br/>"
                    + "<span style='font-size:10px;color:#E0E0E0;'>Sức chứa: "
                    + (table.getCapacity() == null ? "---" : table.getCapacity()) + " khách</span><br/><br/>"
                    + (active ? "● ĐANG DÙNG BỮA" : "● BÀN TRỐNG") + "</center></html>");
            tile.setPreferredSize(new Dimension(150, 125));
            tile.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
            tile.setForeground(Color.WHITE);
            tile.setOpaque(false);
            tile.setContentAreaFilled(false);
            tile.setBorderPainted(false);
            tile.setFocusPainted(false);
            tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            tile.addActionListener(e -> selectTable(table));
            tableGridPanel.add(tile);
        }
        tableGridPanel.revalidate();
        tableGridPanel.repaint();
    }

    private void selectTable(TableDTO table) {
        if (busy) return;
        selectedTable = table;
        lblSelectedTableInfo.setText("Bàn đang chọn: " + table.getTableName() + " (#" + table.getId() + ")");
        clearInvoicePanel();
        renderTableMap(lastTables);
        updateButtons();
        if (table.getActiveSessionId() != null) loadPreview();
        else CToast.info(this, table.getTableName() + " chưa có phiên ACTIVE. Có thể mở phiên mới.");
    }

    private List<TableDTO> lastTables = Collections.emptyList();

    private void clearInvoicePanel() {
        billPreview = null;
        shownItems = Collections.emptyList();
        billItemsModel.setRowCount(0);
        lblRootOrderCode.setText("Mã đơn gốc (Root Order): ---");
        lblSubtotal.setText("0 VNĐ");
        lblDiscount.setText("0 VNĐ");
        lblTaxVAT.setText("0 VNĐ");
        lblFinalTotal.setText("0 VNĐ");
        updateButtons();
    }

    private void renderMergedBillPreview(OrderDTO order) {
        clearInvoicePanel();
        if (order == null) {
            lblRootOrderCode.setText("Chưa có món trong phiên bàn này");
            return;
        }
        billPreview = order;
        shownItems = order.getItems() == null ? Collections.emptyList() : new ArrayList<>(order.getItems());
        lblRootOrderCode.setText("Mã đơn gốc: " + order.getOrderCode()
                + "  (ID #" + order.getId() + " — Đã gộp các lượt gọi thêm)");
        for (OrderItemDTO item : shownItems) {
            String turn = Objects.equals(item.getOrderId(), order.getId())
                    ? "Đơn gốc (#" + item.getOrderId() + ")"
                    : "Gọi thêm (#" + item.getOrderId() + ")";
            billItemsModel.addRow(new Object[]{turn, item.getFoodsName(),
                    money(item.getUnitPrice()), item.getQuantity(), money(item.getLineTotal()),
                    item.getStatus() == null ? "---" : item.getStatus().name()});
        }
        lblSubtotal.setText(money(order.getSubtotal()));
        lblDiscount.setText("-" + money(order.getDiscountAmount()));
        lblTaxVAT.setText("+" + money(pos.previewVat(order)));
        lblFinalTotal.setText(money(pos.previewTotal(order)));
        updateButtons();
    }

    private static String money(BigDecimal v) {
        return MONEY.format(v == null ? BigDecimal.ZERO : v);
    }

    @FunctionalInterface
    private interface ResultHandler<T> {
        void accept(T value);
    }

    /**
     * Truy van CSDL tren SwingWorker, khong khoa Swing EDT.
     */
    private <T> void execute(String description, java.util.concurrent.Callable<T> task,
                             ResultHandler<T> onSuccess) {
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
                    CToast.error(PosGUI.this, "Tác vụ bị gián đoạn.");
                } catch (Exception ex) {
                    Throwable cause = ex instanceof ExecutionException ? ex.getCause() : ex;
                    String msg = cause == null ? ex.getMessage() : cause.getMessage();
                    lblStatus.setText("Lỗi: " + msg);
                    CToast.error(PosGUI.this, "Thao tác thất bại: " + msg);
                } finally {
                    updateButtons();
                }
            }
        }.execute();
    }

    private void refreshTables(boolean notify) {
        if (busy || refreshing || !isDisplayable()) return;
        refreshing = true;
        updateButtons();
        Long selectedId = selectedTable == null ? null : selectedTable.getId();
        Long previousSession = selectedTable == null ? null : selectedTable.getActiveSessionId();
        new SwingWorker<List<TableDTO>, Void>() {
            @Override
            protected List<TableDTO> doInBackground() {
                return pos.listTables();
            }

            @Override
            protected void done() {
                refreshing = false;
                if (!isDisplayable()) return;
                try {
                    List<TableDTO> list = get();
                    lastTables = list == null ? Collections.emptyList() : list;
                    selectedTable = null;
                    for (TableDTO table : lastTables) {
                        if (Objects.equals(selectedId, table.getId())) {
                            selectedTable = table;
                            break;
                        }
                    }
                    if (selectedTable == null) {
                        clearInvoicePanel();
                        lblSelectedTableInfo.setText("Chưa chọn bàn cần thanh toán");
                    } else {
                        lblSelectedTableInfo.setText("Bàn đang chọn: " + selectedTable.getTableName()
                                + " (#" + selectedTable.getId() + ")");
                        if (!Objects.equals(previousSession, selectedTable.getActiveSessionId())) {
                            clearInvoicePanel();
                        }
                        if (selectedTable.getActiveSessionId() == null) clearInvoicePanel();
                    }
                    renderTableMap(lastTables);
                    if (notify) CToast.success(PosGUI.this, "Đã cập nhật sơ đồ bàn.");
                    lblStatus.setText("Danh sách: " + lastTables.size() + " bàn — làm mới tự động 6 giây/lần");
                    if (selectedTable != null && selectedTable.getActiveSessionId() != null) loadPreview();
                } catch (Exception ex) {
                    lblStatus.setText("Không thể tải danh sách bàn: " + ex.getMessage());
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
                });
    }

    private void openSelectedTable() {
        TableDTO snapshot = selectedTable;
        if (snapshot == null || snapshot.getActiveSessionId() != null) return;
        int answer = JOptionPane.showConfirmDialog(this,
                "Mở phiên phục vụ mới cho " + snapshot.getTableName() + "?",
                "POS — Xác nhận mở bàn", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        execute("Mở phiên " + snapshot.getTableName(),
                () -> pos.openTable(snapshot.getId()), sid -> {
                    CToast.success(this, "Đã mở phiên #" + sid + " cho " + snapshot.getTableName());
                    refreshTables(false);
                });
    }

    private void markSelectedServed() {
        int row = billTable.getSelectedRow();
        int index = row < 0 ? -1 : billTable.convertRowIndexToModel(row);
        if (index < 0 || index >= shownItems.size()) return;
        OrderItemDTO item = shownItems.get(index);
        if (item.getId() == null || item.getStatus() != OrderStatus.COMPLETED) return;
        TableDTO snapshot = selectedTable;
        execute("Xác nhận đã phục vụ món", () -> {
            pos.markServed(item.getId());
            return true;
        }, ignored -> {
            CToast.success(this, "Đã chuyển món sang SERVED.");
            if (snapshot != null && selectedTable != null &&
                    Objects.equals(snapshot.getId(), selectedTable.getId())) loadPreview();
        });
    }

    private void paySelectedTable() {
        TableDTO snapshot = selectedTable;
        OrderDTO preview = billPreview;
        PaymentMethod method = (PaymentMethod) cboPaymentMethod.getSelectedItem();
        if (snapshot == null || preview == null || preview.getTableSessionId() == null || method == null) return;
        long sid = preview.getTableSessionId();
        if (!Objects.equals(snapshot.getActiveSessionId(), sid)) {
            CToast.error(this, "Phiên bàn đã thay đổi. Hãy làm mới trước khi thanh toán.");
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Xác nhận xuất hóa đơn cho " + snapshot.getTableName() + "?\n"
                        + "- Tổng thanh toán: " + money(pos.previewTotal(preview)) + "\n"
                        + "- Phương thức: " + method + "\n\n"
                        + "Sau thanh toán, phiên CLOSED và bàn được giải phóng.",
                "Xác nhận thanh toán — An Nhiên", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) return;
        execute("Thanh toán " + snapshot.getTableName(), () -> {
            pos.pay(snapshot.getId(), sid, method);
            return true;
        }, ignored -> {
            CToast.success(this, "Đã thanh toán và đóng phiên #" + sid);
            clearInvoicePanel();
            refreshTables(false);
        });
    }
    public static void main(String[] args) {
        SwingUtilities.invokeLater(PosGUI::showPosLogin);
    }

}
