package gui;

import api.ImageSocketClient;
import java.awt.image.BufferedImage;

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
import java.awt.geom.RoundRectangle2D;
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
    private final JLabel lblClock = new JLabel("", SwingConstants.RIGHT);
    private static final java.time.format.DateTimeFormatter CLOCK_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss  |  dd/MM/yyyy");
    private Timer clockTimer;
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

        responseTimer = new Timer(120, e -> {
            StaffBUS.Event event;
            while ((event = bus.pollEvent()) != null) onStaffEvent(event);
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
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRow.setOpaque(false);
        titleRow.add(createSocketLogo("things/logo.png", 46));
        titleRow.add(heading);
        top.add(titleRow, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);
        // Đồng hồ: giờ:phút:giây | ngày/tháng/năm, đứng trước nút Đăng xuất.
        lblClock.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblClock.setForeground(Color.WHITE);
        lblClock.setPreferredSize(new Dimension(220, 42));
        lblClock.setBorder(new EmptyBorder(0, 0, 0, 6));
        updateClock();
        clockTimer = new Timer(1000, e -> updateClock());
        clockTimer.start();

        // Nút Đăng xuất vẽ lại giống PosGUI.
        JButton logout = new JButton("Đăng xuất");
        logout.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        logout.setForeground(Color.WHITE);
        logout.setBackground(new Color(52, 66, 58));
        logout.setFocusPainted(false);
        logout.setPreferredSize(new Dimension(115, 42));
        logout.setBorder(new LineBorder(new Color(80, 95, 86), 1, true));
        logout.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logout.addActionListener(e -> logout());
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

    /** Hộp xác nhận riêng: phủ xám giao diện StaffGUI và hiển thị dialog ở giữa. */
    private boolean confirmHandoff(TableDTO table) {
        final boolean[] confirmed = {false};

        // GlassPane chỉ phủ lên StaffGUI, không phủ lên cửa sổ xác nhận.
        // Giữ lại glass pane cũ để khôi phục đúng trạng thái khi đóng dialog.
        Component oldGlassPane = getGlassPane();
        boolean oldGlassPaneVisible = oldGlassPane.isVisible();
        JPanel dimOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(new Color(45, 45, 45, 155)); // Xám trong suốt
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        dimOverlay.setOpaque(false);

        JDialog dialog = new JDialog(this, "Xác nhận bàn giao thiết bị", true);
        dialog.setUndecorated(true);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        Color darkText = new Color(39, 54, 44);
        Color lineColor = new Color(226, 231, 225);

        // Panel chính được bo góc 28px, bao gồm cả header và footer.
        final int cornerRadius = 28;
        JPanel content = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                        cornerRadius, cornerRadius));
                g2.dispose();
            }

            @Override
            protected void paintChildren(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Cắt toàn bộ nội dung con theo khung bo góc, tránh header bị vuông ở hai góc trên.
                g2.setClip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(),
                        cornerRadius, cornerRadius));
                super.paintChildren(g2);
                g2.setClip(null);
                g2.setColor(lineColor);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5,
                        getWidth() - 1.0, getHeight() - 1.0, cornerRadius, cornerRadius));
                g2.dispose();
            }
        };
        content.setOpaque(false);
        content.setBorder(BorderFactory.createEmptyBorder());

        // Phần đầu: màu xanh theo nhận diện nhà hàng.
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setBackground(Utils.FOREST_BG);
        header.setBorder(new EmptyBorder(23, 28, 23, 24));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel brand = new JLabel("AN NHIÊN  /  BÀN GIAO THIẾT BỊ");
        brand.setFont(Utils.getCustomFont("Inter", Font.BOLD, 11));
        brand.setForeground(new Color(201, 222, 207));

        JLabel title = new JLabel("Xác nhận bàn giao cho khách");
        title.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 21));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Kiểm tra lại thông tin bàn trước khi tiếp tục.");
        subtitle.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        subtitle.setForeground(new Color(224, 235, 226));

        titleBox.add(brand);
        titleBox.add(Box.createVerticalStrut(10));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(6));
        titleBox.add(subtitle);
        header.add(titleBox, BorderLayout.CENTER);
        content.add(header, BorderLayout.NORTH);

        // Thông tin bàn đang được bàn giao.
        JPanel body = new JPanel(new BorderLayout(0, 17));
        body.setBackground(Color.WHITE);
        body.setBorder(new EmptyBorder(23, 28, 18, 28));

        JLabel detailsTitle = new JLabel("THÔNG TIN BÀN ĐƯỢC CHỌN");
        detailsTitle.setFont(Utils.getCustomFont("Inter", Font.BOLD, 13));
        detailsTitle.setForeground(darkText);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 12));
        details.setOpaque(false);
        details.add(handoffInfoRow("Bàn", table.getTableName()));
        details.add(handoffInfoRow("Tầng", floorName(table.getFloorNumber())));
        details.add(handoffInfoRow("Sức chứa", table.getCapacity() + " người"));
        details.add(handoffInfoRow("Phiên đang mở", "#" + table.getActiveSessionId() + "  •  Đang phục vụ"));
        details.add(handoffInfoRow("Nhân viên bàn giao", staff.getFullName()));

        JPanel infoBlock = new JPanel(new BorderLayout(0, 16));
        infoBlock.setOpaque(false);
        infoBlock.add(detailsTitle, BorderLayout.NORTH);
        infoBlock.add(details, BorderLayout.CENTER);
        body.add(infoBlock, BorderLayout.CENTER);
        content.add(body, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 16));
        footer.setBackground(new Color(249, 250, 249));
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, lineColor),
                new EmptyBorder(0, 20, 0, 20)));

        CButton cancel = new CButton("Hủy bỏ", 122, 44);
        cancel.setForeground(darkText);
        cancel.setBackground(Color.WHITE);
        cancel.setFocusPainted(false);
        cancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancel.setBorder(new LineBorder(lineColor, 1, true));
        cancel.addActionListener(e -> dialog.dispose());

        CButton accept = new CButton("Xác nhận bàn giao", 212, 44);
        accept.addActionListener(e -> {
            confirmed[0] = true;
            dialog.dispose();
        });

        footer.add(cancel);
        footer.add(accept);
        content.add(footer, BorderLayout.SOUTH);

        // Cửa sổ trong suốt tại bốn góc để phần bo góc không có nền vuông.
        dialog.getRootPane().setOpaque(false);
        dialog.getLayeredPane().setOpaque(false);
        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(accept);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();
        dialog.setSize(Math.max(580, dialog.getWidth()), Math.max(490, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);

        // Một số nền tảng không hỗ trợ cửa sổ trong suốt; khi đó vẫn giữ panel bo góc.
        try {
            dialog.setBackground(new Color(0, 0, 0, 0));
            dialog.setShape(new RoundRectangle2D.Double(0, 0,
                    dialog.getWidth(), dialog.getHeight(), cornerRadius, cornerRadius));
        } catch (UnsupportedOperationException ex) {
            System.err.println("[StaffGUI] He dieu hanh khong ho tro dialog bo goc: " + ex.getMessage());
        }

        try {
            setGlassPane(dimOverlay);
            dimOverlay.setVisible(true);
            dimOverlay.repaint();
            dialog.setVisible(true); // Modal: chờ người dùng xác nhận hoặc hủy.
        } finally {
            dialog.dispose();
            dimOverlay.setVisible(false);
            setGlassPane(oldGlassPane);
            oldGlassPane.setVisible(oldGlassPaneVisible);
        }
        return confirmed[0];
    }

    private JPanel handoffInfoRow(String field, String value) {
        JPanel row = new JPanel(new BorderLayout(16, 0));
        row.setOpaque(false);
        JLabel name = new JLabel(field);
        name.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
        name.setForeground(new Color(107, 117, 110));

        JLabel text = new JLabel(value == null ? "—" : ": " + value);
        text.setFont(Utils.getCustomFont("Inter", Font.BOLD, 18));
        text.setForeground(new Color(39, 54, 44));

        row.add(name, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        return row;
    }

    private void onStaffEvent(StaffBUS.Event event) {
        switch (event.type) {
            case TABLES:
                renderTables(bus.getTables());
                break;
            case HANDOFF_ERROR:
                handoffPending = false;
                pendingTableId = null;
                pendingSessionId = null;
                updateSelectionView();
                CToast.error(this, event.message);
                break;
            case HANDOFF:
                if (!handoffPending || !Objects.equals(pendingTableId, event.tableId)
                        || !Objects.equals(pendingSessionId, event.sessionId)) {
                    CToast.error(this, "Phản hồi bàn giao không khớp phiên đang chọn.");
                    return;
                }
                responseTimer.stop();
                sessionSocket.bindCustomerTable(event.tableId);
                // Server da ha quyen thanh CUSTOMER; giu socket khi chuyen GUI.
                new CustomerGUI(event.tableId, event.sessionId, sessionSocket).setVisible(true);
                dispose();
                break;
            case SESSION_CLOSED:
                if (selectedTableId != null && Objects.equals(selectedTableId, event.tableId))
                    CToast.info(this, "Phiên bàn vừa chọn đã đóng. Đang đồng bộ sơ đồ bàn mới.");
                break;
            case NOTICE:
                CToast.info(this, event.message);
                break;
            case ERROR:
                selectedLabel.setText("Lỗi từ máy chủ: " + event.message);
                CToast.warning(this, event.message);
                break;
        }
    }

    private void updateClock() {
        lblClock.setText(java.time.LocalDateTime.now().format(CLOCK_FORMAT));
    }

    @Override
    public void dispose() {
        // Dừng đồng hồ khi đóng cửa sổ (đăng xuất hoặc bàn giao sang CustomerGUI).
        if (clockTimer != null) clockTimer.stop();
        super.dispose();
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