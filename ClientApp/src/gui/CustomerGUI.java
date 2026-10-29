package gui;

import bus.ClientTabletBUS;
import dto.*;
import gui.components.*;
import utils.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CustomerGUI extends JFrame {

    private static final long serialVersionUID = 1L;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,### VNĐ");

    private static final String VIEW_MENU = "VIEW_MENU";
    private static final String VIEW_CART_DETAIL = "VIEW_CART_DETAIL";
    private static final String VIEW_ORDER_TRACKING = "VIEW_ORDER_TRACKING";

    private static final int ITEMS_PER_ROW = 4;
    private static final int MIN_CARD_WIDTH = 225;
    private static final int MAX_CARD_WIDTH = 360;
    private static final int CARD_HEIGHT = 360;
    private static final int IMAGE_HEIGHT = 140;
    private static final int CARD_GAP = 16;

    private final Long currentTableId;
    private final Long currentUserId;

    private final ClientTabletBUS tabletBUS;
    private List<FoodDTO> displayFoods = new ArrayList<>();
    private Long activeCategoryId = null;

    private final Map<Long, String> itemNotesMap = new HashMap<>();
    private final Map<String, BufferedImage> foodImageCache = new HashMap<>();
    private final Map<String, Long> failedImageTimes = new HashMap<>();
    private static final long IMAGE_RETRY_MS = 30_000L;
    private final Map<String, List<JLabel>> loadingFoodImages = new HashMap<>();
    private final Set<Long> completedNotified = new HashSet<>();

    private JPanel categoryListPanel, menuGridPanel;
    private JScrollPane menuScrollPane;
    private int lastMenuViewportWidth = -1;

    private JLabel lblConnectionStatus;
    private JLabel lblKitchenNotice;
    private JButton btnTrackOrderTop;
    private CTable trackTable;
    private JButton btnRequestCancelSupport;
    private CButton btnSendKitchen;
    private boolean awaitingOrderConfirmation;

    private CardLayout centerCardLayout;
    private JPanel centerCardsPanel;
    private String currentActiveView = VIEW_MENU;

    private JPanel summaryCartWrapper;
    private JLabel lblSummaryCountBadge;
    private JLabel lblSummaryTotalMoney;
    private JLabel lblSummaryToggleAction;

    private JPanel cartItemsListPanel;
    private JLabel lblCartDetailTotal;
    private CTextField txtVoucher;

    public CustomerGUI(Long tableId, Long userId) {
        if (tableId == null || tableId <= 0 || userId == null || userId <= 0) throw new IllegalArgumentException("Cần tableId và userId hợp lệ từ phiên đăng nhập.");

        this.currentTableId = tableId;
        this.currentUserId = userId;

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setSize(1360, 820);
        setTitle("Bếp Nhà | Gọi món tại bàn #" + currentTableId);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initLeftSidebar();
        initCenterContentArea();

        tabletBUS = new ClientTabletBUS(currentTableId, currentUserId, this::onServerPacketCallback);

        SwingUtilities.invokeLater(() -> {
            rebuildCategoryButtons(tabletBUS.getCategories());
            refreshAllCartViews();
            refreshTrackTable();
        });
    }

    private void onServerPacketCallback(SocketPacket packet) {
        SwingUtilities.invokeLater(() -> {
            if (!packet.isSuccess()) {
                if (packet.getAction() == SocketAction.PLACE_ORDER) {
                    resetSendOrderButtonState();
                }
                JOptionPane.showMessageDialog(this, packet.getMessage(), "Thông báo từ Bếp Nhà", JOptionPane.WARNING_MESSAGE);
                return;
            }

            switch (packet.getAction()) {
                case REGISTER_TABLE:
                    lblConnectionStatus.setText("● Đã kết nối hệ thống Bếp");
                    lblConnectionStatus.setForeground(new Color(110, 231, 183));
                    tabletBUS.requestTableTracking();
                    break;

                case GET_MENU_AND_CATEGORIES:
                    this.displayFoods = tabletBUS.getMenuFoods();
                    rebuildCategoryButtons(tabletBUS.getCategories());
                    renderMenuCards(activeCategoryId);
                    break;

                case PLACE_ORDER:
                    resetSendOrderButtonState();
                    itemNotesMap.clear();
                    refreshAllCartViews();
                    refreshTrackTable();
                    switchCenterView(VIEW_ORDER_TRACKING);
                    tabletBUS.requestTableTracking();
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Đặt món thành công", JOptionPane.INFORMATION_MESSAGE);
                    break;

                case GET_TABLE_TRACKING:
                    refreshTrackTable();
                    break;

                case KITCHEN_UPDATE_STATUS:
                    refreshTrackTable();
                    if (packet.getData() instanceof Long && packet.getExtraData() == OrderStatus.COMPLETED) {
                        Long completedId = (Long) packet.getData();
                        if (completedNotified.add(completedId)) {
                            String foodName = tabletBUS.getTrackedItems().stream()
                                    .filter(item -> completedId.equals(item.getId()))
                                    .map(OrderItemDTO::getFoodsName)
                                    .findFirst()
                                    .orElse("Món #" + completedId);
                            lblKitchenNotice.setText("🔔 " + foodName + " đã chế biến xong — vui lòng chờ nhân viên mang món.");
                            lblKitchenNotice.setVisible(true);
                        }
                    }
                    break;

                case CALL_STAFF:
                    JOptionPane.showMessageDialog(this, packet.getMessage());
                    break;

                case TABLE_CLEARED_RESET:
                    resetSendOrderButtonState();
                    completedNotified.clear();
                    lblKitchenNotice.setVisible(false);
                    itemNotesMap.clear();
                    refreshAllCartViews();
                    refreshTrackTable();
                    switchCenterView(VIEW_MENU);
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Cảm ơn quý khách", JOptionPane.INFORMATION_MESSAGE);
                    break;

                default:
                    break;
            }
        });
    }

    private void resetSendOrderButtonState() {
        awaitingOrderConfirmation = false;
        btnSendKitchen.setEnabled(true);
        btnSendKitchen.setText("Xác nhận & Gửi xuống bếp");
    }

    private void initLeftSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 25)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Utils.FOREST_CIRCLE_1);
                g2.fillOval(-150, getHeight() - 380, 460, 460);
                g2.setColor(Utils.FOREST_CIRCLE_2);
                g2.fillOval(-80, getHeight() - 280, 320, 320);
            }
        };
        sidebar.setBackground(Utils.FOREST_BG);
        sidebar.setPreferredSize(new Dimension(250, getHeight()));
        sidebar.setBorder(new EmptyBorder(30, 22, 30, 22));

        JPanel topBrandBox = new JPanel();
        topBrandBox.setLayout(new BoxLayout(topBrandBox, BoxLayout.Y_AXIS));
        topBrandBox.setOpaque(false);

        JPanel logoRow = new JPanel();
        logoRow.setLayout(new BoxLayout(logoRow, BoxLayout.X_AXIS));
        logoRow.setOpaque(false);
        logoRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblIcon = new JLabel(Utils.getScaledIcon("/img/logo.png", 45, 45));
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 12));

        JPanel logoTextPanel = new JPanel();
        logoTextPanel.setLayout(new BoxLayout(logoTextPanel, BoxLayout.Y_AXIS));
        logoTextPanel.setOpaque(false);

        JLabel lblBrandName = new JLabel("Bếp Nhà");
        lblBrandName.setFont(Utils.getCustomFont("Inter", Font.BOLD, 20));
        lblBrandName.setForeground(Color.WHITE);

        JLabel lblBrandSub = new JLabel("RESTAURANT  /  GỌI MÓN");
        lblBrandSub.setFont(Utils.getCustomFont("Inter", Font.BOLD, 9));
        lblBrandSub.setForeground(Utils.TEXT_MUTED);

        logoTextPanel.add(lblBrandName);
        logoTextPanel.add(lblBrandSub);
        logoRow.add(lblIcon);
        logoRow.add(logoTextPanel);

        JPanel tableBadge = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(45, 59, 51));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
            }
        };
        tableBadge.setOpaque(false);
        tableBadge.setBorder(new EmptyBorder(10, 14, 10, 14));
        tableBadge.setMaximumSize(new Dimension(220, 58));
        tableBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTableNo = new JLabel("BÀN ĐANG PHỤC VỤ: #" + currentTableId);
        lblTableNo.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        lblTableNo.setForeground(Utils.GOLD_ACCENT);

        lblConnectionStatus = new JLabel("● Đang chờ kết nối bếp");
        lblConnectionStatus.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        lblConnectionStatus.setForeground(new Color(253, 224, 71));

        tableBadge.add(lblTableNo, BorderLayout.NORTH);
        tableBadge.add(lblConnectionStatus, BorderLayout.SOUTH);

        topBrandBox.add(logoRow);
        topBrandBox.add(Box.createVerticalStrut(22));
        topBrandBox.add(tableBadge);

        categoryListPanel = new JPanel();
        categoryListPanel.setLayout(new BoxLayout(categoryListPanel, BoxLayout.Y_AXIS));
        categoryListPanel.setOpaque(false);

        CButton btnCallStaff = new CButton("Gọi nhân viên hỗ trợ", 205, 46);
        btnCallStaff.addActionListener(e -> {
            String req = JOptionPane.showInputDialog(this,
                    "Quý khách cần hỗ trợ gì? (Thêm đồ dùng, chỉnh món, hủy món, gọi tính tiền...):",
                    "Gọi nhân viên Bếp Nhà", JOptionPane.PLAIN_MESSAGE);
            if (req != null && !req.trim().isEmpty()) {
                tabletBUS.callStaffSupport(req.trim());
            }
        });

        JButton btnRequestBill = new JButton("Yêu cầu tính tiền");
        btnRequestBill.setFont(Utils.getCustomFont("Inter", Font.BOLD, 13));
        btnRequestBill.setForeground(Color.WHITE);
        btnRequestBill.setBackground(new Color(48, 63, 55));
        btnRequestBill.setFocusPainted(false);
        btnRequestBill.setBorder(new EmptyBorder(13, 10, 13, 10));
        btnRequestBill.setPreferredSize(new Dimension(205, 46));
        btnRequestBill.setMaximumSize(new Dimension(205, 46));
        btnRequestBill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRequestBill.addActionListener(e -> requestService("Tính tiền",
                "Bàn #" + currentTableId + " yêu cầu nhân viên kiểm tra hóa đơn và hỗ trợ thanh toán."));

        JButton btnLogout = new JButton("Thoát phiên / Đăng xuất");
        btnLogout.setFont(Utils.getCustomFont("Inter", Font.BOLD, 12));
        btnLogout.setForeground(new Color(218, 226, 219));
        btnLogout.setContentAreaFilled(false);
        btnLogout.setFocusPainted(false);
        btnLogout.setBorder(new EmptyBorder(12, 2, 5, 2));
        btnLogout.setPreferredSize(new Dimension(205, 37));
        btnLogout.setMaximumSize(new Dimension(205, 37));
        btnLogout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogout.addActionListener(e -> logout());

        JPanel sidebarActions = new JPanel();
        sidebarActions.setOpaque(false);
        sidebarActions.setLayout(new BoxLayout(sidebarActions, BoxLayout.Y_AXIS));
        for (JComponent comp : new JComponent[]{btnCallStaff, btnRequestBill, btnLogout}) {
            comp.setAlignmentX(Component.CENTER_ALIGNMENT);
            sidebarActions.add(comp);
            sidebarActions.add(Box.createVerticalStrut(8));
        }

        sidebar.add(topBrandBox, BorderLayout.NORTH);
        sidebar.add(categoryListPanel, BorderLayout.CENTER);
        sidebar.add(sidebarActions, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);
    }

    private void requestService(String title, String message) {
        if (tabletBUS == null || !tabletBUS.isServerConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Chưa kết nối ServerApp. Vui lòng báo trực tiếp với nhân viên.",
                    title, JOptionPane.WARNING_MESSAGE);
            return;
        }
        tabletBUS.callStaffSupport(message);
        JOptionPane.showMessageDialog(this,
                "Đã gửi yêu cầu hỗ trợ. Vui lòng chờ nhân viên xác nhận.",
                title, JOptionPane.INFORMATION_MESSAGE);
    }

    private void logout() {
        String alert = tabletBUS != null && !tabletBUS.getCurrentCart().isEmpty()
                ? "Giỏ món chưa gửi sẽ không được lưu khi thoát phiên.\n"
                : "";
        int answer = JOptionPane.showConfirmDialog(this,
                alert + "Các món đã gửi vẫn ở bếp và không tự hủy khi đăng xuất.\nThoát phiên này?",
                "Thoát phiên tại bàn", JOptionPane.YES_NO_OPTION);
        if (answer != JOptionPane.YES_OPTION) {
            return;
        }
        new AuthGUI().setVisible(true);
        dispose();
    }

    private void rebuildCategoryButtons(List<CategoryDTO> categories) {
        categoryListPanel.removeAll();

        JLabel lblMenuSection = new JLabel("THỰC ĐƠN NHÀ HÀNG");
        lblMenuSection.setFont(Utils.getCustomFont("Inter", Font.BOLD, 11));
        lblMenuSection.setForeground(Utils.TEXT_MUTED);
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
        boolean isSelected = Objects.equals(activeCategoryId, categoryId);

        JButton btn = new JButton(title) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover() || isSelected) {
                    g2.setColor(isSelected ? Utils.TERRACOTTA : new Color(48, 63, 55));
                } else {
                    g2.setColor(new Color(39, 52, 45));
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                super.paintComponent(g);
            }
        };
        btn.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
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
            rebuildCategoryButtons(tabletBUS.getCategories());
            renderMenuCards(categoryId);
            switchCenterView(VIEW_MENU);
            SwingUtilities.invokeLater(() -> menuScrollPane.getVerticalScrollBar().setValue(0));
        });
        return btn;
    }

    private void initCenterContentArea() {
        JPanel centerContainer = new JPanel(new BorderLayout());
        centerContainer.setBackground(Utils.CREAM_BG);

        JPanel topBanner = new JPanel(new BorderLayout());
        topBanner.setBackground(Color.WHITE);
        topBanner.setBorder(new EmptyBorder(16, 26, 14, 26));

        btnTrackOrderTop = new JButton("📋 Theo Dõi Đơn Bếp (0)") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean active = VIEW_ORDER_TRACKING.equals(currentActiveView);
                g2.setColor(active ? Utils.TERRACOTTA : (getModel().isRollover() ? new Color(45, 60, 52) : Utils.FOREST_BG));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                super.paintComponent(g);
            }
        };
        btnTrackOrderTop.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        btnTrackOrderTop.setForeground(Color.WHITE);
        btnTrackOrderTop.setContentAreaFilled(false);
        btnTrackOrderTop.setFocusPainted(false);
        btnTrackOrderTop.setBorder(new EmptyBorder(10, 20, 10, 20));
        btnTrackOrderTop.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnTrackOrderTop.addActionListener(e -> {
            if (VIEW_ORDER_TRACKING.equals(currentActiveView)) {
                switchCenterView(VIEW_MENU);
            } else {
                refreshTrackTable();
                tabletBUS.requestTableTracking();
                switchCenterView(VIEW_ORDER_TRACKING);
            }
        });

        initSummaryCartBar();
        JPanel rightBannerActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightBannerActions.setOpaque(false);
        rightBannerActions.add(summaryCartWrapper);
        rightBannerActions.add(btnTrackOrderTop);

        JPanel introduction = new JPanel();
        introduction.setLayout(new BoxLayout(introduction, BoxLayout.Y_AXIS));
        introduction.setOpaque(false);

        JLabel lblGreeting = new JLabel("GỌI MÓN TẠI BÀN #" + currentTableId);
        lblGreeting.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 18));
        lblGreeting.setForeground(Utils.FOREST_BG);

        JLabel lblSteps = new JLabel("Chọn món  →  Xem giỏ  →  Gửi bếp  →  Theo dõi tiến độ");
        lblSteps.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        lblSteps.setForeground(Utils.TEXT_MUTED);

        introduction.add(lblGreeting);
        introduction.add(Box.createVerticalStrut(4));
        introduction.add(lblSteps);

        lblKitchenNotice = new JLabel();
        lblKitchenNotice.setFont(Utils.getCustomFont("Inter", Font.BOLD, 13));
        lblKitchenNotice.setForeground(Utils.TERRACOTTA);
        lblKitchenNotice.setBorder(new EmptyBorder(9, 0, 0, 0));
        lblKitchenNotice.setVisible(false);

        topBanner.add(introduction, BorderLayout.WEST);
        topBanner.add(rightBannerActions, BorderLayout.EAST);
        topBanner.add(lblKitchenNotice, BorderLayout.SOUTH);

        centerCardLayout = new CardLayout();
        centerCardsPanel = new JPanel(centerCardLayout);
        centerCardsPanel.setBackground(Utils.CREAM_BG);

        JPanel menuPageContainer = new JPanel(new BorderLayout());
        menuPageContainer.setBackground(Utils.CREAM_BG);
        menuPageContainer.setBorder(new EmptyBorder(16, 24, 10, 24));
        menuGridPanel = new JPanel(new GridBagLayout());
        menuGridPanel.setBackground(Utils.CREAM_BG);

        menuScrollPane = new JScrollPane(menuGridPanel,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        menuScrollPane.setVerticalScrollBar(new CScrollBar());
        menuScrollPane.setBorder(null);
        menuScrollPane.getViewport().setBackground(Utils.CREAM_BG);
        menuScrollPane.getVerticalScrollBar().setUnitIncrement(24);
        menuScrollPane.getVerticalScrollBar().setBlockIncrement(160);
        menuScrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int width = menuScrollPane.getViewport().getWidth();
                if (width > 0 && width != lastMenuViewportWidth) {
                    lastMenuViewportWidth = width;
                    renderMenuCards(activeCategoryId);
                }
            }
        });

        menuPageContainer.add(menuScrollPane, BorderLayout.CENTER);
        centerCardsPanel.add(menuPageContainer, VIEW_MENU);
        centerCardsPanel.add(createInlineCartDetailView(), VIEW_CART_DETAIL);
        centerCardsPanel.add(createInlineTrackOrderView(), VIEW_ORDER_TRACKING);

        centerContainer.add(topBanner, BorderLayout.NORTH);
        centerContainer.add(centerCardsPanel, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);
    }

    private void switchCenterView(String viewName) {
        this.currentActiveView = viewName;
        if (VIEW_CART_DETAIL.equals(viewName)) {
            rebuildCartDetailRows();
        }
        centerCardLayout.show(centerCardsPanel, viewName);

        if (lblSummaryToggleAction != null) {
            lblSummaryToggleAction.setText(VIEW_CART_DETAIL.equals(viewName) ? "Thu gọn ▼" : "Xem giỏ ▲");
        }
        btnTrackOrderTop.repaint();
    }

    private JComponent createInlineCartDetailView() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 18));
        wrapper.setBackground(Utils.CREAM_BG);
        wrapper.setBorder(new EmptyBorder(20, 26, 14, 26));

        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setOpaque(false);

        JLabel lblTitle = new JLabel("🛒 Giỏ món chưa gửi bếp — Bàn #" + currentTableId);
        lblTitle.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblTitle.setForeground(Utils.TEXT_DARK);

        JButton btnBackToMenu = createOutlineButton("← Quay lại chọn thêm món");
        btnBackToMenu.addActionListener(e -> switchCenterView(VIEW_MENU));

        headerBar.add(lblTitle, BorderLayout.WEST);
        headerBar.add(btnBackToMenu, BorderLayout.EAST);

        cartItemsListPanel = new JPanel();
        cartItemsListPanel.setLayout(new BoxLayout(cartItemsListPanel, BoxLayout.Y_AXIS));
        cartItemsListPanel.setBackground(Utils.CREAM_BG);

        JScrollPane scrollItems = new JScrollPane(cartItemsListPanel);
        scrollItems.setVerticalScrollBar(new CScrollBar());
        scrollItems.getVerticalScrollBar().setUnitIncrement(16);
        scrollItems.setBorder(null);
        scrollItems.setOpaque(false);
        scrollItems.getViewport().setOpaque(false);

        JPanel checkoutCard = new JPanel(new BorderLayout(25, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(new Color(228, 232, 228));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            }
        };
        checkoutCard.setOpaque(false);
        checkoutCard.setBorder(new EmptyBorder(16, 22, 16, 22));

        JPanel voucherBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        voucherBox.setOpaque(false);
        JLabel lblVoucher = new JLabel("Mã ưu đãi (Voucher):");
        lblVoucher.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        lblVoucher.setForeground(Utils.TEXT_DARK);
        txtVoucher = new CTextField("Nhập mã giảm giá (nếu có)...", 280, 42);
        voucherBox.add(lblVoucher);
        voucherBox.add(txtVoucher);

        JPanel submitBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        submitBox.setOpaque(false);

        lblCartDetailTotal = new JLabel("Tổng tạm tính: 0 VNĐ");
        lblCartDetailTotal.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        lblCartDetailTotal.setForeground(Utils.TERRACOTTA);

        btnSendKitchen = new CButton("Xác nhận & Gửi xuống bếp", 260, 46);
        btnSendKitchen.addActionListener(e -> handleSendOrder());

        submitBox.add(lblCartDetailTotal);
        submitBox.add(btnSendKitchen);

        checkoutCard.add(voucherBox, BorderLayout.WEST);
        checkoutCard.add(submitBox, BorderLayout.EAST);

        JLabel lblCartHint = new JLabel("Món trong giỏ có thể sửa/xóa. Sau khi gửi bếp, hãy nhờ nhân viên hỗ trợ nếu muốn thay đổi.");
        lblCartHint.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        lblCartHint.setForeground(Utils.TEXT_MUTED);

        JPanel cartHeading = new JPanel(new BorderLayout(0, 8));
        cartHeading.setOpaque(false);
        cartHeading.add(headerBar, BorderLayout.NORTH);
        cartHeading.add(lblCartHint, BorderLayout.SOUTH);

        wrapper.add(cartHeading, BorderLayout.NORTH);
        wrapper.add(scrollItems, BorderLayout.CENTER);
        wrapper.add(checkoutCard, BorderLayout.SOUTH);

        return wrapper;
    }

    private JComponent createInlineTrackOrderView() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setBackground(Utils.CREAM_BG);
        wrapper.setBorder(new EmptyBorder(20, 26, 20, 26));

        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setOpaque(false);

        JLabel lblTitle = new JLabel("🔥 Các món đã gửi xuống bếp — Bàn #" + currentTableId);
        lblTitle.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblTitle.setForeground(Utils.TEXT_DARK);

        JButton btnBack = createOutlineButton("← Quay lại Thực Đơn");
        btnBack.addActionListener(e -> switchCenterView(VIEW_MENU));

        JButton btnRefresh = createOutlineButton("↻ Làm mới tiến độ");
        btnRefresh.addActionListener(e -> {
            if (!tabletBUS.isServerConnected()) {
                JOptionPane.showMessageDialog(this, "Chưa kết nối được bếp. Vui lòng gọi nhân viên.");
            } else {
                tabletBUS.requestTableTracking();
            }
        });

        JPanel trackingActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        trackingActions.setOpaque(false);
        trackingActions.add(btnRefresh);
        trackingActions.add(btnBack);
        headerBar.add(lblTitle, BorderLayout.WEST);
        headerBar.add(trackingActions, BorderLayout.EAST);

        trackTable = new CTable();
        JTable rawTable = trackTable.getTable();
        rawTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        btnRequestCancelSupport = createOutlineButton("Nhờ nhân viên hỗ trợ hủy / sửa món");
        btnRequestCancelSupport.setEnabled(false);

        rawTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;

            OrderItemDTO item = getSelectedTrackedItem();

            boolean canRequest = tabletBUS.canRequestCancelOrEdit(item);

            btnRequestCancelSupport.setEnabled(canRequest);
        });

        btnRequestCancelSupport.addActionListener(e -> handleCancelOrEditSupportRequest());

        JLabel lblHint = new JLabel("<html><i>Chọn món đang chờ / bếp đã nhận để nhờ nhân viên xem xét hủy, sửa. Món đang nấu hoặc đã lên món không được tự hủy.</i></html>");
        lblHint.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        lblHint.setForeground(Color.GRAY);

        JPanel trackFooter = new JPanel(new BorderLayout(12, 0));
        trackFooter.setOpaque(false);
        trackFooter.add(lblHint, BorderLayout.CENTER);
        trackFooter.add(btnRequestCancelSupport, BorderLayout.EAST);

        wrapper.add(headerBar, BorderLayout.NORTH);
        wrapper.add(trackTable, BorderLayout.CENTER);
        wrapper.add(trackFooter, BorderLayout.SOUTH);

        return wrapper;
    }

    private void handleCancelOrEditSupportRequest() {
        OrderItemDTO item = getSelectedTrackedItem();
        if (item == null) return;

        String reason = JOptionPane.showInputDialog(this,
                "Lý do cần hủy/sửa món " + item.getFoodsName() + "?",
                "Nhập yêu cầu nhân viên", JOptionPane.QUESTION_MESSAGE);
        if (reason == null || reason.trim().isEmpty()) return;

        if (!tabletBUS.isServerConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối bếp. Vui lòng gọi nhân viên trực tiếp.",
                    "Không thể gửi yêu cầu", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Đây là YÊU CẦU hỗ trợ, không tự động hủy món. Chỉ hủy khi nhân viên/bếp xác nhận.\nBạn muốn gửi yêu cầu?",
                "Xác nhận yêu cầu hỗ trợ", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        requestService("Yêu cầu hỗ trợ sửa/hủy món", "Bàn #" + currentTableId
                + " yêu cầu hỗ trợ món #" + item.getId()
                + " (" + item.getFoodsName() + ", trạng thái " + item.getStatus() + "): " + reason.trim());
    }

    private void initSummaryCartBar() {
        summaryCartWrapper = new JPanel(new BorderLayout());
        summaryCartWrapper.setOpaque(false);
        summaryCartWrapper.setVisible(false);

        JPanel barContent = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Utils.FOREST_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.setColor(Utils.GOLD_ACCENT);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);
            }
        };
        barContent.setOpaque(false);
        barContent.setBorder(new EmptyBorder(6, 10, 6, 10));
        barContent.setCursor(new Cursor(Cursor.HAND_CURSOR));
        barContent.setToolTipText("Bấm để xem hoặc thu gọn giỏ hàng");

        lblSummaryCountBadge = createTerracottaBadge("🛒 0 món", 5, 9);

        lblSummaryTotalMoney = new JLabel("Tổng: 0 VNĐ");
        lblSummaryTotalMoney.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        lblSummaryTotalMoney.setForeground(new Color(253, 224, 71));

        lblSummaryToggleAction = createTerracottaBadge("Xem giỏ ▲", 5, 10);
        lblSummaryToggleAction.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JPanel rightInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        rightInfo.setOpaque(false);
        rightInfo.add(lblSummaryTotalMoney);
        rightInfo.add(lblSummaryToggleAction);

        barContent.add(lblSummaryCountBadge, BorderLayout.WEST);
        barContent.add(rightInfo, BorderLayout.CENTER);

        MouseAdapter toggleCartAdapter = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                switchCenterView(VIEW_CART_DETAIL.equals(currentActiveView) ? VIEW_MENU : VIEW_CART_DETAIL);
            }
        };
        for (JComponent comp : new JComponent[]{barContent, lblSummaryCountBadge, lblSummaryTotalMoney, lblSummaryToggleAction, rightInfo}) {
            comp.addMouseListener(toggleCartAdapter);
        }

        summaryCartWrapper.add(barContent, BorderLayout.CENTER);
    }

    private JLabel createTerracottaBadge(String text, int vPad, int hPad) {
        JLabel badge = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Utils.TERRACOTTA);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                super.paintComponent(g);
            }
        };
        badge.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 12));
        badge.setForeground(Color.WHITE);
        badge.setBorder(new EmptyBorder(vPad, hPad, vPad, hPad));
        return badge;
    }

    private void renderMenuCards(Long categoryId) {
        if (menuGridPanel == null) return;

        int scrollY = menuScrollPane.getVerticalScrollBar().getValue();
        menuGridPanel.removeAll();

        List<FoodDTO> filtered = displayFoods.stream()
                .filter(f -> categoryId == null || categoryId.equals(f.getCategoryId()))
                .collect(Collectors.toList());

        int viewportWidth = menuScrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) {
            viewportWidth = Math.max(ITEMS_PER_ROW * MIN_CARD_WIDTH + (ITEMS_PER_ROW - 1) * CARD_GAP, getWidth() - 298);
        }
        int columns = Math.min(ITEMS_PER_ROW, Math.max(1, (viewportWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP)));
        int cardWidth = Math.min(MAX_CARD_WIDTH, Math.max(MIN_CARD_WIDTH, (viewportWidth - (columns - 1) * CARD_GAP - 2) / columns));

        if (filtered.isEmpty()) {
            JLabel message = new JLabel(displayFoods.isEmpty()
                    ? "Chưa tải được thực đơn từ máy chủ. Vui lòng kiểm tra kết nối hoặc gọi nhân viên."
                    : "Chưa có món ăn trong danh mục này.");
            message.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
            message.setForeground(Utils.TEXT_MUTED);
            menuGridPanel.add(message);
        } else {
            for (int i = 0; i < filtered.size(); i++) {
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.gridx = i % columns;
                gbc.gridy = i / columns;
                gbc.insets = new Insets(0, 0, CARD_GAP, gbc.gridx == columns - 1 ? 0 : CARD_GAP);
                gbc.anchor = GridBagConstraints.NORTHWEST;
                menuGridPanel.add(createStyledFoodCard(filtered.get(i), cardWidth), gbc);
            }
        }

        GridBagConstraints horizontalSpace = new GridBagConstraints();
        horizontalSpace.gridx = columns;
        horizontalSpace.gridy = 0;
        horizontalSpace.weightx = 1;
        horizontalSpace.fill = GridBagConstraints.HORIZONTAL;
        menuGridPanel.add(Box.createHorizontalGlue(), horizontalSpace);

        GridBagConstraints verticalSpace = new GridBagConstraints();
        verticalSpace.gridx = 0;
        verticalSpace.gridy = (filtered.size() + columns - 1) / columns;
        verticalSpace.weighty = 1;
        verticalSpace.fill = GridBagConstraints.VERTICAL;
        menuGridPanel.add(Box.createVerticalGlue(), verticalSpace);

        menuGridPanel.revalidate();
        menuGridPanel.repaint();
        SwingUtilities.invokeLater(() -> menuScrollPane.getVerticalScrollBar().setValue(scrollY));
    }

    private JPanel createStyledFoodCard(FoodDTO food, int cardWidth) {
        int currentQty = getFoodQuantityInCart(food.getId());

        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.setColor(currentQty > 0 ? Utils.TERRACOTTA : new Color(230, 232, 228));
                g2.setStroke(new BasicStroke(currentQty > 0 ? 2f : 1f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 18, 18);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(12, 12, 12, 12));

        Dimension cardSize = new Dimension(cardWidth, CARD_HEIGHT);
        card.setPreferredSize(cardSize);
        card.setMinimumSize(cardSize);
        card.setMaximumSize(cardSize);

        JLabel lblImg = new JLabel("🍲", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(250, 243, 238));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);

                Object cached = getClientProperty("foodImage");
                if (cached instanceof BufferedImage) {
                    BufferedImage image = (BufferedImage) cached;
                    double scale = Math.max((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
                    int drawW = (int) Math.ceil(image.getWidth() * scale);
                    int drawH = (int) Math.ceil(image.getHeight() * scale);
                    int x = (getWidth() - drawW) / 2;
                    int y = (getHeight() - drawH) / 2;
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g2.setClip(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                    g2.drawImage(image, x, y, drawW, drawH, null);
                } else {
                    super.paintComponent(g);
                }
                g2.dispose();
            }
        };
        lblImg.setOpaque(false);
        lblImg.setFont(new Font("SansSerif", Font.PLAIN, 42));
        lblImg.setPreferredSize(new Dimension(cardWidth - 24, IMAGE_HEIGHT));
        loadFoodImage(food, lblImg);

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel lblName = new JLabel(food.getName());
        lblName.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
        lblName.setForeground(Utils.TEXT_DARK);
        lblName.setAlignmentX(Component.LEFT_ALIGNMENT);

        String description = (food.getDescription() == null || food.getDescription().trim().isEmpty())
                ? "Chưa có mô tả cho món ăn này."
                : food.getDescription();

        JTextArea lblDescription = new JTextArea(description);
        lblDescription.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        lblDescription.setForeground(Utils.TEXT_MUTED);
        lblDescription.setOpaque(false);
        lblDescription.setEditable(false);
        lblDescription.setFocusable(false);
        lblDescription.setLineWrap(true);
        lblDescription.setWrapStyleWord(true);
        lblDescription.setBorder(null);
        lblDescription.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblDescription.setPreferredSize(new Dimension(cardWidth - 24, 48));
        lblDescription.setMaximumSize(new Dimension(cardWidth - 24, 48));
        lblDescription.setToolTipText(description);

        JLabel lblPrice = new JLabel(MONEY_FORMAT.format(food.getPrice()));
        lblPrice.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
        lblPrice.setForeground(Utils.TERRACOTTA);
        lblPrice.setAlignmentX(Component.LEFT_ALIGNMENT);

        infoPanel.add(lblName);
        infoPanel.add(Box.createVerticalStrut(8));
        infoPanel.add(lblDescription);
        infoPanel.add(Box.createVerticalGlue());
        infoPanel.add(lblPrice);

        JPanel actionBottom = new JPanel(new BorderLayout(6, 0));
        actionBottom.setOpaque(false);

        if (currentQty == 0) {
            CButton btnAdd = new CButton("+ Chọn Món", cardWidth - 24, 38);
            btnAdd.addActionListener(e -> {
                if (awaitingOrderConfirmation) {
                    JOptionPane.showMessageDialog(this, "Đang chờ bếp xác nhận đơn trước. Vui lòng kiểm tra đơn đã gửi.");
                    return;
                }
                tabletBUS.addFoodToCart(food);
                refreshAllCartViews();
            });
            actionBottom.add(btnAdd, BorderLayout.CENTER);
        } else {
            JPanel stepper = new JPanel(new BorderLayout(2, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(new Color(245, 247, 245));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    g2.setColor(new Color(215, 220, 215));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                }
            };
            stepper.setOpaque(false);
            stepper.setBorder(new EmptyBorder(3, 3, 3, 3));
            stepper.setPreferredSize(new Dimension(92, 36));

            JButton btnMinus = createStepperCircleButton("-");
            JLabel lblQty = new JLabel(String.valueOf(currentQty), SwingConstants.CENTER);
            lblQty.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 14));
            lblQty.setForeground(Utils.TEXT_DARK);
            JButton btnPlus = createStepperCircleButton("+");

            btnMinus.addActionListener(e -> changeFoodQuantity(food.getId(), -1));
            btnPlus.addActionListener(e -> changeFoodQuantity(food.getId(), 1));

            stepper.add(btnMinus, BorderLayout.WEST);
            stepper.add(lblQty, BorderLayout.CENTER);
            stepper.add(btnPlus, BorderLayout.EAST);

            JTextField txtItemNote = createInlineItemNoteTextField(food.getId(), "Ghi chú...");

            actionBottom.add(stepper, BorderLayout.WEST);
            actionBottom.add(txtItemNote, BorderLayout.CENTER);
        }

        card.add(lblImg, BorderLayout.NORTH);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(actionBottom, BorderLayout.SOUTH);
        return card;
    }

    private void loadFoodImage(FoodDTO food, JLabel label) {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        if (food.getCoverImageUrl() != null && !food.getCoverImageUrl().isBlank()) {
            sources.add(food.getCoverImageUrl().trim());
        }
        if (food.getImages() != null) {
            for (FoodImageDTO img : food.getImages()) {
                if (img != null && Boolean.TRUE.equals(img.getIsCover()) && img.getUrl() != null && !img.getUrl().isBlank()) {
                    sources.add(img.getUrl().trim());
                }
            }
            for (FoodImageDTO img : food.getImages()) {
                if (img != null && img.getUrl() != null && !img.getUrl().isBlank()) {
                    sources.add(img.getUrl().trim());
                }
            }
        }
        if (food.getSlug() != null && !food.getSlug().isBlank()) {
            sources.add("/img/foods/" + food.getSlug().trim());
        }
        if (sources.isEmpty()) return;

        List<String> candidates = new ArrayList<>(sources);
        String cacheKey = String.join("\n", candidates);
        BufferedImage cached = foodImageCache.get(cacheKey);
        if (cached != null) {
            label.putClientProperty("foodImage", cached);
            return;
        }
        Long failedAt = failedImageTimes.get(cacheKey);

        if (failedAt != null && System.currentTimeMillis() - failedAt < IMAGE_RETRY_MS) {
            return;
        }

        List<JLabel> listeners = loadingFoodImages.get(cacheKey);
        if (listeners != null) {
            listeners.add(label);
            return;
        }
        listeners = new ArrayList<>();
        listeners.add(label);
        loadingFoodImages.put(cacheKey, listeners);

        new SwingWorker<BufferedImage, Void>() {
            @Override
            protected BufferedImage doInBackground() {
                for (String source : candidates) {
                    BufferedImage image = ImageLoader.readImage(source);
                    if (image != null) return image;
                }
                return null;
            }

            @Override
            protected void done() {
                BufferedImage image = null;
                try {
                    image = get();
                } catch (Exception ignored) {
                }
                if (image != null) {
                    foodImageCache.put(cacheKey, image);
                    failedImageTimes.remove(cacheKey);
                } else {
                    failedImageTimes.put(cacheKey, System.currentTimeMillis());
                }
                List<JLabel> waiting = loadingFoodImages.remove(cacheKey);
                if (image != null && waiting != null) {
                    for (JLabel target : waiting) {
                        target.putClientProperty("foodImage", image);
                        target.repaint();
                    }
                }
            }
        }.execute();
    }

    private CTextField createInlineItemNoteTextField(Long foodId, String placeholder) {
        CTextField txtField = new CTextField("📝 " + placeholder, 110, 36, Utils.getCustomFont("Inter", Font.PLAIN, 12));
        txtField.setBorder(new EmptyBorder(4, 8, 4, 8));
        txtField.setText(itemNotesMap.getOrDefault(foodId, ""));

        txtField.getDocument().addDocumentListener(new DocumentListener() {
            private void syncNote() {
                String val = txtField.getText().trim();
                if (val.isEmpty()) itemNotesMap.remove(foodId);
                else itemNotesMap.put(foodId, val);
            }
            @Override public void insertUpdate(DocumentEvent e) { syncNote(); }
            @Override public void removeUpdate(DocumentEvent e) { syncNote(); }
            @Override public void changedUpdate(DocumentEvent e) { syncNote(); }
        });
        return txtField;
    }

    private JButton createStepperCircleButton(String symbol) {
        JButton btn = new JButton(symbol) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? Utils.TERRACOTTA : Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                super.paintComponent(g);
            }
        };
        btn.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        btn.setForeground(Utils.TEXT_DARK);
        btn.setPreferredSize(new Dimension(26, 28));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(null);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private int getFoodQuantityInCart(Long foodId) {
        if (tabletBUS == null) return 0;
        return tabletBUS.getCurrentCart().stream()
                .filter(item -> item.getFoodsId().equals(foodId))
                .mapToInt(OrderItemDTO::getQuantity)
                .findFirst()
                .orElse(0);
    }

    private void changeFoodQuantity(Long foodId, int delta) {
        if (awaitingOrderConfirmation) {
            JOptionPane.showMessageDialog(this, "Đơn đang chờ bếp xác nhận. Vui lòng đợi phản hồi trước khi sửa giỏ.");
            return;
        }
        tabletBUS.updateCartQuantity(foodId, delta);
        if (getFoodQuantityInCart(foodId) <= 0) {
            itemNotesMap.remove(foodId);
        }
        refreshAllCartViews();
    }

    private void refreshAllCartViews() {
        if (tabletBUS == null) return;

        renderMenuCards(activeCategoryId);
        rebuildCartDetailRows();

        int totalQuantity = tabletBUS.getCurrentCart().stream()
                .mapToInt(OrderItemDTO::getQuantity)
                .sum();

        String formattedSubtotal = MONEY_FORMAT.format(tabletBUS.calculateCartSubtotal());
        lblCartDetailTotal.setText("Tổng tạm tính: " + formattedSubtotal);

        if (totalQuantity > 0) {
            lblSummaryCountBadge.setText("🛒 " + totalQuantity + " món");
            lblSummaryTotalMoney.setText("Tổng: " + formattedSubtotal);
            summaryCartWrapper.setVisible(true);
        } else {
            summaryCartWrapper.setVisible(false);
            if (VIEW_CART_DETAIL.equals(currentActiveView)) {
                switchCenterView(VIEW_MENU);
            }
        }

        summaryCartWrapper.revalidate();
        summaryCartWrapper.repaint();
    }

    private void rebuildCartDetailRows() {
        if (cartItemsListPanel == null || tabletBUS == null) return;
        cartItemsListPanel.removeAll();

        for (OrderItemDTO item : tabletBUS.getCurrentCart()) {
            cartItemsListPanel.add(createInlineCartItemRow(item));
            cartItemsListPanel.add(Box.createVerticalStrut(12));
        }

        cartItemsListPanel.revalidate();
        cartItemsListPanel.repaint();
    }

    private JPanel createInlineCartItemRow(OrderItemDTO item) {
        JPanel rowCard = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.setColor(new Color(228, 232, 228));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            }
        };
        rowCard.setOpaque(false);
        rowCard.setBorder(new EmptyBorder(14, 20, 14, 20));
        rowCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 84));

        JPanel leftCol = new JPanel();
        leftCol.setLayout(new BoxLayout(leftCol, BoxLayout.Y_AXIS));
        leftCol.setOpaque(false);
        leftCol.setPreferredSize(new Dimension(230, 52));

        JLabel lblName = new JLabel(item.getFoodsName());
        lblName.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 16));
        lblName.setForeground(Utils.TEXT_DARK);

        JLabel lblUnit = new JLabel("Đơn giá: " + MONEY_FORMAT.format(item.getUnitPrice()));
        lblUnit.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        lblUnit.setForeground(Color.GRAY);

        leftCol.add(lblName);
        leftCol.add(Box.createVerticalStrut(4));
        leftCol.add(lblUnit);

        JPanel centerCol = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 6));
        centerCol.setOpaque(false);

        JPanel qtyBox = new JPanel(new BorderLayout(8, 0));
        qtyBox.setOpaque(false);
        JButton btnMinus = createOutlineButton("-");
        JLabel lblQty = new JLabel(String.valueOf(item.getQuantity()), SwingConstants.CENTER);
        lblQty.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblQty.setPreferredSize(new Dimension(32, 32));
        JButton btnPlus = createOutlineButton("+");

        btnMinus.addActionListener(e -> changeFoodQuantity(item.getFoodsId(), -1));
        btnPlus.addActionListener(e -> changeFoodQuantity(item.getFoodsId(), 1));

        qtyBox.add(btnMinus, BorderLayout.WEST);
        qtyBox.add(lblQty, BorderLayout.CENTER);
        qtyBox.add(btnPlus, BorderLayout.EAST);

        JTextField txtRowNote = createInlineItemNoteTextField(
                item.getFoodsId(),
                "Ghi chú cho " + item.getFoodsName() + " (VD: Ít cay, không hành...)"
        );
        txtRowNote.setPreferredSize(new Dimension(320, 38));

        centerCol.add(qtyBox);
        centerCol.add(txtRowNote);

        JPanel rightCol = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 8));
        rightCol.setOpaque(false);

        JLabel lblLineTotal = new JLabel(MONEY_FORMAT.format(item.getLineTotal()));
        lblLineTotal.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 16));
        lblLineTotal.setForeground(Utils.TERRACOTTA);

        JButton btnRemove = createOutlineButton("✕ Xóa");
        btnRemove.addActionListener(e -> changeFoodQuantity(item.getFoodsId(), -item.getQuantity()));

        rightCol.add(lblLineTotal);
        rightCol.add(btnRemove);

        rowCard.add(leftCol, BorderLayout.WEST);
        rowCard.add(centerCol, BorderLayout.CENTER);
        rowCard.add(rightCol, BorderLayout.EAST);

        return rowCard;
    }

    private void refreshTrackTable() {
        if (trackTable == null || tabletBUS == null) return;
        List<OrderItemDTO> tracked = tabletBUS.getTrackedItems();
        trackTable.updateTrackedItems(tracked);
        trackTable.getTable().clearSelection();
        if (btnRequestCancelSupport != null) btnRequestCancelSupport.setEnabled(false);
        if (btnTrackOrderTop != null) {
            btnTrackOrderTop.setText("📋 Đơn đã gửi (" + tracked.size() + ")");
        }
    }

    private JButton createOutlineButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 12));
        btn.setForeground(Utils.FOREST_BG);
        btn.setBackground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(205, 210, 205), 1, true),
                new EmptyBorder(7, 14, 7, 14)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private String formatStatusVietnamese(OrderStatus st) {
        if (st == null) return "⏳ Chờ xác nhận";
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
        if (awaitingOrderConfirmation) return;
        if (!tabletBUS.isServerConnected()) {
            JOptionPane.showMessageDialog(this,
                    "Không thể gửi đơn khi chưa kết nối bếp. Vui lòng kiểm tra ServerApp.",
                    "Chưa kết nối", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quantity = tabletBUS.getCurrentCart().stream().mapToInt(OrderItemDTO::getQuantity).sum();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bàn #" + currentTableId + " — " + quantity + " phần món\n"
                        + "Tạm tính: " + MONEY_FORMAT.format(tabletBUS.calculateCartSubtotal()) + "\n\n"
                        + "Xác nhận gửi xuống bếp? Sau khi gửi, nếu muốn hủy/sửa món bạn cần liên hệ nhân viên.",
                "Xác nhận gọi món", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        String voucher = txtVoucher.getText().trim();
        if (voucher.equals("Nhập mã giảm giá (nếu có)...")) {
            voucher = "";
        }



        if (tabletBUS.submitOrderToServer(voucher, new HashMap<>(itemNotesMap))) {
            awaitingOrderConfirmation = true;
            btnSendKitchen.setEnabled(false);
            btnSendKitchen.setText("Đang chờ xác nhận...");
        } else {
            JOptionPane.showMessageDialog(this,
                    "Chưa kết nối được với ServerApp (Port 8888)!\nHãy bật KitchenServerGUI bên ServerApp trước.",
                    "Lỗi Kết Nối Mạng", JOptionPane.ERROR_MESSAGE);
        }
    }

    private OrderItemDTO getSelectedTrackedItem() {
        if (trackTable == null || tabletBUS == null) {
            return null;
        }

        Long itemId = trackTable.getSelectedOrderItemId();

        if (itemId == null) return null;

        return tabletBUS.getTrackedItems().stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElse(null);
    }

    public static void main(String[] args) {
        AuthGUI.main(args);
    }
}