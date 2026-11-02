package gui;

import bus.AdminBUS;
import dto.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

public class AdminGUI extends JFrame {

    // Bảng màu chuẩn "Bếp Nhà Restaurant" đồng bộ từ AuthFrame
    private static final Color FOREST_BG = new Color(32, 43, 37);
    private static final Color FOREST_CIRCLE_1 = new Color(29, 41, 34);
    private static final Color FOREST_CIRCLE_2 = new Color(33, 45, 38);
    private static final Color TERRACOTTA = new Color(206, 93, 52);
    private static final Color GOLD_ACCENT = new Color(201, 142, 84);
    private static final Color CREAM_BG = new Color(247, 248, 246);
    private static final Color TEXT_DARK = new Color(23, 31, 26);
    private static final Color TEXT_MUTED = new Color(140, 150, 145);
    private static final Color SAFE_GREEN = new Color(38, 92, 62);
    private static final Color DANGER_RED = new Color(185, 45, 45);

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### VNĐ");
    private static final DecimalFormat QTY_FMT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final AdminBUS adminBUS = new AdminBUS();

    // Sidebar doc + CardLayout: giu nguyen cac trang va xu ly BUS hien co.
    private static final String PAGE_FOODS = "FOODS";
    private static final String PAGE_INVENTORY = "INVENTORY";
    private static final String PAGE_REVENUE = "REVENUE";
    private final CardLayout contentCardLayout = new CardLayout();
    private final JPanel contentCards = new JPanel(contentCardLayout);
    private final Map<String, JButton> sidebarButtons = new LinkedHashMap<>();
    private JLabel lblPageTitle;
    private JLabel lblPageSubtitle;
    private String activePage = PAGE_FOODS;

    // Components Tab 1: Quản lý Món ăn
    private DefaultTableModel foodTableModel;
    private JTable foodTable;
    private JTextField txtSku, txtFoodName, txtPrice, txtCoverUrl;
    private JComboBox<CategoryDTO> cboCategory;
    private JCheckBox chkPublished;

    // Components Tab 2: Cảnh báo Kho nguyên liệu
    private DefaultTableModel stockTableModel;
    private JTable stockTable;
    private JLabel lblLowStockBanner;

    // Components Tab 3: Thống kê Doanh thu
    private DefaultTableModel billTableModel;
    private JLabel lblCardTotalRevenue;
    private JLabel lblCardTotalBills;
    private JLabel lblCardTotalDiscount;
    private JLabel lblCardTotalTax;

    public AdminGUI() {
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setTitle("Bếp Nhà Restaurant - Hệ Thống Quản Trị Trung Tâm (Admin Dashboard)");
        setSize(1360, 820);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(CREAM_BG);

        initBrandHeader();

        // Bo cuc moi: menu doc ben trai, noi dung dang card ben phai.
        // Khoi tao du ca 3 trang truoc khi truy van du lieu de tranh NullPointerException.
        contentCards.setBackground(CREAM_BG);
        contentCards.add(createFoodManagementTab(), PAGE_FOODS);
        contentCards.add(createInventoryAlertTab(), PAGE_INVENTORY);
        contentCards.add(createRevenueAnalyticsTab(), PAGE_REVENUE);

        JPanel workspace = new JPanel(new BorderLayout(0, 0));
        workspace.setBackground(CREAM_BG);
        workspace.add(createSidebar(), BorderLayout.WEST);
        workspace.add(createMainContentArea(), BorderLayout.CENTER);
        add(workspace, BorderLayout.CENTER);

        showPage(PAGE_FOODS);
    }

