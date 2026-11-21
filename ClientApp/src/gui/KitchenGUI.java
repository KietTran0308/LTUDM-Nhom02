package gui;

import api.ImageSocketClient;
import java.awt.image.BufferedImage;

import api.SocketClient;
import bus.KitchenBUS;
import components.*;
import dto.*;
import utils.Utils;

import javax.sound.sampled.*;
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
    private final CToggleButton allTab = new CToggleButton("Tất cả trong ngày", 210, false);
    private final JLabel queueSummary = new JLabel("Chưa có món trong hàng đợi");

    private static final int QUEUE_POLL_MS = 100;
    private static final int AUTO_REFRESH_MS = 5000;
    private static final String LOGO_IMAGE_KEY = "things/logo.png";

    private int stage;
    private javax.swing.Timer responseTimer;
    private javax.swing.Timer autoRefreshTimer;

    private final JLabel lblClock = new JLabel("", SwingConstants.RIGHT);
    private static final DateTimeFormatter CLOCK_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss  |  dd/MM/yyyy");
    private javax.swing.Timer clockTimer;

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

        lblClock.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 15));
        lblClock.setForeground(Color.WHITE);
        lblClock.setPreferredSize(new Dimension(220, 42));
        lblClock.setBorder(new EmptyBorder(0, 0, 0, 6));
        updateClock();
        clockTimer = new javax.swing.Timer(1000, e -> updateClock());
        clockTimer.start();

        headerRight.add(lblClock);
        headerRight.add(logout);
        // Tai logo tu ServerApp qua TCP/IMG/2 tren luong nen, khong doc getResource cua ClientApp.
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
                if (!selected && item != null && item.getStatus() != null) {
                    switch (item.getStatus()) {
                        case COMPLETED -> setBackground(new Color(220, 241, 225)); // Xanh lá nhạt: Nấu xong
                        case COOKING   -> setBackground(new Color(255, 239, 211)); // Cam nhạt: Đang nấu
                        case SERVED    -> {
                            setBackground(new Color(238, 240, 239)); // Xám nhạt: Đã phục vụ
                            setForeground(new Color(110, 120, 115));
                        }
                        case CANCELLED -> {
                            setBackground(new Color(253, 232, 232)); // Đỏ nhạt: Đã hủy
                            setForeground(new Color(160, 50, 50));
                        }
                        default        -> setBackground(Color.WHITE);
                    }
                }
                if (t.convertColumnIndexToModel(col) == 5 && value != null && !value.toString().isBlank()) {
                    setForeground(selected ? t.getSelectionForeground() : new Color(170, 55, 30));
                }
                return this;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateAction());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel middle = new JPanel(new BorderLayout(0, 8));
        middle.setBorder(new EmptyBorder(8, 22, 4, 22));

        JPanel queueHeader = new JPanel(new BorderLayout(0, 10));
        queueHeader.setOpaque(false);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filters.setBackground(Color.WHITE);
        filters.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(227, 232, 228)));

        JPanel leftTabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftTabs.setOpaque(false);

        JPanel rightTotal = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightTotal.setOpaque(false);

        ButtonGroup group = new ButtonGroup();
        CToggleButton[] tabs = {waiting, cooking, ready, allTab};
        for (int i = 0; i < tabs.length; i++) {
            final int selectedStage = i;
            CToggleButton tab = tabs[i];
            group.add(tab);
            tab.addActionListener(e -> {
                stage = selectedStage;
                renderQueue(kitchenBUS.getQueue());
            });
            if (i < 3) {
                leftTabs.add(tab);
            } else {
                rightTotal.add(tab);
            }
        }
        filters.add(leftTabs, BorderLayout.WEST);
        filters.add(rightTotal, BorderLayout.EAST);

        queueSummary.setFont(new Font("SansSerif", Font.PLAIN, 13));
        queueSummary.setBorder(new EmptyBorder(1, 5, 2, 0));
        queueSummary.setBackground(new Color(255, 255, 255));
        queueHeader.add(filters, BorderLayout.CENTER);
        queueHeader.add(queueSummary, BorderLayout.SOUTH);
        middle.add(queueHeader, BorderLayout.NORTH);

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
        dbEnums.OrderStatus next = kitchenBUS.nextStatus(item);
        boolean pending = item != null && pendingUpdates.containsKey(item.getId());
        nextAction.setText(pending ? "Đang cập nhật..." : next == null ? "Chọn món để thao tác"
                : switch (next) {
                    case RECEIVED -> "Nhận món";
                    case COOKING -> "Bắt đầu chế biến";
                    case COMPLETED -> "Nấu xong";
                    case SERVED -> "Đã phục vụ";
                    default -> "Chọn món để thao tác";
                });
        nextAction.setEnabled(next != null && !pending && kitchenBUS.isConnected());
    }

    private void advanceSelectedItem() {
        OrderItemDTO item = selectedItemAt(table.getSelectedRow());
        dbEnums.OrderStatus next = kitchenBUS.nextStatus(item);
        if (next == null || pendingUpdates.containsKey(item.getId())) return;
        if (kitchenBUS.updateItemStatus(item.getId(), next)) {
            pendingUpdates.put(item.getId(), System.currentTimeMillis());
            updateAction();
        } else CToast.error(this, "Mất kết nối Server. Vui lòng kiểm tra mạng và đăng nhập lại.");
    }

    private String statusText(dbEnums.OrderStatus value) {
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
            case NEW_ORDER_ALERT:
                renderQueue(kitchenBUS.getQueue());
                playNewOrderSound();
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
        int totalTodayCount = 0;

        for (OrderDTO o : orders) {
            if (o.getItems() == null) continue;
            for (OrderItemDTO item : o.getItems()) {
                dbEnums.OrderStatus value = item.getStatus();
                if (value == null) continue;

                totalTodayCount++;

                int itemStage = -1;
                if (value != dbEnums.OrderStatus.SERVED && value != dbEnums.OrderStatus.CANCELLED) {
                    itemStage = (value == dbEnums.OrderStatus.COOKING) ? 1 : (value == dbEnums.OrderStatus.COMPLETED ? 2 : 0);
                    counts[itemStage]++;
                }

                // Tab 3 ("Tất cả trong ngày") hiển thị toàn bộ, 3 tab đầu chỉ hiện món chưa SERVED/CANCELLED
                if (stage != 3 && itemStage != stage) continue;

                visibleItems.put(item.getId(), item);
                if (Objects.equals(item.getId(), chosen)) selectedRow = model.getRowCount();
                String tableName = o.getTableName() == null || o.getTableName().isBlank()
                        ? "Bàn #" + o.getTableId() : o.getTableName();
                model.addRow(new Object[]{
                        item.getId(),
                        tableName,
                        o.getOrderCode() == null ? "#" + o.getId() : o.getOrderCode(),
                        item.getFoodsName(),
                        item.getQuantity(),
                        item.getNote(),
                        o.getCreatedAt() == null ? "—" : o.getCreatedAt().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                        statusText(value)
                });
            }
        }
        waiting.setText("Chờ nhận / đã nhận (" + counts[0] + ")");
        cooking.setText("Đang chế biến (" + counts[1] + ")");
        ready.setText("Chờ phục vụ (" + counts[2] + ")");
        allTab.setText("Trong ngày (" + totalTodayCount + ")");
        queueSummary.setText(model.getRowCount() == 0
                ? "Không có món nào trong nhóm này."
                : "Hiển thị " + model.getRowCount() + " món — Dữ liệu đồng bộ trực tiếp từ CSDL trong ngày.");
        if (selectedRow >= 0) {
            int viewRow = table.convertRowIndexToView(selectedRow);
            if (viewRow >= 0) table.setRowSelectionInterval(viewRow, viewRow);
        } else if (table.getRowCount() > 0) {
            table.setRowSelectionInterval(0, 0);
        }
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
        if (clockTimer != null) clockTimer.stop();
        kitchenBUS.logout();
        dispose();
        new AuthGUI().setVisible(true);
    }

    @Override
    public void dispose() {
        if (clockTimer != null) clockTimer.stop();
        super.dispose();
    }

    /** Load logo via TCP IMG/2, khong doc file local va khong block Swing EDT. */
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
                // ImageSocketClient gui IMG/2 things/logo.png, nhan cac block byte nhi phan.
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

    private void playNewOrderSound() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                try {
                    AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
                    byte[] tone1 = generateTone(880.0, 0.14, format);
                    byte[] pause = new byte[(int) (0.05 * format.getSampleRate() * format.getFrameSize())];
                    byte[] tone2 = generateTone(1174.66, 0.22, format);

                    byte[] combined = new byte[tone1.length + pause.length + tone2.length];
                    System.arraycopy(tone1, 0, combined, 0, tone1.length);
                    System.arraycopy(pause, 0, combined, tone1.length, pause.length);
                    System.arraycopy(tone2, 0, combined, tone1.length + pause.length, tone2.length);

                    AudioInputStream ais = new AudioInputStream(
                            new java.io.ByteArrayInputStream(combined),
                            format,
                            combined.length / format.getFrameSize());
                    Clip clip = AudioSystem.getClip();
                    clip.open(ais);
                    clip.addLineListener(ev -> {
                        if (ev.getType() == LineEvent.Type.STOP) {
                            clip.close();
                        }
                    });
                    clip.start();
                } catch (Exception ex) {
                    Toolkit.getDefaultToolkit().beep();
                }
                return null;
            }

            private byte[] generateTone(double freqHz, double durationSec, AudioFormat format) {
                int samples = (int) (durationSec * format.getSampleRate());
                byte[] buf = new byte[samples * 2];
                for (int i = 0; i < samples; i++) {
                    double angle = 2.0 * Math.PI * i * freqHz / format.getSampleRate();
                    // Envelope mượt để tránh tiếng "bụp" đầu/cuối
                    double env = Math.min(1.0, Math.min(i / 400.0, (samples - i) / 600.0));
                    short sample = (short) (Math.sin(angle) * env * Short.MAX_VALUE * 0.45);
                    buf[i * 2] = (byte) (sample & 0xff);
                    buf[i * 2 + 1] = (byte) ((sample >> 8) & 0xff);
                }
                return buf;
            }
        }.execute();
    }

    private void updateClock() {
        lblClock.setText(java.time.LocalDateTime.now().format(CLOCK_FORMAT));
    }

}
