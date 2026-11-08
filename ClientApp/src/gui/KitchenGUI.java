package gui;

import api.ImageSocketClient;
import java.awt.image.BufferedImage;

import api.SocketClient;
import bus.KitchenBUS;
import components.*;
import dto.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import javax.swing.table.DefaultTableCellRenderer;
import java.util.Objects;

public class KitchenGUI extends JFrame {

    private final KitchenBUS kitchenBUS;
    private final DefaultTableModel model;
    private final CTable kitchenTable;
    private final JTable table;
    private final JTextArea history = new JTextArea();
    private final Map<Long, OrderItemDTO> visibleItems = new HashMap<>();
    private final Map<Long, Long> pendingUpdates = new HashMap<>();
    private final CButton nextAction = new CButton("Chọn món để thao tác", 270, 48,
            new Font("SansSerif", Font.BOLD, 16));

    private final CToggleButton waiting = new CToggleButton("Chờ nhận / đã nhận", 255, true);
    private final CToggleButton cooking = new CToggleButton("Đang chế biến", 220, false);
    private final CToggleButton ready = new CToggleButton("Chờ phục vụ", 220, false);
    private final JLabel queueSummary = new JLabel("Chưa có món trong hàng đợi");

    private static final int QUEUE_POLL_MS = 100;
    private static final int AUTO_REFRESH_MS = 5000;
    private static final String LOGO_IMAGE_KEY = "things/logo.png";

    private int stage;
    private javax.swing.Timer responseTimer;
    private javax.swing.Timer autoRefreshTimer;

