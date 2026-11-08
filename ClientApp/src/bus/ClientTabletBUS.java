package bus;

import api.ImageSocketClient;
import api.SocketClient;
import dto.*;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

public class ClientTabletBUS {

    /** Su kien nghiep vu tra ve cho giao dien, khong de GUI xu ly SocketAction. */
    public enum Type {
        MENU, MENU_ERROR, ORDER_SENT, ORDER_ERROR, TRACKING, ITEM_STATUS,
        STAFF_CALLED, ORDER_COMPLETED, BILL, MEMBER_LOGIN, MEMBER_PROFILE,
        MEMBER_HISTORY, MEMBER_LOGOUT, SESSION_CLOSED, ERROR
    }

    public static final class Event {
        public final Type type;
        public final String message;
        public final Object data;
        public final OrderStatus status;

        private Event(Type type, SocketPacket packet) {
            this.type = type;
            this.message = packet.getMessage();
            this.data = packet.getData();
            this.status = packet.getExtraData() instanceof OrderStatus
                    ? (OrderStatus) packet.getExtraData() : null;
        }
    }

    private final Long tableId;
    private final Long defaultTableUserId;
    private final SocketClient socketClient;

    private List<CategoryDTO> categories = new ArrayList<>();
    private List<FoodDTO> menuFoods = new ArrayList<>();
    private final List<OrderItemDTO> currentCart = new ArrayList<>();
    private List<OrderItemDTO> trackedItems = new ArrayList<>();

