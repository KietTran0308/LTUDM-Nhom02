package gui;

import api.SocketServer;
import bus.ServerOrderBUS;
import bus.KdsOrderBUS;
import dto.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;

public class KitchenGUI extends JFrame {

    // Bảng màu chuẩn "Bếp Nhà Restaurant" từ AuthFrame
    private static final Color FOREST_BG = new Color(32, 43, 37);
    private static final Color FOREST_DARK_LOG = new Color(20, 29, 24);
    private static final Color FOREST_CIRCLE = new Color(26, 36, 30);
    private static final Color TERRACOTTA = new Color(206, 93, 52);
    private static final Color GOLD_ACCENT = new Color(201, 142, 84);
    private static final Color CREAM_BG = new Color(247, 248, 246);
    private static final Color TEXT_DARK = new Color(23, 31, 26);

    private SocketServer socketServer;
    private final ServerOrderBUS serverOrderBUS = new ServerOrderBUS();
    private final KdsOrderBUS kdsOrderBUS = new KdsOrderBUS();
    private boolean pollingKitchenQueue = false;

    private DefaultTableModel kitchenTableModel;
    private JTable kitchenTable;
    private JTextArea txtServerLog;

    public KitchenGUI() {
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setTitle("Bếp Nhà Restaurant - Hệ Thống Điều Phối Bếp & TCP Socket Server (Port 8888)");
        setSize(1280, 780);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(CREAM_BG);

        initBrandTopBar();
        initCenterWorkspace();
        initBottomStatusControls();

        // Khởi chạy TCP Socket Server lắng nghe các Tablet bàn khách
        socketServer = new SocketServer(this::onSocketEventFromClient);
        socketServer.startServer();
        appendLog("✅ [BẾP NHÀ TCP SERVER] Đã mở cổng 8888 thành công. Đang chờ các Tablet bàn khách kết nối...");
        // Đơn tạo/cập nhật qua KDS BUS sẽ tự xuất hiện, không cần Spring API.
        javax.swing.Timer refreshTimer = new javax.swing.Timer(5000, e -> refreshKitchenQueue());
        refreshTimer.start();
        refreshKitchenQueue();
    }

