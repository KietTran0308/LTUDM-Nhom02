package bus;

import api.SocketClient;
import dto.*;
import java.math.BigDecimal;
import java.util.List;

/** Admin chỉ tạo yêu cầu. Mọi ghi CSDL và phân quyền thực hiện tại ServerApp. */
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

    public BigDecimal calculateTotalRevenue(List<BillDTO> bills) {
        return bills.stream().map(BillDTO::getTotalAmount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalDiscount(List<BillDTO> bills) {
        return bills.stream().map(BillDTO::getDiscount).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalTax(List<BillDTO> bills) {
        return bills.stream().map(BillDTO::getTax).filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
