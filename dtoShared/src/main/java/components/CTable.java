package components;

import dto.OrderItemDTO;
import dto.OrderStatus;
import utils.Utils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.List;

import java.util.ArrayList;

public class CTable extends JPanel {

    private static final String[] COLUMNS = {
            "Mã Món Trong Đơn", "Tên Món Ăn", "Số Lượng", "Trạng Thái Bếp"
    };

    private final DefaultTableModel tableModel;
    private final List<Long> rowItemIds = new ArrayList<>();
    private final JTable table;

    public CTable() {
        super(new BorderLayout());
        setBackground(Utils.CREAM_BG);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(38);
        table.setFont(Utils.getCustomFont("Inter", Font.PLAIN, 14));
        table.setSelectionBackground(new Color(253, 237, 232));
        table.setSelectionForeground(Utils.TEXT_DARK);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(240, 240, 240));
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setFont(Utils.getCustomFont("Be Vietnam Pro Black", Font.BOLD, 13));
        header.setBackground(Utils.FOREST_BG);
        header.setForeground(Color.WHITE);
        header.setPreferredSize(new Dimension(header.getPreferredSize().width, 40));
        header.setReorderingAllowed(false);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setVerticalScrollBar(new CScrollBar());
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        add(scrollPane, BorderLayout.CENTER);
    }

    /** Làm mới dữ liệu khi server gửi trạng thái đơn mới. */
    public void updateTrackedItems(List<OrderItemDTO> items) {
        tableModel.setRowCount(0);
        rowItemIds.clear();
        if (items == null) return;

        for (OrderItemDTO item : items) {
            rowItemIds.add(item.getId());
            tableModel.addRow(new Object[] {
                    item.getId() != null ? "#" + item.getId() : "---",
                    item.getFoodsName(),
                    item.getQuantity(),
                    formatStatusVietnamese(item.getStatus())
            });
        }
    }

    public JTable getTable() {
        return table;
    }

    public Long getSelectedOrderItemId() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(viewRow);

        if (modelRow < 0 || modelRow >= rowItemIds.size()) return null;
        return rowItemIds.get(modelRow);
    }

    private static String formatStatusVietnamese(OrderStatus status) {
        if (status == null) return "⏳ Chờ xác nhận";
        switch (status) {
            case PENDING: return "⏳ Chờ xác nhận";
            case RECEIVED: return "📥 Bếp đã nhận";
            case COOKING: return "🔥 Đang chế biến";
            case COMPLETED: return "🛎️ Đã nấu xong";
            case SERVED: return "✅ Đã lên món";
            case CANCELLED: return "❌ Đã hủy";
            default: return status.name();
        }
    }
}