    // 1. HEADER ĐỒNG BỘ AUTHFRAME
    private void initBrandTopBar() {
        JPanel topPanel = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(FOREST_CIRCLE);
                g2.fillOval(getWidth() / 2 - 180, -200, 420, 420);
            }
        };
        topPanel.setBackground(FOREST_BG);
        topPanel.setPreferredSize(new Dimension(getWidth(), 82));
        topPanel.setBorder(new EmptyBorder(14, 28, 14, 28));

        JPanel leftBrand = new JPanel();
        leftBrand.setLayout(new BoxLayout(leftBrand, BoxLayout.X_AXIS));
        leftBrand.setOpaque(false);

        JLabel lblIcon = new JLabel(new AdminGUI.BrandLogoIcon());
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 14));

        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel lblTitle = new JLabel("Bếp Nhà  |  MÀN HÌNH ĐIỀU PHỐI BẾP (KDS)");
        lblTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 21));
        lblTitle.setForeground(Color.WHITE);

        JLabel lblSub = new JLabel("● TCP Socket Server (Port 8888) — Kết nối trực tiếp tới Tablet từng bàn");
        lblSub.setFont(getCustomFont("Inter", Font.PLAIN, 13));
        lblSub.setForeground(new Color(110, 231, 183));

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(3));
        titleBox.add(lblSub);

        leftBrand.add(lblIcon);
        leftBrand.add(titleBox);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 6));
        rightActions.setOpaque(false);

        JButton btnOpenAdmin = createStyledActionButton("🛡️ Mở Trang Quản Trị (Admin)", new Color(48, 63, 55), 235, 42);
        btnOpenAdmin.addActionListener(e -> new AdminGUI().setVisible(true));

        JButton btnReloadDB = createStyledActionButton("🔄 Tải Đơn Bàn #1", TERRACOTTA, 175, 42);
        btnReloadDB.addActionListener(e -> loadItemsOfTableFromDB(1L));

        rightActions.add(btnOpenAdmin);
        rightActions.add(btnReloadDB);

        topPanel.add(leftBrand, BorderLayout.WEST);
        topPanel.add(rightActions, BorderLayout.EAST);
        add(topPanel, BorderLayout.NORTH);
    }

    // 2. KHU VỰC BẢNG MÓN CHỜ CHẾ BIẾN & NHẬT KÝ SOCKET
    private void initCenterWorkspace() {
        JPanel centerWrapper = new JPanel(new BorderLayout(0, 16));
        centerWrapper.setBackground(CREAM_BG);
        centerWrapper.setBorder(new EmptyBorder(20, 24, 15, 24));

        // Card chứa bảng điều phối món
        JPanel tableCard = new JPanel(new BorderLayout(0, 10));
        tableCard.setBackground(Color.WHITE);
        tableCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(228, 232, 228), 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JLabel lblSection = new JLabel("Danh Sách Món Ăn Cần Chế Biến Tại Bếp");
        lblSection.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 18));
        lblSection.setForeground(TEXT_DARK);

        String[] cols = {"Mã Chi Tiết (Item ID)", "Bàn Số", "Mã Đơn (Order ID)", "Tên Món Ăn", "Số Lượng", "Trạng Thái Hiện Tại"};
        kitchenTableModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        kitchenTable = new JTable(kitchenTableModel);
        kitchenTable.setRowHeight(38);
        kitchenTable.setFont(getCustomFont("Inter", Font.PLAIN, 14));
        kitchenTable.setSelectionBackground(new Color(253, 237, 232));
        kitchenTable.setSelectionForeground(TEXT_DARK);
        kitchenTable.setShowVerticalLines(false);
        kitchenTable.setGridColor(new Color(238, 240, 238));

        JTableHeader th = kitchenTable.getTableHeader();
        th.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        th.setBackground(FOREST_BG);
        th.setForeground(Color.WHITE);
        th.setPreferredSize(new Dimension(th.getWidth(), 42));

        // Tô màu cột Trạng Thái cho trực quan
        kitchenTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                String st = value != null ? value.toString() : "";
                setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
                if (st.contains("COOKING")) {
                    c.setForeground(TERRACOTTA);
                } else if (st.contains("SERVED")) {
                    c.setForeground(new Color(38, 120, 70));
                } else if (st.contains("COMPLETED")) {
                    c.setForeground(GOLD_ACCENT);
                } else {
                    c.setForeground(TEXT_DARK);
                }
                return c;
            }
        });

        tableCard.add(lblSection, BorderLayout.NORTH);
        tableCard.add(new JScrollPane(kitchenTable), BorderLayout.CENTER);

        // Khung Nhật ký Socket Server dưới đáy
        JPanel logCard = new JPanel(new BorderLayout(0, 6));
        logCard.setBackground(FOREST_DARK_LOG);
        logCard.setPreferredSize(new Dimension(getWidth(), 185));
        logCard.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel lblLogTitle = new JLabel("📡 NHẬT KÝ TRUYỀN THÔNG TCP SOCKET THỜI GIAN THỰC (SERVER LOG)");
        lblLogTitle.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 12));
        lblLogTitle.setForeground(GOLD_ACCENT);

        txtServerLog = new JTextArea();
        txtServerLog.setEditable(false);
        txtServerLog.setBackground(FOREST_DARK_LOG);
        txtServerLog.setForeground(new Color(167, 243, 208));
        txtServerLog.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtServerLog.setBorder(null);

        JScrollPane logScroll = new JScrollPane(txtServerLog);
        logScroll.setBorder(null);

        logCard.add(lblLogTitle, BorderLayout.NORTH);
        logCard.add(logScroll, BorderLayout.CENTER);

        centerWrapper.add(tableCard, BorderLayout.CENTER);
        centerWrapper.add(logCard, BorderLayout.SOUTH);
        add(centerWrapper, BorderLayout.CENTER);
    }

    // 3. THANH NÚT CẬP NHẬT TRẠNG THÁI MÓN ĂN DƯỚI ĐÁY
    private void initBottomStatusControls() {
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 14));
        bottomPanel.setBackground(Color.WHITE);
        bottomPanel.setBorder(new LineBorder(new Color(228, 232, 228), 1));

        JButton btnReceived = createStyledActionButton("📥 1. Bếp Đã Nhận (RECEIVED)", FOREST_BG, 260, 48);
        btnReceived.addActionListener(e -> updateSelectedDishStatus(OrderStatus.RECEIVED));

        JButton btnCooking = createStyledActionButton("🔥 2. Đang Chế Biến (COOKING)", TERRACOTTA, 260, 48);
        btnCooking.addActionListener(e -> updateSelectedDishStatus(OrderStatus.COOKING));

        JButton btnCompleted = createStyledActionButton("🛎️ 3. Nấu Xong - Chờ Bưng (COMPLETED)", GOLD_ACCENT, 290, 48);
        btnCompleted.addActionListener(e -> updateSelectedDishStatus(OrderStatus.COMPLETED));

        JButton btnServed = createStyledActionButton("✅ 4. Đã Lên Món Tại Bàn (SERVED)", new Color(38, 105, 65), 270, 48);
        btnServed.addActionListener(e -> updateSelectedDishStatus(OrderStatus.SERVED));

        bottomPanel.add(btnReceived);
        bottomPanel.add(btnCooking);
        bottomPanel.add(btnCompleted);
        bottomPanel.add(btnServed);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JButton createStyledActionButton(String text, Color bg, int w, int h) {
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
        btn.setForeground(Color.WHITE);
        btn.setFont(getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        btn.setPreferredSize(new Dimension(w, h));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // Xử lý khi Đầu bếp bấm nút đổi trạng thái món ăn
    private void updateSelectedDishStatus(OrderStatus newStatus) {
        int row = kitchenTable.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn 1 món ăn trong bảng trên để cập nhật trạng thái!");
            return;
        }

        Long orderItemId = (Long) kitchenTableModel.getValueAt(row, 0);
        Long tableId = (Long) kitchenTableModel.getValueAt(row, 1);
        String foodName = (String) kitchenTableModel.getValueAt(row, 3);

        boolean updatedDB = serverOrderBUS.changeDishStatusByKitchen(orderItemId, newStatus);
        if (updatedDB) {
            kitchenTableModel.setValueAt(newStatus.name(), row, 5);
            if (tableId != null) SocketServer.pushStatusToTable(tableId, orderItemId, newStatus);
            appendLog("📤 [PUSH -> Bàn #" + tableId + "] Món '" + foodName + "' (Item #" + orderItemId + ") -> " + newStatus.name());
        } else {
            JOptionPane.showMessageDialog(this, "Không thể cập nhật trạng thái xuống Database!", "Lỗi DB", JOptionPane.ERROR_MESSAGE);
        }
    }

    // Hứng sự kiện khi Tablet khách hàng gửi đơn mới hoặc bấm nút gọi nhân viên
    private void onSocketEventFromClient(SocketPacket packet) {
        SwingUtilities.invokeLater(() -> {
            Long tableId = packet.getTableId();
            if (packet.getAction() == SocketAction.PLACE_ORDER) {
                OrderDTO newOrder = (OrderDTO) packet.getData();
                appendLog("🔔 [ĐƠN MỚI - BÀN #" + tableId + "] Mã đơn: " + newOrder.getOrderCode() +
                        " | Tổng tiền: " + newOrder.getTotalAmount() + " VNĐ | Ghi chú: " + newOrder.getNote());

                loadItemsOfTableFromDB(tableId);
            } else if (packet.getAction() == SocketAction.CALL_STAFF) {
                String msg = (String) packet.getData();
                appendLog("🚨 [BÀN #" + tableId + " GỌI HỖ TRỢ]: " + msg);
                JOptionPane.showMessageDialog(this, "Bàn số " + tableId + " vừa gọi hỗ trợ:\n\"" + msg + "\"",
                        "Yêu cầu hỗ trợ tại bàn - Bếp Nhà", JOptionPane.INFORMATION_MESSAGE);
            }
        });
    }

    private void loadItemsOfTableFromDB(Long tableId) {
        List<OrderItemDTO> items = serverOrderBUS.getTableTrackingItems(tableId);
        for (int i = kitchenTableModel.getRowCount() - 1; i >= 0; i--) {
            if (kitchenTableModel.getValueAt(i, 1).equals(tableId)) {
                kitchenTableModel.removeRow(i);
            }
        }
        for (OrderItemDTO item : items) {
            kitchenTableModel.addRow(new Object[]{
                    item.getId(),
                    tableId,
                    item.getOrderId(),
                    item.getFoodsName(),
                    item.getQuantity(),
                    item.getStatus().name()
            });
        }
    }

    private void refreshKitchenQueue() {
        if(pollingKitchenQueue)return;
        pollingKitchenQueue=true;/**
        new SwingWorker<List<KitchenTaskDTO>,Void>() {
            @Override protected List<KitchenTaskDTO> doInBackground() throws Exception {return kdsOrderBUS.getKitchenQueue();}
            @Override protected void done() {
                try {
                    List<KitchenTaskDTO> queue=get();
                    Long selectedItem=null;
                    int selected=kitchenTable.getSelectedRow();
                    if(selected>=0) selectedItem=(Long)kitchenTableModel.getValueAt(kitchenTable.convertRowIndexToModel(selected),0);
                    kitchenTableModel.setRowCount(0);
                    int selectRow=-1;
                    for(KitchenTaskDTO task:queue) {
                        if(selectedItem!=null && selectedItem.longValue()==task.getItemId()) selectRow=kitchenTableModel.getRowCount();
                        kitchenTableModel.addRow(new Object[]{task.getItemId(),task.getTableId(),task.getOrderId(),task.getFoodName(),task.getQuantity(),task.getStatus().name()});
                    }
                    if(selectRow>=0) kitchenTable.setRowSelectionInterval(selectRow,selectRow);
                }catch(Exception ex){/* Tránh hiện popup lặp lại nếu DB tạm mất kết nối }
                finally{pollingKitchenQueue=false;}
            }
        }.execute();*/
    }

    private void appendLog(String log) {
        txtServerLog.append(log + "\n");
        txtServerLog.setCaretPosition(txtServerLog.getDocument().getLength());
    }

    private Font getCustomFont(String fontName, int style, int size) {
        return new Font(fontName, style, size);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new KitchenGUI().setVisible(true));
    }
}