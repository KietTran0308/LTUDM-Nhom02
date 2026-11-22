package gui;

import api.ImageSocketClient;
import api.SocketClient;
import bus.AdminBUS;
import components.*;
import dto.*;
import org.json.JSONArray;
import org.json.JSONObject;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

public class AdminGUI extends JFrame {

    // ==========================================================
    // --- HẰNG SỐ ĐỊNH DẠNG & CẤU HÌNH ---
    // ==========================================================
    private static final DecimalFormat SALES_MONEY_FMT = new DecimalFormat("#,##0.## 'VNĐ'");
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### VNĐ");
    private static final DecimalFormat INT_FMT = new DecimalFormat("#,###");

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter INPUT_DT_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_FULL_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String FONT_FAMILY = resolveFontFamily();
    private static final Font ACTION_FONT = font(Font.BOLD, 12);
    private static final int ACTION_GAP = 8;
    private static final int ACTION_PAD = 12;
    private static final int ACTION_PAD_X = 12;
    private static final int ACTION_H = 28;
    private static final int AUTO_SYNC_INTERVAL_MS = 15_000;

    // Page IDs
    private static final String PAGE_FOODS = "FOODS";
    private static final String PAGE_REVENUE = "REVENUE";
    private static final String PAGE_ACCOUNTS = "ACCOUNTS";
    private static final String PAGE_POINTS = "POINTS";
    private static final String PAGE_VOUCHERS = "VOUCHERS";

    // Nhãn hiển thị
    private static final String ROLE_MEMBER = "Thành viên";
    private static final String ROLE_STAFF = "Nhân viên";
    private static final String ROLE_KITCHEN = "Bếp";
    private static final String ROLE_ADMIN = "Quản trị viên";
    private static final String ST_ACTIVE = "Đang hoạt động";
    private static final String ST_LOCKED = "Đã khóa";

    private static final String TIER_DONG = "Đồng";
    private static final String TIER_BAC = "Bạc";
    private static final String TIER_VANG = "Vàng";
    private static final String TIER_KIM_CUONG = "Kim cương";

    private static final String V_ACTIVE = "Đang áp dụng";
    private static final String V_UPCOMING = "Sắp diễn ra";
    private static final String V_EXPIRED = "Hết hạn / Hết lượt";
    private static final String V_PAUSED = "Tạm ngưng";

    private static final Map<String, ImageIcon> FOOD_THUMB_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Set<String> FOOD_THUMB_LOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ==========================================================
    // --- MẠNG & ĐIỀU HƯỚNG ---
    // ==========================================================
    private final SocketClient socket;
    private final AdminBUS adminBUS;
    private javax.swing.Timer autoSyncTimer;

    private final CardLayout contentCardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(contentCardLayout);
    private final Map<String, JButton> sidebarButtons = new LinkedHashMap<>();
    private JLabel lblPageTitle;
    private JLabel lblPageSubtitle;
    private String activePage = PAGE_FOODS;
    private boolean adjusting = false;

    // Trang 1: Thực đơn
    private List<FoodDTO> allFoods = new ArrayList<>();
    private List<FoodDTO> pageFoods = new ArrayList<>();
    private DefaultTableModel foodModel;
    private JTextField txtFoodSearch;
    private JComboBox<String> cboFoodCat, cboFoodStatus;
    private JLabel kpiFoodTotal, kpiFoodOpen, kpiFoodPaused;
    private JTable foodTable;
    private Paging foodPaging;

    // Trang 2: Doanh thu
    private DefaultTableModel billModel, topFoodsModel;
    private JButton btnFilterRevenue;
    private JLabel lblRevenueStatus;
    private JTextField txtBillSearch;
    private JComboBox<String> cboBillTime, cboBillPay;
    private JLabel kpiRevenue, kpiBills, kpiDiscount, kpiTax;
    private RevenueChart revenueChart;
    private Paging billPaging;

    // Trang 3: Tài khoản
    private List<UserDTO> allAccounts = new ArrayList<>();
    private List<UserDTO> pageAccounts = new ArrayList<>();
    private DefaultTableModel accModel;
    private JTextField txtAccSearch;
    private JComboBox<String> cboAccRole, cboAccStatus;
    private JLabel kpiAccTotal, kpiAccMember, kpiAccStaff, kpiAccLocked;
    private Paging accPaging;

    // Trang 4: Điểm thưởng
    private List<PointDTO> allPoints = new ArrayList<>();
    private List<PointDTO> pagePoints = new ArrayList<>();
    private Map<Long, UserDTO> pointAccountMap = Collections.emptyMap();
    private DefaultTableModel pointModel;
    private JTextField txtPointSearch;
    private JComboBox<String> cboPointTier;
    private JLabel kpiPointMembers, kpiPointCurrent, kpiPointEarned;
    private Paging pointPaging;

    // Trang 5: Voucher
    private List<VoucherDTO> allVouchers = new ArrayList<>();
    private List<VoucherDTO> pageVouchers = new ArrayList<>();
    private DefaultTableModel voucherModel;
    private JTextField txtVoucherSearch;
    private JComboBox<String> cboVoucherType, cboVoucherStatus;
    private JLabel kpiVoucherTotal, kpiVoucherActive, kpiVoucherExpired, kpiVoucherUsed;
    private Paging voucherPaging;