    // =========================================================================
    // NAVIGATION: SIDEBAR DOC BEN TRAI / NOI DUNG CARDLAYOUT BEN PHAI
    // =========================================================================
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 20)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FOREST_CIRCLE_1);
                g2.fillOval(-145, getHeight() - 245, 330, 330);
                g2.setColor(FOREST_CIRCLE_2);
                g2.fillOval(-65, getHeight() - 170, 220, 220);
                g2.dispose();
            }
        };
        sidebar.setBackground(FOREST_BG);
        sidebar.setPreferredSize(new Dimension(256, 0));
        sidebar.setBorder(new EmptyBorder(26, 16, 24, 16));

        JPanel sidebarTop = new JPanel();
        sidebarTop.setLayout(new BoxLayout(sidebarTop, BoxLayout.Y_AXIS));
        sidebarTop.setOpaque(false);

        JLabel menuCaption = new JLabel("KHÔNG GIAN QUẢN TRỊ");
        menuCaption.setFont(getCustomFont("Inter", Font.BOLD, 11));
        menuCaption.setForeground(GOLD_ACCENT);
        menuCaption.setBorder(new EmptyBorder(0, 11, 10, 0));
        menuCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarTop.add(menuCaption);

        JLabel lblMenu = new JLabel("DANH MỤC CHỨC NĂNG");
        lblMenu.setFont(getCustomFont("Inter", Font.PLAIN, 11));
        lblMenu.setForeground(TEXT_MUTED);
        lblMenu.setBorder(new EmptyBorder(18, 11, 12, 0));
        lblMenu.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarTop.add(lblMenu);

        sidebarTop.add(createSidebarButton("🍲  Quản lý thực đơn", PAGE_FOODS));
        sidebarTop.add(Box.createVerticalStrut(10));
        sidebarTop.add(createSidebarButton("📦  Kho nguyên liệu", PAGE_INVENTORY));
        sidebarTop.add(Box.createVerticalStrut(10));
        sidebarTop.add(createSidebarButton("📊  Thống kê doanh thu", PAGE_REVENUE));

        sidebar.add(sidebarTop, BorderLayout.NORTH);

        JPanel sidebarFooter = new JPanel();
        sidebarFooter.setLayout(new BoxLayout(sidebarFooter, BoxLayout.Y_AXIS));
        sidebarFooter.setOpaque(false);
        JLabel lblFooter = new JLabel("AN NHIÊN  •  ADMIN");
        lblFooter.setForeground(GOLD_ACCENT);
        lblFooter.setFont(getCustomFont("Inter", Font.BOLD, 12));
        lblFooter.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lblFooterDesc = new JLabel("Quản trị nhà hàng");
        lblFooterDesc.setForeground(new Color(168, 182, 172));
        lblFooterDesc.setFont(getCustomFont("Inter", Font.PLAIN, 11));
        lblFooterDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebarFooter.add(lblFooter);
        sidebarFooter.add(Box.createVerticalStrut(5));
        sidebarFooter.add(lblFooterDesc);
        sidebar.add(sidebarFooter, BorderLayout.SOUTH);
        return sidebar;
    }

    private JButton createSidebarButton(String text, String pageId) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean selected = pageId.equals(activePage);
                if (selected) {
                    g2.setColor(TERRACOTTA);
                } else if (getModel().isRollover()) {
                    g2.setColor(new Color(54, 71, 60));
                } else {
                    g2.setColor(new Color(40, 56, 46));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                if (selected) {
                    g2.setColor(GOLD_ACCENT);
                    g2.fillRoundRect(0, 8, 4, Math.max(0, getHeight() - 16), 4, 4);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setBorder(new EmptyBorder(0, 13, 0, 8));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(224, 50));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 50));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(e -> showPage(pageId));
        sidebarButtons.put(pageId, button);
        return button;
    }

    private JPanel createMainContentArea() {
        JPanel content = new JPanel(new BorderLayout(0, 15));
        content.setBackground(CREAM_BG);
        content.setBorder(new EmptyBorder(19, 22, 18, 22));

        JPanel pageHeader = new JPanel(new BorderLayout(0, 4));
        pageHeader.setOpaque(false);
        pageHeader.setBorder(new EmptyBorder(0, 4, 4, 0));

        lblPageTitle = new JLabel();
        lblPageTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 23));
        lblPageTitle.setForeground(TEXT_DARK);

        lblPageSubtitle = new JLabel();
        lblPageSubtitle.setFont(getCustomFont("Inter", Font.PLAIN, 12));
        lblPageSubtitle.setForeground(new Color(105, 116, 108));

        pageHeader.add(lblPageTitle, BorderLayout.NORTH);
        pageHeader.add(lblPageSubtitle, BorderLayout.SOUTH);
        content.add(pageHeader, BorderLayout.NORTH);
        content.add(contentCards, BorderLayout.CENTER);
        return content;
    }

    private void showPage(String pageId) {
        activePage = pageId;
        contentCardLayout.show(contentCards, pageId);
        for (JButton button : sidebarButtons.values()) button.repaint();
        switch (pageId) {
            case PAGE_INVENTORY:
                lblPageTitle.setText("Kho Nguyên Liệu & Cảnh Báo Tồn");
                lblPageSubtitle.setText("Theo dõi định mức, tồn kho và bổ sung nguyên liệu.");
                loadInventoryAndAlerts();
                break;
            case PAGE_REVENUE:
                lblPageTitle.setText("Thống Kê Doanh Thu");
                lblPageSubtitle.setText("Tổng hợp doanh thu, ưu đãi, thuế và lịch sử hóa đơn.");
                loadRevenueStatistics();
                break;
            case PAGE_FOODS:
            default:
                lblPageTitle.setText("Quản Lý Thực Đơn");
                lblPageSubtitle.setText("Thêm, chỉnh sửa và quản lý trạng thái bán các món ăn.");
                loadCategoriesAndFoods();
                break;
        }
    }

    // =========================================================================
    // HEADER ĐỒNG BỘ AUTHFRAME (Xanh rừng + Họa tiết vòng tròn + Logo Bếp Nhà)
    // =========================================================================
    private void initBrandHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FOREST_CIRCLE_1);
                g2.fillOval(getWidth() - 420, -220, 520, 520);
                g2.setColor(FOREST_CIRCLE_2);
                g2.fillOval(getWidth() - 300, -140, 360, 360);
            }
        };
        header.setBackground(FOREST_BG);
        header.setPreferredSize(new Dimension(getWidth(), 82));
        header.setBorder(new EmptyBorder(14, 28, 14, 28));

        // Cụm Logo + Tiêu đề bên trái
        JPanel leftBox = new JPanel();
        leftBox.setLayout(new BoxLayout(leftBox, BoxLayout.X_AXIS));
        leftBox.setOpaque(false);

        JLabel lblIcon = new JLabel(new BrandLogoIcon());
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 14));

        JPanel textCol = new JPanel();
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
        textCol.setOpaque(false);

        JLabel lblTitle = new JLabel("Bếp Nhà  |  TRUNG TÂM QUẢN TRỊ NHÀ HÀNG");
        lblTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 21));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSubtitle = new JLabel("Quản lý thực đơn, định mức nguyên liệu kho và báo cáo tài chính thời gian thực");
        lblSubtitle.setFont(getCustomFont("Inter", Font.PLAIN, 13));
        lblSubtitle.setForeground(GOLD_ACCENT);

        textCol.add(lblTitle);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(lblSubtitle);

        leftBox.add(lblIcon);
        leftBox.add(textCol);

        // Cụm nút hành động bên phải
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        rightActions.setOpaque(false);

        JButton btnRefreshAll = createRoundedButton("🔄 Đồng Bộ Dữ Liệu", TERRACOTTA, Color.WHITE, 180, 42);
        btnRefreshAll.addActionListener(e -> refreshAllData());

        rightActions.add(btnRefreshAll);

        header.add(leftBox, BorderLayout.WEST);
        header.add(rightActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    // =========================================================================
    // TAB 1: QUẢN LÝ MÓN ĂN (FOODS)
    // =========================================================================
    private JPanel createFoodManagementTab() {
        JPanel panel = new JPanel(new BorderLayout(20, 0));
        panel.setBackground(CREAM_BG);
        panel.setBorder(new EmptyBorder(15, 5, 5, 5));

        // Khung bảng danh sách món ăn bên trái
        JPanel tableCard = createWhiteCardPanel();
        tableCard.setLayout(new BorderLayout(0, 12));

        JLabel lblTableTitle = new JLabel("Danh Sách Thực Đơn Bếp Nhà");
        lblTableTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 18));
        lblTableTitle.setForeground(TEXT_DARK);
        tableCard.add(lblTableTitle, BorderLayout.NORTH);

        String[] cols = {"ID", "Mã SKU", "Tên Món Ăn", "Danh Mục", "Đơn Giá", "Trạng Thái Bán", "Ảnh Bìa (URL)"};
        foodTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        foodTable = createStyledTable(foodTableModel);
        // Sidebar thu hep chieu ngang: cho phep cuon bang thay vi ep cot qua nho.
        foodTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        foodTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        foodTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        foodTable.getColumnModel().getColumn(2).setPreferredWidth(210);
        foodTable.getColumnModel().getColumn(3).setPreferredWidth(135);
        foodTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        foodTable.getColumnModel().getColumn(5).setPreferredWidth(145);
        foodTable.getColumnModel().getColumn(6).setPreferredWidth(200);

        foodTable.getSelectionModel().addListSelectionListener(e -> {
            int row = foodTable.getSelectedRow();
            if (row != -1) {
                txtSku.setText(foodTableModel.getValueAt(row, 1).toString());
                txtFoodName.setText(foodTableModel.getValueAt(row, 2).toString());
                String rawPrice = foodTableModel.getValueAt(row, 4).toString().replaceAll("[^0-9]", "");
                txtPrice.setText(rawPrice);
                String statusStr = foodTableModel.getValueAt(row, 5).toString();
                chkPublished.setSelected(statusStr.contains("Đang mở bán"));
                Object urlObj = foodTableModel.getValueAt(row, 6);
                txtCoverUrl.setText(urlObj != null ? urlObj.toString() : "");
            }
        });

        JScrollPane scrollFood = new JScrollPane(foodTable);
        scrollFood.setBorder(new LineBorder(new Color(232, 235, 232), 1));
        tableCard.add(scrollFood, BorderLayout.CENTER);

        // Form thêm/sửa bên phải
        JPanel formCard = createWhiteCardPanel();
        formCard.setPreferredSize(new Dimension(350, 0));
        formCard.setLayout(new BorderLayout(0, 15));

        JLabel lblFormTitle = new JLabel("Thông Tin Chi Tiết Món");
        lblFormTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 18));
        lblFormTitle.setForeground(TEXT_DARK);

        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setOpaque(false);

        txtSku = createStyledTextField("HDL-NEW-01");
        txtFoodName = createStyledTextField("");
        cboCategory = new JComboBox<>();
        cboCategory.setFont(getCustomFont("Inter", Font.PLAIN, 14));
        cboCategory.setBackground(Color.WHITE);
        cboCategory.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        cboCategory.setAlignmentX(Component.LEFT_ALIGNMENT);

        txtPrice = createStyledTextField("150000");
        txtCoverUrl = createStyledTextField("lau_bep_nha.jpg");

        chkPublished = new JCheckBox("Hiển thị mở bán trên Tablet khách", true);
        chkPublished.setFont(getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 13));
        chkPublished.setForeground(TEXT_DARK);
        chkPublished.setOpaque(false);
        chkPublished.setFocusPainted(false);
        chkPublished.setAlignmentX(Component.LEFT_ALIGNMENT);

        fields.add(createFieldLabel("Mã định danh món (SKU)"));
        fields.add(Box.createVerticalStrut(5));
        fields.add(txtSku);
        fields.add(Box.createVerticalStrut(12));

        fields.add(createFieldLabel("Tên món ăn"));
        fields.add(Box.createVerticalStrut(5));
        fields.add(txtFoodName);
        fields.add(Box.createVerticalStrut(12));

        fields.add(createFieldLabel("Danh mục thực đơn"));
        fields.add(Box.createVerticalStrut(5));
        fields.add(cboCategory);
        fields.add(Box.createVerticalStrut(12));

        fields.add(createFieldLabel("Đơn giá niêm yết (VNĐ)"));
        fields.add(Box.createVerticalStrut(5));
        fields.add(txtPrice);
        fields.add(Box.createVerticalStrut(12));

        fields.add(createFieldLabel("Đường dẫn ảnh bìa (URL)"));
        fields.add(Box.createVerticalStrut(5));
        fields.add(txtCoverUrl);
        fields.add(Box.createVerticalStrut(14));
        fields.add(chkPublished);

        JPanel btnGroup = new JPanel(new GridLayout(3, 1, 0, 10));
        btnGroup.setOpaque(false);

        JButton btnAdd = createRoundedButton("➕ Thêm Món Mới Vào Thực Đơn", TERRACOTTA, Color.WHITE, 350, 44);
        btnAdd.addActionListener(e -> handleAddFood());

        JButton btnUpdate = createRoundedButton("💾 Lưu Cập Nhật Món Đang Chọn", FOREST_BG, Color.WHITE, 350, 44);
        btnUpdate.addActionListener(e -> handleUpdateFood());

        JButton btnDelete = createRoundedButton("🗑️ Xóa Mềm Món Ăn (Ẩn Khỏi Menu)", DANGER_RED, Color.WHITE, 350, 44);
        btnDelete.addActionListener(e -> handleDeleteFood());

        btnGroup.add(btnAdd);
        btnGroup.add(btnUpdate);
        btnGroup.add(btnDelete);

        formCard.add(lblFormTitle, BorderLayout.NORTH);
        // Cuon form khi khung noi dung bi thu hep theo chieu doc.
        JScrollPane formScroll = new JScrollPane(fields,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setOpaque(false);
        formScroll.getViewport().setOpaque(false);
        formScroll.getVerticalScrollBar().setUnitIncrement(15);
        formCard.add(formScroll, BorderLayout.CENTER);
        formCard.add(btnGroup, BorderLayout.SOUTH);

        panel.add(tableCard, BorderLayout.CENTER);
        panel.add(formCard, BorderLayout.EAST);
        return panel;
    }

    // =========================================================================
    // TAB 2: KHO NGUYÊN LIỆU & CẢNH BÁO CHẠM ĐÁY (STOCK <= MIN_STOCK_LEVEL)
    // =========================================================================
    private JPanel createInventoryAlertTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBackground(CREAM_BG);
        panel.setBorder(new EmptyBorder(15, 5, 5, 5));

        lblLowStockBanner = new JLabel("✅ Tất cả nguyên liệu trong kho Bếp Nhà đều ở mức an toàn.");
        lblLowStockBanner.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblLowStockBanner.setOpaque(true);
        lblLowStockBanner.setBorder(new EmptyBorder(14, 20, 14, 20));

        JPanel tableCard = createWhiteCardPanel();
        tableCard.setLayout(new BorderLayout(0, 12));

        String[] cols = {"ID", "Tên Nguyên Liệu", "Đơn Vị Tính", "Tồn Kho Hiện Tại", "Định Mức Tối Thiểu", "Trạng Thái Cảnh Báo"};
        stockTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        stockTable = createStyledTable(stockTableModel);

        // Custom Renderer tô đỏ cảnh báo nguyên liệu sắp hết
        stockTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String statusText = table.getValueAt(row, 5).toString();
                if (!isSelected) {
                    if (statusText.contains("CẢNH BÁO")) {
                        c.setBackground(new Color(254, 242, 242));
                        c.setForeground(DANGER_RED);
                        setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
                    } else {
                        c.setBackground(Color.WHITE);
                        c.setForeground(TEXT_DARK);
                        setFont(getCustomFont("Inter", Font.PLAIN, 13));
                    }
                }
                return c;
            }
        });

        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 5));
        bottomBar.setOpaque(false);

        JButton btnAddIngredient = createRoundedButton("➕ Khai Báo Nguyên Liệu Mới", FOREST_BG, Color.WHITE, 250, 44);
        btnAddIngredient.addActionListener(e -> handleCreateNewIngredient());

        JButton btnRestock = createRoundedButton("📦 Nhập Thêm Tồn Kho Cho Nguyên Liệu Đang Chọn", TERRACOTTA, Color.WHITE, 390, 44);
        btnRestock.addActionListener(e -> handleRestockSelectedIngredient());

        bottomBar.add(btnAddIngredient);
        bottomBar.add(btnRestock);

        tableCard.add(new JScrollPane(stockTable), BorderLayout.CENTER);
        tableCard.add(bottomBar, BorderLayout.SOUTH);

        panel.add(lblLowStockBanner, BorderLayout.NORTH);
        panel.add(tableCard, BorderLayout.CENTER);
        return panel;
    }

    // =========================================================================
    // TAB 3: THỐNG KÊ DOANH THU TỪ BẢNG BILL
    // =========================================================================
    private JPanel createRevenueAnalyticsTab() {
        JPanel panel = new JPanel(new BorderLayout(0, 18));
        panel.setBackground(CREAM_BG);
        panel.setBorder(new EmptyBorder(15, 5, 5, 5));

        JPanel kpiGrid = new JPanel(new GridLayout(1, 4, 18, 0));
        kpiGrid.setOpaque(false);
        kpiGrid.setPreferredSize(new Dimension(0, 115));

        lblCardTotalRevenue = new JLabel("0 VNĐ", SwingConstants.LEFT);
        lblCardTotalBills = new JLabel("0 Hóa đơn", SwingConstants.LEFT);
        lblCardTotalDiscount = new JLabel("0 VNĐ", SwingConstants.LEFT);
        lblCardTotalTax = new JLabel("0 VNĐ", SwingConstants.LEFT);

        kpiGrid.add(createKpiCard("TỔNG DOANH THU THỰC THU", lblCardTotalRevenue, TERRACOTTA));
        kpiGrid.add(createKpiCard("TỔNG SỐ HÓA ĐƠN ĐÃ XUẤT", lblCardTotalBills, FOREST_BG));
        kpiGrid.add(createKpiCard("TỔNG TIỀN ƯU ĐÃI (VOUCHER)", lblCardTotalDiscount, GOLD_ACCENT));
        kpiGrid.add(createKpiCard("TỔNG THUẾ GTGT (VAT 8%)", lblCardTotalTax, SAFE_GREEN));

        JPanel tableCard = createWhiteCardPanel();
        tableCard.setLayout(new BorderLayout(0, 12));

        JLabel lblHistoryTitle = new JLabel("Lịch Sử Giao Dịch & Xuất Hóa Đơn (Bảng Bill)");
        lblHistoryTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 18));
        lblHistoryTitle.setForeground(TEXT_DARK);

        String[] cols = {"Mã hóa đơn", "Mã phiên bàn", "Giảm giá", "Thuế VAT", "Tổng thanh toán", "Phương thức", "Thời gian thanh toán"};
        billTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable billTable = createStyledTable(billTableModel);
        billTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] revenueWidths = {125, 120, 120, 110, 160, 140, 205};
        for (int i = 0; i < revenueWidths.length; i++) {
            billTable.getColumnModel().getColumn(i).setPreferredWidth(revenueWidths[i]);
        }

        tableCard.add(lblHistoryTitle, BorderLayout.NORTH);
        tableCard.add(new JScrollPane(billTable), BorderLayout.CENTER);

        panel.add(kpiGrid, BorderLayout.NORTH);
        panel.add(tableCard, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createKpiCard(String title, JLabel valueLabel, Color accentColor) {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(accentColor);
                g2.fillRoundRect(0, 0, 8, getHeight(), 8, 8);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(18, 22, 18, 18));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(getCustomFont("Inter", Font.BOLD, 12));
        lblTitle.setForeground(TEXT_MUTED);

        valueLabel.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 24));
        valueLabel.setForeground(accentColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    // =========================================================================
    // CÁC HÀM HELPER TẠO COMPONENT PHONG CÁCH AUTHFRAME
    // =========================================================================
    private JPanel createWhiteCardPanel() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        return card;
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        lbl.setForeground(TEXT_DARK);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JTextField createStyledTextField(String defaultText) {
        JTextField txt = new JTextField(defaultText);
        txt.setFont(getCustomFont("Inter", Font.PLAIN, 14));
        txt.setForeground(TEXT_DARK);
        txt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        txt.setPreferredSize(new Dimension(340, 42));
        txt.setAlignmentX(Component.LEFT_ALIGNMENT);
        txt.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(210, 215, 212), 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        return txt;
    }

    private JButton createRoundedButton(String text, Color bg, Color fg, int w, int h) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? bg.brighter() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                super.paintComponent(g);
            }
        };
        btn.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        btn.setForeground(fg);
        btn.setPreferredSize(new Dimension(w, h));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(36);
        table.setFont(getCustomFont("Inter", Font.PLAIN, 13));
        table.setSelectionBackground(new Color(253, 237, 232));
        table.setSelectionForeground(TEXT_DARK);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(238, 240, 238));

        JTableHeader header = table.getTableHeader();
        header.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        header.setBackground(FOREST_BG);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getWidth(), 40));
        return table;
    }

    // =========================================================================
    // CÁC HÀM XỬ LÝ SỰ KIỆN & ĐỒNG BỘ DỮ LIỆU
    // =========================================================================
    private void refreshAllData() {
        loadCategoriesAndFoods();
        loadInventoryAndAlerts();
        loadRevenueStatistics();
    }

    private void loadCategoriesAndFoods() {
        cboCategory.removeAllItems();
        List<CategoryDTO> cats = adminBUS.getAllCategories();
        for (CategoryDTO c : cats) {
            cboCategory.addItem(c);
        }

        foodTableModel.setRowCount(0);
        for (FoodDTO f : adminBUS.getAllFoods()) {
            foodTableModel.addRow(new Object[]{
                    f.getId(),
                    f.getSku(),
                    f.getName(),
                    f.getCategoryName() != null ? f.getCategoryName() : "Chưa phân loại",
                    MONEY_FMT.format(f.getPrice()),
                    Boolean.TRUE.equals(f.getIsPublished()) ? "🟢 Đang mở bán" : "⚪ Tạm ngưng",
                    f.getCoverImageUrl()
            });
        }
    }

    private void loadInventoryAndAlerts() {
        stockTableModel.setRowCount(0);
        List<IngredientDTO> allIng = adminBUS.getAllIngredients();
        int lowCount = 0;

        for (IngredientDTO ing : allIng) {
            boolean isLow = ing.getStockQuantity().compareTo(ing.getMinStockLevel()) <= 0;
            if (isLow) lowCount++;

            stockTableModel.addRow(new Object[]{
                    ing.getId(),
                    ing.getName(),
                    ing.getUnit(),
                    QTY_FMT.format(ing.getStockQuantity()),
                    QTY_FMT.format(ing.getMinStockLevel()),
                    isLow ? "🚨 CẢNH BÁO: SẮP HẾT HÀNG (Cần nhập gấp)" : "✅ Đủ dùng"
            });
        }

        if (lowCount > 0) {
            lblLowStockBanner.setText("🚨 CẢNH BÁO TỒN KHO: Hiện có " + lowCount + " nguyên liệu chạm hoặc thấp hơn định mức tối thiểu (min_stock_level)!");
            lblLowStockBanner.setBackground(DANGER_RED);
            lblLowStockBanner.setForeground(Color.WHITE);
        } else {
            lblLowStockBanner.setText("✅ Tồn kho Bếp Nhà an toàn: Tất cả nguyên liệu đều cao hơn định mức tối thiểu.");
            lblLowStockBanner.setBackground(SAFE_GREEN);
            lblLowStockBanner.setForeground(Color.WHITE);
        }
    }

    private void loadRevenueStatistics() {
        billTableModel.setRowCount(0);
        List<BillDTO> bills = adminBUS.getAllBills();

        for (BillDTO b : bills) {
            billTableModel.addRow(new Object[]{
                    "#" + b.getId(),
                    "Phiên #" + b.getTableSessionId(),
                    MONEY_FMT.format(b.getDiscount()),
                    MONEY_FMT.format(b.getTax()),
                    MONEY_FMT.format(b.getTotalAmount()),
                    b.getPaymentMethod().name(),
                    b.getPaidAt() != null ? b.getPaidAt().format(TIME_FMT) : "---"
            });
        }

        lblCardTotalRevenue.setText(MONEY_FMT.format(adminBUS.calculateTotalRevenue(bills)));
        lblCardTotalBills.setText(bills.size() + " Hóa đơn");
        lblCardTotalDiscount.setText(MONEY_FMT.format(adminBUS.calculateTotalDiscount(bills)));
        lblCardTotalTax.setText(MONEY_FMT.format(adminBUS.calculateTotalTax(bills)));
    }

    private void handleAddFood() {
        try {
            CategoryDTO selectedCat = (CategoryDTO) cboCategory.getSelectedItem();
            Long catId = selectedCat != null ? selectedCat.getId() : null;
            BigDecimal price = new BigDecimal(txtPrice.getText().trim());

            boolean ok = adminBUS.addNewFood(
                    txtSku.getText(), catId, txtFoodName.getText(),
                    price, txtCoverUrl.getText(), chkPublished.isSelected()
            );
            if (ok) {
                JOptionPane.showMessageDialog(this, "Đã thêm món ăn mới vào thực đơn Bếp Nhà!");
                loadCategoriesAndFoods();
            } else {
                JOptionPane.showMessageDialog(this, "Thêm thất bại (Kiểm tra trùng mã SKU)!", "Lỗi", JOptionPane.ERROR_MESSAGE);
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Đơn giá không hợp lệ!");
        }
    }

    private void handleUpdateFood() {
        int row = foodTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Hãy chọn 1 món ăn trên bảng để cập nhật!");
            return;
        }
        try {
            Long foodId = (Long) foodTableModel.getValueAt(row, 0);
            CategoryDTO selectedCat = (CategoryDTO) cboCategory.getSelectedItem();
            Long catId = selectedCat != null ? selectedCat.getId() : null;
            BigDecimal price = new BigDecimal(txtPrice.getText().trim());

            if (adminBUS.updateFood(foodId, txtFoodName.getText(), price, catId, chkPublished.isSelected())) {
                JOptionPane.showMessageDialog(this, "Cập nhật món ăn #" + foodId + " thành công!");
                loadCategoriesAndFoods();
            }
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Đơn giá không hợp lệ!");
        }
    }

    private void handleDeleteFood() {
        int row = foodTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Hãy chọn 1 món ăn cần xóa!");
            return;
        }
        Long foodId = (Long) foodTableModel.getValueAt(row, 0);
        String name = foodTableModel.getValueAt(row, 2).toString();

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc muốn xóa mềm món '" + name + "'?\nMón ăn sẽ ẩn khỏi Tablet của khách nhưng vẫn giữ nguyên trong lịch sử hóa đơn cũ.",
                "Xác nhận xóa mềm - Bếp Nhà", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION && adminBUS.deleteFood(foodId)) {
            loadCategoriesAndFoods();
        }
    }

    private void handleRestockSelectedIngredient() {
        int row = stockTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 nguyên liệu trong bảng để nhập thêm hàng!");
            return;
        }
        Long ingId = (Long) stockTableModel.getValueAt(row, 0);
        String ingName = stockTableModel.getValueAt(row, 1).toString();
        String unit = stockTableModel.getValueAt(row, 2).toString();

        String input = JOptionPane.showInputDialog(this,
                "Nhập số lượng (" + unit + ") cần bổ sung vào kho cho [" + ingName + "]:", "10.0");
        if (input != null && !input.trim().isEmpty()) {
            try {
                BigDecimal added = new BigDecimal(input.trim());
                if (adminBUS.restockIngredient(ingId, added)) {
                    JOptionPane.showMessageDialog(this, "Đã cộng thêm " + added + " " + unit + " vào kho [" + ingName + "]!");
                    loadInventoryAndAlerts();
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Số lượng nhập vào phải là số hợp lệ!");
            }
        }
    }

    private void handleCreateNewIngredient() {
        JTextField txtName = new JTextField();
        JTextField txtUnit = new JTextField("kg");
        JTextField txtStock = new JTextField("20");
        JTextField txtMin = new JTextField("5");

        Object[] message = {
                "Tên nguyên liệu:", txtName,
                "Đơn vị tính (kg, gram, phần, gói):", txtUnit,
                "Số lượng tồn ban đầu:", txtStock,
                "Mức cảnh báo tối thiểu (min_stock_level):", txtMin
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Khai Báo Nguyên Liệu Mới", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            try {
                adminBUS.addIngredient(
                        txtName.getText(),
                        txtUnit.getText(),
                        new BigDecimal(txtStock.getText().trim()),
                        new BigDecimal(txtMin.getText().trim())
                );
                loadInventoryAndAlerts();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Dữ liệu nhập không hợp lệ!");
            }
        }
    }

    // LogoIcon đồng bộ 100% với AuthFrame
    static class BrandLogoIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TERRACOTTA);
            g2.fillRoundRect(x, y, 42, 42, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("⌂", x + (42 - fm.stringWidth("⌂")) / 2, y + ((42 - fm.getHeight()) / 2) + fm.getAscent());
        }
        @Override public int getIconWidth() { return 42; }
        @Override public int getIconHeight() { return 42; }
    }

    private Font getCustomFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AdminGUI().setVisible(true));
    }
}