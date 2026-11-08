package gui;

import api.SocketClient;
import bus.ClientTabletBUS;
import components.*;
import dto.*;
import utils.Utils;

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

    private final SocketClient sessionSocket;
    private Long currentTableId;
    private Long activeSessionId;
    private final ClientTabletBUS tabletBUS;
    private javax.swing.Timer responseTimer;
    private Long activeCategoryId = null;

    private final Map<Long, String> itemNotesMap = new HashMap<>();
    private final Map<Long, JLabel> foodImageLabels = new HashMap<>();
    private final Set<Long> completedNotified = new HashSet<>();

    private JPanel categoryListPanel, menuGridPanel;
    private JScrollPane menuScrollPane;
    private int lastMenuViewportWidth = -1;

    private JLabel lblKitchenNotice;

    private JLabel lblSentItems;
    private JButton btnMemberAccount;

    private UserDTO loggedInMember;
    private JDialog memberProfileDialog;
    private JTextArea memberProfileArea;

    private String memberProfileText = "Đang tải thông tin...";
    private String memberHistoryText = "Đang tải lịch sử...";

    private JButton btnTrackOrderTop;
    private CTable trackTable;
    private JButton btnRequestCancelSupport;
    private CButton btnSendKitchen;
    private boolean awaitingOrderConfirmation;

    // Doi man hinh noi bo bang remove/add; khong dung bo cuc dang the.
    private JPanel centerContentHost;
    private JComponent menuView;
    private JComponent cartView;
    private JComponent trackingView;
    private String currentActiveView = VIEW_MENU;

    private JPanel summaryCartWrapper;
    private JLabel lblSummaryCountBadge;
    private JLabel lblSummaryTotalMoney;
    private JLabel lblSummaryToggleAction;

    private JPanel cartItemsListPanel;
    private JLabel lblCartDetailTotal;

    /**
     * Chỉ gọi SAU KHI StaffGUI nhận ACK ENTER_CUSTOMER_MODE từ Server.
     */
    public CustomerGUI(Long tableId, Long sessionId, SocketClient customerSocket) {
        this.sessionSocket = Objects.requireNonNull(customerSocket, "Can Socket da xac nhan CUSTOMER MODE");

        if (tableId == null || tableId <= 0 || sessionId == null || sessionId <= 0
                || !customerSocket.isConnected())
            throw new IllegalArgumentException("Thieu phien ban da xac minh");
        this.currentTableId = tableId;
        this.activeSessionId = sessionId;
        this.sessionSocket.bindCustomerTable(tableId);
        this.tabletBUS = new ClientTabletBUS(tableId, sessionId, sessionSocket);

        setTitle("An Nhien | Goi mon tai ban #" + tableId);
        setSize(1360, 820);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        initLeftSidebar();
        initCenterContentArea();

        rebuildCategoryButtons(tabletBUS.getCategories());
        refreshAllCartViews();
        refreshTrackTable();
        startResponseTimer();
        loadInitialData();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                logout();
            }
        });
    }

    private void loadInitialData() {
        SwingUtilities.invokeLater(() -> {
            if (!sessionSocket.isConnected()) {
                renderMenuCards(activeCategoryId);
                CToast.error(this,
                        "Mất kết nối máy chủ. Không thể tải thực đơn.");
                return;
            }

            // Yêu cầu thực đơn nếu chưa nhận dữ liệu.
            if (!tabletBUS.hasReceivedMenuResponse()) {
                tabletBUS.reloadMenu();
            }

            // Lấy các món đã gọi của phiên bàn hiện tại.
            tabletBUS.requestTableTracking();
        });
    }

    private void startResponseTimer() {
        responseTimer = new javax.swing.Timer(100, e -> {
            if (!sessionSocket.isConnected()
                    && awaitingOrderConfirmation) {
                resetSendOrderButtonState();
                CToast.error(this,
                        "Mất kết nối máy chủ. Kiểm tra đơn trước khi gửi lại.");
            }

            processServerPackets();
            updateLoadedImages();
        });

        responseTimer.start();
    }

    private void returnToLogin(String message) {
        if (responseTimer != null) responseTimer.stop();
        sessionSocket.logout();
        sessionSocket.close();
        getContentPane().removeAll();
        if (tabletBUS != null) {
            itemNotesMap.clear();
            foodImageLabels.clear();
            completedNotified.clear();
        }
        dispose();
        AuthGUI login = new AuthGUI();
        login.setVisible(true);
        if (message != null && !message.isBlank()) CToast.info(login, message);
    }

    private void onServerPacketCallback(SocketPacket packet) {
        if (!packet.isSuccess()) {
            if (packet.getAction() == SocketAction.GET_MENU_AND_CATEGORIES) {
                // Phan biet loi SQL/Server voi truong hop khong co mon duoc mo ban.
                renderMenuCards(activeCategoryId);
            }
            if (packet.getAction() == SocketAction.PLACE_ORDER
                    || packet.getAction() == SocketAction.ORDER_SUBMIT) {
                resetSendOrderButtonState();
            }
            CToast.show(this, packet.getMessage(), "Thông báo từ Bếp Nhà", CToast.WARNING);
            return;
        }

        switch (packet.getAction()) {
            case REGISTER_TABLE:
                tabletBUS.requestTableTracking();
                break;

            case GET_MENU_AND_CATEGORIES:
                rebuildCategoryButtons(tabletBUS.getCategories());
                renderMenuCards(activeCategoryId);
                break;

            case ORDER_SUBMIT:
            case PLACE_ORDER:
                resetSendOrderButtonState();
                itemNotesMap.clear();
                refreshAllCartViews();
                refreshTrackTable();
                switchCenterView(VIEW_ORDER_TRACKING);
                tabletBUS.requestTableTracking();
                CToast.show(this, packet.getMessage(), "Đặt món thành công", CToast.SUCCESS);
                break;

            case GET_TABLE_TRACKING:
                refreshTrackTable();
                break;

            case ITEM_STATUS_CHANGED:
            case KITCHEN_UPDATE_STATUS:
                refreshTrackTable();
                if (packet.getData() instanceof Long && packet.getExtraData() == OrderStatus.COMPLETED) {
                    Long completedId = (Long) packet.getData();
                    if (completedNotified.add(completedId)) {
                        OrderItemDTO completedItem = tabletBUS.getTrackedItemById(completedId);
                        String foodName = completedItem != null ? completedItem.getFoodsName() : "Món #" + completedId;
                        lblKitchenNotice.setText("🔔 " + foodName + " đã chế biến xong — vui lòng chờ nhân viên mang món.");
                        lblKitchenNotice.setVisible(true);
                    }
                }
                break;

            case CALL_STAFF:
                CToast.success(this, packet.getMessage());
                break;

            case ORDER_COMPLETED:
                lblKitchenNotice.setText("🔔 Một đơn đã hoàn tất chế biến — nhân viên sẽ mang món ra bàn.");
                lblKitchenNotice.setVisible(true);
                CToast.success(this, packet.getMessage());
                tabletBUS.requestTableTracking();
                break;
            case VIEW_SESSION_BILL:
                showSessionBill((OrderDTO) packet.getData());
                break;

            case MEMBER_LOGIN:
                if (packet.getData() instanceof UserDTO) {
                    loggedInMember = (UserDTO) packet.getData();

                    btnMemberAccount.setText(
                            "Tài khoản: " + loggedInMember.getFullName()
                    );

                    CToast.success(this,
                            "Đăng nhập thành viên thành công.");
                }
                break;

            case MEMBER_PROFILE:
                memberProfileText = String.valueOf(
                        packet.getData() == null
                                ? "Chưa có thông tin."
                                : packet.getData()
                );
                updateMemberProfileText();
                break;

            case MEMBER_HISTORY:
                memberHistoryText = String.valueOf(
                        packet.getData() == null
                                ? "Chưa có lịch sử đặt món."
                                : packet.getData()
                );
                updateMemberProfileText();
                break;

            case MEMBER_LOGOUT:
                loggedInMember = null;
                btnMemberAccount.setText("Đăng nhập thành viên");

                if (memberProfileDialog != null) {
                    memberProfileDialog.dispose();
                    memberProfileDialog = null;
                    memberProfileArea = null;
                }

                CToast.success(this,
                        "Đã đăng xuất tài khoản thành viên.");
                break;
            case SESSION_CLOSED:
            case TABLE_CLEARED_RESET:
                returnToLogin("Đã đóng phiên bàn. Thiết bị sẵn sàng cho lượt khách tiếp theo.");
                return;

            default:
                break;
        }
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

        JLabel lblIcon = new JLabel(Utils.getScaledIcon("/img/things/logo.png", 45, 45));
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

        topBrandBox.add(logoRow);
        topBrandBox.add(Box.createVerticalStrut(22));
        topBrandBox.add(tableBadge);

        categoryListPanel = new CategoryListPanel();
        categoryListPanel.setLayout(new BoxLayout(categoryListPanel, BoxLayout.Y_AXIS));
        categoryListPanel.setOpaque(false);

        CButton btnCallStaff = new CButton("Gọi nhân viên hỗ trợ", 205, 46);
        btnCallStaff.addActionListener(e -> {
            String req = JOptionPane.showInputDialog(this,
                    "Quý khách cần hỗ trợ gì? (Thêm đồ dùng, chỉnh món, hủy món, gọi tính tiền...):",
                    "Gọi nhân viên Bếp Nhà", JOptionPane.PLAIN_MESSAGE);
            if (req != null && !req.trim().isEmpty()) {
                if (!tabletBUS.callStaffSupport(req.trim())) {
                    CToast.warning(this, "Chưa xác thực phiên bàn hoặc mất kết nối.");
                }
            }
        });

        JButton btnViewBill = new JButton("Xem tạm tính của bàn");
        btnViewBill.addActionListener(e -> {
            if (!tabletBUS.requestBillPreview()) CToast.warning(this, "Khong tai duoc tam tinh");
        });
        btnViewBill.setPreferredSize(new Dimension(205, 43));
        btnViewBill.setMaximumSize(new Dimension(205, 43));

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

        JButton btnLogout = new JButton("Trả thiết bị / Đăng xuất");
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
        for (JComponent comp : new JComponent[]{btnCallStaff, btnViewBill, btnRequestBill, btnLogout}) {
            comp.setAlignmentX(Component.CENTER_ALIGNMENT);
            sidebarActions.add(comp);
            sidebarActions.add(Box.createVerticalStrut(8));
        }

        sidebar.add(topBrandBox, BorderLayout.NORTH);
        sidebar.add(createCategoryScrollPane(categoryListPanel), BorderLayout.CENTER);
        sidebar.add(sidebarActions, BorderLayout.SOUTH);

        add(sidebar, BorderLayout.WEST);
    }

    // Danh mục cuộn độc lập; các nút gọi nhân viên/tính tiền vẫn cố định.
    private static JScrollPane createCategoryScrollPane(JPanel categories) {
        JScrollPane scroll = new JScrollPane(categories,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setVerticalScrollBar(new CScrollBar());
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.setWheelScrollingEnabled(true);
        scroll.setMinimumSize(new Dimension(0, 0));
        return scroll;
    }

    private static class CategoryListPanel extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 24;
        }
        @Override public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(24, visible.height - 44);
        }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private void requestService(String title, String message) {
        if (tabletBUS == null || !tabletBUS.isServerConnected()) {
            CToast.show(this,
                    "Mất kết nối. Vui lòng thử lại hoặc gọi nhân viên.",
                    title, CToast.WARNING);
            return;
        }
        if (tabletBUS.callStaffSupport(message)) {
            CToast.info(this, "Đang gửi yêu cầu. Vui lòng đợi phản hồi.");
        } else {
            CToast.warning(this, "Chưa xác thực phiên bàn hoặc mất kết nối.");
        }
    }

    private void showSessionBill(OrderDTO bill) {
        if (bill == null) {
            CToast.info(this, "Bàn chưa có món đã gọi.");
            return;
        }
        StringBuilder lines = new StringBuilder();
        lines.append("TẠM TÍNH CẢ BÀN #").append(currentTableId).append("\n\n");
        if (bill.getItems() != null) for (OrderItemDTO item : bill.getItems()) {
            lines.append(item.getFoodsName()).append(" x ").append(item.getQuantity())
                    .append(" = ").append(MONEY_FORMAT.format(item.getLineTotal())).append("\n");
        }
        lines.append("\nTẠM TÍNH CẢ BÀN: ").append(MONEY_FORMAT.format(bill.getTotalAmount()));
        lines.append("\nVui lòng liên hệ nhân viên để thanh toán tại quầy.");
        JTextArea details = new JTextArea(lines.toString(), 18, 44);
        details.setEditable(false);
        JDialog dlg = new JDialog(this, "Hóa đơn tạm tính", false);
        dlg.add(new JScrollPane(details));
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void logout() {
        String alert = tabletBUS != null && !tabletBUS.getCurrentCart().isEmpty()
                ? "Giỏ món chưa gửi sẽ không được lưu khi trả thiết bị.\n" : "";
        int answer = JOptionPane.showConfirmDialog(this,
                alert + "Các món đã gửi vẫn ở bếp. Trả thiết bị KHÔNG đóng phiên bàn.\nBạn muốn trả thiết bị?",
                "Trả thiết bị dùng chung", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) returnToLogin(null);
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
        btn.setPreferredSize(new Dimension(190, 44));
        btn.setMinimumSize(new Dimension(0, 44));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
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

        btnTrackOrderTop = new JButton("Món đã gọi (0 phần)") {
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
        introduction.setPreferredSize(new Dimension(425, 90));

// Dòng đầu: Thông tin bàn và đăng nhập thành viên
        JPanel headerInfo = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerInfo.setOpaque(false);
        headerInfo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblTable = new JLabel("Bàn #" + currentTableId);
        lblTable.setFont(Utils.getCustomFont(
                "Inter", Font.BOLD, 17));
        lblTable.setForeground(Utils.FOREST_BG);

        btnMemberAccount = createOutlineButton("Đăng nhập thành viên");
        btnMemberAccount.addActionListener(e -> {
            if (loggedInMember == null) {
                openMemberLoginDialog();
            } else {
                openMemberProfile();
            }
        });

        headerInfo.add(lblTable);
        headerInfo.add(btnMemberAccount);

// Số món đã gửi
        lblSentItems = new JLabel("Đã gọi: 0 dòng món / 0 phần");
        lblSentItems.setFont(Utils.getCustomFont(
                "Inter", Font.PLAIN, 13));
        lblSentItems.setForeground(Utils.TEXT_MUTED);
        lblSentItems.setAlignmentX(Component.LEFT_ALIGNMENT);

// Thông báo từ màn hình bếp
        lblKitchenNotice = new JLabel("Chưa có thông báo mới từ bếp.");
        lblKitchenNotice.setFont(Utils.getCustomFont(
                "Inter", Font.PLAIN, 12));
        lblKitchenNotice.setForeground(Utils.TERRACOTTA);
        lblKitchenNotice.setAlignmentX(Component.LEFT_ALIGNMENT);

        introduction.add(headerInfo);
        introduction.add(Box.createVerticalStrut(8));
        introduction.add(lblSentItems);
        introduction.add(Box.createVerticalStrut(6));
        introduction.add(lblKitchenNotice);

        topBanner.add(introduction, BorderLayout.WEST);
        topBanner.add(rightBannerActions, BorderLayout.EAST);
        topBanner.add(lblKitchenNotice, BorderLayout.SOUTH);

        centerContentHost = new JPanel(new BorderLayout());
        centerContentHost.setBackground(Utils.CREAM_BG);

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
        menuView = menuPageContainer;
        cartView = createInlineCartDetailView();
        trackingView = createInlineTrackOrderView();
        centerContentHost.add(menuView, BorderLayout.CENTER);

        centerContainer.add(topBanner, BorderLayout.NORTH);
        centerContainer.add(centerContentHost, BorderLayout.CENTER);

        add(centerContainer, BorderLayout.CENTER);
    }

    private void switchCenterView(String viewName) {
        this.currentActiveView = viewName;
        if (VIEW_CART_DETAIL.equals(viewName)) {
            rebuildCartDetailRows();
        }
        JComponent nextView = VIEW_CART_DETAIL.equals(viewName) ? cartView
                : VIEW_ORDER_TRACKING.equals(viewName) ? trackingView : menuView;
        centerContentHost.removeAll();
        centerContentHost.add(nextView, BorderLayout.CENTER);
        centerContentHost.revalidate();
        centerContentHost.repaint();

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

        JPanel submitBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 0));
        submitBox.setOpaque(false);

        lblCartDetailTotal = new JLabel("Tạm tính lượt gọi này: 0 VNĐ");
        lblCartDetailTotal.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        lblCartDetailTotal.setForeground(Utils.TERRACOTTA);

        btnSendKitchen = new CButton("Xác nhận & Gửi xuống bếp", 260, 46);
        btnSendKitchen.addActionListener(e -> handleSendOrder());

        submitBox.add(lblCartDetailTotal);
        submitBox.add(btnSendKitchen);

        // Flow iPad: voucher khong nam trong nghiep vu ORDER_SUBMIT hien tai.
        // Khong hien thi o nhap voucher khi Server khong ap dung.
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
                CToast.warning(this, "Chưa kết nối được bếp. Vui lòng gọi nhân viên.");
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
            btnRequestCancelSupport.setEnabled(tabletBUS != null && tabletBUS.canRequestCancelOrEdit(item));
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
            CToast.show(this, "Chưa kết nối bếp. Vui lòng gọi nhân viên trực tiếp.",
                    "Không thể gửi yêu cầu", CToast.WARNING);
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

        lblSummaryCountBadge = createTerracottaBadge("🛒 0 phần", 5, 9);

        lblSummaryTotalMoney = new JLabel("Giỏ chưa gửi: 0 VNĐ");
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
        if (menuGridPanel == null || tabletBUS == null) return;

        int scrollY = menuScrollPane.getVerticalScrollBar().getValue();
        menuGridPanel.removeAll();
        foodImageLabels.clear();

        List<FoodDTO> filtered = tabletBUS.getFoodsByCategory(categoryId);

        int viewportWidth = menuScrollPane.getViewport().getWidth();
        if (viewportWidth <= 0) {
            viewportWidth = Math.max(ITEMS_PER_ROW * MIN_CARD_WIDTH + (ITEMS_PER_ROW - 1) * CARD_GAP, getWidth() - 298);
        }
        int columns = Math.min(ITEMS_PER_ROW, Math.max(1, (viewportWidth + CARD_GAP) / (MIN_CARD_WIDTH + CARD_GAP)));
        int cardWidth = Math.min(MAX_CARD_WIDTH, Math.max(MIN_CARD_WIDTH, (viewportWidth - (columns - 1) * CARD_GAP - 2) / columns));

        if (filtered.isEmpty()) {
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST;

            menuGridPanel.add(createEmptyMenuPanel(), gbc);
        } else {
            // Giữ nguyên vòng for tạo thẻ món ăn hiện tại.
            for (int i = 0; i < filtered.size(); i++) {
                GridBagConstraints gbc = new GridBagConstraints();

                gbc.gridx = i % columns;
                gbc.gridy = i / columns;
                gbc.insets = new Insets(
                        0, 0, CARD_GAP,
                        gbc.gridx == columns - 1 ? 0 : CARD_GAP
                );
                gbc.anchor = GridBagConstraints.NORTHWEST;

                menuGridPanel.add(
                        createStyledFoodCard(filtered.get(i), cardWidth),
                        gbc
                );
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
        verticalSpace.gridy = filtered.isEmpty() ? 1 : (filtered.size() + columns - 1) / columns;
        verticalSpace.weighty = 1;
        verticalSpace.fill = GridBagConstraints.VERTICAL;
        menuGridPanel.add(Box.createVerticalGlue(), verticalSpace);

        menuGridPanel.revalidate();
        menuGridPanel.repaint();
        SwingUtilities.invokeLater(() -> menuScrollPane.getVerticalScrollBar().setValue(scrollY));
    }

    private JPanel createEmptyMenuPanel() {
        JPanel panel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 0, 14)
        );
        panel.setOpaque(false);

        JLabel message = new JLabel(getMenuStatusMessage());
        message.setFont(
                Utils.getCustomFont("Inter", Font.PLAIN, 14)
        );
        message.setForeground(Utils.TEXT_MUTED);

        panel.add(message);
        return panel;
    }

    /**
     * Xác định thông báo dựa trên trạng thái dữ liệu.
     */
    private String getMenuStatusMessage() {
        if (!tabletBUS.isConnected()) {
            return "Đã mất kết nối máy chủ.";
        }

        if (tabletBUS.getMenuLoadError() != null) {
            return "Không tải được thực đơn: "
                    + tabletBUS.getMenuLoadError();
        }

        if (!tabletBUS.hasReceivedMenuResponse()) {
            return "Đang tải thực đơn từ máy chủ...";
        }

        if (tabletBUS.getMenuFoods().isEmpty()) {
            return "Hiện chưa có món ăn được mở bán.";
        }

        return "Danh mục này chưa có món ăn.";
    }

    private JPanel createStyledFoodCard(FoodDTO food, int cardWidth) {
        int currentQty = tabletBUS.getFoodQuantityInCart(food.getId());

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

        // Đăng ký nhãn ảnh và lấy ảnh qua BUS (dùng hàng đợi, không dùng Consumer)
        foodImageLabels.put(food.getId(), lblImg);
        BufferedImage cachedImg = tabletBUS.getOrRequestFoodImage(food);
        if (cachedImg != null) {
            lblImg.putClientProperty("foodImage", cachedImg);
        }

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
                    CToast.warning(this, "Đang gửi yêu cầu gọi món. Vui lòng đợi phản hồi.");
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

            @Override
            public void insertUpdate(DocumentEvent e) {
                syncNote();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                syncNote();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                syncNote();
            }
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

    private void changeFoodQuantity(Long foodId, int delta) {
        if (awaitingOrderConfirmation) {
            CToast.warning(this, "Đang gửi yêu cầu gọi món. Vui lòng đợi phản hồi trước khi sửa giỏ.");
            return;
        }
        tabletBUS.updateCartQuantity(foodId, delta);
        if (tabletBUS.getFoodQuantityInCart(foodId) <= 0) {
            itemNotesMap.remove(foodId);
        }
        refreshAllCartViews();
    }

    private void refreshAllCartViews() {
        if (tabletBUS == null) return;

        renderMenuCards(activeCategoryId);
        rebuildCartDetailRows();

        int totalQuantity = tabletBUS.getTotalCartQuantity();
        String formattedSubtotal = MONEY_FORMAT.format(tabletBUS.calculateCartSubtotal());
        lblCartDetailTotal.setText("Tạm tính lượt gọi này: " + formattedSubtotal);

        if (totalQuantity > 0) {
            lblSummaryCountBadge.setText("🛒 " + totalQuantity + " phần");
            lblSummaryTotalMoney.setText("Giỏ chưa gửi: " + formattedSubtotal);
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
        if (trackTable == null || tabletBUS == null) {
            return;
        }

        List<OrderItemDTO> tracked = tabletBUS.getTrackedItems();

        trackTable.updateTrackedItems(tracked);
        if (btnRequestCancelSupport != null) {
            btnRequestCancelSupport.setEnabled(tabletBUS.canRequestCancelOrEdit(getSelectedTrackedItem()));
        }
        int activeLines = 0;
        int portions = 0;
        int cancelled = 0;
        for (OrderItemDTO item : tracked) {
            if (item.getStatus() == OrderStatus.CANCELLED) { cancelled++; continue; }
            activeLines++;
            if (item.getQuantity() != null) portions += item.getQuantity();
        }
        String summary = activeLines + " dòng món / " + portions + " phần"
                + (cancelled > 0 ? " · " + cancelled + " dòng đã hủy" : "");
        if (lblSentItems != null) lblSentItems.setText("Đã gọi: " + summary);
        if (btnTrackOrderTop != null) btnTrackOrderTop.setText("Món đã gọi (" + portions + " phần)");
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

    private void handleSendOrder() {
        if (tabletBUS.getCurrentCart().isEmpty()) {
            CToast.warning(this, "Giỏ món của bàn đang trống!");
            return;
        }
        if (awaitingOrderConfirmation) return;
        if (!tabletBUS.isServerConnected()) {
            CToast.show(this,
                    "Mất kết nối. Vui lòng thử lại hoặc gọi nhân viên.",
                    "Chưa kết nối", CToast.WARNING);
            return;
        }

        int quantity = tabletBUS.getTotalCartQuantity();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bàn #" + currentTableId + " — " + quantity + " phần món\n"
                        + "Tạm tính lượt gọi này: " + MONEY_FORMAT.format(tabletBUS.calculateCartSubtotal()) + "\n\n"
                        + "Xác nhận gửi xuống bếp? Sau khi gửi, nếu muốn hủy/sửa món bạn cần liên hệ nhân viên.",
                "Xác nhận gọi món", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (tabletBUS.submitOrderToServer("", new HashMap<>(itemNotesMap))) {
            awaitingOrderConfirmation = true;
            btnSendKitchen.setEnabled(false);
            btnSendKitchen.setText("Đang gửi yêu cầu...");
        } else {
            CToast.show(this,
                    "Mất kết nối. Vui lòng thử lại hoặc gọi nhân viên.",
                    "Lỗi Kết Nối Mạng", CToast.ERROR);
        }
    }

    private void processServerPackets() {
        SocketPacket packet;

        while ((packet = tabletBUS.pollPacket()) != null) {
            onServerPacketCallback(packet);

            // GUI có thể đã đóng khi server kết thúc phiên.
            if (!responseTimer.isRunning()) {
                break;
            }
        }
    }

    /**
     * Cập nhật ảnh món khi việc tải ảnh hoàn tất.
     */
    private void updateLoadedImages() {
        Long foodId;

        while ((foodId = tabletBUS.pollLoadedImageFoodId()) != null) {
            JLabel label = foodImageLabels.get(foodId);

            if (label == null) {
                continue;
            }

            label.putClientProperty(
                    "foodImage",
                    tabletBUS.getCachedFoodImage(foodId)
            );

            label.repaint();
        }
    }

    private void openMemberLoginDialog() {
        JDialog dialog = new JDialog(
                this, "Đăng nhập thành viên", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setSize(410, 295);
        dialog.setResizable(false);
        dialog.setLocationRelativeTo(this);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
        form.setBackground(Color.WHITE);
        form.setBorder(new EmptyBorder(20, 25, 20, 25));

        JTextField txtEmail = new JTextField();
        JPasswordField txtPassword = new JPasswordField();

        JButton btnLogin = new JButton("Đăng nhập");
        JButton btnCancel = new JButton("Hủy");

        form.add(new JLabel("Email tài khoản:"));
        form.add(txtEmail);
        form.add(new JLabel("Mật khẩu:"));
        form.add(txtPassword);

        JPanel actions = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        actions.add(btnCancel);
        actions.add(btnLogin);
        form.add(actions);

        btnCancel.addActionListener(e -> dialog.dispose());

        btnLogin.addActionListener(e -> {
            String email = txtEmail.getText().trim();
            char[] passwordChars = txtPassword.getPassword();

            try {
                if (email.isEmpty() || passwordChars.length == 0) {
                    CToast.warning(this,
                            "Vui lòng nhập email và mật khẩu.");
                    return;
                }

                Map<String, String> credentials = new HashMap<>();
                credentials.put("email", email);
                credentials.put(
                        "password", new String(passwordChars));

                SocketPacket request = new SocketPacket(
                        SocketAction.MEMBER_LOGIN,
                        currentTableId,
                        credentials,
                        null
                );

                if (sessionSocket.sendRequest(request)) {
                    dialog.dispose();
                    CToast.info(this,
                            "Đang xác thực tài khoản thành viên...");
                } else {
                    CToast.error(this,
                            "Không gửi được yêu cầu đăng nhập.");
                }
            } finally {
                Arrays.fill(passwordChars, '\0');
                txtPassword.setText("");
            }
        });

        dialog.setContentPane(form);
        dialog.getRootPane().setDefaultButton(btnLogin);
        dialog.setVisible(true);
    }

    private void openMemberProfile() {
        if (loggedInMember == null) {
            openMemberLoginDialog();
            return;
        }

        memberProfileDialog = new JDialog(
                this, "Tài khoản thành viên", false);
        memberProfileDialog.setSize(580, 470);
        memberProfileDialog.setLocationRelativeTo(this);

        memberProfileText = "Đang tải thông tin thành viên...";
        memberHistoryText = "Đang tải lịch sử đặt món...";

        memberProfileArea = new JTextArea();
        memberProfileArea.setEditable(false);
        memberProfileArea.setFont(
                Utils.getCustomFont("Inter", Font.PLAIN, 14));
        memberProfileArea.setBorder(
                new EmptyBorder(18, 18, 18, 18));

        JButton btnLogoutMember =
                new JButton("Đăng xuất thành viên");

        btnLogoutMember.addActionListener(e -> {
            boolean sent = sessionSocket.sendRequest(
                    new SocketPacket(
                            SocketAction.MEMBER_LOGOUT,
                            currentTableId,
                            null,
                            null
                    )
            );

            if (!sent) {
                CToast.error(this,
                        "Không gửi được yêu cầu đăng xuất.");
            }
        });

        memberProfileDialog.setLayout(new BorderLayout());
        memberProfileDialog.add(
                new JScrollPane(memberProfileArea),
                BorderLayout.CENTER
        );
        memberProfileDialog.add(
                btnLogoutMember,
                BorderLayout.SOUTH
        );

        updateMemberProfileText();
        memberProfileDialog.setVisible(true);

        sessionSocket.sendRequest(new SocketPacket(
                SocketAction.MEMBER_PROFILE,
                currentTableId,
                null,
                null
        ));

        sessionSocket.sendRequest(new SocketPacket(
                SocketAction.MEMBER_HISTORY,
                currentTableId,
                null,
                null
        ));
    }

    private void updateMemberProfileText() {
        if (memberProfileArea == null) return;

        memberProfileArea.setText(
                "THÔNG TIN THÀNH VIÊN\n\n"
                        + memberProfileText
                        + "\n\n-----------------------------\n"
                        + "LỊCH SỬ ĐẶT MÓN\n\n"
                        + memberHistoryText
        );
    }

    private OrderItemDTO getSelectedTrackedItem() {
        if (trackTable == null || tabletBUS == null) return null;
        return tabletBUS.getTrackedItemById(trackTable.getSelectedOrderItemId());
    }

    public static void main(String[] args) {
        AuthGUI.main(args);
    }
}