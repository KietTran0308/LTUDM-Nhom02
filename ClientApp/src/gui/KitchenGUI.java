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
import java.util.Locale;
import java.util.Objects;

public class KitchenGUI extends JFrame {
    private final KitchenBUS kitchenBUS;
    private final UserDTO currentUser;
    private final DefaultTableModel model;
    private final JTable table;
    private final JTextArea history = new JTextArea();
    private final JLabel status = new JLabel("Đang kết nối bếp...");
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
        setTitle("An Nhiên | Màn hình bếp KDS");
        setSize(1330, 800);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        getContentPane().setBackground(new Color(247, 248, 246));
        setLayout(new BorderLayout(0, 12));

        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(new Color(32, 43, 37));
        header.setBorder(new EmptyBorder(17, 24, 17, 24));
        JLabel title = new JLabel("AN NHIÊN  |  MÀN HÌNH ĐIỀU PHỐI BẾP");
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

        String[] columns = {"Item ID", "Bàn", "Đơn ID", "Tên món", "SL", "Trạng thái"};
        model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        table.setRowHeight(36);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setBorder(new EmptyBorder(8, 22, 4, 22));
        middle.add(status, BorderLayout.NORTH);
        middle.add(new JScrollPane(table), BorderLayout.CENTER);
        history.setEditable(false);
        history.setRows(5);
        history.setForeground(new Color(167, 243, 208));
        history.setBackground(new Color(20, 29, 24));
        middle.add(new JScrollPane(history), BorderLayout.SOUTH);
        add(middle, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 12));
        actions.setBackground(Color.WHITE);
        actions.add(actionButton("1. Đã nhận", OrderStatus.RECEIVED));
        actions.add(actionButton("2. Đang chế biến", OrderStatus.COOKING));
        actions.add(actionButton("3. Hoàn thành", OrderStatus.COMPLETED));
        add(actions, BorderLayout.SOUTH);

        responseTimer = new javax.swing.Timer(100, e -> {
            SocketPacket packet;
            while ((packet = kitchenBUS.pollPacket()) != null) handlePacket(packet);
            if (!kitchenBUS.isConnected()) status.setText("● Mất kết nối máy chủ");
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

    private JButton actionButton(String text, OrderStatus nextStatus) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 15));
        b.setPreferredSize(new Dimension(220, 47));
        b.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                CToast.warning(this, "Hãy chọn một món trong bảng");
                return;
            }
            Long id = (Long) model.getValueAt(table.convertRowIndexToModel(row), 0);
            if (!kitchenBUS.updateItemStatus(id, nextStatus))
                CToast.error(this, "Không gửi được yêu cầu cập nhật trạng thái");
        });
        return b;
    }

    private void handlePacket(SocketPacket p) {
        if (!p.isSuccess()) {
            CToast.error(this, p.getMessage());
            log("LỖI: " + p.getMessage());
            return;
        }
        switch (p.getAction()) {
            case GET_KITCHEN_QUEUE:
                renderQueue(kitchenBUS.getQueue());
                status.setText("● Đã kết nối Server | Nhân viên: " + currentUser.getFullName());
                break;
            case SET_KITCHEN_ITEM_STATUS:
                log(p.getMessage());
                CToast.success(this, p.getMessage());
                break;
            default: break;
        }
    }

    private void renderQueue(List<OrderDTO> orders) {
        Long chosen = null;
        int selected = table.getSelectedRow();
        if (selected >= 0) chosen = (Long) model.getValueAt(table.convertRowIndexToModel(selected), 0);
        model.setRowCount(0);
        int selectedRow = -1;
        for (OrderDTO o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItemDTO item : o.getItems()) {
                if (item.getStatus() == OrderStatus.SERVED || item.getStatus() == OrderStatus.CANCELLED) continue;
                if (Objects.equals(item.getId(), chosen)) selectedRow = model.getRowCount();
                model.addRow(new Object[]{item.getId(), o.getTableId(), o.getId(),
                        item.getFoodsName(), item.getQuantity(), item.getStatus().name()});
            }
        }
        if (selectedRow >= 0) table.setRowSelectionInterval(selectedRow, selectedRow);
        log("Đồng bộ hàng đợi: " + model.getRowCount() + " món");
    }

    private void log(String message) {
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
