package gui;

import api.ImageSocketClient;
import java.awt.image.BufferedImage;

import api.SocketClient;
import bus.AuthBUS;
import bus.AdminBUS;
import components.CToast;
import components.CButton;
import components.CCheckBox;
import components.CPasswordField;
import components.CTable;
import components.CTextField;
import components.CToggleButton;
import dto.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import utils.Utils;

public class AdminGUI extends JFrame {

    private static final Color BORDER_SOFT = new Color(232, 235, 232);
    private static final Color HOVER_ROW = new Color(246, 247, 244);
    private static final Color SELECT_BG = new Color(253, 237, 232);
    private static final Color NEUTRAL_BTN = new Color(238, 241, 238);

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### VNĐ");
    private static final DecimalFormat QTY_FMT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat INT_FMT = new DecimalFormat("#,###");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter DAY_FULL_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Font
    private static final String FONT_FAMILY = resolveFontFamily();

    private static String resolveFontFamily() {
        Set<String> have = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Be Vietnam Pro", "Inter", "Segoe UI", "Roboto"}) {
            if (have.contains(f)) return f;
        }
        return Font.SANS_SERIF;
    }

    private static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size);
    }

    // Mạng & Dữ liệu
    private final SocketClient socket;
    private final AdminBUS adminBUS;

    // Điều hướng
    private static final String PAGE_FOODS = "FOODS";
    private static final String PAGE_INVENTORY = "INVENTORY";
    private static final String PAGE_REVENUE = "REVENUE";
    private static final String PAGE_ACCOUNTS = "ACCOUNTS";

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
    private Paging<FoodDTO> foodPaging;

    // Trang 2: Kho
    private List<IngredientDTO> allIngredients = new ArrayList<>();
    private List<IngredientDTO> shownIngredients = new ArrayList<>();
    private DefaultTableModel stockModel;
    private JTable stockTable;
    private JTextField txtStockSearch;
    private JComboBox<String> cboStockStatus;
    private JLabel kpiStockTotal, kpiStockOk, kpiStockLow;
    private JPanel stockBanner;
    private JLabel lblStockBanner;
    private Color bannerBg = new Color(228, 240, 232);
    private Color bannerBorder = Utils.SAFE_GREEN;

    // Trang 3: Doanh thu
    private List<BillDTO> allBills = new ArrayList<>();
    private DefaultTableModel billModel;
    private JTextField txtBillSearch;
    private JComboBox<String> cboBillTime, cboBillPay;
    private JLabel kpiRevenue, kpiBills, kpiDiscount, kpiTax;
    private RevenueChart revenueChart;
    private Paging<BillDTO> billPaging;

    // Trang 4: Tài khoản
    private static final String ROLE_MEMBER = "Thành viên";
    private static final String ROLE_STAFF = "Nhân viên";
    private static final String ST_ACTIVE = "Đang hoạt động";
    private static final String ST_LOCKED = "Đã khóa";


    private List<UserDTO> allAccounts = new ArrayList<>();
    private List<UserDTO> pageAccounts = new ArrayList<>();
    private DefaultTableModel accModel;
    private JTextField txtAccSearch;
    private JComboBox<String> cboAccRole, cboAccStatus;
    private JLabel kpiAccTotal, kpiAccMember, kpiAccStaff, kpiAccLocked;
    private Paging<UserDTO> accPaging;

    // KHỞI TẠO
    public AdminGUI(UserDTO admin, SocketClient authenticatedSocket) {
        if (admin == null || !"ADMIN".equalsIgnoreCase(admin.getRoleName()))
            throw new SecurityException("Chỉ tài khoản ADMIN mới được mở giao diện quản trị");

        this.socket = authenticatedSocket;
        this.adminBUS = new AdminBUS(authenticatedSocket);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                socket.logout();
                socket.close();
            }
        });

        setTitle("Bếp Nhà Restaurant - Admin Dashboard");
        setSize(1440, 900);
        setMinimumSize(new Dimension(1200, 720));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Chỉ đóng Client Admin, không tắt Server
        setLayout(new BorderLayout());
        getContentPane().setBackground(Utils.CREAM_BG);

        contentCards.setOpaque(false);
        contentCards.setBorder(new EmptyBorder(22, 28, 24, 28));
        contentCards.add(createFoodPage(), PAGE_FOODS);
        contentCards.add(createInventoryPage(), PAGE_INVENTORY);
        contentCards.add(createRevenuePage(), PAGE_REVENUE);
        contentCards.add(createAccountPage(), PAGE_ACCOUNTS);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(Utils.CREAM_BG);
        main.add(createHeader(), BorderLayout.NORTH);
        main.add(contentCards, BorderLayout.CENTER);

        add(createSidebar(), BorderLayout.WEST);
        add(main, BorderLayout.CENTER);

        showPage(PAGE_FOODS);
    }

    // KẾT NỐI MẠNG (TCP & THREADING BẤT ĐỒNG BỘ)
    private void showNetworkError(Exception ex) {
        Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
        CToast.error(this, "Lỗi giao tiếp server: " + cause.getMessage());
    }

    private void performUpdate(String name, java.util.concurrent.Callable<Boolean> operation, Runnable refresh) {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() throws Exception {
                return operation.call();
            }

            @Override
            protected void done() {
                if (!isDisplayable()) return;
                try {
                    if (!Boolean.TRUE.equals(get())) throw new IllegalStateException("Server từ chối thao tác");
                    CToast.success(AdminGUI.this, name + " thành công");
                    if (refresh != null) refresh.run();
                } catch (Exception ex) {
                    showNetworkError(ex);
                }
            }
        }.execute();
    }

    /** Mẫu tải dữ liệu chung: chạy TCP ở worker, cập nhật Swing trên EDT. */
    private abstract class PageLoadWorker<T> extends SwingWorker<T, Void> {
        protected abstract void onLoaded(T result);

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

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }

    private void refreshAllData() {
        loadFoods();
        loadInventory();
        loadRevenue();
        loadAccounts();
    }

    // SIDEBAR & HEADER
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
        JLabel lblBrand = new JLabel("BẾP NHÀ");
        lblBrand.setFont(font(Font.BOLD, 18));
        lblBrand.setForeground(Color.WHITE);
        JLabel lblBrandSub = new JLabel("Restaurant Admin");
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
        top.add(createNavButton("Kho nguyên liệu", NavIcon.BOX, PAGE_INVENTORY));
        top.add(Box.createVerticalStrut(8));
        top.add(createNavButton("Thống kê doanh thu", NavIcon.CHART, PAGE_REVENUE));
        top.add(Box.createVerticalStrut(8));
        top.add(createNavButton("Quản lý tài khoản", NavIcon.USER, PAGE_ACCOUNTS));
        sidebar.add(top, BorderLayout.NORTH);

        JPanel adminBox = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 18));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        adminBox.setOpaque(false);
        adminBox.setBorder(new EmptyBorder(12, 12, 12, 12));
        adminBox.add(new JLabel(new AvatarIcon(38, "A", Utils.TERRACOTTA)), BorderLayout.WEST);
        JPanel adminText = new JPanel();
        adminText.setLayout(new BoxLayout(adminText, BoxLayout.Y_AXIS));
        adminText.setOpaque(false);
        JLabel lblName = new JLabel("Quản trị viên");
        lblName.setFont(font(Font.BOLD, 13));
        lblName.setForeground(Color.WHITE);
        JLabel lblRole = new JLabel("ADMIN");
        lblRole.setFont(font(Font.PLAIN, 11));
        lblRole.setForeground(Utils.GOLD_ACCENT);
        adminText.add(Box.createVerticalGlue());
        adminText.add(lblName);
        adminText.add(lblRole);
        adminText.add(Box.createVerticalGlue());
        adminBox.add(adminText, BorderLayout.CENTER);
        sidebar.add(adminBox, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton createNavButton(String text, int iconType, String pageId) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = pageId.equals(activePage);
                if (selected) {
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
                new MatteBorder(0, 0, 1, 0, BORDER_SOFT), new EmptyBorder(0, 28, 0, 28)));

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
        JButton btnSync = createRoundedButton("Đồng bộ", Utils.TERRACOTTA, Color.WHITE, 120, 40);
        btnSync.addActionListener(e -> refreshAllData());
        right.add(btnSync);

        JPanel adminInfo = new JPanel(new BorderLayout(10, 0));
        adminInfo.setOpaque(false);
        adminInfo.add(new JLabel(new AvatarIcon(38, "A", Utils.FOREST_BG)), BorderLayout.WEST);
        JPanel t = new JPanel();
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
        t.setOpaque(false);
        JLabel n = new JLabel("Quản trị viên");
        n.setFont(font(Font.BOLD, 13));
        n.setForeground(Utils.TEXT_DARK);
        JLabel r = new JLabel("ADMIN");
        r.setFont(font(Font.PLAIN, 11));
        r.setForeground(Utils.TEXT_MUTED);
        t.add(n);
        t.add(r);
        adminInfo.add(t, BorderLayout.CENTER);
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
            case PAGE_INVENTORY:
                lblPageTitle.setText("Kho nguyên liệu");
                lblPageSubtitle.setText("Theo dõi tồn kho, định mức tối thiểu và nhập thêm hàng.");
                loadInventory();
                break;
            case PAGE_REVENUE:
                lblPageTitle.setText("Thống kê doanh thu");
                lblPageSubtitle.setText("Doanh thu thực thu, giảm giá, VAT và lịch sử hóa đơn.");
                loadRevenue();
                break;
            case PAGE_ACCOUNTS:
                lblPageTitle.setText("Quản lý tài khoản");
                lblPageSubtitle.setText("Nhân viên cấp thẻ thành viên, quản lý tài khoản nhân viên và khách hàng.");
                loadAccounts();
                break;
            case PAGE_FOODS:
            default:
                lblPageTitle.setText("Quản lý thực đơn");
                lblPageSubtitle.setText("Thêm, chỉnh sửa và quản lý trạng thái bán của các món ăn.");
                loadFoods();
                break;
        }
    }

    // TRANG 1: QUẢN LÝ THỰC ĐƠN
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
        cboFoodCat = createFilterCombo("Tất cả danh mục");
        cboFoodStatus = createFilterCombo("Tất cả trạng thái", "Đang mở bán", "Tạm ngưng");
        onTextChange(txtFoodSearch, () -> applyFoodFilter(true));
        cboFoodCat.addActionListener(e -> {
            if (!adjusting) applyFoodFilter(true);
        });
        cboFoodStatus.addActionListener(e -> {
            if (!adjusting) applyFoodFilter(true);
        });

        JButton btnAdd = createRoundedButton("+  Thêm món mới", Utils.TERRACOTTA, Color.WHITE, 160, 40);
        btnAdd.addActionListener(e -> openFoodDialog(null));

        foodModel = readOnlyModel("Hình", "Mã SKU", "Tên món", "Danh mục", "Đơn giá", "Trạng thái", "Thao tác");
        CTable foodTableComponent = createStyledTable(foodModel, 52);
        JTable foodTable = foodTableComponent.getTable();
        foodTable.getColumnModel().getColumn(0).setCellRenderer(new ThumbRenderer());
        foodTable.getColumnModel().getColumn(4).setCellRenderer(new RowRenderer(true));
        foodTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        installActionColumn(foodTable, 6, (row, idx) -> {
            if (row < 0 || row >= pageFoods.size()) return;
            FoodDTO f = pageFoods.get(row);
            if (idx == 0) openFoodDialog(f);
            else confirmDeleteFood(f);
        });
        setColumnWidths(foodTable, 70, 110, 260, 170, 150, 150, 130);

        foodPaging = new Paging<>(8, this::renderFoodPage);
        page.add(createTableCard(createToolbar(
                flow(FlowLayout.LEFT, txtFoodSearch, cboFoodCat, cboFoodStatus),
                flow(FlowLayout.RIGHT, btnAdd)), foodTableComponent, foodPaging.bar), BorderLayout.CENTER);
        return page;
    }

    private void loadFoods() {
        new PageLoadWorker<List<FoodDTO>>() {
            @Override
            protected List<FoodDTO> doInBackground() {
                return adminBUS.getAllFoods();
            }

            @Override
            protected void onLoaded(List<FoodDTO> result) {
                allFoods = copyOrEmpty(result);
                updateComboItems(cboFoodCat, "Tất cả danh mục",
                        allFoods.stream()
                                .map(f -> f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại")
                                .distinct().sorted().toList());

                int open = 0;
                for (FoodDTO f : allFoods) if (isOpen(f)) open++;
                kpiFoodTotal.setText(String.valueOf(allFoods.size()));
                kpiFoodOpen.setText(String.valueOf(open));
                kpiFoodPaused.setText(String.valueOf(allFoods.size() - open));
                applyFoodFilter(false);
            }
        }.execute();
    }

    private void applyFoodFilter(boolean resetPage) {
        String q = txtFoodSearch.getText().trim().toLowerCase();
        String cat = (String) cboFoodCat.getSelectedItem();
        String st = (String) cboFoodStatus.getSelectedItem();
        List<FoodDTO> res = new ArrayList<>();
        for (FoodDTO f : allFoods) {
            String catN = f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại";
            if (!q.isEmpty()
                    && !String.valueOf(f.getName()).toLowerCase().contains(q)
                    && !String.valueOf(f.getSku()).toLowerCase().contains(q)) continue;
            if (cat != null && !cat.startsWith("Tất cả") && !cat.equals(catN)) continue;
            if ("Đang mở bán".equals(st) && !isOpen(f)) continue;
            if ("Tạm ngưng".equals(st) && isOpen(f)) continue;
            res.add(f);
        }
        foodPaging.setAll(res, resetPage);
        renderFoodPage();
    }

    private void renderFoodPage() {
        pageFoods = foodPaging.slice();
        foodModel.setRowCount(0);
        for (FoodDTO f : pageFoods) {
            foodModel.addRow(new Object[]{
                    f.getName(), f.getSku(), f.getName(),
                    f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại",
                    MONEY_FMT.format(f.getPrice()),
                    isOpen(f) ? "Đang mở bán" : "Tạm ngưng",
                    "Sửa|Xóa"
            });
        }
        foodPaging.refreshBar();
    }

    private boolean isOpen(FoodDTO f) {
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
                    JTextField cover = createStyledTextField(edit && food.getCoverImageUrl() != null ? food.getCoverImageUrl() : "");
                    JComboBox<CategoryDTO> catBox = new JComboBox<>();
                    styleCombo(catBox);
                    for (CategoryDTO c : categories) {
                        catBox.addItem(c);
                        if (edit && String.valueOf(c).equals(food.getCategoryName())) catBox.setSelectedItem(c);
                    }
                    JCheckBox published = new JCheckBox("Mở bán trên Tablet của khách", !edit || isOpen(food));
                    published.setIcon(new CCheckBox(false));
                    published.setSelectedIcon(new CCheckBox(true));
                    published.setIconTextGap(9);
                    published.setFont(font(Font.PLAIN, 13));
                    published.setForeground(Utils.TEXT_DARK);
                    published.setOpaque(false);
                    published.setFocusPainted(false);
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

                    showModal(edit ? "Sửa món ăn" : "Thêm món mới",
                            edit ? "Cập nhật thông tin món đang chọn." : "Nhập thông tin món mới để đưa lên thực đơn.",
                            form, edit ? "Lưu thay đổi" : "Thêm món", Utils.TERRACOTTA, () -> {
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

                                performUpdate(edit ? "Cập nhật món" : "Thêm món", () -> {
                                    return edit
                                            ? adminBUS.updateFood(food.getId(), n, p, catId, published.isSelected())
                                            : adminBUS.addNewFood(sku.getText().trim(), catId, n, p, cover.getText(), published.isSelected());
                                }, AdminGUI.this::loadFoods);
                                return null;
                            });
                } catch (Exception ex) {
                    showNetworkError(ex);
                }
            }
        }.execute();
    }

    private void confirmDeleteFood(FoodDTO f) {
        if (confirmAction("Xác nhận xóa mềm",
                "Xóa mềm món '" + f.getName() + "'?\nMón sẽ ẩn khỏi Tablet nhưng vẫn giữ trong lịch sử.")) {
            performUpdate("Xóa món", () -> adminBUS.deleteFood(f.getId()), this::loadFoods);
        }
    }

    // TRANG 2: KHO NGUYÊN LIỆU
    private JPanel createInventoryPage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        lblStockBanner = new JLabel();
        lblStockBanner.setFont(font(Font.BOLD, 14));
        stockBanner = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bannerBg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.setColor(bannerBorder);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
            }
        };
        stockBanner.setOpaque(false);
        stockBanner.setBorder(new EmptyBorder(14, 20, 14, 20));
        stockBanner.add(lblStockBanner, BorderLayout.CENTER);

        kpiStockTotal = kpiLabel("0");
        kpiStockOk = kpiLabel("0");
        kpiStockLow = kpiLabel("0");
        kpiStockOk.setForeground(Utils.SAFE_GREEN);
        kpiStockLow.setForeground(Utils.DANGER_RED);
        JPanel kpis = kpiRow(3);
        kpis.add(createKpiCard("TỔNG NGUYÊN LIỆU", kpiStockTotal, Utils.FOREST_BG, NavIcon.BOX));
        kpis.add(createKpiCard("ĐỦ HÀNG", kpiStockOk, Utils.SAFE_GREEN, NavIcon.CHECK));
        kpis.add(createKpiCard("SẮP HẾT", kpiStockLow, Utils.DANGER_RED, NavIcon.ALERT));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        stockBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        kpis.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(stockBanner);
        north.add(Box.createVerticalStrut(18));
        north.add(kpis);
        page.add(north, BorderLayout.NORTH);

        txtStockSearch = createSearchField("Tìm theo tên nguyên liệu...", 300);
        cboStockStatus = createFilterCombo("Tất cả trạng thái", "Đủ hàng", "Sắp hết");
        onTextChange(txtStockSearch, this::applyStockFilter);
        cboStockStatus.addActionListener(e -> {
            if (!adjusting) applyStockFilter();
        });
        JButton btnNew = createRoundedButton("+  Khai báo mới", Utils.FOREST_BG, Color.WHITE, 150, 40);
        btnNew.addActionListener(e -> openIngredientDialog());
        JButton btnRestock = createRoundedButton("Nhập kho", Utils.TERRACOTTA, Color.WHITE, 120, 40);
        btnRestock.addActionListener(e -> {
            int viewRow = stockTable.getSelectedRow();
            int modelRow = viewRow < 0 ? -1 : stockTable.convertRowIndexToModel(viewRow);
            openRestockDialog(modelRow >= 0 && modelRow < shownIngredients.size()
                    ? shownIngredients.get(modelRow) : null);
        });

        stockModel = readOnlyModel("ID", "Tên nguyên liệu", "Tồn kho", "Định mức", "Trạng thái", "Thao tác");
        CTable stockTableComponent = createStyledTable(stockModel, 46);
        stockTable = stockTableComponent.getTable();
        stockTable.putClientProperty("rowTint", (IntFunction<Color>) r -> isLowRow(r) ? new Color(254, 245, 245) : null);
        stockTable.getColumnModel().getColumn(2).setCellRenderer(new RowRenderer(true) {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                super.getTableCellRendererComponent(t, v, s, f, r, c);
                setForeground(isLowRow(r) ? Utils.DANGER_RED : Utils.SAFE_GREEN);
                return this;
            }
        });
        stockTable.getColumnModel().getColumn(4).setCellRenderer(new BadgeRenderer());
        installActionColumn(stockTable, 5, (row, idx) -> {
            if (row >= 0 && row < shownIngredients.size()) openRestockDialog(shownIngredients.get(row));
        });
        setColumnWidths(stockTable, 70, 300, 180, 180, 160, 120);

        page.add(createTableCard(createToolbar(flow(FlowLayout.LEFT, txtStockSearch, cboStockStatus),
                flow(FlowLayout.RIGHT, btnNew, btnRestock)), stockTableComponent, null), BorderLayout.CENTER);
        return page;
    }

    private boolean isLowRow(int row) {
        return row >= 0 && row < shownIngredients.size() && isLow(shownIngredients.get(row));
    }

    private boolean isLow(IngredientDTO i) {
        return i.getStockQuantity().compareTo(i.getMinStockLevel()) <= 0;
    }

    private void loadInventory() {
        new PageLoadWorker<List<IngredientDTO>>() {
            @Override
            protected List<IngredientDTO> doInBackground() {
                return adminBUS.getAllIngredients();
            }

            @Override
            protected void onLoaded(List<IngredientDTO> result) {
                allIngredients = copyOrEmpty(result);
                int low = 0;
                for (IngredientDTO i : allIngredients) if (isLow(i)) low++;
                kpiStockTotal.setText(String.valueOf(allIngredients.size()));
                kpiStockOk.setText(String.valueOf(allIngredients.size() - low));
                kpiStockLow.setText(String.valueOf(low));

                if (low > 0) {
                    lblStockBanner.setText("Cảnh báo tồn kho: " + low + " nguyên liệu đang chạm hoặc thấp hơn định mức. Cần nhập sớm.");
                    lblStockBanner.setForeground(Utils.DANGER_RED);
                    bannerBg = new Color(254, 242, 242);
                    bannerBorder = new Color(238, 188, 188);
                } else {
                    lblStockBanner.setText("Tồn kho an toàn: tất cả nguyên liệu đều cao hơn định mức tối thiểu.");
                    lblStockBanner.setForeground(Utils.SAFE_GREEN);
                    bannerBg = new Color(232, 243, 236);
                    bannerBorder = new Color(180, 214, 192);
                }
                stockBanner.repaint();
                applyStockFilter();
            }
        }.execute();
    }

    private void applyStockFilter() {
        String q = txtStockSearch.getText().trim().toLowerCase();
        String st = (String) cboStockStatus.getSelectedItem();
        shownIngredients = new ArrayList<>();
        stockModel.setRowCount(0);
        for (IngredientDTO i : allIngredients) {
            boolean low = isLow(i);
            if (!q.isEmpty() && !String.valueOf(i.getName()).toLowerCase().contains(q)) continue;
            if ("Đủ hàng".equals(st) && low) continue;
            if ("Sắp hết".equals(st) && !low) continue;
            shownIngredients.add(i);
            stockModel.addRow(new Object[]{
                    i.getId(), i.getName(), QTY_FMT.format(i.getStockQuantity()) + " " + i.getUnit(),
                    QTY_FMT.format(i.getMinStockLevel()) + " " + i.getUnit(), low ? "Sắp hết" : "Đủ hàng", "Nhập kho"
            });
        }
    }

    private void openRestockDialog(IngredientDTO preselected) {
        if (allIngredients.isEmpty()) {
            CToast.warning(this, "Chưa có nguyên liệu nào.");
            return;
        }
        JComboBox<IngredientDTO> box = new JComboBox<>();
        styleCombo(box);
        for (IngredientDTO i : allIngredients) {
            box.addItem(i);
            if (preselected != null && i.getId().equals(preselected.getId())) box.setSelectedItem(i);
        }
        box.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean foc) {
                super.getListCellRendererComponent(list, value, index, sel, foc);
                if (value instanceof IngredientDTO) {
                    IngredientDTO i = (IngredientDTO) value;
                    setText(i.getName() + "  (tồn " + QTY_FMT.format(i.getStockQuantity()) + " " + i.getUnit() + ")");
                }
                return this;
            }
        });
        box.setEnabled(preselected == null);
        JTextField qty = createStyledTextField("10");
        JPanel form = newFormPanel();
        addField(form, "Nguyên liệu", box);
        addField(form, "Số lượng nhập thêm", qty);

        showModal("Nhập kho", "Số lượng sẽ được cộng vào tồn kho hiện tại.", form, "Xác nhận nhập", Utils.TERRACOTTA, () -> {
            BigDecimal added;
            try {
                added = new BigDecimal(qty.getText().trim().replace(",", "."));
            } catch (NumberFormatException ex) {
                return "Số lượng phải là số hợp lệ.";
            }
            if (added.signum() <= 0) return "Số lượng phải lớn hơn 0.";
            IngredientDTO ing = (IngredientDTO) box.getSelectedItem();
            if (ing == null) return "Vui lòng chọn nguyên liệu.";

            performUpdate("Nhập kho", () -> adminBUS.restockIngredient(ing.getId(), added), this::loadInventory);
            return null;
        });
    }

    private void openIngredientDialog() {
        JTextField name = createStyledTextField("");
        JTextField unit = createStyledTextField("kg");
        JTextField stock = createStyledTextField("20");
        JTextField min = createStyledTextField("5");
        JPanel form = newFormPanel();
        addField(form, "Tên nguyên liệu", name);
        addField(form, "Đơn vị tính", unit);
        addField(form, "Số lượng tồn ban đầu", stock);
        addField(form, "Định mức tối thiểu", min);

        showModal("Khai báo nguyên liệu", "Thêm nguyên liệu vào danh mục kho.", form, "Khai báo", Utils.FOREST_BG, () -> {
            if (name.getText().trim().isEmpty()) return "Vui lòng nhập tên.";
            if (unit.getText().trim().isEmpty()) return "Vui lòng nhập đơn vị tính.";
            BigDecimal s, m;
            try {
                s = new BigDecimal(stock.getText().trim().replace(",", "."));
                m = new BigDecimal(min.getText().trim().replace(",", "."));
            } catch (NumberFormatException ex) {
                return "Tồn kho và định mức không hợp lệ.";
            }
            if (s.signum() < 0 || m.signum() < 0) return "Giá trị không được âm.";

            performUpdate("Khai báo nguyên liệu", () -> adminBUS.addIngredient(name.getText().trim(), unit.getText().trim(), s, m), this::loadInventory);
            return null;
        });
    }

    // TRANG 3: THỐNG KÊ DOANH THU
    private JPanel createRevenuePage() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setOpaque(false);

        kpiRevenue = kpiLabel("0 VNĐ");
        kpiBills = kpiLabel("0");
        kpiDiscount = kpiLabel("0 VNĐ");
        kpiTax = kpiLabel("0 VNĐ");
        JPanel kpis = kpiRow(4);
        kpis.add(createKpiCard("THỰC THU", kpiRevenue, Utils.TERRACOTTA, NavIcon.COIN));
        kpis.add(createKpiCard("SỐ HÓA ĐƠN", kpiBills, Utils.FOREST_BG, NavIcon.RECEIPT));
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
        chartTitle.setForeground(Utils.TEXT_DARK);
        chartHead.add(chartTitle, BorderLayout.WEST);
        chartHead.add(flow(FlowLayout.RIGHT, segLine, segBar), BorderLayout.EAST);
        JPanel chartCard = createCard();
        chartCard.setLayout(new BorderLayout(0, 8));
        chartCard.add(chartHead, BorderLayout.NORTH);
        chartCard.add(revenueChart, BorderLayout.CENTER);
        chartCard.setPreferredSize(new Dimension(0, 270));

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        north.setOpaque(false);
        kpis.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        chartCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 270));
        north.add(kpis);
        north.add(Box.createVerticalStrut(18));
        north.add(chartCard);
        page.add(north, BorderLayout.NORTH);

        txtBillSearch = createSearchField("Tìm theo mã hóa đơn...", 260);
        cboBillTime = createFilterCombo("Tất cả thời gian", "Hôm nay", "7 ngày qua", "30 ngày qua");
        cboBillPay = createFilterCombo("Tất cả thanh toán");
        onTextChange(txtBillSearch, () -> applyRevenueFilter(true));
        cboBillTime.addActionListener(e -> {
            if (!adjusting) applyRevenueFilter(true);
        });
        cboBillPay.addActionListener(e -> {
            if (!adjusting) applyRevenueFilter(true);
        });

        billModel = readOnlyModel("Mã HĐ", "Phiên bàn", "Giảm giá", "VAT", "Tổng thanh toán", "Thanh toán", "Thời gian");
        CTable billTableComponent = createStyledTable(billModel, 40);
        JTable billTable = billTableComponent.getTable();
        billTable.getColumnModel().getColumn(4).setCellRenderer(new RowRenderer(true));
        billTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        setColumnWidths(billTable, 100, 110, 140, 130, 180, 140, 190);

        billPaging = new Paging<>(6, this::renderBillPage);
        page.add(createTableCard(createToolbar(flow(FlowLayout.LEFT, txtBillSearch, cboBillTime, cboBillPay),
                flow(FlowLayout.RIGHT)), billTableComponent, billPaging.bar), BorderLayout.CENTER);
        return page;
    }

    private void loadRevenue() {
        new PageLoadWorker<List<BillDTO>>() {
            @Override
            protected List<BillDTO> doInBackground() {
                return adminBUS.getAllBills();
            }

            @Override
            protected void onLoaded(List<BillDTO> result) {
                allBills = copyOrEmpty(result);
                updateComboItems(cboBillPay, "Tất cả thanh toán",
                        allBills.stream()
                                .map(b -> b.getPaymentMethod() != null ? b.getPaymentMethod().name() : "---")
                                .distinct().sorted().toList());
                applyRevenueFilter(false);
            }
        }.execute();
    }

    private void applyRevenueFilter(boolean resetPage) {
        String q = txtBillSearch.getText().trim().replace("#", "").toLowerCase();
        String timeSel = (String) cboBillTime.getSelectedItem();
        String paySel = (String) cboBillPay.getSelectedItem();
        LocalDate today = LocalDate.now();
        LocalDate from = null;
        if ("Hôm nay".equals(timeSel)) from = today;
        else if ("7 ngày qua".equals(timeSel)) from = today.minusDays(6);
        else if ("30 ngày qua".equals(timeSel)) from = today.minusDays(29);

        List<BillDTO> res = new ArrayList<>();
        for (BillDTO b : allBills) {
            String pName = b.getPaymentMethod() != null ? b.getPaymentMethod().name() : "---";
            if (!q.isEmpty() && !String.valueOf(b.getId()).toLowerCase().contains(q)) continue;
            if (from != null && (b.getPaidAt() == null || b.getPaidAt().toLocalDate().isBefore(from))) continue;
            if (paySel != null && !paySel.startsWith("Tất cả") && !paySel.equals(pName)) continue;
            res.add(b);
        }
        kpiRevenue.setText(MONEY_FMT.format(adminBUS.calculateTotalRevenue(res)));
        kpiBills.setText(INT_FMT.format(res.size()));
        kpiDiscount.setText(MONEY_FMT.format(adminBUS.calculateTotalDiscount(res)));
        kpiTax.setText(MONEY_FMT.format(adminBUS.calculateTotalTax(res)));
        updateChart(res);

        billPaging.setAll(res, resetPage);
        renderBillPage();
    }

    private void renderBillPage() {
        List<BillDTO> currentPage = billPaging.slice();
        billModel.setRowCount(0);
        for (BillDTO b : currentPage) {
            billModel.addRow(new Object[]{
                    "#" + b.getId(), "Phiên #" + b.getTableSessionId(),
                    MONEY_FMT.format(b.getDiscount()), MONEY_FMT.format(b.getTax()),
                    MONEY_FMT.format(b.getTotalAmount()),
                    b.getPaymentMethod() != null ? b.getPaymentMethod().name() : "---",
                    b.getPaidAt() != null ? b.getPaidAt().format(TIME_FMT) : "---"
            });
        }
        billPaging.refreshBar();
    }

    private void updateChart(List<BillDTO> bills) {
        TreeMap<LocalDate, Double> perDay = new TreeMap<>();
        for (BillDTO b : bills) {
            if (b.getPaidAt() == null) continue;
            perDay.merge(b.getPaidAt().toLocalDate(), b.getTotalAmount() instanceof Number ? ((Number) b.getTotalAmount()).doubleValue() : 0, Double::sum);
        }
        List<LocalDate> days = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        if (!perDay.isEmpty()) {
            LocalDate last = perDay.lastKey();
            LocalDate first = perDay.firstKey();
            if (first.isBefore(last.minusDays(29))) first = last.minusDays(29);
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                days.add(d);
                values.add(perDay.getOrDefault(d, 0.0));
            }
        }
        revenueChart.setData(days, values);
    }

    // TRANG 4: TÀI KHOẢN (dữ liệu lấy qua AdminBUS)
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
        kpis.add(createKpiCard("NHÂN VIÊN", kpiAccStaff, Utils.SAFE_GREEN, NavIcon.USER));
        kpis.add(createKpiCard("ĐANG KHÓA", kpiAccLocked, Utils.DANGER_RED, NavIcon.LOCK));
        page.add(kpis, BorderLayout.NORTH);

        txtAccSearch = createSearchField("Tìm theo tên, SĐT...", 300);
        cboAccRole = createFilterCombo("Tất cả vai trò", ROLE_MEMBER, ROLE_STAFF);
        cboAccStatus = createFilterCombo("Tất cả trạng thái", ST_ACTIVE, ST_LOCKED);
        onTextChange(txtAccSearch, () -> applyAccountFilter(true));
        cboAccRole.addActionListener(e -> {
            if (!adjusting) applyAccountFilter(true);
        });
        cboAccStatus.addActionListener(e -> {
            if (!adjusting) applyAccountFilter(true);
        });

        JButton btnNew = createRoundedButton("+  Cấp tài khoản mới", Utils.TERRACOTTA, Color.WHITE, 190, 40);
        btnNew.addActionListener(e -> openAccountDialog(null));

        accModel = readOnlyModel("ID", "Họ tên", "SĐT", "Email", "Vai trò", "Trạng thái", "Thao tác");
        CTable accTableComponent = createStyledTable(accModel, 48);
        JTable accTable = accTableComponent.getTable();
        accTable.getColumnModel().getColumn(4).setCellRenderer(new BadgeRenderer()); // Cột Vai trò
        accTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer()); // Cột Trạng thái
        installActionColumn(accTable, 6, (row, idx) -> {
            if (row < 0 || row >= pageAccounts.size()) return;
            UserDTO u = pageAccounts.get(row);
            if (idx == 0) openAccountDialog(u);
            else toggleLock(u);
        });
        setColumnWidths(accTable, 60, 200, 120, 200, 130, 140, 170);

        accPaging = new Paging<>(8, this::renderAccountPage);
        page.add(createTableCard(createToolbar(flow(FlowLayout.LEFT, txtAccSearch, cboAccRole, cboAccStatus),
                flow(FlowLayout.RIGHT, btnNew)), accTableComponent, accPaging.bar), BorderLayout.CENTER);
        return page;
    }

    private void loadAccounts() {
        new PageLoadWorker<List<UserDTO>>() {
            @Override
            protected List<UserDTO> doInBackground() {
                return adminBUS.getAllAccounts();
            }

            @Override
            protected void onLoaded(List<UserDTO> result) {
                allAccounts = copyOrEmpty(result);
                applyAccountFilter(false);
            }
        }.execute();
    }

    private static boolean isMemberAccount(UserDTO account) {
        String role = account.getRoleName();
        return "CUSTOMER".equalsIgnoreCase(role)
                || "MEMBER".equalsIgnoreCase(role)
                || ROLE_MEMBER.equalsIgnoreCase(role);
    }

    private static String accountRoleLabel(UserDTO account) {
        return isMemberAccount(account) ? ROLE_MEMBER : ROLE_STAFF;
    }

    private void applyAccountFilter(boolean resetPage) {
        int members = 0, staff = 0, locked = 0;
        for (UserDTO u : allAccounts) {
            if (isMemberAccount(u)) members++;
            else staff++;

            if (Boolean.FALSE.equals(u.getIsActive())) locked++;
        }
        kpiAccTotal.setText(String.valueOf(allAccounts.size()));
        kpiAccMember.setText(String.valueOf(members));
        kpiAccStaff.setText(String.valueOf(staff));
        kpiAccLocked.setText(String.valueOf(locked));

        String q = txtAccSearch.getText().trim().toLowerCase();
        String role = (String) cboAccRole.getSelectedItem();
        String st = (String) cboAccStatus.getSelectedItem();

        List<UserDTO> res = new ArrayList<>();
        for (UserDTO u : allAccounts) {
            String name = u.getFullName() != null ? u.getFullName().toLowerCase() : "";
            String phone = u.getPhone() != null ? u.getPhone().toLowerCase() : "";
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
        pageAccounts = accPaging.slice();
        accModel.setRowCount(0);
        for (UserDTO u : pageAccounts) {
            String rName = accountRoleLabel(u);
            boolean isLocked = Boolean.FALSE.equals(u.getIsActive());

            accModel.addRow(new Object[]{
                    u.getId(), u.getFullName(), u.getPhone(), u.getEmail(), rName,
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

        JComboBox<String> roleBox = new JComboBox<>(new String[]{ROLE_MEMBER, ROLE_STAFF});
        styleCombo(roleBox);

        if (edit) {
            String rName = accountRoleLabel(acc);
            roleBox.setSelectedItem(rName);
            roleBox.setEnabled(false);
        }

        JPanel form = newFormPanel();
        addField(form, "Họ và tên", name);
        addField(form, "Số điện thoại", phone);
        addField(form, "Địa chỉ Email (Tùy chọn)", email);
        addField(form, "Vai trò", roleBox);

        showModal(edit ? "Cập nhật tài khoản" : "Cấp tài khoản mới",
                edit ? "Chỉnh sửa thông tin liên hệ của tài khoản." : "Mật khẩu mặc định sẽ là số điện thoại của người dùng.",
                form, edit ? "Lưu thay đổi" : "Cấp tài khoản", Utils.TERRACOTTA, () -> {

                    String n = name.getText().trim();
                    String p = phone.getText().trim();
                    String e = email.getText().trim();
                    String roleStr = (String) roleBox.getSelectedItem();

                    if (n.isEmpty()) return "Vui lòng nhập họ tên.";
                    if (!p.matches("(0|\\+84)\\d{9}")) return "Số điện thoại không hợp lệ (VD: 0901234567).";

                    // Kiểm tra roleId theo bảng roles trong CSDL trước khi triển khai.
                    Long rId = ROLE_STAFF.equals(roleStr) ? 2L : 3L;
                    String rNameDB = ROLE_STAFF.equals(roleStr) ? "STAFF" : "CUSTOMER";

                    performUpdate(edit ? "Cập nhật tài khoản" : "Cấp tài khoản", () -> {
                        if (edit) {
                            return adminBUS.updateAccount(acc.getId(), n, p, e);
                        } else {
                            return adminBUS.createNewAccount(n, p, e, rId, rNameDB);
                        }
                    }, this::loadAccounts);

                    return null;
                });
    }

    private void toggleLock(UserDTO u) {
        boolean isLocked = Boolean.FALSE.equals(u.getIsActive());
        String action = isLocked ? "mở khóa" : "khóa";
        if (confirmAction("Xác nhận",
                "Bạn có chắc muốn " + action + " tài khoản của " + u.getFullName() + "?")) {
            performUpdate("Chuyển trạng thái tài khoản",
                    () -> adminBUS.lockAccount(u.getId()), this::loadAccounts);
        }
    }

    // THÀNH PHẦN DÙNG CHUNG: CARD, KPI, TOOLBAR, MODAL, FORM
    private JPanel createCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
                g2.setColor(BORDER_SOFT);
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
        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private JPanel createTableCard(JComponent toolbar, CTable tableComponent, JComponent footer) {
        JPanel card = createCard();
        card.setLayout(new BorderLayout(0, 14));
        if (toolbar != null) card.add(toolbar, BorderLayout.NORTH);
        tableComponent.setBackground(Color.WHITE);
        tableComponent.setBorder(new LineBorder(BORDER_SOFT, 1));
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
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
    }

    private JTextField createSearchField(String placeholder, int width) {
        return new CTextField(placeholder, width, 40, font(Font.PLAIN, 13));
    }

    /** Cập nhật lựa chọn bộ lọc từ dữ liệu server, tránh tự kích hoạt lọc giữa chừng. */
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
                if (java.util.Objects.equals(combo.getItemAt(i), selected)) {
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
        JComboBox<String> c = new JComboBox<>(items);
        c.setFont(font(Font.PLAIN, 13));
        c.setBackground(Color.WHITE);
        c.setPreferredSize(new Dimension(180, 40));
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
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
        // CButton hiện có màu cam cố định; giữ màu riêng cho nút xanh/trung tính.
        if (Utils.TERRACOTTA.equals(bg) && Color.WHITE.equals(fg)) {
            return new CButton(text, w, h, font(Font.BOLD, 13));
        }
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color c = !isEnabled() ? new Color(225, 228, 226) : getModel().isRollover() ? bg.brighter() : bg;
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
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

    /** Hộp xác nhận dùng JDialog và các component hiện có. */
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

        JButton cancel = createRoundedButton("Hủy", NEUTRAL_BTN, Utils.TEXT_DARK, 100, 40);
        JButton confirm = new CButton("Xác nhận", 132, 40, font(Font.BOLD, 13));
        boolean[] accepted = {false};
        cancel.addActionListener(e -> dialog.dispose());
        confirm.addActionListener(e -> {
            accepted[0] = true;
            dialog.dispose();
        });
        JPanel controls = flow(FlowLayout.RIGHT, cancel, confirm);
        content.add(controls, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(confirm);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return accepted[0];
    }

    private void showModal(String title, String desc, JPanel form, String okText, Color okColor, Supplier<String> onSubmit) {
        JDialog d = new JDialog(this, title, true);
        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(Color.WHITE);
        root.setBorder(new EmptyBorder(26, 30, 22, 30));

        JPanel head = new JPanel();
        head.setLayout(new BoxLayout(head, BoxLayout.Y_AXIS));
        head.setOpaque(false);
        JLabel t = new JLabel(title);
        t.setFont(font(Font.BOLD, 20));
        t.setForeground(Utils.TEXT_DARK);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel s = new JLabel(desc);
        s.setFont(font(Font.PLAIN, 13));
        s.setForeground(Utils.TEXT_MUTED);
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        head.add(t);
        head.add(Box.createVerticalStrut(4));
        head.add(s);

        JLabel err = new JLabel(" ");
        err.setFont(font(Font.PLAIN, 12));
        err.setForeground(Utils.DANGER_RED);

        JButton ok = createRoundedButton(okText, okColor, Color.WHITE, 150, 42);
        JButton cancel = createRoundedButton("Hủy", NEUTRAL_BTN, Utils.TEXT_DARK, 96, 42);
        cancel.addActionListener(e -> d.dispose());
        ok.addActionListener(e -> {
            String msg = null;
            try {
                msg = onSubmit.get();
            } catch (RuntimeException ex) {
                msg = "Lỗi: " + ex.getMessage();
            }
            if (msg == null) d.dispose();
            else {
                err.setText(msg);
                d.pack();
            }
        });
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        btns.add(cancel);
        btns.add(ok);
        JPanel foot = new JPanel(new BorderLayout(0, 8));
        foot.setOpaque(false);
        foot.add(err, BorderLayout.NORTH);
        foot.add(btns, BorderLayout.CENTER);

        root.add(head, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        root.add(foot, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.getRootPane().setDefaultButton(ok);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.setResizable(false);
        d.pack();
        d.setLocationRelativeTo(this);
        d.setVisible(true);
    }

    // BẢNG VÀ RENDERER
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
        table.setSelectionBackground(SELECT_BG);
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
                l.setBorder(BorderFactory.createCompoundBorder(new MatteBorder(0, 0, 1, 0, BORDER_SOFT), new EmptyBorder(0, 12, 0, 8)));
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
    private Color rowBackground(JTable t, boolean selected, int row) {
        if (selected) return SELECT_BG;
        Object tint = t.getClientProperty("rowTint");
        if (tint instanceof IntFunction) {
            Color c = ((IntFunction<Color>) tint).apply(row);
            if (c != null) return c;
        }
        Object h = t.getClientProperty("hoverRow");
        if (h instanceof Integer && (Integer) h == row) return HOVER_ROW;
        return Color.WHITE;
    }

    private class RowRenderer extends DefaultTableCellRenderer {
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

    private class BadgeRenderer extends JPanel implements TableCellRenderer {
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

    private static Color[] badgeColors(String text) {
        switch (text) {
            case "Đang mở bán":
            case "Đủ hàng":
            case ST_ACTIVE:
                return new Color[]{new Color(226, 240, 232),Utils.SAFE_GREEN};
            case "Sắp hết":
            case ST_LOCKED:
                return new Color[]{new Color(252, 230, 230), Utils.DANGER_RED};
            case "Tạm ngưng":
                return new Color[]{new Color(238, 240, 238), new Color(105, 116, 108)};
            case ROLE_STAFF:
                return new Color[]{new Color(222, 232, 226), Utils.FOREST_BG};
            default:
                return new Color[]{new Color(250, 238, 224), new Color(150, 95, 40)};
        }
    }

    private class ThumbRenderer extends JPanel implements TableCellRenderer {
        private String name = "";
        private Color rowBg = Color.WHITE;

        ThumbRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int r, int c) {
            name = v == null ? "" : v.toString();
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
            Color[] palette = {Utils.FOREST_BG, Utils.TERRACOTTA, Utils.GOLD_ACCENT, Utils.SAFE_GREEN};
            int size = 36, x = 14, y = (getHeight() - size) / 2;
            g2.setColor(palette[Math.floorMod(name.hashCode(), palette.length)]);
            g2.fillRoundRect(x, y, size, size, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(font(Font.BOLD, 15));
            String ch = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(ch, x + (size - fm.stringWidth(ch)) / 2, y + (size + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    private static final Font ACTION_FONT = font(Font.BOLD, 12);
    private static final int ACTION_GAP = 18;
    private static final int ACTION_PAD = 12;

    private static Color actionColor(String label) {
        if ("Xóa".equals(label) || "Khóa".equals(label)) return Utils.DANGER_RED;
        if ("Mở khóa".equals(label)) return Utils.SAFE_GREEN;
        return Utils.TERRACOTTA;
    }

    private class ActionRenderer extends JPanel implements TableCellRenderer {
        private String labels = "";
        private Color cellBg = Color.WHITE;

        ActionRenderer() {
            setOpaque(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean selected, boolean focused, int row, int column) {
            labels = value == null ? "" : value.toString();
            cellBg = rowBackground(table, selected, row);
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(cellBg);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setFont(ACTION_FONT);
                FontMetrics fm = g.getFontMetrics();
                int x = ACTION_PAD;
                for (String label : labels.split("\\|")) {
                    g.setColor(actionColor(label));
                    g.drawString(label, x, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    x += fm.stringWidth(label) + ACTION_GAP;
                }
            } finally {
                g.dispose();
            }
        }
    }

    private void installActionColumn(JTable table, int col, java.util.function.BiConsumer<Integer, Integer> handler) {
        table.getColumnModel().getColumn(col).setCellRenderer(new ActionRenderer());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int r = table.rowAtPoint(e.getPoint());
                int c = table.columnAtPoint(e.getPoint());
                if (r < 0 || c < 0 || table.convertColumnIndexToModel(c) != col) return;
                String[] labels = String.valueOf(table.getValueAt(r, c)).split("\\|");
                FontMetrics fm = table.getFontMetrics(ACTION_FONT);
                int x = table.getCellRect(r, c, false).x + ACTION_PAD;
                for (int i = 0; i < labels.length; i++) {
                    int w = fm.stringWidth(labels[i]);
                    if (e.getX() >= x - 6 && e.getX() <= x + w + 6) {
                        handler.accept(table.convertRowIndexToModel(r), i);
                        return;
                    }
                    x += w + ACTION_GAP;
                }
            }
        });
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                table.setCursor(Cursor.getPredefinedCursor((table.columnAtPoint(e.getPoint()) >= 0 && table.convertColumnIndexToModel(table.columnAtPoint(e.getPoint())) == col)
                        ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        });
    }

    // PHÂN TRANG
    private class Paging<T> {
        final int pageSize;
        final Runnable onChange;
        List<T> all = new ArrayList<>();
        int page = 0;
        final JPanel bar = new JPanel(new BorderLayout());
        final JLabel info = new JLabel(), pageLbl = new JLabel();
        final JButton prev = createRoundedButton("‹ Trước", NEUTRAL_BTN, Utils.TEXT_DARK, 92, 34);
        final JButton next = createRoundedButton("Sau ›", NEUTRAL_BTN, Utils.TEXT_DARK, 92, 34);

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

        void setAll(List<T> items, boolean reset) {
            all = items;
            page = reset ? 0 : Math.min(page, totalPages() - 1);
        }

        List<T> slice() {
            return new ArrayList<>(all.subList(Math.min(page * pageSize, all.size()), Math.min((page + 1) * pageSize, all.size())));
        }

        void refreshBar() {
            info.setText("Hiển thị " + (all.isEmpty() ? 0 : page * pageSize + 1) + "–" + Math.min((page + 1) * pageSize, all.size()) + " / " + all.size() + " bản ghi");
            pageLbl.setText("Trang " + (page + 1) + " / " + totalPages());
            prev.setEnabled(page > 0);
            next.setEnabled(page < totalPages() - 1);
        }
    }

    // BIỂU ĐỒ DOANH THU
    private class RevenueChart extends JPanel {
        private List<LocalDate> days = new ArrayList<>();
        private List<Double> values = new ArrayList<>();
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

        void setData(List<LocalDate> d, List<Double> v) {
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
            return new Rectangle(66, 10, Math.max(10, getWidth() - 66 - 12), Math.max(10, getHeight() - 10 - 28));
        }

        private int indexAt(int x) {
            if (days.isEmpty()) return -1;
            Rectangle p = plot();
            int i = (int) ((x - p.x) / (p.width / (double) days.size()));
            return (x < p.x || i < 0 || i >= days.size()) ? -1 : i;
        }

        private double niceCeil(double v) {
            double pow = Math.pow(10, Math.floor(Math.log10(v)));
            double n = v / pow;
            return (n <= 1 ? 1 : n <= 2 ? 2 : n <= 5 ? 5 : 10) * pow;
        }

        private String shortMoney(double v) {
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
            for (double v : values) max = Math.max(max, v);
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
                ys[i] = base - (int) (p.height * values.get(i) / top);
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
                String t1 = days.get(hover).format(DAY_FULL_FMT), t2 = MONEY_FMT.format(values.get(hover));
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

    // ICONS (Đồ hoạ vẽ tay)
    static class NavIcon implements Icon {
        static final int FOOD = 0, BOX = 1, CHART = 2, USER = 3, CHECK = 4, PAUSE = 5, ALERT = 6, RECEIPT = 7, PERCENT = 8, COIN = 9, LOCK = 10;
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
                case FOOD:
                    g2.drawArc(x + 2, y + 4, 16, 14, 180, 180);
                    g2.drawLine(x + 1, y + 11, x + 19, y + 11);
                    g2.drawLine(x + 7, y + 1, x + 7, y + 5);
                    g2.drawLine(x + 13, y + 1, x + 13, y + 5);
                    break;
                case BOX:
                    g2.drawRoundRect(x + 2, y + 5, 16, 12, 3, 3);
                    g2.drawLine(x + 2, y + 9, x + 18, y + 9);
                    g2.drawLine(x + 8, y + 12, x + 12, y + 12);
                    break;
                case CHART:
                    g2.fillRoundRect(x + 3, y + 10, 4, 8, 2, 2);
                    g2.fillRoundRect(x + 8, y + 4, 4, 14, 2, 2);
                    g2.fillRoundRect(x + 13, y + 7, 4, 11, 2, 2);
                    break;
                case USER:
                    g2.drawOval(x + 6, y + 2, 8, 8);
                    g2.drawArc(x + 2, y + 12, 16, 14, 0, 180);
                    break;
                case CHECK:
                    g2.drawOval(x + 2, y + 2, 16, 16);
                    g2.drawPolyline(new int[]{x + 6, x + 9, x + 14}, new int[]{y + 10, y + 13, y + 7}, 3);
                    break;
                case PAUSE:
                    g2.drawOval(x + 2, y + 2, 16, 16);
                    g2.fillRect(x + 7, y + 6, 2, 8);
                    g2.fillRect(x + 11, y + 6, 2, 8);
                    break;
                case ALERT:
                    g2.drawPolygon(new int[]{x + 10, x + 2, x + 18}, new int[]{y + 2, y + 17, y + 17}, 3);
                    g2.drawLine(x + 10, y + 7, x + 10, y + 12);
                    g2.fillOval(x + 9, y + 14, 2, 2);
                    break;
                case RECEIPT:
                    g2.drawRoundRect(x + 4, y + 2, 12, 16, 3, 3);
                    g2.drawLine(x + 7, y + 7, x + 13, y + 7);
                    g2.drawLine(x + 7, y + 11, x + 13, y + 11);
                    break;
                case PERCENT:
                    g2.drawLine(x + 15, y + 4, x + 5, y + 16);
                    g2.drawOval(x + 3, y + 3, 5, 5);
                    g2.drawOval(x + 12, y + 12, 5, 5);
                    break;
                case COIN:
                    g2.drawOval(x + 2, y + 2, 16, 16);
                    g2.drawLine(x + 10, y + 6, x + 10, y + 14);
                    g2.drawLine(x + 7, y + 8, x + 13, y + 8);
                    break;
                case LOCK:
                    g2.drawRoundRect(x + 4, y + 9, 12, 8, 3, 3);
                    g2.drawArc(x + 6, y + 3, 8, 12, 0, 180);
                    break;
            }
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
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

    private static class AvatarIcon implements Icon {
        private final int size;
        private final String letter;
        private final Color bg;

        AvatarIcon(int size, String letter, Color bg) {
            this.size = size;
            this.letter = letter;
            this.bg = bg;
        }

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

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }

    static class BrandLogoIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Utils.TERRACOTTA);
            g2.fillRoundRect(x, y, 42, 42, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("⌂", x + (42 - fm.stringWidth("⌂")) / 2, y + ((42 - fm.getHeight()) / 2) + fm.getAscent());
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 42;
        }

        @Override
        public int getIconHeight() {
            return 42;
        }
    }

    // ĐĂNG NHẬP (TÍCH HỢP GỌI TCP SERVER)
    public static void showAdminLogin() {
        JDialog dialog = new JDialog((Frame) null, "Đăng nhập quản trị — An Nhiên", true);
        JPanel body = new JPanel(new BorderLayout(12, 12));
        body.setBorder(new EmptyBorder(18, 22, 18, 22));

        JPanel fields = new JPanel(new GridLayout(2, 2, 10, 10));
        JTextField account = new CTextField("Email hoặc SĐT", 270, 42, font(Font.PLAIN, 14));
        JPasswordField password = new CPasswordField("Mật khẩu", 270, 42, font(Font.PLAIN, 14));
        fields.add(new JLabel("Email / SĐT ADMIN:"));
        fields.add(account);
        fields.add(new JLabel("Mật khẩu:"));
        fields.add(password);

        JLabel feedback = new JLabel("Đăng nhập qua máy chủ TCP với tài khoản ADMIN.");
        JButton signIn = new CButton("Đăng nhập", 125, 40, font(Font.BOLD, 13));
        JButton cancel = new JButton("Hủy");
        cancel.addActionListener(e -> dialog.dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(cancel);
        footer.add(signIn);

        signIn.addActionListener(e -> {
            char[] pass = password.getPassword();
            String user = account.getText().trim();
            if (user.isEmpty() || pass.length == 0) {
                java.util.Arrays.fill(pass, '\0');
                feedback.setText("Vui lòng điền tài khoản và mật khẩu");
                return;
            }
            String secret = new String(pass);
            java.util.Arrays.fill(pass, '\0');
            password.setText("");
            signIn.setEnabled(false);
            feedback.setText("Đang xác thực qua mạng...");

            new SwingWorker<Object[], Void>() {
                @Override
                protected Object[] doInBackground() {
                    SocketClient socket = new SocketClient();
                    try {
                        UserDTO admin = AuthBUS.loginWorkstation(socket, user, secret);
                        if (!"ADMIN".equalsIgnoreCase(admin.getRoleName()))
                            throw new SecurityException("Tài khoản không có quyền ADMIN");
                        return new Object[]{admin, socket};
                    } catch (RuntimeException ex) {
                        socket.close();
                        throw ex;
                    }
                }

                @Override
                protected void done() {
                    signIn.setEnabled(true);
                    try {
                        Object[] result = get();
                        UserDTO admin = (UserDTO) result[0];
                        SocketClient socket = (SocketClient) result[1];
                        if (!dialog.isDisplayable()) {
                            socket.close();
                            return;
                        }
                        dialog.dispose();
                        new AdminGUI(admin, socket).setVisible(true);
                    } catch (Exception ex) {
                        Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                        feedback.setText("Lỗi: " + cause.getMessage());
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AdminGUI::showAdminLogin);
    }
    /** Load logo via TCP IMG/1, khong doc file local va khong block Swing EDT. */
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
                // ImageSocketClient gui IMG/1 things/logo.png, nhan cac block byte nhi phan.
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
