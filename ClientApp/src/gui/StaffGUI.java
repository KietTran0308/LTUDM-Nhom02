package gui;

import api.SocketClient;
import bus.StaffBUS;
import components.*;
import dto.*;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Set;

public class StaffGUI extends JFrame {
    private static final Color ACTIVE_COLOR = new Color(202, 227, 207);
    private static final Color EMPTY_COLOR = new Color(244, 231, 224);
    private static final Color SELECT_COLOR = new Color(35, 101, 66);

    private final StaffBUS bus;
    private final UserDTO staff;

    private final SocketClient sessionSocket;

    private final Timer responseTimer;
    private final JPanel tableGrid = new JPanel(new GridLayout(0, 4, 14, 14));
    private final JPanel floorList = new JPanel();
    private final JLabel floorHeading = new JLabel("Đang tải các tầng...");

    private Integer selectedFloor;
    private final JLabel selectedLabel = new JLabel("Đang tải danh sách bàn...");
    private final JButton handoffButton = new CButton("Bàn giao thiết bị cho khách", 300, 50);

    private Long selectedTableId, selectedSessionId;
    private String selectedTableName;
    private Long pendingTableId, pendingSessionId;
    private boolean handoffPending, tablesLoaded;

    public StaffGUI(UserDTO staff, SocketClient socket) {
        this.staff = Objects.requireNonNull(staff);
        sessionSocket = Objects.requireNonNull(socket);
        bus = new StaffBUS(socket);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        buildUI();

        // Timer chi rut packet trong bo nho tren EDT, KHONG goi SQL/socket lay lai ban.
        responseTimer = new Timer(120, e -> {
            SocketPacket p;
            while ((p = bus.pollPacket()) != null) handlePacket(p);
        });
        responseTimer.start();


        SwingUtilities.invokeLater(() -> {
            if (!bus.requestListTables()) {
                selectedLabel.setText("Không gửi được yêu cầu danh sách bàn. Vui lòng đăng nhập lại.");
                CToast.error(this, "Không gửi được yêu cầu tải danh sách bàn. Kiểm tra kết nối server.");
            } else {
                System.out.println("[StaffGUI] Đã gửi LIST_TABLES tới server");
            }
        });
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
        CButton logout = new CButton("Đăng xuất", 200, 50);
        logout.addActionListener(e -> logout());
        actions.add(logout);
        top.add(actions, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 16));
        center.setBackground(Utils.CREAM_BG);
        center.setBorder(new EmptyBorder(22, 28, 22, 28));
        JLabel subtitle = new JLabel("Nhân viên: " + staff.getFullName());
        subtitle.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
        center.add(subtitle, BorderLayout.NORTH);

        JPanel workspace = new JPanel(new BorderLayout(18, 0));
        workspace.setOpaque(false);
        workspace.add(menuFloor(), BorderLayout.WEST);

        JPanel tableArea = new JPanel(new BorderLayout(0, 12));
        tableArea.setBackground(Color.WHITE);
        tableArea.setBorder(new EmptyBorder(18, 18, 18, 18));
        JPanel tableHeader = new JPanel(new GridLayout(0, 1, 0, 5));
        tableHeader.setOpaque(false);
        floorHeading.setFont(Utils.getCustomFont("Inter", Font.BOLD, 20));
        floorHeading.setForeground(Utils.FOREST_BG);
        tableHeader.add(floorHeading);
        tableArea.add(tableHeader, BorderLayout.NORTH);

