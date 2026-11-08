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

    private final Long sessionId;
    private boolean menuResponseReceived;
    private String menuLoadError;

    public ClientTabletBUS(Long tableId, Long sessionId, SocketClient authenticatedCustomerSocket) {
        this.tableId = Objects.requireNonNull(tableId, "Can ma ban");
        this.sessionId = Objects.requireNonNull(sessionId, "Can sessionId da duoc Server xac minh");
        this.defaultTableUserId = null; // Khach khong can dang nhap tai khoan.
        this.socketClient = Objects.requireNonNull(authenticatedCustomerSocket);
        if (!socketClient.isConnected()) throw new IllegalStateException("Mat ket noi Server");
        socketClient.bindCustomerTable(tableId);
        tableRegistered = true; // Da duoc Server phan hoi ENTER_CUSTOMER_MODE truoc khi mo GUI.
        reloadMenu();
        socketClient.requestTableTracking();
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
            if (packet.getAction() == SocketAction.GET_MENU_AND_CATEGORIES) {
                menuResponseReceived = true;
                menuLoadError = packet.getMessage() == null || packet.getMessage().isBlank()
                        ? "May chu khong tai duoc thuc don." : packet.getMessage();
            }
            return;
        }

        switch (packet.getAction()) {
            case GET_MENU_AND_CATEGORIES:
                this.menuFoods = packet.getData() instanceof List<?>
                        ? (List<FoodDTO>) packet.getData() : new ArrayList<>();
                this.categories = packet.getExtraData() instanceof List<?>
                        ? (List<CategoryDTO>) packet.getExtraData() : new ArrayList<>();
                menuResponseReceived = true;
                menuLoadError = null;
                break;

            case ORDER_SUBMIT:
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

            case ITEM_STATUS_CHANGED:
            case KITCHEN_UPDATE_STATUS:
                Long updatedItemId = (Long) packet.getData();
                OrderStatus newStatus = (OrderStatus) packet.getExtraData();
                for (OrderItemDTO item : trackedItems) {
                    if (item.getId() != null && item.getId().equals(updatedItemId)) {
                        item.setStatus(newStatus);
                    }
                }
                break;

            case SESSION_CLOSED:
            case TABLE_CLEARED_RESET:
                tableRegistered = false;
                this.currentCart.clear();
                this.trackedItems.clear();
                this.menuFoods.clear();
                this.categories.clear();
                this.foodImageCache.clear();
                this.loadingFoodIds.clear();
                this.loadedImageFoodIds.clear();
                menuResponseReceived = false;
                menuLoadError = null;
                break;

            case REGISTER_TABLE:
                tableRegistered = true;
                reloadMenu();
                break;

            default:
                break;
        }
    }

    /** Tai lai thuc don: han che hien thi "chua tai" khi Server tra ve mang rong. */
    public boolean reloadMenu() {
        menuResponseReceived = false;
        menuLoadError = null;
        boolean sent = socketClient.requestMenuAndCategories();
        if (!sent) {
            menuResponseReceived = true;
            menuLoadError = "Khong gui duoc yeu cau tai thuc don. Kiem tra ket noi Server.";
        }
        return sent;
    }

    public boolean hasReceivedMenuResponse() { return menuResponseReceived; }
    public String getMenuLoadError() { return menuLoadError; }
    public boolean isConnected() { return socketClient.isConnected(); }

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
        if (food.getSlug() != null && !food.getSlug().isBlank()) {
            String slug = food.getSlug().trim();

            for (String ext : new String[]{".png", ".jpg", ".jpeg"}) {
                BufferedImage img = ImageSocketClient.fetchImage(slug + ext);
                if (img != null) return img;
            }
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
        for (OrderItemDTO item : currentCart) {
            String note = itemNotes == null ? null : itemNotes.get(item.getFoodsId());
            item.setNote(note == null ? "" : note.trim());
        }
        order.setItems(new ArrayList<>(currentCart));

        order.setTableSessionId(sessionId);
        return socketClient.sendRequest(new SocketPacket(SocketAction.ORDER_SUBMIT, tableId, order, null));
    }

    public boolean requestBillPreview() {
        return tableRegistered && socketClient.sendRequest(
                new SocketPacket(SocketAction.VIEW_SESSION_BILL, tableId, null, null));
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