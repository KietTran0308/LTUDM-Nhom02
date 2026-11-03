package gui;

import api.SocketClient;
import bus.StaffBUS;
import components.CScrollBar;
import components.CToast;
import dto.*;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Objects;

/** Nhan vien tren tablet Windows/PC: xem phien ACTIVE va ban giao cho khach, KHONG POS. */
public class StaffGUI extends JFrame {
    private final StaffBUS bus;
    private final SocketClient sessionSocket;
    private final UserDTO staff;
    private final Timer responseTimer;
    private final JPanel tableGrid = new JPanel(new GridLayout(0, 3, 14, 14));
    private final JLabel selectedLabel = new JLabel("Chua chon ban");
    private final JButton handoffButton = new JButton("Ban giao thiet bi cho khach");
    private Long selectedTableId;
    private Long selectedSessionId;
    private String selectedTableName;
    private boolean handoffPending;

    public StaffGUI(UserDTO staff, SocketClient socket) {
        this.staff = Objects.requireNonNull(staff);
        sessionSocket = Objects.requireNonNull(socket);
        bus = new StaffBUS(socket);
        setTitle("An Nhien | Nhan vien phuc vu");
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        buildUI();
        responseTimer = new Timer(120, e -> {
            SocketPacket p;
            while ((p = bus.pollPacket()) != null) handlePacket(p);
        });
        responseTimer.start();
        bus.requestListTables();
    }

    private void buildUI() {
        JPanel top = new JPanel(new BorderLayout(16, 8));
        top.setBackground(Utils.FOREST_BG);
        top.setBorder(new EmptyBorder(18, 24, 18, 24));
        JLabel heading = new JLabel("AN NHIÊN  |  CHỌN BÀN PHỤC VỤ");
        heading.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        heading.setForeground(Color.WHITE);
        top.add(heading, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);
        JButton refresh = new JButton("Làm mới bàn");
        refresh.addActionListener(e -> bus.requestListTables());
        JButton logout = new JButton("Đăng xuất");
        logout.addActionListener(e -> logout());
        actions.add(refresh);
        actions.add(logout);
        top.add(actions, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setBackground(Utils.CREAM_BG);
        center.setBorder(new EmptyBorder(22, 28, 22, 28));
        JLabel subtitle = new JLabel("Nhân viên: " + staff.getFullName()
                + "   |   Bàn chưa mở phiên: yêu cầu thu ngân tại POS mở trước khi bàn giao");
        subtitle.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
        center.add(subtitle, BorderLayout.NORTH);
        tableGrid.setBackground(Color.WHITE);
        tableGrid.setBorder(new EmptyBorder(16, 16, 16, 16));
        JScrollPane scroll = new JScrollPane(tableGrid);
        scroll.setVerticalScrollBar(new CScrollBar());
        center.add(scroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(18, 0));
        bottom.setOpaque(false);
        selectedLabel.setFont(Utils.getCustomFont("Inter", Font.BOLD, 15));
        bottom.add(selectedLabel, BorderLayout.CENTER);
        handoffButton.setEnabled(false);
        handoffButton.addActionListener(e -> handoff());
        bottom.add(handoffButton, BorderLayout.EAST);
        center.add(bottom, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
    }

    private void renderTables(List<TableDTO> tables) {
        tableGrid.removeAll();
        selectedTableId = null;
        selectedSessionId = null;
        handoffButton.setEnabled(false);
        selectedLabel.setText("Chọn bàn đang có phiên ACTIVE");
        for (TableDTO t : tables) {
            boolean active = t.getActiveSessionId() != null;
            JButton button = new JButton("<html><center><b>" + t.getTableName() + "</b><br/>"
                    + "Sức chứa: " + t.getCapacity() + "<br/>"
                    + (active ? "● PHIÊN ĐANG MỞ" : "○ CHƯA MỞ PHIÊN") + "</center></html>");
            button.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
            button.setPreferredSize(new Dimension(210, 125));
            button.setBackground(active ? new Color(202, 227, 207) : new Color(244, 231, 224));
            button.setFocusPainted(false);
            button.addActionListener(e -> {
                selectedTableId = t.getId();
                selectedSessionId = t.getActiveSessionId();
                selectedTableName = t.getTableName();
                selectedLabel.setText(t.getTableName() + (active
                        ? " — phiên #" + selectedSessionId + " đang phục vụ"
                        : " — chưa mở phiên: liên hệ POS"));
                handoffButton.setEnabled(active && !handoffPending);
                if (!active) CToast.info(this, "Yêu cầu thu ngân mở phiên tại máy POS trung tâm.");
            });
            tableGrid.add(button);
        }
        tableGrid.revalidate();
        tableGrid.repaint();
    }

    private void handoff() {
        if (handoffPending || selectedTableId == null || selectedSessionId == null) return;
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Bàn giao thiết bị cho " + selectedTableName + "?\n"
                        + "Quyền STAFF sẽ được thu hồi trên kết nối này.",
                "Xác nhận bàn giao", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) return;
        handoffPending = true;
        handoffButton.setEnabled(false);
        if (!bus.enterCustomerMode(selectedTableId, selectedSessionId)) {
            handoffPending = false;
            handoffButton.setEnabled(true);
            CToast.error(this, "Không gửi được yêu cầu bàn giao tới Server.");
        }
    }

    private void handlePacket(SocketPacket p) {
        if (!p.isSuccess()) {
            if (p.getAction() == SocketAction.ENTER_CUSTOMER_MODE) {
                handoffPending = false;
                handoffButton.setEnabled(selectedSessionId != null);
            }
            CToast.warning(this, p.getMessage());
            return;
        }
        switch (p.getAction()) {
            case LIST_TABLES:
                renderTables(bus.getTables());
                break;
            case ENTER_CUSTOMER_MODE:
                if (!handoffPending || !(p.getData() instanceof Long)
                        || !Objects.equals(p.getData(), selectedSessionId)
                        || !Objects.equals(p.getTableId(), selectedTableId)) {
                    CToast.error(this, "Phản hồi bàn giao không khớp phiên đã chọn.");
                    return;
                }
                responseTimer.stop();
                // Ket noi da ha quyen tu STAFF thanh CUSTOMER. KHONG logout tai day.
                new CustomerGUI(selectedTableId, selectedSessionId, sessionSocket).setVisible(true);
                dispose();
                break;
            case SESSION_CLOSED:
            case ORDER_CREATED:
                bus.requestListTables();
                break;
            case CALL_STAFF:
            case ORDER_COMPLETED:
                CToast.info(this, p.getMessage());
                break;
            default:
                break;
        }
    }

    private void logout() {
        responseTimer.stop();
        bus.logout();
        new AuthGUI().setVisible(true);
        dispose();
    }
}
