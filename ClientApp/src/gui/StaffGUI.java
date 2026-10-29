package gui;

import bus.StaffBUS;
import dto.*;
import gui.components.*;
import utils.Utils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.text.DecimalFormat;
import java.util.List;

public class StaffGUI extends JFrame {

    private static final Color TABLE_FREE_COLOR = new Color(38, 77, 56);
    private static final Color TABLE_BUSY_COLOR = Utils.TERRACOTTA;

    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,### VNĐ");

    private final StaffBUS posBUS;
    private final UserDTO currentStaff;
    private Long selectedTableId = null;
    private String selectedTableName = "";

    private JPanel tableGridPanel;
    private DefaultTableModel billItemsModel;
    private JLabel lblSelectedTableInfo;
    private JLabel lblRootOrderCode;
    private JLabel lblSubtotal;
    private JLabel lblDiscount;
    private JLabel lblTaxVAT;
    private JLabel lblFinalTotal;
    private JComboBox<PaymentMethod> cboPaymentMethod;
    private CButton btnCheckout;

    public StaffGUI() {
        this(null);
    }

    public StaffGUI(UserDTO loggedInStaff) {
        this.currentStaff = loggedInStaff;

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setUndecorated(true);
        setSize(1360, 820);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initBrandHeader();
        initMainWorkspace();

        posBUS = new StaffBUS(this::onServerPacketReceived);
        posBUS.requestAllTables();
    }

    private void onServerPacketReceived(SocketPacket packet) {
        SwingUtilities.invokeLater(() -> {
            if (!packet.isSuccess()) {
                clearInvoicePanel();
                JOptionPane.showMessageDialog(this, packet.getMessage(), "Thông báo Bếp Nhà", JOptionPane.WARNING_MESSAGE);
                return;
            }

            switch (packet.getAction()) {
                case GET_ALL_TABLES:
                    renderTableMap(posBUS.getTableList());
                    break;

                case GET_TABLE_BILL_PREVIEW:
                    renderMergedBillPreview(posBUS.getCurrentPreviewOrder());
                    break;

                case PAY_TABLE_BILL:
                    renderTableMap(posBUS.getTableList());
                    clearInvoicePanel();
                    JOptionPane.showMessageDialog(this, packet.getMessage(), "Thanh Toán Thành Công", JOptionPane.INFORMATION_MESSAGE);
                    break;

                default:
                    break;
            }
        });
    }

