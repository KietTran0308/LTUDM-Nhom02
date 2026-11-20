package bus;

import api.SocketClient;
import dto.*;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class AdminBUS {

    private final SocketClient socket;

    public AdminBUS(SocketClient socket) {
        if (socket == null || !socket.isConnected())
            throw new IllegalArgumentException("Chưa kết nối máy chủ quản trị");
        this.socket = socket;
    }

    private SocketPacket request(SocketAction action, Object data) {
        return socket.requestAndWait(new SocketPacket(action, null, data, null), 7000);
    }

    // ==========================================================
    // --- 1. NGHIỆP VỤ THỰC ĐƠN ---
    // ==========================================================
    @SuppressWarnings("unchecked")
    public List<FoodDTO> getAllFoods() {
        return (List<FoodDTO>) request(SocketAction.ADMIN_GET_FOODS, null).getData();
    }

    @SuppressWarnings("unchecked")
    public List<CategoryDTO> getAllCategories() {
        return (List<CategoryDTO>) request(SocketAction.ADMIN_GET_CATEGORIES, null).getData();
    }

    public boolean addNewFood(String sku, Long categoryId, String name,
                              BigDecimal price, String coverUrl, boolean isPublished) {
        FoodDTO f = new FoodDTO();
        f.setSku(sku); f.setCategoryId(categoryId); f.setName(name);
        f.setPrice(price); f.setCoverImageUrl(coverUrl); f.setIsPublished(isPublished);
        request(SocketAction.ADMIN_ADD_FOOD, f);
        return true;
    }

    public boolean updateFood(Long id, String name, BigDecimal price,
                              Long categoryId, boolean isPublished) {
        FoodDTO f = new FoodDTO();
        f.setId(id); f.setName(name); f.setPrice(price);
        f.setCategoryId(categoryId); f.setIsPublished(isPublished);
        request(SocketAction.ADMIN_UPDATE_FOOD, f);
        return true;
    }

    public boolean deleteFood(Long id) {
        request(SocketAction.ADMIN_DELETE_FOOD, id);
        return true;
    }

    @SuppressWarnings("unchecked")
    public List<IngredientDTO> getAllIngredients() {
        return (List<IngredientDTO>) request(SocketAction.ADMIN_GET_INGREDIENTS, null).getData();
    }

    public boolean addIngredient(String name, String unit, BigDecimal stock, BigDecimal min) {
        IngredientDTO ingredient = new IngredientDTO(null, name, unit, stock, min, null, null);
        request(SocketAction.ADMIN_ADD_INGREDIENT, ingredient);
        return true;
    }

    public boolean restockIngredient(Long id, BigDecimal amount) {
        IngredientDTO ingredient = new IngredientDTO();
        ingredient.setId(id); ingredient.setStockQuantity(amount);
        request(SocketAction.ADMIN_RESTOCK_INGREDIENT, ingredient);
        return true;
    }

    @SuppressWarnings("unchecked")
    public List<BillDTO> getAllBills() {
        return (List<BillDTO>) request(SocketAction.ADMIN_GET_BILLS, null).getData();
    }

    public record RevenueReport(List<BillDTO> bills, JSONObject summary) {}

    @SuppressWarnings("unchecked")
    public RevenueReport getRevenueReport(LocalDate from, LocalDate to, String billNumber, String paymentMethod) {
        JSONObject filters = new JSONObject().put("billNumber", billNumber).put("paymentMethod", paymentMethod);
        if (from != null && to != null) {
            filters.put("from", from.toString()).put("to", to.toString());
        }
        SocketPacket packet = request(SocketAction.ADMIN_GET_BILLS, filters);
        if (!(packet.getExtraData() instanceof JSONObject summary)) {
            throw new IllegalStateException("Server chưa trả dữ liệu thống kê");
        }
        return new RevenueReport((List<BillDTO>) packet.getData(), summary);
    }

    // ==========================================================
    // --- 4. NGHIỆP VỤ TÀI KHOẢN ---
    // ==========================================================
    @SuppressWarnings("unchecked")
    public List<UserDTO> getAllAccounts() {
        return (List<UserDTO>) request(SocketAction.ADMIN_GET_ACCOUNTS, null).getData();
    }

    public boolean createNewAccount(String fullName, String phone, String email, Long roleId, String roleName) {
        UserDTO u = new UserDTO();
        u.setFullName(fullName);
        u.setPhone(phone);
        u.setEmail(email);
        u.setRoleID(roleId);
        u.setRoleName(roleName);
        request(SocketAction.ADMIN_CREATE_ACCOUNT, u);
        return true;
    }

    public boolean updateAccount(Long id, String fullName, String phone, String email, String roleName) {
        UserDTO u = new UserDTO();
        u.setId(id);
        u.setFullName(fullName);
        u.setPhone(phone);
        u.setEmail(email);
        u.setRoleName(roleName);
        request(SocketAction.ADMIN_UPDATE_ACCOUNT, u);
        return true;
    }

    public static UserDTO loginAdmin(
            SocketClient socket,
            String account,
            String password) {

        if (socket == null) {
            throw new IllegalArgumentException("Socket không hợp lệ");
        }

        if (account == null || account.isBlank()
                || password == null || password.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập tài khoản và mật khẩu");
        }

        // Host và Port lấy từ ServerConfig.
        if (!socket.isConnected() && !socket.connect(null)) {
            throw new IllegalStateException(
                    "Không kết nối được Server");
        }

        SocketPacket response = socket.requestAndWait(
                new SocketPacket(
                        SocketAction.LOGIN,
                        null,
                        account.trim(),
                        password
                ),
                7000
        );

        if (!(response.getData() instanceof UserDTO admin)) {
            throw new IllegalStateException(
                    "Server trả về tài khoản không hợp lệ");
        }

        if (!"ADMIN".equalsIgnoreCase(admin.getRoleName())) {
            throw new SecurityException(
                    "Tài khoản không có quyền ADMIN");
        }

        return admin;
    }

    /** Đặt trạng thái đích: active=true là mở khóa, active=false là khóa. */
    public boolean setAccountActive(Long id, boolean active) {
        socket.requestAndWait(
                new SocketPacket(SocketAction.ADMIN_LOCK_ACCOUNT, null, id, active ? "UNLOCK" : "LOCK"),
                7000);
        return true;
    }
}