    public KitchenGUI(UserDTO user, SocketClient authenticatedSocket) {
        if (user == null || user.getRoleName() == null ||
                !("KITCHEN".equalsIgnoreCase(user.getRoleName()) ||
                        "ADMIN".equalsIgnoreCase(user.getRoleName()))) {
            throw new IllegalArgumentException("Tài khoản không có quyền truy cập bếp");
        }
        kitchenBUS = new KitchenBUS(authenticatedSocket);

        setTitle("NHÀ HÀNG AN NHIÊN | Màn hình bếp");
        setUndecorated(true);
        setSize(1330, 800);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(new Color(247, 248, 246));
        setLayout(new BorderLayout(0, 12));

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(new Color(32, 43, 37));
        header.setBorder(new EmptyBorder(17, 24, 17, 24));
        JLabel title = new JLabel("NHÀ HÀNG AN NHIÊN  |  MÀN HÌNH BẾP");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        headerRight.setOpaque(false);
        CButton logout = new CButton("Đăng xuất", 120, 42);
        logout.addActionListener(e -> logout());
        headerRight.add(logout);
        // Tai logo tu ServerApp qua TCP/IMG/1 tren luong nen, khong doc getResource cua ClientApp.
        JPanel brand = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        brand.setOpaque(false);
        brand.add(createSocketLogo(LOGO_IMAGE_KEY, 46));
        brand.add(title);
        header.add(brand, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        String[] columns = {"Item ID", "Bàn", "Lượt gọi", "Tên món", "SL", "Ghi chú chế biến", "Giờ gọi", "Trạng thái"};
        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        // CTable la component dung chung: giu nguyen constructor CTable() cho tat ca GUI.
        kitchenTable = new CTable();
        table = kitchenTable.getTable();
        table.setModel(model); // Chi cau hinh model rieng cho KitchenGUI.
        table.setRowHeight(48);
        table.setFont(new Font("SansSerif", Font.PLAIN, 15));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(4).setPreferredWidth(270);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean selected, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, col);
                OrderItemDTO item = selectedItemAt(row);
                setToolTipText(value == null ? null : value.toString());
                setForeground(selected ? t.getSelectionForeground() : new Color(32, 43, 37));
                if (!selected) setBackground(item != null && item.getStatus() == OrderStatus.COMPLETED
                        ? new Color(220, 241, 225) : item != null && item.getStatus() == OrderStatus.COOKING
                        ? new Color(255, 239, 211) : Color.WHITE);
                if (t.convertColumnIndexToModel(col) == 5 && value != null && !value.toString().isBlank())
                    setForeground(selected ? t.getSelectionForeground() : new Color(170, 55, 30));
                return this;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateAction());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setBorder(new EmptyBorder(8, 22, 4, 22));
        // CToggleButton hiển thị tab, vẫn dùng ButtonGroup; không cần JTabbedPane / CardLayout.
        JPanel queueHeader = new JPanel(new BorderLayout(0, 10));
        queueHeader.setOpaque(false);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setBackground(Color.WHITE);
        filters.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(227, 232, 228)));

        ButtonGroup group = new ButtonGroup();
        CToggleButton[] tabs = {waiting, cooking, ready};
        for (int i = 0; i < tabs.length; i++) {
            final int selectedStage = i;
            CToggleButton tab = tabs[i];
            group.add(tab);
            filters.add(tab);
            tab.addActionListener(e -> {
                stage = selectedStage;
                renderQueue(kitchenBUS.getQueue());
            });
        }
        queueSummary.setFont(new Font("SansSerif", Font.PLAIN, 13));
        queueSummary.setBorder(new EmptyBorder(1, 5, 2, 0));
        queueSummary.setBackground(new Color(255, 255, 255));
        queueHeader.add(filters, BorderLayout.CENTER);
        queueHeader.add(queueSummary, BorderLayout.SOUTH);
        middle.add(queueHeader, BorderLayout.NORTH);
        // CTable da co JScrollPane va thanh cuon rieng, khong boc ScrollPane lan nua.
        middle.add(kitchenTable, BorderLayout.CENTER);
        history.setEditable(false);
        history.setRows(5);
        history.setForeground(new Color(167, 243, 208));
        history.setBackground(new Color(20, 29, 24));

        JScrollPane historyPane = new JScrollPane(history);
        historyPane.setVisible(false);
        JPanel historyPanel = new JPanel(new BorderLayout());
        middle.add(historyPanel, BorderLayout.SOUTH);
        add(middle, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12));
        actions.setBackground(Color.WHITE);

        nextAction.setEnabled(false);
        nextAction.addActionListener(e -> advanceSelectedItem());
        actions.add(nextAction);
        actions.add(new JLabel("Nấu xong → nhân viên mang món → xác nhận đã phục vụ"));
        add(actions, BorderLayout.SOUTH);

        responseTimer = new javax.swing.Timer(QUEUE_POLL_MS, e -> {
            KitchenBUS.Event event;
            while ((event = kitchenBUS.pollEvent()) != null) onKitchenEvent(event);
            pendingUpdates.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 15000);
            updateAction();
        });
        responseTimer.start();
        // Server PUSH cho thay doi ngay; Timer dinh ky dong bo lai hang doi neu bo lo PUSH.
        autoRefreshTimer = new javax.swing.Timer(AUTO_REFRESH_MS, e -> {
            if (kitchenBUS.isConnected()) kitchenBUS.refreshQueue();
        });
        autoRefreshTimer.start();
        kitchenBUS.refreshQueue(); // Lay du lieu lan dau khi mo man hinh.
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                logout();
            }
        });
    }

    private OrderItemDTO selectedItemAt(int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) return null;
        return visibleItems.get((Long) model.getValueAt(table.convertRowIndexToModel(viewRow), 0));
    }

    private void updateAction() {
        OrderItemDTO item = selectedItemAt(table.getSelectedRow());
        OrderStatus next = kitchenBUS.nextStatus(item);
        boolean pending = item != null && pendingUpdates.containsKey(item.getId());
        nextAction.setText(pending ? "Đang cập nhật..." : next == OrderStatus.RECEIVED ? "Nhận món"
                : next == OrderStatus.COOKING ? "Bắt đầu chế biến"
                : next == OrderStatus.COMPLETED ? "Nấu xong — chờ phục vụ"
                : item == null ? "Chọn món để thao tác" : "Đang chờ nhân viên mang món");
        nextAction.setEnabled(next != null && !pending && kitchenBUS.isConnected());
    }

    private void advanceSelectedItem() {
        OrderItemDTO item = selectedItemAt(table.getSelectedRow());
        OrderStatus next = kitchenBUS.nextStatus(item);
        if (next == null || pendingUpdates.containsKey(item.getId())) return;
        if (kitchenBUS.updateItemStatus(item.getId(), next)) {
            pendingUpdates.put(item.getId(), System.currentTimeMillis());
            updateAction();
        } else CToast.error(this, "Mất kết nối Server. Vui lòng kiểm tra mạng và đăng nhập lại.");
    }

    private String statusText(OrderStatus value) {
        if (value == null) return "Chưa rõ trạng thái";
        return switch (value) {
            case PENDING -> "Chờ bếp nhận";
            case RECEIVED -> "Bếp đã nhận";
            case COOKING -> "Đang chế biến";
            case COMPLETED -> "Nấu xong — chờ phục vụ";
            case SERVED -> "Đã phục vụ";
            case CANCELLED -> "Đã hủy";
        };
    }

    private void onKitchenEvent(KitchenBUS.Event event) {
        switch (event.type) {
            case QUEUE_UPDATED:
                renderQueue(kitchenBUS.getQueue());
                break;
            case ITEM_UPDATED:
                if (event.itemId != null) pendingUpdates.remove(event.itemId);
                log("Đã cập nhật trạng thái món #" + event.itemId);
                updateAction();
                break;
            case ERROR:
                pendingUpdates.clear();
                updateAction();
                CToast.error(this, event.message);
                log("LỖI: " + event.message);
                break;
        }
    }

    private void renderQueue(List<OrderDTO> orders) {
        Long chosen = null;
        int selected = table.getSelectedRow();
        if (selected >= 0) chosen = (Long) model.getValueAt(table.convertRowIndexToModel(selected), 0);
        model.setRowCount(0);
        visibleItems.clear();
        int selectedRow = -1;
        int[] counts = new int[3];
        for (OrderDTO o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItemDTO item : o.getItems()) {
                OrderStatus value = item.getStatus();
                if (value == null || value == OrderStatus.SERVED || value == OrderStatus.CANCELLED) continue;
                int itemStage = value == OrderStatus.COOKING ? 1 : value == OrderStatus.COMPLETED ? 2 : 0;
                counts[itemStage]++;
                if (itemStage != stage) continue;
                visibleItems.put(item.getId(), item);
                if (Objects.equals(item.getId(), chosen)) selectedRow = model.getRowCount();
                String tableName = o.getTableName() == null || o.getTableName().isBlank()
                        ? "Bàn #" + o.getTableId() : o.getTableName();
                model.addRow(new Object[]{item.getId(), tableName,
                        o.getOrderCode() == null ? "#" + o.getId() : o.getOrderCode(),
                        item.getFoodsName(), item.getQuantity(), item.getNote(),
                        o.getCreatedAt() == null ? "—" : o.getCreatedAt().format(DateTimeFormatter.ofPattern("dd/MM HH:mm")),
                        statusText(value)});
            }
        }
        waiting.setText("Chờ nhận / đã nhận (" + counts[0] + ")");
        cooking.setText("Đang chế biến (" + counts[1] + ")");
        ready.setText("Chờ phục vụ (" + counts[2] + ")");
        queueSummary.setText(model.getRowCount() == 0 ? "Không có món trong nhóm này."
                : model.getRowCount() + " dòng món — ưu tiên lượt gọi trước; đọc kỹ ghi chú chế biến.");
        if (selectedRow >= 0) table.setRowSelectionInterval(selectedRow, selectedRow);
        updateAction();
    }

    private void log(String message) {
        if (history.getLineCount() > 200) history.setText("");
        history.append(message + "\n");
        history.setCaretPosition(history.getDocument().getLength());
    }

    private void logout() {
        if (responseTimer != null) responseTimer.stop();
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
        kitchenBUS.logout();
        dispose();
        new AuthGUI().setVisible(true);
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
