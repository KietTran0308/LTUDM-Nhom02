package components;

import dto.OrderItemDTO;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.util.List;

public class CTable extends JPanel {

    private final DefaultTableModel model;
    private final JTable table;

    public CTable() {
        super(new BorderLayout());

        model = new DefaultTableModel(
                new Object[]{"Món ăn", "Số lượng", "Trạng thái"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(model);
        table.setRowHeight(32);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setVerticalScrollBar(new CScrollBar());

        add(scrollPane, BorderLayout.CENTER);
    }

    public JTable getTable() {
        return table;
    }

    public void updateTrackedItems(List<OrderItemDTO> items) {
        model.setRowCount(0);

        if (items == null) return;

        for (OrderItemDTO item : items) {
            model.addRow(new Object[]{
                    item.getFoodsName(),
                    item.getQuantity(),
                    item.getStatus() == null
                            ? ""
                            : item.getStatus().name()
            });
        }
    }
}