package bus;

import api.ImageSocketClient;
import api.SocketClient;
import dto.*;
import utils.ImageLoader;

import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ClientTabletBUS {

    private final Long tableId;
    private final Long defaultTableUserId;
    private final SocketClient socketClient;

    private List<CategoryDTO> categories = new ArrayList<>();
    private List<FoodDTO> menuFoods = new ArrayList<>();
    private final List<OrderItemDTO> currentCart = new ArrayList<>();
    private List<OrderItemDTO> trackedItems = new ArrayList<>();

    private final Map<Long, BufferedImage> foodImageCache = new ConcurrentHashMap<>();
    private final Set<Long> loadingFoodIds = ConcurrentHashMap.newKeySet();
    private boolean tableRegistered;
    private final BlockingQueue<Long> loadedImageFoodIds = new LinkedBlockingQueue<>();

    public ClientTabletBUS(Long tableId, Long userId, SocketClient sessionSocket, String token) {
        this.tableId = tableId;
        this.defaultTableUserId = userId;
        this.socketClient = Objects.requireNonNull(sessionSocket, "Phải dùng Socket đã đăng nhập");
        if (!this.socketClient.isConnected()) throw new IllegalStateException("Socket đã ngắt kết nối");
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Thiếu token phiên bàn");
        if (!this.socketClient.registerTable(tableId, token)) {
            throw new IllegalStateException("Không gửi được yêu cầu xác minh phiên bàn");
        }
    }

    /**
     * Lấy gói tin từ hàng đợi của SocketClient (giống hệt StaffBUS và AuthBUS)
     */
    public SocketPacket pollPacket() {
        SocketPacket packet = socketClient.pollPacket();
        if (packet != null) {
            handleServerResponse(packet);
        }
        return packet;
    }

    @SuppressWarnings("unchecked")
    private void handleServerResponse(SocketPacket packet) {
        if (!packet.isSuccess()) {
            if (packet.getAction() == SocketAction.REGISTER_TABLE) tableRegistered = false;
            return;
        }

        switch (packet.getAction()) {
            case GET_MENU_AND_CATEGORIES:
                this.menuFoods = packet.getData() instanceof List<?>
                        ? (List<FoodDTO>) packet.getData() : new ArrayList<>();
                this.categories = packet.getExtraData() instanceof List<?>
                        ? (List<CategoryDTO>) packet.getExtraData() : new ArrayList<>();
                break;

            case PLACE_ORDER:
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
                Long updatedItemId = (Long) packet.getData();
                OrderStatus newStatus = (OrderStatus) packet.getExtraData();
                for (OrderItemDTO item : trackedItems) {
                    if (item.getId() != null && item.getId().equals(updatedItemId)) {
                        item.setStatus(newStatus);
                    }
                }
                break;

            case TABLE_CLEARED_RESET:
                tableRegistered = false;
                this.currentCart.clear();
                this.trackedItems.clear();
                break;

            case REGISTER_TABLE:
                tableRegistered = true;
                socketClient.requestMenuAndCategories();
                break;

            default:
                break;
        }
    }

    /**
     * Lấy ảnh đã có trong cache hoặc kích hoạt luồng tải ngầm đưa vào hàng đợi
     */
    public BufferedImage getOrRequestFoodImage(FoodDTO food) {
        if (food == null || food.getId() == null) return null;
        Long foodId = food.getId();

        if (foodImageCache.containsKey(foodId)) {
            return foodImageCache.get(foodId);
        }
        if (loadingFoodIds.contains(foodId)) {
            return null;
        }

        loadingFoodIds.add(foodId);
        Thread worker = new Thread(() -> {
            try {
                BufferedImage img = fetchFoodImageFromSources(food);
                if (img != null) {
                    foodImageCache.put(foodId, img);
                    loadedImageFoodIds.offer(foodId);
                }
            } finally {
                loadingFoodIds.remove(foodId);
            }
        }, "food-img-loader-" + foodId);
        worker.setDaemon(true);
        worker.start();

        return null;
    }

    /**
     * Để Timer bên CustomerGUI kiểm tra xem có ảnh mới tải xong trong hàng đợi không
     */
    public Long pollLoadedImageFoodId() {
        return loadedImageFoodIds.poll();
    }

    public BufferedImage getCachedFoodImage(Long foodId) {
        return foodImageCache.get(foodId);
    }

    private BufferedImage fetchFoodImageFromSources(FoodDTO food) {
        // 1. Ưu tiên tải nhị phân theo khối qua ImageSocketClient
        if (food.getSlug() != null && !food.getSlug().isBlank()) {
            String slug = food.getSlug().trim();
            BufferedImage socketImg = ImageSocketClient.fetchImage(slug + ".png");
            if (socketImg == null) socketImg = ImageSocketClient.fetchImage(slug + ".jpg");
            if (socketImg != null) return socketImg;
        }
        // 2. Fallback qua ImageLoader
        if (food.getCoverImageUrl() != null && !food.getCoverImageUrl().isBlank()) {
            BufferedImage img = ImageLoader.readImage(food.getCoverImageUrl().trim());
            if (img != null) return img;
        }
        if (food.getSlug() != null && !food.getSlug().isBlank()) {
            return ImageLoader.readImage("/img/foods/" + food.getSlug().trim());
        }
        return null;
    }

    public List<FoodDTO> getFoodsByCategory(Long categoryId) {
        return menuFoods.stream()
                .filter(f -> categoryId == null || categoryId.equals(f.getCategoryId()))
                .collect(Collectors.toList());
    }

    public int getFoodQuantityInCart(Long foodId) {
        return currentCart.stream()
                .filter(item -> item.getFoodsId().equals(foodId))
                .mapToInt(OrderItemDTO::getQuantity)
                .findFirst()
                .orElse(0);
    }

    public int getTotalCartQuantity() {
        return currentCart.stream().mapToInt(OrderItemDTO::getQuantity).sum();
    }

    public OrderItemDTO getTrackedItemById(Long itemId) {
        if (itemId == null) return null;
        return trackedItems.stream()
                .filter(item -> Objects.equals(item.getId(), itemId))
                .findFirst()
                .orElse(null);
    }

    public OrderItemDTO getTrackedItemAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= trackedItems.size()) return null;
        return trackedItems.get(rowIndex);
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

    public boolean submitOrderToServer(String voucherCode, Map<Long, String> itemNotes) {
        if (currentCart.isEmpty() || !socketClient.isConnected() || !tableRegistered) return false;

        OrderDTO order = new OrderDTO();
        order.setUserId(this.defaultTableUserId);
        order.setTableId(this.tableId);
        order.setOrderType(OrderType.DINE_IN);
        order.setStatus(OrderStatus.PENDING);
        order.setNote(buildOrderNote(itemNotes));
        order.setItems(new ArrayList<>(currentCart));

        return socketClient.sendOrderToServer(order, voucherCode);
    }

    public boolean callStaffSupport(String message) {
        return tableRegistered && socketClient.callStaff(message);
    }

    public void logout() {
        socketClient.logout();
        socketClient.close();
    }

    public void requestTableTracking() {
        if (socketClient.isConnected() && tableRegistered) socketClient.requestTableTracking();
    }

    public boolean isServerConnected() {
        return socketClient.isConnected();
    }

    private String buildOrderNote(Map<Long, String> itemNotes) {
        if (itemNotes == null || itemNotes.isEmpty()) return "";
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