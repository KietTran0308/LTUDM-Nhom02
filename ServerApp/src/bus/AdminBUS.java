package bus;

import dao.*;
import dto.*;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AdminBUS {
    private final FoodDAO foodDAO = new FoodDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final IngredientDAO ingredientDAO = new IngredientDAO();
    private final BillDAO billDAO = new BillDAO();

    // --- 1. NGHIỆP VỤ QUẢN LÝ MÓN ĂN ---
    public List<FoodDTO> getAllFoods() {
        return foodDAO.getAllFoodsForAdmin();
    }

    public List<CategoryDTO> getAllCategories() {
        return categoryDAO.getAllCategories();
    }

    public boolean addNewFood(String sku, Long categoryId, String name, BigDecimal price, String coverUrl, boolean isPublished) {
        FoodDTO food = new FoodDTO();
        food.setSku(sku.trim().toUpperCase());
        food.setCategoryId(categoryId);
        food.setName(name.trim());
        food.setSlug(generateSlug(name) + "-" + (System.currentTimeMillis() % 10000));
        food.setDescription("Món ngon chuẩn vị Haidilao");
        food.setPrice(price);
        food.setIsPublished(isPublished);
        food.setCoverImageUrl(coverUrl);
        return foodDAO.insertFoodWithCover(food);
    }

    public boolean updateFood(Long id, String name, BigDecimal price, Long categoryId, boolean isPublished) {
        FoodDTO food = new FoodDTO();
        food.setId(id);
        food.setName(name.trim());
        food.setPrice(price);
        food.setCategoryId(categoryId);
        food.setIsPublished(isPublished);
        return foodDAO.updateFoodBasicInfo(food);
    }

    public boolean deleteFood(Long id) {
        return foodDAO.softDeleteFood(id);
    }

    // --- 2. NGHIỆP VỤ KHO NGUYÊN LIỆU & CẢNH BÁO ---
    public List<IngredientDTO> getAllIngredients() {
        return ingredientDAO.getAllIngredients();
    }

    /**
     * Lọc danh sách nguyên liệu có tồn kho chạm đáy (stock_quantity <= min_stock_level)
     */
    public List<IngredientDTO> getLowStockIngredients() {
        return ingredientDAO.getAllIngredients().stream()
                .filter(i -> i.getStockQuantity().compareTo(i.getMinStockLevel()) <= 0)
                .collect(Collectors.toList());
    }

    public boolean restockIngredient(Long ingredientId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return false;
        return ingredientDAO.restockIngredient(ingredientId, amount);
    }

    public boolean addIngredient(String name, String unit, BigDecimal initialStock, BigDecimal minLevel) {
        IngredientDTO ing = new IngredientDTO(null, name.trim(), unit.trim(), initialStock, minLevel, null, null);
        return ingredientDAO.insertIngredient(ing);
    }

    // --- 3. NGHIỆP VỤ THỐNG KÊ DOANH THU (BẢNG BILL) ---
    public List<BillDTO> getAllBills() {
        return billDAO.getAllBills();
    }

    public BigDecimal calculateTotalRevenue(List<BillDTO> bills) {
        return bills.stream()
                .map(BillDTO::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalDiscount(List<BillDTO> bills) {
        return bills.stream()
                .map(BillDTO::getDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal calculateTotalTax(List<BillDTO> bills) {
        return bills.stream()
                .map(BillDTO::getTax)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Hàm tiện ích chuyển tên tiếng Việt có dấu thành slug chuẩn
    private String generateSlug(String input) {
        String temp = Normalizer.normalize(input, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        return pattern.matcher(temp).replaceAll("")
                .toLowerCase()
                .replaceAll("đ", "d")
                .replaceAll("[^a-z0-9\\s-]", "")
                .trim()
                .replaceAll("\\s+", "-");
    }
}