        tableGrid.setBackground(Color.WHITE);
        tableGrid.setBorder(new EmptyBorder(6, 4, 12, 4));
        // Neo luoi len dau: 1-2 ban khong bi keo cao theo toan bo vung cuon.
        JPanel tableAnchor = new JPanel(new BorderLayout());
        tableAnchor.setBackground(Color.WHITE);
        tableAnchor.add(tableGrid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(tableAnchor);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setVerticalScrollBar(new CScrollBar());
        scroll.getVerticalScrollBar().setUnitIncrement(20);
        tableArea.add(scroll, BorderLayout.CENTER);
        workspace.add(tableArea, BorderLayout.CENTER);
        center.add(workspace, BorderLayout.CENTER);

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

    private JPanel menuFloor() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 14));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(new EmptyBorder(20, 12, 16, 12));
        sidebar.setPreferredSize(new Dimension(245, 0));
        JLabel label = new JLabel("CHỌN TẦNG");
        label.setFont(Utils.getCustomFont("Inter", Font.BOLD, 17));
        label.setForeground(Utils.FOREST_BG);
        sidebar.add(label, BorderLayout.NORTH);

        floorList.setLayout(new BoxLayout(floorList, BoxLayout.Y_AXIS));
        floorList.setBackground(Color.WHITE);
        JScrollPane floorsScroll = new JScrollPane(floorList);
        floorsScroll.setBorder(BorderFactory.createEmptyBorder());
        floorsScroll.getViewport().setBackground(Color.WHITE);
        floorsScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        floorsScroll.setVerticalScrollBar(new CScrollBar());
        floorsScroll.getVerticalScrollBar().setUnitIncrement(16);
        sidebar.add(floorsScroll, BorderLayout.CENTER);

        return sidebar;
    }

    private static String floorName(int floor) {
        return "Tầng " + floor;
    }

    private void selectFloor(int floor) {
        if (handoffPending || Objects.equals(selectedFloor, floor)) return;
        selectedFloor = floor;
        // Khong duoc ban giao ban da chon khi nhan vien da chuyen sang tang khac.
        clearSelection();
        renderTables(bus.getTables()); // Render snapshot local; KHONG gui socket.
    }

    private void renderFloorSidebar(List<TableDTO> tables, Set<Integer> floors) {
        floorList.removeAll();
        if (floors.isEmpty()) {
            JLabel empty = new JLabel("Chưa có tầng nào");
            empty.setBorder(new EmptyBorder(10, 7, 10, 7));
            floorList.add(empty);
        }
        for (int floor : floors) {
            int total = 0;
            int active = 0;
            for (TableDTO t : tables) {
                if (t.getFloorNumber() == floor) {
                    total++;
                    if (t.getActiveSessionId() != null) active++;
                }
            }
            final boolean picked = Objects.equals(selectedFloor, floor);

            JButton button = new JButton("<html><div style='text-align:left;'><b>" + floorName(floor)
                    + "</b><br/>" + total + " bàn  •  " + active + " đang phục vụ</div></html>");
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
            button.setForeground(picked ? Color.WHITE : new Color(37, 65, 49));
            button.setBackground(picked ? SELECT_COLOR : new Color(244, 247, 243));
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(picked ? SELECT_COLOR : new Color(224, 230, 223), 1, true),
                    new EmptyBorder(11, 14, 11, 8)));
            button.setPreferredSize(new Dimension(209, 65));
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setEnabled(!handoffPending);
            button.addActionListener(e -> selectFloor(floor));
            floorList.add(button);
            floorList.add(Box.createVerticalStrut(9));
        }
        floorList.revalidate();
        floorList.repaint();
    }

    /** Snapshot LIST_TABLES den tu queue. Giu tang dang xem va ban da chon neu hop le. */
    private void renderTables(List<TableDTO> tables) {
        tablesLoaded = true;
        Set<Integer> floors = new TreeSet<>();
        for (TableDTO t : tables) floors.add(t.getFloorNumber());
        if (selectedFloor == null || !floors.contains(selectedFloor)) {
            selectedFloor = floors.isEmpty() ? null : floors.iterator().next();
            clearSelection();
        }

        Long oldSessionId = selectedSessionId;
        if (selectedTableId != null) {
            TableDTO selected = findTable(tables, selectedTableId);
            if (selected == null || !Objects.equals(selected.getFloorNumber(), selectedFloor)) {
                clearSelection();
            } else {
                selectedSessionId = selected.getActiveSessionId();
                selectedTableName = selected.getTableName();
                if (oldSessionId != null && !Objects.equals(oldSessionId, selectedSessionId)
                        && !handoffPending) {
                    CToast.warning(this, "Phiên bàn đã thay đổi. Vui lòng kiểm tra lại trước khi bàn giao.");
                }
            }
        }

        renderFloorSidebar(tables, floors);
        List<TableDTO> visible = new ArrayList<>();
        for (TableDTO t : tables) {
            if (Objects.equals(t.getFloorNumber(), selectedFloor)) visible.add(t);
        }

        floorHeading.setText(selectedFloor == null ? "SƠ ĐỒ BÀN" : "SƠ ĐỒ BÀN · " + floorName(selectedFloor).toUpperCase());

        tableGrid.removeAll();
        if (tables.isEmpty()) {
            JLabel empty = new JLabel("Server chưa có dữ liệu bàn trong restaurant_tables.", SwingConstants.CENTER);
            empty.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
            tableGrid.add(empty);
        } else if (visible.isEmpty()) {
            JLabel empty = new JLabel("Tầng này hiện chưa có bàn.", SwingConstants.CENTER);
            empty.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
            tableGrid.add(empty);
        }
        for (TableDTO t : visible) {
            boolean active = t.getActiveSessionId() != null;
            boolean selected = Objects.equals(selectedTableId, t.getId());
            JButton button = new JButton("<html><center><b>" + safeHtml(t.getTableName()) + "</b><br/>"
                    + "Sức chứa: " + t.getCapacity() + " người<br/>"
                    + (active ? "KHÁCH ĐANG DÙNG BỮA" : "") + "</center></html>");
            button.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
            button.setPreferredSize(new Dimension(195, 125));
            button.setBackground(active ? ACTIVE_COLOR : EMPTY_COLOR);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(selected ? new LineBorder(SELECT_COLOR, 3, true)
                    : new LineBorder(new Color(220, 224, 217), 1, true));
            button.addActionListener(e -> {
                if (handoffPending) return;
                selectTable(t);
                for (Component component : tableGrid.getComponents()) {
                    if (component instanceof JButton) {
                        ((JButton) component).setBorder(new LineBorder(new Color(220, 224, 217), 1, true));
                    }
                }
                button.setBorder(new LineBorder(SELECT_COLOR, 3, true));
            });
            tableGrid.add(button);
        }
        System.out.println("[StaffGUI] Nhận " + tables.size() + " bàn; "
                + floorHeading.getText() + ": " + visible.size() + " bàn");
        updateSelectionView();
        tableGrid.revalidate();
        tableGrid.repaint();
    }

    private static String safeHtml(String raw) {
        if (raw == null) return "Bàn";
        return raw.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static TableDTO findTable(List<TableDTO> tables, Long id) {
        for (TableDTO table : tables) {
            if (Objects.equals(table.getId(), id)) return table;
        }
        return null;
    }

    private void selectTable(TableDTO table) {
        selectedTableId = table.getId();
        selectedSessionId = table.getActiveSessionId();
        selectedTableName = table.getTableName();
        updateSelectionView();
        if (selectedSessionId == null) {
            CToast.info(this, "Bàn chưa mở phiên. Vui lòng yêu cầu thu ngân mở tại POS.");
        }
    }

    private void clearSelection() {
        selectedTableId = null;
        selectedSessionId = null;
        selectedTableName = null;
    }

    private void updateSelectionView() {
        if (selectedTableId == null) {
            selectedLabel.setText(!tablesLoaded
                    ? "Đang tải danh sách bàn..."
                    : bus.getTables().isEmpty()
                    ? "Server trả về 0 bàn — kiểm tra dữ liệu bảng restaurant_tables"
                    : "");
        } else if (selectedSessionId == null) {
            selectedLabel.setText(selectedTableName + " — chưa mở: liên hệ quầy thu ngân");
        } else {
            selectedLabel.setText(selectedTableName + " — phiên #" + selectedSessionId + " đang phục vụ");
        }
        handoffButton.setEnabled(!handoffPending && bus.isConnected() && selectedSessionId != null);
    }


    private void handoff() {
        if (handoffPending || !bus.isConnected() || selectedTableId == null || selectedSessionId == null)
            return;
        // Lay lai tu snapshot moi nhat. Khong su dung thong tin ban co the da cu.
        TableDTO current = findTable(bus.getTables(), selectedTableId);
        if (current == null || !Objects.equals(current.getActiveSessionId(), selectedSessionId)) {
            CToast.warning(this, "Phiên bàn đã thay đổi, vui lòng chọn lại bàn.");
            if (current != null) selectTable(current);
            else { clearSelection(); updateSelectionView(); }
            return;
        }

        final Long confirmingTableId = current.getId();
        final Long confirmingSessionId = current.getActiveSessionId();
        if (!confirmHandoff(current)) return;

        // Dialog modal van dispatch cac push tren EDT; kiem tra phien lan nua sau xac nhan.
        TableDTO latest = findTable(bus.getTables(), confirmingTableId);
        if (!bus.isConnected() || latest == null
                || !Objects.equals(latest.getActiveSessionId(), confirmingSessionId)
                || !Objects.equals(selectedTableId, confirmingTableId)) {
            CToast.warning(this, "Phiên bàn đã thay đổi trong lúc xác nhận, không thể bàn giao.");
            return;
        }

        pendingTableId = confirmingTableId;
        pendingSessionId = confirmingSessionId;
        handoffPending = true;
        updateSelectionView();
        if (!bus.enterCustomerMode(pendingTableId, pendingSessionId)) {
            handoffPending = false;
            pendingTableId = null;
            pendingSessionId = null;
            updateSelectionView();
            CToast.error(this, "Không gửi được yêu cầu bàn giao đến server.");
        }
    }

    /** Hop xac nhan thiet ke rieng, khong su dung JOptionPane. */
    private boolean confirmHandoff(TableDTO table) {
        JDialog dialog = new JDialog(this, "Xác nhận bàn giao thiết bị", true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(Color.WHITE);
        content.setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("XÁC NHẬN BÀN GIAO CHO KHÁCH");
        title.setFont(Utils.getCustomFont("Inter", Font.BOLD, 18));
        title.setForeground(Utils.FOREST_BG);
        content.add(title, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 10));
        details.setBackground(Color.WHITE);
        details.add(detailLine("Bàn", table.getTableName()));
        details.add(detailLine("Tầng", floorName(table.getFloorNumber())));
        details.add(detailLine("Sức chứa", table.getCapacity() + " người"));
        details.add(detailLine("Phiên đang mở", "#" + table.getActiveSessionId() + " (ACTIVE)"));
        details.add(detailLine("Nhân viên bàn giao", staff.getFullName()));
        JLabel note = new JLabel("<html><div style='width:380px'>Sau khi xác nhận, thiết bị chuyển sang chế độ khách hàng. "
                + "Quyền nhân viên trên kết nối này sẽ được thu hồi.</div></html>");
        note.setForeground(new Color(158, 75, 48));
        note.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        details.add(note);
        content.add(details, BorderLayout.CENTER);

        boolean[] confirmed = {false};
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controls.setBackground(Color.WHITE);
        JButton cancel = new JButton("Hủy");
        cancel.setPreferredSize(new Dimension(105, 40));
        cancel.addActionListener(e -> dialog.dispose());
        JButton accept = new JButton("Xác nhận bàn giao");
        accept.setPreferredSize(new Dimension(185, 40));
        accept.setBackground(SELECT_COLOR);
        accept.setForeground(Color.WHITE);
        accept.setOpaque(true);
        accept.setFocusPainted(false);
        accept.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        controls.add(cancel);
        controls.add(accept);
        content.add(controls, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(accept);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(510, 340));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return confirmed[0];
    }

    private JLabel detailLine(String field, String value) {
        JLabel label = new JLabel(field + ":  " + value);
        label.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 15));
        label.setForeground(new Color(40, 54, 44));
        return label;
    }

    private void handlePacket(SocketPacket p) {
        if (!p.isSuccess()) {
            if (p.getAction() == SocketAction.LIST_TABLES) {
                selectedLabel.setText("Không tải được danh sách bàn: " + p.getMessage());
                System.err.println("[StaffGUI] LIST_TABLES thất bại: " + p.getMessage());
            }
            if (p.getAction() == SocketAction.ENTER_CUSTOMER_MODE) {
                handoffPending = false;
                pendingTableId = null;
                pendingSessionId = null;
                updateSelectionView();
            }
            CToast.warning(this, p.getMessage());
            return;
        }
        switch (p.getAction()) {
            case LIST_TABLES:
                // SocketClient.reader -> packetQueue -> Swing Timer -> StaffBUS -> giao dien.
                renderTables(bus.getTables());
                break;
            case ENTER_CUSTOMER_MODE:
                if (!handoffPending || !(p.getData() instanceof Long)
                        || !Objects.equals(p.getData(), pendingSessionId)
                        || !Objects.equals(p.getTableId(), pendingTableId)) {
                    CToast.error(this, "Phản hồi bàn giao không khớp phiên đã yêu cầu.");
                    return;
                }
                responseTimer.stop();
                sessionSocket.bindCustomerTable(pendingTableId);
                // Socket da bi server ha quyen thanh CUSTOMER, khong logout/close.
                new CustomerGUI(pendingTableId, pendingSessionId, sessionSocket).setVisible(true);
                dispose();
                break;
            case SESSION_CLOSED:
                // POS se phat tiep LIST_TABLES push sau khi thanh toan, KHONG request lai.
                if (selectedTableId != null && Objects.equals(p.getTableId(), selectedTableId)) {
                    CToast.info(this, "Phiên bàn vừa chọn đã đóng. Đang nhận sơ đồ bàn mới.");
                }
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
