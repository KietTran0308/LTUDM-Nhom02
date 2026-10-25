package gui;

import bus.ClientTabletBUS;
import dto.*;
import gui.components.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerGUI extends JFrame {
    // Bảng màu chuẩn từ AuthFrame (Bếp Nhà Restaurant)
    private static final Color FOREST_BG = new Color(32, 43, 37);
    private static final Color FOREST_CIRCLE_1 = new Color(29, 41, 34);
    private static final Color FOREST_CIRCLE_2 = new Color(33, 45, 38);
    private static final Color TERRACOTTA = new Color(206, 93, 52);
    private static final Color GOLD_ACCENT = new Color(201, 142, 84);
    private static final Color CREAM_BG = new Color(247, 248, 246);
    private static final Color TEXT_DARK = new Color(23, 31, 26);
    private static final Color TEXT_MUTED = new Color(140, 150, 145);

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,### VNĐ");

    private final Long currentTableId;
    private final Long currentUserId;

    private ClientTabletBUS tabletBUS;
    private List<FoodDTO> displayFoods = new ArrayList<>();
    private Long activeCategoryId = null;

    private JPanel categoryListPanel;
    private JPanel menuGridPanel;
    private DefaultTableModel cartTableModel;
    private DefaultTableModel trackTableModel;
    private JLabel lblTotalMoney;
    private JLabel lblConnectionStatus;
    private CTextField txtVoucher;
    private CTextField txtNote;
    private JTabbedPane rightTabbedPane;

    public CustomerGUI() {
        this(1L, 1L);
    }

    public CustomerGUI(Long tableId, Long userId) {
        this.currentTableId = tableId;
        this.currentUserId = userId;

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setTitle("Bếp Nhà Restaurant - Tablet Đặt Món Tại Bàn (Bàn #" + currentTableId + ")");
        setSize(1360, 820);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initLeftBrandSidebar();
        initCenterContentArea();
        initRightOrderDrawer();

        tabletBUS = new ClientTabletBUS(currentTableId, currentUserId, this::onServerPacketCallback);

        SwingUtilities.invokeLater(() -> {
            if (tabletBUS.getMenuFoods().isEmpty()) {
                loadFallbackSampleMenu();
            }
        });
    }

    private void onServerPacketCallback(SocketPacket packet) {
        SwingUtilities.invokeLater(() -> {
            if (!packet.isSuccess()) {
                JOptionPane.showMessageDialog(this, packet.getMessage(), "Thông báo từ Bếp Nhà", JOptionPane.WARNING_MESSAGE);
                return;
            }

            switch (packet.getAction()) {
                case REGISTER_TABLE:
                    lblConnectionStatus.setText("● Đã kết nối hệ thống Bếp");
                    lblConnectionStatus.setForeground(new Color(110, 231, 183));
                    break;

                case GET_MENU_AND_CATEGORIES:
                    if (!tabletBUS.getMenuFoods().isEmpty()) {
                        this.displayFoods = tabletBUS.getMenuFoods();
                        rebuildCategoryButtons(tabletBUS.getCategories());
                        renderMenuCards(null);
                    }
                    break;

                case PLACE_ORDER:
                    refreshCartTable();
                    refreshTrackTable();
                    rightTabbedPane.setSelectedIndex(1);
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Đặt món thành công", JOptionPane.INFORMATION_MESSAGE);
                    break;

                case GET_TABLE_TRACKING:
                case KITCHEN_UPDATE_STATUS:
                    refreshTrackTable();
                    break;

                case CALL_STAFF:
                    JOptionPane.showMessageDialog(this, packet.getMessage());
                    break;

                case TABLE_CLEARED_RESET:
                    refreshCartTable();
                    refreshTrackTable();
                    rightTabbedPane.setSelectedIndex(0);
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Cảm ơn quý khách", JOptionPane.INFORMATION_MESSAGE);
                    break;
                default:
                    break;
            }
        });
    }

    // 1. SIDEBAR TRÁI PHONG CÁCH AUTHFRAME (Xanh rừng + Họa tiết vòng tròn + Logo Bếp Nhà)
    private void initLeftBrandSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 25)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FOREST_CIRCLE_1);
                g2.fillOval(-150, getHeight() - 380, 460, 460);
                g2.setColor(FOREST_CIRCLE_2);
                g2.fillOval(-80, getHeight() - 280, 320, 320);
            }
        };
        sidebar.setBackground(FOREST_BG);
        sidebar.setPreferredSize(new Dimension(250, getHeight()));
        sidebar.setBorder(new EmptyBorder(30, 22, 30, 22));

        // Cụm Logo "Bếp Nhà" chuẩn AuthFrame
        JPanel topBrandBox = new JPanel();
        topBrandBox.setLayout(new BoxLayout(topBrandBox, BoxLayout.Y_AXIS));
        topBrandBox.setOpaque(false);

        JPanel logoRow = new JPanel();
        logoRow.setLayout(new BoxLayout(logoRow, BoxLayout.X_AXIS));
        logoRow.setOpaque(false);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblIcon = new JLabel(new BrandLogoIcon());
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 12));

        JPanel logoTextPanel = new JPanel();
        logoTextPanel.setLayout(new BoxLayout(logoTextPanel, BoxLayout.Y_AXIS));
        logoTextPanel.setOpaque(false);

        JLabel lblBrandName = new JLabel("Bếp Nhà");
        lblBrandName.setFont(getCustomFont("Inter", Font.BOLD, 20));
        lblBrandName.setForeground(Color.WHITE);
        JLabel lblBrandSub = new JLabel("RESTAURANT");
        lblBrandSub.setFont(getCustomFont("Inter", Font.BOLD, 9));
        lblBrandSub.setForeground(TEXT_MUTED);

        logoTextPanel.add(lblBrandName);
        logoTextPanel.add(lblBrandSub);
        logoRow.add(lblIcon);
        logoRow.add(logoTextPanel);

        // Badge Bàn số
        JPanel tableBadge = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(45, 59, 51));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        tableBadge.setOpaque(false);
        tableBadge.setLayout(new BorderLayout());
        tableBadge.setBorder(new EmptyBorder(10, 14, 10, 14));
        tableBadge.setMaximumSize(new Dimension(220, 58));
        tableBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTableNo = new JLabel("BÀN ĐANG PHỤC VỤ: #" + currentTableId);
        lblTableNo.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        lblTableNo.setForeground(GOLD_ACCENT);

        lblConnectionStatus = new JLabel("● Chế độ Offline Demo");
        lblConnectionStatus.setFont(getCustomFont("Inter", Font.PLAIN, 12));
        lblConnectionStatus.setForeground(new Color(253, 224, 71));

        tableBadge.add(lblTableNo, BorderLayout.NORTH);
        tableBadge.add(lblConnectionStatus, BorderLayout.SOUTH);

        topBrandBox.add(logoRow);
        topBrandBox.add(Box.createVerticalStrut(22));
        topBrandBox.add(tableBadge);

        // Danh sách nút chọn Danh mục
        categoryListPanel = new JPanel();
        categoryListPanel.setLayout(new BoxLayout(categoryListPanel, BoxLayout.Y_AXIS));
        categoryListPanel.setOpaque(false);

        // Nút gọi nhân viên ở đáy Sidebar
        CButton btnCallStaff = new CButton("Gọi Nhân Viên Hỗ Trợ", 205, 46);
        btnCallStaff.addActionListener(e -> {
            String req = JOptionPane.showInputDialog(this,
                    "Quý khách cần hỗ trợ gì ạ? (VD: Thêm nước lẩu, thêm chén đũa, khăn giấy):",
                    "Gọi nhân viên Bếp Nhà", JOptionPane.PLAIN_MESSAGE);
            if (req != null && !req.trim().isEmpty()) {
                tabletBUS.callStaffSupport(req.trim());
            }
        });

        sidebar.add(topBrandBox, BorderLayout.NORTH);
        sidebar.add(categoryListPanel, BorderLayout.CENTER);
        sidebar.add(btnCallStaff, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);
    }

    private void rebuildCategoryButtons(List<CategoryDTO> categories) {
        categoryListPanel.removeAll();

        JLabel lblMenuSection = new JLabel("THỰC ĐƠN BẾP NHÀ");
        lblMenuSection.setFont(getCustomFont("Inter", Font.BOLD, 11));
        lblMenuSection.setForeground(TEXT_MUTED);
        lblMenuSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        categoryListPanel.add(lblMenuSection);
        categoryListPanel.add(Box.createVerticalStrut(12));

        categoryListPanel.add(createNavCategoryButton("Tất Cả Món Ngon", null));
        categoryListPanel.add(Box.createVerticalStrut(10));

        for (CategoryDTO cat : categories) {
            categoryListPanel.add(createNavCategoryButton(cat.getName(), cat.getId()));
            categoryListPanel.add(Box.createVerticalStrut(10));
        }

        categoryListPanel.revalidate();
        categoryListPanel.repaint();
    }

    private JButton createNavCategoryButton(String title, Long categoryId) {
        boolean isSelected = (activeCategoryId == null && categoryId == null)
                || (activeCategoryId != null && activeCategoryId.equals(categoryId));

        JButton btn = new JButton(title) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || isSelected) {
                    g2.setColor(isSelected ? TERRACOTTA : new Color(48, 63, 55));
                } else {
                    g2.setColor(new Color(39, 52, 45));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                super.paintComponent(g);
            }
        };
        btn.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        btn.setForeground(Color.WHITE);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(12, 16, 12, 16));
        btn.setMaximumSize(new Dimension(210, 44));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addActionListener(e -> {
            this.activeCategoryId = categoryId;
            rebuildCategoryButtons(tabletBUS.getCategories().isEmpty() ? getSampleCategories() : tabletBUS.getCategories());
            renderMenuCards(categoryId);
        });
        return btn;
    }

    // 2. KHU VỰC GIỮA: BANNER TIÊU ĐỀ & LƯỚI MÓN ĂN
    private void initCenterContentArea() {
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBackground(CREAM_BG);

        // Header chào mừng phong cách AuthFrame
        JPanel topBanner = new JPanel(new BorderLayout());
        topBanner.setBackground(Color.WHITE);
        topBanner.setBorder(new EmptyBorder(22, 30, 18, 30));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel lblHeadline = new JLabel("Mỗi đơn món, một trải nghiệm trọn vẹn.");
        lblHeadline.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 26));
        lblHeadline.setForeground(TEXT_DARK);

        JLabel lblSub = new JLabel("Chọn món trực tiếp tại bàn — Đơn hàng sẽ được chuyển thẳng tới Bếp theo thời gian thực.");
        lblSub.setFont(getCustomFont("Inter", Font.PLAIN, 14));
        lblSub.setForeground(Color.GRAY);

        titleBox.add(lblHeadline);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(lblSub);

        topBanner.add(titleBox, BorderLayout.WEST);

        // Lưới thẻ món ăn
        menuGridPanel = new JPanel(new GridLayout(0, 3, 18, 18));
        menuGridPanel.setBackground(CREAM_BG);
        menuGridPanel.setBorder(new EmptyBorder(22, 25, 25, 25));

        JScrollPane scrollPane = new JScrollPane(menuGridPanel);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBorder(null);

        centerContainer.add(topBanner, BorderLayout.NORTH);
        centerContainer.add(scrollPane, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);
    }

    private void renderMenuCards(Long categoryId) {
        menuGridPanel.removeAll();
        List<FoodDTO> filtered = displayFoods.stream()
                .filter(f -> categoryId == null || (f.getCategoryId() != null && f.getCategoryId().equals(categoryId)))
                .collect(Collectors.toList());

        for (FoodDTO food : filtered) {
            menuGridPanel.add(createStyledFoodCard(food));
        }
        menuGridPanel.revalidate();
        menuGridPanel.repaint();
    }

    private JPanel createStyledFoodCard(FoodDTO food) {
        JPanel card = new JPanel(new BorderLayout(0, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.setColor(new Color(230, 232, 228));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 20, 20);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 14, 14, 14));
        card.setPreferredSize(new Dimension(220, 255));

        // Khung ảnh món ăn màu kem ấm
        JLabel lblImg = new JLabel("🍲", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(250, 243, 238));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                super.paintComponent(g);
            }
        };
        lblImg.setFont(new Font("SansSerif", Font.PLAIN, 50));
        lblImg.setPreferredSize(new Dimension(180, 115));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel lblName = new JLabel(food.getName());
        lblName.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblName.setForeground(TEXT_DARK);
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblPrice = new JLabel(MONEY_FORMAT.format(food.getPrice()));
        lblPrice.setFont(getCustomFont("Inter", Font.BOLD, 15));
        lblPrice.setForeground(TERRACOTTA);
        lblPrice.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(lblName);
        infoPanel.add(Box.createVerticalStrut(4));
        infoPanel.add(lblPrice);

        CButton btnAdd = new CButton("+ Chọn Món", 190, 40);
        btnAdd.addActionListener(e -> {
            tabletBUS.addFoodToCart(food);
            refreshCartTable();
            rightTabbedPane.setSelectedIndex(0);
        });

        card.add(lblImg, BorderLayout.NORTH);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(btnAdd, BorderLayout.SOUTH);
        return card;
    }

    // 3. PANEL PHẢI: GIỎ HÀNG & THEO DÕI ĐƠN (SỬ DỤNG CTextField & CButton)
    private void initRightOrderDrawer() {
        JPanel rightWrapper = new JPanel(new BorderLayout());
        rightWrapper.setBackground(Color.WHITE);
        rightWrapper.setPreferredSize(new Dimension(440, getHeight()));
        rightWrapper.setBorder(new LineBorder(new Color(230, 232, 228), 1));

        rightTabbedPane = new JTabbedPane();
        rightTabbedPane.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        rightTabbedPane.setBackground(Color.WHITE);

        // --- TAB 1: GIỎ HÀNG ---
        JPanel cartPanel = new JPanel(new BorderLayout(0, 12));
        cartPanel.setBackground(Color.WHITE);
        cartPanel.setBorder(new EmptyBorder(15, 18, 18, 18));

        String[] cartCols = {"ID", "Tên Món", "SL", "Thành Tiền"};
        cartTableModel = new DefaultTableModel(cartCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable cartTable = createStyledTable(cartTableModel);
        cartTable.getColumnModel().getColumn(0).setPreferredWidth(35);
        cartTable.getColumnModel().getColumn(1).setPreferredWidth(165);
        cartTable.getColumnModel().getColumn(2).setPreferredWidth(35);

        JPanel qtyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        qtyRow.setOpaque(false);
        JButton btnMinus = createOutlineButton("- Giảm SL");
        JButton btnPlus = createOutlineButton("+ Tăng SL");
        btnMinus.addActionListener(e -> adjustCartQuantity(cartTable, -1));
        btnPlus.addActionListener(e -> adjustCartQuantity(cartTable, 1));
        qtyRow.add(btnMinus);
        qtyRow.add(btnPlus);

        // Form nhập Voucher & Ghi chú bằng CTextField chuẩn AuthFrame
        JPanel bottomForm = new JPanel();
        bottomForm.setLayout(new BoxLayout(bottomForm, BoxLayout.Y_AXIS));
        bottomForm.setOpaque(false);

        qtyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomForm.add(qtyRow);
        bottomForm.add(Box.createVerticalStrut(12));

        JLabel lblVoucher = new JLabel("Mã ưu đãi (Voucher)");
        lblVoucher.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        lblVoucher.setForeground(TEXT_DARK);
        lblVoucher.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtVoucher = new CTextField("Nhập mã giảm giá (nếu có)...", 400, 42);
        txtVoucher.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblNote = new JLabel("Ghi chú cho Bếp");
        lblNote.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        lblNote.setForeground(TEXT_DARK);
        lblNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtNote = new CTextField("Ít cay, không hành...", 400, 42);
        txtNote.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblTotalMoney = new JLabel("Tổng tạm tính: 0 VNĐ");
        lblTotalMoney.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        lblTotalMoney.setForeground(TERRACOTTA);
        lblTotalMoney.setAlignmentX(Component.LEFT_ALIGNMENT);

        CButton btnSubmitOrder = new CButton("Gửi Đơn Xuống Bếp", 400, 50);
        btnSubmitOrder.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSubmitOrder.addActionListener(e -> handleSendOrder());

        bottomForm.add(lblVoucher);
        bottomForm.add(Box.createVerticalStrut(5));
        bottomForm.add(txtVoucher);
        bottomForm.add(Box.createVerticalStrut(10));
        bottomForm.add(lblNote);
        bottomForm.add(Box.createVerticalStrut(5));
        bottomForm.add(txtNote);
        bottomForm.add(Box.createVerticalStrut(15));
        bottomForm.add(lblTotalMoney);
        bottomForm.add(Box.createVerticalStrut(12));
        bottomForm.add(btnSubmitOrder);

        cartPanel.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        cartPanel.add(bottomForm, BorderLayout.SOUTH);

        // --- TAB 2: THEO DÕI ĐƠN HÀNG ---
        JPanel trackPanel = new JPanel(new BorderLayout(0, 12));
        trackPanel.setBackground(Color.WHITE);
        trackPanel.setBorder(new EmptyBorder(15, 18, 18, 18));

        String[] trackCols = {"Mã", "Tên Món", "SL", "Trạng Thái Bếp"};
        trackTableModel = new DefaultTableModel(trackCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable trackTable = createStyledTable(trackTableModel);
        trackTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        trackTable.getColumnModel().getColumn(1).setPreferredWidth(155);
        trackTable.getColumnModel().getColumn(2).setPreferredWidth(35);

        JLabel lblHint = new JLabel("<html><i>* Tiến độ chế biến được đồng bộ thời gian thực từ Bếp Nhà.</i></html>");
        lblHint.setFont(getCustomFont("Inter", Font.PLAIN, 12));
        lblHint.setForeground(Color.GRAY);

        trackPanel.add(new JScrollPane(trackTable), BorderLayout.CENTER);
        trackPanel.add(lblHint, BorderLayout.SOUTH);

        rightTabbedPane.addTab("Giỏ Món (0)", cartPanel);
        rightTabbedPane.addTab("Theo Dõi Đơn (0)", trackPanel);

        rightWrapper.add(rightTabbedPane, BorderLayout.CENTER);
        add(rightWrapper, BorderLayout.EAST);
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(34);
        table.setFont(getCustomFont("Inter", Font.PLAIN, 13));
        table.setSelectionBackground(new Color(253, 237, 232));
        table.setSelectionForeground(TEXT_DARK);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(240, 240, 240));

        JTableHeader header = table.getTableHeader();
        header.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        header.setBackground(FOREST_BG);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getWidth(), 36));
        return table;
    }

    private JButton createOutlineButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 12));
        btn.setForeground(FOREST_BG);
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(200, 205, 200), 1, true),
                new EmptyBorder(6, 14, 6, 14)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private void adjustCartQuantity(JTable table, int delta) {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 món trong giỏ để điều chỉnh số lượng!");
            return;
        }
        Long foodId = (Long) cartTableModel.getValueAt(row, 0);
        tabletBUS.updateCartQuantity(foodId, delta);
        refreshCartTable();
    }

    private void refreshCartTable() {
        cartTableModel.setRowCount(0);
        int count = 0;
        for (OrderItemDTO item : tabletBUS.getCurrentCart()) {
            cartTableModel.addRow(new Object[]{
                    item.getFoodsId(),
                    item.getFoodsName(),
                    item.getQuantity(),
                    MONEY_FORMAT.format(item.getLineTotal())
            });
            count += item.getQuantity();
        }
        lblTotalMoney.setText("Tổng tạm tính: " + MONEY_FORMAT.format(tabletBUS.calculateCartSubtotal()));
        rightTabbedPane.setTitleAt(0, "Giỏ Món (" + count + ")");
    }

    private void refreshTrackTable() {
        trackTableModel.setRowCount(0);
        for (OrderItemDTO item : tabletBUS.getTrackedItems()) {
            trackTableModel.addRow(new Object[]{
                    item.getId() != null ? "#" + item.getId() : "---",
                    item.getFoodsName(),
                    item.getQuantity(),
                    formatStatusVietnamese(item.getStatus())
            });
        }
        rightTabbedPane.setTitleAt(1, "Theo Dõi Đơn (" + tabletBUS.getTrackedItems().size() + ")");
    }

    private String formatStatusVietnamese(OrderStatus st) {
        if (st == null) return "Chờ xác nhận";
        switch (st) {
            case PENDING: return "⏳ Chờ xác nhận";
            case RECEIVED: return "📥 Bếp đã nhận";
            case COOKING: return "🔥 Đang chế biến";
            case COMPLETED: return "🛎️ Đã nấu xong";
            case SERVED: return "✅ Đã lên món";
            case CANCELLED: return "❌ Đã hủy";
            default: return st.name();
        }
    }

    private void handleSendOrder() {
        if (tabletBUS.getCurrentCart().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Giỏ món của bàn đang trống!");
            return;
        }
        String voucher = txtVoucher.getText().trim();
        if (voucher.equals("Nhập mã giảm giá (nếu có)...")) voucher = "";

        boolean sent = tabletBUS.submitOrderToServer(voucher, txtNote.getText().trim());
        if (!sent) {
            JOptionPane.showMessageDialog(this,
                    "Chưa kết nối được với ServerApp (Port 8888)!\nHãy bật KitchenServerGUI bên ServerApp trước.",
                    "Lỗi Kết Nối Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    private List<CategoryDTO> getSampleCategories() {
        List<CategoryDTO> sampleCats = new ArrayList<>();
        sampleCats.add(new CategoryDTO(1L, "Nước Lẩu Đặc Trưng", "nuoc-lau", "", null));
        sampleCats.add(new CategoryDTO(2L, "Thịt Bò & Hải Sản", "thit-hai-san", "", null));
        sampleCats.add(new CategoryDTO(3L, "Món Đặc Sắc Bếp Nhà", "mon-dac-sac", "", null));
        return sampleCats;
    }

    private void loadFallbackSampleMenu() {
        displayFoods.clear();
        displayFoods.add(new FoodDTO(1L, "SKU01", 1L, "Lẩu Dầu Cay Tứ Xuyên", "lau-tu-xuyen", "", new BigDecimal("139000"), true, false, null, null));
        displayFoods.add(new FoodDTO(2L, "SKU02", 1L, "Lẩu Cà Chua Bếp Nhà", "lau-ca-chua", "", new BigDecimal("129000"), true, false, null, null));
        displayFoods.add(new FoodDTO(3L, "SKU03", 2L, "Bò Bông Tuyết Thượng Hạng", "bo-bong-tuyet", "", new BigDecimal("219000"), true, false, null, null));
        displayFoods.add(new FoodDTO(4L, "SKU04", 2L, "Tôm Sú Tươi Nhảy Múa", "tom-su", "", new BigDecimal("185000"), true, false, null, null));
        displayFoods.add(new FoodDTO(5L, "SKU05", 3L, "Múa Mì Kéo Sợi Tại Bàn", "mua-mi", "", new BigDecimal("45000"), true, false, null, null));

        rebuildCategoryButtons(getSampleCategories());
        renderMenuCards(null);
    }

    // LogoIcon đồng bộ 100% với AuthFrame
    static class BrandLogoIcon implements Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(TERRACOTTA);
            g2.fillRoundRect(x, y, 40, 40, 12, 12);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 22));
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString("⌂", x + (40 - fm.stringWidth("⌂")) / 2, y + ((40 - fm.getHeight()) / 2) + fm.getAscent());
        }
        @Override public int getIconWidth() { return 40; }
        @Override public int getIconHeight() { return 40; }
    }

    private Font getCustomFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new CustomerGUI().setVisible(true));
    }
}