package bus;

import dto.*;
import api.SocketClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class StaffBUS {

    private final SocketClient socketClient;
    private List<TableDTO> tableList = new ArrayList<>();
    private OrderDTO currentPreviewOrder;
    private static final BigDecimal VAT_RATE = new BigDecimal("0.08");

    public StaffBUS(SocketClient sessionSocket) {
        this.socketClient = java.util.Objects.requireNonNull(sessionSocket);
    }

    /**
     * Chi goi tu Swing Timer tren EDT de update model va giao dien cung luc.
     */
    public SocketPacket pollPacket() {
        SocketPacket packet = socketClient.pollPacket();
        if (packet != null) onServerResponse(packet);
        return packet;
    }

    public void logout() {
        socketClient.logout();
        socketClient.close();
    }

    public boolean isServerConnected() {
        return socketClient.isConnected();
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
        if (currentPreviewOrder == null || currentPreviewOrder.getTableSessionId() == null ||
                tableId == null || !socketClient.isConnected() || paymentMethod == null) return false;

        BillDTO bill = new BillDTO();
        // SQL hien tai: bill.table_session_id; KHONG con bill.order_id.
        // Server se doi chieu phien ACTIVE theo tableId va tinh lai tong tien.
        bill.setTableSessionId(currentPreviewOrder.getTableSessionId());
        bill.setDiscount(currentPreviewOrder.getDiscountAmount());
        bill.setTax(calculateVAT());
        bill.setTotalAmount(calculateFinalBillTotal());
        bill.setPaymentMethod(paymentMethod);

        return socketClient.sendRequest(new SocketPacket(SocketAction.PAY_TABLE_BILL, tableId, bill, null));
    }

    public List<TableDTO> getTableList() {
        return tableList;
    }

    public OrderDTO getCurrentPreviewOrder() {
        return currentPreviewOrder;
    }
}