package bus;

import api.SocketClient;
import dto.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.Map;

public class ClientTabletBUS {

    private final Long tableId, defaultTableUserId;
    private final SocketClient socketClient = new SocketClient();

    private List<CategoryDTO> categories = new ArrayList<>();
    private List<FoodDTO> menuFoods = new ArrayList<>();
    private final List<OrderItemDTO> currentCart = new ArrayList<>();
    private List<OrderItemDTO> trackedItems = new ArrayList<>();

    // Callback để báo cho HaidilaoClientGUI tự động vẽ lại màn hình khi có dữ liệu từ Server
    private final Consumer<SocketPacket> guiUpdater;

    public ClientTabletBUS(Long tableId, Long defaultTableUserId, Consumer<SocketPacket> guiUpdater) {
        this.tableId = tableId;
        this.defaultTableUserId = defaultTableUserId;
        this.guiUpdater = guiUpdater;

        // Kết nối tới Server TCP cổng 8888
        socketClient.connect("localhost", 8888, tableId, this::handleServerResponse);
    }

    @SuppressWarnings("unchecked")
    private void handleServerResponse(SocketPacket packet) {
        if (packet.isSuccess()) {
            switch (packet.getAction()) {
                case GET_MENU_AND_CATEGORIES:
                    this.menuFoods = packet.getData() instanceof List<?>
                            ? (List<FoodDTO>) packet.getData() : new ArrayList<>();
                    this.categories = packet.getExtraData() instanceof List<?>
                            ? (List<CategoryDTO>) packet.getExtraData() : new ArrayList<>();
                    break;

                case PLACE_ORDER:
                    // Chỉ xóa giỏ hàng sau khi server xác nhận thành công.
                    this.currentCart.clear();
                    if (packet.getExtraData() instanceof List<?>) {
                        this.trackedItems = (List<OrderItemDTO>) packet.getExtraData();
                    }
                    break;

                case GET_TABLE_TRACKING:
                    this.trackedItems = packet.getData() instanceof List<?>
                            ? (List<OrderItemDTO>) packet.getData() : new ArrayList<>();
                    break;

                case KITCHEN_UPDATE_STATUS:
                    // Khi Bếp bấm đổi trạng thái 1 món, cập nhật ngay món đó trên danh sách của Tablet
                    Long updatedItemId = (Long) packet.getData();
                    OrderStatus newStatus = (OrderStatus) packet.getExtraData();
                    for (OrderItemDTO item : trackedItems) {
                        if (item.getId() != null && item.getId().equals(updatedItemId)) {
                            item.setStatus(newStatus);
                        }
                    }
                    break;

                case TABLE_CLEARED_RESET:
                    this.currentCart.clear();
                    this.trackedItems.clear();
                    break;
                default:
                    break;
            }
        }
        // Đẩy sự kiện lên GUI (chạy trong luồng SwingUtilities.invokeLater)
        if (guiUpdater != null) {
            guiUpdater.accept(packet);
        }
    }

    public void addFoodToCart(FoodDTO food) {
        for (OrderItemDTO item : currentCart) {
            if (item.getFoodsId().equals(food.getId())) {
                int newQty = item.getQuantity() + 1;
                item.setQuantity(newQty);
                item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(newQty)));
                return;
            }
        }
        currentCart.add(new OrderItemDTO(
                null, null, food.getId(), food.getName(),
                food.getPrice(), 1, food.getPrice(), OrderStatus.PENDING
        ));
    }

    public void updateCartQuantity(Long foodId, int delta) {
        currentCart.removeIf(item -> {
            if (item.getFoodsId().equals(foodId)) {
                int newQty = item.getQuantity() + delta;
                if (newQty <= 0) return true;
                item.setQuantity(newQty);
                item.setLineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(newQty)));
            }
            return false;
        });
    }

    public BigDecimal calculateCartSubtotal() {
        return currentCart.stream()
                .map(OrderItemDTO::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Gửi đơn hàng từ giỏ kèm mã Voucher và ghi chú qua TCP Socket
     */
    public boolean submitOrderToServer(String voucherCode, Map<Long, String> itemNotes){
        if (currentCart.isEmpty() || !socketClient.isConnected()) return false;

        OrderDTO order = new OrderDTO();
        order.setUserId(this.defaultTableUserId);
        order.setTableId(this.tableId);
        order.setOrderType(OrderType.DINE_IN);
        order.setStatus(OrderStatus.PENDING);
        order.setNote(buildOrderNote(itemNotes));
        order.setItems(new ArrayList<>(currentCart));

        socketClient.sendOrderToServer(order, voucherCode);
        return true;
    }

    public void callStaffSupport(String message) {
        socketClient.callStaff(message);
    }

    public void requestTableTracking() {
        if (socketClient.isConnected()) socketClient.requestTableTracking();
    }

    public boolean isServerConnected() {
        return socketClient.isConnected();
    }

    private String buildOrderNote(Map<Long, String> itemNotes) {
        if (itemNotes == null || itemNotes.isEmpty()) {
            return "";
        }

        List<String> notes = new ArrayList<>();

        for (OrderItemDTO item : currentCart) {
            String note = itemNotes.get(item.getFoodsId());
            if (note == null || note.isBlank()) continue;

            notes.add(item.getFoodsName() + " (x" + item.getQuantity() + "): " + note.trim());

        }

        return String.join(" | ", notes);
    }

    public boolean canRequestCancelOrEdit(OrderItemDTO item) {
        if (item == null) return false;
        OrderStatus status = item.getStatus();

        return status == OrderStatus.PENDING || status == OrderStatus.RECEIVED;
    }

    public List<CategoryDTO> getCategories() {
        return categories;
    }

    public List<FoodDTO> getMenuFoods() {
        return menuFoods;
    }

    public List<OrderItemDTO> getCurrentCart() {
        return currentCart;
    }

    public List<OrderItemDTO> getTrackedItems() {
        return trackedItems;
    }
}