    // ==========================================================
    // --- KHỞI TẠO ---
    // ==========================================================
    public AdminGUI(UserDTO admin, SocketClient authenticatedSocket) {
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRoleName())) {
            throw new SecurityException("Chỉ tài khoản ADMIN mới được mở giao diện quản trị");
        }

        this.socket = authenticatedSocket;
        this.adminBUS = new AdminBUS(authenticatedSocket);

        setTitle("Bếp Nhà Restaurant - Admin Dashboard");
        setUndecorated(true);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(Utils.CREAM_BG);

        contentCards.setOpaque(false);
        contentCards.setBorder(new EmptyBorder(22, 28, 24, 28));
        contentCards.add(createFoodPage(), PAGE_FOODS);
        contentCards.add(createRevenuePage(), PAGE_REVENUE);
        contentCards.add(createAccountPage(), PAGE_ACCOUNTS);
        contentCards.add(createPointPage(), PAGE_POINTS);
        contentCards.add(createVoucherPage(), PAGE_VOUCHERS);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Utils.CREAM_BG);
        main.add(createHeader(), BorderLayout.NORTH);
        main.add(contentCards, BorderLayout.CENTER);

        add(createSidebar(), BorderLayout.WEST);
        add(main, BorderLayout.CENTER);

        showPage(PAGE_FOODS);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                if (autoSyncTimer != null) autoSyncTimer.stop();
                socket.logout();
                socket.close();
            }
        });
        startAutoSync();
    }

    private static String resolveFontFamily() {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String family : new String[]{"Be Vietnam Pro", "Inter", "Segoe UI", "Roboto"}) {
            if (available.contains(family)) return family;
        }
        return Font.SANS_SERIF;
    }

    private static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size);
    }

    // ==========================================================
    // --- ĐỒNG BỘ & NETWORK WORKERS ---
    // ==========================================================
    private void startAutoSync() {
        autoSyncTimer = new javax.swing.Timer(AUTO_SYNC_INTERVAL_MS, e -> {
            if (!isDisplayable() || !socket.isConnected()) return;
            for (Window w : Window.getWindows()) {
                if (w instanceof JDialog && w.isShowing()) return;
            }
            refreshCurrentPage();
        });
        autoSyncTimer.setRepeats(true);
        autoSyncTimer.start();
    }

    private void refreshCurrentPage() {
        switch (activePage) {
            case PAGE_REVENUE -> loadRevenue();
            case PAGE_ACCOUNTS -> loadAccounts();
            case PAGE_POINTS -> loadPoints();
            case PAGE_VOUCHERS -> loadVouchers();
            default -> loadFoods();
        }
    }

    private void showNetworkError(Exception ex) {
        Throwable cause = (ex instanceof ExecutionException && ex.getCause() != null) ? ex.getCause() : ex;
        CToast.error(this, "Lỗi giao tiếp server: " + cause.getMessage());
    }

    private void performUpdate(String name, Callable<Boolean> operation, Runnable refresh) {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return operation.call();
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    if (!Boolean.TRUE.equals(get())) {
                        throw new IllegalStateException("Server từ chối thao tác");
                    }
                    CToast.success(AdminGUI.this, name + " thành công");
                    if (refresh != null) refresh.run();
                } catch (Exception ex) {
                    showNetworkError(ex);
                }
            }
        }.execute();
    }

    private abstract class PageLoadWorker extends SwingWorker<Object, Void> {
        protected abstract void onLoaded(Object result);

        @Override
        protected final void done() {
            if (!isDisplayable()) return;
            try {
                onLoaded(get());
            } catch (Exception ex) {
                showNetworkError(ex);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private static List copyOrEmpty(List values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    // ==========================================================
    // --- SIDEBAR & HEADER ---
    // ==========================================================
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 20)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Utils.FOREST_CIRCLE_1);
                g2.fillOval(-145, getHeight() - 330, 330, 330);
                g2.setColor(Utils.FOREST_CIRCLE_2);
                g2.fillOval(-65, getHeight() - 250, 220, 220);
                g2.dispose();
            }
        };
        sidebar.setBackground(Utils.FOREST_BG);
        sidebar.setPreferredSize(new Dimension(248, 0));
        sidebar.setBorder(new EmptyBorder(26, 16, 20, 16));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);

        JPanel logoRow = new JPanel(new BorderLayout(12, 0));
        logoRow.setOpaque(false);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        logoRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        logoRow.setBorder(new EmptyBorder(0, 6, 0, 0));
        logoRow.add(createSocketLogo("things/logo.png", 46), BorderLayout.WEST);

        JPanel brandText = new JPanel();
        brandText.setLayout(new BoxLayout(brandText, BoxLayout.Y_AXIS));
        brandText.setOpaque(false);
        JLabel lblBrand = new JLabel("AN NHIÊN");
        lblBrand.setFont(font(Font.BOLD, 18));
        lblBrand.setForeground(Color.WHITE);
        JLabel lblBrandSub = new JLabel("QUẢN LÝ NHÀ HÀNG");
        lblBrandSub.setFont(font(Font.PLAIN, 11));
        lblBrandSub.setForeground(Utils.GOLD_ACCENT);
        brandText.add(lblBrand);
        brandText.add(lblBrandSub);
        logoRow.add(brandText, BorderLayout.CENTER);
        top.add(logoRow);
        top.add(Box.createVerticalStrut(30));

        JLabel caption = new JLabel("DANH MỤC QUẢN TRỊ");
        caption.setFont(font(Font.BOLD, 11));
        caption.setForeground(new Color(120, 140, 128));
        caption.setBorder(new EmptyBorder(0, 12, 10, 0));
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(caption);

        top.add(createNavButton("Quản lý thực đơn", NavIcon.FOOD, PAGE_FOODS));
        top.add(Box.createVerticalStrut(8));
        top.add(createNavButton("Thống kê doanh thu", NavIcon.CHART, PAGE_REVENUE));
        top.add(Box.createVerticalStrut(8));
        top.add(createNavButton("Quản lý tài khoản", NavIcon.USER, PAGE_ACCOUNTS));
        top.add(Box.createVerticalStrut(8));
        top.add(createNavButton("Quản lý điểm thưởng", NavIcon.RECEIPT, PAGE_POINTS));
        top.add(Box.createVerticalStrut(8));
        top.add(createNavButton("Quản lý voucher", NavIcon.PERCENT, PAGE_VOUCHERS));
        sidebar.add(top, BorderLayout.NORTH);

        JPanel clockCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        clockCard.setOpaque(false);
        clockCard.setBorder(new EmptyBorder(8, 10, 8, 10));

        JLabel lblClock = Utils.createClockLabel();
        lblClock.setHorizontalAlignment(SwingConstants.CENTER);
        lblClock.setFont(font(Font.BOLD, 13));
        lblClock.setForeground(Utils.GOLD_ACCENT);
        clockCard.add(lblClock, BorderLayout.CENTER);
        sidebar.add(clockCard, BorderLayout.SOUTH);

        return sidebar;
    }

    private JButton createNavButton(String text, int iconType, String pageId) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (pageId.equals(activePage)) {
                    g2.setColor(Utils.TERRACOTTA);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(255, 255, 255, 22));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setIcon(new NavIcon(iconType, 20));
        button.setIconTextGap(14);
        button.setFont(font(Font.BOLD, 13));
        button.setForeground(new Color(190, 204, 195));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBorder(new EmptyBorder(0, 14, 0, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(216, 46));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> showPage(pageId));
        sidebarButtons.put(pageId, button);
        return button;
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Color.WHITE);
        header.setPreferredSize(new Dimension(0, 84));
        header.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, Utils.BORDER_SOFT),
                new EmptyBorder(0, 28, 0, 28)
        ));

        lblPageTitle = new JLabel();
        lblPageTitle.setFont(font(Font.BOLD, 22));
        lblPageTitle.setForeground(Utils.TEXT_DARK);
        lblPageSubtitle = new JLabel();
        lblPageSubtitle.setFont(font(Font.PLAIN, 13));
        lblPageSubtitle.setForeground(Utils.TEXT_MUTED);

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(Box.createVerticalGlue());
        left.add(lblPageTitle);
        left.add(Box.createVerticalStrut(3));
        left.add(lblPageSubtitle);
        left.add(Box.createVerticalGlue());
        header.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 22));
        right.setOpaque(false);

        JPanel adminInfo = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                if (Boolean.TRUE.equals(getClientProperty("hover"))) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Utils.HOVER_ROW);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    g2.dispose();
                }
                super.paintComponent(g);
            }
        };
        adminInfo.setOpaque(false);
        adminInfo.setBorder(new EmptyBorder(4, 10, 4, 10));
        adminInfo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        adminInfo.add(new JLabel(new AvatarIcon(38, "A", Utils.FOREST_BG)), BorderLayout.WEST);

        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setOpaque(false);
        JLabel n = new JLabel("Quản trị viên  ▾");
        n.setFont(font(Font.BOLD, 13));
        n.setForeground(Utils.TEXT_DARK);
        JLabel r = new JLabel("ADMIN");
        r.setFont(font(Font.PLAIN, 11));
        r.setForeground(Utils.TEXT_MUTED);
        t.add(n);
        t.add(r);
        adminInfo.add(t, BorderLayout.CENTER);

        JPopupMenu adminMenu = new JPopupMenu();
        adminMenu.setBackground(Color.WHITE);
        adminMenu.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(Utils.BORDER_SOFT, 1),
                new EmptyBorder(6, 6, 6, 6)
        ));

        JButton btnLogout = createRoundedButton("Đăng xuất", Utils.DANGER_RED, Color.WHITE, 150, 36);
        btnLogout.addActionListener(e -> {
            adminMenu.setVisible(false);
            if (confirmAction("Xác nhận đăng xuất", "Bạn có chắc muốn đăng xuất khỏi trang quản trị?")) {
                dispose();
                SwingUtilities.invokeLater(AdminGUI::showAdminLogin);
            }
        });
        adminMenu.add(btnLogout);

        adminInfo.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                adminInfo.putClientProperty("hover", true);
                adminInfo.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                adminInfo.putClientProperty("hover", false);
                adminInfo.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                int x = adminInfo.getWidth() - adminMenu.getPreferredSize().width;
                adminMenu.show(adminInfo, Math.min(0, x), adminInfo.getHeight() + 6);
            }
        });

        right.add(adminInfo);
        header.add(right, BorderLayout.EAST);
        return header;
    }

    private void showPage(String pageId) {
        activePage = pageId;
        contentCardLayout.show(contentCards, pageId);
        for (Map.Entry<String, JButton> e : sidebarButtons.entrySet()) {
            e.getValue().setForeground(e.getKey().equals(pageId) ? Color.WHITE : new Color(190, 204, 195));
            e.getValue().repaint();
        }

        switch (pageId) {
            case PAGE_REVENUE -> {
                lblPageTitle.setText("Thống kê doanh thu");
                lblPageSubtitle.setText("Doanh thu theo ngày, món bán chạy và lịch sử hóa đơn.");
                loadRevenue();
            }
            case PAGE_ACCOUNTS -> {
                lblPageTitle.setText("Quản lý tài khoản");
                lblPageSubtitle.setText("Nhân viên cấp thẻ thành viên, quản lý tài khoản nhân viên và khách hàng.");
                loadAccounts();
            }
            case PAGE_POINTS -> {
                lblPageTitle.setText("Quản lý điểm thưởng");
                lblPageSubtitle.setText("Theo dõi điểm tích lũy, hạng thành viên và lịch sử giao dịch điểm.");
                loadPoints();
            }
            case PAGE_VOUCHERS -> {
                lblPageTitle.setText("Quản lý voucher");
                lblPageSubtitle.setText("Tạo mã khuyến mãi, giới hạn lượt dùng và theo dõi hiệu lực áp dụng.");
                loadVouchers();
            }
            default -> {
                lblPageTitle.setText("Quản lý thực đơn");
                lblPageSubtitle.setText("Thêm, chỉnh sửa và quản lý trạng thái bán của các món ăn.");
                loadFoods();
            }
        }
    }

    // ==========================================================
    // --- TRANG 1: QUẢN LÝ THỰC ĐƠN ---
    // ==========================================================
    private JPanel createFoodPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        kpiFoodTotal = kpiLabel("0");
        kpiFoodOpen = kpiLabel("0");
        kpiFoodPaused = kpiLabel("0");
        JPanel kpis = kpiRow(3);
        kpis.add(createKpiCard("TỔNG SỐ MÓN", kpiFoodTotal, Utils.FOREST_BG, NavIcon.FOOD));
        kpis.add(createKpiCard("ĐANG MỞ BÁN", kpiFoodOpen, Utils.SAFE_GREEN, NavIcon.CHECK));
        kpis.add(createKpiCard("TẠM NGƯNG", kpiFoodPaused, Utils.GOLD_ACCENT, NavIcon.PAUSE));
        page.add(kpis, BorderLayout.NORTH);

        txtFoodSearch = createSearchField("Tìm theo tên món hoặc SKU...", 300);
        cboFoodCat = createFilterCombo("Danh mục", "Tất cả danh mục");
        cboFoodStatus = createFilterCombo("Trạng thái", "Tất cả trạng thái", "Đang mở bán", "Tạm ngưng");
        onTextChange(txtFoodSearch, () -> applyFoodFilter(true));
        cboFoodCat.addActionListener(e -> { if (!adjusting) applyFoodFilter(true); });
        cboFoodStatus.addActionListener(e -> { if (!adjusting) applyFoodFilter(true); });

        JButton btnAdd = createRoundedButton("+  Thêm món mới", Utils.TERRACOTTA, Color.WHITE, 160, 40);
        btnAdd.addActionListener(e -> openFoodDialog(null));

        foodModel = readOnlyModel("Hình", "Mã SKU", "Tên món", "Danh mục", "Đơn giá", "Trạng thái", "Thao tác");
        CTable foodTableComponent = createStyledTable(foodModel, 52);
        this.foodTable = foodTableComponent.getTable();
        foodTable.getColumnModel().getColumn(0).setCellRenderer(new ThumbRenderer());
        foodTable.getColumnModel().getColumn(4).setCellRenderer(new RowRenderer(true));
        foodTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        installActionColumn(foodTable, 6, (row, idx) -> {
            if (row < 0 || row >= pageFoods.size()) return;
            FoodDTO f = pageFoods.get(row);
            if (idx == 0) openFoodDialog(f);
            else if (idx == 1) toggleFoodStatus(f);
            else confirmDeleteFood(f);
        });
        setColumnWidths(foodTable, 70, 110, 220, 150, 140, 140, 250);

        foodPaging = new Paging(10, this::renderFoodPage);
        page.add(createTableCard(
                createToolbar(flow(FlowLayout.LEFT, txtFoodSearch, cboFoodCat, cboFoodStatus), flow(FlowLayout.RIGHT, btnAdd)),
                foodTableComponent,
                foodPaging.bar
        ), BorderLayout.CENTER);
        return page;
    }

    private void loadFoods() {
        new PageLoadWorker() {
            @Override
            protected Object doInBackground() {
                return adminBUS.getAllFoods();
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void onLoaded(Object result) {
                allFoods = copyOrEmpty((List<FoodDTO>) result);
                updateComboItems(cboFoodCat, "Tất cả danh mục",
                        allFoods.stream()
                                .map(f -> f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại")
                                .distinct().sorted().toList());

                int open = (int) allFoods.stream().filter(AdminGUI::isOpen).count();
                kpiFoodTotal.setText(String.valueOf(allFoods.size()));
                kpiFoodOpen.setText(String.valueOf(open));
                kpiFoodPaused.setText(String.valueOf(allFoods.size() - open));
                applyFoodFilter(false);
            }
        }.execute();
    }

    private void applyFoodFilter(boolean resetPage) {
        String q = txtFoodSearch.getText().trim().toLowerCase(Locale.ROOT);
        String cat = (String) cboFoodCat.getSelectedItem();
        String st = (String) cboFoodStatus.getSelectedItem();

        List<FoodDTO> res = new ArrayList<>();
        for (FoodDTO f : allFoods) {
            String catN = f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại";
            if (!q.isEmpty()
                    && !String.valueOf(f.getName()).toLowerCase(Locale.ROOT).contains(q)
                    && !String.valueOf(f.getSku()).toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            if (cat != null && !cat.startsWith("Tất cả") && !cat.equals(catN)) continue;
            if ("Đang mở bán".equals(st) && !isOpen(f)) continue;
            if ("Tạm ngưng".equals(st) && isOpen(f)) continue;
            res.add(f);
        }

        foodPaging.setAll(res, resetPage);
        renderFoodPage();
    }

    private void renderFoodPage() {
        pageFoods = (List<FoodDTO>) (List<?>) foodPaging.slice();
        foodModel.setRowCount(0);
        for (FoodDTO f : pageFoods) {
            boolean open = isOpen(f);
            loadFoodThumbAsync(f.getCoverImageUrl(), this.foodTable);
            foodModel.addRow(new Object[]{
                    f, // Truyền FoodDTO để ThumbRenderer lấy được cả coverImageUrl lẫn tên món dự phòng
                    f.getSku(),
                    f.getName(),
                    f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại",
                    MONEY_FMT.format(f.getPrice()),
                    open ? "Đang mở bán" : "Tạm ngưng",
                    open ? "Sửa|Tạm ngưng|Xóa" : "Sửa|Mở bán|Xóa"
            });
        }
        foodPaging.refreshBar();
    }

    private static boolean isOpen(FoodDTO f) {
        return Boolean.TRUE.equals(f.getIsPublished());
    }

    private void openFoodDialog(FoodDTO food) {
        new SwingWorker<List<CategoryDTO>, Void>() {
            @Override
            protected List<CategoryDTO> doInBackground() {
                return adminBUS.getAllCategories();
            }

            @Override
            protected void done() {
                try {
                    List<CategoryDTO> categories = get();
                    boolean edit = food != null;

                    JTextField sku = createStyledTextField(edit ? String.valueOf(food.getSku()) : "");
                    JTextField name = createStyledTextField(edit ? String.valueOf(food.getName()) : "");
                    JTextField price = createStyledTextField(edit && food.getPrice() != null
                            ? food.getPrice().toBigInteger().toString() : "");
                    JTextField cover = createStyledTextField(edit && food.getCoverImageUrl() != null
                            ? food.getCoverImageUrl() : "");

                    JComboBox<CategoryDTO> catBox = new JComboBox<>();
                    styleCombo(catBox);
                    for (CategoryDTO c : categories) {
                        catBox.addItem(c);
                        if (edit && String.valueOf(c).equals(food.getCategoryName())) {
                            catBox.setSelectedItem(c);
                        }
                    }

                    JCheckBox published = createCheckBox("Mở bán trên Tablet của khách", !edit || isOpen(food));
                    if (edit) {
                        sku.setEnabled(false);
                        cover.setEnabled(false);
                    }

                    JPanel form = newFormPanel();
                    addField(form, "Mã SKU", sku);
                    addField(form, "Tên món ăn", name);
                    addField(form, "Danh mục", catBox);
                    addField(form, "Đơn giá niêm yết (VNĐ)", price);
                    addField(form, "Ảnh bìa (URL / tên file)", cover);
                    form.add(published);

                    showModal(
                            edit ? "Sửa món ăn" : "Thêm món mới",
                            edit ? "Cập nhật thông tin món đang chọn." : "Nhập thông tin món mới để đưa lên thực đơn.",
                            form,
                            edit ? "Lưu thay đổi" : "Thêm món",
                            Utils.TERRACOTTA,
                            () -> {
                                String n = name.getText().trim();
                                if (!edit && sku.getText().trim().isEmpty()) return "Vui lòng nhập mã SKU.";
                                if (n.isEmpty()) return "Vui lòng nhập tên món ăn.";
                                BigDecimal p;
                                try {
                                    p = new BigDecimal(price.getText().trim().replaceAll("[,\\s]", ""));
                                } catch (NumberFormatException ex) {
                                    return "Đơn giá không hợp lệ.";
                                }
                                if (p.signum() <= 0) return "Đơn giá phải lớn hơn 0.";

                                CategoryDTO cat = (CategoryDTO) catBox.getSelectedItem();
                                Long catId = cat != null ? cat.getId() : null;

                                performUpdate(
                                        edit ? "Cập nhật món" : "Thêm món",
                                        () -> edit
                                                ? adminBUS.updateFood(food.getId(), n, p, catId, published.isSelected())
                                                : adminBUS.addNewFood(sku.getText().trim(), catId, n, p, cover.getText(), published.isSelected()),
                                        AdminGUI.this::loadFoods
                                );
                                return null;
                            }
                    );
                } catch (Exception ex) {
                    showNetworkError(ex);
                }
            }
        }.execute();
    }

    private void toggleFoodStatus(FoodDTO f) {
        boolean pause = isOpen(f);
        String msg = pause
                ? "Tạm ngưng bán món '" + f.getName() + "'?\nMón sẽ không hiển thị trên Tablet của khách."
                : "Mở bán lại món '" + f.getName() + "'?\nMón sẽ hiển thị trên Tablet của khách.";
        if (!confirmAction(pause ? "Xác nhận tạm ngưng" : "Xác nhận mở bán", msg)) return;

        performUpdate(pause ? "Tạm ngưng món" : "Mở bán món", () -> {
            Long catId = null;
            if (f.getCategoryName() != null) {
                for (CategoryDTO c : adminBUS.getAllCategories()) {
                    if (String.valueOf(c).equals(f.getCategoryName())) {
                        catId = c.getId();
                        break;
                    }
                }
            }
            return adminBUS.updateFood(f.getId(), f.getName(), f.getPrice(), catId, !pause);
        }, this::loadFoods);
    }

    private void confirmDeleteFood(FoodDTO f) {
        if (confirmAction("Xác nhận xóa mềm",
                "Xóa mềm món '" + f.getName() + "'?\nMón sẽ ẩn khỏi Tablet nhưng vẫn giữ trong lịch sử.")) {
            performUpdate("Xóa món", () -> adminBUS.deleteFood(f.getId()), this::loadFoods);
        }
    }

    // ==========================================================
    // --- TRANG 2: THỐNG KÊ DOANH THU ---
    // ==========================================================
    private JPanel createRevenuePage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        kpiRevenue = kpiLabel("0 VNĐ");
        kpiBills = kpiLabel("0");
        kpiDiscount = kpiLabel("0 VNĐ");
        kpiTax = kpiLabel("0 VNĐ");
        JPanel kpis = kpiRow(4);
        kpis.add(createKpiCard("THỰC THU", kpiRevenue, Utils.TERRACOTTA, NavIcon.COIN));
        kpis.add(createKpiCard("HÓA ĐƠN ĐÃ THU", kpiBills, Utils.FOREST_BG, NavIcon.RECEIPT));
        kpis.add(createKpiCard("GIẢM GIÁ", kpiDiscount, Utils.GOLD_ACCENT, NavIcon.PERCENT));
        kpis.add(createKpiCard("VAT", kpiTax, Utils.SAFE_GREEN, NavIcon.CHART));

        revenueChart = new RevenueChart();
        CToggleButton segLine = new CToggleButton("Đường", 84, true);
        CToggleButton segBar = new CToggleButton("Cột", 84, false);
        ButtonGroup chartGroup = new ButtonGroup();
        chartGroup.add(segLine);
        chartGroup.add(segBar);
        segLine.addActionListener(e -> revenueChart.setLine(true));
        segBar.addActionListener(e -> revenueChart.setLine(false));

        JPanel chartHead = new JPanel(new BorderLayout());
        chartHead.setOpaque(false);
        JLabel chartTitle = new JLabel("Doanh thu theo ngày");
        chartTitle.setFont(font(Font.BOLD, 16));
        chartTitle.setToolTipText("Biểu đồ hiển thị tối đa 30 ngày gần nhất có trong kết quả");
        chartTitle.setForeground(Utils.TEXT_DARK);
        chartHead.add(chartTitle, BorderLayout.WEST);
        chartHead.add(flow(FlowLayout.RIGHT, segLine, segBar), BorderLayout.EAST);

        JPanel chartCard = createCard();
        chartCard.setLayout(new BorderLayout(0, 8));
        chartCard.add(chartHead, BorderLayout.NORTH);
        chartCard.add(revenueChart, BorderLayout.CENTER);

        topFoodsModel = readOnlyModel("Món", "Số phần");
        CTable topTable = createStyledTable(topFoodsModel, 26);
        setColumnWidths(topTable.getTable(), 280, 72);
        JLabel topTitle = new JLabel("Top 10 món bán chạy");
        topTitle.setFont(font(Font.BOLD, 16));
        lblRevenueStatus = new JLabel(" ");
        lblRevenueStatus.setForeground(Utils.TEXT_MUTED);

        JPanel charts = new JPanel(new GridLayout(1, 2, 16, 0));
        charts.setOpaque(false);
        charts.add(chartCard);
        charts.add(createTableCard(topTitle, topTable, lblRevenueStatus));
        charts.setPreferredSize(new Dimension(0, 245));
        charts.setMaximumSize(new Dimension(Integer.MAX_VALUE, 245));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        kpis.setAlignmentX(Component.LEFT_ALIGNMENT);
        charts.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(kpis);
        north.add(Box.createVerticalStrut(18));
        north.add(charts);
        page.add(north, BorderLayout.NORTH);

        txtBillSearch = createSearchField("Tìm theo mã hóa đơn...", 260);
        cboBillTime = createFilterCombo("Tất cả thời gian", "Hôm nay", "7 ngày qua", "30 ngày qua");
        cboBillPay = createFilterCombo("Tất cả thanh toán");
        for (dbEnums.PaymentMethod payment : dbEnums.PaymentMethod.values()) {
            cboBillPay.addItem(payment.name());
        }
        btnFilterRevenue = createRoundedButton("Lọc", Utils.FOREST_BG, Color.WHITE, 84, 40);
        btnFilterRevenue.addActionListener(e -> loadRevenue());
        txtBillSearch.addActionListener(e -> loadRevenue());

        billModel = readOnlyModel("Mã HĐ", "Phiên bàn", "Giảm giá", "VAT", "Tổng thanh toán",
                "Thanh toán", "Trạng thái", "Thời gian");
        CTable billTableComponent = createStyledTable(billModel, 40);
        JTable billTable = billTableComponent.getTable();
        billTable.getColumnModel().getColumn(4).setCellRenderer(new RowRenderer(true));
        billTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        setColumnWidths(billTable, 80, 90, 100, 100, 155, 125, 100, 160);

        billPaging = new Paging(6, this::renderBillPage);
        page.add(createTableCard(
                createToolbar(flow(FlowLayout.LEFT, txtBillSearch, cboBillTime, cboBillPay), flow(FlowLayout.RIGHT, btnFilterRevenue)),
                billTableComponent,
                billPaging.bar
        ), BorderLayout.CENTER);
        return page;
    }

    private void loadRevenue() {
        if (!btnFilterRevenue.isEnabled()) return;
        LocalDate today = LocalDate.now();
        LocalDate from = switch (cboBillTime.getSelectedIndex()) {
            case 1 -> today;
            case 2 -> today.minusDays(6);
            case 3 -> today.minusDays(29);
            default -> null;
        };
        LocalDate to = from == null ? null : today;
        String billNumber = txtBillSearch.getText().replace("#", "").trim();
        String payment = cboBillPay.getSelectedIndex() > 0 ? (String) cboBillPay.getSelectedItem() : "";

        setRevenueFiltersEnabled(false);
        lblRevenueStatus.setText("Đang tải thống kê…");
        new SwingWorker<AdminBUS.RevenueReport, Void>() {
            @Override
            protected AdminBUS.RevenueReport doInBackground() {
                return adminBUS.getRevenueReport(from, to, billNumber, payment);
            }

            @Override
            protected void done() {
                setRevenueFiltersEnabled(true);
                if (!isDisplayable()) return;
                try {
                    renderRevenue(get());
                } catch (Exception ex) {
                    lblRevenueStatus.setText("Không tải được. Bảng đang hiển thị kết quả lần trước.");
                    showNetworkError(ex);
                }
            }
        }.execute();
    }

    private void setRevenueFiltersEnabled(boolean enabled) {
        btnFilterRevenue.setEnabled(enabled);
        txtBillSearch.setEnabled(enabled);
        cboBillTime.setEnabled(enabled);
        cboBillPay.setEnabled(enabled);
    }

    private void renderRevenue(AdminBUS.RevenueReport report) {
        JSONObject summary = report.summary();
        kpiRevenue.setText(SALES_MONEY_FMT.format(summary.getBigDecimal("revenue")));
        kpiBills.setText(INT_FMT.format(summary.getInt("paidBillCount")));
        kpiDiscount.setText(SALES_MONEY_FMT.format(summary.getBigDecimal("discount")));
        kpiTax.setText(SALES_MONEY_FMT.format(summary.getBigDecimal("tax")));
        updateChart(summary.getJSONArray("days"));

        topFoodsModel.setRowCount(0);
        for (Object value : summary.getJSONArray("topFoods")) {
            JSONObject food = (JSONObject) value;
            topFoodsModel.addRow(new Object[]{food.getString("name"), INT_FMT.format(food.getLong("quantity"))});
        }
        lblRevenueStatus.setText(topFoodsModel.getRowCount() == 0 ? "Không có món đã bán trong kỳ" : " ");
        billPaging.setAll(report.bills(), true);
        renderBillPage();
    }

    private void renderBillPage() {
        List<BillDTO> currentPage = (List<BillDTO>) (List<?>) billPaging.slice();
        billModel.setRowCount(0);
        for (BillDTO b : currentPage) {
            billModel.addRow(new Object[]{
                    "#" + b.getId(),
                    "Phiên #" + b.getTableSessionId(),
                    SALES_MONEY_FMT.format(b.getDiscount()),
                    SALES_MONEY_FMT.format(b.getTax()),
                    SALES_MONEY_FMT.format(b.getTotalAmount()),
                    b.getPaymentMethod() != null ? b.getPaymentMethod().name() : "---",
                    switch (b.getPaymentStatus()) {
                        case PAID -> "Đã thu";
                        case UNPAID -> "Chưa thu";
                        case CANCELLED -> "Đã hủy";
                        case null -> "---";
                    },
                    b.getPaidAt() != null ? b.getPaidAt().format(TIME_FMT) : "---"
            });
        }
        billPaging.refreshBar();
    }

    private void updateChart(JSONArray dailyRevenue) {
        TreeMap<LocalDate, BigDecimal> perDay = new TreeMap<>();
        for (Object value : dailyRevenue) {
            JSONObject day = (JSONObject) value;
            perDay.put(LocalDate.parse(day.getString("day")), day.getBigDecimal("total"));
        }
        List<LocalDate> days = new ArrayList<>();
        List<BigDecimal> values = new ArrayList<>();
        if (!perDay.isEmpty()) {
            LocalDate last = perDay.lastKey();
            LocalDate first = perDay.firstKey();
            if (first.isBefore(last.minusDays(29))) first = last.minusDays(29);
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                days.add(d);
                values.add(perDay.getOrDefault(d, BigDecimal.ZERO));
            }
        }
        revenueChart.setData(days, values);
    }

    // ==========================================================
    // --- TRANG 3: QUẢN LÝ TÀI KHOẢN ---
    // ==========================================================
    private JPanel createAccountPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        kpiAccTotal = kpiLabel("0");
        kpiAccMember = kpiLabel("0");
        kpiAccStaff = kpiLabel("0");
        kpiAccLocked = kpiLabel("0");
        JPanel kpis = kpiRow(4);
        kpis.add(createKpiCard("TỔNG TÀI KHOẢN", kpiAccTotal, Utils.FOREST_BG, NavIcon.USER));
        kpis.add(createKpiCard("THÀNH VIÊN", kpiAccMember, Utils.GOLD_ACCENT, NavIcon.RECEIPT));
        kpis.add(createKpiCard("NHÂN VIÊN & BẾP", kpiAccStaff, Utils.SAFE_GREEN, NavIcon.USER));
        kpis.add(createKpiCard("ĐANG KHÓA", kpiAccLocked, Utils.DANGER_RED, NavIcon.LOCK));
        page.add(kpis, BorderLayout.NORTH);

        txtAccSearch = createSearchField("Tìm theo tên, SĐT...", 300);
        cboAccRole = createFilterCombo("Tất cả vai trò", ROLE_MEMBER, ROLE_STAFF, ROLE_KITCHEN);
        cboAccStatus = createFilterCombo("Tất cả trạng thái", ST_ACTIVE, ST_LOCKED);
        onTextChange(txtAccSearch, () -> applyAccountFilter(true));
        cboAccRole.addActionListener(e -> { if (!adjusting) applyAccountFilter(true); });
        cboAccStatus.addActionListener(e -> { if (!adjusting) applyAccountFilter(true); });

        JButton btnNew = createRoundedButton("+  Cấp tài khoản mới", Utils.TERRACOTTA, Color.WHITE, 190, 40);
        btnNew.addActionListener(e -> openAccountDialog(null));

        accModel = readOnlyModel("ID", "Họ tên", "SĐT", "Email", "Vai trò", "Trạng thái", "Thao tác");
        CTable accTableComponent = createStyledTable(accModel, 48);
        JTable accTable = accTableComponent.getTable();
        accTable.getColumnModel().getColumn(4).setCellRenderer(new BadgeRenderer());
        accTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        installActionColumn(accTable, 6, (row, idx) -> {
            if (row < 0 || row >= pageAccounts.size()) return;
            UserDTO u = pageAccounts.get(row);
            if (idx == 0) openAccountDialog(u);
            else toggleLock(u);
        });
        setColumnWidths(accTable, 60, 200, 120, 200, 130, 140, 170);

        accPaging = new Paging(10, this::renderAccountPage);
        page.add(createTableCard(
                createToolbar(flow(FlowLayout.LEFT, txtAccSearch, cboAccRole, cboAccStatus), flow(FlowLayout.RIGHT, btnNew)),
                accTableComponent,
                accPaging.bar
        ), BorderLayout.CENTER);
        return page;
    }

    private void loadAccounts() {
        new PageLoadWorker() {
            @Override
            protected Object doInBackground() {
                return adminBUS.getAllAccounts();
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void onLoaded(Object result) {
                allAccounts = copyOrEmpty((List<UserDTO>) result);
                allAccounts.removeIf(u -> "ADMIN".equalsIgnoreCase(u.getRoleName()));
                applyAccountFilter(false);
            }
        }.execute();
    }

    private static String accountRoleLabel(UserDTO account) {
        String role = account.getRoleName();
        if (role == null) return ROLE_MEMBER;
        return switch (role.trim().toUpperCase(Locale.ROOT)) {
            case "ADMIN" -> ROLE_ADMIN;
            case "STAFF" -> ROLE_STAFF;
            case "KITCHEN" -> ROLE_KITCHEN;
            default -> ROLE_MEMBER;
        };
    }

    private void applyAccountFilter(boolean resetPage) {
        int members = 0, staff = 0, locked = 0;
        for (UserDTO u : allAccounts) {
            String label = accountRoleLabel(u);
            if (ROLE_MEMBER.equals(label)) members++;
            else if (ROLE_STAFF.equals(label) || ROLE_KITCHEN.equals(label)) staff++;
            if (Boolean.FALSE.equals(u.getIsActive())) locked++;
        }
        kpiAccTotal.setText(String.valueOf(allAccounts.size()));
        kpiAccMember.setText(String.valueOf(members));
        kpiAccStaff.setText(String.valueOf(staff));
        kpiAccLocked.setText(String.valueOf(locked));

        String q = txtAccSearch.getText().trim().toLowerCase(Locale.ROOT);
        String role = (String) cboAccRole.getSelectedItem();
        String st = (String) cboAccStatus.getSelectedItem();

        List<UserDTO> res = new ArrayList<>();
        for (UserDTO u : allAccounts) {
            String name = u.getFullName() != null ? u.getFullName().toLowerCase(Locale.ROOT) : "";
            String phone = u.getPhone() != null ? u.getPhone().toLowerCase(Locale.ROOT) : "";
            String rName = accountRoleLabel(u);
            boolean isLocked = Boolean.FALSE.equals(u.getIsActive());

            if (!q.isEmpty() && !name.contains(q) && !phone.contains(q)) continue;
            if (role != null && !role.startsWith("Tất cả") && !role.equals(rName)) continue;
            if (ST_ACTIVE.equals(st) && isLocked) continue;
            if (ST_LOCKED.equals(st) && !isLocked) continue;
            res.add(u);
        }
        accPaging.setAll(res, resetPage);
        renderAccountPage();
    }

    private void renderAccountPage() {
        pageAccounts = (List<UserDTO>) (List<?>) accPaging.slice();
        accModel.setRowCount(0);
        for (UserDTO u : pageAccounts) {
            boolean isLocked = Boolean.FALSE.equals(u.getIsActive());
            accModel.addRow(new Object[]{
                    u.getId(),
                    u.getFullName(),
                    u.getPhone(),
                    u.getEmail(),
                    accountRoleLabel(u),
                    isLocked ? ST_LOCKED : ST_ACTIVE,
                    isLocked ? "Cập nhật|Mở khóa" : "Cập nhật|Khóa"
            });
        }
        accPaging.refreshBar();
    }

    private void openAccountDialog(UserDTO acc) {
        boolean edit = acc != null;
        JTextField name = createStyledTextField(edit ? acc.getFullName() : "");
        JTextField phone = createStyledTextField(edit ? acc.getPhone() : "");
        JTextField email = createStyledTextField(edit && acc.getEmail() != null ? acc.getEmail() : "");

        JComboBox<String> roleBox = new JComboBox<>(new String[]{ROLE_MEMBER, ROLE_STAFF, ROLE_KITCHEN});
        styleCombo(roleBox);

        if (edit) {
            String rName = accountRoleLabel(acc);
            if (ROLE_ADMIN.equals(rName)) roleBox.addItem(ROLE_ADMIN);
            roleBox.setSelectedItem(rName);
        }

        JPanel form = newFormPanel();
        addField(form, "Họ và tên", name);
        addField(form, "Số điện thoại", phone);
        addField(form, "Địa chỉ Email", email);
        addField(form, "Vai trò", roleBox);

        showModal(
                edit ? "Cập nhật tài khoản" : "Cấp tài khoản mới",
                edit ? "Chỉnh sửa thông tin liên hệ của tài khoản." : "Mật khẩu mặc định sẽ là số điện thoại của người dùng.",
                form,
                edit ? "Lưu thay đổi" : "Cấp tài khoản",
                Utils.TERRACOTTA,
                () -> {
                    String n = name.getText().trim();
                    String p = phone.getText().trim();
                    String e = email.getText().trim();
                    String roleStr = (String) roleBox.getSelectedItem();

                    if (n.isEmpty()) return "Vui lòng nhập họ tên.";
                    if (!p.matches("0\\d{9}")) return "Số điện thoại không hợp lệ (VD: 0901234567).";
                    if (e.isEmpty() || !e.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                        return "Email không hợp lệ.";
                    }

                    String rNameDB = switch (String.valueOf(roleStr)) {
                        case ROLE_STAFF -> "STAFF";
                        case ROLE_KITCHEN -> "KITCHEN";
                        default -> "CUSTOMER";
                    };

                    performUpdate(
                            edit ? "Cập nhật tài khoản" : "Cấp tài khoản",
                            () -> edit
                                    ? adminBUS.updateAccount(acc.getId(), n, p, e, rNameDB)
                                    : adminBUS.createNewAccount(n, p, e, 0L, rNameDB),
                            this::loadAccounts
                    );
                    return null;
                }
        );
    }

    private void toggleLock(UserDTO u) {
        boolean isCurrentlyActive = Boolean.TRUE.equals(u.getIsActive());
        String title = isCurrentlyActive ? "Khóa tài khoản" : "Mở khóa tài khoản";
        String message = isCurrentlyActive
                ? "Bạn có chắc muốn khóa tài khoản '" + u.getFullName() + "'?"
                : "Bạn có chắc muốn mở khóa tài khoản '" + u.getFullName() + "'?";
        if (!confirmAction(title, message)) return;

        performUpdate(title, () -> adminBUS.setAccountActive(u.getId(), !isCurrentlyActive), this::loadAccounts);
    }

    // ==========================================================
    // --- TRANG 4: QUẢN LÝ ĐIỂM THƯỞNG ---
    // ==========================================================
    private static String tierLabel(dbEnums.PointTier tier) {
        if (tier == null) return TIER_DONG;
        return switch (tier) {
            case BAC -> TIER_BAC;
            case VANG -> TIER_VANG;
            case KIM_CUONG -> TIER_KIM_CUONG;
            default -> TIER_DONG;
        };
    }

    private JPanel createPointPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        kpiPointMembers = kpiLabel("0");
        kpiPointCurrent = kpiLabel("0");
        kpiPointEarned = kpiLabel("0");
        JPanel kpis = kpiRow(3);
        kpis.add(createKpiCard("TỔNG THÀNH VIÊN", kpiPointMembers, Utils.FOREST_BG, NavIcon.USER));
        kpis.add(createKpiCard("ĐIỂM HIỆN CÓ", kpiPointCurrent, Utils.GOLD_ACCENT, NavIcon.RECEIPT));
        kpis.add(createKpiCard("ĐIỂM ĐÃ TÍCH LŨY", kpiPointEarned, Utils.SAFE_GREEN, NavIcon.CHECK));
        page.add(kpis, BorderLayout.NORTH);

        txtPointSearch = createSearchField("Tìm theo tên, SĐT...", 300);
        cboPointTier = createFilterCombo("Hạng thành viên", "Tất cả hạng",
                TIER_DONG, TIER_BAC, TIER_VANG, TIER_KIM_CUONG);
        onTextChange(txtPointSearch, () -> applyPointFilter(true));
        cboPointTier.addActionListener(e -> { if (!adjusting) applyPointFilter(true); });

        pointModel = readOnlyModel("ID", "Họ tên", "SĐT", "Điểm hiện có", "Tổng tích lũy",
                "Hạng", "Cập nhật", "Thao tác");
        CTable pointTableComponent = createStyledTable(pointModel, 48);
        JTable pointTable = pointTableComponent.getTable();
        pointTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        installActionColumn(pointTable, 7, (row, idx) -> {
            if (row < 0 || row >= pagePoints.size()) return;
            openPointHistoryDialog(pagePoints.get(row));
        });
        setColumnWidths(pointTable, 60, 200, 120, 120, 120, 110, 160, 130);

        pointPaging = new Paging(10, this::renderPointPage);
        page.add(createTableCard(
                createToolbar(flow(FlowLayout.LEFT, txtPointSearch, cboPointTier), null),
                pointTableComponent,
                pointPaging.bar
        ), BorderLayout.CENTER);
        return page;
    }

    private void loadPoints() {
        new PageLoadWorker() {
            @Override
            protected Object doInBackground() {
                return new Object[]{adminBUS.getAllMemberPoints(), adminBUS.getAllAccounts()};
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void onLoaded(Object resObj) {
                Object[] result = (Object[]) resObj;
                allPoints = copyOrEmpty((List<PointDTO>) result[0]);
                List<UserDTO> accounts = copyOrEmpty((List<UserDTO>) result[1]);
                Map<Long, UserDTO> map = new HashMap<>(accounts.size());
                for (UserDTO acc : accounts) {
                    if (acc.getId() != null) map.put(acc.getId(), acc);
                }
                pointAccountMap = map;
                applyPointFilter(false);
            }
        }.execute();
    }

    private void applyPointFilter(boolean resetPage) {
        long totalCurrent = 0, totalEarned = 0;
        for (PointDTO p : allPoints) {
            totalCurrent += p.getCurrentPoints();
            totalEarned += p.getTotalEarnedPoints();
        }
        kpiPointMembers.setText(String.valueOf(allPoints.size()));
        kpiPointCurrent.setText(INT_FMT.format(totalCurrent));
        kpiPointEarned.setText(INT_FMT.format(totalEarned));

        String q = txtPointSearch.getText().trim().toLowerCase(Locale.ROOT);
        String tier = (String) cboPointTier.getSelectedItem();

        List<PointDTO> res = new ArrayList<>();
        for (PointDTO p : allPoints) {
            UserDTO acc = pointAccountMap.get(p.getUserId());
            String name = acc != null && acc.getFullName() != null ? acc.getFullName().toLowerCase(Locale.ROOT) : "";
            String phone = acc != null && acc.getPhone() != null ? acc.getPhone().toLowerCase(Locale.ROOT) : "";

            if (!q.isEmpty() && !name.contains(q) && !phone.contains(q)) continue;
            if (tier != null && !tier.startsWith("Tất cả") && !tier.equals(tierLabel(p.getTier()))) continue;
            res.add(p);
        }
        pointPaging.setAll(res, resetPage);
        renderPointPage();
    }

    private void renderPointPage() {
        pagePoints = (List<PointDTO>) (List<?>) pointPaging.slice();
        pointModel.setRowCount(0);
        for (PointDTO p : pagePoints) {
            UserDTO acc = pointAccountMap.get(p.getUserId());
            pointModel.addRow(new Object[]{
                    p.getUserId(),
                    acc != null && acc.getFullName() != null ? acc.getFullName() : "---",
                    acc != null && acc.getPhone() != null ? acc.getPhone() : "---",
                    INT_FMT.format(p.getCurrentPoints()),
                    INT_FMT.format(p.getTotalEarnedPoints()),
                    tierLabel(p.getTier()),
                    p.getUpdatedAt() == null ? "---" : p.getUpdatedAt().format(TIME_FMT),
                    "Lịch sử"
            });
        }
        pointPaging.refreshBar();
    }

    private void openPointHistoryDialog(PointDTO point) {
        UserDTO acc = pointAccountMap.get(point.getUserId());
        String who = acc != null && acc.getFullName() != null ? acc.getFullName() : "Thành viên #" + point.getUserId();

        new PageLoadWorker() {
            @Override
            protected Object doInBackground() {
                return adminBUS.getPointHistory(point.getUserId());
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void onLoaded(Object result) {
                DefaultTableModel model = readOnlyModel("Thời gian", "Loại", "Điểm", "Hóa đơn", "Ghi chú");
                for (PointTransactionDTO t : (List<PointTransactionDTO>) copyOrEmpty((List<PointTransactionDTO>) result)) {
                    int change = t.getPointsChange();
                    model.addRow(new Object[]{
                            t.getCreatedAt() == null ? "---" : t.getCreatedAt().format(TIME_FMT),
                            t.getType() == null ? "---" : t.getType().name(),
                            (change > 0 ? "+" : "") + INT_FMT.format(change),
                            t.getBillId() == null ? "---" : "#" + t.getBillId(),
                            t.getNote() == null ? "" : t.getNote()
                    });
                }
                CTable tableComponent = createStyledTable(model, 40);
                setColumnWidths(tableComponent.getTable(), 150, 80, 80, 80, 260);
                tableComponent.setPreferredSize(new Dimension(660, 320));

                JPanel form = newFormPanel();
                form.add(tableComponent);
                showModal(
                        "Lịch sử điểm: " + who,
                        "Hiện có " + INT_FMT.format(point.getCurrentPoints()) + " điểm, hạng " + tierLabel(point.getTier()) + ".",
                        form,
                        "Đóng",
                        Utils.FOREST_BG,
                        () -> null
                );
            }
        }.execute();
    }

    // ==========================================================
    // --- TRANG 5: QUẢN LÝ VOUCHER ---
    // ==========================================================
    private JPanel createVoucherPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        kpiVoucherTotal = kpiLabel("0");
        kpiVoucherActive = kpiLabel("0");
        kpiVoucherExpired = kpiLabel("0");
        kpiVoucherUsed = kpiLabel("0");
        JPanel kpis = kpiRow(4);
        kpis.add(createKpiCard("TỔNG MÃ VOUCHER", kpiVoucherTotal, Utils.FOREST_BG, NavIcon.PERCENT));
        kpis.add(createKpiCard("ĐANG ÁP DỤNG", kpiVoucherActive, Utils.SAFE_GREEN, NavIcon.CHECK));
        kpis.add(createKpiCard("HẾT HẠN / TẠM NGƯNG", kpiVoucherExpired, Utils.DANGER_RED, NavIcon.PAUSE));
        kpis.add(createKpiCard("TỔNG LƯỢT ĐÃ DÙNG", kpiVoucherUsed, Utils.GOLD_ACCENT, NavIcon.RECEIPT));
        page.add(kpis, BorderLayout.NORTH);

        txtVoucherSearch = createSearchField("Tìm theo mã code hoặc tên chương trình...", 310);
        cboVoucherType = createFilterCombo("Loại giảm", "Tất cả loại", "Giảm %", "Giảm tiền cố định");
        cboVoucherStatus = createFilterCombo("Trạng thái", "Tất cả trạng thái",
                V_ACTIVE, V_UPCOMING, V_EXPIRED, V_PAUSED);

        onTextChange(txtVoucherSearch, () -> applyVoucherFilter(true));
        cboVoucherType.addActionListener(e -> { if (!adjusting) applyVoucherFilter(true); });
        cboVoucherStatus.addActionListener(e -> { if (!adjusting) applyVoucherFilter(true); });

        JButton btnAdd = createRoundedButton("+  Tạo voucher mới", Utils.TERRACOTTA, Color.WHITE, 175, 40);
        btnAdd.addActionListener(e -> openVoucherDialog(null));

        voucherModel = readOnlyModel(
                "Mã Code", "Tên chương trình", "Mức giảm", "Đơn tối thiểu",
                "Lượt dùng", "Hiệu lực", "Trạng thái", "Thao tác"
        );
        CTable voucherTableComponent = createStyledTable(voucherModel, 50);
        JTable voucherTable = voucherTableComponent.getTable();
        voucherTable.getColumnModel().getColumn(0).setCellRenderer(new RowRenderer(true));
        voucherTable.getColumnModel().getColumn(2).setCellRenderer(new RowRenderer(true));
        voucherTable.getColumnModel().getColumn(6).setCellRenderer(new BadgeRenderer());

        installActionColumn(voucherTable, 7, (row, idx) -> {
            if (row < 0 || row >= pageVouchers.size()) return;
            VoucherDTO v = pageVouchers.get(row);
            if (idx == 0) openVoucherDialog(v);
            else toggleVoucherStatus(v);
        });
        setColumnWidths(voucherTable, 125, 200, 145, 125, 105, 195, 145, 175);

        voucherPaging = new Paging(10, this::renderVoucherPage);
        page.add(createTableCard(
                createToolbar(flow(FlowLayout.LEFT, txtVoucherSearch, cboVoucherType, cboVoucherStatus), flow(FlowLayout.RIGHT, btnAdd)),
                voucherTableComponent,
                voucherPaging.bar
        ), BorderLayout.CENTER);
        return page;
    }

    private void loadVouchers() {
        new PageLoadWorker() {
            @Override
            protected Object doInBackground() {
                return adminBUS.getAllVouchers();
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void onLoaded(Object result) {
                allVouchers = copyOrEmpty((List<VoucherDTO>) result);
                applyVoucherFilter(false);
            }
        }.execute();
    }

    private static String voucherStatusLabel(VoucherDTO v) {
        if (!Boolean.TRUE.equals(v.getIsActive())) return V_PAUSED;
        LocalDateTime now = LocalDateTime.now();
        if (v.getStartAt() != null && now.isBefore(v.getStartAt())) return V_UPCOMING;
        if (v.getEndAt() != null && now.isAfter(v.getEndAt())) return V_EXPIRED;
        int used = v.getUsedCount() == null ? 0 : v.getUsedCount();
        if (v.getUsageLimit() != null && used >= v.getUsageLimit()) return V_EXPIRED;
        return V_ACTIVE;
    }

    private void applyVoucherFilter(boolean resetPage) {
        int activeCount = 0;
        long totalUsed = 0;
        for (VoucherDTO v : allVouchers) {
            if (V_ACTIVE.equals(voucherStatusLabel(v))) activeCount++;
            totalUsed += (v.getUsedCount() == null ? 0 : v.getUsedCount());
        }
        kpiVoucherTotal.setText(String.valueOf(allVouchers.size()));
        kpiVoucherActive.setText(String.valueOf(activeCount));
        kpiVoucherExpired.setText(String.valueOf(allVouchers.size() - activeCount));
        kpiVoucherUsed.setText(INT_FMT.format(totalUsed));

        String q = txtVoucherSearch.getText().trim().toLowerCase(Locale.ROOT);
        String type = (String) cboVoucherType.getSelectedItem();
        String status = (String) cboVoucherStatus.getSelectedItem();

        List<VoucherDTO> res = new ArrayList<>();
        for (VoucherDTO v : allVouchers) {
            String code = v.getCode() == null ? "" : v.getCode().toLowerCase(Locale.ROOT);
            String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !code.contains(q) && !name.contains(q)) continue;
            if ("Giảm %".equals(type) && v.getDiscountType() != dbEnums.DiscountType.PERCENT) continue;
            if ("Giảm tiền cố định".equals(type) && v.getDiscountType() != dbEnums.DiscountType.FIXED) continue;
            if (status != null && !status.startsWith("Tất cả") && !status.equals(voucherStatusLabel(v))) continue;
            res.add(v);
        }
        voucherPaging.setAll(res, resetPage);
        renderVoucherPage();
    }

    private void renderVoucherPage() {
        pageVouchers = (List<VoucherDTO>) (List<?>) voucherPaging.slice();
        voucherModel.setRowCount(0);
        for (VoucherDTO v : pageVouchers) {
            String discountStr;
            if (v.getDiscountType() == dbEnums.DiscountType.PERCENT) {
                discountStr = v.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
                if (v.getMaxDiscountAmount() != null && v.getMaxDiscountAmount().signum() > 0) {
                    discountStr += " (tối đa " + SALES_MONEY_FMT.format(v.getMaxDiscountAmount()) + ")";
                }
            } else {
                discountStr = SALES_MONEY_FMT.format(v.getDiscountValue());
            }

            int used = v.getUsedCount() == null ? 0 : v.getUsedCount();
            String usageStr = v.getUsageLimit() == null
                    ? INT_FMT.format(used) + " / ∞"
                    : INT_FMT.format(used) + " / " + INT_FMT.format(v.getUsageLimit());

            String period = (v.getStartAt() != null ? v.getStartAt().format(INPUT_DT_FMT) : "---")
                    + " → "
                    + (v.getEndAt() != null ? v.getEndAt().format(INPUT_DT_FMT) : "---");

            boolean active = Boolean.TRUE.equals(v.getIsActive());
            voucherModel.addRow(new Object[]{
                    v.getCode(),
                    v.getName() == null || v.getName().isBlank() ? "---" : v.getName(),
                    discountStr,
                    v.getMinOrderAmount() != null ? SALES_MONEY_FMT.format(v.getMinOrderAmount()) : "0 VNĐ",
                    usageStr,
                    period,
                    voucherStatusLabel(v),
                    active ? "Sửa|Tạm ngưng" : "Sửa|Kích hoạt"
            });
        }
        voucherPaging.refreshBar();
    }

    private void openVoucherDialog(VoucherDTO existing) {
        boolean edit = existing != null;
        LocalDateTime defaultStart = LocalDateTime.now().withSecond(0).withNano(0);
        LocalDateTime defaultEnd = defaultStart.plusDays(7);

        JTextField txtCode = createStyledTextField(edit ? existing.getCode() : "");
        JTextField txtName = createStyledTextField(edit && existing.getName() != null ? existing.getName() : "");
        JTextField txtDesc = createStyledTextField(edit && existing.getDescription() != null ? existing.getDescription() : "");

        JComboBox<String> cboType = new JComboBox<>(new String[]{"Giảm theo phần trăm (%)", "Giảm tiền cố định (VNĐ)"});
        styleCombo(cboType);
        if (edit && existing.getDiscountType() == dbEnums.DiscountType.FIXED) {
            cboType.setSelectedIndex(1);
        }

        JTextField txtValue = createStyledTextField(edit && existing.getDiscountValue() != null
                ? existing.getDiscountValue().stripTrailingZeros().toPlainString() : "");
        JTextField txtMinOrder = createStyledTextField(edit && existing.getMinOrderAmount() != null
                ? existing.getMinOrderAmount().toBigInteger().toString() : "0");
        JTextField txtMaxDiscount = createStyledTextField(edit && existing.getMaxDiscountAmount() != null
                ? existing.getMaxDiscountAmount().toBigInteger().toString() : "");
        JTextField txtLimit = createStyledTextField(edit && existing.getUsageLimit() != null
                ? String.valueOf(existing.getUsageLimit()) : "");
        JTextField txtStart = createStyledTextField(edit && existing.getStartAt() != null
                ? existing.getStartAt().format(INPUT_DT_FMT) : defaultStart.format(INPUT_DT_FMT));
        JTextField txtEnd = createStyledTextField(edit && existing.getEndAt() != null
                ? existing.getEndAt().format(INPUT_DT_FMT) : defaultEnd.format(INPUT_DT_FMT));

        JCheckBox chkActive = createCheckBox("Kích hoạt voucher ngay sau khi lưu",
                !edit || Boolean.TRUE.equals(existing.getIsActive()));
        if (edit) txtCode.setEnabled(false);

        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 0));
        grid.setOpaque(false);

        JPanel col1 = newFormPanel();
        addField(col1, "Mã Voucher (VD: ANNHIEN20)", txtCode);
        addField(col1, "Loại giảm giá", cboType);
        addField(col1, "Đơn tối thiểu (VNĐ)", txtMinOrder);
        addField(col1, "Bắt đầu (dd/MM/yyyy HH:mm)", txtStart);
        addField(col1, "Mô tả ngắn", txtDesc);

        JPanel col2 = newFormPanel();
        addField(col2, "Tên chương trình", txtName);
        addField(col2, "Giá trị giảm (% hoặc VNĐ)", txtValue);
        addField(col2, "Giảm tối đa (VNĐ - bỏ trống nếu không giới hạn)", txtMaxDiscount);
        addField(col2, "Kết thúc (dd/MM/yyyy HH:mm)", txtEnd);
        addField(col2, "Giới hạn lượt dùng (bỏ trống = không giới hạn)", txtLimit);

        grid.add(col1);
        grid.add(col2);

        JPanel wrapper = newFormPanel();
        wrapper.add(grid);
        wrapper.add(chkActive);

        showModal(
                edit ? "Cập nhật Voucher" : "Tạo Voucher mới",
                edit ? "Chỉnh sửa thông tin khuyến mãi cho mã " + existing.getCode() : "Thiết lập mã giảm giá mới cho khách hàng.",
                wrapper,
                edit ? "Lưu thay đổi" : "Tạo voucher",
                Utils.TERRACOTTA,
                () -> {
                    String code = txtCode.getText().trim().toUpperCase(Locale.ROOT);
                    if (code.isEmpty()) return "Vui lòng nhập mã Voucher.";
                    if (!code.matches("[A-Z0-9_-]{3,50}")) {
                        return "Mã Voucher chỉ gồm chữ in hoa, số, gạch ngang/dưới (3–50 ký tự).";
                    }
                    String programName = txtName.getText().trim();
                    if (programName.isEmpty()) return "Vui lòng nhập tên chương trình.";

                    dbEnums.DiscountType type = cboType.getSelectedIndex() == 0
                            ? dbEnums.DiscountType.PERCENT : dbEnums.DiscountType.FIXED;

                    BigDecimal val, minOrder, maxDiscount = null;
                    try {
                        val = new BigDecimal(txtValue.getText().trim().replaceAll("[,\\s]", ""));
                    } catch (NumberFormatException ex) {
                        return "Giá trị giảm không hợp lệ.";
                    }
                    if (val.signum() <= 0) return "Giá trị giảm phải lớn hơn 0.";
                    if (type == dbEnums.DiscountType.PERCENT && val.compareTo(BigDecimal.valueOf(100)) > 0) {
                        return "Giảm theo phần trăm không được vượt quá 100%.";
                    }

                    try {
                        String minStr = txtMinOrder.getText().trim().replaceAll("[,\\s]", "");
                        minOrder = minStr.isEmpty() ? BigDecimal.ZERO : new BigDecimal(minStr);
                        if (minOrder.signum() < 0) return "Đơn tối thiểu không được âm.";
                    } catch (NumberFormatException ex) {
                        return "Đơn tối thiểu không hợp lệ.";
                    }

                    String maxStr = txtMaxDiscount.getText().trim().replaceAll("[,\\s]", "");
                    if (!maxStr.isEmpty()) {
                        try {
                            maxDiscount = new BigDecimal(maxStr);
                            if (maxDiscount.signum() <= 0) return "Mức giảm tối đa phải lớn hơn 0.";
                        } catch (NumberFormatException ex) {
                            return "Mức giảm tối đa không hợp lệ.";
                        }
                    }

                    Integer limit = null;
                    String limitStr = txtLimit.getText().trim();
                    if (!limitStr.isEmpty()) {
                        try {
                            limit = Integer.parseInt(limitStr);
                            if (limit <= 0) return "Giới hạn lượt dùng phải lớn hơn 0.";
                        } catch (NumberFormatException ex) {
                            return "Giới hạn lượt dùng phải là số nguyên.";
                        }
                    }

                    LocalDateTime startAt, endAt;
                    try {
                        startAt = LocalDateTime.parse(txtStart.getText().trim(), INPUT_DT_FMT);
                        endAt = LocalDateTime.parse(txtEnd.getText().trim(), INPUT_DT_FMT);
                    } catch (Exception ex) {
                        return "Định dạng ngày giờ phải là dd/MM/yyyy HH:mm (VD: 25/11/2026 18:30).";
                    }
                    if (!endAt.isAfter(startAt)) return "Thời gian kết thúc phải sau thời gian bắt đầu.";

                    VoucherDTO dto = new VoucherDTO();
                    if (edit) dto.setId(existing.getId());
                    dto.setCode(code);
                    dto.setName(programName);
                    dto.setDescription(txtDesc.getText().trim());
                    dto.setDiscountType(type);
                    dto.setDiscountValue(val);
                    dto.setMinOrderAmount(minOrder);
                    dto.setMaxDiscountAmount(maxDiscount);
                    dto.setStartAt(startAt);
                    dto.setEndAt(endAt);
                    dto.setUsageLimit(limit);
                    dto.setIsActive(chkActive.isSelected());

                    performUpdate(
                            edit ? "Cập nhật voucher" : "Tạo voucher",
                            () -> edit ? adminBUS.updateVoucher(dto) : adminBUS.addNewVoucher(dto),
                            this::loadVouchers
                    );
                    return null;
                }
        );
    }

    private void toggleVoucherStatus(VoucherDTO v) {
        boolean active = Boolean.TRUE.equals(v.getIsActive());
        String title = active ? "Tạm ngưng voucher" : "Kích hoạt voucher";
        String msg = active
                ? "Tạm ngưng áp dụng mã '" + v.getCode() + "'?\nKhách hàng sẽ không thể áp mã này khi thanh toán."
                : "Kích hoạt lại mã '" + v.getCode() + "'?";
        if (!confirmAction(title, msg)) return;

        performUpdate(title, () -> adminBUS.setVoucherActive(v.getId(), !active), this::loadVouchers);
    }

    // ==========================================================
    // --- UI HELPERS: CARD, KPI, TOOLBAR, MODAL, FORM ---
    // ==========================================================
    private JPanel createCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(Utils.BORDER_SOFT);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        return card;
    }

    private JPanel kpiRow(int cols) {
        JPanel row = new JPanel(new GridLayout(1, cols, 18, 0));
        row.setOpaque(false);
        row.setPreferredSize(new Dimension(0, 98));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 98));
        return row;
    }

    private JLabel kpiLabel(String init) {
        JLabel l = new JLabel(init);
        l.setFont(font(Font.BOLD, 24));
        l.setForeground(Utils.TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel createKpiCard(String title, JLabel value, Color accent, int iconType) {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(12, 0));
        card.setBorder(new EmptyBorder(16, 20, 16, 18));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(font(Font.BOLD, 11));
        lblTitle.setForeground(Utils.TEXT_MUTED);
        lblTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        text.add(Box.createVerticalGlue());
        text.add(lblTitle);
        text.add(Box.createVerticalStrut(6));
        text.add(value);
        text.add(Box.createVerticalGlue());

        card.add(text, BorderLayout.CENTER);
        card.add(new IconChip(iconType, accent), BorderLayout.EAST);
        return card;
    }

    private JPanel flow(int align, JComponent... comps) {
        JPanel p = new JPanel(new FlowLayout(align, 10, 0));
        p.setOpaque(false);
        for (JComponent c : comps) p.add(c);
        return p;
    }

    private JPanel createToolbar(JComponent left, JComponent right) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        if (left != null) bar.add(left, BorderLayout.WEST);
        if (right != null) bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel createTableCard(JComponent toolbar, CTable tableComponent, JComponent footer) {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 14));
        if (toolbar != null) card.add(toolbar, BorderLayout.NORTH);
        tableComponent.setBackground(Color.WHITE);
        tableComponent.setBorder(new LineBorder(Utils.BORDER_SOFT, 1));
        card.add(tableComponent, BorderLayout.CENTER);
        if (footer != null) card.add(footer, BorderLayout.SOUTH);
        return card;
    }

    private DefaultTableModel readOnlyModel(String... cols) {
        return new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    private void setColumnWidths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private JTextField createSearchField(String placeholder, int width) {
        return new CTextField(placeholder, width, 40, font(Font.PLAIN, 13));
    }

    private JCheckBox createCheckBox(String text, boolean selected) {
        JCheckBox cb = new JCheckBox(text, selected);
        cb.setIcon(new CCheckBox(false));
        cb.setSelectedIcon(new CCheckBox(true));
        cb.setIconTextGap(9);
        cb.setFont(font(Font.PLAIN, 13));
        cb.setForeground(Utils.TEXT_DARK);
        cb.setOpaque(false);
        cb.setFocusPainted(false);
        return cb;
    }

    private void updateComboItems(JComboBox<String> combo, String allLabel, List<String> options) {
        Object selected = combo.getSelectedItem();
        boolean previousAdjusting = adjusting;
        adjusting = true;
        try {
            combo.removeAllItems();
            combo.addItem(allLabel);
            for (String option : options) combo.addItem(option);
            boolean found = false;
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (Objects.equals(combo.getItemAt(i), selected)) {
                    found = true;
                    break;
                }
            }
            combo.setSelectedItem(found ? selected : allLabel);
        } finally {
            adjusting = previousAdjusting;
        }
    }

    private JComboBox<String> createFilterCombo(String... items) {
        boolean hasPrefix = items.length > 1 && items[1].startsWith("Tất cả");
        String prefix = hasPrefix ? items[0] : null;
        String[] options = hasPrefix ? Arrays.copyOfRange(items, 1, items.length) : items;

        JComboBox<String> c = new JComboBox<>(options);
        c.setFont(font(Font.PLAIN, 13));
        c.setBackground(Color.WHITE);
        c.setPreferredSize(new Dimension(hasPrefix ? 210 : 180, 40));
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        c.setOpaque(false);
        c.setUI(new FilterComboUI());
        c.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean sel, boolean focus) {
                JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, sel, false);
                l.setFont(font(Font.PLAIN, 13));
                l.setBorder(new EmptyBorder(0, 14, 0, 10));
                l.setPreferredSize(new Dimension(l.getPreferredSize().width, 34));
                if (index < 0) {
                    String v = String.valueOf(value);
                    boolean isAll = c.getSelectedIndex() <= 0;
                    if (prefix != null) l.setText(prefix + ":  " + (isAll ? "Tất cả" : v));
                    l.setForeground(isAll ? Utils.TEXT_MUTED : Utils.TEXT_DARK);
                    l.setOpaque(false);
                } else {
                    l.setOpaque(true);
                    l.setBackground(sel ? Utils.SELECT_BG : Color.WHITE);
                    l.setForeground(Utils.TEXT_DARK);
                }
                return l;
            }
        });
        return c;
    }

    private void styleCombo(JComboBox<?> c) {
        c.setFont(font(Font.PLAIN, 14));
        c.setBackground(Color.WHITE);
        c.setPreferredSize(new Dimension(380, 42));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void onTextChange(JTextField f, Runnable r) {
        f.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { r.run(); }
            @Override public void removeUpdate(DocumentEvent e) { r.run(); }
            @Override public void changedUpdate(DocumentEvent e) { r.run(); }
        });
    }

    private JTextField createStyledTextField(String text) {
        CTextField field = new CTextField("", 380, 42, font(Font.PLAIN, 14));
        field.setText(text == null ? "" : text);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        return field;
    }

    private JButton createRoundedButton(String text, Color bg, Color fg, int w, int h) {
        if (Utils.TERRACOTTA.equals(bg) && Color.WHITE.equals(fg)) {
            return new CButton(text, w, h, font(Font.BOLD, 13));
        }
        boolean neutral = Utils.NEUTRAL_BTN.equals(bg);
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c;
                if (!isEnabled()) c = new Color(232, 235, 233);
                else if (getModel().isPressed()) c = bg.darker();
                else if (getModel().isRollover()) c = neutral ? new Color(228, 232, 228) : bg.brighter();
                else c = bg;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                if (neutral) {
                    g2.setColor(new Color(214, 219, 214));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(font(Font.BOLD, 13));
        btn.setForeground(fg);
        btn.setPreferredSize(new Dimension(w, h));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel newFormPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private void addField(JPanel form, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(font(Font.BOLD, 13));
        l.setForeground(Utils.TEXT_DARK);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(l);
        form.add(Box.createVerticalStrut(6));
        form.add(field);
        form.add(Box.createVerticalStrut(14));
    }

    private boolean confirmAction(String title, String message) {
        JDialog dialog = new JDialog(this, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(Color.WHITE);
        content.setBorder(new EmptyBorder(24, 26, 20, 26));

        JLabel heading = new JLabel(title);
        heading.setFont(font(Font.BOLD, 18));
        heading.setForeground(Utils.TEXT_DARK);
        content.add(heading, BorderLayout.NORTH);

        JTextArea detail = new JTextArea(message, 3, 45);
        detail.setFont(font(Font.PLAIN, 13));
        detail.setForeground(Utils.TEXT_DARK);
        detail.setBackground(Color.WHITE);
        detail.setEditable(false);
        detail.setFocusable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        content.add(detail, BorderLayout.CENTER);

        JButton cancel = createRoundedButton("Hủy", Utils.NEUTRAL_BTN, Utils.TEXT_DARK, 100, 40);
        JButton confirm = new CButton("Xác nhận", 132, 40, font(Font.BOLD, 13));
        boolean[] accepted = {false};
        cancel.addActionListener(e -> dialog.dispose());
        confirm.addActionListener(e -> {
            accepted[0] = true;
            dialog.dispose();
        });
        content.add(flow(FlowLayout.RIGHT, cancel, confirm), BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(confirm);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return accepted[0];
    }

    private void showModal(String title, String desc, JPanel form, String okText, Color okColor, Supplier<String> onSubmit) {
        CDialog dialog = new CDialog(this, "AN NHIÊN  /  QUẢN TRỊ HỆ THỐNG", title, desc);

        JLabel err = new JLabel(" ");
        err.setFont(font(Font.PLAIN, 12));
        err.setForeground(Utils.DANGER_RED);
        err.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(form, BorderLayout.CENTER);
        body.add(err, BorderLayout.SOUTH);

        CButton cancel = CDialog.createSecondaryButton("Hủy", 110, 42);
        cancel.addActionListener(e -> dialog.dispose());

        JButton ok = createRoundedButton(okText, okColor, Color.WHITE, 165, 42);
        ok.addActionListener(e -> {
            String msg;
            try {
                msg = onSubmit.get();
            } catch (RuntimeException ex) {
                msg = "Lỗi: " + ex.getMessage();
            }
            if (msg == null) {
                dialog.dispose();
            } else {
                err.setText(msg);
                dialog.pack();
            }
        });

        dialog.setBody(body)
                .setFooterButtons(ok, cancel, ok)
                .setMinSize(520, 320)
                .showModal();
    }

    // ==========================================================
    // --- BẢNG & RENDERER ---
    // ==========================================================
    private CTable createStyledTable(DefaultTableModel model, int rowHeight) {
        CTable tableComponent = new CTable();
        JTable table = tableComponent.getTable();
        table.setModel(model);
        table.setRowHeight(rowHeight);
        table.setFont(font(Font.PLAIN, 13));
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(238, 240, 238));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setSelectionBackground(Utils.SELECT_BG);
        table.setSelectionForeground(Utils.TEXT_DARK);
        table.setFillsViewportHeight(true);
        table.setDefaultRenderer(Object.class, new RowRenderer(false));

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(0, 42));
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, v, false, false, r, c);
                l.setOpaque(true);
                l.setBackground(new Color(240, 243, 240));
                l.setForeground(Utils.FOREST_BG);
                l.setFont(font(Font.BOLD, 12));
                l.setHorizontalAlignment(SwingConstants.LEFT);
                l.setBorder(BorderFactory.createCompoundBorder(
                        new MatteBorder(0, 0, 1, 0, Utils.BORDER_SOFT),
                        new EmptyBorder(0, 12, 0, 8)
                ));
                return l;
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                Object old = table.getClientProperty("hoverRow");
                if (!(old instanceof Integer) || (Integer) old != r) {
                    table.putClientProperty("hoverRow", r);
                    table.repaint();
                }
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                table.putClientProperty("hoverRow", -1);
                table.repaint();
            }
        });
        return tableComponent;
    }

    @SuppressWarnings("unchecked")
    private static Color rowBackground(JTable t, boolean selected, int row) {
        if (selected) return Utils.SELECT_BG;
        Object tint = t.getClientProperty("rowTint");
        if (tint instanceof IntFunction) {
            Color c = ((IntFunction<Color>) tint).apply(row);
            if (c != null) return c;
        }
        Object h = t.getClientProperty("hoverRow");
        if (h instanceof Integer && (Integer) h == row) return Utils.HOVER_ROW;
        return Color.WHITE;
    }

    private static Color[] badgeColors(String text) {
        return switch (text) {
            case "Đang mở bán", "Đủ hàng", ST_ACTIVE, V_ACTIVE ->
                    new Color[]{new Color(226, 240, 232), Utils.SAFE_GREEN};
            case "Sắp hết", ST_LOCKED, V_EXPIRED, "Tạm ngưng" ->
                    new Color[]{new Color(252, 230, 230), Utils.DANGER_RED};
            case V_UPCOMING, TIER_KIM_CUONG ->
                    new Color[]{new Color(224, 240, 250), new Color(30, 110, 170)};
            case ROLE_STAFF ->
                    new Color[]{new Color(222, 232, 226), Utils.FOREST_BG};
            case ROLE_KITCHEN ->
                    new Color[]{new Color(253, 237, 232), Utils.TERRACOTTA};
            case ROLE_ADMIN ->
                    new Color[]{new Color(235, 230, 250), new Color(102, 51, 153)};
            case TIER_BAC ->
                    new Color[]{new Color(236, 239, 241), new Color(96, 110, 120)};
            case TIER_VANG ->
                    new Color[]{new Color(253, 243, 214), new Color(166, 118, 20)};
            default ->
                    new Color[]{new Color(250, 238, 224), new Color(150, 95, 40)};
        };
    }

    private static Color actionColor(String label) {
        return switch (label) {
            case "Xóa", "Khóa" -> Utils.DANGER_RED;
            case "Mở khóa", "Mở bán", "Kích hoạt" -> Utils.SAFE_GREEN;
            case "Tạm ngưng" -> new Color(176, 112, 40);
            default -> Utils.TERRACOTTA;
        };
    }

    private static Color withAlpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    private static int actionIndexAt(JTable table, int r, int c, int mouseX) {
        String[] labels = String.valueOf(table.getValueAt(r, c)).split("\\|");
        FontMetrics fm = table.getFontMetrics(ACTION_FONT);
        int x = table.getCellRect(r, c, false).x + ACTION_PAD;
        for (int i = 0; i < labels.length; i++) {
            int w = fm.stringWidth(labels[i]) + ACTION_PAD_X * 2;
            if (mouseX >= x && mouseX <= x + w) return i;
            x += w + ACTION_GAP;
        }
        return -1;
    }

    private void installActionColumn(JTable table, int col, BiConsumer<Integer, Integer> handler) {
        table.getColumnModel().getColumn(col).setCellRenderer(new ActionRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                int c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c < 0 || table.convertColumnIndexToModel(c) != col) return;
                int idx = actionIndexAt(table, r, c, e.getX());
                if (idx >= 0) handler.accept(table.convertRowIndexToModel(r), idx);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                table.putClientProperty("hoverAction", null);
                table.repaint();
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                int c = table.columnAtPoint(e.getPoint());
                boolean inCol = r >= 0 && c >= 0 && table.convertColumnIndexToModel(c) == col;
                int idx = inCol ? actionIndexAt(table, r, c, e.getX()) : -1;
                table.setCursor(Cursor.getPredefinedCursor(idx >= 0 ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                Object old = table.getClientProperty("hoverAction");
                int[] now = idx >= 0 ? new int[]{r, c, idx} : null;
                boolean same = (old == null && now == null)
                        || (old instanceof int[] && now != null && Arrays.equals((int[]) old, now));
                if (!same && (now != null || old != null)) {
                    table.putClientProperty("hoverAction", now);
                    table.repaint();
                }
            }
        });
    }

    private static class RowRenderer extends DefaultTableCellRenderer {
        private final boolean bold;

        RowRenderer(boolean bold) {
            this.bold = bold;
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, false, r, c);
            setBorder(new EmptyBorder(0, 12, 0, 8));
            setFont(font(bold ? Font.BOLD : Font.PLAIN, 13));
            setForeground(Utils.TEXT_DARK);
            setBackground(rowBackground(t, sel, r));
            setHorizontalAlignment(SwingConstants.LEFT);
            return this;
        }
    }

    private static class BadgeRenderer extends JPanel implements TableCellRenderer {
        private String text = "";
        private Color rowBg = Color.WHITE;

        BadgeRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            text = String.valueOf(v);
            rowBg = rowBackground(t, sel, r);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(rowBg);
            g2.fillRect(0, 0, getWidth(), getHeight());
            Color[] col = badgeColors(text);
            g2.setFont(font(Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int w = fm.stringWidth(text) + 24, h = 24;
            int x = 10, y = (getHeight() - h) / 2;
            g2.setColor(col[0]);
            g2.fillRoundRect(x, y, w, h, h, h);
            g2.setColor(col[1]);
            g2.drawString(text, x + 12, y + (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    private static class ThumbRenderer extends JPanel implements TableCellRenderer {
        private String name = "";
        private String imageKey = null;
        private Color rowBg = Color.WHITE;

        ThumbRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            if (v instanceof FoodDTO f) {
                name = f.getName() == null ? "" : f.getName();
                imageKey = f.getCoverImageUrl();
            } else {
                name = v == null ? "" : v.toString();
                imageKey = null;
            }
            rowBg = rowBackground(t, sel, r);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(rowBg);
            g2.fillRect(0, 0, getWidth(), getHeight());

            int size = 36, x = 14, y = (getHeight() - size) / 2;
            ImageIcon cached = (imageKey != null) ? FOOD_THUMB_CACHE.get(imageKey) : null;
            if (cached != null) {
                cached.paintIcon(this, g2, x, y);
            } else {
                // Fallback khi chưa có ảnh hoặc đang tải
                Color[] palette = {Utils.FOREST_BG, Utils.TERRACOTTA, Utils.GOLD_ACCENT, Utils.SAFE_GREEN};
                g2.setColor(palette[Math.floorMod(name.hashCode(), palette.length)]);
                g2.fillRoundRect(x, y, size, size, 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(font(Font.BOLD, 15));
                String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(ch, x + (size - fm.stringWidth(ch)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.dispose();
        }
    }

    private static class ActionRenderer extends JPanel implements TableCellRenderer {
        private String labels = "";
        private Color cellBg = Color.WHITE;
        private int hoverIdx = -1;

        ActionRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused, int row, int column) {
            labels = value == null ? "" : value.toString();
            cellBg = rowBackground(table, selected, row);
            Object h = table.getClientProperty("hoverAction");
            hoverIdx = (h instanceof int[] && ((int[]) h)[0] == row && ((int[]) h)[1] == column) ? ((int[]) h)[2] : -1;
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(cellBg);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(ACTION_FONT);
                FontMetrics fm = g.getFontMetrics();
                int x = ACTION_PAD;
                int y = (getHeight() - ACTION_H) / 2;
                String[] parts = labels.split("\\|");
                for (int i = 0; i < parts.length; i++) {
                    String label = parts[i];
                    Color col = actionColor(label);
                    int w = fm.stringWidth(label) + ACTION_PAD_X * 2;
                    boolean hot = i == hoverIdx;
                    g.setColor(hot ? col : withAlpha(col, 28));
                    g.fillRoundRect(x, y, w, ACTION_H, 10, 10);
                    g.setColor(hot ? col : withAlpha(col, 90));
                    g.drawRoundRect(x, y, w - 1, ACTION_H - 1, 10, 10);
                    g.setColor(hot ? Color.WHITE : col);
                    g.drawString(label, x + ACTION_PAD_X, y + (ACTION_H + fm.getAscent() - fm.getDescent()) / 2);
                    x += w + ACTION_GAP;
                }
            } finally {
                g.dispose();
            }
        }
    }

    private static class FilterComboUI extends javax.swing.plaf.basic.BasicComboBoxUI {
        @Override
        protected JButton createArrowButton() {
            JButton b = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(Utils.TEXT_MUTED);
                    g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int cx = getWidth() / 2, cy = getHeight() / 2;
                    g2.drawLine(cx - 4, cy - 2, cx, cy + 2);
                    g2.drawLine(cx, cy + 2, cx + 4, cy - 2);
                    g2.dispose();
                }
            };
            b.setBorder(BorderFactory.createEmptyBorder());
            b.setContentAreaFilled(false);
            b.setFocusPainted(false);
            b.setOpaque(false);
            b.setPreferredSize(new Dimension(30, 30));
            return b;
        }

        @Override
        public void paint(Graphics g, JComponent c) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean hot = comboBox.isPopupVisible() || comboBox.hasFocus();
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 12, 12);
            g2.setColor(hot ? Utils.TERRACOTTA : new Color(214, 219, 214));
            g2.drawRoundRect(0, 0, c.getWidth() - 1, c.getHeight() - 1, 12, 12);
            g2.dispose();
            super.paint(g, c);
        }

        @Override
        public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {}

        @Override
        protected javax.swing.plaf.basic.ComboPopup createPopup() {
            javax.swing.plaf.basic.BasicComboPopup p = new javax.swing.plaf.basic.BasicComboPopup(comboBox);
            p.setBorder(new LineBorder(new Color(214, 219, 214), 1));
            return p;
        }
    }

    // ==========================================================
    // --- PHÂN TRANG ---
    // ==========================================================
    private class Paging {
        final int pageSize;
        final Runnable onChange;
        List<Object> all = new ArrayList<>();
        int page = 0;
        final JPanel bar = new JPanel(new BorderLayout());
        final JLabel info = new JLabel(), pageLbl = new JLabel();
        final JButton prev = createRoundedButton("‹ Trước", Utils.NEUTRAL_BTN, Utils.TEXT_DARK, 92, 34);
        final JButton next = createRoundedButton("Sau ›", Utils.NEUTRAL_BTN, Utils.TEXT_DARK, 92, 34);

        Paging(int pageSize, Runnable onChange) {
            this.pageSize = pageSize;
            this.onChange = onChange;
            bar.setOpaque(false);
            info.setFont(font(Font.PLAIN, 12));
            info.setForeground(Utils.TEXT_MUTED);
            pageLbl.setFont(font(Font.BOLD, 12));
            pageLbl.setForeground(Utils.TEXT_DARK);
            prev.addActionListener(e -> {
                if (page > 0) {
                    page--;
                    this.onChange.run();
                }
            });
            next.addActionListener(e -> {
                if (page < totalPages() - 1) {
                    page++;
                    this.onChange.run();
                }
            });
            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            right.setOpaque(false);
            right.add(prev);
            right.add(pageLbl);
            right.add(next);
            bar.add(info, BorderLayout.WEST);
            bar.add(right, BorderLayout.EAST);
        }

        int totalPages() {
            return Math.max(1, (all.size() + pageSize - 1) / pageSize);
        }

        @SuppressWarnings("unchecked")
        void setAll(List<?> items, boolean reset) {
            all = (List<Object>) (List<?>) items;
            page = reset ? 0 : Math.min(page, totalPages() - 1);
        }

        List<Object> slice() {
            int fromIdx = Math.min(page * pageSize, all.size());
            int toIdx = Math.min((page + 1) * pageSize, all.size());
            return new ArrayList<>(all.subList(fromIdx, toIdx));
        }

        void refreshBar() {
            int start = all.isEmpty() ? 0 : page * pageSize + 1;
            int end = Math.min((page + 1) * pageSize, all.size());
            info.setText("Hiển thị " + start + "–" + end + " / " + all.size() + " bản ghi");
            pageLbl.setText("Trang " + (page + 1) + " / " + totalPages());
            prev.setEnabled(page > 0);
            next.setEnabled(page < totalPages() - 1);
        }
    }

    // ==========================================================
    // --- BIỂU ĐỒ DOANH THU ---
    // ==========================================================
    private static class RevenueChart extends JPanel {
        private List<LocalDate> days = new ArrayList<>();
        private List<BigDecimal> values = new ArrayList<>();
        private boolean line = true;
        private int hover = -1;

        RevenueChart() {
            setOpaque(false);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int i = indexAt(e.getX());
                    if (i != hover) {
                        hover = i;
                        repaint();
                    }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseExited(MouseEvent e) {
                    hover = -1;
                    repaint();
                }
            });
        }

        void setData(List<LocalDate> d, List<BigDecimal> v) {
            days = d;
            values = v;
            hover = -1;
            repaint();
        }

        void setLine(boolean b) {
            line = b;
            repaint();
        }

        private Rectangle plot() {
            return new Rectangle(66, 10, Math.max(10, getWidth() - 78), Math.max(10, getHeight() - 38));
        }

        private int indexAt(int x) {
            if (days.isEmpty()) return -1;
            Rectangle p = plot();
            int i = (int) ((x - p.x) / (p.width / (double) days.size()));
            return (x < p.x || i < 0 || i >= days.size()) ? -1 : i;
        }

        private static double niceCeil(double v) {
            double pow = Math.pow(10, Math.floor(Math.log10(v)));
            double n = v / pow;
            return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * pow;
        }

        private static String shortMoney(double v) {
            String s;
            if (v >= 1e9) {
                s = String.format(Locale.US, "%.1f", v / 1e9);
                return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + " tỷ";
            }
            if (v >= 1e6) {
                s = String.format(Locale.US, "%.1f", v / 1e6);
                return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "tr";
            }
            if (v >= 1e3) return String.format(Locale.US, "%.0fk", v / 1e3);
            return String.format(Locale.US, "%.0f", v);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            Rectangle p = plot();
            double max = 0;
            for (BigDecimal v : values) max = Math.max(max, v.doubleValue());
            double top = niceCeil(max <= 0 ? 1 : max);
            g2.setFont(font(Font.PLAIN, 11));
            FontMetrics fm = g2.getFontMetrics();

            for (int k = 0; k <= 4; k++) {
                int y = p.y + p.height - (int) (p.height * k / 4.0);
                g2.setColor(new Color(238, 240, 238));
                g2.drawLine(p.x, y, p.x + p.width, y);
                String lbl = shortMoney(top * k / 4.0);
                g2.setColor(Utils.TEXT_MUTED);
                g2.drawString(lbl, p.x - 8 - fm.stringWidth(lbl), y + fm.getAscent() / 2 - 1);
            }
            if (days.isEmpty()) {
                String msg = "Chưa có dữ liệu";
                g2.setColor(Utils.TEXT_MUTED);
                g2.drawString(msg, p.x + (p.width - fm.stringWidth(msg)) / 2, p.y + p.height / 2);
                g2.dispose();
                return;
            }

            int n = days.size();
            double step = p.width / (double) n;
            int base = p.y + p.height;
            int[] xs = new int[n], ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = (int) (p.x + step * (i + 0.5));
                ys[i] = base - (int) (p.height * values.get(i).doubleValue() / top);
            }

            if (line) {
                Path2D area = new Path2D.Double();
                area.moveTo(xs[0], base);
                for (int i = 0; i < n; i++) area.lineTo(xs[i], ys[i]);
                area.lineTo(xs[n - 1], base);
                area.closePath();
                g2.setColor(new Color(0xCE, 0x5D, 0x34, 26));
                g2.fill(area);
                g2.setColor(Utils.TERRACOTTA);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                for (int i = 0; i < n - 1; i++) g2.drawLine(xs[i], ys[i], xs[i + 1], ys[i + 1]);
                for (int i = 0; i < n; i++) {
                    int r = i == hover ? 6 : 4;
                    g2.setColor(Color.WHITE);
                    g2.fillOval(xs[i] - r, ys[i] - r, r * 2, r * 2);
                    g2.setColor(Utils.TERRACOTTA);
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(xs[i] - r, ys[i] - r, r * 2, r * 2);
                }
            } else {
                int bw = (int) Math.max(6, Math.min(46, step * 0.55));
                for (int i = 0; i < n; i++) {
                    int h = base - ys[i];
                    if (h <= 0) continue;
                    g2.setColor(i == hover ? Utils.TERRACOTTA : new Color(0xCE, 0x5D, 0x34, 190));
                    g2.fillRoundRect(xs[i] - bw / 2, ys[i], bw, h, 8, 8);
                    g2.fillRect(xs[i] - bw / 2, ys[i] + Math.min(h, 8) - 1, bw, Math.max(0, h - Math.min(h, 8) + 1));
                }
            }

            g2.setStroke(new BasicStroke(1f));
            g2.setColor(Utils.TEXT_MUTED);
            int every = (int) Math.ceil(n / Math.max(1.0, p.width / 62.0));
            for (int i = 0; i < n; i += Math.max(1, every)) {
                String lbl = days.get(i).format(DAY_FMT);
                g2.drawString(lbl, xs[i] - fm.stringWidth(lbl) / 2, base + 18);
            }

            if (hover >= 0 && hover < n) {
                String t1 = days.get(hover).format(DAY_FULL_FMT);
                String t2 = SALES_MONEY_FMT.format(values.get(hover));
                g2.setFont(font(Font.BOLD, 12));
                FontMetrics fb = g2.getFontMetrics();
                int w = Math.max(fb.stringWidth(t1), fb.stringWidth(t2)) + 22, h = 46;
                int x = Math.max(p.x, Math.min(xs[hover] - w / 2, p.x + p.width - w));
                int y = Math.max(0, ys[hover] - h - 12);
                g2.setColor(Utils.FOREST_BG);
                g2.fillRoundRect(x, y, w, h, 10, 10);
                g2.setColor(Utils.GOLD_ACCENT);
                g2.drawString(t1, x + 11, y + 19);
                g2.setColor(Color.WHITE);
                g2.drawString(t2, x + 11, y + 37);
            }
            g2.dispose();
        }
    }

    // ==========================================================
    // --- ICONS ---
    // ==========================================================
    static class NavIcon implements Icon {
        static final int FOOD = 0, CHART = 2, USER = 3, CHECK = 4, PAUSE = 5,
                RECEIPT = 7, PERCENT = 8, COIN = 9, LOCK = 10;
        private final int type, size;

        NavIcon(int type, int size) {
            this.type = type;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(c.getForeground());
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (type) {
                case FOOD -> {
                    g2.drawArc(x + 2, y + 4, 16, 14, 180, 180);
                    g2.drawLine(x + 1, y + 11, x + 19, y + 11);
                    g2.drawLine(x + 7, y + 1, x + 7, y + 5);
                    g2.drawLine(x + 13, y + 1, x + 13, y + 5);
                }
                case CHART -> {
                    g2.fillRoundRect(x + 3, y + 10, 4, 8, 2, 2);
                    g2.fillRoundRect(x + 8, y + 4, 4, 14, 2, 2);
                    g2.fillRoundRect(x + 13, y + 7, 4, 11, 2, 2);
                }
                case USER -> {
                    g2.drawOval(x + 6, y + 2, 8, 8);
                    g2.drawArc(x + 2, y + 12, 16, 14, 0, 180);
                }
                case CHECK -> {
                    g2.drawOval(x + 2, y + 2, 16, 16);
                    g2.drawPolyline(new int[]{x + 6, x + 9, x + 14}, new int[]{y + 10, y + 13, y + 7}, 3);
                }
                case PAUSE -> {
                    g2.drawOval(x + 2, y + 2, 16, 16);
                    g2.fillRect(x + 7, y + 6, 2, 8);
                    g2.fillRect(x + 11, y + 6, 2, 8);
                }
                case RECEIPT -> {
                    g2.drawRoundRect(x + 4, y + 2, 12, 16, 3, 3);
                    g2.drawLine(x + 7, y + 7, x + 13, y + 7);
                    g2.drawLine(x + 7, y + 11, x + 13, y + 11);
                }
                case PERCENT -> {
                    g2.drawLine(x + 15, y + 4, x + 5, y + 16);
                    g2.drawOval(x + 3, y + 3, 5, 5);
                    g2.drawOval(x + 12, y + 12, 5, 5);
                }
                case COIN -> {
                    g2.drawOval(x + 2, y + 2, 16, 16);
                    g2.drawLine(x + 10, y + 6, x + 10, y + 14);
                    g2.drawLine(x + 7, y + 8, x + 13, y + 8);
                }
                case LOCK -> {
                    g2.drawRoundRect(x + 4, y + 9, 12, 8, 3, 3);
                    g2.drawArc(x + 6, y + 3, 8, 12, 0, 180);
                }
            }
            g2.dispose();
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    private static class IconChip extends JComponent {
        private final NavIcon icon;
        private final Color color;

        IconChip(int type, Color color) {
            this.icon = new NavIcon(type, 20);
            this.color = color;
            setForeground(color);
            setPreferredSize(new Dimension(44, 44));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int s = 44, x = (getWidth() - s) / 2, y = (getHeight() - s) / 2;
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
            g2.fillRoundRect(x, y, s, s, 12, 12);
            g2.dispose();
            icon.paintIcon(this, g, x + 12, y + 12);
        }
    }

    private record AvatarIcon(int size, String letter, Color bg) implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillOval(x, y, size, size);
            g2.setColor(Color.WHITE);
            g2.setFont(font(Font.BOLD, size / 2));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(letter, x + (size - fm.stringWidth(letter)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }

        @Override public int getIconWidth() { return size; }
        @Override public int getIconHeight() { return size; }
    }

    // ==========================================================
    // --- ĐĂNG NHẬP & LOGO ---
    // ==========================================================
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
                } catch (Exception ignored) {
                }
            }
        }.execute();
        return label;
    }

    public static void showAdminLogin() {
        JDialog dialog = new JDialog((Frame) null, "Đăng nhập quản trị — An Nhiên", true);
        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel fields = new JPanel(new GridLayout(2, 2, 10, 10));
        JTextField account = new CTextField("Email hoặc SĐT", 270, 42, font(Font.PLAIN, 14));
        CPasswordField password = new CPasswordField("Mật khẩu", 270, 42, font(Font.PLAIN, 14));

        // 1. Tải icon mắt ngay khi vừa mở dialog (chạy nền)
        new SwingWorker<ImageIcon[], Void>() {
            @Override
            protected ImageIcon[] doInBackground() {
                BufferedImage eyeOpen = ImageSocketClient.fetchImage("img/things/eye.png");
                BufferedImage eyeClosed = ImageSocketClient.fetchImage("img/things/eye_off.png");
                ImageIcon openIcon = eyeOpen != null
                        ? new ImageIcon(eyeOpen.getScaledInstance(18, 18, Image.SCALE_SMOOTH)) : null;
                ImageIcon closedIcon = eyeClosed != null
                        ? new ImageIcon(eyeClosed.getScaledInstance(18, 18, Image.SCALE_SMOOTH)) : null;
                return new ImageIcon[]{openIcon, closedIcon};
            }

            @Override
            protected void done() {
                try {
                    ImageIcon[] icons = get();
                    if (icons[0] != null || icons[1] != null) {
                        password.setVisibilityIcons(icons[0], icons[1]);
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();

        fields.add(new JLabel("Email / SĐT ADMIN:"));
        fields.add(account);
        fields.add(new JLabel("Mật khẩu:"));
        fields.add(password);

        JLabel feedback = new JLabel("AN NHIÊN | Đăng nhập qua hệ thống quản lý");
        JButton signIn = new CButton("Đăng nhập", 125, 40, font(Font.BOLD, 13));
        CButton cancel = new CButton("Hủy", 125, 40);
        cancel.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancel);
        footer.add(signIn);

        // 2. Logic xử lý đăng nhập thật khi bấm nút
        signIn.addActionListener(e -> {
            char[] pass = password.getPassword();
            String user = account.getText().trim();
            if (user.isEmpty() || pass.length == 0) {
                Arrays.fill(pass, '\0');
                feedback.setText("Vui lòng điền tài khoản và mật khẩu");
                return;
            }
            String secret = new String(pass);
            Arrays.fill(pass, '\0');
            password.setText("");
            signIn.setEnabled(false);
            feedback.setText("Đang xác thực qua mạng...");

            new SwingWorker<Object[], Void>() {
                @Override
                protected Object[] doInBackground() {
                    SocketClient client = new SocketClient();
                    try {
                        UserDTO admin = AdminBUS.loginAdmin(client, user, secret);
                        return new Object[]{admin, client};
                    } catch (RuntimeException ex) {
                        client.close();
                        throw ex;
                    }
                }

                @Override
                protected void done() {
                    signIn.setEnabled(true);
                    try {
                        Object[] res = get();
                        dialog.dispose();
                        new AdminGUI((UserDTO) res[0], (SocketClient) res[1]).setVisible(true);
                    } catch (Exception ex) {
                        Throwable cause = (ex instanceof ExecutionException && ex.getCause() != null)
                                ? ex.getCause() : ex;
                        feedback.setText(cause.getMessage());
                    }
                }
            }.execute();
        });

        body.add(feedback, BorderLayout.NORTH);
        body.add(fields, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        dialog.setContentPane(body);
        dialog.getRootPane().setDefaultButton(signIn);
        dialog.pack();
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
    }

    private void loadFoodThumbAsync(String imageKey, JTable tableToRepaint) {
        if (imageKey == null || imageKey.isBlank()) return;
        if (FOOD_THUMB_CACHE.containsKey(imageKey) || !FOOD_THUMB_LOADING.add(imageKey)) return;

        new SwingWorker<ImageIcon, Void>() {
            @Override
            protected ImageIcon doInBackground() {
                BufferedImage raw = ImageSocketClient.fetchImage(imageKey);
                if (raw == null) return null;
                int size = 36;
                BufferedImage rounded = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = rounded.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, size, size, 12, 12));
                g2.drawImage(raw, 0, 0, size, size, null);
                g2.dispose();
                return new ImageIcon(rounded);
            }

            @Override
            protected void done() {
                FOOD_THUMB_LOADING.remove(imageKey);
                try {
                    ImageIcon icon = get();
                    if (icon != null) {
                        FOOD_THUMB_CACHE.put(imageKey, icon);
                        if (tableToRepaint != null && tableToRepaint.isDisplayable()) {
                            tableToRepaint.repaint();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdminGUI::showAdminLogin);
    }
}