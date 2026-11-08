package gui;

import api.SocketClient;
import bus.KitchenBUS;
import components.CToast;
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
    private final UserDTO currentUser;
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextArea history = new JTextArea();
    private final JLabel status = new JLabel("Đang kết nối bếp...");
    private final Map<Long, OrderItemDTO> visibleItems = new HashMap<>();
    private final Map<Long, Long> pendingUpdates = new HashMap<>();
    private final JButton nextAction = new JButton("Chọn món để thao tác");
    private final JToggleButton waiting = new JToggleButton("Chờ nhận / đã nhận", true);
    private final JToggleButton cooking = new JToggleButton("Đang chế biến");
    private final JToggleButton ready = new JToggleButton("Chờ phục vụ");
    private final JLabel queueSummary = new JLabel("Chưa có món trong hàng đợi");
    private int stage;
    private javax.swing.Timer responseTimer;
    private javax.swing.Timer fallbackTimer;

    public KitchenGUI(UserDTO user, SocketClient authenticatedSocket) {
        if (user == null || user.getRoleName() == null ||
                !("KITCHEN".equalsIgnoreCase(user.getRoleName()) ||
                        "ADMIN".equalsIgnoreCase(user.getRoleName()))) {
            throw new IllegalArgumentException("Tài khoản không có quyền truy cập bếp");
        }
        currentUser = user;
        kitchenBUS = new KitchenBUS(authenticatedSocket);
        setTitle("Bếp Nhà | Màn hình bếp KDS");
        setSize(1330, 800);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(new Color(247, 248, 246));
        setLayout(new BorderLayout(0, 12));

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(new Color(32, 43, 37));
        header.setBorder(new EmptyBorder(17, 24, 17, 24));
        JLabel title = new JLabel("BẾP NHÀ  |  MÀN HÌNH ĐIỀU PHỐI BẾP");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("SansSerif", Font.BOLD, 22));
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        headerRight.setOpaque(false);
        JButton refresh = new JButton("Làm mới hàng đợi");
        refresh.addActionListener(e -> kitchenBUS.refreshQueue());
        JButton logout = new JButton("Đăng xuất");
        logout.addActionListener(e -> logout());
        headerRight.add(refresh);
        headerRight.add(logout);
        header.add(title, BorderLayout.WEST);
        header.add(headerRight, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        String[] columns = {"Item ID", "Bàn", "Lượt gọi", "Tên món", "SL", "Ghi chú chế biến", "Giờ gọi", "Trạng thái"};
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(48);
        table.setFont(new Font("SansSerif", Font.PLAIN, 15));
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.removeColumn(table.getColumnModel().getColumn(0));
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(4).setPreferredWidth(270);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object value,
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
        JPanel queueHeader = new JPanel(new GridLayout(3, 1, 0, 6));
        queueHeader.setOpaque(false);
        queueHeader.add(status);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filters.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        JToggleButton[] buttons = {waiting, cooking, ready};
        for (int i = 0; i < buttons.length; i++) {
            final int selectedStage = i;
            group.add(buttons[i]);
            filters.add(buttons[i]);
            buttons[i].addActionListener(e -> {
                stage = selectedStage;
                renderQueue(kitchenBUS.getQueue());
            });
        }
        queueHeader.add(filters);
        queueHeader.add(queueSummary);
        middle.add(queueHeader, BorderLayout.NORTH);
        middle.add(new JScrollPane(table), BorderLayout.CENTER);
        history.setEditable(false);
        history.setRows(5);
        history.setForeground(new Color(167, 243, 208));
        history.setBackground(new Color(20, 29, 24));
        JCheckBox showHistory = new JCheckBox("Hiện nhật ký thao tác");
        JScrollPane historyPane = new JScrollPane(history);
        historyPane.setVisible(false);
        JPanel historyPanel = new JPanel(new BorderLayout());
        historyPanel.add(showHistory, BorderLayout.NORTH);
        historyPanel.add(historyPane, BorderLayout.CENTER);
        showHistory.addActionListener(e -> {
            historyPane.setVisible(showHistory.isSelected());
            historyPanel.revalidate();
        });
        middle.add(historyPanel, BorderLayout.SOUTH);
        add(middle, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12));
        actions.setBackground(Color.WHITE);
        nextAction.setFont(new Font("SansSerif", Font.BOLD, 16));
        nextAction.setPreferredSize(new Dimension(270, 48));
        nextAction.setEnabled(false);
        nextAction.addActionListener(e -> advanceSelectedItem());
        actions.add(nextAction);
        actions.add(new JLabel("Nấu xong → nhân viên mang món → xác nhận đã phục vụ"));
        add(actions, BorderLayout.SOUTH);

        responseTimer = new javax.swing.Timer(100, e -> {
            SocketPacket packet;
            while ((packet = kitchenBUS.pollPacket()) != null) handlePacket(packet);
            if (!kitchenBUS.isConnected()) status.setText("● Mất kết nối máy chủ — tạm dừng thao tác");
            pendingUpdates.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue() > 15000);
            updateAction();
        });
        responseTimer.start();
        // Chi la phuong an du phong; cap nhat chinh do Server PUSH ngay khi co thay doi.
        fallbackTimer = new javax.swing.Timer(5000, e -> {
            if (kitchenBUS.isConnected()) kitchenBUS.refreshQueue();
        });
        fallbackTimer.start();
        kitchenBUS.refreshQueue();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { logout(); }
        });
    }

    private OrderItemDTO selectedItemAt(int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) return null;
        return visibleItems.get((Long) model.getValueAt(table.convertRowIndexToModel(viewRow), 0));
    }

    private OrderStatus nextStatus(OrderItemDTO item) {
        if (item == null || item.getStatus() == null) return null;
        return switch (item.getStatus()) {
            case PENDING -> OrderStatus.RECEIVED;
            case RECEIVED -> OrderStatus.COOKING;
            case COOKING -> OrderStatus.COMPLETED;
            default -> null;
        };
    }

    private void updateAction() {
        OrderItemDTO item = selectedItemAt(table.getSelectedRow());
        OrderStatus next = nextStatus(item);
        boolean pending = item != null && pendingUpdates.containsKey(item.getId());
        nextAction.setText(pending ? "Đang cập nhật..." : next == OrderStatus.RECEIVED ? "Nhận món"
                : next == OrderStatus.COOKING ? "Bắt đầu chế biến"
                : next == OrderStatus.COMPLETED ? "Nấu xong — chờ phục vụ"
                : item == null ? "Chọn món để thao tác" : "Đang chờ nhân viên mang món");
        nextAction.setEnabled(next != null && !pending && kitchenBUS.isConnected());
    }

    private void advanceSelectedItem() {
        OrderItemDTO item = selectedItemAt(table.getSelectedRow());
        OrderStatus next = nextStatus(item);
        if (next == null || pendingUpdates.containsKey(item.getId())) return;
        if (kitchenBUS.updateItemStatus(item.getId(), next)) {
            pendingUpdates.put(item.getId(), System.currentTimeMillis());
            updateAction();
        } else CToast.error(this, "Mất kết nối. Vui lòng thử làm mới hàng đợi.");
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

    private void handlePacket(SocketPacket p) {
        if (!p.isSuccess()) {
            pendingUpdates.clear();
            updateAction();
            CToast.error(this, p.getMessage());
            log("LỖI: " + p.getMessage());
            return;
        }
        switch (p.getAction()) {
            case GET_KITCHEN_QUEUE:
                renderQueue(kitchenBUS.getQueue());
                status.setText("● Đã kết nối Server | Nhân viên: " + currentUser.getFullName());
                break;
            case UPDATE_ITEM_STATUS:
            case SET_KITCHEN_ITEM_STATUS:
                if (p.getData() instanceof Long) {
                    Long id = (Long) p.getData();
                    pendingUpdates.remove(id);
                    OrderItemDTO item = visibleItems.get(id);
                    if (item != null && p.getExtraData() instanceof OrderStatus)
                        item.setStatus((OrderStatus) p.getExtraData());
                }
                renderQueue(kitchenBUS.getQueue());
                log("Đã cập nhật trạng thái món #" + p.getData());
                kitchenBUS.refreshQueue();
                updateAction();
                break;
            default: break;
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
        if (fallbackTimer != null) fallbackTimer.stop();
        kitchenBUS.logout();
        dispose();
        new AuthGUI().setVisible(true);
    }
}
