/**package gui;

import bus.KdsManagementBUS;
import bus.KdsOrderBUS;
import dto.*;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class KdsManagementPanel extends JPanel {
    private final KdsManagementBUS management = new KdsManagementBUS();
    private final KdsOrderBUS orders = new KdsOrderBUS();
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final Color background = new Color(247, 248, 246);

    private interface Task {
        void execute() throws Exception;
    }

    private void run(Task task) {
        try {
            task.execute();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Không thực hiện được", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JTextField text(String value) {
        return new JTextField(value == null ? "" : value, 20);
    }

    private Map<String, String> form(String title, String[] fields, String[] defaults) {
        JPanel panel = new JPanel(new GridLayout(fields.length, 2, 10, 8));
        JTextField[] input = new JTextField[fields.length];
        for (int i = 0; i < fields.length; i++) {
            panel.add(new JLabel(fields[i]));
            input[i] = text(defaults[i]);
            panel.add(input[i]);
        }
        if (JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION)
            return null;
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i++) out.put(fields[i], input[i].getText().trim());
        return out;
    }

    private static long id(JTable table) {
        int view = table.getSelectedRow();
        if (view < 0) throw new IllegalArgumentException("Chọn một dòng trong danh sách trước");
        return Long.parseLong(table.getModel().getValueAt(table.convertRowIndexToModel(view), 0).toString());
    }

    private static String cell(JTable t, int col) {
        int row = t.convertRowIndexToModel(t.getSelectedRow());
        Object value = t.getModel().getValueAt(row, col);
        return value == null ? "" : value.toString();
    }

    private JTable table(String... headers) {
        DefaultTableModel model = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable t = new JTable(model);
        t.setRowHeight(31);
        t.setAutoCreateRowSorter(true);
        t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        return t;
    }

    private static void rows(JTable table, Object[][] items) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);
        for (Object[] item : items) model.addRow(item);
    }

    private JButton action(JPanel tools, String name, Task task) {
        JButton b = new JButton(name);
        b.addActionListener(e -> run(task));
        tools.add(b);
        return b;
    }

    private JPanel page(JTable table, JPanel tools) {
        JPanel p = new JPanel(new BorderLayout(5, 8));
        p.setBackground(background);
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(tools, BorderLayout.NORTH);
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        return p;
    }

    private JPanel tools() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 5));
        p.setOpaque(false);
        return p;
    }

    public KdsManagementPanel() {
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Danh mục", categories());
        tabs.addTab("Bàn", tables());
        tabs.addTab("Món ăn / tìm kiếm", foods());
        tabs.addTab("Voucher", vouchers());
        tabs.addTab("Người dùng", users());
        tabs.addTab("Đơn hàng", orders());
        add(tabs, BorderLayout.CENTER);
    }

    private JPanel categories() {
        JTable t = table("ID", "Tên", "Slug", "Mô tả");
        JPanel tools = tools();
        Task refresh = () -> {
            java.util.List<CategoryDTO> data = management.categories();
            Object[][] values = new Object[data.size()][4];
            for (int i = 0; i < data.size(); i++) {
                CategoryDTO c = data.get(i);
                values[i] = new Object[]{c.getId(), c.getName(), c.getSlug(), c.getDescription()};
            }
            rows(t, values);
        };
        action(tools, "Làm mới", refresh);
        action(tools, "Thêm", () -> editCategory(t, false, refresh));
        action(tools, "Sửa", () -> editCategory(t, true, refresh));
        action(tools, "Xóa", () -> {
            long id = id(t);
            if (confirmDelete()) {
                management.deleteCategory(id);
                refresh.execute();
            }
        });
        run(refresh);
        return page(t, tools);
    }

    private void editCategory(JTable t, boolean edit, Task refresh) throws Exception {
        Long id = edit ? id(t) : null;
        Map<String, String> p = form("Danh mục", new String[]{"Tên", "Slug (trống = tự tạo)", "Mô tả"},
                new String[]{edit ? cell(t, 1) : "", edit ? cell(t, 2) : "", edit ? cell(t, 3) : ""});
        if (p == null) return;
        CategoryDTO dto = new CategoryDTO();
        dto.setId(id);
        dto.setName(p.get("Tên"));
        dto.setSlug(p.get("Slug (trống = tự tạo)"));
        dto.setDescription(p.get("Mô tả"));
        management.saveCategory(dto);
        refresh.execute();
    }

    private JPanel tables() {
        JTable t = table("ID", "Tên bàn", "Sức chứa", "Trống?");
        JPanel tools = tools();
        Task refresh = () -> {
            java.util.List<TableDTO> data = management.tables();
            Object[][] values = new Object[data.size()][4];
            for (int i = 0; i < data.size(); i++) {
                TableDTO x = data.get(i);
                values[i] = new Object[]{x.getId(), x.getTableName(), x.getCapacity(), x.getStatus()};
            }
            rows(t, values);
        };
        action(tools, "Làm mới", refresh);
        action(tools, "Thêm", () -> editTable(t, false, refresh));
        action(tools, "Sửa", () -> editTable(t, true, refresh));
        action(tools, "Xóa", () -> {
            long id = id(t);
            if (confirmDelete()) {
                management.deleteTable(id);
                refresh.execute();
            }
        });
        run(refresh);
        return page(t, tools);
    }

    private void editTable(JTable t, boolean edit, Task refresh) throws Exception {
        Long id = edit ? id(t) : null;
        Map<String, String> p = form("Thông tin bàn", new String[]{"Tên bàn", "Sức chứa", "Trống? true/false"},
                new String[]{edit ? cell(t, 1) : "", edit ? cell(t, 2) : "4", edit ? cell(t, 3) : "true"});
        if (p == null) return;
        TableDTO dto = new TableDTO();
        dto.setId(id);
        dto.setTableName(p.get("Tên bàn"));
        dto.setCapacity(Integer.parseInt(p.get("Sức chứa")));
        dto.setStatus(Boolean.parseBoolean(p.get("Trống? true/false")));
        management.saveTable(dto);
        refresh.execute();
    }

    private JPanel foods() {
        JTable t = table("ID", "SKU", "Tên", "Danh mục", "Giá", "Hiển thị");
        JPanel tools = tools();
        JTextField search = text("");
        JTextField category = text("");
        category.setColumns(5);
        tools.add(new JLabel("Từ khóa"));
        tools.add(search);
        tools.add(new JLabel("ID danh mục"));
        tools.add(category);
        Task refresh = () -> {
            Long cat = category.getText().isBlank() ? null : Long.valueOf(category.getText().trim());
            java.util.List<FoodDTO> data = management.searchFoods(search.getText(), cat);
            Object[][] values = new Object[data.size()][6];
            for (int i = 0; i < data.size(); i++) {
                FoodDTO f = data.get(i);
                values[i] = new Object[]{f.getId(), f.getSku(), f.getName(), f.getCategoryId(), f.getPrice(), f.getIsPublished()};
            }
            rows(t, values);
        };
        action(tools, "Tìm / Tải lại", refresh);
        tools.add(new JLabel("Thêm/sửa món: dùng tab Quản lý thực đơn đã có sẵn trong AdminGUI."));
        run(refresh);
        return page(t, tools);
    }

    private JPanel vouchers() {
        JTable t = table("ID", "Mã", "Tên", "Loại", "Giá trị", "Tối thiểu", "Tối đa", "Bắt đầu", "Kết thúc", "Giới hạn", "Đã dùng", "Hoạt động");
        JPanel tools = tools();
        Task refresh = () -> {
            java.util.List<VoucherDTO> data = management.vouchers();
            Object[][] values = new Object[data.size()][12];
            for (int i = 0; i < data.size(); i++) {
                VoucherDTO v = data.get(i);
                values[i] = new Object[]{v.getId(), v.getCode(), v.getName(), v.getDiscountType(), v.getDiscountValue(), v.getMinOrderAmount(), v.getMaxDiscountAmount(), v.getStartAt(), v.getEndAt(), v.getUsageLimit(), v.getUsedCount(), v.getIsActive()};
            }
            rows(t, values);
        };
        action(tools, "Làm mới", refresh);
        action(tools, "Thêm", () -> editVoucher(t, false, refresh));
        action(tools, "Sửa", () -> editVoucher(t, true, refresh));
        action(tools, "Ngừng áp dụng", () -> {
            management.disableVoucher(id(t));
            refresh.execute();
        });
        run(refresh);
        return page(t, tools);
    }

    private static BigDecimal decimal(String v) {
        return v == null || v.isBlank() ? null : new BigDecimal(v);
    }

    private static Integer integer(String v) {
        return v == null || v.isBlank() ? null : Integer.valueOf(v);
    }

    private static LocalDateTime date(String v) {
        return LocalDateTime.parse(v, FORMAT);
    }

    private void editVoucher(JTable t, boolean edit, Task refresh) throws Exception {
        Long id = edit ? id(t) : null;
        String[] fields = {"Mã", "Tên", "Loại PERCENT/FIXED", "Giá trị", "Đơn tối thiểu", "Giảm tối đa (có thể trống)", "Bắt đầu yyyy-MM-dd HH:mm", "Kết thúc yyyy-MM-dd HH:mm", "Giới hạn lượt (có thể trống)", "Hoạt động true/false"};
        String now = LocalDateTime.now().format(FORMAT);
        String[] defaults = edit ? new String[]{cell(t, 1), cell(t, 2), cell(t, 3), cell(t, 4), cell(t, 5), cell(t, 6), date(cell(t, 7).replace('T', ' ').substring(0, 16)).format(FORMAT), date(cell(t, 8).replace('T', ' ').substring(0, 16)).format(FORMAT), cell(t, 9), cell(t, 11)}
                : new String[]{"", "", "PERCENT", "10", "0", "", now, LocalDateTime.now().plusMonths(1).format(FORMAT), "", "true"};
        Map<String, String> p = form("Voucher", fields, defaults);
        if (p == null) return;
        VoucherDTO v = new VoucherDTO();
        v.setId(id);
        v.setCode(p.get(fields[0]));
        v.setName(p.get(fields[1]));
        v.setDiscountType(DiscountType.valueOf(p.get(fields[2]).toUpperCase()));
        v.setDiscountValue(decimal(p.get(fields[3])));
        v.setMinOrderAmount(decimal(p.get(fields[4])));
        v.setMaxDiscountAmount(decimal(p.get(fields[5])));
        v.setStartAt(date(p.get(fields[6])));
        v.setEndAt(date(p.get(fields[7])));
        v.setUsageLimit(integer(p.get(fields[8])));
        v.setIsActive(Boolean.valueOf(p.get(fields[9])));
        management.saveVoucher(v);
        refresh.execute();
    }

    private JPanel users() {
        JTable t = table("ID", "Email", "Họ tên", "Số điện thoại", "Mã quyền", "Vai trò", "Hoạt động");
        JPanel tools = tools();
        Task refresh = () -> {
            java.util.List<UserDTO> data = management.users();
            Object[][] values = new Object[data.size()][7];
            for (int i = 0; i < data.size(); i++) {
                UserDTO u = data.get(i);
                values[i] = new Object[]{u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getRoleID(), u.getRoleName(), u.getIsActive()};
            }
            rows(t, values);
        };
        action(tools, "Làm mới", refresh);
        action(tools, "Thêm", () -> editUser(t, false, refresh));
        action(tools, "Sửa hồ sơ/quyền", () -> editUser(t, true, refresh));
        action(tools, "Vô hiệu hóa", () -> {
            management.disableUser(id(t));
            refresh.execute();
        });
        action(tools, "Đổi mật khẩu", () -> {
            long selected = id(t);
            Map<String, String> p = form("Đổi mật khẩu", new String[]{"Mật khẩu cũ", "Mật khẩu mới (>= 8 ký tự)"}, new String[]{"", ""});
            if (p != null) {
                management.changePassword(selected, p.get("Mật khẩu cũ"), p.get("Mật khẩu mới (>= 8 ký tự)"));
                JOptionPane.showMessageDialog(this, "Đã đổi mật khẩu");
            }
        });
        action(tools, "Đặt lại mật khẩu", () -> {
            long selected = id(t);
            if (confirmDelete()) {
                String password = management.resetPassword(selected);
                JOptionPane.showMessageDialog(this, "Mật khẩu tạm thời, chỉ hiện một lần:\n" + password + "\nHãy đổi ngay khi đăng nhập.");
            }
        });
        run(refresh);
        return page(t, tools);
    }

    private void editUser(JTable t, boolean edit, Task refresh) throws Exception {
        Long id = edit ? id(t) : null;
        String[] fields = {"Email", "Họ tên", "Số điện thoại", "Mã quyền (tạo mới: dùng quyền mặc định)", "Hoạt động true/false", "Mật khẩu mới (chỉ lúc tạo, >=8 ký tự)"};
        Map<String, String> p = form("Người dùng", fields, edit ? new String[]{cell(t, 1), cell(t, 2), cell(t, 3), cell(t, 4), cell(t, 6), ""} : new String[]{"", "", "", "", "true", ""});
        if (p == null) return;
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setEmail(p.get(fields[0]));
        u.setFullName(p.get(fields[1]));
        u.setPhone(p.get(fields[2]));
        if (edit) u.setRoleID(Long.parseLong(p.get(fields[3])));
        u.setIsActive(Boolean.valueOf(p.get(fields[4])));
        management.saveUser(u, p.get(fields[5]));
        refresh.execute();
    }

    private JPanel orders() {
        JTable t = table("ID", "Mã đơn", "Bàn", "Khách ID", "Loại", "Trạng thái", "Tạm tính", "Giảm", "Tổng", "Ngày tạo");
        JPanel tools = tools();
        JTextField statusFilter = text("");
        statusFilter.setColumns(10);
        tools.add(new JLabel("Lọc trạng thái"));
        tools.add(statusFilter);
        Task refresh = () -> {
            java.util.List<OrderDTO> data = orders.getOrders(statusFilter.getText().trim());
            Object[][] values = new Object[data.size()][10];
            for (int i = 0; i < data.size(); i++) {
                OrderDTO o = data.get(i);
                values[i] = new Object[]{o.getId(), o.getOrderCode(), o.getTableId(), o.getUserId(), o.getOrderType(), o.getStatus(), o.getSubtotal(), o.getDiscountAmount(), o.getTotalAmount(), o.getCreatedAt()};
            }
            rows(t, values);
        };
        action(tools, "Làm mới", refresh);
        action(tools, "Tạo đơn", () -> {
            String[] fields = {"ID khách hàng", "ID bàn (để trống nếu giao/đem về)", "Loại DINE_IN/TAKEAWAY/DELIVERY", "ID món", "Số lượng", "Tên người nhận", "SĐT nhận", "Địa chỉ", "Ghi chú"};
            Map<String, String> p = form("Tạo đơn hàng", fields, new String[]{"", "", "DINE_IN", "", "1", "", "", "", ""});
            if (p != null) {
                OrderDTO o = new OrderDTO();
                o.setUserId(Long.parseLong(p.get(fields[0])));
                if (!p.get(fields[1]).isBlank()) o.setTableId(Long.parseLong(p.get(fields[1])));
                o.setOrderType(OrderType.valueOf(p.get(fields[2]).toUpperCase()));
                OrderItemDTO item = new OrderItemDTO();
                item.setFoodsId(Long.parseLong(p.get(fields[3])));
                item.setQuantity(Integer.parseInt(p.get(fields[4])));
                o.setItems(java.util.List.of(item));
                o.setReceiverName(p.get(fields[5]));
                o.setReceiverPhone(p.get(fields[6]));
                o.setShippingAddress(p.get(fields[7]));
                o.setNote(p.get(fields[8]));
                OrderDTO created = orders.createOrder(o);
                JOptionPane.showMessageDialog(this, "Tạo đơn thành công: " + created.getOrderCode());
                refresh.execute();
            }
        });
        action(tools, "Xem món", () -> {
            OrderDTO o = orders.getOrder(id(t));
            StringBuilder sb = new StringBuilder("Đơn #" + o.getId() + "\n");
            for (OrderItemDTO i : o.getItems())
                sb.append("#").append(i.getId()).append(" ").append(i.getFoodsName()).append(" x").append(i.getQuantity()).append(" / ").append(i.getStatus()).append("\n");
            JOptionPane.showMessageDialog(this, new JScrollPane(new JTextArea(sb.toString(), 16, 46)), "Chi tiết đơn hàng", JOptionPane.INFORMATION_MESSAGE);
        });
        action(tools, "Tìm đơn đang dùng bàn", () -> {
            String answer = JOptionPane.showInputDialog(this, "ID bàn:");
            if (answer != null) {
                OrderDTO o = orders.activeOrderOfTable(Long.parseLong(answer.trim()));
                JOptionPane.showMessageDialog(this, "Đơn đang hoạt động #" + o.getId() + " / " + o.getOrderCode());
            }
        });
        action(tools, "Ghi chú / loại đơn", () -> {
            OrderDTO current = orders.getOrder(id(t));
            Map<String, String> p = form("Sửa đơn", new String[]{"Ghi chú", "Loại DINE_IN/TAKEAWAY/DELIVERY"},
                    new String[]{current.getNote(), current.getOrderType().name()});
            if (p != null) {
                orders.updateOrder(id(t), p.get("Ghi chú"), OrderType.valueOf(p.get("Loại DINE_IN/TAKEAWAY/DELIVERY").toUpperCase()));
                refresh.execute();
            }
        });
        action(tools, "Trạng thái đơn", () -> {
            String value = JOptionPane.showInputDialog(this, "Trạng thái: PENDING, RECEIVED, COOKING, SERVED", cell(t, 5));
            if (value != null) {
                orders.updateOrderStatus(id(t), OrderStatus.valueOf(value.trim().toUpperCase()));
                refresh.execute();
            }
        });
        action(tools, "Trạng thái món", () -> {
            Map<String, String> p = form("Cập nhật món", new String[]{"Item ID", "Trạng thái: PENDING/RECEIVED/COOKING/COMPLETED/SERVED"}, new String[]{"", "COOKING"});
            if (p != null) {
                long oid = id(t), iid = Long.parseLong(p.get("Item ID"));
                OrderStatus status = OrderStatus.valueOf(p.get("Trạng thái: PENDING/RECEIVED/COOKING/COMPLETED/SERVED").toUpperCase());
                orders.updateOrderItemStatus(oid, iid, status);
                OrderDTO order = orders.getOrder(oid);
                if (order.getTableId() != null) api.SocketServer.pushStatusToTable(order.getTableId(), iid, status);
                refresh.execute();
            }
        });
        action(tools, "Chuyển bàn", () -> {
            String answer = JOptionPane.showInputDialog(this, "ID bàn mới:");
            if (answer != null) {
                orders.changeTable(id(t), Long.parseLong(answer.trim()));
                refresh.execute();
            }
        });
        action(tools, "Gộp đơn", () -> {
            String answer = JOptionPane.showInputDialog(this, "ID đơn cần gộp vào đơn đang chọn:");
            if (answer != null) {
                orders.mergeOrders(id(t), Long.parseLong(answer.trim()));
                refresh.execute();
            }
        });
        action(tools, "Thêm món", () -> {
            Map<String, String> p = form("Thêm món", new String[]{"ID món", "Số lượng"}, new String[]{"", "1"});
            if (p != null) {
                orders.addItems(id(t), Collections.singletonMap(Long.parseLong(p.get("ID món")), Integer.parseInt(p.get("Số lượng"))));
                refresh.execute();
            }
        });
        action(tools, "Áp voucher", () -> {
            String code = JOptionPane.showInputDialog(this, "Mã voucher (để trống = gỡ):");
            if (code != null) {
                orders.applyVoucher(id(t), code);
                refresh.execute();
            }
        });
        action(tools, "Hủy đơn", () -> {
            String reason = JOptionPane.showInputDialog(this, "Lý do hủy:");
            if (reason != null && !reason.isBlank()) {
                orders.cancelOrder(id(t), reason);
                refresh.execute();
            }
        });
        run(refresh);
        return page(t, tools);
    }

    private boolean confirmDelete() {
        return JOptionPane.showConfirmDialog(this, "Bạn chắc chắn muốn thực hiện?", "Xác nhận", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
}
**/