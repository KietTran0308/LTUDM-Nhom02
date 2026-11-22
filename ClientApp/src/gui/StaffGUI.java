package gui;

import api.ImageSocketClient;
import api.SocketClient;
import bus.StaffBUS;
import components.CButton;
import components.CDialog;
import components.CScrollBar;
import components.CToast;
import dto.TableDTO;
import dto.UserDTO;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class StaffGUI extends JFrame {
    private final StaffBUS bus;
    private final UserDTO staff;
    private final SocketClient sessionSocket;
    private final Timer responseTimer;
    private final JLabel lblClock = Utils.createClockLabel();

    private final JPanel tableGrid = new JPanel(new GridLayout(0, 4, 14, 14));
    private final JPanel floorList = new JPanel();
    private final JLabel floorHeading = new JLabel("Đang tải các tầng...");
    private final JLabel selectedLabel = new JLabel("Đang tải danh sách bàn...");
    private final JButton handoffButton = new CButton("Bàn giao thiết bị cho khách", 300, 50);

    private Integer selectedFloor;
    private Long selectedTableId;
    private Long selectedSessionId;
    private String selectedTableName;
    private Long pendingTableId;
    private Long pendingSessionId;
    private boolean handoffPending;
    private boolean tablesLoaded;

    public StaffGUI(UserDTO staff, SocketClient socket) {
        this.staff = Objects.requireNonNull(staff);
        this.sessionSocket = Objects.requireNonNull(socket);
        this.bus = new StaffBUS(socket);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());
        buildUI();

        responseTimer = new Timer(120, e -> {
            StaffBUS.Event event;
            while ((event = bus.pollEvent()) != null) onStaffEvent(event);
        });
        responseTimer.start();

        SwingUtilities.invokeLater(() -> {
            if (!bus.requestListTables()) {
                selectedLabel.setText("Không gửi được yêu cầu danh sách bàn. Vui lòng đăng nhập lại.");
                CToast.error(this, "Không gửi được yêu cầu tải danh sách bàn. Kiểm tra kết nối server.");
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

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRow.setOpaque(false);
        titleRow.add(createSocketLogo("things/logo.png", 46));
        titleRow.add(heading);
        top.add(titleRow, BorderLayout.WEST);

        JButton logout = new JButton("Đăng xuất");
        logout.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        logout.setForeground(Color.WHITE);
        logout.setBackground(new Color(52, 66, 58));
        logout.setFocusPainted(false);
        logout.setPreferredSize(new Dimension(115, 42));
        logout.setBorder(new LineBorder(new Color(80, 95, 86), 1, true));
        logout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logout.addActionListener(e -> logout());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);
        actions.add(lblClock);
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
        workspace.add(buildFloorSidebar(), BorderLayout.WEST);

        JPanel tableArea = new JPanel(new BorderLayout(0, 12));
        tableArea.setBackground(Color.WHITE);
        tableArea.setBorder(new EmptyBorder(18, 18, 18, 18));

        floorHeading.setFont(Utils.getCustomFont("Inter", Font.BOLD, 20));
        floorHeading.setForeground(Utils.FOREST_BG);
        tableArea.add(floorHeading, BorderLayout.NORTH);

        tableGrid.setBackground(Color.WHITE);
        tableGrid.setBorder(new EmptyBorder(6, 4, 12, 4));
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
        handoffButton.setEnabled(false);
        handoffButton.addActionListener(e -> handoff());
        bottom.add(selectedLabel, BorderLayout.CENTER);
        bottom.add(handoffButton, BorderLayout.EAST);
        center.add(bottom, BorderLayout.SOUTH);

        add(center, BorderLayout.CENTER);
    }

    private JPanel buildFloorSidebar() {
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
        clearSelection();
        renderTables(bus.getTables());
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
            button.setBackground(picked ? Utils.SELECT_COLOR : new Color(244, 247, 243));
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(picked ? Utils.SELECT_COLOR : new Color(224, 230, 223), 1, true),
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
                if (oldSessionId != null && !Objects.equals(oldSessionId, selectedSessionId) && !handoffPending) {
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
            button.setBackground(active ? Utils.ACTIVE_COLOR : Utils.EMPTY_COLOR);
            button.setOpaque(true);
            button.setFocusPainted(false);
            button.setBorder(selected ? new LineBorder(Utils.SELECT_COLOR, 3, true)
                    : new LineBorder(new Color(220, 224, 217), 1, true));
            button.addActionListener(e -> {
                if (handoffPending) return;
                selectTable(t);
                for (Component component : tableGrid.getComponents()) {
                    if (component instanceof JButton btn) {
                        btn.setBorder(new LineBorder(new Color(220, 224, 217), 1, true));
                    }
                }
                button.setBorder(new LineBorder(Utils.SELECT_COLOR, 3, true));
            });
            tableGrid.add(button);
        }

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
    }

    private void clearSelection() {
        selectedTableId = null;
        selectedSessionId = null;
        selectedTableName = null;
    }

    private void resetPendingHandoff() {
        handoffPending = false;
        pendingTableId = null;
        pendingSessionId = null;
        updateSelectionView();
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
        if (handoffPending || !bus.isConnected() || selectedTableId == null || selectedSessionId == null) return;

        TableDTO current = findTable(bus.getTables(), selectedTableId);
        if (current == null || !Objects.equals(current.getActiveSessionId(), selectedSessionId)) {
            CToast.warning(this, "Phiên bàn đã thay đổi, vui lòng chọn lại bàn.");
            if (current != null) selectTable(current);
            else {
                clearSelection();
                updateSelectionView();
            }
            return;
        }

        final Long confirmingTableId = current.getId();
        final Long confirmingSessionId = current.getActiveSessionId();
        if (!confirmHandoff(current)) return;

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
            resetPendingHandoff();
            CToast.error(this, "Không gửi được yêu cầu bàn giao đến server.");
        }
    }

    private boolean confirmHandoff(TableDTO table) {
        final boolean[] confirmed = {false};

        CDialog dialog = new CDialog(
                this,
                "AN NHIÊN  /  BÀN GIAO THIẾT BỊ",
                "Xác nhận bàn giao cho khách",
                "Kiểm tra lại thông tin bàn trước khi tiếp tục."
        );

        JPanel body = new JPanel(new BorderLayout(0, 16));
        body.setOpaque(false);

        JLabel detailsTitle = new JLabel("THÔNG TIN BÀN ĐƯỢC CHỌN");
        detailsTitle.setFont(Utils.getCustomFont("Inter", Font.BOLD, 13));
        detailsTitle.setForeground(CDialog.DARK_TEXT);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 12));
        details.setOpaque(false);
        details.add(CDialog.createInfoRow("Bàn", table.getTableName()));
        details.add(CDialog.createInfoRow("Tầng", floorName(table.getFloorNumber())));
        details.add(CDialog.createInfoRow("Sức chứa", table.getCapacity() + " người"));
        details.add(CDialog.createInfoRow("Phiên đang mở", "#" + table.getActiveSessionId() + "  •  Đang phục vụ"));
        details.add(CDialog.createInfoRow("Nhân viên bàn giao", staff.getFullName()));

        body.add(detailsTitle, BorderLayout.NORTH);
        body.add(details, BorderLayout.CENTER);

        CButton cancel = CDialog.createSecondaryButton("Hủy bỏ", 122, 44);
        cancel.addActionListener(e -> dialog.dispose());

        CButton accept = new CButton("Xác nhận bàn giao", 212, 44);
        accept.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        dialog.setBody(body)
                .setFooterButtons(accept, cancel, accept)
                .setMinSize(580, 490)
                .showModal();

        return confirmed[0];
    }

    private void onStaffEvent(StaffBUS.Event event) {
        switch (event.type) {
            case TABLES -> renderTables(bus.getTables());
            case HANDOFF_ERROR -> {
                resetPendingHandoff();
                CToast.error(this, event.message);
            }
            case HANDOFF -> {
                if (!handoffPending || !Objects.equals(pendingTableId, event.tableId)
                        || !Objects.equals(pendingSessionId, event.sessionId)) {
                    CToast.error(this, "Phản hồi bàn giao không khớp phiên đang chọn.");
                    return;
                }
                responseTimer.stop();
                sessionSocket.bindCustomerTable(event.tableId);
                new CustomerGUI(event.tableId, event.sessionId, sessionSocket).setVisible(true);
                dispose();
            }
            case SESSION_CLOSED -> {
                if (selectedTableId != null && Objects.equals(selectedTableId, event.tableId)) {
                    CToast.info(this, "Phiên bàn vừa chọn đã đóng. Đang đồng bộ sơ đồ bàn mới.");
                }
            }
            case NOTICE -> CToast.info(this, event.message);
            case ERROR -> {
                selectedLabel.setText("Lỗi từ máy chủ: " + event.message);
                CToast.warning(this, event.message);
            }
        }
    }

    private void logout() {
        responseTimer.stop();
        bus.logout();
        new AuthGUI().setVisible(true);
        dispose();
    }

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
                return ImageSocketClient.fetchImage(imageKey);
            }

            @Override
            protected void done() {
                try {
                    BufferedImage received = get();
                    if (received == null) return;
                    label.setIcon(new ImageIcon(received.getScaledInstance(size, size, Image.SCALE_SMOOTH)));
                    label.setText("");
                    if ("things/logo.png".equals(imageKey)) setIconImage(received);
                    label.revalidate();
                    label.repaint();
                } catch (Exception ex) {
                    System.err.println("[LOGO-TCP] Lỗi " + imageKey + ": " + ex.getMessage());
                }
            }
        }.execute();
        return label;
    }
}