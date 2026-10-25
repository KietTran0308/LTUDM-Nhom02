package bus;

import dao.*;
import dto.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.List;

public class ServerOrderBUS {
    private final OrderDAO orderDAO = new OrderDAO();
    private final VoucherDAO voucherDAO = new VoucherDAO();

    /**
     * Xử lý nghiệp vụ khi nhận 1 OrderDTO từ Tablet khách hàng gửi qua TCP Socket
     */
    public OrderDTO processIncomingTableOrder(OrderDTO order, String voucherCode) throws Exception {
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new IllegalArgumentException("Đơn hàng không có món ăn nào!");
        }

        // 1. Kiểm tra xem bàn này đang có đơn gốc nào chưa thanh toán không (Gọi món nhiều lượt)
        if (order.getTableId() != null) {
            Long activeRootOrderId = orderDAO.findActiveRootOrderByTable(order.getTableId());
            if (activeRootOrderId != null) {
                order.setParentOrderId(activeRootOrderId); // Gắn làm đơn con của lượt gọi đầu tiên
            }
        }

        // 2. Sinh mã đơn hàng duy nhất (Order Code <= 30 ký tự)
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("ddHHmmss"));
        String code = "HDL-T" + (order.getTableId() != null ? order.getTableId() : "0") + "-" + timestamp + "-" + java.util.UUID.randomUUID().toString().substring(0, 6);
        order.setOrderCode(code);
        order.setOrderType(OrderType.DINE_IN);
        order.setStatus(OrderStatus.PENDING);

        // 3. Tính tổng tiền các món (Subtotal)
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderItemDTO item : order.getItems()) {
            BigDecimal lineTotal = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            item.setLineTotal(lineTotal);
            item.setStatus(OrderStatus.PENDING);
            subtotal = subtotal.add(lineTotal);
        }
        order.setSubtotal(subtotal);
        order.setShippingFee(BigDecimal.ZERO); // Ăn tại bàn không tính phí ship

        // 4. Tính khuyến mãi nếu khách nhập mã Voucher
        BigDecimal discount = BigDecimal.ZERO;
        if (voucherCode != null && !voucherCode.trim().isEmpty()) {
            VoucherDTO v = voucherDAO.getValidVoucherByCode(voucherCode.trim());
            if (v != null && subtotal.compareTo(v.getMinOrderAmount()) >= 0) {
                order.setVoucherId(v.getId());
                if (v.getDiscountType() == DiscountType.PERCENT) {
                    discount = subtotal.multiply(v.getDiscountValue())
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    if (v.getMaxDiscountAmount() != null && discount.compareTo(v.getMaxDiscountAmount()) > 0) {
                        discount = v.getMaxDiscountAmount();
                    }
                } else {
                    discount = v.getDiscountValue();
                }
            }
        }
        order.setDiscountAmount(discount);

        // 5. Tính tổng thanh toán cuối cùng
        BigDecimal total = subtotal.subtract(discount);
        if (total.compareTo(BigDecimal.ZERO) < 0) total = BigDecimal.ZERO;
        order.setTotalAmount(total);

        // 6. Gọi xuống DAO để thực thi Transaction lưu Database
        try {
            Long newOrderId = orderDAO.createTableOrderTransaction(order);
            order.setId(newOrderId);
            return order;
        } catch (SQLException e) {
            throw new Exception("Lỗi lưu đơn hàng xuống CSDL: " + e.getMessage());
        }
    }

    /**
     * Dành cho Bếp: Đổi trạng thái món ăn
     */
    public boolean changeDishStatusByKitchen(Long orderItemId, OrderStatus newStatus) {
        return orderDAO.updateOrderItemStatus(orderItemId, newStatus);
    }

    /**
     * Trả về danh sách các món đã gọi của bàn để hiển thị tab "Theo dõi đơn"
     */
    public List<OrderItemDTO> getTableTrackingItems(Long tableId) {
        return orderDAO.getTrackingItemsByTable(tableId);
    }

    public List<TableDTO> getAllRestaurantTables() {
        return TableDAO.getAllTables();
    }

    public OrderDTO getTableBillPreview(Long tableId) {
        return orderDAO.getMergedTableOrderForCheckout(tableId);
    }

    public boolean processTablePayment(BillDTO bill, Long tableId) {
        return BillDAO.createBillAndReleaseTable(bill, tableId);
    }
}