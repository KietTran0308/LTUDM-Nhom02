package bus;

import api.ImageSocketClient;
import api.SocketClient;
import dto.*;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ClientTabletBUS {

    public enum Type {
        MENU, MENU_ERROR, ORDER_SENT, ORDER_ERROR, TRACKING, ITEM_STATUS,
        ORDER_COMPLETED, BILL, SESSION_CLOSED, ERROR
    }

    public static final class Event {
        public final Type type;
        public final String message;
        public final Object data;
        public final dbEnums.OrderStatus status;

        private Event(Type type, SocketPacket packet) {
            this.type = type;
            this.message = packet.getMessage();
            this.data = packet.getData();
            this.status = packet.getExtraData() instanceof dbEnums.OrderStatus st ? st : null;
        }
    }

    private static final long IMAGE_RETRY_DELAY_MS = 60_000L;

    private final Long tableId;
    private final Long sessionId;
    private final SocketClient socketClient;

    private List<CategoryDTO> categories = new ArrayList<>();
    private List<FoodDTO> menuFoods = new ArrayList<>();
    private final List<OrderItemDTO> currentCart = new ArrayList<>();
    private List<OrderItemDTO> trackedItems = new ArrayList<>();

    private final Map<Long, BufferedImage> foodImageCache = new ConcurrentHashMap<>();
    private final Set<Long> loadingFoodIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> failedImageUntil = new ConcurrentHashMap<>();
    private final Map<Long, String> foodImageKeys = new ConcurrentHashMap<>();
    private final BlockingQueue<Long> loadedImageFoodIds = new LinkedBlockingQueue<>();

    private final ExecutorService imageWorkers = Executors.newFixedThreadPool(3, job -> {
        Thread thread = new Thread(job, "food-image-tcp");
        thread.setDaemon(true);
        return thread;
    });

    private volatile boolean imagesActive = true;
    private boolean tableRegistered;
    private boolean menuResponseReceived;
    private String menuLoadError;

    public ClientTabletBUS(Long tableId, Long sessionId, SocketClient authenticatedCustomerSocket) {
        this.tableId = Objects.requireNonNull(tableId, "Cần mã bàn");
        this.sessionId = Objects.requireNonNull(sessionId, "Cần sessionId đã được Server xác minh");
        this.socketClient = Objects.requireNonNull(authenticatedCustomerSocket);
        if (!socketClient.isConnected()) {
            throw new IllegalStateException("Mất kết nối Server");
        }

        socketClient.bindCustomerTable(tableId);
        this.tableRegistered = true;
        reloadMenu();
        socketClient.requestTableTracking();
    }

    /** Đọc hàng đợi và chuyển phản hồi TCP thành sự kiện nghiệp vụ cho GUI. */
    public Event pollEvent() {
        SocketPacket packet;
        while ((packet = socketClient.pollPacket()) != null) {
            handleServerResponse(packet);

            if (!packet.isSuccess()) {
                Type failure = switch (packet.getAction()) {
                    case GET_MENU_AND_CATEGORIES -> Type.MENU_ERROR;
                    case ORDER_SUBMIT -> Type.ORDER_ERROR;
                    default -> Type.ERROR;
                };
                return new Event(failure, packet);
            }

            Type type = switch (packet.getAction()) {
                case GET_MENU_AND_CATEGORIES -> Type.MENU;
                case ORDER_SUBMIT -> Type.ORDER_SENT;
                case GET_TABLE_TRACKING -> Type.TRACKING;
                case ITEM_STATUS_CHANGED -> Type.ITEM_STATUS;
                case ORDER_COMPLETED -> Type.ORDER_COMPLETED;
                case VIEW_SESSION_BILL -> Type.BILL;
                case SESSION_CLOSED -> Type.SESSION_CLOSED;
                default -> null; // ORDER_CREATED chỉ đồng bộ dữ liệu tại BUS
            };
            if (type != null) {
                return new Event(type, packet);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private void handleServerResponse(SocketPacket packet) {
        if (!packet.isSuccess()) {
            if (packet.getAction() == SocketAction.GET_MENU_AND_CATEGORIES) {
                menuResponseReceived = true;
                menuLoadError = (packet.getMessage() == null || packet.getMessage().isBlank())
                        ? "Máy chủ không tải được thực đơn."
                        : packet.getMessage();
            }
            return;
        }

        switch (packet.getAction()) {
            case GET_MENU_AND_CATEGORIES -> {
                menuFoods = packet.getData() instanceof List<?> list
                        ? new ArrayList<>((List<FoodDTO>) list) : new ArrayList<>();
                categories = packet.getExtraData() instanceof List<?> list
                        ? new ArrayList<>((List<CategoryDTO>) list) : new ArrayList<>();
                Set<Long> availableIds = menuFoods.stream().map(FoodDTO::getId).collect(Collectors.toSet());
                currentCart.removeIf(item -> !availableIds.contains(item.getFoodsId()));
                menuResponseReceived = true;
                menuLoadError = null;
            }
            case ORDER_SUBMIT -> {
                currentCart.clear();
                if (packet.getExtraData() instanceof List<?> list) {
                    trackedItems = new ArrayList<>((List<OrderItemDTO>) list);
                }
            }
            case GET_TABLE_TRACKING -> trackedItems = packet.getData() instanceof List<?> list
                    ? new ArrayList<>((List<OrderItemDTO>) list) : new ArrayList<>();
            case ITEM_STATUS_CHANGED -> {
                if (packet.getData() instanceof Long updatedItemId
                        && packet.getExtraData() instanceof dbEnums.OrderStatus newStatus) {
                    for (OrderItemDTO item : trackedItems) {
                        if (Objects.equals(item.getId(), updatedItemId)) {
                            item.setStatus(newStatus);
                        }
                    }
                }
            }
            case SESSION_CLOSED -> {
                tableRegistered = false;
                currentCart.clear();
                trackedItems.clear();
                menuFoods.clear();
                categories.clear();
                stopImageLoading();
                menuResponseReceived = false;
                menuLoadError = null;
            }
            case ORDER_CREATED -> requestTableTracking();
            default -> { }
        }
    }

    public boolean reloadMenu() {
        menuResponseReceived = false;
        menuLoadError = null;
        boolean sent = socketClient.requestMenuAndCategories();
        if (!sent) {
            menuResponseReceived = true;
            menuLoadError = "Không gửi được yêu cầu tải thực đơn. Kiểm tra kết nối Server.";
        }
        return sent;
    }

    public boolean hasReceivedMenuResponse() { return menuResponseReceived; }
    public String getMenuLoadError() { return menuLoadError; }
    public boolean isConnected() { return socketClient.isConnected(); }
    public boolean isServerConnected() { return isConnected(); }

    /** Trả ảnh đã cache hoặc tải bất đồng bộ từ worker pool (không block Swing EDT). */
    public BufferedImage getOrRequestFoodImage(FoodDTO food) {
        if (!imagesActive || food == null || food.getId() == null) return null;

        Long foodId = food.getId();
        String key = String.valueOf(food.getCoverImageUrl());
        String previous = foodImageKeys.put(foodId, key);
        if (previous != null && !previous.equals(key)) {
            foodImageCache.remove(foodId);
            failedImageUntil.remove(foodId);
        }

        BufferedImage cached = foodImageCache.get(foodId);
        if (cached != null) return cached;

        Long retryAt = failedImageUntil.get(foodId);
        if (retryAt != null && System.currentTimeMillis() < retryAt) return null;
        if (!loadingFoodIds.add(foodId)) return null;

        try {
            imageWorkers.execute(() -> {
                try {
                    BufferedImage image = fetchFoodImageFromSources(food);
                    if (!imagesActive || !Objects.equals(foodImageKeys.get(foodId), key)) return;
                    if (image != null) {
                        foodImageCache.put(foodId, downscaleForCard(image));
                        failedImageUntil.remove(foodId);
                        loadedImageFoodIds.offer(foodId);
                    } else {
                        failedImageUntil.put(foodId, System.currentTimeMillis() + IMAGE_RETRY_DELAY_MS);
                    }
                } catch (RuntimeException ex) {
                    failedImageUntil.put(foodId, System.currentTimeMillis() + IMAGE_RETRY_DELAY_MS);
                    System.err.println("Tải ảnh món #" + foodId + ": " + ex.getMessage());
                } finally {
                    loadingFoodIds.remove(foodId);
                }
            });
        } catch (RejectedExecutionException stopped) {
            loadingFoodIds.remove(foodId);
        }
        return null;
    }

    public Long pollLoadedImageFoodId() {
        return loadedImageFoodIds.poll();
    }

    public BufferedImage getCachedFoodImage(Long foodId) {
        return foodImageCache.get(foodId);
    }

    private BufferedImage fetchFoodImageFromSources(FoodDTO food) {
        Set<String> candidates = new LinkedHashSet<>();
        addImageCandidates(candidates, food.getCoverImageUrl());
        for (String name : candidates) {
            if (!imagesActive || Thread.currentThread().isInterrupted()) return null;
            BufferedImage image = ImageSocketClient.fetchImage(name);
            if (image != null) return image;
        }
        return null;
    }

    private static void addImageCandidates(Set<String> candidates, String source) {
        if (source == null || source.isBlank()) return;
        String name = source.trim().replace('\\', '/');
        int queryIdx = name.indexOf('?');
        if (queryIdx >= 0) name = name.substring(0, queryIdx);
        int hashIdx = name.indexOf('#');
        if (hashIdx >= 0) name = name.substring(0, hashIdx);
        name = name.substring(name.lastIndexOf('/') + 1);

        if (name.matches("(?i)[a-z0-9_-]+\\.(png|jpg|jpeg|webp)")) {
            candidates.add(name);
        } else if (name.matches("(?i)[a-z0-9_-]+")) {
            candidates.add(name + ".png");
            candidates.add(name + ".jpg");
            candidates.add(name + ".jpeg");
        }
    }

    private static BufferedImage downscaleForCard(BufferedImage original) {
        final int maxWidth = 640, maxHeight = 480;
        double ratio = Math.min(1.0, Math.min(maxWidth / (double) original.getWidth(),
                maxHeight / (double) original.getHeight()));
        if (ratio >= 1.0) return original;

        int width = Math.max(1, (int) Math.round(original.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(original.getHeight() * ratio));
        int imageType = original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage resized = new BufferedImage(width, height, imageType);

        Graphics2D g2 = resized.createGraphics();
        try {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(original, 0, 0, width, height, null);
        } finally {
            g2.dispose();
        }
        return resized;
    }

    private void stopImageLoading() {
        imagesActive = false;
        imageWorkers.shutdownNow();
        foodImageCache.clear();
        failedImageUntil.clear();
        foodImageKeys.clear();
        loadingFoodIds.clear();
        loadedImageFoodIds.clear();
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
        return (rowIndex >= 0 && rowIndex < trackedItems.size()) ? trackedItems.get(rowIndex) : null;
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
                food.getPrice(), 1, food.getPrice(), dbEnums.OrderStatus.PENDING
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
        order.setTableId(tableId);
        order.setTableSessionId(sessionId);
        order.setStatus(dbEnums.OrderStatus.PENDING);
        order.setNote(buildOrderNote(itemNotes));

        for (OrderItemDTO item : currentCart) {
            String note = itemNotes == null ? null : itemNotes.get(item.getFoodsId());
            item.setNote(note == null ? "" : note.trim());
        }
        order.setItems(new ArrayList<>(currentCart));

        return socketClient.sendRequest(new SocketPacket(SocketAction.ORDER_SUBMIT, tableId, order, null));
    }

    private String buildOrderNote(Map<Long, String> itemNotes) {
        if (itemNotes == null || itemNotes.isEmpty()) return "";
        List<String> notes = new ArrayList<>();
        for (OrderItemDTO item : currentCart) {
            String note = itemNotes.get(item.getFoodsId());
            if (note != null && !note.isBlank()) {
                notes.add(item.getFoodsName() + " (x" + item.getQuantity() + "): " + note.trim());
            }
        }
        return String.join(" | ", notes);
    }

    public void requestTableTracking() {
        if (socketClient.isConnected() && tableRegistered) {
            socketClient.requestTableTracking();
        }
    }

    public boolean canRequestCancelOrEdit(OrderItemDTO item) {
        if (item == null) return false;
        dbEnums.OrderStatus status = item.getStatus();
        return status == dbEnums.OrderStatus.PENDING || status == dbEnums.OrderStatus.RECEIVED;
    }

    public void logout() {
        stopImageLoading();
        socketClient.logout();
        socketClient.close();
    }

    public List<CategoryDTO> getCategories() { return categories; }
    public List<FoodDTO> getMenuFoods() { return menuFoods; }
    public List<OrderItemDTO> getCurrentCart() { return currentCart; }
    public List<OrderItemDTO> getTrackedItems() { return trackedItems; }
}