    private final Map<Long, BufferedImage> foodImageCache = new ConcurrentHashMap<>();
    private final Set<Long> loadingFoodIds = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> failedImageUntil = new ConcurrentHashMap<>();
    private final Map<Long, String> foodImageKeys = new ConcurrentHashMap<>();
    private static final long IMAGE_RETRY_DELAY_MS = 60_000L;
    private final ExecutorService imageWorkers = Executors.newFixedThreadPool(3, job -> {
        Thread thread = new Thread(job, "food-image-tcp");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean imagesActive = true;
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
     * Doc hang doi va chuyen phan hoi TCP thanh su kien nghiep vu cho GUI.
     */
    public Event pollEvent() {
        SocketPacket packet;
        while ((packet = socketClient.pollPacket()) != null) {
            handleServerResponse(packet); // Cap nhat snapshot o BUS truoc khi GUI ve lai.

            if (!packet.isSuccess()) {
                Type failure = packet.getAction() == SocketAction.GET_MENU_AND_CATEGORIES
                        ? Type.MENU_ERROR
                        : packet.getAction() == SocketAction.ORDER_SUBMIT || packet.getAction() == SocketAction.PLACE_ORDER
                        ? Type.ORDER_ERROR : Type.ERROR;
                return new Event(failure, packet);
            }

            Type type;
            switch (packet.getAction()) {
                case GET_MENU_AND_CATEGORIES: type = Type.MENU; break;
                case ORDER_SUBMIT:
                case PLACE_ORDER: type = Type.ORDER_SENT; break;
                case GET_TABLE_TRACKING: type = Type.TRACKING; break;
                case ITEM_STATUS_CHANGED:
                case KITCHEN_UPDATE_STATUS: type = Type.ITEM_STATUS; break;
                case CALL_STAFF: type = Type.STAFF_CALLED; break;
                case ORDER_COMPLETED: type = Type.ORDER_COMPLETED; break;
                case VIEW_SESSION_BILL: type = Type.BILL; break;
                case MEMBER_LOGIN: type = Type.MEMBER_LOGIN; break;
                case MEMBER_PROFILE: type = Type.MEMBER_PROFILE; break;
                case MEMBER_HISTORY: type = Type.MEMBER_HISTORY; break;
                case MEMBER_LOGOUT: type = Type.MEMBER_LOGOUT; break;
                case SESSION_CLOSED:
                case TABLE_CLEARED_RESET: type = Type.SESSION_CLOSED; break;
                case REGISTER_TABLE: type = Type.TRACKING; break;
                case ORDER_CREATED: continue;
                default: continue;
            }
            return new Event(type, packet);
        }
        return null;
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
                        ? new ArrayList<>((List<FoodDTO>) packet.getData()) : new ArrayList<>();
                this.categories = packet.getExtraData() instanceof List<?>
                        ? new ArrayList<>((List<CategoryDTO>) packet.getExtraData()) : new ArrayList<>();
                menuResponseReceived = true;
                menuLoadError = null;
                break;

            case ORDER_SUBMIT:
            case PLACE_ORDER:
                this.currentCart.clear();
                if (packet.getExtraData() instanceof List<?>) {
                    this.trackedItems = new ArrayList<>((List<OrderItemDTO>) packet.getExtraData());
                }
                break;

            case GET_TABLE_TRACKING:
                this.trackedItems = packet.getData() instanceof List<?>
                        ? new ArrayList<>((List<OrderItemDTO>) packet.getData()) : new ArrayList<>();
                break;

            case ITEM_STATUS_CHANGED:
            case KITCHEN_UPDATE_STATUS:
                if (packet.getData() instanceof Long && packet.getExtraData() instanceof OrderStatus) {
                    Long updatedItemId = (Long) packet.getData();
                    OrderStatus newStatus = (OrderStatus) packet.getExtraData();
                    for (OrderItemDTO item : trackedItems) {
                        if (Objects.equals(item.getId(), updatedItemId)) item.setStatus(newStatus);
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
                stopImageLoading();
                menuResponseReceived = false;
                menuLoadError = null;
                break;

            case REGISTER_TABLE:
                tableRegistered = true;
                reloadMenu();
                break;

            case ORDER_CREATED:
                requestTableTracking();
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

    /** Tra anh da cache hoac yeu cau TCP tu pool worker. Khong bao gio block Swing EDT. */
    public BufferedImage getOrRequestFoodImage(FoodDTO food) {
        if (!imagesActive || food == null || food.getId() == null) return null;
        Long foodId = food.getId();
        String key = String.valueOf(food.getCoverImageUrl()) + "|" + food.getSlug();
        String previous = foodImageKeys.put(foodId, key);
        if (previous != null && !previous.equals(key)) {
            foodImageCache.remove(foodId); // Anh bìa thay doi => bo cache cu.
            failedImageUntil.remove(foodId);
        }

        BufferedImage cached = foodImageCache.get(foodId);
        if (cached != null) return cached;
        Long retryAt = failedImageUntil.get(foodId);
        if (retryAt != null && System.currentTimeMillis() < retryAt) return null;
        if (!loadingFoodIds.add(foodId)) return null; // Ngan duplicate worker.

        try {
            imageWorkers.execute(() -> {
                try {
                    BufferedImage image = fetchFoodImageFromSources(food);
                    if (!imagesActive || !Objects.equals(foodImageKeys.get(foodId), key)) return;
                    if (image != null) {
                        foodImageCache.put(foodId, downscaleForCard(image));
                        failedImageUntil.remove(foodId);
                        loadedImageFoodIds.offer(foodId); // CustomerGUI Timer cap nhat tren EDT.
                    } else {
                        failedImageUntil.put(foodId, System.currentTimeMillis() + IMAGE_RETRY_DELAY_MS);
                    }
                } catch (RuntimeException ex) {
                    failedImageUntil.put(foodId, System.currentTimeMillis() + IMAGE_RETRY_DELAY_MS);
                    System.err.println("Tai anh mon #" + foodId + ": " + ex.getMessage());
                } finally {
                    loadingFoodIds.remove(foodId);
                }
            });
        } catch (RejectedExecutionException stopped) {
            loadingFoodIds.remove(foodId);
        }
        return null;
    }

    /** GUI chi poll queue va repaint, khong goi network. */
    public Long pollLoadedImageFoodId() {
        return loadedImageFoodIds.poll();
    }

    public BufferedImage getCachedFoodImage(Long foodId) {
        return foodImageCache.get(foodId);
    }

    /**
     * food_images.url (coverImageUrl) moi la ten anh chinh. Slug chi la fallback.
     * Duong dan trong DB duoc chuyen thanh ten file; Server chi tra anh /img/foods/.
     */
    private BufferedImage fetchFoodImageFromSources(FoodDTO food) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        addImageCandidates(candidates, food.getCoverImageUrl());
        addImageCandidates(candidates, food.getSlug());
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
        int suffix = name.indexOf('?');
        if (suffix >= 0) name = name.substring(0, suffix);
        suffix = name.indexOf('#');
        if (suffix >= 0) name = name.substring(0, suffix);
        name = name.substring(name.lastIndexOf('/') + 1);
        if (name.matches("(?i)[a-z0-9_-]+\\.(png|jpg|jpeg|webp)")) {
            candidates.add(name);
        } else if (name.matches("(?i)[a-z0-9_-]+")) {
            candidates.add(name + ".png");
            candidates.add(name + ".jpg");
            candidates.add(name + ".jpeg");
        }
    }

    /** Anh goc co the rat lon: chi cache anh thu nho de khong tran bo nho tablet. */
    private static BufferedImage downscaleForCard(BufferedImage original) {
        final int maxWidth = 640, maxHeight = 480;
        double ratio = Math.min(1.0, Math.min(maxWidth / (double) original.getWidth(),
                maxHeight / (double) original.getHeight()));
        if (ratio >= 1.0) return original;
        int width = Math.max(1, (int) Math.round(original.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(original.getHeight() * ratio));
        BufferedImage resized = new BufferedImage(width, height,
                original.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(original, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
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

    public boolean loginMember(String email, String password) {
        if (!tableRegistered || !socketClient.isConnected()) return false;
        Map<String, String> credentials = new HashMap<>();
        credentials.put("email", email);
        credentials.put("password", password);
        return socketClient.sendRequest(new SocketPacket(SocketAction.MEMBER_LOGIN, tableId, credentials, null));
    }

    public boolean logoutMember() {
        return tableRegistered && socketClient.sendRequest(
                new SocketPacket(SocketAction.MEMBER_LOGOUT, tableId, null, null));
    }

    public boolean requestMemberProfile() {
        return tableRegistered && socketClient.sendRequest(
                new SocketPacket(SocketAction.MEMBER_PROFILE, tableId, null, null));
    }

    public boolean requestMemberHistory() {
        return tableRegistered && socketClient.sendRequest(
                new SocketPacket(SocketAction.MEMBER_HISTORY, tableId, null, null));
    }

    public void logout() {
        stopImageLoading();
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