    private void initBrandHeader() {
        JPanel header = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Utils.FOREST_CIRCLE_1);
                g2.fillOval(getWidth() / 2 - 150, -180, 380, 380);
            }
        };
        header.setBackground(Utils.FOREST_BG);
        header.setPreferredSize(new Dimension(getWidth(), 78));
        header.setBorder(new EmptyBorder(14, 28, 14, 28));

        JPanel leftBrand = new JPanel();
        leftBrand.setLayout(new BoxLayout(leftBrand, BoxLayout.X_AXIS));
        leftBrand.setOpaque(false);

        JLabel lblIcon = new JLabel(Utils.getScaledIcon("img/logo.png", 40, 40));
        lblIcon.setBorder(new EmptyBorder(0, 0, 0, 10));

        JPanel titleCol = new JPanel();
        titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));
        titleCol.setOpaque(false);

        JLabel lblBrand = new JLabel("Nhà hàng An Nhiên  |  QUẦY THU NGÂN & QUẢN LÝ BÀN");
        lblBrand.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 20));
        lblBrand.setForeground(Color.WHITE);

        String staffInfo = (currentStaff != null)
                ? "Nhân viên trực ca: " + currentStaff.getFullName() + " (" + currentStaff.getEmail() + ")"
                : "Hệ thống POS kết nối thời gian thực (TCP Socket)";
        JLabel lblSub = new JLabel(staffInfo);
        lblSub.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 12));
        lblSub.setForeground(Utils.GOLD_ACCENT);

        titleCol.add(lblBrand);
        titleCol.add(Box.createVerticalStrut(2));
        titleCol.add(lblSub);

        leftBrand.add(lblIcon);
        leftBrand.add(titleCol);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
        rightActions.setOpaque(false);

        CButton btnRefresh = new CButton("Làm Mới Sơ Đồ Bàn", 190, 42);
        btnRefresh.addActionListener(e -> posBUS.requestAllTables());

        JButton btnLogout = new JButton("Đăng xuất");
        btnLogout.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        btnLogout.setForeground(Color.WHITE);
        btnLogout.setBackground(new Color(52, 66, 58));
        btnLogout.setFocusPainted(false);
        btnLogout.setPreferredSize(new Dimension(115, 42));
        btnLogout.setBorder(new LineBorder(new Color(80, 95, 86), 1, true));
        btnLogout.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnLogout.addActionListener(e -> {
            new AuthGUI().setVisible(true);
            dispose();
        });

        rightActions.add(btnRefresh);
        rightActions.add(btnLogout);

        header.add(leftBrand, BorderLayout.WEST);
        header.add(rightActions, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    private void initMainWorkspace() {
        JPanel workspace = new JPanel(new GridLayout(1, 2, 22, 0));
        workspace.setBackground(Utils.CREAM_BG);
        workspace.setBorder(new EmptyBorder(22, 25, 25, 25));

        JPanel leftCard = new JPanel(new BorderLayout(0, 15));
        leftCard.setBackground(Color.WHITE);
        leftCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(228, 232, 228), 1, true),
                new EmptyBorder(20, 20, 20, 20)
        ));

        JPanel leftHeader = new JPanel(new BorderLayout());
        leftHeader.setOpaque(false);

        JLabel lblMapTitle = new JLabel("Sơ Đồ Bàn Phục Vụ");
        lblMapTitle.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblMapTitle.setForeground(Utils.TEXT_DARK);

        JLabel lblLegend = new JLabel("● Xanh rừng: Bàn trống   |   ● Cam đất: Đang dùng bữa");
        lblLegend.setFont(Utils.getCustomFont("Inter", Font.BOLD, 12));
        lblLegend.setForeground(Utils.GOLD_ACCENT);

        leftHeader.add(lblMapTitle, BorderLayout.NORTH);
        leftHeader.add(lblLegend, BorderLayout.SOUTH);

        tableGridPanel = new JPanel(new GridLayout(0, 3, 16, 16));
        tableGridPanel.setBackground(Color.WHITE);
        tableGridPanel.setBorder(new EmptyBorder(10, 5, 10, 5));

        JScrollPane tableScroll = new JScrollPane(tableGridPanel);
        tableScroll.setVerticalScrollBar(new CScrollBar());
        tableScroll.setBorder(null);

        leftCard.add(leftHeader, BorderLayout.NORTH);
        leftCard.add(tableScroll, BorderLayout.CENTER);

        JPanel rightCard = new JPanel(new BorderLayout(0, 15));
        rightCard.setBackground(Color.WHITE);
        rightCard.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(228, 232, 228), 1, true),
                new EmptyBorder(20, 24, 20, 24)
        ));

        JPanel billHeader = new JPanel();
        billHeader.setLayout(new BoxLayout(billHeader, BoxLayout.Y_AXIS));
        billHeader.setOpaque(false);

        lblSelectedTableInfo = new JLabel("Chưa chọn bàn cần thanh toán");
        lblSelectedTableInfo.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 22));
        lblSelectedTableInfo.setForeground(Utils.TEXT_DARK);

        lblRootOrderCode = new JLabel("Mã đơn gốc (Root Order): ---");
        lblRootOrderCode.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        lblRootOrderCode.setForeground(Color.GRAY);

        billHeader.add(lblSelectedTableInfo);
        billHeader.add(Box.createVerticalStrut(4));
        billHeader.add(lblRootOrderCode);

        String[] cols = {"Lượt Gọi", "Tên Món Ăn", "Đơn Giá", "SL", "Thành Tiền", "Trạng Thái"};
        billItemsModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        JTable billTable = new JTable(billItemsModel);
        billTable.setRowHeight(34);
        billTable.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 13));
        billTable.setShowVerticalLines(false);
        billTable.setGridColor(new Color(240, 240, 240));

        JTableHeader th = billTable.getTableHeader();
        th.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        th.setBackground(Utils.FOREST_BG);
        th.setForeground(Color.WHITE);
        th.setPreferredSize(new Dimension(th.getWidth(), 38));

        JPanel summaryBox = new JPanel(new GridLayout(5, 2, 10, 10));
        summaryBox.setBackground(Utils.CREAM_BG);
        summaryBox.setBorder(new EmptyBorder(16, 18, 16, 18));

        lblSubtotal = createValueLabel("0 VNĐ", Font.BOLD, 14, Utils.TEXT_DARK);
        lblDiscount = createValueLabel("0 VNĐ", Font.BOLD, 14, new Color(46, 125, 50));
        lblTaxVAT = createValueLabel("0 VNĐ", Font.BOLD, 14, Utils.TEXT_DARK);
        lblFinalTotal = createValueLabel("0 VNĐ", Font.BOLD, 22, Utils.TERRACOTTA);

        cboPaymentMethod = new JComboBox<>(PaymentMethod.values());
        cboPaymentMethod.setFont(Utils.getCustomFont("Inter", Font.BOLD, 14));
        cboPaymentMethod.setBackground(Color.WHITE);

        summaryBox.add(createTitleLabel("Tổng tiền món (Đã gộp các lượt):"));
        summaryBox.add(lblSubtotal);
        summaryBox.add(createTitleLabel("Giảm giá (Voucher):"));
        summaryBox.add(lblDiscount);
        summaryBox.add(createTitleLabel("Thuế GTGT (VAT 8%):"));
        summaryBox.add(lblTaxVAT);
        summaryBox.add(createTitleLabel("Phương thức thanh toán:"));
        summaryBox.add(cboPaymentMethod);
        summaryBox.add(createTitleLabel("TỔNG THANH TOÁN (BILL):"));
        summaryBox.add(lblFinalTotal);

        btnCheckout = new CButton("Xuất Hóa Đơn & Giải Phóng Bàn", 500, 52);
        btnCheckout.setEnabled(false);
        btnCheckout.addActionListener(e -> handleConfirmPayment());

        JPanel southPanel = new JPanel(new BorderLayout(0, 14));
        southPanel.setOpaque(false);
        southPanel.add(summaryBox, BorderLayout.CENTER);
        southPanel.add(btnCheckout, BorderLayout.SOUTH);

        rightCard.add(billHeader, BorderLayout.NORTH);
        JScrollPane billScroll = new JScrollPane(billTable);
        billScroll.setVerticalScrollBar(new CScrollBar());
        billScroll.setBorder(BorderFactory.createEmptyBorder());
        rightCard.add(billScroll, BorderLayout.CENTER);
        rightCard.add(southPanel, BorderLayout.SOUTH);

        workspace.add(leftCard);
        workspace.add(rightCard);
        add(workspace, BorderLayout.CENTER);
    }

    private JLabel createTitleLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
        lbl.setForeground(Utils.TEXT_DARK);
        return lbl;
    }

    private JLabel createValueLabel(String text, int style, int size, Color color) {
        JLabel lbl = new JLabel(text, SwingConstants.RIGHT);
        lbl.setFont(Utils.getCustomFont("Be Vietnam Pro Black", style, size));
        lbl.setForeground(color);
        return lbl;
    }

    private void renderTableMap(List<TableDTO> tables) {
        tableGridPanel.removeAll();
        for (TableDTO t : tables) {
            boolean isFree = Boolean.TRUE.equals(t.getStatus());

            JButton btnTable = new JButton() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(isFree ? TABLE_FREE_COLOR : TABLE_BUSY_COLOR);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                    super.paintComponent(g);
                }
            };
            btnTable.setText("<html><center><b>" + t.getTableName() + "</b><br/>"
                    + "<span style='font-size:10px;color:#E0E0E0;'>Sức chứa: " + t.getCapacity() + " khách</span><br/><br/>"
                    + (isFree ? "● BÀN TRỐNG" : "● ĐANG DÙNG BỮA") + "</center></html>");

            btnTable.setPreferredSize(new Dimension(150, 125));
            btnTable.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.PLAIN, 14));
            btnTable.setForeground(Color.WHITE);
            btnTable.setContentAreaFilled(false);
            btnTable.setFocusPainted(false);
            btnTable.setCursor(new Cursor(Cursor.HAND_CURSOR));

            btnTable.addActionListener(e -> {
                selectedTableId = t.getId();
                selectedTableName = t.getTableName();
                lblSelectedTableInfo.setText("Bàn đang chọn: " + selectedTableName + " (#" + selectedTableId + ")");
                if (isFree) {
                    clearInvoicePanel();
                    JOptionPane.showMessageDialog(this, selectedTableName + " hiện đang trống!");
                } else {
                    posBUS.requestTableBillPreview(selectedTableId);
                }
            });

            tableGridPanel.add(btnTable);
        }
        tableGridPanel.revalidate();
        tableGridPanel.repaint();
    }

    private void renderMergedBillPreview(OrderDTO mergedOrder) {
        billItemsModel.setRowCount(0);
        if (mergedOrder == null) {
            clearInvoicePanel();
            return;
        }

        lblRootOrderCode.setText("Mã đơn gốc: " + mergedOrder.getOrderCode()
                + " (ID gốc: #" + mergedOrder.getId() + " — Đã gộp toàn bộ các lượt gọi thêm)");

        for (OrderItemDTO item : mergedOrder.getItems()) {
            String turnLabel = item.getOrderId().equals(mergedOrder.getId())
                    ? "Đơn gốc (#" + item.getOrderId() + ")"
                    : "Gọi thêm (#" + item.getOrderId() + ")";

            billItemsModel.addRow(new Object[]{
                    turnLabel,
                    item.getFoodsName(),
                    MONEY_FMT.format(item.getUnitPrice()),
                    item.getQuantity(),
                    MONEY_FMT.format(item.getLineTotal()),
                    item.getStatus().name()
            });
        }

        lblSubtotal.setText(MONEY_FMT.format(mergedOrder.getSubtotal()));
        lblDiscount.setText("-" + MONEY_FMT.format(mergedOrder.getDiscountAmount()));
        lblTaxVAT.setText("+" + MONEY_FMT.format(posBUS.calculateVAT()));
        lblFinalTotal.setText(MONEY_FMT.format(posBUS.calculateFinalBillTotal()));
        btnCheckout.setEnabled(true);
    }

    private void clearInvoicePanel() {
        billItemsModel.setRowCount(0);
        lblRootOrderCode.setText("Mã đơn gốc (Root Order): ---");
        lblSubtotal.setText("0 VNĐ");
        lblDiscount.setText("0 VNĐ");
        lblTaxVAT.setText("0 VNĐ");
        lblFinalTotal.setText("0 VNĐ");
        btnCheckout.setEnabled(false);
    }

    private void handleConfirmPayment() {
        if (selectedTableId == null || posBUS.getCurrentPreviewOrder() == null) return;

        PaymentMethod method = (PaymentMethod) cboPaymentMethod.getSelectedItem();
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Xác nhận xuất hóa đơn thanh toán cho " + selectedTableName + "?\n"
                        + "- Tổng thanh toán: " + lblFinalTotal.getText() + "\n"
                        + "- Phương thức: " + method.name() + "\n\n"
                        + "Sau khi xác nhận, tất cả các lượt gọi món sẽ chuyển sang COMPLETED và bàn sẽ mở trống.",
                "Xác Nhận Thanh Toán - Bếp Nhà Restaurant",
                JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            posBUS.confirmCheckout(selectedTableId, method);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new StaffGUI().setVisible(true);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
    }
}