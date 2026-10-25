package bus;

import dto.*;
import api.SocketClient;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StaffBUS {

    private final SocketClient socketClient = new SocketClient();
    private List<TableDTO> tableList = new ArrayList<>();
    private OrderDTO currentPreviewOrder; // Chứa đơn gốc + toàn bộ món gộp từ các lượt gọi thêm
    private final Consumer<SocketPacket> guiCallback;

    // Thuế VAT nhà hàng tiêu chuẩn (8%)
    private static final BigDecimal VAT_RATE = new BigDecimal("0.08");

    public StaffBUS(Consumer<SocketPacket> guiCallback) {
        this.guiCallback = guiCallback;
        // Thu ngân kết nối với tableId = 0L (Định danh máy POS Thu ngân)
        socketClient.connect("localhost", 8888, 0L, this::onServerResponse);
    }

    @SuppressWarnings("unchecked")
    private void onServerResponse(SocketPacket packet) {
        if (packet.isSuccess()) {
            switch (packet.getAction()) {
                case GET_ALL_TABLES:
                    this.tableList = (List<TableDTO>) packet.getData();
                    break;
                case GET_TABLE_BILL_PREVIEW:
                    this.currentPreviewOrder = (OrderDTO) packet.getData();
                    break;
                case PAY_TABLE_BILL:
                    this.tableList = (List<TableDTO>) packet.getData();
                    this.currentPreviewOrder = null;
                    break;
                default:
                    break;
            }
        }
        if (guiCallback != null) {
            guiCallback.accept(packet);
        }
    }

    public void requestAllTables() {
        SocketPacket sp = new SocketPacket(SocketAction.GET_ALL_TABLES, 0L, null, null);
        socketClient.sendRequest(sp);
    }

    public void requestTableBillPreview(Long tableId) {
        this.currentPreviewOrder = null;
        socketClient.sendRequest(new SocketPacket(SocketAction.GET_TABLE_BILL_PREVIEW, tableId, null, null));
    }

    public BigDecimal calculateVAT() {
        if (currentPreviewOrder == null || currentPreviewOrder.getSubtotal() == null) return BigDecimal.ZERO;
        BigDecimal afterDiscount = currentPreviewOrder.getSubtotal().subtract(currentPreviewOrder.getDiscountAmount());
        if (afterDiscount.compareTo(BigDecimal.ZERO) < 0) afterDiscount = BigDecimal.ZERO;
        return afterDiscount.multiply(VAT_RATE).setScale(0, RoundingMode.HALF_UP);
    }

    public BigDecimal calculateFinalBillTotal() {
        if (currentPreviewOrder == null || currentPreviewOrder.getSubtotal() == null) return BigDecimal.ZERO;
        BigDecimal afterDiscount = currentPreviewOrder.getSubtotal().subtract(currentPreviewOrder.getDiscountAmount());
        if (afterDiscount.compareTo(BigDecimal.ZERO) < 0) afterDiscount = BigDecimal.ZERO;
        return afterDiscount.add(calculateVAT());
    }

    public boolean confirmCheckout(Long tableId, PaymentMethod paymentMethod) {
        if (currentPreviewOrder == null) return false;

        BillDTO bill = new BillDTO();
        bill.setOrderId(currentPreviewOrder.getId()); // Gắn vào ID của đơn gốc (parent_order_id IS NULL)
        bill.setDiscount(currentPreviewOrder.getDiscountAmount());
        bill.setTax(calculateVAT());
        bill.setTotalAmount(calculateFinalBillTotal());
        bill.setPaymentMethod(paymentMethod);

        socketClient.sendRequest(new SocketPacket(SocketAction.PAY_TABLE_BILL, tableId, bill, null));
        return true;
    }

    public List<TableDTO> getTableList() { return tableList; }
    public OrderDTO getCurrentPreviewOrder() { return currentPreviewOrder; }
}